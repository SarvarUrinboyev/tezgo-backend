package com.taxi.backend.service;

import com.taxi.backend.repository.ClickPaymentOrderRepository;
import com.taxi.backend.repository.ClickTransactionRepository;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class PaymentCatalogRoutingTest {

    private static final String SERVICE_ID = "105926";
    private static final String SECRET = "secretXYZ";
    private ClickCatalogPaymentService catalogService;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        catalogService = mock(ClickCatalogPaymentService.class);
        DriverRepository drivers = mock(DriverRepository.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        service = new PaymentService(mock(StringRedisTemplate.class), drivers, transactions,
                mock(ClickTransactionRepository.class), mock(ClickPaymentOrderRepository.class),
                new ClickWalletCreditService(drivers, transactions), catalogService);
        ReflectionTestUtils.setField(service, "clickServiceId", SERVICE_ID);
        ReflectionTestUtils.setField(service, "clickSecretKey", SECRET);
    }

    @Test
    void signedCatalogPrepareUsesCanonicalCallbackAndNeverAdvancedShopService() throws Exception {
        Map<String, String> callback = prepare("CT-CATALOG", "TZ-0005", "1000", true);
        when(catalogService.prepare(callback)).thenReturn(Map.of("error", 0, "merchant_prepare_id", "cp-1"));

        Map<String, Object> response = service.handleClickPrepare(callback);

        assertEquals(0, response.get("error"));
        verify(catalogService).prepare(callback);
    }

    @Test
    void invalidCatalogSignatureCannotReachCatalogStateMachine() throws Exception {
        Map<String, String> callback = prepare("CT-CATALOG-BAD", "TZ-0005", "1000", false);

        assertEquals(-1, service.handleClickPrepare(callback).get("error"));
        verifyNoInteractions(catalogService);
    }

    @Test
    void signedCatalogCompleteUsesTheSameCanonicalCallbackContract() throws Exception {
        Map<String, String> callback = complete("CT-CATALOG-COMPLETE", "TZ-0005", "cp-1", "1000", true);
        when(catalogService.complete(callback)).thenReturn(Map.of("error", 0, "merchant_confirm_id", "cp-1"));

        Map<String, Object> response = service.handleClickComplete(callback);

        assertEquals(0, response.get("error"));
        verify(catalogService).complete(callback);
    }

    @Test
    void signedAppOrderReferenceNeverFallsIntoTheCatalogStateMachine() throws Exception {
        Map<String, String> callback = prepare("CT-APP-ONLY", "c2af006c7b11406b9202", "1000", true);

        assertEquals(-5, service.handleClickPrepare(callback).get("error"));
        verifyNoInteractions(catalogService);
    }

    private static Map<String, String> prepare(String clickTransId, String account, String amount, boolean valid) throws Exception {
        String action = "0";
        String signTime = "2026-07-14 12:00:00";
        String sign = md5(clickTransId + SERVICE_ID + SECRET + account + amount + action + signTime);
        Map<String, String> callback = new HashMap<>();
        callback.put("click_trans_id", clickTransId);
        callback.put("click_paydoc_id", "PD-" + clickTransId);
        callback.put("service_id", SERVICE_ID);
        callback.put("merchant_trans_id", account);
        callback.put("amount", amount);
        callback.put("action", action);
        callback.put("error", "0");
        callback.put("error_note", "Success");
        callback.put("sign_time", signTime);
        callback.put("sign_string", valid ? sign : "deadbeef");
        return callback;
    }

    private static Map<String, String> complete(String clickTransId, String account, String prepareId,
                                                String amount, boolean valid) throws Exception {
        String action = "1";
        String signTime = "2026-07-14 12:00:00";
        String sign = md5(clickTransId + SERVICE_ID + SECRET + account + prepareId + amount + action + signTime);
        Map<String, String> callback = new HashMap<>();
        callback.put("click_trans_id", clickTransId);
        callback.put("click_paydoc_id", "PD-" + clickTransId);
        callback.put("service_id", SERVICE_ID);
        callback.put("merchant_trans_id", account);
        callback.put("merchant_prepare_id", prepareId);
        callback.put("amount", amount);
        callback.put("action", action);
        callback.put("error", "0");
        callback.put("error_note", "Success");
        callback.put("sign_time", signTime);
        callback.put("sign_string", valid ? sign : "deadbeef");
        return callback;
    }

    private static String md5(String input) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder value = new StringBuilder();
        for (byte item : digest) value.append(String.format("%02x", item));
        return value.toString();
    }
}
