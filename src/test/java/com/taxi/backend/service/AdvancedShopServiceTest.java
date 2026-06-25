package com.taxi.backend.service;

import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.ClickShopTransactionRepository;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AdvancedShopService}.
 *
 * <p>Covers the four highest-risk behaviours:
 * <ul>
 *   <li>Signature formula — values-in-original-order concat (LOCKED by an explicit test
 *       so we'd catch the moment Click's actual rule diverges from our implementation).</li>
 *   <li>Getinfo branches — active/unknown/blocked-or-suspended/missing-name.</li>
 *   <li>Prepare branches — valid sign + driver active, bad sign.</li>
 *   <li>Complete branches — credit ONCE on first, idempotent NO-CREDIT on duplicate.</li>
 * </ul>
 */
class AdvancedShopServiceTest {

    private static final String SERVICE_ID = "12345";
    private static final String SECRET = "supersecret";

    private DriverRepository driverRepo;
    private TransactionRepository txRepo;
    private ClickShopTransactionRepository ledger;
    private AdvancedShopService service;

    @BeforeEach
    void setup() {
        driverRepo = mock(DriverRepository.class);
        txRepo = mock(TransactionRepository.class);
        ledger = mock(ClickShopTransactionRepository.class);
        service = new AdvancedShopService(driverRepo, txRepo, ledger);
        ReflectionTestUtils.setField(service, "shopServiceId", SERVICE_ID);
        ReflectionTestUtils.setField(service, "shopSecretKey", SECRET);
    }

    // ─── helpers ───

    /** Build a JSON-like body. Uses LinkedHashMap so params iteration order matches insertion order. */
    private static Map<String, Object> body(String paydoc, String attempt, int action,
                                            String signTime, Map<String, Object> params) {
        LinkedHashMap<String, Object> b = new LinkedHashMap<>();
        b.put("click_paydoc_id", paydoc);
        b.put("attempt_trans_id", attempt);
        b.put("service_id", SERVICE_ID);
        b.put("action", action);
        b.put("sign_time", signTime);
        b.put("params", params);
        return b;
    }

