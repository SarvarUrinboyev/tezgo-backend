package com.taxi.backend.service;

import org.junit.jupiter.api.AfterEach;
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
import java.sql.SQLException;
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
 * Uses real PostgreSQL to prove V49's last-line invariant under a concurrent
 * insert race: one trip cannot acquire two live driver offers.
 */
@Testcontainers(disabledWithoutDocker = true)
class TripDriverOfferPostgresConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void createSchema() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE trips (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE drivers (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO trips (id) VALUES (1)");
            statement.execute("INSERT INTO drivers (id) VALUES (10), (11), (12)");
            statement.execute("CREATE TABLE trip_driver_offers ("
                    + "id BIGSERIAL PRIMARY KEY, version BIGINT, trip_id BIGINT NOT NULL REFERENCES trips(id), "
                    + "driver_id BIGINT NOT NULL REFERENCES drivers(id), generation INTEGER NOT NULL, "
                    + "candidate_rank INTEGER NOT NULL, distance_km DOUBLE PRECISION, status VARCHAR(32) NOT NULL, "
                    + "offered_at TIMESTAMP NOT NULL, expires_at TIMESTAMP NOT NULL, acknowledged_at TIMESTAMP, "
                    + "closed_at TIMESTAMP, delivery_attempt_state VARCHAR(32) NOT NULL, delivery_error VARCHAR(500), "
                    + "created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL, "
                    + "CONSTRAINT uq_trip_driver_offers_trip_generation UNIQUE (trip_id, generation), "
                    + "CONSTRAINT uq_trip_driver_offers_trip_driver UNIQUE (trip_id, driver_id))");
            statement.execute("CREATE UNIQUE INDEX uq_trip_driver_offers_one_live_per_trip "
                    + "ON trip_driver_offers (trip_id) "
                    + "WHERE status IN ('PENDING_DELIVERY', 'ACTIVE', 'ACKNOWLEDGED')");
        }
    }

    @AfterEach
    void clearData() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM trip_driver_offers");
        }
    }

    @Test
    @Timeout(60)
    void parallelLiveOfferInsertsAdmitExactlyOneWinner() throws Exception {
        List<InsertResult> results = runParallel();

        assertEquals(1, results.stream().filter(result -> result == InsertResult.INSERTED).count());
        assertEquals(1, results.stream().filter(result -> result == InsertResult.LIVE_OFFER_CONFLICT).count());
        try (Connection connection = connection()) {
            assertEquals(1L, scalarLong(connection, "SELECT count(*) FROM trip_driver_offers"));
        }
    }

    @Test
    void terminalOfferAllowsOneNextGenerationButNeverReoffersSameDriver() throws Exception {
        assertEquals(InsertResult.INSERTED, insert(10L, 1));
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE trip_driver_offers SET status='REJECTED', closed_at=CURRENT_TIMESTAMP");
        }
        assertEquals(InsertResult.INSERTED, insert(11L, 2));
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE trip_driver_offers SET status='EXPIRED', closed_at=CURRENT_TIMESTAMP");
        }
        // The historical (trip_id, driver_id) key prevents returning to a prior driver.
        assertEquals(InsertResult.LIVE_OFFER_CONFLICT, insert(10L, 3));
        try (Connection connection = connection()) {
            assertEquals(2L, scalarLong(connection, "SELECT count(*) FROM trip_driver_offers"));
        }
    }

    private List<InsertResult> runParallel() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<InsertResult>> futures = new ArrayList<>();
            futures.add(executor.submit(() -> waitAndInsert(ready, start, 10L, 1)));
            futures.add(executor.submit(() -> waitAndInsert(ready, start, 11L, 2)));
            assertTrue(ready.await(20, TimeUnit.SECONDS));
            start.countDown();
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private InsertResult waitAndInsert(CountDownLatch ready, CountDownLatch start, long driverId, int generation) throws Exception {
        ready.countDown();
        assertTrue(start.await(20, TimeUnit.SECONDS));
        return insert(driverId, generation);
    }

    private InsertResult insert(long driverId, int generation) throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO trip_driver_offers (trip_id, driver_id, generation, candidate_rank, status, "
                            + "offered_at, expires_at, delivery_attempt_state, created_at, updated_at) "
                            + "VALUES (1, ?, ?, 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + interval '15 seconds', "
                            + "'QUEUED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                statement.setLong(1, driverId);
                statement.setInt(2, generation);
                statement.executeUpdate();
                connection.commit();
                return InsertResult.INSERTED;
            } catch (SQLException exception) {
                connection.rollback();
                if ("23505".equals(exception.getSQLState())) return InsertResult.LIVE_OFFER_CONFLICT;
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

    private enum InsertResult { INSERTED, LIVE_OFFER_CONFLICT }
}
