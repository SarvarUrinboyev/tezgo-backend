package com.taxi.backend.service;

import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Transaction;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.ClickTransactionRepository;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Click to'lov hardening testlari — pul xavfsizligining yuragi.
 *
 *  1) Imzo (MD5) — TUZATILGAN formula bo'yicha to'g'ri imzo qabul, soxta imzo rad (-1).
 *  2) IDEMPOTENTLIK — bir xil click_trans_id qayta yetkazilsa BALANS IKKI MARTA kreditlanmaydi.
 *  3) Redis OUTAGE — Redis o'chsa ham (lock exception) DB claim orqali aynan bir marta kreditlanadi.
 *  4) Summa mos kelmasa — Click -2.
 *
 * Birlik: balans TIYIN da; Click so'm yuboradi → ×100. (1000 so'm = 100000 tiyin.)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClickPaymentTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private DriverRepository driverRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private ClickTransactionRepository clickTxRepository;

    private PaymentService service;

    private static final String SERVICE_ID = "svc123";
    private static final String SECRET = "secretXYZ";
    private static final long DRIVER_ID = 7L;
    private static final long ORDER_TIYIN = 100_000L; // 1000 so'm
    private String orderId;

    @BeforeEach
    void setUp() throws Exception {
        service = new PaymentService(redis, driverRepository, transactionRepository, clickTxRepository);
        // @Value maydonlari
        ReflectionTestUtils.setField(service, "clickServiceId", SERVICE_ID);
        ReflectionTestUtils.setField(service, "clickSecretKey", SECRET);
        ReflectionTestUtils.setField(service, "clickMerchantId", "m123");
        ReflectionTestUtils.setField(service, "clickCheckoutUrl", "https://my.click.uz/services/pay");
        ReflectionTestUtils.setField(service, "paymeMerchantId", "pm");
        ReflectionTestUtils.setField(service, "paymeSecretKey", "ps");
        ReflectionTestUtils.setField(service, "paymeCheckoutUrl", "https://checkout.paycom.uz");
        ReflectionTestUtils.setField(service, "returnUrl", "tezyol://payment-result");

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);          // getOrder → in-memory cache fallback
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        // Haqiqiy order yaratish (in-memory orderCache ga tushadi)
        Map<String, Object> created = service.createOrder(DRIVER_ID, ORDER_TIYIN);
        orderId = (String) created.get("orderId");

        // Kredit yo'li (creditDriverBalance) uchun stublar
        Driver d = new Driver();
        d.setId(DRIVER_ID);
        User u = new User(); u.setId(DRIVER_ID + 100); d.setUser(u);
        d.setBalance(0L);
        when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(d));
        when(driverRepository.addToBalance(eq(DRIVER_ID), anyLong())).thenReturn(1);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ── Imzo helperlari (Click MD5 formulasi) ──
    private static String md5(String s) throws Exception {
        byte[] h = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : h) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private Map<String, String> completeParams(String clickTransId, String amountSom, boolean validSign) throws Exception {
        String action = "1";
        String signTime = "2026-06-23 10:00:00";
        String prepareId = orderId; // PREPARE javobida merchant_prepare_id = orderId
        // COMPLETE: md5(click_trans_id + service_id + secret + merchant_trans_id + merchant_prepare_id + amount + action + sign_time)
        String sign = md5(clickTransId + SERVICE_ID + SECRET + orderId + prepareId + amountSom + action + signTime);
        if (!validSign) sign = "deadbeef" + sign.substring(8); // buzilgan imzo

        Map<String, String> p = new HashMap<>();
        p.put("click_trans_id", clickTransId);
        p.put("service_id", SERVICE_ID);
        p.put("merchant_trans_id", orderId);
        p.put("merchant_prepare_id", prepareId);
        p.put("amount", amountSom);
        p.put("action", action);
        p.put("error", "0");
        p.put("sign_time", signTime);
        p.put("sign_string", sign);
        return p;
    }

    private Map<String, String> prepareParams(String clickTransId, String amountSom, boolean validSign) throws Exception {
        String action = "0";
        String signTime = "2026-06-23 10:00:00";
        // PREPARE: md5(click_trans_id + service_id + secret + merchant_trans_id + amount + action + sign_time)
        String sign = md5(clickTransId + SERVICE_ID + SECRET + orderId + amountSom + action + signTime);
        if (!validSign) sign = "deadbeef" + sign.substring(8);
        Map<String, String> p = new HashMap<>();
        p.put("click_trans_id", clickTransId);
        p.put("service_id", SERVICE_ID);
        p.put("merchant_trans_id", orderId);
        p.put("amount", amountSom);
        p.put("action", action);
        p.put("error", "0");
        p.put("sign_time", signTime);
        p.put("sign_string", sign);
        return p;
    }

    // ─────────────────────────────────────────────
    // 1) IMZO
    // ─────────────────────────────────────────────

    @Test
    void prepare_validSign_accepted() throws Exception {
        Map<String, Object> res = service.handleClickPrepare(prepareParams("CT-P1", "1000", true));
        assertEquals(0, res.get("error"));
        assertEquals(orderId, res.get("merchant_prepare_id"));
    }

    @Test
    void prepare_tamperedSign_rejected() throws Exception {
        Map<String, Object> res = service.handleClickPrepare(prepareParams("CT-P2", "1000", false));
        assertEquals(-1, res.get("error"));
        verifyNoInteractions(transactionRepository); // hech narsa kreditlanmaydi
    }

    @Test
    void complete_validSign_accepted_andCredits() throws Exception {
        when(clickTxRepository.markConfirmedIfNotAlready(eq("CT-1"), anyString())).thenReturn(1);

        Map<String, Object> res = service.handleClickComplete(completeParams("CT-1", "1000", true));

        assertEquals(0, res.get("error"));
        assertEquals(orderId, res.get("merchant_confirm_id"));
        // Aynan bir marta kreditlandi (1000 so'm = 100000 tiyin)
        verify(driverRepository, times(1)).addToBalance(DRIVER_ID, ORDER_TIYIN);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void complete_tamperedSign_rejected_noCredit() throws Exception {
        Map<String, Object> res = service.handleClickComplete(completeParams("CT-2", "1000", false));
        assertEquals(-1, res.get("error"));
        verify(driverRepository, never()).addToBalance(anyLong(), anyLong());
        verify(transactionRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────
    // 2) IDEMPOTENTLIK — qayta yetkazish ikki marta kreditlamaydi
    // ─────────────────────────────────────────────

    @Test
    void complete_replayedClickTransId_doesNotDoubleCredit() throws Exception {
        // Birinchi chaqiriqda claim g'olib (1), ikkinchisida allaqachon CONFIRMED (0)
        when(clickTxRepository.markConfirmedIfNotAlready(eq("CT-DUP"), anyString()))
                .thenReturn(1)   // 1-callback: kreditlaydi
                .thenReturn(0);  // 2-callback (replay): kreditlamaydi

        Map<String, String> p = completeParams("CT-DUP", "1000", true);
        Map<String, Object> r1 = service.handleClickComplete(p);
        Map<String, Object> r2 = service.handleClickComplete(p); // qayta yetkazish

        assertEquals(0, r1.get("error"));
        assertEquals(0, r2.get("error")); // ikkala javob ham success (Click retry qilmasin)
        // BALANS FAQAT BIR MARTA kreditlandi — double-credit YO'Q
        verify(driverRepository, times(1)).addToBalance(DRIVER_ID, ORDER_TIYIN);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    // ─────────────────────────────────────────────
    // 3) REDIS OUTAGE — DB claim baribir aynan bir marta kreditlaydi
    // ─────────────────────────────────────────────

    @Test
    void complete_redisOutage_stillCreditsExactlyOnce_viaDbClaim() throws Exception {
        // Redis lock olishda exception (Redis o'chgan)
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RuntimeException("Redis unavailable"));
        when(clickTxRepository.markConfirmedIfNotAlready(eq("CT-REDIS"), anyString()))
                .thenReturn(1)   // DB claim hal qiladi
                .thenReturn(0);  // replay → kreditlamaydi

        Map<String, String> p = completeParams("CT-REDIS", "1000", true);
        Map<String, Object> r1 = service.handleClickComplete(p);
        Map<String, Object> r2 = service.handleClickComplete(p); // Redis hali ham o'chgan + replay

        assertEquals(0, r1.get("error"));
        assertEquals(0, r2.get("error"));
        verify(driverRepository, times(1)).addToBalance(DRIVER_ID, ORDER_TIYIN); // aynan bir marta
    }

    // ─────────────────────────────────────────────
    // 4) SUMMA MOS KELMASA — Click -2
    // ─────────────────────────────────────────────

    @Test
    void complete_amountMismatch_returnsMinus2_noCredit() throws Exception {
        when(clickTxRepository.markConfirmedIfNotAlready(anyString(), anyString())).thenReturn(1);
        // Order 1000 so'm, lekin Click 2000 so'm da'vo qilmoqda (imzo 2000 ustidan to'g'ri)
        Map<String, Object> res = service.handleClickComplete(completeParams("CT-AMT", "2000", true));
        assertEquals(-2, res.get("error"));
        verify(driverRepository, never()).addToBalance(anyLong(), anyLong());
    }

    @Test
    void complete_unknownOrder_returnsMinus5() throws Exception {
        // Boshqa orderId — imzoni shu noma'lum order ustidan to'g'ri qilamiz
        String unknown = "nonexistent_order_xyz";
        String signTime = "2026-06-23 10:00:00";
        String sign = md5("CT-X" + SERVICE_ID + SECRET + unknown + unknown + "1000" + "1" + signTime);
        Map<String, String> p = new HashMap<>();
        p.put("click_trans_id", "CT-X");
        p.put("merchant_trans_id", unknown);
        p.put("merchant_prepare_id", unknown);
        p.put("amount", "1000");
        p.put("action", "1");
        p.put("error", "0");
        p.put("sign_time", signTime);
        p.put("sign_string", sign);

        Map<String, Object> res = service.handleClickComplete(p);
        assertEquals(-5, res.get("error"));
        verify(driverRepository, never()).addToBalance(anyLong(), anyLong());
    }
}
