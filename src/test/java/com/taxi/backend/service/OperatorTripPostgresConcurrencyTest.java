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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real PostgreSQL race coverage for V47's exact uniqueness boundaries and the
 * repository claim SQL. Service unit tests separately prove that only CREATE
 * invokes the dispatching OperatorService path; this test proves the database
 * admits exactly one creator under concurrent requests.
 */
@Testcontainers(disabledWithoutDocker = true)
class OperatorTripPostgresConcurrencyTest {

    private static final long OPERATOR_ID = 1L;
    private static final long PASSENGER_ID = 2L;
    private static final String REQUEST_HASH = "a".repeat(64);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void createSchema() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE trips ("
                    + "id BIGSERIAL PRIMARY KEY, passenger_id BIGINT NOT NULL REFERENCES users(id), "
                    + "scheduled_at TIMESTAMP NULL, status VARCHAR(40) NOT NULL, source VARCHAR(20) NOT NULL)");
            statement.execute("CREATE TABLE operator_trip_idempotencies ("
                    + "id BIGSERIAL PRIMARY KEY, operator_id BIGINT NOT NULL REFERENCES users(id), "
                    + "idempotency_key VARCHAR(36) NOT NULL, request_hash CHAR(64) NOT NULL, "
                    + "state VARCHAR(20) NOT NULL DEFAULT 'PROCESSING', trip_id BIGINT REFERENCES trips(id), "
                    + "response_status VARCHAR(40), response_source VARCHAR(20), "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "CONSTRAINT uq_operator_trip_idempotency UNIQUE (operator_id, idempotency_key), "
                    + "CONSTRAINT chk_operator_trip_idempotency_state "
                    + "CHECK (state IN ('PROCESSING', 'COMPLETED'))) ");
            statement.execute("CREATE UNIQUE INDEX uq_trips_one_active_immediate_trip_per_passenger "
                    + "ON trips(passenger_id) WHERE passenger_id IS NOT NULL AND scheduled_at IS NULL "
                    + "AND status IN ('SEARCHING', 'ACCEPTED', 'DRIVER_ARRIVED', 'STARTED')");
            statement.execute("INSERT INTO users (id) VALUES (1), (2)");
        }
    }

    @AfterEach
    void clearRaceData() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM operator_trip_idempotencies");
            statement.execute("DELETE FROM trips");
        }
    }

    @Test
    @Timeout(60)
    void twoParallelSameKeyRequestsCreateOneTripAndOneReplay() throws Exception {
        List<CreateResult> results = runParallel(2, false);

        assertEquals(1, results.stream().filter(result -> result == CreateResult.CREATED).count());
        assertEquals(1, results.stream().filter(result -> result == CreateResult.REPLAY).count());
    }

    @Test
    @Timeout(60)
    void twentyParallelSameKeyRequestsCreateExactlyOneTripAndNineteenReplays() throws Exception {
        List<CreateResult> results = runParallel(20, false);

        assertEquals(1, results.stream().filter(result -> result == CreateResult.CREATED).count());
        assertEquals(19, results.stream().filter(result -> result == CreateResult.REPLAY).count());
        try (Connection connection = connection()) {
            assertEquals(1L, scalarLong(connection, "SELECT count(*) FROM trips"));
            assertEquals(1L, scalarLong(connection, "SELECT count(*) FROM operator_trip_idempotencies"));
            assertEquals(1L, scalarLong(connection,
                    "SELECT count(*) FROM operator_trip_idempotencies WHERE state='COMPLETED' AND trip_id IS NOT NULL"));
        }
    }

    @Test
    @Timeout(60)
    void twentyDistinctKeysForOnePassengerCreateOneImmediateTripAndConflictTheRest() throws Exception {
        List<CreateResult> results = runParallel(20, true);

        assertEquals(1, results.stream().filter(result -> result == CreateResult.CREATED).count());
        assertEquals(19, results.stream().filter(result -> result == CreateResult.ACTIVE_TRIP_CONFLICT).count());
        try (Connection connection = connection()) {
            assertEquals(1L, scalarLong(connection, "SELECT count(*) FROM trips WHERE scheduled_at IS NULL"));
            assertEquals(1L, scalarLong(connection, "SELECT count(*) FROM operator_trip_idempotencies"));
        }
    }

    @Test
    void scheduledTripsRemainOutsideTheImmediateTripConstraint() throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            insertTrip(connection, null);
            insertTrip(connection, LocalDateTime.now().plusHours(1));
            connection.commit();
        }
        try (Connection connection = connection()) {
            assertEquals(2L, scalarLong(connection, "SELECT count(*) FROM trips"));
        }
    }

    private List<CreateResult> runParallel(int workers, boolean distinctKeys) throws Exception {
        String sharedKey = UUID.randomUUID().toString();
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<CreateResult>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                String key = distinctKeys ? UUID.randomUUID().toString() : sharedKey;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(20, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("parallel race did not start");
                    }
                    return idempotentCreate(key);
                }));
            }
            assertTrue(ready.await(20, TimeUnit.SECONDS), "all requests must be ready before the race");
            start.countDown();
            List<CreateResult> results = new ArrayList<>();
            for (Future<CreateResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private CreateResult idempotentCreate(String key) throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                int claimed;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO operator_trip_idempotencies "
                                + "(operator_id, idempotency_key, request_hash, state) "
                                + "VALUES (?, ?, ?, 'PROCESSING') "
                                + "ON CONFLICT (operator_id, idempotency_key) DO NOTHING")) {
                    statement.setLong(1, OPERATOR_ID);
                    statement.setString(2, key);
                    statement.setString(3, REQUEST_HASH);
                    claimed = statement.executeUpdate();
                }

                if (claimed == 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT request_hash, state, trip_id FROM operator_trip_idempotencies "
                                    + "WHERE operator_id=? AND idempotency_key=?")) {
                        statement.setLong(1, OPERATOR_ID);
                        statement.setString(2, key);
                        try (ResultSet result = statement.executeQuery()) {
                            assertTrue(result.next());
                            assertEquals(REQUEST_HASH, result.getString("request_hash").trim());
                            assertEquals("COMPLETED", result.getString("state"));
                            assertTrue(result.getLong("trip_id") > 0);
                        }
                    }
                    connection.commit();
                    return CreateResult.REPLAY;
                }

                long tripId = insertTrip(connection, null);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE operator_trip_idempotencies SET trip_id=?, state='COMPLETED', updated_at=CURRENT_TIMESTAMP "
                                + "WHERE operator_id=? AND idempotency_key=?")) {
                    statement.setLong(1, tripId);
                    statement.setLong(2, OPERATOR_ID);
                    statement.setString(3, key);
                    assertEquals(1, statement.executeUpdate());
                }
                connection.commit();
                return CreateResult.CREATED;
            } catch (SQLException exception) {
                connection.rollback();
                if ("23505".equals(exception.getSQLState())) {
                    return CreateResult.ACTIVE_TRIP_CONFLICT;
                }
                throw exception;
            }
        }
    }

    private static long insertTrip(Connection connection, LocalDateTime scheduledAt) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO trips (passenger_id, scheduled_at, status, source) VALUES (?, ?, 'SEARCHING', 'CALL')",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, PASSENGER_ID);
            if (scheduledAt == null) statement.setNull(2, java.sql.Types.TIMESTAMP);
            else statement.setTimestamp(2, Timestamp.valueOf(scheduledAt));
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
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

    private enum CreateResult { CREATED, REPLAY, ACTIVE_TRIP_CONFLICT }
}
