package com.taxi.backend.pricing;

import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.util.*;

/**
 * TezYol Dynamic Pricing Engine v2 — ko'p omilli surge hisoblash.
 *
 * OMILLAR (8 ta):
 *   1. Talab/Taklif nisbati  — SEARCHING buyurtmalar vs online haydovchilar
 *   2. Kunning vaqti         — tong rush, kechki rush, tun
 *   3. Haftaning kuni        — juma/shanba kechasi
 *   4. Bayram kunlari        — Navro'z, Mustaqillik, Ro'za hayit, Qurbon hayit
 *   5. Ob-havo holati        — yomg'ir/qor (API emas, vaqtga asoslangan mavsumiy)
 *   6. Zone (hudud)          — markazda yuqori, chekkada past
 *   7. Haydovchi yetishmovchi — 0 online → maksimal surge
 *   8. Acceptance rate       — buyurtma qabul qilish tezligi (oxirgi 5 daqiqa)
 *
 * FORMULA:
 *   rawSurge = 1.0 + (demand/supply * 0.5 * timeMultiplier)
 *              + holidayBonus + weatherBonus + zoneBonus + scarcityBonus
 *
 *   finalSurge = clamp(rawSurge, 1.0, 3.0)
 *
 * Har bir omil SurgeFactor record sifatida qaytariladi —
 * UI da foydalanuvchiga narx oshish sababini ko'rsatish mumkin.
 */
@Service
public class SurgePricingEngine {

    private static final Logger log = LoggerFactory.getLogger(SurgePricingEngine.class);

    @Value("${surge.min:1.0}")
    private double minSurge = 1.0;

    @Value("${surge.max:3.0}")
    private double maxSurge = 3.0;

    /** Parkent markazi koordinatalari */
    private static final double PARKENT_CENTER_LAT = 41.295;
    private static final double PARKENT_CENTER_LON = 69.677;
    private static final double CENTER_RADIUS_KM = 3.0;

    /** O'zbekiston bayram kunlari (MM-DD) */
    private static final Set<MonthDay> HOLIDAYS = Set.of(
            MonthDay.of(1, 1),   // Yangi yil
            MonthDay.of(3, 8),   // Xotin-qizlar kuni
            MonthDay.of(3, 21),  // Navro'z
            MonthDay.of(5, 9),   // Xotira va qadrlash kuni
            MonthDay.of(9, 1),   // Mustaqillik kuni
            MonthDay.of(10, 1),  // O'qituvchilar kuni
            MonthDay.of(12, 8)   // Konstitutsiya kuni
    );

    /** Yomg'irli mavsumlar (O'zbekiston — mart-aprel, oktyabr-noyabr) */
    private static final Set<Integer> RAINY_MONTHS = Set.of(3, 4, 10, 11);

    private final TripRepository tripRepository;
    private final DriverRepository driverRepository;

    public SurgePricingEngine(TripRepository tripRepository, DriverRepository driverRepository) {
        this.tripRepository = tripRepository;
        this.driverRepository = driverRepository;
    }

    /**
     * Surge hisoblash — barcha omillar bilan.
     *
     * @param basePriceTiyin Asosiy narx (tiyinlarda)
     * @param lat Yo'lovchi joylashuvi (kenglik)
     * @param lon Yo'lovchi joylashuvi (uzunlik)
     * @return SurgeResult — multiplier, narx, daraja, omillar
     */
    public SurgeResult calculate(long basePriceTiyin, double lat, double lon) {
        List<SurgeFactor> factors = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // ── 1. Talab/Taklif nisbati ──
        double demandFactor = calculateDemandSupply(now, factors);

        // ── 2. Vaqt omili (tong/kech/tun) ──
        double timeFactor = calculateTimeFactor(now, factors);

        // ── 3. Hafta kuni ──
        double weekdayFactor = calculateWeekdayFactor(now, factors);

        // ── 4. Bayram ──
        double holidayFactor = calculateHolidayFactor(now, factors);

        // ── 5. Ob-havo (mavsumiy taxmin) ──
        double weatherFactor = calculateWeatherFactor(now, factors);

        // ── 6. Zone (hudud) ──
        double zoneFactor = calculateZoneFactor(lat, lon, factors);

        // ── 7. Haydovchi yetishmovchiligi ──
        double scarcityFactor = calculateScarcityFactor(factors);

        // ── 8. Acceptance tezligi ──
        double acceptanceFactor = calculateAcceptanceFactor(now, factors);

        // ═══ YAKUNIY SURGE FORMULA ═══
        double rawSurge = 1.0
                + (demandFactor * 0.35 * (1.0 + timeFactor + weekdayFactor))
                + holidayFactor
                + weatherFactor
                + zoneFactor
                + scarcityFactor
                + acceptanceFactor;

        double surge = Math.min(maxSurge, Math.max(minSurge, rawSurge));
        // 0.1 qadam bilan yaxlitlash (1.0, 1.1, 1.2, ...)
        surge = Math.round(surge * 10.0) / 10.0;

        long finalPrice = Math.round(basePriceTiyin * surge);

        String level;
        if (surge < 1.2)      level = "NORMAL";
        else if (surge < 1.5) level = "ELEVATED";
        else if (surge < 2.0) level = "HIGH";
        else if (surge < 2.5) level = "VERY_HIGH";
        else                  level = "EXTREME";

        return new SurgeResult(surge, finalPrice, level, factors);
    }

