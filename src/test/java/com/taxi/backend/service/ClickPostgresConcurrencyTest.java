package com.taxi.backend.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PostgreSQL satr-lock semantikasi bilan real concurrent COMPLETE isboti.
 * Docker yo'q muhitda Testcontainers bu klassni xavfsiz SKIP qiladi; CI/Docker
 * muhitida aynan repositorydagi conditional UPDATE orqali faqat bitta kredit
 * berilishini tekshiradi.
 */
@Testcontainers(disabledWithoutDocker = true)
class ClickPostgresConcurrencyTest {

    private static final String CLICK_TRANS_ID = "CT-CONCURRENT-1";
    private static final String MERCHANT_TRANS_ID = "ORDER-CONCURRENT-1";
    private static final long AMOUNT_TIYIN = 100_000L;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void createSchema() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE drivers (id BIGINT PRIMARY KEY, balance BIGINT NOT NULL)");
            statement.execute("CREATE TABLE click_transactions ("
                    + "click_trans_id VARCHAR(128) PRIMARY KEY, "
                    + "merchant_trans_id VARCHAR(128) NOT NULL, "
                    + "status VARCHAR(20) NOT NULL, "
                    + "merchant_confirm_id VARCHAR(128), "
                    + "action INTEGER, error INTEGER, completed_at TIMESTAMP)");
            statement.execute("CREATE TABLE transactions ("
                    + "id BIGSERIAL PRIMARY KEY, driver_id BIGINT NOT NULL, amount BIGINT NOT NULL)");
            statement.execute("INSERT INTO drivers (id, balance) VALUES (7, 0)");
            statement.execute("INSERT INTO click_transactions (click_trans_id, merchant_trans_id, status) VALUES ('"
                    + CLICK_TRANS_ID + "', '" + MERCHANT_TRANS_ID + "', 'PREPARED')");
        }
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    @Timeout(30)
    void concurrentCompleteClaimsExactlyOneCredit() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> completeOnce("confirm-a", ready, start));
            Future<Boolean> second = executor.submit(() -> completeOnce("confirm-b", ready, start));

            assertTrue(ready.await(10, TimeUnit.SECONDS), "both callbacks must be ready before the race");
            start.countDown();

            int winners = (first.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, winners, "only one Complete callback may claim the PREPARED row");

            try (Connection connection = connection()) {
                assertEquals(AMOUNT_TIYIN, scalarLong(connection, "SELECT balance FROM drivers WHERE id=7"));
                assertEquals(1L, scalarLong(connection, "SELECT count(*) FROM transactions WHERE driver_id=7"));
                assertEquals(1L, scalarLong(connection,
                        "SELECT count(*) FROM click_transactions WHERE click_trans_id='" + CLICK_TRANS_ID
                                + "' AND status='CONFIRMED'"));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean completeOnce(String confirmId, CountDownLatch ready, CountDownLatch start) throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test race did not start");
                }

                // Production ClickTransactionRepository.markConfirmedIfPrepared query.
                int claimed;
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE click_transactions "
                                + "SET status='CONFIRMED', merchant_confirm_id=?, action=1, error=0, completed_at=now() "
                                + "WHERE click_trans_id=? AND merchant_trans_id=? AND status='PREPARED'")) {
                    statement.setString(1, confirmId);
                    statement.setString(2, CLICK_TRANS_ID);
                    statement.setString(3, MERCHANT_TRANS_ID);
                    claimed = statement.executeUpdate();
                }

                if (claimed == 1) {
                    try (PreparedStatement updateBalance = connection.prepareStatement(
                            "UPDATE drivers SET balance=balance+? WHERE id=7");
                         PreparedStatement insertLedger = connection.prepareStatement(
                                 "INSERT INTO transactions (driver_id, amount) VALUES (7, ?)")) {
                        updateBalance.setLong(1, AMOUNT_TIYIN);
                        assertEquals(1, updateBalance.executeUpdate());
                        insertLedger.setLong(1, AMOUNT_TIYIN);
                        assertEquals(1, insertLedger.executeUpdate());
                    }
                }
                connection.commit();
                return claimed == 1;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static long scalarLong(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }
}
