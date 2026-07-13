package com.taxi.backend.schema;

import com.taxi.backend.model.SystemSetting;
import com.taxi.backend.model.OperatorTripIdempotency;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Build-time migration bootstrap guard (Testcontainers Postgres, prod major 16).
 *
 * QAMROVI:
 *  1) Butun Flyway zanjiri (V1..V48) TOZA bazada noldan muvaffaqiyatli qo'llaniladi —
 *     deploy'gacha ko'rinmaydigan migratsiya buzilishini ushlaydi (masalan, V5/V6
 *     tartib xatosi shu test orqali topilgan edi).
 *  2) V27 + SystemSetting moslashuvi: app_settings jadvali yaratilgan va SystemSetting
 *     entity'si unga to'g'ri map bo'ladi (surge_enabled='false' default).
 *
 * SCOPE IZOHI (Option B): bu test entity↔ustun TUR validatsiyasini (Hibernate
 * ddl-auto=validate) qilmaydi — ddl-auto=none. Sababi: mavjud kodbazada bir nechta
 * eski entity↔schema drift bor (broadcast_targets jadvali yo'q; trips.distance_km
 * double vs BigDecimal numeric(8,2)) — ular alohida migratsiya-reconciliation
 * vazifasiga ajratilgan (prod uchun ALTER/repair oynasi kerak). Bu guard zanjir
 * qo'llanishini va V27/SystemSetting'ni qo'riqlaydi; to'liq validate keyin yoqiladi.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration"
})
class SchemaMigrationBootstrapTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private EntityManager em;

    @Test
    void flywayChainAppliesFromScratch_throughV48() {
        Object count = em.createNativeQuery(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '48' AND success = true")
                .getSingleResult();
        assertEquals(1, ((Number) count).intValue(),
                "V48 toza bazada muvaffaqiyatli qo'llanishi kerak (zanjir V1..V48 buzilmagan)");
    }

    @Test
    void v47AddsDurableIdempotencyAndTheImmediateTripGuard() {
        assertEquals(1, ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'operator_trip_idempotencies'")
                .getSingleResult()).intValue());
        assertEquals(1, ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'uq_trips_one_active_immediate_trip_per_passenger' "
                        + "AND indexdef LIKE '%scheduled_at IS NULL%'")
                .getSingleResult()).intValue());
    }

    @Test
    void v48RequestHashColumnAndEntityMappingRemainAligned() throws NoSuchFieldException {
        Column mapping = OperatorTripIdempotency.class.getDeclaredField("requestHash")
                .getAnnotation(Column.class);

        assertNotNull(mapping);
        assertEquals("", mapping.columnDefinition());
        assertEquals("character varying", em.createNativeQuery(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = 'operator_trip_idempotencies' AND column_name = 'request_hash'")
                .getSingleResult());
        assertEquals(64, ((Number) em.createNativeQuery(
                "SELECT character_maximum_length FROM information_schema.columns "
                        + "WHERE table_name = 'operator_trip_idempotencies' AND column_name = 'request_hash'")
                .getSingleResult()).intValue());
    }

    @Test
    void systemSettingEntityMapsToMigratedAppSettings() {
        // JPQL — agar SystemSetting entity'si app_settings (V27) bilan mos kelmasa, bu so'rov yiqiladi.
        SystemSetting surge = em.createQuery(
                "SELECT s FROM SystemSetting s WHERE s.key = :k", SystemSetting.class)
                .setParameter("k", "surge_enabled")
                .getSingleResult();
        assertNotNull(surge);
        assertEquals("false", surge.getValue(), "surge default OFF (V27 seed)");
    }
}
