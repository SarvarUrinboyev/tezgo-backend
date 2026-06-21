package com.taxi.backend.service;

import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Security Monitor — anomaliyalarni aniqlash va Sentry alert yuborish.
 *
 * MONITORING:
 *   1. Failed login tracking: 1 daqiqada 10+ → ALERT
 *   2. Admin unauthorized access → ALERT
 *   3. Trip anomaliya: 1 soatda 50+ trip yaratish → ALERT
 *   4. DB connection pool monitoring → WARNING at 80%
 */
@Service
public class SecurityMonitorService {

    private static final Logger log = LoggerFactory.getLogger(SecurityMonitorService.class);

    private final ConcurrentHashMap<String, Deque<Instant>> failedLogins = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Deque<Instant>> tripCreations = new ConcurrentHashMap<>();
    private final DataSource dataSource;

    public SecurityMonitorService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Failed login tracking — AuthService dan chaqiriladi */
    public void trackFailedLogin(String ip, String phone) {
        Instant now = Instant.now();
        Deque<Instant> attempts = failedLogins.computeIfAbsent(ip, k -> new LinkedList<>());
        synchronized (attempts) {
            attempts.removeIf(ts -> ts.isBefore(now.minusSeconds(60)));
            attempts.addLast(now);

            if (attempts.size() >= 10) {
                String msg = String.format("[BRUTE-FORCE] IP %s dan 1 daqiqada %d marta noto'g'ri login (phone=%s)",
                        ip, attempts.size(), maskPhone(phone));
                log.error(msg);
                sendSentryAlert("Brute Force Attack Detected", msg, SentryLevel.ERROR);
                attempts.clear();
            }
        }
    }

    /** Admin unauthorized access — JwtFilter dan chaqiriladi */
    public void trackUnauthorizedAdminAccess(String ip, String path, String phone) {
        String msg = String.format("[UNAUTHORIZED-ADMIN] %s -> %s (phone=%s)", ip, path, maskPhone(phone));
        log.error(msg);
        sendSentryAlert("Unauthorized Admin Access", msg, SentryLevel.WARNING);
    }

    /** Trip creation anomaliya — TripService dan chaqiriladi */
    public void trackTripCreation(Long userId) {
        Instant now = Instant.now();
        Deque<Instant> trips = tripCreations.computeIfAbsent(userId, k -> new LinkedList<>());
        synchronized (trips) {
            trips.removeIf(ts -> ts.isBefore(now.minusSeconds(3600)));
            trips.addLast(now);

            if (trips.size() >= 50) {
                String msg = String.format("[ANOMALY] User #%d 1 soatda %d ta trip yaratdi", userId, trips.size());
                log.error(msg);
                sendSentryAlert("Trip Creation Anomaly", msg, SentryLevel.WARNING);
                trips.clear();
            }
        }
    }

    /** DB connection pool monitoring — har 30 soniyada */
    @Scheduled(fixedRate = 30000)
    public void checkDbPool() {
        try {
            if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hikari) {
                var pool = hikari.getHikariPoolMXBean();
                if (pool != null) {
                    int active = pool.getActiveConnections();
                    int total = pool.getTotalConnections();
                    int max = hikari.getMaximumPoolSize();

                    double usage = total > 0 ? (double) active / max * 100 : 0;
                    if (usage >= 80) {
                        String msg = String.format("[DB-POOL] %d%% band (%d/%d active, %d total)",
                                (int) usage, active, max, total);
                        log.warn(msg);
                        sendSentryAlert("DB Connection Pool High Usage", msg, SentryLevel.WARNING);
                    }
                }
            }
        } catch (Exception e) {
            // HikariDataSource bo'lmasa yoki pool bean yo'q bo'lsa — o'tkazish
        }
    }

    /** Sentry ga alert yuborish */
    private void sendSentryAlert(String title, String detail, SentryLevel level) {
        try {
            SentryEvent event = new SentryEvent();
            event.setLevel(level);
            Message message = new Message();
            message.setFormatted(title + ": " + detail);
            event.setMessage(message);
            event.setTag("type", "security-monitor");
            Sentry.captureEvent(event);
        } catch (Exception ignored) {
            // Sentry DSN yo'q bo'lsa — yutish
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) return "***";
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 2);
    }

    /** Eskirgan yozuvlarni tozalash */
    @Scheduled(fixedRate = 600000)
    public void cleanup() {
        Instant cutoff = Instant.now().minusSeconds(3600);
        failedLogins.entrySet().removeIf(e -> {
            synchronized (e.getValue()) { e.getValue().removeIf(ts -> ts.isBefore(cutoff)); return e.getValue().isEmpty(); }
        });
        tripCreations.entrySet().removeIf(e -> {
            synchronized (e.getValue()) { e.getValue().removeIf(ts -> ts.isBefore(cutoff)); return e.getValue().isEmpty(); }
        });
    }
}
