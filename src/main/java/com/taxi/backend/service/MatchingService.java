package com.taxi.backend.service;

import com.taxi.backend.model.Driver;
import com.taxi.backend.repository.DriverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Yandex Taxi Matching Engine — soddalashtirilgan versiyasi.
 *
 * Yandex yondashuvi:
 *   1. In-memory graph (ko'chalar + kesishishlar) bilan barcha mos haydovchilarni topadi
 *   2. Har birining aniq ETA'sini hisoblaydi
 *   3. ENG TEZIDA yetib keluvchini tanlaydi
 *
 * Bizning yondashuv (MVP):
 *   1. Redis'dan online haydovchilar lokatsiyasini oladi
 *   2. Haversine formula bilan masofani hisoblaydi
 *   3. Radiusda eng yaqin N ta haydovchini qaytaradi
 *   4. Kelajakda: road graph + real ETA
 */
@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);
    private static final double DEFAULT_RADIUS_KM = 5.0;
    private static final int    MAX_CANDIDATES    = 10;

    private final DriverLocationCache locationCache;
    private final DriverRepository    driverRepository;

    @Value("${matching.weight-proximity:0.60}")
    private double weightProximity;

    @Value("${matching.weight-activity:0.40}")
    private double weightActivity;

    @Value("${matching.tie-epsilon:0.5}")
    private double tieEpsilon;

    public MatchingService(DriverLocationCache locationCache, DriverRepository driverRepository) {
        this.locationCache = locationCache;
        this.driverRepository = driverRepository;
    }

    /**
     * Yo'lovchi joylashuviga yaqin, faol haydovchilarni topish.
     *
     * @param passengerLat  Yo'lovchi kengligi
     * @param passengerLon  Yo'lovchi uzunligi
     * @param radiusKm      Qidirish radiusi (km)
     * @return Eng yaqin haydovchilar ro'yxati (masofasi bo'yicha saralangan)
     */
    public List<MatchedDriver> findNearbyDrivers(double passengerLat, double passengerLon, double radiusKm, boolean ignoreCooldown) {
        List<DriverLocationCache.NearbyDriver> nearby =
                locationCache.getNearbyDrivers(passengerLat, passengerLon, radiusKm);

        if (!nearby.isEmpty()) {
            log.info("[MATCHING] {} — found {} drivers",
                    locationCache.isRedisAvailable() ? "Redis available" : "In-memory fallback",
                    nearby.size());
        } else {
            // DB fallback — server restart bo'lsa yoki Redis+memory ikkalasi ham bo'sh
            nearby = findFromDatabase(passengerLat, passengerLon, radiusKm);
            if (!nearby.isEmpty()) {
                log.info("[MATCHING] Redis unavailable — DB fallback, found {} drivers", nearby.size());
            } else {
                log.warn("[MATCHING] No drivers found in either Redis or DB");
            }
        }

        List<DriverLocationCache.NearbyDriver> candidates = nearby.stream()
                .limit(MAX_CANDIDATES).toList();

        if (candidates.isEmpty()) return List.of();

        // Batch fetch — bitta SQL query (N+1 fix)
        List<Long> ids = candidates.stream().map(DriverLocationCache.NearbyDriver::driverId).toList();
        Map<Long, Driver> driverMap = driverRepository.findAllByIdsWithUser(ids).stream()
                .collect(Collectors.toMap(Driver::getId, Function.identity()));

        List<MatchedDriver> eligible = candidates.stream()
                .map(nd -> {
                    Driver driver = driverMap.get(nd.driverId());
                    if (driver == null || !driver.isOnline()) return null;
                    if (driver.getBalance() != null && driver.getBalance() < 0) return null;
                    if (!ignoreCooldown && driver.isInCooldown()) return null; // rad etish cooldown'i (operator dispatch e'tiborsiz qoldiradi)

                    // ETA hisoblash — Haversine * yo'l egriligi koeffitsiyenti * tezlik
                    // Shahar ichida Haversine x 1.4 (yo'l egriligi), o'rtacha 30 km/h
                    double roadDistanceKm = nd.distanceKm() * 1.4;
                    double avgSpeedKmPerMin = 0.5; // 30 km/h = 0.5 km/min
                    double etaMinutes = Math.max(1.0, roadDistanceKm / avgSpeedKmPerMin);

                    return new MatchedDriver(
                            nd.driverId(),
                            driver.getUser() != null ? driver.getUser().getName() : "",
                            driver.getCarModel(),
                            driver.getCarNumber(),
                            nd.lat(),
                            nd.lon(),
                            nd.distanceKm(),
                            etaMinutes,
                            driver.getRating() != null ? driver.getRating().doubleValue() : 5.0,
                            driver.getAcceptedTariffs() != null
                                    ? driver.getAcceptedTariffs() : "EKONOM,DAMAS,BIZNES",
                            driver.getTariffGrants(),
                            driver.getActivityScore(),
                            driver.getFreeSince()
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Eng yuqori scoring g'olib — masofa (60%) + aktivlik (40%), tenglikda eng erta bo'shagani
        return rankByScore(eligible, radiusKm);
    }

    /** Standart matching — rad etish cooldown'i HISOBGA OLINADI (yo'lovchi auto-dispatch). */
    public List<MatchedDriver> findNearbyDrivers(double passengerLat, double passengerLon, double radiusKm) {
        return findNearbyDrivers(passengerLat, passengerLon, radiusKm, false);
    }

    /**
     * Mos haydovchilarni vaznli ball bo'yicha saralash (eng yuqori birinchi).
     *
     *   score = weightProximity * proximityScore + weightActivity * activityNorm
     *
     * - proximityScore (0..100): yaqinroq = yuqoriroq. 100 - (distance/maxRadius * 100).
     * - activityNorm (0..100): nomzodlar POOLI ichida min-max normalizatsiya. Shu sabab
     *   yangi (past aktivlikli) lekin yaqin haydovchi yutib chiqishi mumkin. Agar barcha
     *   nomzodlarning aktivligi teng bo'lsa — activity hissasi konstant, proximity hal qiladi.
     * - Tie-break: ballar epsilon-bucket ichida teng bo'lsa, eng erta free_since (navbatda
     *   eng uzoq kutgan) g'olib; free_since null bo'lsa oxirida.
     */
    public List<MatchedDriver> rankByScore(List<MatchedDriver> candidates, double maxRadiusKm) {
        if (candidates.size() <= 1) return new ArrayList<>(candidates);

        double minAct = candidates.stream().mapToDouble(MatchedDriver::activityScore).min().getAsDouble();
        double maxAct = candidates.stream().mapToDouble(MatchedDriver::activityScore).max().getAsDouble();
        double range = maxAct - minAct;

        Map<Long, Double> scoreById = new HashMap<>();
        for (MatchedDriver d : candidates) {
            double proximity = proximityScore(d.distanceKm(), maxRadiusKm);
            double activityNorm = range == 0 ? 100.0 : (d.activityScore() - minAct) / range * 100.0;
            scoreById.put(d.driverId(), weightProximity * proximity + weightActivity * activityNorm);
        }

        return candidates.stream()
                .sorted(Comparator
                        // epsilon-bucket: shu bucketdagi ballar "teng" hisoblanadi (yuqori birinchi)
                        .comparingLong((MatchedDriver d) -> Math.round(scoreById.get(d.driverId()) / tieEpsilon))
                        .reversed()
                        // tenglikda — eng erta bo'shagani (free_since kichikroq) birinchi
                        .thenComparing(d -> d.freeSince() != null ? d.freeSince() : LocalDateTime.MAX)
                        // pirovard — aniq ball (yuqori birinchi)
                        .thenComparing((MatchedDriver d) -> scoreById.get(d.driverId()), Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    /** Yaqinlik balli 0..100 — yaqinroq = yuqoriroq. */
    public double proximityScore(double distanceKm, double maxRadiusKm) {
        if (maxRadiusKm <= 0) return 0;
        return Math.max(0.0, 100.0 - (distanceKm / maxRadiusKm * 100.0));
    }

    /** DB fallback — server restart yoki Redis+memory ikkalasi bo'sh bo'lganda */
    private List<DriverLocationCache.NearbyDriver> findFromDatabase(double lat, double lon, double radiusKm) {
        // Tier 1: so'nggi 30 daqiqada yangilangan ACTIVE haydovchilar
        List<Driver> dbDrivers = driverRepository.findOnlineDriversUpdatedSince(
                LocalDateTime.now().minusMinutes(30));

        // Tier 2: vaqt filteri yo'q, lekin hali ham status=ACTIVE talab qilinadi
        if (dbDrivers.isEmpty()) {
            log.info("[MATCHING] DB tier-1 (30 daqiqa) bo'sh — ACTIVE online haydovchilarga fallback");
            dbDrivers = driverRepository.findActiveOnlineDrivers();
        }

        List<DriverLocationCache.NearbyDriver> result = new ArrayList<>();
        for (Driver d : dbDrivers) {
            if (d.getLatitude() == null || d.getLongitude() == null) continue;
            if (d.getBalance() != null && d.getBalance() < 0) continue; // Manfiy balans — skip
            double dist = DriverLocationCache.haversineKm(lat, lon, d.getLatitude(), d.getLongitude());
            if (dist <= radiusKm) {
                result.add(new DriverLocationCache.NearbyDriver(d.getId(), d.getLatitude(), d.getLongitude(), dist));
            }
        }
        result.sort((a, b) -> Double.compare(a.distanceKm(), b.distanceKm()));

        // Tier 3 (dev/test fallback): ACTIVE tekshiruvini o'tkazib, barcha isOnline haydovchilar.
        if (result.isEmpty()) {
            log.warn("[MATCHING] DB tier-2 bo'sh — barcha isOnline fallback (status/coords ignored, dev mode)");
            for (Driver d : driverRepository.findByIsOnlineTrue()) {
                if (d.getBalance() != null && d.getBalance() < 0) continue; // Manfiy balans — skip
                double dLat = d.getLatitude()  != null ? d.getLatitude()  : lat;
                double dLon = d.getLongitude() != null ? d.getLongitude() : lon;
                double dist = DriverLocationCache.haversineKm(lat, lon, dLat, dLon);
                result.add(new DriverLocationCache.NearbyDriver(d.getId(), dLat, dLon, dist));
            }
            result.sort((a, b) -> Double.compare(a.distanceKm(), b.distanceKm()));
        }

        return result;
    }

    /** Standart radius bilan topish */
    public List<MatchedDriver> findNearbyDrivers(double lat, double lon) {
        return findNearbyDrivers(lat, lon, DEFAULT_RADIUS_KM);
    }

    /** Yo'lovchi uchun haydovchi ETA va lokatsiyasini qaytarish */
    public Map<String, Object> getDriverEta(Long driverId, double passengerLat, double passengerLon) {
        Map<String, Object> loc = locationCache.getLocation(driverId);
        if (loc == null) return Map.of("etaMinutes", -1, "available", false);

        double dLat = ((Number) loc.get("lat")).doubleValue();
        double dLon = ((Number) loc.get("lon")).doubleValue();
        double distKm = DriverLocationCache.haversineKm(passengerLat, passengerLon, dLat, dLon);
        // Yo'l egriligi x 1.4, o'rtacha 30 km/h
        double etaMin = Math.max(1.0, (distKm * 1.4) / 0.5);

        return Map.of(
                "lat", dLat,
                "lon", dLon,
                "distanceKm", Math.round(distKm * 10.0) / 10.0,
                "etaMinutes", (int) Math.ceil(etaMin),
                "available", true
        );
    }

    /** Mos haydovchi ma'lumotlari */
    public record MatchedDriver(
            Long driverId,
            String driverName,
            String carModel,
            String carNumber,
            double lat,
            double lon,
            double distanceKm,
            double etaMinutes,
            double rating,
            String acceptedTariffs,
            String tariffGrants,
            double activityScore,
            LocalDateTime freeSince
    ) {}
}