    /** Demand ma'lumoti (UI uchun) */
    public Map<String, Object> getDemandInfo(double lat, double lon) {
        SurgeResult result = calculate(0, lat, lon);
        long onlineDrivers = driverRepository.countByIsOnlineTrueAndStatusACTIVE();
        LocalDateTime tenMinAgo = LocalDateTime.now().minusMinutes(10);
        long waitingOrders = tripRepository.countByStatusAndCreatedAtAfter(TripStatus.SEARCHING, tenMinAgo);

        Map<String, Object> info = new HashMap<>();
        info.put("surgeMultiplier", result.multiplier());
        info.put("surgeLevel", result.level());
        info.put("onlineDrivers", onlineDrivers);
        info.put("waitingOrders", waitingOrders);
        info.put("estimatedWaitMinutes", onlineDrivers > 0 ? (int)(waitingOrders / onlineDrivers * 3 + 2) : 15);
        info.put("factors", result.activeFactors());
        return info;
    }

    // ═══════════════════════════════════════════════════════════════
    // PRIVATE — Har bir omil alohida hisoblash
    // ═══════════════════════════════════════════════════════════════

    private double calculateDemandSupply(LocalDateTime now, List<SurgeFactor> factors) {
        LocalDateTime tenMinAgo = now.minusMinutes(10);
        long demand = tripRepository.countByStatusAndCreatedAtAfter(TripStatus.SEARCHING, tenMinAgo);
        long supply = driverRepository.countByIsOnlineTrueAndStatusACTIVE();
        if (supply == 0) supply = 1;

        double ratio = (double) demand / supply;
        factors.add(new SurgeFactor("demand_supply",
                ratio > 0.5 ? ratio * 0.3 : 0.0,
                demand + " buyurtma / " + supply + " haydovchi"));
        return ratio;
    }

    private double calculateTimeFactor(LocalDateTime now, List<SurgeFactor> factors) {
        int hour = now.getHour();
        double factor = 0.0;
        String reason = "Oddiy vaqt";

        if (hour >= 7 && hour <= 9) {
            factor = 0.3;
            reason = "Tong rush soati (07:00-09:00)";
        } else if (hour >= 17 && hour <= 20) {
            factor = 0.4;
            reason = "Kechki rush soati (17:00-20:00)";
        } else if (hour >= 23 || hour <= 4) {
            factor = 0.25;
            reason = "Tungi soat (23:00-04:00)";
        }

        factors.add(new SurgeFactor("time_of_day", factor, reason));
        return factor;
    }

    private double calculateWeekdayFactor(LocalDateTime now, List<SurgeFactor> factors) {
        int dow = now.getDayOfWeek().getValue(); // 1=Mon, 7=Sun
        int hour = now.getHour();
        double factor = 0.0;
        String reason = "Oddiy kun";

        if ((dow == 5 || dow == 6) && hour >= 18) {
            factor = 0.3;
            reason = "Juma/Shanba kechasi";
        } else if (dow == 7 && hour >= 10 && hour <= 14) {
            factor = 0.15;
            reason = "Yakshanba kunduzi";
        }

        factors.add(new SurgeFactor("weekday", factor, reason));
        return factor;
    }

