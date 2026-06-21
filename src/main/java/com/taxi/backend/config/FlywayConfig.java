package com.taxi.backend.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway ishga tushish strategiyasi — har startda avval repair, keyin migrate.
 *
 * NEGA: allaqachon qo'llanilgan bir migratsiyaning matni keyin o'zgartirilsa (mas. V5
 * "remove old admin" sent_by tuzatishi), uning checksum'i o'zgaradi. Lekin bazadagi
 * flyway_schema_history hali eski checksum'ni saqlaydi → Flyway validate "checksum mismatch"
 * deb startni to'xtatadi. repair() history jadvalidagi checksum'larni joriy skriptlarga
 * moslab YANGILAYDI (sxema/ma'lumotga tegmaydi), shundan keyin migrate() yangi migratsiyalarni
 * qo'llaydi. Ikkala chaqiriq ham idempotent — o'zgarish bo'lmasa repair no-op.
 *
 * Bu "DEPLOY — flyway repair required" bayrog'ini doimiy yopadi: qo'llanilgan migratsiyaning
 * o'zgargan checksum'i keyingi startda o'zini-o'zi davolaydi.
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy repairBeforeMigrate() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
