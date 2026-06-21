package com.taxi.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Haydovchi joylashuvini Redis Geo + In-memory dual-write da saqlash.
 *
 * ARXITEKTURA: Dual-write pattern
 *   - saveLocation() → DOIMO ikkiga yozadi: in-memory + Redis
 *   - getNearbyDrivers() → Redis dan oladi, xato bo'lsa in-memory fallback
 *   - getLocation() → DOIMO in-memory dan (eng tez, eng ishonchli)
 *
 * EDGE CASE HIMOYASI:
 *   1. Redis uzilib qolsa → in-memory fallback (so'rov yo'qolmaydi)
 *   2. Redis qayta tiklansa → 30s ichida avtomatik sync-back
 *   3. Sync-back: in-memory dagi barcha location va online state Redis ga yuklanadi
 *   4. Redis timeout → redisAvailable=false, lekin 30s health check qayta sinaydi
 *
 * MUHIM: in-memory doimo to'g'ri bo'ladi (dual-write),
 * Redis faqat GEORADIUS optimization uchun ishlatiladi.
 */
@Service
public class DriverLocationCache {

    private static final Logger log = LoggerFactory.getLogger(DriverLocationCache.class);
    private static final String GEO_KEY = "driver:geo:locations";
    private static final String ONLINE_KEY = "driver:online:set";

    private final StringRedisTemplate redis;

    // In-memory — DOIMO yangilanadi (primary source of truth)
    private final Map<Long, Map<String, Object>> locationMap = new ConcurrentHashMap<>();
    private final Set<Long> onlineDrivers = ConcurrentHashMap.newKeySet();

    // Redis holati
    private final AtomicBoolean redisAvailable = new AtomicBoolean(false);
    private final AtomicLong lastRedisFailure = new AtomicLong(0);
    private final AtomicLong redisSyncCount = new AtomicLong(0);

    public DriverLocationCache(StringRedisTemplate redis) {
        this.redis = redis;
        checkRedisHealth();
    }

    /** Haydovchi lokatsiyasini saqlash — dual-write: in-memory + Redis */
    public void saveLocation(Long driverId, double lat, double lon) {
        // 1. In-memory — DOIMO (primary)
        locationMap.put(driverId, Map.of("driverId", driverId, "lat", lat, "lon", lon,
                "ts", System.currentTimeMillis()));

        // 2. Redis — best-effort (secondary)
        if (redisAvailable.get()) {
            try {
                redis.opsForGeo().add(GEO_KEY, new Point(lon, lat), driverId.toString());
            } catch (Exception e) {
                markRedisDown("GEOADD", e);
            }
        }
    }

    /** Haydovchi lokatsiyasini olish — doimo in-memory dan */
    public Map<String, Object> getLocation(Long driverId) {
        return locationMap.get(driverId);
    }

    /** Haydovchini online ro'yxatiga qo'shish */
    public void setOnline(Long driverId) {
        onlineDrivers.add(driverId);
        if (redisAvailable.get()) {
            try {
                redis.opsForSet().add(ONLINE_KEY, driverId.toString());
            } catch (Exception e) {
                markRedisDown("SADD", e);
            }
        }
    }

    /** Haydovchini offline qilish */
    public void setOffline(Long driverId) {
        onlineDrivers.remove(driverId);
        locationMap.remove(driverId);
        if (redisAvailable.get()) {
            try {
                redis.opsForGeo().remove(GEO_KEY, driverId.toString());
                redis.opsForSet().remove(ONLINE_KEY, driverId.toString());
            } catch (Exception e) {
                markRedisDown("ZREM", e);
            }
        }
    }

    /** Barcha online haydovchilar ID lari */
    public List<Long> getOnlineDriverIds() {
        return new ArrayList<>(onlineDrivers);
    }

    /** Yaqin haydovchilar — Redis GEORADIUS yoki in-memory fallback */
    public List<NearbyDriver> getNearbyDrivers(double lat, double lon, double radiusKm) {
        if (redisAvailable.get()) {
            try {
                return getNearbyFromRedis(lat, lon, radiusKm);
            } catch (Exception e) {
                markRedisDown("GEORADIUS", e);
            }
        }
        return getNearbyFromMemory(lat, lon, radiusKm);
    }