    /** Recreate the spec's signature outside the SUT — independent oracle to prove the formula. */
    private static String oracleSign(String paydoc, String attempt, String serviceId, String secret,
                                     Map<String, Object> params, int action, String signTime) {
        StringBuilder paramsConcat = new StringBuilder();
        for (Object v : params.values()) paramsConcat.append(v == null ? "" : String.valueOf(v));
        String pre = paydoc + attempt + serviceId + secret + paramsConcat + action + signTime;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(pre.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte by : hash) sb.append(String.format(Locale.ROOT, "%02x", by));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void sign(Map<String, Object> b) {
        b.put("sign_string", oracleSign(
                (String) b.get("click_paydoc_id"),
                (String) b.get("attempt_trans_id"),
                (String) b.get("service_id"),
                SECRET,
                (Map<String, Object>) b.get("params"),
                ((Number) b.get("action")).intValue(),
                (String) b.get("sign_time")
        ));
    }

    private static Driver activeDriverWithCode(long id, String code, String fio) {
        User u = new User();
        u.setName(fio);
        Driver d = new Driver();
        d.setId(id);
        d.setDriverCode(code);
        d.setStatus(DriverStatus.ACTIVE);
        d.setUser(u);
        d.setBalance(0L);
        return d;
    }

    // ─── 1. Signature formula LOCK (the highest-risk unknown — params order) ───

    @Test
    void signatureFormulaLocked_paramsValuesInInsertionOrder() {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        params.put("amount", "1000");

        // Build the in-SUT signature.
        Map<String, Object> b = body("PAYDOC-1", "ATT-1", 1, "2026-06-25 12:00:00", params);
        String mine = service.buildSignature(b);
        String oracle = oracleSign("PAYDOC-1", "ATT-1", SERVICE_ID, SECRET, params, 1, "2026-06-25 12:00:00");
        assertEquals(oracle, mine, "MD5 must match the values-in-insertion-order concat oracle");

        // Reordering params changes the signature — proves order matters.
        LinkedHashMap<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("amount", "1000");
        reordered.put("account", "TZ-0005");
        Map<String, Object> bReordered = body("PAYDOC-1", "ATT-1", 1, "2026-06-25 12:00:00", reordered);
        String mineReordered = service.buildSignature(bReordered);
        assertNotEquals(mine, mineReordered, "Different params iteration order must produce a different signature");
    }

    @Test
    void verifySignature_acceptsCorrectSign_rejectsTampered() {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        Map<String, Object> b = body("P", "A", 0, "2026-06-25 12:00:00", params);
        sign(b);
        assertTrue(service.verifySignature(b));

        b.put("sign_string", "deadbeef00000000000000000000beef");
        assertFalse(service.verifySignature(b));
    }

    @Test
    void verifySignature_refusesWhenSecretMissing() {
        ReflectionTestUtils.setField(service, "shopSecretKey", "");
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        Map<String, Object> b = body("P", "A", 0, "2026-06-25 12:00:00", params);
        b.put("sign_string", "anything");
        assertFalse(service.verifySignature(b),
                "Empty secret -> all signatures rejected (fail-closed default until Click registers the service)");
    }

    // ─── 2. Getinfo ───

    @Test
    void getinfo_activeDriver_returnsFio() {
        Driver d = activeDriverWithCode(5L, "TZ-0005", "Aliyev Akmal");
        when(driverRepo.findByDriverCode("TZ-0005")).thenReturn(Optional.of(d));

        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        Map<String, Object> b = body("P", "A", 0, "T", params);
        sign(b);

        Map<String, Object> resp = service.dispatch(b);
        assertEquals(0, resp.get("error"));
        assertEquals("Success", resp.get("error_note"));
        assertEquals(Map.of("fio", "Aliyev Akmal"), resp.get("params"));
    }

    @Test
    void getinfo_unknownCode_returnsMinusFive() {
        when(driverRepo.findByDriverCode("TZ-9999")).thenReturn(Optional.empty());
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-9999");
        Map<String, Object> b = body("P", "A", 0, "T", params);
        sign(b);

        Map<String, Object> resp = service.dispatch(b);
        assertEquals(-5, resp.get("error"));
    }

    @Test
    void getinfo_blockedDriver_returnsMinusFive() {
        Driver d = activeDriverWithCode(5L, "TZ-0005", "Blocked Person");
        d.setStatus(DriverStatus.BLOCKED);
        when(driverRepo.findByDriverCode("TZ-0005")).thenReturn(Optional.of(d));

        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        Map<String, Object> b = body("P", "A", 0, "T", params);
        sign(b);

        assertEquals(-5, service.dispatch(b).get("error"));
    }

    @Test
    void getinfo_suspendedDriver_returnsMinusFive() {
        Driver d = activeDriverWithCode(5L, "TZ-0005", "X");
        d.setStatus(DriverStatus.SUSPENDED);
        when(driverRepo.findByDriverCode("TZ-0005")).thenReturn(Optional.of(d));

        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        Map<String, Object> b = body("P", "A", 0, "T", params);
        sign(b);

        assertEquals(-5, service.dispatch(b).get("error"));
    }

    @Test
    void getinfo_badSign_returnsMinusOne_andDoesNotHitRepo() {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        Map<String, Object> b = body("P", "A", 0, "T", params);
        b.put("sign_string", "wrong");

        Map<String, Object> resp = service.dispatch(b);
        assertEquals(-1, resp.get("error"));
        assertEquals("SIGN CHECK FAILED", resp.get("error_note"));
        verify(driverRepo, never()).findByDriverCode(any());
    }

    @Test
    void getinfo_missingIdentifier_returnsMinusEight() {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        // No identifier-shaped key at all.
        Map<String, Object> b = body("P", "A", 0, "T", params);
        sign(b);

        assertEquals(-8, service.dispatch(b).get("error"));
    }

    // ─── 3. Prepare ───

    @Test
    void prepare_validSign_activeDriver_returnsSuccessAndInsertsLedger() {
        Driver d = activeDriverWithCode(5L, "TZ-0005", "Aliyev Akmal");
        when(driverRepo.findByDriverCode("TZ-0005")).thenReturn(Optional.of(d));
        when(ledger.insertIfAbsent(anyString(), anyString(), anyLong(), anyLong(), eq(1), anyString()))
                .thenReturn(1);

        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        params.put("amount", "1000");
        Map<String, Object> b = body("PAYDOC-1", "ATT-1", 1, "T", params);
        sign(b);

        Map<String, Object> resp = service.dispatch(b);
        assertEquals(0, resp.get("error"));
        assertEquals("PAYDOC-1", resp.get("merchant_prepare_id"));
        verify(ledger).insertIfAbsent(eq("PAYDOC-1"), eq("ATT-1"), eq(5L), eq(100_000L),
                eq(1), eq("PAYDOC-1"));
    }

    @Test
    void prepare_badSign_returnsMinusOne_noLedgerWrite() {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        params.put("amount", "1000");
        Map<String, Object> b = body("PAYDOC-1", "ATT-1", 1, "T", params);
        b.put("sign_string", "wrong");

        Map<String, Object> resp = service.dispatch(b);
        assertEquals(-1, resp.get("error"));
        verifyNoInteractions(ledger);
        verify(driverRepo, never()).findByDriverCode(any());
    }

    // ─── 4. Complete — money-safety: credit ONCE, idempotent on retry ───

    @Test
    void complete_firstCallCreditsOnce() {
        Driver d = activeDriverWithCode(5L, "TZ-0005", "Aliyev Akmal");
        when(driverRepo.findByDriverCode("TZ-0005")).thenReturn(Optional.of(d));
        when(driverRepo.findById(5L)).thenReturn(Optional.of(d));
        when(driverRepo.addToBalance(eq(5L), anyLong())).thenAnswer(inv -> {
            long amt = inv.getArgument(1);
            d.setBalance(d.getBalance() + amt);
            return 1;
        });
        when(ledger.insertIfAbsent(anyString(), anyString(), anyLong(), anyLong(), eq(2), any()))
                .thenReturn(1);
        // Atomic claim: first call wins.
        when(ledger.markConfirmedIfNotAlready(eq("PAYDOC-1"), eq("PAYDOC-1"))).thenReturn(1);

        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        params.put("amount", "1000");
        Map<String, Object> b = body("PAYDOC-1", "ATT-1", 2, "T", params);
        b.put("merchant_prepare_id", "PAYDOC-1");
        sign(b);

        Map<String, Object> resp = service.dispatch(b);
        assertEquals(0, resp.get("error"));
        assertEquals(2, resp.get("status"));
        assertEquals("PAYDOC-1", resp.get("merchant_confirm_id"));

        verify(driverRepo).addToBalance(5L, 100_000L);
        verify(txRepo).save(any());
        assertEquals(100_000L, d.getBalance(), "balance must equal 1000 som in tiyin");
    }

    @Test
    void complete_duplicateCall_isIdempotent_noDoubleCredit() {
        Driver d = activeDriverWithCode(5L, "TZ-0005", "Aliyev Akmal");
        when(driverRepo.findByDriverCode("TZ-0005")).thenReturn(Optional.of(d));
        when(ledger.insertIfAbsent(anyString(), anyString(), anyLong(), anyLong(), eq(2), any()))
                .thenReturn(0); // row exists — duplicate path
        // Atomic claim already CONFIRMED -> 0 rowcount.
        when(ledger.markConfirmedIfNotAlready(eq("PAYDOC-1"), eq("PAYDOC-1"))).thenReturn(0);

        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        params.put("amount", "1000");
        Map<String, Object> b = body("PAYDOC-1", "ATT-1", 2, "T", params);
        b.put("merchant_prepare_id", "PAYDOC-1");
        sign(b);

        Map<String, Object> resp = service.dispatch(b);
        assertEquals(0, resp.get("error"));
        assertEquals(2, resp.get("status"));

        // CRITICAL: balance must NOT change on duplicate Complete.
        verify(driverRepo, never()).addToBalance(anyLong(), anyLong());
        verify(txRepo, never()).save(any());
    }

    @Test
    void complete_badSign_returnsMinusOne_noCredit() {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        params.put("amount", "1000");
        Map<String, Object> b = body("PAYDOC-1", "ATT-1", 2, "T", params);
        b.put("sign_string", "wrong");

        Map<String, Object> resp = service.dispatch(b);
        assertEquals(-1, resp.get("error"));
        verify(driverRepo, never()).addToBalance(anyLong(), anyLong());
        verify(txRepo, never()).save(any());
    }

    // ─── 5. Action dispatch ───

    @Test
    void unknownAction_returnsMinusThree() {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        Map<String, Object> b = body("P", "A", 99, "T", params);
        sign(b);
        assertEquals(-3, service.dispatch(b).get("error"));
    }

    @Test
    void parseAmountTiyin_handlesDecimalSom() {
        LinkedHashMap<String, Object> p = new LinkedHashMap<>();
        p.put("amount", "1234.56");
        assertEquals(123_456L, AdvancedShopService.parseAmountTiyin(p),
                "1234.56 UZS -> 123456 tiyin (BigDecimal movePointRight(2))");
    }

    @Test
    void parseAmountTiyin_rejectsGarbage() {
        LinkedHashMap<String, Object> p = new LinkedHashMap<>();
        p.put("amount", "not-a-number");
        assertNull(AdvancedShopService.parseAmountTiyin(p));
    }

    @Test
    void concatParamValues_preservesInsertionOrder_andSkipsNullsAsEmpty() {
        LinkedHashMap<String, Object> p = new LinkedHashMap<>();
        p.put("a", "X");
        p.put("b", null);
        p.put("c", 42);
        assertEquals("X42", AdvancedShopService.concatParamValues(p));
    }

    private static String anyString() { return any(String.class); }
}
