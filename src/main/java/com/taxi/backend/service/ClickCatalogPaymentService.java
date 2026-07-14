package com.taxi.backend.service;

import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.model.ClickTransaction;
import com.taxi.backend.model.Driver;
import com.taxi.backend.repository.ClickTransactionRepository;
import com.taxi.backend.repository.DriverRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Catalog payment state machine behind the already-proven Merchant API
 * /api/payment/click/prepare and /complete endpoints.  Request signatures are
 * checked by PaymentService before this class is entered.
 */
@Service
public class ClickCatalogPaymentService {

    static final String SOURCE = "CLICK_SUPERAPP";

    private final DriverRepository driverRepository;
    private final ClickTransactionRepository clickTransactionRepository;
    private final ClickWalletCreditService walletCreditService;
    private final ClickCatalogModePolicy catalogModePolicy;

    public ClickCatalogPaymentService(DriverRepository driverRepository,
                                      ClickTransactionRepository clickTransactionRepository,
                                      ClickWalletCreditService walletCreditService,
                                      ClickCatalogModePolicy catalogModePolicy) {
        this.driverRepository = driverRepository;
        this.clickTransactionRepository = clickTransactionRepository;
        this.walletCreditService = walletCreditService;
        this.catalogModePolicy = catalogModePolicy;
    }

    @Transactional
    public Map<String, Object> prepare(Map<String, String> params) {
        String account = params.get("merchant_trans_id");
        String clickTransId = params.get("click_trans_id");
        String clickPaydocId = params.get("click_paydoc_id");
        ClickCatalogMode mode = catalogModePolicy.current();
        if (mode == ClickCatalogMode.OFF) {
            // OFF is intentionally side-effect free: no catalog lookup that can
            // be mistaken for a new payment intent, no order row, no credit.
            return disabled(clickTransId, account);
        }
        Long amount = amountToTiyin(params.get("amount"));
        if (amount == null) {
            return error(-2, "Summa formati xato", clickTransId, account);
        }

        Optional<ClickTransaction> existing = clickTransactionRepository.findByClickTransIdForUpdate(clickTransId);
        if (!catalogModePolicy.allowsNewPrepare()) {
            if (mode == ClickCatalogMode.DRAIN && existing.isPresent()) {
                return existingPrepare(existing.get(), account, clickPaydocId, amount, clickTransId);
            }
            return disabled(clickTransId, account);
        }
        if (existing.isPresent()) {
            return existingPrepare(existing.get(), account, clickPaydocId, amount, clickTransId);
        }
        if (clickTransactionRepository.findByClickPaydocId(clickPaydocId).isPresent()) {
            return error(-6, "Transaction does not exist", clickTransId, account);
        }

        Driver driver = driverRepository.findByDriverCode(account)
                .filter(candidate -> candidate.getStatus() == DriverStatus.ACTIVE)
                .orElse(null);
        if (driver == null) {
            return error(-5, "Order topilmadi", clickTransId, account);
        }

        String merchantPrepareId = "cp-" + UUID.randomUUID().toString().replace("-", "");
        int inserted = clickTransactionRepository.insertCatalogIfAbsent(
                clickTransId, clickPaydocId, account, driver.getId(), amount, merchantPrepareId, account);
        if (inserted == 1) {
            return prepareSuccess(clickTransId, account, merchantPrepareId);
        }

        // A concurrent identical Prepare is idempotent.  A collision on another
        // Click identifier cannot be reinterpreted as this account/payment.
        return clickTransactionRepository.findByClickTransIdForUpdate(clickTransId)
                .map(tx -> existingPrepare(tx, account, clickPaydocId, amount, clickTransId))
                .orElseGet(() -> error(-6, "Transaction does not exist", clickTransId, account));
    }