    // ═══════════════════════════════════════════════════════════════
    // REDIS HEALTH CHECK + SYNC-BACK (har 30 soniyada)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Redis health check — har 30 soniyada.
     * Agar Redis uzilib qayta tiklangan bo'lsa:
     *   1. PING bilan tekshiradi
     *   2. In-memory dagi barcha ma'lumotlarni Redis ga sync qiladi
     *   3. redisAvailable = true qiladi
     */
    @Scheduled(fixedDelay = 30000) // 30 soniya
    public void checkRedisHealth() {
        try {
            String pong = redis.getConnectionFactory().getConnection().ping();
            if (pong != null) {
                boolean wasDown = !redisAvailable.get();
                redisAvailable.set(true);

                if (wasDown) {
                    log.info("[REDIS] Redis qayta tiklandi — sync-back boshlanmoqda...");
                    syncBackToRedis();
                }
            }
        } catch (Exception e) {
            if (redisAvailable.compareAndSet(true, false)) {
                log.warn("[REDIS] Redis uzilib qoldi — in-memory fallback rejimiga o'tildi: {}", e.getMessage());
                lastRedisFailure.set(System.currentTimeMillis());
            }
        }
    }

    /**
     * Sync-back — in-memory dagi barcha ma'lumotlarni Redis ga yuklash.
     * Redis qayta tiklanganda chaqiriladi.
     *
     * Bu yerda "edge case": sync paytida yangi location kelsa,
     * dual-write tufayli u ham Redis ga yoziladi — hech narsa yo'qolmaydi.
     */
    private void syncBackToRedis() {
        int synced = 0;
        try {
            // 1. Online haydovchilar setini sync
            for (Long driverId : onlineDrivers) {
                redis.opsForSet().add(ONLINE_KEY, driverId.toString());
            }

            // 2. Joylashuvlarni GEOADD bilan sync
            for (Map.Entry<Long, Map<String, Object>> entry : locationMap.entrySet()) {
                Long driverId = entry.getKey();
                Map<String, Object> loc = entry.getValue();
                double lat = ((Number) loc.get("lat")).doubleValue();
                double lon = ((Number) loc.get("lon")).doubleValue();
                redis.opsForGeo().add(GEO_KEY, new Point(lon, lat), driverId.toString());
                synced++;
            }

            redisSyncCount.incrementAndGet();
            log.info("[REDIS] Sync-back tugadi: {} ta joylashuv, {} ta online haydovchi",
                    synced, onlineDrivers.size());
        } catch (Exception e) {
            log.error("[REDIS] Sync-back xato: {}", e.getMessage());
            redisAvailable.set(false);
        }
    }

    /** Redis mavjudligini tekshirish (MatchingService DB fallback uchun) */
    public boolean isRedisAvailable() {
        return redisAvailable.get();
    }

    /** Redis holati haqida ma'lumot (monitoring uchun) */
    public Map<String, Object> getHealthInfo() {
        return Map.of(
                "redisAvailable", redisAvailable.get(),
                "inMemoryDrivers", locationMap.size(),
                "onlineDrivers", onlineDrivers.size(),
                "totalSyncs", redisSyncCount.get(),
                "lastFailureMs", lastRedisFailure.get()
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // PRIVATE — Redis va In-memory search
    // ═══════════════════════════════════════════════════════════════

    private List<NearbyDriver> getNearbyFromRedis(double lat, double lon, double radiusKm) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().radius(
                GEO_KEY,
                new Circle(new Point(lon, lat), new Distance(radiusKm, Metrics.KILOMETERS)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeCoordinates()
                        .includeDistance()
                        .sortAscending()
                        .limit(50)
        );

        if (results == null) return List.of();

        // Online tekshiruvini in-memory dan olish (tez va ishonchli)
        List<NearbyDriver> nearby = new ArrayList<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : results) {
            String idStr = r.getContent().getName();
            try {
                Long driverId = Long.parseLong(idStr);
                if (!onlineDrivers.contains(driverId)) continue; // in-memory online check
                Point point = r.getContent().getPoint();
                double dist = r.getDistance().getValue();
                nearby.add(new NearbyDriver(driverId, point.getY(), point.getX(), dist));
            } catch (NumberFormatException ignored) {}
        }
        return nearby;
    }

    private List<NearbyDriver> getNearbyFromMemory(double lat, double lon, double radiusKm) {
        List<NearbyDriver> result = new ArrayList<>();
        for (Long driverId : new ArrayList<>(onlineDrivers)) {
            Map<String, Object> loc = locationMap.get(driverId);
            if (loc == null) continue;
            double dLat = ((Number) loc.get("lat")).doubleValue();
            double dLon = ((Number) loc.get("lon")).doubleValue();
            double dist = haversineKm(lat, lon, dLat, dLon);
            if (dist <= radiusKm) {
                result.add(new NearbyDriver(driverId, dLat, dLon, dist));
            }
        }
        result.sort((a, b) -> Double.compare(a.distanceKm(), b.distanceKm()));
        return result;
    }

    private void markRedisDown(String operation, Exception e) {
        if (redisAvailable.compareAndSet(true, false)) {
            lastRedisFailure.set(System.currentTimeMillis());
            log.warn("[REDIS] {} xato — fallback rejim: {}", operation, e.getMessage());
        }
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public record NearbyDriver(Long driverId, double lat, double lon, double distanceKm) {}
}
