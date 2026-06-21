package com.taxi.backend.service;

import com.taxi.backend.dto.response.*;
import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.enums.ServiceType;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.enums.TransactionType;
import com.taxi.backend.model.*;
import com.taxi.backend.model.Tariff;
import com.taxi.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DriverAppService {

    private static final Logger log = LoggerFactory.getLogger(DriverAppService.class);

    @Value("${app.commission.percent:10}") // TezYol commission rate — change here only
    private double commissionPercent;

    private final DriverRepository driverRepository;
    private final DriverServiceRepository driverServiceRepository;
    private final TransactionRepository transactionRepository;
    private final BroadcastMessageRepository broadcastMessageRepository;
    private final DriverPhotoRepository driverPhotoRepository;
    private final TripRepository tripRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final DriverLocationCache locationCache;
    private final TariffRepository tariffRepository;

    public DriverAppService(DriverRepository driverRepository,
            DriverServiceRepository driverServiceRepository,
            TransactionRepository transactionRepository,
            BroadcastMessageRepository broadcastMessageRepository,
            DriverPhotoRepository driverPhotoRepository,
            TripRepository tripRepository,
            SimpMessagingTemplate messagingTemplate,
            DriverLocationCache locationCache,
            TariffRepository tariffRepository) {
        this.driverRepository = driverRepository;
        this.driverServiceRepository = driverServiceRepository;
        this.transactionRepository = transactionRepository;
        this.broadcastMessageRepository = broadcastMessageRepository;
        this.driverPhotoRepository = driverPhotoRepository;
        this.tripRepository = tripRepository;
        this.messagingTemplate = messagingTemplate;
        this.locationCache = locationCache;
        this.tariffRepository = tariffRepository;
    }

    /** Haydovchi profilini olish */
    public DriverProfileResponse getProfile(User user) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));

        // Qabul/bekor foizlari
        long totalAssigned = tripRepository.countByDriverId(driver.getId());
        long completed = tripRepository.countByDriverIdAndStatus(driver.getId(), com.taxi.backend.enums.TripStatus.COMPLETED);
        long cancelledByDriver = tripRepository.countByDriverIdAndStatus(driver.getId(), com.taxi.backend.enums.TripStatus.CANCELLED_BY_DRIVER);
        long acceptRate = totalAssigned > 0 ? Math.round((double) completed / totalAssigned * 100) : 100;
        long cancelRate = totalAssigned > 0 ? Math.round((double) cancelledByDriver / totalAssigned * 100) : 0;

        String acceptedTariffs = driver.getAcceptedTariffs() != null
                ? driver.getAcceptedTariffs() : "EKONOM,DAMAS,BIZNES";

        // Mashina modeli FIZIK qaysi tariflarni bera oladi — app shu to'plamdan tanlaydi
        List<String> eligibleTariffs = new ArrayList<>(
                DriverTariffFilter.eligibleTariffs(driver.getCarModel(), driver.getTariffGrants()));

        return new DriverProfileResponse(driver.getId(), driver.getId(),
                user.getName(), user.getPhone(), driver.getDriverCode(),
                driver.getCarModel(), driver.getCarNumber(), driver.getCarColor(), driver.getCarYear(),
                driver.getStatus().name(), driver.isOnline(), driver.getRating(),
                driver.getTotalTrips(), driver.getBalance(), driver.getTechPassportNumber(),
                completed, cancelledByDriver, acceptRate, cancelRate, acceptedTariffs,
                eligibleTariffs);
    }

    /** A3 — Barcha aktiv tariflar + shu haydovchi uchun eligible/accepted bayroqlari (A2 grantlarini hisobga oladi). */
    public List<Map<String, Object>> getDriverTariffs(User user) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        java.util.Set<String> eligible =
                DriverTariffFilter.eligibleTariffs(driver.getCarModel(), driver.getTariffGrants());
        java.util.Set<String> accepted = new java.util.HashSet<>();
        if (driver.getAcceptedTariffs() != null) {
            for (String s : driver.getAcceptedTariffs().split(",")) {
                String n = s.trim().toUpperCase();
                if (!n.isBlank()) accepted.add(n);
            }
        }
        return tariffRepository.findByIsActiveTrue().stream()
                .map(t -> {
                    String name = t.getName() == null ? "" : t.getName().trim().toUpperCase();
                    boolean isEligible = eligible.contains(name);
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("name", t.getName());
                    m.put("basePrice", t.getBasePrice());
                    m.put("pricePerKm", t.getPricePerKm());
                    m.put("pricePerMin", t.getPricePerMin());
                    m.put("minPrice", t.getMinPrice());
                    m.put("eligible", isEligible);
                    m.put("accepted", isEligible && accepted.contains(name));
                    return m;
                })
                .collect(Collectors.toList());
    }

    /** Avtomobil ma'lumotlarini yangilash */
    @Transactional
    public Map<String, Object> updateProfile(User user, String carModel, String carNumber, String carColor, Integer carYear) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        if (carModel != null && !carModel.isBlank()) driver.setCarModel(carModel.trim());
        if (carNumber != null && !carNumber.isBlank()) driver.setCarNumber(carNumber.trim());
        if (carColor != null && !carColor.isBlank()) driver.setCarColor(carColor.trim());
        if (carYear != null && carYear > 1990 && carYear <= 2030) driver.setCarYear(carYear);
        driverRepository.save(driver);
        return java.util.Map.of(
                "carModel", driver.getCarModel() != null ? driver.getCarModel() : "",
                "carNumber", driver.getCarNumber() != null ? driver.getCarNumber() : "",
                "carColor", driver.getCarColor() != null ? driver.getCarColor() : "",
                "carYear", driver.getCarYear() != null ? driver.getCarYear() : 0
        );
    }

    /** Online/Offline almashtirish — Redis + DB + WebSocket */
    @Transactional
    public Map<String, Object> toggleOnlinePlain(User user) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        if (driver.getStatus() != DriverStatus.ACTIVE)
            throw new RuntimeException("Siz hali tasdiqlanmagansiz");

        boolean newOnline = !driver.isOnline();
        driver.setOnline(newOnline);
        driver.setUpdatedAt(LocalDateTime.now());
        // Online bo'lganda — "bo'sh bo'lgan vaqt" (free_since) yangilanadi (matching tie-break uchun)
        if (newOnline) {
            driver.setFreeSince(LocalDateTime.now());
        }
        driverRepository.save(driver);

        // Redis/Cache da ham yangilash
        if (newOnline) {
            locationCache.setOnline(driver.getId());
            // DB'dan oxirgi bilgan joylashuvni yuklash (fake emas!)
            Double lastLat = driver.getLatitude();
            Double lastLon = driver.getLongitude();
            if (lastLat != null && lastLon != null && lastLat != 0.0 && lastLon != 0.0) {
                locationCache.saveLocation(driver.getId(), lastLat, lastLon);
            } else {
                // Hech qachon lokatsiya yubormagan — Parkent markazi (default)
                locationCache.saveLocation(driver.getId(), 41.2950, 69.6770);
                driver.setLatitude(41.2950);
                driver.setLongitude(69.6770);
                driverRepository.save(driver);
            }
        } else {
            locationCache.setOffline(driver.getId());
        }

        messagingTemplate.convertAndSend("/topic/admin/driver-status",
                Map.of("driverId", driver.getId(), "isOnline", newOnline));
        return Map.of("isOnline", newOnline);
    }

    /** Online/Offline almashtirish — state bilan (eski API) */
    @Transactional
    public Map<String, Object> toggleOnline(User user, boolean online) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        if (driver.getStatus() != DriverStatus.ACTIVE)
            throw new RuntimeException("Siz hali tasdiqlanmagansiz");
        driver.setOnline(online);
        driver.setUpdatedAt(LocalDateTime.now());
        driverRepository.save(driver);
        messagingTemplate.convertAndSend("/topic/admin/driver-status",
                Map.of("driverId", driver.getId(), "isOnline", online));
        return Map.of("isOnline", online);
    }

    /** Lokatsiyani yangilash — Redis cache + DB + WebSocket + GPS Spoofing himoyasi */
    public void updateLocation(User user, Double lat, Double lon) {
        // ANTI-FRAUD: Koordinata validatsiyasi
        if (lat == null || lon == null || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            log.warn("[GPS] Noto'g'ri koordinata: userId={}, lat={}, lon={}", user.getId(), lat, lon);
            return;
        }

        driverRepository.findByUserId(user.getId()).ifPresent(driver -> {
            // ANTI-FRAUD: GPS Spoofing detection — tezlik limiti
            Double prevLat = driver.getLatitude();
            Double prevLon = driver.getLongitude();
            if (prevLat != null && prevLon != null && prevLat != 0 && prevLon != 0) {
                double distKm = DriverLocationCache.haversineKm(prevLat, prevLon, lat, lon);
                LocalDateTime lastUpdate = driver.getUpdatedAt();
                if (lastUpdate != null) {
                    long secondsElapsed = java.time.Duration.between(lastUpdate, LocalDateTime.now()).getSeconds();
                    if (secondsElapsed > 0 && secondsElapsed < 300) { // 5 daqiqadan kam
                        double speedKmH = (distKm / secondsElapsed) * 3600;
                        // 200 km/h dan tez — GPS spoofing
                        if (speedKmH > 200) {
                            log.warn("[GPS-SPOOF] Tezlik limiti oshdi: driverId={}, speed={} km/h, dist={} km, time={} s",
                                    driver.getId(), Math.round(speedKmH), Math.round(distKm * 10.0) / 10.0, secondsElapsed);
                            return; // Soxta koordinatani qabul qilmaslik
                        }
                    }
                }
            }

            // 1. Redis/Memory cache ga tezkor yozish (matching engine ishlatadi)
            locationCache.saveLocation(driver.getId(), lat, lon);

            // 2. DB'ga ham yozish (tarix uchun)
            driver.setLatitude(lat);
            driver.setLongitude(lon);
            driver.setUpdatedAt(LocalDateTime.now());
            driverRepository.save(driver);

            // 3. WebSocket orqali yo'lovchiga real-time tracking
            messagingTemplate.convertAndSend("/topic/driver-location/" + driver.getId(),
                    Map.of("lat", lat, "lon", lon, "driverId", driver.getId(),
                            "ts", System.currentTimeMillis()));
        });
    }

    /** Xizmatlar ro'yxati — mobile app uchun */
    public List<ServiceResponse> getServices(User user) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        return driverServiceRepository.findByDriverId(driver.getId()).stream().map(s ->
                new ServiceResponse(s.getId(), s.getServiceType().name(), s.isEnabled(), s.getExtraPrice())
        ).collect(Collectors.toList());
    }

    /** Xizmatni id bo'yicha toggle */
    @Transactional
    public Map<String, Object> toggleServiceById(User user, Long serviceId) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        DriverService ds = driverServiceRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Xizmat topilmadi"));
        if (!ds.getDriver().getId().equals(driver.getId()))
            throw new RuntimeException("Bu xizmat sizniki emas");
        ds.setEnabled(!ds.isEnabled());
        driverServiceRepository.save(ds);
        return Map.of("id", serviceId, "isActive", ds.isEnabled());
    }

    /** Xizmat toggle */
    @Transactional
    public Map<String, Object> toggleService(User user, ServiceType serviceType, boolean enabled) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));

        DriverService ds = driverServiceRepository.findByDriverIdAndServiceType(driver.getId(), serviceType)
                .orElseThrow(() -> new RuntimeException("Xizmat topilmadi"));

        ds.setEnabled(enabled);
        driverServiceRepository.save(ds);

        return Map.of("type", serviceType.name(), "enabled", enabled);
    }

    /** Balans — sodda (BalanceScreen uchun) */
    public BalanceResponse getBalanceSimple(User user) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        return new BalanceResponse(driver.getBalance());
    }

    /** Tranzaksiyalar (alohida endpoint) */
    public List<TransactionResponse> getTransactions(User user, Pageable pageable) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        return transactionRepository.findByDriverIdOrderByCreatedAtDesc(driver.getId(), pageable)
                .getContent().stream().map(t ->
                    new TransactionResponse(t.getId(), t.getType().name(), t.getAmount(),
                            t.getDescription(), t.getCreatedAt().toString())
                ).collect(Collectors.toList());
    }

    /** Balans va tranzaksiyalar (eski endpoint) */
    public Map<String, Object> getBalance(User user, Pageable pageable) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        List<TransactionResponse> items = getTransactions(user, pageable);
        Map<String, Object> result = new HashMap<>();
        result.put("balance", driver.getBalance());
        result.put("driverId", driver.getId());
        result.put("transactions", items);
        return result;
    }

    /** Balans to'ldirish — atomic DB update (race condition himoyasi) */
    @Transactional
    public Map<String, Object> topupBalance(User user, Long amount) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        long before = driver.getBalance();

        // Atomic DB update — concurrent so'rovlar xavfsiz
        driverRepository.addToBalance(driver.getId(), amount);

        Transaction tx = new Transaction();
        tx.setDriver(driver);
        tx.setType(TransactionType.TOPUP);
        tx.setAmount(amount);
        tx.setBalanceBefore(before);
        tx.setBalanceAfter(before + amount);
        tx.setDescription("Payme orqali to'ldirildi");
        transactionRepository.save(tx);

        return Map.of("balance", before + amount);
    }

    /** Bugungi statistika — optimallashtirilgan (1 ta DB query bilan) */
    public DriverStatsResponse getStats(User user) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart = LocalDate.now().minusDays(6).atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        // Bitta query bilan oylik barcha triplarni olish (kunlik breakdown ham shu ichida)
        List<Trip> monthTrips = tripRepository.findByDriverIdAndStatusAndCreatedAtBetween(
                driver.getId(), TripStatus.COMPLETED, monthStart, now);

        // Java'da filterlash (DB query'lar o'rniga)
        long todayTripsCount = 0, todayEarnings = 0;
        long weekTripsCount = 0, weekEarnings = 0;
        long monthEarnings = 0;

        // Kunlik breakdown uchun map
        Map<LocalDate, long[]> dailyMap = new HashMap<>();
        for (int i = 6; i >= 0; i--) {
            dailyMap.put(LocalDate.now().minusDays(i), new long[]{0, 0}); // [trips, earnings]
        }

        for (Trip t : monthTrips) {
            long price = t.getTotalPrice() != null ? t.getTotalPrice() : 0L;
            monthEarnings += price;

            LocalDate tripDate = t.getCreatedAt().toLocalDate();

            // Bugungi
            if (!t.getCreatedAt().isBefore(todayStart)) {
                todayTripsCount++;
                todayEarnings += price;
            }
            // Haftalik
            if (!t.getCreatedAt().isBefore(weekStart)) {
                weekTripsCount++;
                weekEarnings += price;
            }
            // Kunlik breakdown
            long[] dayStats = dailyMap.get(tripDate);
            if (dayStats != null) {
                dayStats[0]++;
                dayStats[1] += price;
            }
        }

        long todayCommission = Math.round(todayEarnings * commissionPercent / 100.0);

        // Kunlik breakdown ro'yxat
        List<DriverStatsResponse.DayStats> daily = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            long[] stats = dailyMap.get(date);
            daily.add(new DriverStatsResponse.DayStats(date.toString(),
                    stats != null ? stats[0] : 0, stats != null ? stats[1] : 0));
        }

        return new DriverStatsResponse(todayTripsCount, todayEarnings,
                todayEarnings - todayCommission, todayCommission,
                weekTripsCount, weekEarnings, monthTrips.size(), monthEarnings,
                driver.getRating() != null ? driver.getRating() : java.math.BigDecimal.valueOf(5.0),
                driver.getTotalTrips(), daily);
    }

    /**
     * A4 — Haydovchi daromadi chart uchun: kunlik (oxirgi 30 kun), haftalik (oxirgi 12 hafta,
     * Dushanba boshi), oylik (oxirgi 12 oy). earnings = COMPLETED triplar totalPrice yig'indisi
     * (tiyin), createdAt bo'yicha. Har bir bucket: {label, trips, earnings}. Bo'sh bucketlar 0 bilan
     * to'ldiriladi (uzluksiz chart). 12 oylik bitta query — kunlik/haftalik shu oyna ichida.
     */
    public Map<String, Object> getEarningsChart(User user) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = today.minusMonths(11).withDayOfMonth(1).atStartOfDay();
        List<Trip> trips = tripRepository.findByDriverIdAndStatusAndCreatedAtBetween(
                driver.getId(), TripStatus.COMPLETED, windowStart, now);

        Map<LocalDate, long[]> dailyMap = new java.util.LinkedHashMap<>();
        for (int i = 29; i >= 0; i--) dailyMap.put(today.minusDays(i), new long[]{0, 0});

        Map<LocalDate, long[]> weeklyMap = new java.util.LinkedHashMap<>();
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        for (int i = 11; i >= 0; i--) weeklyMap.put(weekStart.minusWeeks(i), new long[]{0, 0});

        Map<java.time.YearMonth, long[]> monthlyMap = new java.util.LinkedHashMap<>();
        java.time.YearMonth thisMonth = java.time.YearMonth.from(today);
        for (int i = 11; i >= 0; i--) monthlyMap.put(thisMonth.minusMonths(i), new long[]{0, 0});

        for (Trip t : trips) {
            long price = t.getTotalPrice() != null ? t.getTotalPrice() : 0L;
            LocalDate d = t.getCreatedAt().toLocalDate();
            long[] day = dailyMap.get(d);
            if (day != null) { day[0]++; day[1] += price; }
            long[] wk = weeklyMap.get(d.with(java.time.DayOfWeek.MONDAY));
            if (wk != null) { wk[0]++; wk[1] += price; }
            long[] mo = monthlyMap.get(java.time.YearMonth.from(d));
            if (mo != null) { mo[0]++; mo[1] += price; }
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("daily", earningsBuckets(dailyMap, java.time.LocalDate::toString));
        result.put("weekly", earningsBuckets(weeklyMap, java.time.LocalDate::toString));
        result.put("monthly", earningsBuckets(monthlyMap, java.time.YearMonth::toString));
        return result;
    }

    private <K> List<Map<String, Object>> earningsBuckets(Map<K, long[]> map,
            java.util.function.Function<K, String> label) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<K, long[]> e : map.entrySet()) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("label", label.apply(e.getKey()));
            m.put("trips", e.getValue()[0]);
            m.put("earnings", e.getValue()[1]);
            out.add(m);
        }
        return out;
    }

    /** Rasmlar ro'yxati */
    public List<Map<String, Object>> getPhotos(User user) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        return driverPhotoRepository.findByDriverId(driver.getId()).stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("photoType", p.getPhotoType().name());
            m.put("url", p.getPhotoUrl());
            boolean approved = p.getStatus() == com.taxi.backend.enums.PhotoStatus.APPROVED;
            boolean rejected = p.getStatus() == com.taxi.backend.enums.PhotoStatus.REJECTED;
            m.put("approved", approved ? Boolean.TRUE : (rejected ? Boolean.FALSE : null));
            return m;
        }).collect(Collectors.toList());
    }

    /** Xabarlar tarixi */
    public List<Map<String, Object>> getMessages(Pageable pageable) {
        return broadcastMessageRepository.findAllByOrderBySentAtDesc(pageable).map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("title", m.getTitle());
            map.put("body", m.getContent());
            map.put("sentAt", m.getSentAt().toString());
            return map;
        }).getContent();
    }

    private String getServiceUzName(ServiceType type) {
        return switch (type) {
            case DELIVERY -> "Dastavka";
            case ROOF_LUGGAGE -> "Tom Bagaj";
            case REAR_LUGGAGE -> "Orqa Bagaj";
            case AC -> "Konditsioner";
            case CABIN_CARGO -> "Salonga Yuk";
        };
    }

    /** Qabul qiluvchi tariflarni yangilash */
    @Transactional
    public Map<String, Object> updateAcceptedTariffs(User user, List<String> tariffNames) {
        if (tariffNames == null || tariffNames.isEmpty())
            throw new RuntimeException("Kamida bitta tarif tanlanishi kerak");

        List<String> activeTariffNames = tariffRepository.findByIsActiveTrue().stream()
                .map(t -> t.getName().toUpperCase()).toList();

        List<String> valid = tariffNames.stream()
                .map(String::toUpperCase)
                .filter(activeTariffNames::contains)
                .distinct().toList();

        if (valid.isEmpty())
            throw new RuntimeException("Tanlangan tariflar noto'g'ri: " + tariffNames);

        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));

        String value = String.join(",", valid);
        driver.setAcceptedTariffs(value);
        driverRepository.save(driver);

        return Map.of("acceptedTariffs", value);
    }

    /** Haydovchi sozlamalari (allows_pets va boshqalar) */
    public Map<String, Object> getPreferences(User user) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        return Map.of("allowsPets", driver.isAllowsPets());
    }

    @Transactional
    public Map<String, Object> updatePreferences(User user, Map<String, Object> body) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        if (body.containsKey("allowsPets")) {
            driver.setAllowsPets(Boolean.TRUE.equals(body.get("allowsPets")));
        }
        driverRepository.save(driver);
        return Map.of("allowsPets", driver.isAllowsPets());
    }

    /** Haydovchi joylashuvini olish (admin map uchun) */
    public Map<String, Object> getSimpleBalance(User user) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        return Map.of("balance", driver.getBalance());
    }
}