    private double calculateHolidayFactor(LocalDateTime now, List<SurgeFactor> factors) {
        MonthDay today = MonthDay.from(now);
        MonthDay yesterday = MonthDay.from(now.minusDays(1));
        double factor = 0.0;
        String reason = "Oddiy kun";

        if (HOLIDAYS.contains(today)) {
            factor = 0.4;
            reason = "Bayram kuni";
        } else if (HOLIDAYS.contains(yesterday) && now.getHour() <= 3) {
            factor = 0.3;
            reason = "Bayram kechasi";
        }
        // Navro'z haftaligi (21-23 mart)
        if (now.getMonthValue() == 3 && now.getDayOfMonth() >= 21 && now.getDayOfMonth() <= 23) {
            factor = Math.max(factor, 0.35);
            reason = "Navro'z bayrami";
        }

        factors.add(new SurgeFactor("holiday", factor, reason));
        return factor;
    }

    private double calculateWeatherFactor(LocalDateTime now, List<SurgeFactor> factors) {
        int month = now.getMonthValue();
        int hour = now.getHour();
        double factor = 0.0;
        String reason = "Ochiq havo";

        // Yomg'irli mavsumlar — kechqurun ehtimol yuqori
        if (RAINY_MONTHS.contains(month)) {
            if (hour >= 15 && hour <= 22) {
                factor = 0.15;
                reason = "Yomg'ir mavsumi (kechqurun)";
            } else {
                factor = 0.08;
                reason = "Yomg'ir mavsumi";
            }
        }
        // Qish — dekabr-fevral, 0 atrofidagi harorat
        if (month == 12 || month == 1 || month == 2) {
            factor = Math.max(factor, 0.12);
            reason = "Qishki mavsum (sovuq/muzlash)";
        }

        factors.add(new SurgeFactor("weather", factor, reason));
        return factor;
    }

    private double calculateZoneFactor(double lat, double lon, List<SurgeFactor> factors) {
        if (lat == 0 || lon == 0) {
            factors.add(new SurgeFactor("zone", 0.0, "Joylashuv aniqlanmadi"));
            return 0.0;
        }

        double distFromCenter = com.taxi.backend.service.DriverLocationCache
                .haversineKm(lat, lon, PARKENT_CENTER_LAT, PARKENT_CENTER_LON);

        double factor = 0.0;
        String reason;

        if (distFromCenter <= CENTER_RADIUS_KM) {
            factor = 0.1;
            reason = "Markaz zone (" + Math.round(distFromCenter * 10.0) / 10.0 + " km)";
        } else if (distFromCenter <= 10) {
            reason = "Shahar ichida (" + Math.round(distFromCenter * 10.0) / 10.0 + " km)";
        } else {
            factor = -0.1; // Chekkada arzonroq
            reason = "Shahar chekkasi (" + Math.round(distFromCenter * 10.0) / 10.0 + " km)";
        }

        factors.add(new SurgeFactor("zone", factor, reason));
        return factor;
    }

    private double calculateScarcityFactor(List<SurgeFactor> factors) {
        long online = driverRepository.countByIsOnlineTrueAndStatusACTIVE();
        double factor = 0.0;
        String reason;

        if (online == 0) {
            factor = 0.8;
            reason = "Haydovchi yo'q!";
        } else if (online <= 2) {
            factor = 0.4;
            reason = "Juda kam haydovchi (" + online + ")";
        } else if (online <= 5) {
            factor = 0.15;
            reason = "Kam haydovchi (" + online + ")";
        } else {
            reason = online + " ta haydovchi online";
        }

        factors.add(new SurgeFactor("scarcity", factor, reason));
        return factor;
    }

    private double calculateAcceptanceFactor(LocalDateTime now, List<SurgeFactor> factors) {
        LocalDateTime fiveMinAgo = now.minusMinutes(5);
        long searching = tripRepository.countByStatusAndCreatedAtAfter(TripStatus.SEARCHING, fiveMinAgo);
        double factor = 0.0;
        String reason = "Buyurtmalar tez qabul qilinmoqda";

        // Ko'p buyurtma qabul qilinmay qolsa — talab yuqori
        if (searching >= 10) {
            factor = 0.3;
            reason = searching + " ta buyurtma kutmoqda!";
        } else if (searching >= 5) {
            factor = 0.15;
            reason = searching + " ta buyurtma kutmoqda";
        }

        factors.add(new SurgeFactor("acceptance_rate", factor, reason));
        return factor;
    }
}
