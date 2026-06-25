package com.taxi.backend.service;

import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.enums.TransactionType;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Transaction;
import com.taxi.backend.repository.ClickShopTransactionRepository;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Click ADVANCED SHOP integration service.
 *
 * <p>FULLY ISOLATED from {@link PaymentService} (Merchant API). This service:
 * <ul>
 *   <li>Has its own {@code service_id} + {@code secret_key} config keys
 *       ({@code click.advanced-shop-service-id} / {@code click.advanced-shop-secret-key}).</li>
 *   <li>Uses its own idempotency ledger ({@code click_shop_transactions} table via
 *       {@link ClickShopTransactionRepository}).</li>
 *   <li>Replicates the proven credit pattern (atomic balance update + transactions ledger row)
 *       INLINE — does NOT call into {@link PaymentService#creditDriverBalance} to keep both
 *       integrations fully decoupled. See {@link #creditDriverBalanceInline} javadoc for the
 *       isolation rationale.</li>
 * </ul>
 *
 * <p>ADVANCED SHOP action codes (distinct from Merchant API 0/1):
 * Getinfo=0, Prepare=1, Complete=2, Check=3, Compare=4. Content-Type is
 * {@code application/json; charset=utf-8}.
 *
 * <p>Signature formula (same for ALL ADVANCED SHOP actions, per Click's official docs):
 * <pre>
 *   md5(click_paydoc_id + attempt_trans_id + service_id + SECRET_KEY + params + action + sign_time)
 *   where params = the VALUES of the params object concatenated in their ORIGINAL ORDER
 *   (values only, no keys, no separator).
 * </pre>
 *
 * <p>Error codes returned: 0 Success, -1 SIGN CHECK FAILED, -2 Incorrect amount,
 * -3 Action not found, -4 Already paid, -5 User does not exist, -6 Transaction not found,
 * -7 Failed to update user, -8 Error in request from Click, -9 Transaction cancelled.
 */
@Service
public class AdvancedShopService {

    private static final Logger log = Logger.getLogger(AdvancedShopService.class.getName());

    /**
     * Ordered list of param keys we try (in order) to extract the user-typed identifier
     * for Getinfo. OPEN QUESTION: which key Click's ADVANCED SHOP cabinet uses — extracted
     * defensively until we get a confirmed answer. To switch the canonical key, just
     * reorder this list (no code change needed).
     */
    private static final List<String> IDENTIFIER_KEY_CANDIDATES =
            List.of("account", "contract", "merchant_trans_id", "driver_code", "code");

    /** Where the amount field lives in params (Prepare/Complete). Same defensive list. */
    private static final List<String> AMOUNT_KEY_CANDIDATES =
            List.of("amount", "summa");

    @Value("${click.advanced-shop-service-id:}")
    private String shopServiceId;

    @Value("${click.advanced-shop-secret-key:}")
    private String shopSecretKey;

    private final DriverRepository driverRepository;
    private final TransactionRepository transactionRepository;
    private final ClickShopTransactionRepository shopLedger;

    public AdvancedShopService(DriverRepository driverRepository,
                               TransactionRepository transactionRepository,
                               ClickShopTransactionRepository shopLedger) {
        this.driverRepository = driverRepository;
        this.transactionRepository = transactionRepository;
        this.shopLedger = shopLedger;
    }

    // ─────────────────────────────────────────────
    // ACTION DISPATCH
    // ─────────────────────────────────────────────

    /**
     * Top-level dispatch. Reads {@code action} from the body, verifies the signature,
     * delegates to the per-action handler.
     */
    public Map<String, Object> dispatch(Map<String, Object> body) {
        int action = asInt(body.get("action"), -1);
        // Sign check is the first hard gate for ALL actions.
        if (!verifySignature(body)) {
            return error(-1, "SIGN CHECK FAILED", body);
        }
        return switch (action) {
            case 0 -> handleGetinfo(body);
            case 1 -> handlePrepare(body);
            case 2 -> handleComplete(body);
            case 3 -> handleCheck(body);
            case 4 -> handleCompare(body);
            default -> error(-3, "Action not found", body);
        };
    }

    // ─────────────────────────────────────────────
    // GETINFO (action=0)
    // ─────────────────────────────────────────────

    /**
     * User typed a driver identifier in the Click app — we return their FIO.
     * Response on success: {@code {"error":0, "error_note":"Success", "params":{"fio": "<full name>"}}}
     * <p>NOTE: ONLY {@code fio} is returned — no car number, no address. Click app shows just the name
     * for the user to confirm "I'm paying the right driver" before entering the amount.
     */
    Map<String, Object> handleGetinfo(Map<String, Object> body) {
        Map<String, Object> params = paramsOf(body);
        String typedId = readFirst(params, IDENTIFIER_KEY_CANDIDATES);
        if (typedId == null || typedId.isBlank()) {
            return error(-8, "Error in request from Click", body);
        }
        Optional<Driver> opt = driverRepository.findByDriverCode(typedId.trim());
        if (opt.isEmpty()) {
            return error(-5, "User does not exist by params", body);
        }
        Driver driver = opt.get();
        if (driver.getStatus() != DriverStatus.ACTIVE) {
            return error(-5, "User does not exist by params", body);
        }
        String fio = driver.getUser() != null ? driver.getUser().getName() : null;
        if (fio == null || fio.isBlank()) {
            return error(-5, "User does not exist by params", body);
        }
        Map<String, Object> ok = baseResponse(body);
        ok.put("error", 0);
        ok.put("error_note", "Success");
        ok.put("params", Map.of("fio", fio));
        return ok;
    }

    // ─────────────────────────────────────────────
    // PREPARE (action=1)
    // ─────────────────────────────────────────────

    /**
     * Click confirms the driver still exists and is payable. Returns our {@code merchant_prepare_id}
     * (= the click_paydoc_id, mirroring the Merchant API pattern of echoing the natural key).
     * Records a PREPARED row in {@code click_shop_transactions} for the eventual Complete.
     */
    @Transactional
    Map<String, Object> handlePrepare(Map<String, Object> body) {
        String clickPaydocId = asString(body.get("click_paydoc_id"));
        String attemptTransId = asString(body.get("attempt_trans_id"));
        Map<String, Object> params = paramsOf(body);
        String typedId = readFirst(params, IDENTIFIER_KEY_CANDIDATES);
        Long amountTiyin = parseAmountTiyin(params);

        if (clickPaydocId == null || typedId == null || amountTiyin == null) {
            return error(-8, "Error in request from Click", body);
        }

        Optional<Driver> opt = driverRepository.findByDriverCode(typedId.trim());
        if (opt.isEmpty() || opt.get().getStatus() != DriverStatus.ACTIVE) {
            return error(-5, "User does not exist by params", body);
        }
        Driver driver = opt.get();

        // Use click_paydoc_id as the merchant_prepare_id — echoes the natural key Click
        // already correlates by. (Same pattern as Merchant API at PaymentService:373.)
        String merchantPrepareId = clickPaydocId;
        try {
            shopLedger.insertIfAbsent(clickPaydocId, attemptTransId, driver.getId(),
                    amountTiyin, 1, merchantPrepareId);
        } catch (Exception e) {
            log.warning("Shop prepare ledger insert skipped: " + e.getMessage());
        }

        Map<String, Object> ok = baseResponse(body);
        ok.put("merchant_prepare_id", merchantPrepareId);
        ok.put("error", 0);
        ok.put("error_note", "Success");
        ok.put("params", params);
        return ok;
    }

    // ─────────────────────────────────────────────
    // COMPLETE (action=2)
    // ─────────────────────────────────────────────

    /**
     * Click confirms the payment was charged on their side. We credit the driver's balance —
     * EXACTLY ONCE, regardless of retries — using the durable ledger claim pattern proven by
     * the Merchant API.
     *
     * <p>{@code status} field in the response:
     * 0 = not yet processed (Click retries), 1 = failed (Click cancels), 2 = success.
     */
    @Transactional
    Map<String, Object> handleComplete(Map<String, Object> body) {
        String clickPaydocId = asString(body.get("click_paydoc_id"));
        String attemptTransId = asString(body.get("attempt_trans_id"));
        String prepareId = asString(body.get("merchant_prepare_id"));
        Map<String, Object> params = paramsOf(body);
        String typedId = readFirst(params, IDENTIFIER_KEY_CANDIDATES);
        Long callbackAmountTiyin = parseAmountTiyin(params);

        if (clickPaydocId == null || typedId == null || callbackAmountTiyin == null) {
            return failed(body, -8, "Error in request from Click");
        }

        Optional<Driver> opt = driverRepository.findByDriverCode(typedId.trim());
        if (opt.isEmpty() || opt.get().getStatus() != DriverStatus.ACTIVE) {
            return failed(body, -5, "User does not exist by params");
        }
        Driver driver = opt.get();

        // Defensive ledger insert (in case PREPARE was skipped). NO-OP if already present.
        try {
            shopLedger.insertIfAbsent(clickPaydocId, attemptTransId, driver.getId(),
                    callbackAmountTiyin, 2, prepareId);
        } catch (Exception e) {
            log.warning("Shop complete ledger insert skipped: " + e.getMessage());
        }

        // ATOMIC CLAIM. Only the row-update winner credits balance.
        int claimed = shopLedger.markConfirmedIfNotAlready(clickPaydocId, clickPaydocId);
        if (claimed == 1) {
            creditDriverBalanceInline(driver.getId(), callbackAmountTiyin,
                    "Click SHOP to'lovi #" + clickPaydocId);
            log.info("ADVANCED SHOP credited: driverId=" + driver.getId()
                    + " +" + callbackAmountTiyin + " tiyin paydoc=" + clickPaydocId);
        } else {
            log.info("ADVANCED SHOP duplicate Complete ignored (idempotent): paydoc=" + clickPaydocId);
        }

        Map<String, Object> ok = baseResponse(body);
        ok.put("merchant_confirm_id", clickPaydocId);
        ok.put("error", 0);
        ok.put("error_note", "Success");
        ok.put("status", 2);
        ok.put("params", params);
        return ok;
    }

    /** Helper for COMPLETE failure path — also marks ledger CANCELLED (if not already CONFIRMED). */
    private Map<String, Object> failed(Map<String, Object> body, int code, String note) {
        String clickPaydocId = asString(body.get("click_paydoc_id"));
        if (clickPaydocId != null) {
            try { shopLedger.markCancelled(clickPaydocId, code); } catch (Exception ignored) {}
        }
        Map<String, Object> resp = error(code, note, body);
        resp.put("status", 1); // 1 = failed (Click should cancel)
        return resp;
    }

    // ─────────────────────────────────────────────
    // CHECK (action=3) / COMPARE (action=4)
    // ─────────────────────────────────────────────

    /**
     * STUB. The Click docs for these two actions were not fully extracted in Phase 1 discovery.
     * Returns a well-formed Click response so the integration doesn't 500 if Click invokes them
     * during reconciliation. FLAG in report: confirm the exact request/response shape with Click
     * before relying on these for payment-status reconciliation.
     */
    Map<String, Object> handleCheck(Map<String, Object> body) {
        String clickPaydocId = asString(body.get("click_paydoc_id"));
        Optional<com.taxi.backend.model.ClickShopTransaction> row =
                clickPaydocId != null ? shopLedger.findByClickPaydocId(clickPaydocId) : Optional.empty();

        Map<String, Object> ok = baseResponse(body);
        ok.put("error", 0);
        ok.put("error_note", "Success");
        // Best-effort status: 2 = CONFIRMED in our ledger, 0 = unknown/pending, 1 = cancelled.
        int status = row
                .map(r -> switch (r.getStatus()) {
                    case "CONFIRMED" -> 2;
                    case "CANCELLED" -> 1;
                    default -> 0;
                })
                .orElse(0);
        ok.put("status", status);
        return ok;
    }

    /**
     * STUB (same caveat as {@link #handleCheck}). Reconciliation/compare endpoint.
     */
    Map<String, Object> handleCompare(Map<String, Object> body) {
        Map<String, Object> ok = baseResponse(body);
        ok.put("error", 0);
        ok.put("error_note", "Success");
        return ok;
    }

    // ─────────────────────────────────────────────
    // BALANCE CREDIT (replicated inline for isolation — see class javadoc)
    // ─────────────────────────────────────────────

    /**
     * Atomic balance credit + transactions ledger row.
     * <p>REPLICATED from {@link PaymentService#creditDriverBalance} on purpose:
     * keeps AdvancedShopService self-contained, so any future change to the Merchant API's
     * credit path cannot accidentally affect ADVANCED SHOP and vice versa. The two flows
     * each have their own copy of the same proven Decimal/tiyin handling.
     */
    @Transactional
    void creditDriverBalanceInline(Long driverId, Long amount, String description) {
        driverRepository.findById(driverId).ifPresent(driver -> {
            driverRepository.addToBalance(driver.getId(), amount);
            driverRepository.flush();
            Driver updated = driverRepository.findById(driverId).orElse(driver);
            long balanceAfter = updated.getBalance();
            long balanceBefore = balanceAfter - amount;

            Transaction tx = new Transaction();
            tx.setDriver(driver);
            tx.setType(TransactionType.TOPUP);
            tx.setAmount(amount);
            tx.setBalanceBefore(balanceBefore);
            tx.setBalanceAfter(balanceAfter);
            tx.setDescription(description);
            transactionRepository.save(tx);
        });
    }

    // ─────────────────────────────────────────────
    // SIGNATURE
    // ─────────────────────────────────────────────

    /**
     * Verifies the ADVANCED SHOP signature.
     * <p>Formula:
     * <pre>md5(click_paydoc_id + attempt_trans_id + service_id + SECRET_KEY + params + action + sign_time)</pre>
     * where {@code params} is the concatenation of param VALUES in their original (insertion) order.
     * Jackson deserializes JSON objects into {@link LinkedHashMap} which preserves insertion order,
     * so iterating {@code params.values()} gives us the original wire order.
     */
    boolean verifySignature(Map<String, Object> body) {
        if (shopSecretKey == null || shopSecretKey.isBlank()) {
            // Config not yet provided by Click — refuse all signatures rather than silently allowing.
            return false;
        }
        String expected = asString(body.get("sign_string"));
        if (expected == null || expected.isBlank()) return false;
        String mine = buildSignature(body);
        return constantTimeEquals(mine, expected);
    }

    /** Builds the expected signature for a given body. Visible for tests. */
    String buildSignature(Map<String, Object> body) {
        String clickPaydocId  = nullSafe(asString(body.get("click_paydoc_id")));
        String attemptTransId = nullSafe(asString(body.get("attempt_trans_id")));
        String serviceId      = nullSafe(asString(body.get("service_id")));
        String action         = nullSafe(asString(body.get("action")));
        String signTime       = nullSafe(asString(body.get("sign_time")));
        String paramsConcat   = concatParamValues(paramsOf(body));
        return md5(clickPaydocId + attemptTransId + serviceId + shopSecretKey
                + paramsConcat + action + signTime);
    }

    /** Concatenates params values in their original insertion order (values only, no separator). */
    static String concatParamValues(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Object v : params.values()) {
            sb.append(v == null ? "" : String.valueOf(v));
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────
    // RESPONSE HELPERS — ADVANCED SHOP shape (do NOT reuse Merchant API helpers)
    // ─────────────────────────────────────────────

    private Map<String, Object> baseResponse(Map<String, Object> body) {
        LinkedHashMap<String, Object> resp = new LinkedHashMap<>();
        resp.put("click_paydoc_id", body.get("click_paydoc_id"));
        resp.put("attempt_trans_id", body.get("attempt_trans_id"));
        return resp;
    }

    private Map<String, Object> error(int code, String note, Map<String, Object> body) {
        Map<String, Object> resp = baseResponse(body);
        resp.put("error", code);
        resp.put("error_note", note);
        return resp;
    }

    // ─────────────────────────────────────────────
    // INPUT HELPERS
    // ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Object> paramsOf(Map<String, Object> body) {
        Object p = body.get("params");
        if (p instanceof Map) return (Map<String, Object>) p;
        return new LinkedHashMap<>();
    }

    private static String readFirst(Map<String, Object> params, List<String> keys) {
        for (String k : keys) {
            Object v = params.get(k);
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty()) return s;
            }
        }
        // Defensive fallback: if Click uses a key we didn't anticipate, take the first
        // non-blank string value in params (preserves correctness even if our key list is stale).
        for (Object v : params.values()) {
            if (v instanceof String s && !s.isBlank()) return s.trim();
        }
        return null;
    }

    /** Reads the amount from params (Click sends UZS so'm — convert to tiyin via BigDecimal). */
    static Long parseAmountTiyin(Map<String, Object> params) {
        for (String k : AMOUNT_KEY_CANDIDATES) {
            Object v = params.get(k);
            if (v == null) continue;
            try {
                BigDecimal som = new BigDecimal(String.valueOf(v));
                return som.movePointRight(2).longValueExact();
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static int asInt(Object o, int dflt) {
        if (o == null) return dflt;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o).trim()); } catch (Exception e) { return dflt; }
    }

    // ─────────────────────────────────────────────
    // CRYPTO HELPERS (replicated from PaymentService — pure functions only)
    // ─────────────────────────────────────────────

    static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format(Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
