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
import java.util.ArrayList;
import java.util.List;
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
                    + "id BIGSERIAL PRIMARY KEY, driver_id BIGINT NOT NULL, amount BIGINT NOT NULL, "
                    + "balance_before BIGINT NOT NULL, balance_after BIGINT NOT NULL)");
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
                assertEquals(0L, scalarLong(connection,
                        "SELECT balance_before FROM transactions WHERE driver_id=7"));
                assertEquals(AMOUNT_TIYIN, scalarLong(connection,
                        "SELECT balance_after FROM transactions WHERE driver_id=7"));
                assertEquals(1L, scalarLong(connection,
                        "SELECT count(*) FROM click_transactions WHERE click_trans_id='" + CLICK_TRANS_ID
                                + "' AND status='CONFIRMED'"));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(30)
    void concurrentDistinctCreditsFormOneAuthoritativeLedgerChain() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO drivers (id, balance) VALUES (8, 0)");
        }

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> {
                creditWithLedger(8L, 100_000L, ready, start);
                return true;
            });
            Future<Boolean> second = executor.submit(() -> {
                creditWithLedger(8L, 250_000L, ready, start);
                return true;
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS), "both wallet credits must be ready before the race");
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);

            try (Connection connection = connection()) {
                assertEquals(350_000L, scalarLong(connection, "SELECT balance FROM drivers WHERE id=8"));
                List<long[]> snapshots = snapshots(connection, 8L);
                assertEquals(2, snapshots.size());
                assertEquals(0L, snapshots.get(0)[0]);
                assertEquals(snapshots.get(0)[1], snapshots.get(1)[0]);
                assertEquals(350_000L, snapshots.get(1)[1]);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rollbackAfterBalanceMutationLeavesNoBalanceOrLedgerChange() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO drivers (id, balance) VALUES (9, 0)");
        }

        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            applyLedgerCredit(connection, 9L, 100_000L, false);
            connection.rollback();
        }

        try (Connection connection = connection()) {
            assertEquals(0L, scalarLong(connection, "SELECT balance FROM drivers WHERE id=9"));
            assertEquals(0L, scalarLong(connection, "SELECT count(*) FROM transactions WHERE driver_id=9"));
        }
    }

    @Test
    void rollbackAfterLedgerCreationLeavesNoBalanceOrLedgerChange() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO drivers (id, balance) VALUES (10, 0)");
        }

        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            applyLedgerCredit(connection, 10L, 100_000L, true);
            connection.rollback();
        }

        try (Connection connection = connection()) {
            assertEquals(0L, scalarLong(connection, "SELECT balance FROM drivers WHERE id=10"));
            assertEquals(0L, scalarLong(connection, "SELECT count(*) FROM transactions WHERE driver_id=10"));
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
                    applyLedgerCredit(connection, 7L, AMOUNT_TIYIN, true);
                }
                connection.commit();
                return claimed == 1;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void creditWithLedger(long driverId, long amount, CountDownLatch ready, CountDownLatch start) throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test race did not start");
                }
                applyLedgerCredit(connection, driverId, amount, true);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    /** Mirrors the fixed managed-entity path: lock, capture before, update, then insert ledger. */
    private static void applyLedgerCredit(Connection connection, long driverId, long amount, boolean insertLedger) throws Exception {
        long balanceBefore = scalarLong(connection,
                "SELECT balance FROM drivers WHERE id=" + driverId + " FOR UPDATE");
        long balanceAfter = Math.addExact(balanceBefore, amount);
        try (PreparedStatement updateBalance = connection.prepareStatement(
                "UPDATE drivers SET balance=? WHERE id=?")) {
            updateBalance.setLong(1, balanceAfter);
            updateBalance.setLong(2, driverId);
            assertEquals(1, updateBalance.executeUpdate());
        }
        if (insertLedger) {
            try (PreparedStatement insertLedgerStatement = connection.prepareStatement(
                    "INSERT INTO transactions (driver_id, amount, balance_before, balance_after) VALUES (?, ?, ?, ?)")) {
                insertLedgerStatement.setLong(1, driverId);
                insertLedgerStatement.setLong(2, amount);
                insertLedgerStatement.setLong(3, balanceBefore);
                insertLedgerStatement.setLong(4, balanceAfter);
                assertEquals(1, insertLedgerStatement.executeUpdate());
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

    private static List<long[]> snapshots(Connection connection, long driverId) throws Exception {
        List<long[]> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT balance_before, balance_after FROM transactions WHERE driver_id=? ORDER BY id")) {
            statement.setLong(1, driverId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new long[] { rows.getLong(1), rows.getLong(2) });
                }
            }
        }
        return result;
    }
}
