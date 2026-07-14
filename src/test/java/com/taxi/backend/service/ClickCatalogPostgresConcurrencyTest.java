package com.taxi.backend.service;

import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.enums.Role;
import com.taxi.backend.enums.TransactionType;
import com.taxi.backend.model.ClickTransaction;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Transaction;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.ClickTransactionRepository;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

/**
 * Production-shaped proof for the catalog money path.  Unlike the retired
 * JDBC model, each callback traverses the real PaymentService -> catalog
 * state machine -> shared wallet service and writes to Flyway-migrated
 * PostgreSQL.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({PaymentService.class, ClickCatalogPaymentService.class, ClickWalletCreditService.class,
        ClickCatalogModePolicy.class, ClickCatalogPostgresConcurrencyTest.TestBeans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "click.superapp.catalog.mode=ON",
        "click.service-id=105926",
        "click.secret-key=local-integration-secret",
        "click.merchant-id=local-merchant",
        "click.checkout-url=https://example.invalid/click",
        "payme.merchant-id=local-payme",
        "payme.secret-key=local-payme-secret",
        "payme.checkout-url=https://example.invalid/payme",
        "app.payment.return-url=https://example.invalid/return"
})
class ClickCatalogPostgresConcurrencyTest {

    private static final String ACCOUNT = "TZ-9001";
    private static final String SERVICE_ID = "105926";
    private static final String SECRET = "local-integration-secret";
    private static final long INITIAL_BALANCE = 1_000_000L;
    private static final long AMOUNT = 100_000L;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private PaymentService paymentService;
    @Autowired private ClickCatalogPaymentService catalogPaymentService;
    @Autowired private ClickCatalogModePolicy catalogModePolicy;
    @Autowired private DriverRepository driverRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ClickTransactionRepository clickTransactionRepository;

    /** A spy only observes the real, proxied wallet method; it never replaces it. */
    @MockitoSpyBean private ClickWalletCreditService walletCreditService;

    private Long driverId;
    private Long userId;

    @BeforeEach
    void seedCommittedDriver() {
        inTransaction(() -> {
            User user = new User();
            user.setPhone(String.format("+998%09d", Math.floorMod(System.nanoTime(), 1_000_000_000L)));
            user.setRole(Role.DRIVER);
            user.setName("Catalog integration driver");
            entityManager.persist(user);

            Driver driver = new Driver();
            driver.setUser(user);
            driver.setDriverCode(ACCOUNT);
            driver.setStatus(DriverStatus.ACTIVE);
            driver.setBalance(INITIAL_BALANCE);
            entityManager.persist(driver);
            entityManager.flush();
            driverId = driver.getId();
            userId = user.getId();
        });
        setMode("ON");
    }

    @AfterEach
    void cleanup() {
        dropFailureObjects();
        if (driverId != null) {
            inTransaction(() -> {
                jdbcTemplate.update("DELETE FROM transactions WHERE driver_id = ?", driverId);
                jdbcTemplate.update("DELETE FROM click_transactions WHERE driver_id = ?", driverId);
                jdbcTemplate.update("DELETE FROM drivers WHERE id = ?", driverId);
                jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
            });
        }
    }

    @Test
    void realSpringProxiesUseOneTransactionForPrepareCompleteAndWalletCredit() {
        assertTrue(AopUtils.isAopProxy(paymentService), "PaymentService must be invoked through Spring AOP");
        assertTrue(AopUtils.isAopProxy(catalogPaymentService), "catalog state machine must be proxied");

        AtomicBoolean walletSawTransaction = new AtomicBoolean(false);
        doAnswer(invocation -> {
            walletSawTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(walletCreditService).credit(any(), anyLong(), any());

        CatalogOrder order = prepare("SPRING-CT-1");
        Map<String, Object> completed = paymentService.handleClickComplete(order.complete());

        assertEquals(0, completed.get("error"));
        assertTrue(walletSawTransaction.get(), "wallet mutation must run with Spring transaction synchronization");
        assertEquals(INITIAL_BALANCE + AMOUNT, balance());
        assertEquals(1L, topupCount());

        Transaction ledger = transactionRepository.findByDriverId(driverId).stream().findFirst().orElseThrow();
        assertEquals(TransactionType.TOPUP, ledger.getType());
        assertEquals(AMOUNT, ledger.getAmount());
        assertEquals(INITIAL_BALANCE, ledger.getBalanceBefore());
        assertEquals(INITIAL_BALANCE + AMOUNT, ledger.getBalanceAfter());

        ClickTransaction click = clickTransactionRepository.findByClickTransId(order.clickTransId()).orElseThrow();
        assertEquals("CLICK_SUPERAPP", click.getPaymentSource());
        assertEquals(ACCOUNT, click.getCatalogAccount());
        assertEquals("CONFIRMED", click.getStatus());
    }

    @Test
    void offAndDrainRespectDurableCatalogStateWithoutTouchingNewMoney() {
        setMode("OFF");
        assertEquals(-8, paymentService.handleClickPrepare(prepareCallback("MODE-OFF", "PD-MODE-OFF")).get("error"));
        assertEquals(-8, paymentService.handleClickComplete(completeCallback("MODE-OFF", "PD-MODE-OFF", "cp-off")).get("error"));
        assertEquals(0L, catalogCount());
        assertEquals(0L, topupCount());

        setMode("ON");
        CatalogOrder durable = prepare("MODE-DRAIN");
        setMode("DRAIN");
        assertEquals(-8, paymentService.handleClickPrepare(prepareCallback("MODE-NEW", "PD-MODE-NEW")).get("error"));
        assertEquals(-6, paymentService.handleClickComplete(completeCallback("MODE-UNKNOWN", "PD-MODE-UNKNOWN", "cp-unknown")).get("error"));
        assertEquals(0, paymentService.handleClickComplete(durable.complete()).get("error"));
        assertEquals(-4, paymentService.handleClickComplete(durable.complete()).get("error"));
        assertEquals(INITIAL_BALANCE + AMOUNT, balance());
        assertEquals(1L, topupCount());
    }

    @RepeatedTest(20)
    @Timeout(60)
    void twentyConcurrentDuplicateCompletesCreditExactlyOnceThroughActualServices() throws Exception {
        CatalogOrder order = prepare("RACE-CT-1");
        List<Map<String, Object>> results = concurrently(20, () ->
                paymentService.handleClickComplete(new HashMap<>(order.complete())));

        assertEquals(1L, results.stream().filter(result -> error(result) == 0).count());
        assertEquals(19L, results.stream().filter(result -> error(result) == -4).count());
        assertEquals(INITIAL_BALANCE + AMOUNT, balance());
        assertEquals(1L, topupCount());
        assertEquals("CONFIRMED", status(order.clickTransId()));
    }

    @Test
    @Timeout(60)
    void tenDistinctCatalogPaymentsShareOneDriverWithoutLosingLedgerSnapshots() throws Exception {
        List<CatalogOrder> orders = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            orders.add(prepare("DISTINCT-CT-" + index));
        }

        List<Map<String, Object>> results = concurrently(orders);

        assertEquals(10L, results.stream().filter(result -> error(result) == 0).count());
        assertEquals(INITIAL_BALANCE + 10 * AMOUNT, balance());
        assertEquals(10L, topupCount());

        List<Map<String, Object>> ledger = jdbcTemplate.queryForList(
                "SELECT balance_before, balance_after, amount FROM transactions WHERE driver_id = ? "
                        + "AND type = 'TOPUP' ORDER BY balance_before", driverId);
        assertEquals(10, ledger.size());
        for (int index = 0; index < ledger.size(); index++) {
            assertEquals(INITIAL_BALANCE + index * AMOUNT, number(ledger.get(index), "balance_before"));
            assertEquals(INITIAL_BALANCE + (index + 1L) * AMOUNT, number(ledger.get(index), "balance_after"));
            assertEquals(AMOUNT, number(ledger.get(index), "amount"));
        }
    }

    @Test
    void ledgerInsertFailureRollsBackClaimBalanceAndLedgerTogether() {
        CatalogOrder order = prepare("ROLLBACK-LEDGER");
        jdbcTemplate.execute("CREATE OR REPLACE FUNCTION test_catalog_fail_ledger() RETURNS trigger LANGUAGE plpgsql AS "
                + "$$ BEGIN RAISE EXCEPTION 'forced catalog ledger failure'; END; $$");
        jdbcTemplate.execute("CREATE TRIGGER test_catalog_fail_ledger_trigger BEFORE INSERT ON transactions "
                + "FOR EACH ROW EXECUTE FUNCTION test_catalog_fail_ledger()");

        assertThrows(RuntimeException.class, () -> paymentService.handleClickComplete(order.complete()));

        assertEquals(INITIAL_BALANCE, balance());
        assertEquals(0L, topupCount());
        assertEquals("PREPARED", status(order.clickTransId()));
    }

    @Test
    void confirmationFailureRollsBackBeforeAnyWalletMutation() {
        CatalogOrder order = prepare("ROLLBACK-CONFIRM");
        jdbcTemplate.execute("CREATE OR REPLACE FUNCTION test_catalog_fail_confirm() RETURNS trigger LANGUAGE plpgsql AS "
                + "$$ BEGIN IF NEW.status = 'CONFIRMED' THEN RAISE EXCEPTION 'forced catalog confirm failure'; END IF; "
                + "RETURN NEW; END; $$");
        jdbcTemplate.execute("CREATE TRIGGER test_catalog_fail_confirm_trigger BEFORE UPDATE ON click_transactions "
                + "FOR EACH ROW EXECUTE FUNCTION test_catalog_fail_confirm()");

        assertThrows(RuntimeException.class, () -> paymentService.handleClickComplete(order.complete()));

        assertEquals(INITIAL_BALANCE, balance());
        assertEquals(0L, topupCount());
        assertEquals("PREPARED", status(order.clickTransId()));
    }

    @Test
    void uniqueLedgerConflictRollsBackConfirmationAndBalance() {
        CatalogOrder order = prepare("ROLLBACK-UNIQUE");
        String description = "Click SuperApp TOPUP " + ACCOUNT + " #" + safeId(order.clickTransId());
        inTransaction(() -> {
            Driver driver = entityManager.find(Driver.class, driverId);
            Transaction fixture = new Transaction();
            fixture.setDriver(driver);
            fixture.setType(TransactionType.TOPUP);
            fixture.setAmount(1L);
            fixture.setBalanceBefore(INITIAL_BALANCE);
            fixture.setBalanceAfter(INITIAL_BALANCE);
            fixture.setDescription(description);
            entityManager.persist(fixture);
        });
        jdbcTemplate.execute("CREATE UNIQUE INDEX test_catalog_unique_ledger "
                + "ON transactions (driver_id, description)");

        assertThrows(RuntimeException.class, () -> paymentService.handleClickComplete(order.complete()));

        assertEquals(INITIAL_BALANCE, balance());
        assertEquals(1L, topupCount(), "only the fixture exists; callback inserted no second ledger row");
        assertEquals("PREPARED", status(order.clickTransId()));
    }

    private CatalogOrder prepare(String clickTransId) {
        Map<String, Object> response = paymentService.handleClickPrepare(prepareCallback(clickTransId, "PD-" + clickTransId));
        assertEquals(0, response.get("error"), response::toString);
        String prepareId = (String) response.get("merchant_prepare_id");
        assertNotNull(prepareId);
        return new CatalogOrder(clickTransId, "PD-" + clickTransId, prepareId);
    }

    private Map<String, String> prepareCallback(String clickTransId, String paydocId) {
        String amount = "1000";
        String action = "0";
        String signTime = "2026-07-14 12:00:00";
        String sign = md5(clickTransId + SERVICE_ID + SECRET + ACCOUNT + amount + action + signTime);
        Map<String, String> callback = base(clickTransId, paydocId, amount, action, signTime, sign);
        callback.put("error", "0");
        callback.put("error_note", "Success");
        return callback;
    }

    private Map<String, String> completeCallback(String clickTransId, String paydocId, String prepareId) {
        String amount = "1000";
        String action = "1";
        String signTime = "2026-07-14 12:00:00";
        String sign = md5(clickTransId + SERVICE_ID + SECRET + ACCOUNT + prepareId + amount + action + signTime);
        Map<String, String> callback = base(clickTransId, paydocId, amount, action, signTime, sign);
        callback.put("merchant_prepare_id", prepareId);
        callback.put("error", "0");
        callback.put("error_note", "Success");
        return callback;
    }

    private static Map<String, String> base(String clickTransId, String paydocId, String amount,
                                            String action, String signTime, String sign) {
        Map<String, String> callback = new HashMap<>();
        callback.put("click_trans_id", clickTransId);
        callback.put("click_paydoc_id", paydocId);
        callback.put("service_id", SERVICE_ID);
        callback.put("merchant_trans_id", ACCOUNT);
        callback.put("amount", amount);
        callback.put("action", action);
        callback.put("sign_time", signTime);
        callback.put("sign_string", sign);
        return callback;
    }

    private List<Map<String, Object>> concurrently(int workers, Callback callback) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(20, TimeUnit.SECONDS), "race start timed out");
                    return callback.call();
                }));
            }
            assertTrue(ready.await(20, TimeUnit.SECONDS), "workers did not become ready");
            start.countDown();
            List<Map<String, Object>> results = new ArrayList<>();
            for (Future<Map<String, Object>> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Map<String, Object>> concurrently(List<CatalogOrder> orders) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(orders.size());
        CountDownLatch ready = new CountDownLatch(orders.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            for (int index = 0; index < orders.size(); index++) {
                CatalogOrder order = orders.get(index);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(20, TimeUnit.SECONDS), "race start timed out");
                    return paymentService.handleClickComplete(new HashMap<>(order.complete()));
                }));
            }
            assertTrue(ready.await(20, TimeUnit.SECONDS), "workers did not become ready");
            start.countDown();
            List<Map<String, Object>> results = new ArrayList<>();
            for (Future<Map<String, Object>> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private void inTransaction(Runnable work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> work.run());
    }

    private long balance() {
        return jdbcTemplate.queryForObject("SELECT balance FROM drivers WHERE id = ?", Long.class, driverId);
    }

    private long topupCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM transactions WHERE driver_id = ? AND type = 'TOPUP'",
                Long.class, driverId);
    }

    private long catalogCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM click_transactions WHERE driver_id = ? "
                        + "AND payment_source = 'CLICK_SUPERAPP'", Long.class, driverId);
    }

    private String status(String clickTransId) {
        return jdbcTemplate.queryForObject("SELECT status FROM click_transactions WHERE click_trans_id = ?", String.class,
                clickTransId);
    }

    private void setMode(String mode) {
        ReflectionTestUtils.setField(catalogModePolicy, "configuredMode", mode);
    }

    private void dropFailureObjects() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS test_catalog_fail_ledger_trigger ON transactions");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS test_catalog_fail_ledger()");
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS test_catalog_fail_confirm_trigger ON click_transactions");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS test_catalog_fail_confirm()");
        jdbcTemplate.execute("DROP INDEX IF EXISTS test_catalog_unique_ledger");
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static int error(Map<String, Object> result) {
        return ((Number) result.get("error")).intValue();
    }

    private static String safeId(String value) {
        return value.length() <= 12 ? value : value.substring(0, 12) + "...";
    }

    private static String md5(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                output.append(String.format("%02x", value));
            }
            return output.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record CatalogOrder(String clickTransId, String paydocId, String prepareId) {
        Map<String, String> complete() {
            return completeCallbackStatic(clickTransId, paydocId, prepareId);
        }
    }

    private static Map<String, String> completeCallbackStatic(String clickTransId, String paydocId, String prepareId) {
        String amount = "1000";
        String action = "1";
        String signTime = "2026-07-14 12:00:00";
        String sign = md5(clickTransId + SERVICE_ID + SECRET + ACCOUNT + prepareId + amount + action + signTime);
        Map<String, String> callback = base(clickTransId, paydocId, amount, action, signTime, sign);
        callback.put("merchant_prepare_id", prepareId);
        callback.put("error", "0");
        callback.put("error_note", "Success");
        return callback;
    }

    @FunctionalInterface
    private interface Callback {
        Map<String, Object> call();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory("127.0.0.1", 1);
            connectionFactory.afterPropertiesSet();
            return new StringRedisTemplate(connectionFactory);
        }
    }
}
