package com.taxi.backend.service;

import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.model.ClickTransaction;
import com.taxi.backend.model.Driver;
import com.taxi.backend.repository.ClickTransactionRepository;
import com.taxi.backend.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClickCatalogPaymentServiceTest {

    private DriverRepository driverRepository;
    private ClickTransactionRepository transactionRepository;
    private ClickWalletCreditService walletCreditService;
    private ClickCatalogModePolicy catalogModePolicy;
    private ClickCatalogPaymentService service;

    @BeforeEach
    void setUp() {
        driverRepository = mock(DriverRepository.class);
        transactionRepository = mock(ClickTransactionRepository.class);
        walletCreditService = mock(ClickWalletCreditService.class);
        catalogModePolicy = new ClickCatalogModePolicy();
        org.springframework.test.util.ReflectionTestUtils.setField(catalogModePolicy, "configuredMode", "ON");
        service = new ClickCatalogPaymentService(driverRepository, transactionRepository, walletCreditService,
                catalogModePolicy);
    }

    @Test
    void prepareCreatesDurableCatalogIntentButNeverCredits() {
        Driver driver = activeDriver(7L);
        when(transactionRepository.findByClickTransIdForUpdate("CT-1")).thenReturn(Optional.empty());
        when(transactionRepository.findByClickPaydocId("PD-1")).thenReturn(Optional.empty());
        when(driverRepository.findByDriverCode("TZ-0005")).thenReturn(Optional.of(driver));
        when(transactionRepository.insertCatalogIfAbsent(eq("CT-1"), eq("PD-1"), eq("TZ-0005"), eq(7L),
                eq(100_000L), anyString(), eq("TZ-0005"))).thenReturn(1);

        Map<String, Object> response = service.prepare(callback("CT-1", "PD-1", "TZ-0005", "1000"));

        assertEquals(0, response.get("error"));
        assertTrue(((String) response.get("merchant_prepare_id")).startsWith("cp-"));
        verify(walletCreditService, never()).credit(anyLong(), anyLong(), anyString());
    }

    @Test
    void duplicatePrepareReturnsOriginalPrepareIdAndDistinctCatalogPaymentIsIndependent() {
        ClickTransaction existing = prepared("CT-1", "PD-1", "TZ-0005", "cp-original", 100_000L);
        when(transactionRepository.findByClickTransIdForUpdate("CT-1")).thenReturn(Optional.of(existing));

        Map<String, Object> duplicate = service.prepare(callback("CT-1", "PD-1", "TZ-0005", "1000"));
        assertEquals("cp-original", duplicate.get("merchant_prepare_id"));

        when(transactionRepository.findByClickTransIdForUpdate("CT-2")).thenReturn(Optional.empty());
        when(transactionRepository.findByClickPaydocId("PD-2")).thenReturn(Optional.empty());
        when(driverRepository.findByDriverCode("TZ-0005")).thenReturn(Optional.of(activeDriver(7L)));
        when(transactionRepository.insertCatalogIfAbsent(eq("CT-2"), eq("PD-2"), eq("TZ-0005"), eq(7L),
                eq(5_000_000L), anyString(), eq("TZ-0005"))).thenReturn(1);
        Map<String, Object> distinct = service.prepare(callback("CT-2", "PD-2", "TZ-0005", "50000"));

        assertEquals(0, distinct.get("error"));
        assertNotEquals(duplicate.get("merchant_prepare_id"), distinct.get("merchant_prepare_id"));
        verify(walletCreditService, never()).credit(anyLong(), anyLong(), anyString());
    }

    @Test
    void completeClaimsOnceAndCreditsSharedWalletExactlyOnce() {
        ClickTransaction transaction = prepared("CT-1", "PD-1", "TZ-0005", "cp-1", 100_000L);
        when(transactionRepository.findByClickTransIdForUpdate("CT-1")).thenReturn(Optional.of(transaction));
        when(transactionRepository.markCatalogConfirmedIfPrepared("CT-1", "TZ-0005", "cp-1", "cp-1"))
                .thenReturn(1, 0);

        Map<String, String> callback = callback("CT-1", "PD-1", "TZ-0005", "1000");
        callback.put("merchant_prepare_id", "cp-1");
        callback.put("error", "0");
        Map<String, Object> first = service.complete(callback);
        Map<String, Object> duplicate = service.complete(callback);

        assertEquals(0, first.get("error"));
        assertEquals(-4, duplicate.get("error"));
        verify(walletCreditService, times(1)).credit(eq(7L), eq(100_000L), contains("TZ-0005"));
    }

    @Test
    void failedCompleteInvalidAmountAndMissingPreparedIntentNeverCredit() {
        ClickTransaction transaction = prepared("CT-1", "PD-1", "TZ-0005", "cp-1", 100_000L);
        when(transactionRepository.findByClickTransIdForUpdate("CT-1")).thenReturn(Optional.of(transaction));
        Map<String, String> failed = callback("CT-1", "PD-1", "TZ-0005", "1000");
        failed.put("merchant_prepare_id", "cp-1");
        failed.put("error", "-501");
        assertEquals(-9, service.complete(failed).get("error"));
        verify(transactionRepository).markCatalogCancelled("CT-1", -501);

        Map<String, String> wrongAmount = callback("CT-1", "PD-1", "TZ-0005", "1000.001");
        wrongAmount.put("merchant_prepare_id", "cp-1");
        wrongAmount.put("error", "0");
        assertEquals(-2, service.complete(wrongAmount).get("error"));
        verify(walletCreditService, never()).credit(anyLong(), anyLong(), anyString());
    }

    @Test
    void offBlocksNewPrepareAndCompleteWithoutAnyWalletMutation() {
        org.springframework.test.util.ReflectionTestUtils.setField(catalogModePolicy, "configuredMode", "OFF");

        Map<String, Object> prepare = service.prepare(callback("CT-OFF", "PD-OFF", "TZ-0005", "1000"));
        Map<String, String> completeParams = callback("CT-OFF", "PD-OFF", "TZ-0005", "1000");
        completeParams.put("merchant_prepare_id", "cp-off");
        completeParams.put("error", "0");
        Map<String, Object> complete = service.complete(completeParams);

        assertEquals(-8, prepare.get("error"));
        assertEquals(-8, complete.get("error"));
        verifyNoInteractions(driverRepository, transactionRepository, walletCreditService);
    }

    @Test
    void drainRejectsNewPrepareButSettlesOnlyAnExistingPreparedTransaction() {
        org.springframework.test.util.ReflectionTestUtils.setField(catalogModePolicy, "configuredMode", "DRAIN");
        when(transactionRepository.findByClickTransIdForUpdate("CT-DRAIN-NEW")).thenReturn(Optional.empty());
        assertEquals(-8, service.prepare(callback("CT-DRAIN-NEW", "PD-DRAIN-NEW", "TZ-0005", "1000")).get("error"));

        ClickTransaction transaction = prepared("CT-DRAIN", "PD-DRAIN", "TZ-0005", "cp-drain", 100_000L);
        when(transactionRepository.findByClickTransIdForUpdate("CT-DRAIN")).thenReturn(Optional.of(transaction));
        when(transactionRepository.markCatalogConfirmedIfPrepared("CT-DRAIN", "TZ-0005", "cp-drain", "cp-drain"))
                .thenReturn(1);
        Map<String, String> complete = callback("CT-DRAIN", "PD-DRAIN", "TZ-0005", "1000");
        complete.put("merchant_prepare_id", "cp-drain");
        complete.put("error", "0");

        assertEquals(0, service.complete(complete).get("error"));
        verify(walletCreditService).credit(eq(7L), eq(100_000L), contains("TZ-0005"));
    }

    private static Map<String, String> callback(String clickTransId, String paydocId, String account, String amount) {
        Map<String, String> params = new HashMap<>();
        params.put("click_trans_id", clickTransId);
        params.put("click_paydoc_id", paydocId);
        params.put("merchant_trans_id", account);
        params.put("amount", amount);
        return params;
    }

    private static Driver activeDriver(long id) {
        Driver driver = new Driver();
        driver.setId(id);
        driver.setStatus(DriverStatus.ACTIVE);
        return driver;
    }

    private static ClickTransaction prepared(String clickTransId, String paydocId, String account,
                                             String prepareId, long amount) {
        ClickTransaction transaction = new ClickTransaction();
        transaction.setClickTransId(clickTransId);
        transaction.setClickPaydocId(paydocId);
        transaction.setMerchantTransId(account);
        transaction.setCatalogAccount(account);
        transaction.setPaymentSource(ClickCatalogPaymentService.SOURCE);
        transaction.setDriverId(7L);
        transaction.setAmount(amount);
        transaction.setStatus("PREPARED");
        transaction.setMerchantPrepareId(prepareId);
        return transaction;
    }
}