    @Transactional
    public Map<String, Object> complete(Map<String, String> params) {
        String account = params.get("merchant_trans_id");
        String clickTransId = params.get("click_trans_id");
        String clickPaydocId = params.get("click_paydoc_id");
        String merchantPrepareId = params.get("merchant_prepare_id");

        if (catalogModePolicy.current() == ClickCatalogMode.OFF) {
            // OFF never creates or credits a catalog intent. Existing PREPARED rows
            // remain durable for reconciliation; operators must drain before OFF.
            return disabled(clickTransId, account);
        }
        ClickTransaction transaction = clickTransactionRepository.findByClickTransIdForUpdate(clickTransId)
                .orElse(null);
        if (transaction != null && !catalogModePolicy.allowsCompleteForExistingStatus(transaction.getStatus())) {
            return disabled(clickTransId, account);
        }
        if (!matches(transaction, account, clickPaydocId, null, merchantPrepareId)) {
            return error(-6, "Transaction does not exist", clickTransId, account);
        }
        if ("CONFIRMED".equals(transaction.getStatus())) {
            return error(-4, "Already paid", clickTransId, account);
        }
        if ("CANCELLED".equals(transaction.getStatus())) {
            return error(-9, "Transaction cancelled", clickTransId, account);
        }

        Long amount = amountToTiyin(params.get("amount"));
        if (amount == null || !amount.equals(transaction.getAmount())) {
            return error(-2, "Summa mos kelmaydi", clickTransId, account);
        }
        Integer clickError = parseInteger(params.get("error"));
        if (clickError == null) {
            return error(-8, "Error in request from Click", clickTransId, account);
        }
        if (clickError != 0) {
            clickTransactionRepository.markCatalogCancelled(clickTransId, clickError);
            return error(-9, "Transaction cancelled", clickTransId, account);
        }

        int claimed = clickTransactionRepository.markCatalogConfirmedIfPrepared(
                clickTransId, account, merchantPrepareId, merchantPrepareId);
        if (claimed != 1) {
            return error(-4, "Already paid", clickTransId, account);
        }

        // The conditional claim and wallet mutation share this transaction.  If
        // the ledger write or balance mutation fails, both changes roll back.
        walletCreditService.credit(transaction.getDriverId(), amount,
                "Click SuperApp TOPUP " + account + " #" + safeId(clickTransId));
        return completeSuccess(clickTransId, account, merchantPrepareId);
    }

    private Map<String, Object> existingPrepare(ClickTransaction transaction, String account,
                                                 String clickPaydocId, Long amount, String clickTransId) {
        if (!matches(transaction, account, clickPaydocId, amount, null)) {
            return error(-6, "Transaction does not exist", clickTransId, account);
        }
        if ("CONFIRMED".equals(transaction.getStatus())) {
            return error(-4, "Already paid", clickTransId, account);
        }
        if ("CANCELLED".equals(transaction.getStatus())) {
            return error(-9, "Transaction cancelled", clickTransId, account);
        }
        return prepareSuccess(clickTransId, account, transaction.getMerchantPrepareId());
    }

    private boolean matches(ClickTransaction transaction, String account, String clickPaydocId,
                            Long amount, String merchantPrepareId) {
        if (transaction == null || !SOURCE.equals(transaction.getPaymentSource())
                || !account.equals(transaction.getMerchantTransId())
                || !account.equals(transaction.getCatalogAccount())
                || !clickPaydocId.equals(transaction.getClickPaydocId())) {
            return false;
        }
        if (amount != null && !amount.equals(transaction.getAmount())) {
            return false;
        }
        return merchantPrepareId == null || merchantPrepareId.equals(transaction.getMerchantPrepareId());
    }

    static Long amountToTiyin(String amount) {
        if (amount == null || !amount.matches("\\d+(\\.\\d{1,2})?")) {
            return null;
        }
        try {
            long tiyin = new BigDecimal(amount).movePointRight(2).longValueExact();
            return tiyin > 0 ? tiyin : null;
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.valueOf(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Map<String, Object> prepareSuccess(String clickTransId, String account, String merchantPrepareId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("click_trans_id", clickTransId);
        response.put("merchant_trans_id", account);
        response.put("merchant_prepare_id", merchantPrepareId);
        response.put("error", 0);
        response.put("error_note", "Success");
        return response;
    }

    private static Map<String, Object> completeSuccess(String clickTransId, String account, String merchantConfirmId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("click_trans_id", clickTransId);
        response.put("merchant_trans_id", account);
        response.put("merchant_confirm_id", merchantConfirmId);
        response.put("error", 0);
        response.put("error_note", "Success");
        return response;
    }

    private static Map<String, Object> error(int code, String note, String clickTransId, String account) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("click_trans_id", clickTransId == null ? "" : clickTransId);
        response.put("merchant_trans_id", account == null ? "" : account);
        response.put("error", code);
        response.put("error_note", note);
        return response;
    }

    private static Map<String, Object> disabled(String clickTransId, String account) {
        // Click acceptance of this business-error mapping remains an external gate.
        return error(-8, "CATALOG_DISABLED", clickTransId, account);
    }

    private static String safeId(String value) {
        return value == null || value.length() <= 12 ? value : value.substring(0, 12) + "...";
    }
}
