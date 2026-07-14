package com.taxi.backend.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Clean PostgreSQL bootstrap proves the unshipped V49 matches its JPA columns. */
@Testcontainers(disabledWithoutDocker = true)
class TripDriverOfferFlywayBootstrapTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void flywayBootstrapsV49WithTypedTimingColumnsAndBigintForeignKeys() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertColumn(connection, "trip_id", "bigint");
            assertColumn(connection, "driver_id", "bigint");
            assertColumn(connection, "response_expires_at", "timestamp without time zone");
            assertColumn(connection, "delivery_attempt_count", "integer");
            assertColumn(connection, "last_delivery_outcome", "character varying");
            assertColumn(connection, "delivery_recipient_fingerprint", "character varying");
        }
    }

    private static void assertColumn(Connection connection, String column, String expectedDataType) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = 'trip_driver_offers' AND column_name = ?")) {
            statement.setString(1, column);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo(expectedDataType);
            }
        }
    }
}
