package com.taxi.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRules;

/**
 * Vaqt konfiguratsiyasi — tungi tarif qarorlari uchun.
 *
 * - nightClock: biznes-zonaga (pricing.night.zone, default Asia/Tashkent) bog'langan
 *   Clock. "Hozir" tungi tarif qarorlari shu orqali olinadi → JVM/host zonasidan mustaqil.
 *
 * - JVM=UTC tekshiruvi (Option A): trips.created_at 'timestamp' (zonasiz) ustunda
 *   LocalDateTime.now() bilan yoziladi, ya'ni JVM default zonasidagi wall-clock.
 *   createdAt-asosli yo'llar (operator updateTrip, taxometer finish) uni UTC instant
 *   deb aylantiradi. Bu faqat JVM=UTC bo'lsa to'g'ri — shuning uchun ishga tushishda
 *   tekshiramiz va aks holda darhol xato beramiz (fail-fast).
 */
@Configuration
public class TimeConfig {

    private static final Logger log = LoggerFactory.getLogger(TimeConfig.class);

    @Value("${pricing.night.zone:Asia/Tashkent}")
    private String nightZone;

    @Value("${pricing.night.assert-utc:true}")
    private boolean assertUtc;

    @Bean
    public Clock nightClock() {
        ZoneId zone = ZoneId.of(nightZone);
        log.info("[TIME] Night-fare business zone = {}", zone);
        return Clock.system(zone);
    }

    @PostConstruct
    public void assertJvmIsUtc() {
        ZoneId jvm = ZoneId.systemDefault();
        ZoneRules rules = jvm.getRules();
        boolean isUtc = rules.isFixedOffset()
                && rules.getOffset(Instant.now()).getTotalSeconds() == 0;
        if (!isUtc) {
            String msg = "Server JVM timezone must be UTC (found '" + jvm + "'). "
                    + "trips.created_at is a zone-less TIMESTAMP written in the JVM zone and "
                    + "is converted as a UTC instant for night-fare. Set TZ=UTC or "
                    + "-Duser.timezone=UTC (or disable via pricing.night.assert-utc=false if "
                    + "you accept createdAt-based night decisions may be off).";
            if (assertUtc) {
                throw new IllegalStateException(msg);
            }
            log.warn("[TIME] {}", msg);
        } else {
            log.info("[TIME] JVM timezone is UTC — createdAt UTC conversion is valid.");
        }
    }
}
