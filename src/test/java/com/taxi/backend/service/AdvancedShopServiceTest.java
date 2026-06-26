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
        when(driverRepo.findByDriverCodeWithUser("TZ-0005")).thenReturn(Optional.of(d));

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
        when(driverRepo.findByDriverCodeWithUser("TZ-9999")).thenReturn(Optional.empty());
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
        when(driverRepo.findByDriverCodeWithUser("TZ-0005")).thenReturn(Optional.of(d));

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
        when(driverRepo.findByDriverCodeWithUser("TZ-0005")).thenReturn(Optional.of(d));

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
        verify(driverRepo, never()).findByDriverCodeWithUser(any());
    }

    @Test
    void getinfo_missingIdentifier_returnsMinusEight() {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        // No identifier-shaped key at all.
        Map<String, Object> b = body("P", "A", 0, "T", params);
        sign(b);

        assertEquals(-8, service.dispatch(b).get("error"));
    }

    // ─── 2b. Getinfo authorization — UNSIGNED getinfo (Click's real shape) ───
    // Click sends getinfo without sign_string: { action:0, service_id, params:{account} }.
    // getinfo (read-only) accepts unsigned IFF service_id matches; money path stays strict.

    @Test
    void getinfo_unsigned_matchingServiceId_returnsFio() {
        Driver d = activeDriverWithCode(5L, "TZ-0005", "Aliyev Akmal");
        when(driverRepo.findByDriverCodeWithUser("TZ-0005")).thenReturn(Optional.of(d));

        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        // Click's real getinfo shape: no sign_string. body() already sets service_id=SERVICE_ID.
        Map<String, Object> b = body("P", "A", 0, "T", params);
        // NOTE: deliberately NOT calling sign(b) — this is an UNSIGNED getinfo.

        Map<String, Object> resp = service.dispatch(b);
        assertEquals(0, resp.get("error"), "unsigned getinfo with matching service_id must be accepted");
        assertEquals(Map.of("fio", "Aliyev Akmal"), resp.get("params"));
    }

    @Test
    void getinfo_unsigned_wrongServiceId_returnsMinusOne() {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        Map<String, Object> b = body("P", "A", 0, "T", params);
        b.put("service_id", "99999");        // does NOT match configured SERVICE_ID
        // unsigned

        Map<String, Object> resp = service.dispatch(b);
        assertEquals(-1, resp.get("error"), "unsigned getinfo with wrong service_id must be rejected");
        verify(driverRepo, never()).findByDriverCodeWithUser(any());
    }

    @Test
    void getinfo_unsigned_missingServiceId_returnsMinusOne() {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        Map<String, Object> b = body("P", "A", 0, "T", params);
        b.remove("service_id");               // no service_id at all
        // unsigned

        Map<String, Object> resp = service.dispatch(b);
        assertEquals(-1, resp.get("error"), "unsigned getinfo with no service_id must be rejected");
        verify(driverRepo, never()).findByDriverCodeWithUser(any());
    }

    @Test
    void getinfo_unsigned_secretUnset_returnsMinusOne_failClosed() {
        ReflectionTestUtils.setField(service, "shopSecretKey", "");   // not configured yet
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        Map<String, Object> b = body("P", "A", 0, "T", params);       // matching service_id, unsigned

        Map<String, Object> resp = service.dispatch(b);
        assertEquals(-1, resp.get("error"),
                "fail-closed: even matching service_id is rejected until the secret is configured");
        verify(driverRepo, never()).findByDriverCodeWithUser(any());
    }

    @Test
    void getinfo_signed_stillVerifies_whenSignPresent() {
        // If Click DOES sign getinfo, the signed path must still work (verify the signature).
        Driver d = activeDriverWithCode(5L, "TZ-0005", "Aliyev Akmal");
        when(driverRepo.findByDriverCodeWithUser("TZ-0005")).thenReturn(Optional.of(d));

        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        Map<String, Object> b = body("P", "A", 0, "T", params);
        sign(b);                              // signed getinfo
        assertEquals(0, service.dispatch(b).get("error"), "signed getinfo must still verify + succeed");

        // tampered signature on getinfo → rejected (signed path still enforced)
        b.put("sign_string", "deadbeef00000000000000000000beef");
        assertEquals(-1, service.dispatch(b).get("error"), "signed getinfo with bad signature must be rejected");
    }

    /**
     * REGRESSION (prod incident 2026-06-26): getinfo 500'd in prod with
     * "Could not initialize proxy [User] - no session" — a LazyInitializationException.
     * getinfo runs WITHOUT a transaction (dispatch self-invokes this package-private handler,
     * so @Transactional would be a proxy no-op). The fix: handleGetinfo must use the EAGER
     * findByDriverCodeWithUser (JOIN FETCH), never the lazy findByDriverCode.
     *
     * The original unit tests missed this because they mock a driver with a REAL User (getName works).
     * Here we reproduce the EXACT symptom: the lazy lookup returns a driver whose User proxy throws
     * "no session" on getName(); the eager lookup returns a usable driver. The test passes ONLY if
     * getinfo took the eager path. If anyone reverts to the lazy findByDriverCode, getName() throws
     * and this goes red — exactly the prod failure, caught in the suite.
     *
     * NOTE: a pure-Mockito test can't open a real Hibernate session, so this guards the *code path*
     * (eager fetch) via the real failure symptom. A full DB-level reproduction would need a
     * persistence context (testcontainers/Docker).
     */
    @Test
    void getinfo_usesEagerFetch_soLazyUserProxyNeverInitializedOutsideSession() {
        // Lazy path (the bug): driver whose User proxy explodes with the real Hibernate symptom.
        Driver lazyDriver = new Driver();
        lazyDriver.setId(5L);
        lazyDriver.setStatus(DriverStatus.ACTIVE);
        User lazyUserProxy = mock(User.class);
        when(lazyUserProxy.getName())
                .thenThrow(new org.hibernate.LazyInitializationException(
                        "could not initialize proxy [com.taxi.backend.model.User#8] - no session"));
        lazyDriver.setUser(lazyUserProxy);
        lenient().when(driverRepo.findByDriverCode("TZ-0005")).thenReturn(Optional.of(lazyDriver));

        // Eager path (the fix): driver with a fully-initialized User.
        Driver eagerDriver = activeDriverWithCode(5L, "TZ-0005", "Aliyev Akmal");
        when(driverRepo.findByDriverCodeWithUser("TZ-0005")).thenReturn(Optional.of(eagerDriver));

        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        Map<String, Object> b = body("P", "A", 0, "T", params);
        sign(b);

        Map<String, Object> resp = service.dispatch(b);   // must NOT throw LazyInitializationException
        assertEquals(0, resp.get("error"), "getinfo must use the eager fetch (no lazy User proxy)");
        assertEquals(Map.of("fio", "Aliyev Akmal"), resp.get("params"));
        verify(driverRepo).findByDriverCodeWithUser("TZ-0005");
    }

    @Test
    void prepare_unsigned_stillRejected_moneyPathStaysStrict() {
        // The getinfo relaxation must NOT leak into the money path.
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        params.put("amount", "1000");
        Map<String, Object> b = body("PAYDOC-1", "ATT-1", 1, "T", params);   // action=1 prepare
        // unsigned, but matching service_id — must STILL be rejected (money path requires signature)

        Map<String, Object> resp = service.dispatch(b);
        assertEquals(-1, resp.get("error"), "unsigned prepare must be rejected even with matching service_id");
        verifyNoInteractions(ledger);
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

    // ─── 6. Click integration handover requirements ───
    //
    // These four tests lock the behaviors the user described to Click on 2026-06-26:
    //   - "account" is the canonical parameter name we asked Click to send
    //   - prepare with missing amount is rejected (no ledger write, no transaction)
    //   - complete with a non-existent driver is rejected (no balance credit) — with
    //     the "failed" status so Click cancels rather than retries forever
    //   - canonical identifier key constant is publicly declared (deliverable references it)

    @Test
    void canonicalIdentifierKey_isAccount() {
        // The Click integration deliverable says we tell Click to send "account" — this test
        // FAILS LOUDLY if the canonical key is ever changed without coordinating with Click.
        assertEquals("account", AdvancedShopService.CANONICAL_IDENTIFIER_KEY,
                "Telegram handover (2026-06-26) pinned 'account' as the identifier key — coordinate any change with Click");
    }

    @Test
    void getinfo_accountKeyWins_overOtherCandidates() {
        // If Click ever sends BOTH "account" and a legacy key (e.g. "merchant_trans_id"),
        // "account" must win — that's the contract.
        Driver d = activeDriverWithCode(5L, "TZ-0005", "Aliyev Akmal");
        when(driverRepo.findByDriverCodeWithUser("TZ-0005")).thenReturn(Optional.of(d));

        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("merchant_trans_id", "TZ-9999");           // legacy / wrong driver
        params.put("account", "TZ-0005");                     // canonical — wins
        Map<String, Object> b = body("P", "A", 0, "T", params);
        sign(b);

        Map<String, Object> resp = service.dispatch(b);
        assertEquals(0, resp.get("error"));
        assertEquals(Map.of("fio", "Aliyev Akmal"), resp.get("params"));
        verify(driverRepo).findByDriverCodeWithUser("TZ-0005");
        verify(driverRepo, never()).findByDriverCodeWithUser("TZ-9999");
    }

    @Test
    void prepare_missingAmount_returnsMinusEight_andNoLedgerWrite() {
        Driver d = activeDriverWithCode(5L, "TZ-0005", "Aliyev Akmal");
        // We deliberately do NOT stub findByDriverCode — if amount validation runs
        // BEFORE driver lookup, the repo is never hit. If validation runs after, we'd
        // still get -8 because driver is fine. Either way: no ledger write.
        lenient().when(driverRepo.findByDriverCode("TZ-0005")).thenReturn(Optional.of(d));

        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-0005");
        // No amount / summa key at all
        Map<String, Object> b = body("PAYDOC-1", "ATT-1", 1, "T", params);
        sign(b);

        Map<String, Object> resp = service.dispatch(b);
        assertEquals(-8, resp.get("error"), "missing amount -> -8 (malformed request)");
        verifyNoInteractions(ledger);
        verify(txRepo, never()).save(any());
    }

    @Test
    void complete_unknownDriver_returnsFailedStatus_noCredit_noConfirmedLedger() {
        // Driver code that doesn't exist -> error -5 + status=1 (Click cancels, doesn't retry).
        when(driverRepo.findByDriverCode("TZ-9999")).thenReturn(Optional.empty());

        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("account", "TZ-9999");
        params.put("amount", "1000");
        Map<String, Object> b = body("PAYDOC-X", "ATT-X", 2, "T", params);
        b.put("merchant_prepare_id", "PAYDOC-X");
        sign(b);

        Map<String, Object> resp = service.dispatch(b);
        assertEquals(-5, resp.get("error"));
        assertEquals(1, resp.get("status"), "status=1 (failed) so Click stops retrying");

        // CRITICAL: no balance credit, no transaction logged, no CONFIRMED ledger row.
        verify(driverRepo, never()).addToBalance(anyLong(), anyLong());
        verify(txRepo, never()).save(any());
        verify(ledger, never()).markConfirmedIfNotAlready(anyString(), anyString());
        // markCancelled MAY have been called (safe — still no money moved).
    }

    private static String anyString() { return any(String.class); }
}
