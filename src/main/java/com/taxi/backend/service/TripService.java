package com.taxi.backend.service;

import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.enums.TransactionType;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.kafka.TripEvent;
import com.taxi.backend.kafka.TripEventProducer;
import com.taxi.backend.model.*;
import com.taxi.backend.repository.*;
import com.taxi.backend.repository.RatingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TripService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TripService.class);

    private final TripRepository tripRepository;
    private final DriverRepository driverRepository;
    private final TariffRepository tariffRepository;
    private final TransactionRepository transactionRepository;
    private final RatingRepository ratingRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MatchingService matchingService;
    private final SurgePricingService surgePricingService;
    private final Optional<TripEventProducer> eventProducer;
    private final PushNotificationService pushService;
    private final PromoCodeService promoCodeService;
    private final AsyncNotificationService asyncNotifier;
    private final TripNotificationHelper notificationHelper;
    private final SecurityMonitorService securityMonitor;
    private final SmsInviteService smsInviteService;
    private final com.taxi.backend.pricing.NightFareService nightFareService;
    private final ReferralService referralService;

    @Value("${matching.radius-km:5.0}")
    private double matchingRadiusKm;

    @Value("${app.commission.percent:10}") // TezYol commission rate — change here only
    private double commissionPercent;

    @Value("${app.waiting.price-per-minute:200000}")
    private long waitingPricePerMinute;

    @Value("${app.waiting.free-seconds:60}")
    private long waitingFreeSeconds;

    @Value("${app.waiting.trip-price-per-minute:250000}")
    private long tripWaitingPricePerMinute;

    @Value("${app.cancellation.max-driver:3}") // shuncha marta bekor qilinsa trip yakuniy CANCELLED
    private int maxDriverCancellations;

    @Value("${matching.decline-cooldown-seconds:60}") // rad etishdan keyin yangi buyurtma olmaslik (soniya)
    private long declineCooldownSeconds;

    /**
     * Trip COMPLETED bo'lganda haydovchi va yo'lovchiga "Sayohat yakunlandi! 🏁 / Baholashni
     * unutmang" push xabari. Reyting tizimi olib tashlangani uchun DEFAULT=false (push yo'q,
     * tovush yo'q). Reyting qaytarilganda qayta yoqish uchun: tezyol.env'da
     * TRIP_COMPLETION_PUSH_ENABLED=true qiling va tezyol.service'ni qayta ishga tushiring —
     * kod o'zgarishi shart emas. Flag faqat ikkala COMPLETED push chaqirig'iga ta'sir qiladi;
     * DRIVER_ARRIVED push, WebSocket broadcast, Kafka TRIP_COMPLETED event, balans krediti,
     * referral mukofoti, DB status saqlash — barchasi har doim ishlaydi.
     */
    @Value("${tezgo.trip-completion-push.enabled:false}")
    private boolean tripCompletionPushEnabled;

    public TripService(TripRepository tripRepository,
            DriverRepository driverRepository,
            TariffRepository tariffRepository,
            TransactionRepository transactionRepository,
            RatingRepository ratingRepository,
            SimpMessagingTemplate messagingTemplate,
            MatchingService matchingService,
            SurgePricingService surgePricingService,
            Optional<TripEventProducer> eventProducer,
            PushNotificationService pushService,
            PromoCodeService promoCodeService,
            AsyncNotificationService asyncNotifier,
            TripNotificationHelper notificationHelper,
            SecurityMonitorService securityMonitor,
            SmsInviteService smsInviteService,
            com.taxi.backend.pricing.NightFareService nightFareService,
            ReferralService referralService) {
        this.tripRepository = tripRepository;
        this.driverRepository = driverRepository;
        this.tariffRepository = tariffRepository;
        this.transactionRepository = transactionRepository;
        this.ratingRepository = ratingRepository;
        this.messagingTemplate = messagingTemplate;
        this.matchingService = matchingService;
        this.surgePricingService = surgePricingService;
        this.eventProducer = eventProducer;
        this.pushService = pushService;
        this.promoCodeService = promoCodeService;
        this.asyncNotifier = asyncNotifier;
        this.notificationHelper = notificationHelper;
        this.securityMonitor = securityMonitor;
        this.smsInviteService = smsInviteService;
        this.nightFareService = nightFareService;
        this.referralService = referralService;
    }

    /** Narx hisoblash — surge pricing bilan */
    public Map<String, Object> estimate(Long tariffId, Double distanceKm) {
        return estimate(tariffId, distanceKm, 0.0, 0.0);
    }

    public Map<String, Object> estimate(Long tariffId, Double distanceKm, double lat, double lon) {
        if (distanceKm != null && (distanceKm <= 0 || distanceKm > 500))
            throw new RuntimeException("Masofa 0 dan katta va 500 km dan kichik bo'lishi kerak");
        Tariff tariff = tariffRepository.findById(tariffId)
                .orElseThrow(() -> new RuntimeException("Tarif topilmadi"));

        // 10 km dan ortiq bo'lsa har km narxiga +1000 so'm (100000 tiyin) qo'shiladi
        long effectivePricePerKm = tariff.getPricePerKm();
        if (distanceKm > 10) {
            effectivePricePerKm += 100000;
        }
        long basePrice = tariff.getBasePrice() + (long) (distanceKm * effectivePricePerKm);

        // Tungi tarif — base ga bir martalik ustama. Qaror biznes-zonada (Asia/Tashkent)
        // Clock orqali — JVM zonasidan mustaqil. Tartib: night (additiv) → surge (×).
        boolean night = nightFareService.isNightNow();
        basePrice = nightFareService.applyToBaseNow(basePrice);

        // Surge pricing
        com.taxi.backend.pricing.SurgeResult surge = surgePricingService.calculate(basePrice, lat, lon);

        long estimatedTiyin = surge.finalPriceTiyin();
        Map<String, Object> result = new HashMap<>();
        result.put("tariffId", tariffId);
        result.put("tariffName", tariff.getName());
        result.put("distanceKm", distanceKm);
        result.put("basePrice", basePrice / 100);
        result.put("estimatedPrice", estimatedTiyin / 100);
        result.put("estimatedPriceFormatted", String.format("%,d UZS", estimatedTiyin / 100));
        result.put("minFare", estimatedTiyin * 8 / 10 / 100);
        result.put("maxFare", estimatedTiyin * 12 / 10 / 100);
        result.put("surgeMultiplier", surge.multiplier());
        result.put("surgeLevel", surge.level());
        // Tungi tarif — passenger app to'g'ri label ko'rsatishi uchun (surge emas)
        result.put("isNight", night);
        result.put("nightSurcharge", night ? nightFareService.surchargeSom() : 0);
        if (distanceKm > 10) {
            result.put("longDistanceExtra", 1000);
            result.put("effectivePricePerKm", effectivePricePerKm / 100);
        }
        return result;
    }

    /** Buyurtma berish */
    @Transactional
    public Map<String, Object> bookTrip(User passenger, Long tariffId,
            Double fromLat, Double fromLon, String fromAddress,
            Double toLat, Double toLon, String toAddress,
            Double distanceKm) {
        return bookTrip(passenger, tariffId, fromLat, fromLon, fromAddress,
                toLat, toLon, toAddress, distanceKm, null, null, "APP");
    }

    /** Buyurtma berish — promo kod bilan + anomaliya monitoring */
    @Transactional
    public Map<String, Object> bookTrip(User passenger, Long tariffId,
            Double fromLat, Double fromLon, String fromAddress,
            Double toLat, Double toLon, String toAddress,
            Double distanceKm, String promoCode) {
        return bookTrip(passenger, tariffId, fromLat, fromLon, fromAddress,
                toLat, toLon, toAddress, distanceKm, promoCode, null, "APP");
    }

    /** Buyurtma berish — promo kod + source (offeredFare'siz; orqaga-moslik) */
    @Transactional
    public Map<String, Object> bookTrip(User passenger, Long tariffId,
            Double fromLat, Double fromLon, String fromAddress,
            Double toLat, Double toLon, String toAddress,
            Double distanceKm, String promoCode, String source) {
        return bookTrip(passenger, tariffId, fromLat, fromLon, fromAddress,
                toLat, toLon, toAddress, distanceKm, promoCode, null, source);
    }

    /** Buyurtma berish — promo kod + source (APP / APP_TAXOMETER) */
    @Transactional
    public Map<String, Object> bookTrip(User passenger, Long tariffId,
            Double fromLat, Double fromLon, String fromAddress,
            Double toLat, Double toLon, String toAddress,
            Double distanceKm, String promoCode, Long offeredFare, String source) {
        return bookTrip(passenger, tariffId, fromLat, fromLon, fromAddress,
                toLat, toLon, toAddress, distanceKm, promoCode, offeredFare, source, null);
    }

    /** Buyurtma berish — to'liq (rejalashtirilgan buyurtma scheduledAt bilan; NULL = darhol) */
    @Transactional
    public Map<String, Object> bookTrip(User passenger, Long tariffId,
            Double fromLat, Double fromLon, String fromAddress,
            Double toLat, Double toLon, String toAddress,
            Double distanceKm, String promoCode, Long offeredFare, String source,
            java.time.LocalDateTime scheduledAt) {

        boolean isTaxometer = "APP_TAXOMETER".equals(source);
        // Rejalashtirilgan buyurtma (kelajak vaqt)? Bo'lsa — darhol dispatch QILINMAYDI;
        // scheduler vaqti kelganda SEARCHING ga o'tkazib haydovchilarga yuboradi.
        boolean isScheduled = scheduledAt != null && scheduledAt.isAfter(java.time.LocalDateTime.now());

        if (!isTaxometer) {
            if (distanceKm != null && distanceKm <= 0)
                throw new RuntimeException("Masofa musbat bo'lishi kerak");
            if (distanceKm != null && distanceKm > 500)
                throw new RuntimeException("Masofa 500 km dan oshmasligi kerak");
        }

        // XAVFSIZLIK: Server-side masofa tekshiruvi — client manipulyatsiyasidan himoya
        if (!isTaxometer) {
            double haversineKm = DriverLocationCache.haversineKm(fromLat, fromLon, toLat, toLon);
            if (haversineKm > 0.5) { // 500m dan uzoq manzillar uchun tekshirish
                if (distanceKm < haversineKm * 0.7) {
                    log.warn("Masofa manipulyatsiya urinishi: client={}km, haversine={}km, user={}",
                            distanceKm, haversineKm, passenger.getPhone());
                    distanceKm = haversineKm * 1.3;
                }
                if (distanceKm > haversineKm * 5) {
                    throw new RuntimeException("Masofa juda katta — qayta urining");
                }
            }
        }

        // Rejalashtirilgan buyurtma kelajak uchun — joriy/faol safarga tegmaydi
        // (cancel-prior va active-check faqat DARHOL buyurtma uchun).
        if (!isScheduled) {
            // Haydovchi jalb etilmagan SEARCHING triplarni avtomatik bekor qilish
            tripRepository.findFirstByPassengerIdAndStatusIn(
                    passenger.getId(), List.of(TripStatus.SEARCHING))
                    .ifPresent(old -> {
                        old.setStatus(TripStatus.CANCELLED_BY_PASSENGER);
                        tripRepository.save(old);
                    });

            // Haydovchi allaqachon jalb etilgan bo'lsa — blok
            List<TripStatus> activeWithDriver = List.of(
                    TripStatus.ACCEPTED, TripStatus.DRIVER_ARRIVED, TripStatus.STARTED);
            boolean hasActiveWithDriver = tripRepository
                    .findFirstByPassengerIdAndStatusIn(passenger.getId(), activeWithDriver).isPresent();
            if (hasActiveWithDriver)
                throw new RuntimeException("Haydovchi allaqachon qabul qilgan. Safarni yakunlang yoki bekor qiling.");
        }

        Tariff tariff = tariffRepository.findById(tariffId)
                .orElseThrow(() -> new RuntimeException("Tarif topilmadi"));

        // Narx hisoblash: taxometr rejimida faqat boshlang'ich narx
        long basePrice;
        if (isTaxometer) {
            basePrice = tariff.getBasePrice();
        } else {
            long effectivePricePerKm = tariff.getPricePerKm();
            if (distanceKm > 10) effectivePricePerKm += 100000;
            basePrice = tariff.getBasePrice() + (long) (distanceKm * effectivePricePerKm);
        }

        // Tungi tarif — base ga bir martalik ustama (biznes-zona, hozir). Per-km tegilmaydi.
        // estimate() bilan bir xil mantiq → ko'rsatilgan narx = olinadigan narx.
        basePrice = nightFareService.applyToBaseNow(basePrice);

        // Surge pricing qo'llash (night-li base ustiga ko'paytma)
        com.taxi.backend.pricing.SurgeResult surge = surgePricingService.calculate(basePrice, fromLat, fromLon);
        long price = surge.finalPriceTiyin();

        // Promo kod chegirmasi
        long promoDiscount = 0;
        if (promoCode != null && !promoCode.isBlank()) {
            promoDiscount = promoCodeService.applyPromo(promoCode, price);
            price = Math.max(price - promoDiscount, 0);
        }

        Trip trip = new Trip();
        trip.setPassenger(passenger);
        trip.setTariff(tariff);
        trip.setFromLat(fromLat);
        trip.setFromLon(fromLon);
        trip.setFromAddress(fromAddress);
        trip.setToLat(toLat);
        trip.setToLon(toLon);
        trip.setToAddress(toAddress);
        trip.setDistanceKm(isTaxometer ? BigDecimal.ZERO : BigDecimal.valueOf(distanceKm));
        final long meteredPrice = price;
        // Fare-bidding: yo'lovchi narx taklif qilgan bo'lsa — totalPrice = taklif (basePrice metered qoladi)
        final long finalPrice = (offeredFare != null && offeredFare > 0) ? offeredFare : price; // lambda uchun effectively final
        trip.setBasePrice(meteredPrice);
        trip.setTotalPrice(finalPrice);
        trip.setOfferedFare(offeredFare);
        if (isScheduled) {
            trip.setScheduledAt(scheduledAt);
            trip.setStatus(TripStatus.SCHEDULED);
        } else {
            trip.setStatus(TripStatus.SEARCHING);
        }
        trip.setSource(source != null ? source : "APP");
        Trip saved = tripRepository.save(trip);

        // SECURITY: Anomaliya monitoring — 50+ trip/soat = alert
        securityMonitor.trackTripCreation(passenger.getId());

        // Matching Engine: yaqin haydovchilarga WebSocket xabari (async).
        // Rejalashtirilgan buyurtma — hozir yuborilmaydi; scheduler vaqti kelganda yuboradi.
        if (!isScheduled) notificationHelper.notifyNearbyDrivers(saved);

        // Kafka event
        eventProducer.ifPresent(p -> p.publish(TripEvent.of(
                "TRIP_CREATED", saved.getId(), passenger.getId(), null,
                "SEARCHING", finalPrice, fromLat, fromLon, fromAddress,
                toAddress != null ? toAddress : "")));

        Map<String, Object> result = tripToMap(saved);
        result.put("estimatedPrice", finalPrice / 100);
        result.put("surgeLevel", surge.level());
        result.put("surgeMultiplier", surge.multiplier());
        if (promoDiscount > 0) {
            result.put("promoDiscount", promoDiscount / 100);
            result.put("promoCode", promoCode.toUpperCase());
        }
        return result;
    }

    /**
     * Haydovchi — qabul qilish (Optimistic Lock — @Version bilan race condition himoyasi).
     *
     * PESSIMISTIC vs OPTIMISTIC Lock tahlili:
     *
     * Pessimistic (FOR UPDATE):
     *   - 10,000 haydovchi → 9,999 tasi DB connection ushlab KUTADI
     *   - HikariCP default pool = 10 connection → 9,990 so'rov queue da
     *   - Wait timeout = 30s → 30s * 10,000 = server "o'ladi"
     *   - Foyda: 100% garantiya, lekin bottleneck
     *
     * Optimistic (@Version):
     *   - 10,000 haydovchi → barchasi parallel SELECT qiladi (lock yo'q)
     *   - Birinchi save() — muvaffaqiyat (version 0 → 1)
     *   - Qolgan 9,999 save() → OptimisticLockException (version mos kelmaydi)
     *   - DB connection BAND BO'LMAYDI — darhol xato qaytaradi
     *   - Foyda: scalable, bottleneck yo'q
     *
     * Xulosa: Optimistic Lock bu ssenariy uchun 100x yaxshiroq.
     */
    @Transactional
    public Map<String, Object> acceptTrip(User driverUser, Long tripId) {
        Driver driver = driverRepository.findByUserId(driverUser.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));

        // Strict: manfiy balansda (>= 0 talab) buyurtma qabul qilib bo'lmaydi
        if (driver.getBalance() != null && driver.getBalance() < 0) {
            throw new RuntimeException("Balansingiz manfiy — buyurtma olish uchun to'ldiring");
        }

        // Faol tripi bor haydovchi yangi buyurtma qabul qila olmaydi
        if (tripRepository.existsByDriverIdAndStatusIn(driver.getId(), TripStatus.ACTIVE_DRIVER_STATUSES)) {
            throw new RuntimeException("Sizda faol buyurtma bor");
        }

        // Optimistic Lock: @Version field trip.save() da avtomatik tekshiriladi
        // Agar boshqa haydovchi oldin save qilgan bo'lsa → OptimisticLockException
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));

        // Rad etish cooldown'idagi haydovchi qabul qila olmaydi — AMMO operator/admin (CALL/CALL_TAXOMETER)
        // buyurtmasi cooldown'ni E'TIBORSIZ qoldiradi (getAvailableTrips ro'yxati + MatchingService push
        // bilan AYNAN bir xil shart — TripNotificationHelper:60). Aks holда ro'yxatda KO'RINADIGAN operator
        // buyurtmasini haydovchi qabul qila olmay "kuting" xatosiga uchrardi (yarim tuzatish).
        boolean isOperatorOrder = trip.getSource() != null && trip.getSource().startsWith("CALL");
        if (!isOperatorOrder && driver.isInCooldown()) {
            throw new RuntimeException("Iltimos biroz kuting — yangi buyurtma tez orada");
        }

        if (trip.getStatus() != TripStatus.SEARCHING)
            throw new RuntimeException("Bu buyurtma allaqachon qabul qilingan");

        if (ExcludedDriverFilter.contains(trip.getExcludedDriverIds(), driver.getId()))
            throw new RuntimeException("Siz bu buyurtmani bekor qildingiz — qayta qabul qila olmaysiz");

        // Mashina-modeli hard-gate: buyurtma tarifi haydovchining mashinasiga FIZIK mos kelishi shart.
        // (Buyurtmalar ro'yxatini O'ZGARTIRMAYDI — haydovchi baribir barcha buyurtmalarni ko'radi,
        //  lekin mashinasiga mos kelmaydigan tarifni QABUL qila olmaydi.)
        String tariffName = trip.getTariff() != null ? trip.getTariff().getName() : null;
        if (tariffName != null && !tariffName.isBlank()) {
            Set<String> eligible = DriverTariffFilter.eligibleTariffs(driver.getCarModel(), driver.getTariffGrants());
            if (!eligible.contains(tariffName.trim().toUpperCase())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Bu tarif (" + tariffName + ") sizning mashinangizga ("
                                + (driver.getCarModel() == null ? "—" : driver.getCarModel())
                                + ") mos emas");
            }
        }

        // Service hard-filter: buyurtma talab qilgan barcha xizmatlar haydovchida yoqilgan bo'lishi shart
        if (!DriverServiceFilter.accepts(enabledServiceCodes(driver.getId()), trip.getSelectedServices()))
            throw new RuntimeException("Bu buyurtma uchun kerakli xizmatlar sizda yoqilmagan");

        trip.setDriver(driver);
        trip.setStatus(TripStatus.ACCEPTED);
        trip.setAcceptedAt(LocalDateTime.now());

        try {
            tripRepository.save(trip); // @Version → WHERE version = ? AND id = ?
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            // Boshqa haydovchi oldin qabul qildi — normal holat
            log.info("[ACCEPT] Buyurtma #{} boshqa haydovchi tomonidan qabul qilindi (driverId={})",
                    tripId, driver.getId());
            throw new RuntimeException("Bu buyurtma allaqachon qabul qilingan");
        }

        // Notification — async (DB tranzaksiyasidan keyin)
        asyncNotifier.notifyTripAsync(tripId,
                Map.of("status", "ACCEPTED", "driverId", driver.getId(),
                        "driverName", driverUser.getName(), "driverPhone", driverUser.getPhone(),
                        "carModel", driver.getCarModel(), "carNumber", driver.getCarNumber()));

        asyncNotifier.pushPassengerAsync(trip.getPassenger().getId(),
                "Haydovchi topildi! 🚗",
                driverUser.getName() + " — " + driver.getCarModel() + " (" + driver.getCarNumber() + ")",
                Map.of("tripId", tripId, "type", "ACCEPTED"));

        return tripToMap(trip);
    }

    /**
     * Haydovchi yangi kelgan buyurtmani RAD ETADI (decline).
     * Bu haydovchini excluded_driver_ids ga qo'shadi (idempotent) — endi shu tripni ko'rmaydi/ololmaydi.
     * Status O'ZGARMAYDI va cancel_count O'ZGARMAYDI (rad etish = qabul qilingan tripni bekor qilish emas,
     * shuning uchun strike/jarima yo'q). Trip boshqa haydovchilarga SEARCHING/taxtada ochiq qoladi.
     */
    @Transactional
    public Map<String, Object> declineTrip(User driverUser, Long tripId) {
        Driver driver = driverRepository.findByUserId(driverUser.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));

        trip.setExcludedDriverIds(
                ExcludedDriverFilter.append(trip.getExcludedDriverIds(), driver.getId()));
        tripRepository.save(trip);

        // Rad etish cooldown'i: shu haydovchi 60s davomida hech qanday yangi buyurtma olmaydi
        driver.setOrderCooldownUntil(LocalDateTime.now().plusSeconds(declineCooldownSeconds));
        driverRepository.save(driver);

        return Map.of("status", trip.getStatus().name(), "tripId", tripId, "declined", true);
    }

    /**
     * Layer 2c — buyurtma "QABUL QILINDI (ko'rsatildi)" ACK.
     * Haydovchi ilovasi IncomingOrderModal'ni EKRANDA ko'rsatgan zahoti chaqiriladi
     * ("qabul qilish" emas — shunchaki "men buni ko'rdim" signali). first_received_at NULL bo'lsa
     * o'rnatadi (birinchi ACK g'olib). Idempotent — takroriy chaqiruv hech narsa qilmaydi.
     *
     * Bu signal 2b eskalatsiyasini ANIQ qiladi: agar HECH BIR haydovchi ilovasi buyurtmani ko'rsatmagan
     * bo'lsa (push fonда o'lgan), operatorga eskalatsiya bo'ladi. Bu yerda ko'rsatilgan bo'lsa — eskalatsiya yo'q.
     *
     * Hech qachon xato tashlamaydi (haydovchi UI'sini bloklamaslik uchun) — noma'lum/ruxsatsiz holatda ham 200.
     * ADDITIVE: FSI/native/push contract'ga TEGILMAGAN.
     */
    @Transactional
    public Map<String, Object> markOrderReceived(User driverUser, Long tripId) {
        try {
            Driver driver = driverRepository.findByUserId(driverUser.getId()).orElse(null);
            Trip trip = tripRepository.findById(tripId).orElse(null);
            if (driver == null || trip == null) {
                return Map.of("tripId", tripId, "acked", false);
            }
            // Faqat shu trip uchun XABARDOR QILINGAN haydovchi ACK yubora oladi (soxta ACK'lardan himoya).
            if (!isDriverNotified(trip, driver.getId())) {
                return Map.of("tripId", tripId, "acked", false);
            }
            // Birinchi ACK g'olib — keyingilar no-op (idempotent).
            if (trip.getFirstReceivedAt() == null) {
                trip.setFirstReceivedAt(LocalDateTime.now());
                tripRepository.save(trip);
            }
            return Map.of("tripId", tripId, "acked", true);
        } catch (Exception e) {
            // Hech qachon bloklamaymiz — ACK best-effort.
            log.warn("[ACK] markOrderReceived xato (trip={}): {}", tripId, e.getMessage());
            return Map.of("tripId", tripId, "acked", false);
        }
    }

    /** trip.notifiedDriverIds (CSV) ichida driverId bormi? */
    private static boolean isDriverNotified(Trip trip, Long driverId) {
        String csv = trip.getNotifiedDriverIds();
        if (csv == null || csv.isBlank() || driverId == null) return false;
        for (String part : csv.split(",")) {
            if (part.trim().equals(String.valueOf(driverId))) return true;
        }
        return false;
    }

    /** Status almashtirish: ARRIVED → STARTED → COMPLETED */
    @Transactional
    public Map<String, Object> updateTripStatus(User driverUser, Long tripId, TripStatus newStatus) {
        Driver driver = driverRepository.findByUserId(driverUser.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));

        if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId()))
            throw new RuntimeException("Bu siz qabul qilgan buyurtma emas");

        // ── Status-transition guard (idempotency + re-completion himoyasi) ──
        // Money logikasidan (creditDriverBalance) OLDIN ishlaydi — terminal/teskari o'tish
        // hech qachon komissiyani ikkinchi marta hisoblamaydi. Faqat OLDINGA o'tish ruxsat.
        TripStatus current = trip.getStatus();
        if (TERMINAL_STATUSES.contains(current)) {
            // Yakunlangan/bekor qilingan tripni qayta o'zgartirib bo'lmaydi (re-charge yo'q)
            throw new RuntimeException(current == TripStatus.COMPLETED
                    ? "Buyurtma allaqachon yakunlangan"
                    : "Buyurtma allaqachon bekor qilingan");
        }
        if (forwardRank(newStatus) <= forwardRank(current)) {
            // Teskari (masalan COMPLETED->DRIVER_ARRIVED) yoki bir xil holatga o'tish — rad etiladi
            throw new RuntimeException("Buyurtma holatini bu tartibda o'zgartirib bo'lmaydi");
        }

        if (newStatus == TripStatus.DRIVER_ARRIVED) {
            // "Yetib keldim" — server arrival vaqtini yozadi va pullik kutishni avtomatik boshlaydi.
            // Kutish narxi shu paytdan: waitingFreeSeconds bepul, keyin waitingPricePerMinute (har soniya).
            LocalDateTime now = LocalDateTime.now();
            trip.setArrivedAt(now);
            if (trip.getWaitingStartedAt() == null) {
                trip.setWaitingStartedAt(now);
            }
        }
        if (newStatus == TripStatus.STARTED) {
            trip.setStartedAt(LocalDateTime.now());
            // Kutish narxini hisoblash: waitingFreeSeconds bepul, keyin har soniya uchun pul.
            // 600 so'm = 60000 tiyin per daqiqa = 1000 tiyin per soniya.
            if (trip.getWaitingStartedAt() != null && trip.getWaitingEndedAt() == null) {
                trip.setWaitingEndedAt(LocalDateTime.now());
                long waitingSeconds = java.time.Duration.between(
                        trip.getWaitingStartedAt(), trip.getWaitingEndedAt()).getSeconds();
                long billableSeconds = Math.max(0, waitingSeconds - waitingFreeSeconds);
                long waitingCost = Math.round(billableSeconds * waitingPricePerMinute / 60.0);
                trip.setWaitingPrice(waitingCost);
                trip.setTotalPrice(trip.getTotalPrice() + waitingCost);
            }
        }
        if (newStatus == TripStatus.COMPLETED) {
            trip.setCompletedAt(LocalDateTime.now());
            // Safar davomidagi kutish faol bo'lsa — yakunlash
            if (trip.getTripWaitingStartedAt() != null && trip.getTripWaitingEndedAt() == null) {
                trip.setTripWaitingEndedAt(LocalDateTime.now());
                long twSeconds = java.time.Duration.between(
                        trip.getTripWaitingStartedAt(), trip.getTripWaitingEndedAt()).getSeconds();
                long twCost = Math.round(twSeconds * tripWaitingPricePerMinute / 60.0);
                long prevTw = trip.getTripWaitingPrice() != null ? trip.getTripWaitingPrice() : 0;
                trip.setTripWaitingPrice(prevTw + twCost);
                trip.setTotalPrice(trip.getTotalPrice() + twCost);
            }
            driver.setTotalTrips(driver.getTotalTrips() + 1);
            // Aktivlik balli: safarni yakunlagani uchun +1.0
            driver.setActivityScore(driver.getActivityScore() + 1.0);
            // Haydovchi yana bo'sh — navbat (free_since) yangilanadi
            driver.setFreeSince(LocalDateTime.now());
            // Haydovchi balansi yangilash (komissiyasiz hozir)
            creditDriverBalance(driver, trip);
            // Referal bonusi (do'st kodini kiritgan yo'lovchining birinchi yakunlangan safari).
            // Xatolik safar yakunlanishini buzmasligi uchun try/catch.
            try {
                if (trip.getPassenger() != null) {
                    referralService.rewardOnFirstTrip(trip.getPassenger());
                }
            } catch (Exception ex) {
                log.warn("Referral reward failed for trip {}: {}", trip.getId(), ex.getMessage());
            }
        }

        trip.setStatus(newStatus);
        tripRepository.save(trip);
        driverRepository.save(driver);

        // ── DB ishi yuqorida yakunlandi (status, kutish haqi, balans). Quyidagi side-effect'lar
        //    (WS broadcast / push / SMS / Kafka) HAR BIRI alohida try/catch bilan o'ralgan —
        //    birortasining xatosi ham tranzaksiyani ROLLBACK qila olmaydi. Shu sabab COMPLETED
        //    (va DRIVER_ARRIVED/STARTED) holati har doim commit bo'ladi va 200 qaytadi.
        try {
            messagingTemplate.convertAndSend("/topic/trip/" + tripId,
                    Map.of("status", newStatus.name(), "tripId", tripId));
        } catch (Exception e) {
            log.warn("[STATUS] Trip #{} WS broadcast xato (status saqlandi): {}", tripId, e.getMessage());
        }

        // Yo'lovchiga push notification (holat o'zgarganda)
        // NB: COMPLETED branch tripCompletionPushEnabled flag bilan o'chirilgan (reyting yo'q).
        //     DRIVER_ARRIVED branch flag bilan bog'liq emas — har doim ishlaydi.
        Long passengerId = trip.getPassenger() != null ? trip.getPassenger().getId() : null;
        if (passengerId != null) {
            try {
                if (newStatus == TripStatus.DRIVER_ARRIVED) {
                    pushService.notifyPassenger(passengerId, "Haydovchi yetib keldi! 📍",
                            "Haydovchi sizni kutmoqda", Map.of("tripId", tripId, "type", "ARRIVED"));
                } else if (newStatus == TripStatus.COMPLETED && tripCompletionPushEnabled) {
                    boolean appTrip = "APP".equals(trip.getSource());
                    pushService.notifyPassenger(passengerId, "Sayohat yakunlandi! 🏁",
                            appTrip ? "Baholashni unutmang" : "Haydovchingiz siz bilan bo'ldi",
                            Map.of("tripId", tripId, "type", "COMPLETED", "showRating", appTrip));
                }
            } catch (Exception e) {
                log.warn("[STATUS] Trip #{} yo'lovchi push xato (status saqlandi): {}", tripId, e.getMessage());
            }
        }

        // Haydovchiga ham push — safar yakunlanganda (post-trip SMS o'rniga; SMS pul/vaqt sarflaydi).
        // Yo'lovchi push'ini yuqorida oladi; bu yerda haydovchiga ilova ichi bildirishnomasi.
        // tripCompletionPushEnabled flag bilan o'chirilgan — reyting tizimi qaytsa qayta yoqiladi.
        if (newStatus == TripStatus.COMPLETED && driver != null && tripCompletionPushEnabled) {
            try {
                pushService.notifyDriver(driver.getId(), "Sayohat yakunlandi! 🏁",
                        "Yo'lovchini baholashni unutmang",
                        Map.of("tripId", tripId, "type", "COMPLETED", "showRating", true));
            } catch (Exception e) {
                log.warn("[STATUS] Trip #{} haydovchi push xato (status saqlandi): {}", tripId, e.getMessage());
            }
        }

        // Kafka event — passenger null-safe (SMS qatori kabi), xatosi commit'ni buzmaydi
        if (newStatus == TripStatus.COMPLETED) {
            try {
                Long passengerEventId = trip.getPassenger() != null ? trip.getPassenger().getId() : null;
                eventProducer.ifPresent(p -> p.publish(TripEvent.of(
                        "TRIP_COMPLETED", trip.getId(),
                        passengerEventId, driver.getId(),
                        "COMPLETED", trip.getTotalPrice(),
                        trip.getFromLat() != null ? trip.getFromLat() : 0.0,
                        trip.getFromLon() != null ? trip.getFromLon() : 0.0,
                        trip.getFromAddress(), trip.getToAddress() != null ? trip.getToAddress() : "")));
            } catch (Exception e) {
                log.warn("[KAFKA] Trip #{} TRIP_COMPLETED publish xato (trip davom etadi): {}",
                        tripId, e.getMessage());
            }
        }

        return tripToMap(trip);
    }

    /** Haydovchi tarixi */
    @Transactional(readOnly = true)
    public Map<String, Object> driverHistory(User user, Pageable pageable) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));

        Page<Trip> page = tripRepository.findByDriverIdWithRelations(driver.getId(), pageable);
        return buildTripPage(page);
    }

    /** Yo'lovchi tarixi */
    @Transactional(readOnly = true)
    public Map<String, Object> passengerHistory(User user, Pageable pageable) {
        Page<Trip> page = tripRepository.findByPassengerIdWithRelations(user.getId(), pageable);
        return buildTripPage(page);
    }

    /** Faol sayohat (yo'lovchi) */
    @Transactional(readOnly = true)
    public Map<String, Object> activeTrip(User user) {
        List<TripStatus> activeStatuses = List.of(
                TripStatus.SEARCHING, TripStatus.ACCEPTED, TripStatus.DRIVER_ARRIVED, TripStatus.STARTED);
        return tripRepository.findFirstByPassengerIdAndStatusIn(user.getId(), activeStatuses)
                .map(this::tripToMap)
                .orElse(Map.of("active", false));
    }

    /** Haydovchi uchun faol sayohat */
    @Transactional(readOnly = true)
    public Map<String, Object> driverActiveTrip(User user) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        List<TripStatus> activeStatuses = List.of(
                TripStatus.ACCEPTED, TripStatus.DRIVER_ARRIVED, TripStatus.STARTED);
        return tripRepository.findFirstByDriverIdAndStatusIn(driver.getId(), activeStatuses)
                .map(this::tripToMap)
                .orElse(Map.of("active", false));
    }

    /** Haydovchi uchun mavjud buyurtmalar — joylashuviga qarab filtrlangan */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAvailableTrips(User user) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        // Strict: manfiy balansda (>= 0 talab) yangi buyurtma berilmaydi
        if (!driver.isOnline() || (driver.getBalance() != null && driver.getBalance() < 0)) return List.of();
        // Faol tripi bor haydovchiga UMUMAN yangi buyurtma ko'rsatilmaydi (band — bu darvoza istisno qilinmaydi)
        if (tripRepository.existsByDriverIdAndStatusIn(driver.getId(), TripStatus.ACTIVE_DRIVER_STATUSES))
            return List.of();

        // Rad etish cooldown'i: oddiy yo'lovchi (APP) auto-dispatch buyurtmalari KO'RSATILMAYDI. AMMO
        // operator/admin (CALL/CALL_TAXOMETER) buyurtmalari cooldown'ni E'TIBORSIZ qoldiradi — bu
        // MatchingService.findNearbyDrivers(ignoreCooldown) + TripNotificationHelper:107 push yo'lidagi
        // bypass'ning FOREGROUND ro'yxatdagi JUFTI (Deploy 5). Aks holда operator buyurtmasi faqat
        // backgrounded push'da ko'rinib, ilova ochiq turganda ro'yxatga tushmasdi (cooldown tugaguncha).
        boolean inCooldown = driver.isInCooldown();

        List<Trip> searchingTrips = tripRepository.findByStatusWithRelations(TripStatus.SEARCHING);

        // Haydovchi joylashuviga qarab filtrlash — faqat radius ichidagi buyurtmalar
        Double driverLat = driver.getLatitude();
        Double driverLon = driver.getLongitude();
        if (driverLat != null && driverLon != null && driverLat != 0) {
            searchingTrips = searchingTrips.stream()
                    .filter(t -> {
                        if (t.getFromLat() == null || t.getFromLat() == 0) return true;
                        double dist = DriverLocationCache.haversineKm(
                                driverLat, driverLon, t.getFromLat(), t.getFromLon());
                        return dist <= matchingRadiusKm * 2; // 2x radius — kengaytirilgan
                    })
                    .collect(Collectors.toList());
        }

        // Haydovchining yoqilgan xizmatlari — service hard-filter uchun (bir marta o'qiladi)
        java.util.Set<String> enabledServices = enabledServiceCodes(driver.getId());

        return searchingTrips.stream()
                // Cooldown'da FAQAT operator/CALL buyurtmalar o'tadi — matching bypass bilan AYNAN bir xil shart
                // (TripNotificationHelper:60 — source != null && source.startsWith("CALL"), CALL + CALL_TAXOMETER).
                .filter(t -> !inCooldown || (t.getSource() != null && t.getSource().startsWith("CALL")))
                .filter(t -> DriverTariffFilter.accepts(driver, t))
                .filter(t -> DriverServiceFilter.accepts(enabledServices, t.getSelectedServices()))
                .filter(t -> !ExcludedDriverFilter.contains(t.getExcludedDriverIds(), driver.getId()))
                .map(this::tripToMap)
                .collect(Collectors.toList());
    }

    /** Haydovchida yoqilgan xizmat kodlari (ServiceType.name()) — service eligibility filtri uchun. */
    private java.util.Set<String> enabledServiceCodes(Long driverId) {
        return driverRepository.findEnabledServiceTypesByDriverId(driverId).stream()
                .map(Enum::name)
                .collect(Collectors.toSet());
    }

    /** Kutishni boshlash (DRIVER_ARRIVED holatida) */
    @Transactional
    public Map<String, Object> startWaiting(User driverUser, Long tripId) {
        Driver driver = driverRepository.findByUserId(driverUser.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));
        if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId()))
            throw new RuntimeException("Bu siz qabul qilgan buyurtma emas");
        if (trip.getStatus() != TripStatus.DRIVER_ARRIVED)
            throw new RuntimeException("Faqat yetib kelgandan keyin kutish boshlanadi");

        // Idempotent: kutish DRIVER_ARRIVED da avtomatik boshlanadi. Allaqachon boshlangan
        // bo'lsa — joriy holatni qaytaramiz (xato qaytarmaymiz).
        if (trip.getWaitingStartedAt() == null) {
            trip.setWaitingStartedAt(LocalDateTime.now());
            tripRepository.save(trip);

            messagingTemplate.convertAndSend("/topic/trip/" + tripId,
                    Map.of("type", "WAITING_STARTED", "tripId", tripId,
                            "waitingStartedAt", trip.getWaitingStartedAt().toString()));

            Long passengerId = trip.getPassenger() != null ? trip.getPassenger().getId() : null;
            if (passengerId != null) {
                pushService.notifyPassenger(passengerId,
                        "Kutish boshlandi ⏱",
                        "Haydovchi sizni kutmoqda. Har daqiqa " + (waitingPricePerMinute / 100) + " so'm",
                        Map.of("tripId", tripId, "type", "WAITING_STARTED"));
            }
        }

        return tripToMap(trip);
    }

    /** Safar davomida kutishni boshlash (STARTED holatida) — 2,500 so'm/daqiqa */
    @Transactional
    public Map<String, Object> startTripWaiting(User driverUser, Long tripId) {
        Driver driver = driverRepository.findByUserId(driverUser.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));
        if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId()))
            throw new RuntimeException("Bu siz qabul qilgan buyurtma emas");
        if (trip.getStatus() != TripStatus.STARTED)
            throw new RuntimeException("Faqat safar davomida kutish boshlash mumkin");
        if (trip.getTripWaitingStartedAt() != null && trip.getTripWaitingEndedAt() == null)
            throw new RuntimeException("Kutish allaqachon boshlangan");

        trip.setTripWaitingStartedAt(LocalDateTime.now());
        trip.setTripWaitingEndedAt(null);
        tripRepository.save(trip);

        messagingTemplate.convertAndSend("/topic/trip/" + tripId,
                Map.of("type", "TRIP_WAITING_STARTED", "tripId", tripId,
                        "tripWaitingStartedAt", trip.getTripWaitingStartedAt().toString()));

        Long passengerId = trip.getPassenger() != null ? trip.getPassenger().getId() : null;
        if (passengerId != null) {
            pushService.notifyPassenger(passengerId,
                    "Kutish boshlandi ⏱",
                    "Haydovchi kutmoqda. Har daqiqa 2,500 so'm",
                    Map.of("tripId", tripId, "type", "TRIP_WAITING_STARTED"));
        }

        return tripToMap(trip);
    }

    /** Safar davomidagi kutishni to'xtatish — narx hisoblash va safarni davom ettirish */
    @Transactional
    public Map<String, Object> stopTripWaiting(User driverUser, Long tripId) {
        Driver driver = driverRepository.findByUserId(driverUser.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));
        if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId()))
            throw new RuntimeException("Bu siz qabul qilgan buyurtma emas");
        if (trip.getTripWaitingStartedAt() == null || trip.getTripWaitingEndedAt() != null)
            throw new RuntimeException("Kutish boshlangan emas yoki allaqachon to'xtatilgan");

        trip.setTripWaitingEndedAt(LocalDateTime.now());
        long waitingSeconds = java.time.Duration.between(
                trip.getTripWaitingStartedAt(), trip.getTripWaitingEndedAt()).getSeconds();
        long waitingCost = Math.round(waitingSeconds * tripWaitingPricePerMinute / 60.0);

        // Oldingi trip waiting narxiga qo'shish (bir safardan ko'p marta kutish bo'lishi mumkin)
        long prevTripWaitingPrice = trip.getTripWaitingPrice() != null ? trip.getTripWaitingPrice() : 0;
        trip.setTripWaitingPrice(prevTripWaitingPrice + waitingCost);
        trip.setTotalPrice(trip.getTotalPrice() + waitingCost);
        tripRepository.save(trip);

        messagingTemplate.convertAndSend("/topic/trip/" + tripId,
                Map.of("type", "TRIP_WAITING_STOPPED", "tripId", tripId,
                        "tripWaitingCost", waitingCost,
                        "totalTripWaitingPrice", trip.getTripWaitingPrice(),
                        "totalPrice", trip.getTotalPrice()));

        Long passengerId = trip.getPassenger() != null ? trip.getPassenger().getId() : null;
        if (passengerId != null) {
            pushService.notifyPassenger(passengerId,
                    "Kutish tugadi ▶️",
                    "Safar davom etmoqda. Kutish: +" + (waitingCost / 100) + " so'm",
                    Map.of("tripId", tripId, "type", "TRIP_WAITING_STOPPED"));
        }

        return tripToMap(trip);
    }

    /** Yo'lovchining kelgusi rejalashtirilgan buyurtmalari (ro'yxat) */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getScheduledTrips(Long passengerId) {
        return tripRepository.findByPassengerIdAndStatusOrderByScheduledAtAsc(passengerId, TripStatus.SCHEDULED)
                .stream().map(this::tripToMap).collect(Collectors.toList());
    }

    /** Yo'lovchi tomonidan bekor qilish */
    @Transactional
    public Map<String, Object> cancelTripByPassenger(User user, Long tripId, String reason) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));
        if (!trip.getPassenger().getId().equals(user.getId()))
            throw new RuntimeException("Bu sizning buyurtmangiz emas");

        TripStatus status = trip.getStatus();

        // STARTED holatda bekor qilib bo'lmaydi
        if (status == TripStatus.STARTED)
            throw new RuntimeException("Safar boshlangan — bekor qilib bo'lmaydi");
        if (status != TripStatus.SCHEDULED && status != TripStatus.SEARCHING
                && status != TripStatus.ACCEPTED && status != TripStatus.DRIVER_ARRIVED)
            throw new RuntimeException("Bu bosqichda bekor qilib bo'lmaydi");

        // DRIVER_ARRIVED da kutish narxini hisoblash (agar haydovchi kutgan bo'lsa)
        if (status == TripStatus.DRIVER_ARRIVED && trip.getWaitingStartedAt() != null
                && trip.getWaitingEndedAt() == null) {
            trip.setWaitingEndedAt(LocalDateTime.now());
            long waitingSeconds = java.time.Duration.between(
                    trip.getWaitingStartedAt(), trip.getWaitingEndedAt()).getSeconds();
            long billableSeconds = Math.max(0, waitingSeconds - waitingFreeSeconds);
            long waitingCost = Math.round(billableSeconds * waitingPricePerMinute / 60.0);
            trip.setWaitingPrice(waitingCost);
        }

        trip.setStatus(TripStatus.CANCELLED_BY_PASSENGER);
        if (reason != null && !reason.isBlank()) trip.setCancelReason(reason);
        tripRepository.save(trip);

        // Haydovchiga xabar berish
        if (trip.getDriver() != null) {
            messagingTemplate.convertAndSend("/topic/trip/" + tripId,
                    Map.of("status", "CANCELLED_BY_PASSENGER", "tripId", tripId));
            pushService.notifyDriver(trip.getDriver().getId(),
                    "Buyurtma bekor qilindi ❌",
                    "Yo'lovchi buyurtmani bekor qildi",
                    Map.of("tripId", tripId, "type", "CANCELLED_BY_PASSENGER"));
        }

        return Map.of("status", "CANCELLED_BY_PASSENGER", "tripId", tripId);
    }

    /**
     * Haydovchi tomonidan bekor qilish — tripni boshqa haydovchilarga qayta yuborish bilan.
     *
     * Biznes qoidalari:
     * - Bekor qilgan haydovchiga jarima yo'q (hozircha).
     * - Trip qayta SEARCHING holatiga qaytadi va BOSHQA haydovchilarga taklif qilinadi.
     *   Bekor qilgan haydovchi(lar) excluded_driver_ids ga qo'shiladi — shu tripni
     *   qayta ko'rmaydi va qabul qila olmaydi.
     * - Maksimal {@code maxDriverCancellations} (default 3) marta bekor qilinishi mumkin.
     *   Shu songa yetganda trip yakuniy CANCELLED_BY_DRIVER (reason="DRIVER_CANCELLED_LIMIT")
     *   bo'ladi va yo'lovchiga "Haydovchi topilmadi" deb xabar beriladi.
     * - NO_SHOW (yo'lovchi chiqmadi): qayta yuborilmaydi — yakuniy bekor qilinadi.
     * - STARTED holatda bekor qilib bo'lmaydi.
     */
    @Transactional
    public Map<String, Object> cancelTripByDriver(User driverUser, Long tripId, String reason) {
        Driver driver = driverRepository.findByUserId(driverUser.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));
        if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId()))
            throw new RuntimeException("Bu siz qabul qilgan buyurtma emas");

        TripStatus status = trip.getStatus();

        // STARTED holatda bekor qilib bo'lmaydi
        if (status == TripStatus.STARTED)
            throw new RuntimeException("Safar boshlangan — bekor qilib bo'lmaydi");
        if (status != TripStatus.ACCEPTED && status != TripStatus.DRIVER_ARRIVED)
            throw new RuntimeException("Bu bosqichda bekor qilib bo'lmaydi");

        // NO_SHOW — yo'lovchi chiqmadi: faqat DRIVER_ARRIVED + 5 daqiqa kutgandan keyin.
        // Yo'lovchi yo'qligi sababli qayta yuborish foydasiz — yakuniy bekor qilinadi.
        boolean isNoShow = "NO_SHOW".equals(reason);
        if (isNoShow) {
            if (status != TripStatus.DRIVER_ARRIVED)
                throw new RuntimeException("Yo'lovchi chiqmadi — faqat yetib kelgandan keyin");
            long waitedSeconds = 0;
            if (trip.getWaitingStartedAt() != null) {
                waitedSeconds = java.time.Duration.between(
                        trip.getWaitingStartedAt(), LocalDateTime.now()).getSeconds();
            }
            if (waitedSeconds < 300) // 5 daqiqa = 300 soniya
                throw new RuntimeException("Yo'lovchini kamida 5 daqiqa kutish kerak (" +
                        (300 - waitedSeconds) + " soniya qoldi)");
            return finalizeDriverCancellation(trip, "NO_SHOW", "Kutish vaqti tugadi");
        }

        // Aktivlik balli: faqat "BOSHQA" sababli bekor qilishda -0.2.
        // "MASHINA_BUZILDI" / "FAVQULODDA" — uzrli sabab, jarima yo'q. Yulduz reytingiga tegmaymiz.
        if ("BOSHQA".equals(reason)) {
            driver.setActivityScore(driver.getActivityScore() - 0.2);
            driverRepository.save(driver);
        }

        // Bekor qilgan haydovchini qayta yuborishdan chiqarib tashlash + hisoblagich
        int newCancelCount = trip.getCancelCount() + 1;
        trip.setCancelCount(newCancelCount);
        trip.setExcludedDriverIds(
                ExcludedDriverFilter.append(trip.getExcludedDriverIds(), driver.getId()));

        // Haydovchi biriktirilishini va kutish/broadcast holatini tozalash
        clearDriverAssignment(trip);

        // Limitga yetgan bo'lsa — qayta yuborilmaydi, yakuniy bekor qilinadi
        if (newCancelCount >= maxDriverCancellations) {
            Map<String, Object> result = finalizeDriverCancellation(
                    trip, "DRIVER_CANCELLED_LIMIT", "Haydovchi topilmadi");
            result.put("cancelCount", newCancelCount);
            return result;
        }

        // Aks holda — qayta SEARCHING, broadcast scheduler yangi sifatida olishi uchun tozalangan
        trip.setStatus(TripStatus.SEARCHING);
        trip.setCancelReason(null);
        tripRepository.save(trip);

        // Darhol qayta matching — excluded haydovchilarni chiqarib (scheduler ham broadcast qiladi)
        notificationHelper.notifyNearbyDrivers(trip);

        // Yo'lovchiga: hali ham qidirilmoqda
        messagingTemplate.convertAndSend("/topic/trip/" + tripId,
                Map.of("status", "SEARCHING", "tripId", tripId));

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("status", "SEARCHING");
        result.put("tripId", tripId);
        result.put("redispatched", true);
        result.put("cancelCount", newCancelCount);
        return result;
    }

    /** Haydovchi biriktirilishi va kutish/broadcast holatini tozalash (qayta yuborishga tayyorlash).
     *  Umumiy mantiq {@link TripAssignmentUtil} da — operator bekor qilish + scheduler backstop ham shuni ishlatadi. */
    private void clearDriverAssignment(Trip trip) {
        TripAssignmentUtil.clearDriverAssignment(trip);
    }

    /** Tripni yakuniy CANCELLED_BY_DRIVER holatiga o'tkazish va yo'lovchini xabardor qilish. */
    private Map<String, Object> finalizeDriverCancellation(Trip trip, String reason, String passengerBody) {
        trip.setStatus(TripStatus.CANCELLED_BY_DRIVER);
        trip.setCancelReason(reason);
        tripRepository.save(trip);

        Long tripId = trip.getId();
        messagingTemplate.convertAndSend("/topic/trip/" + tripId,
                Map.of("status", "CANCELLED_BY_DRIVER", "tripId", tripId));
        if (trip.getPassenger() != null) {
            pushService.notifyPassenger(trip.getPassenger().getId(),
                    "Haydovchi bekor qildi ❌",
                    passengerBody,
                    Map.of("tripId", tripId, "type", "CANCELLED_BY_DRIVER"));
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("status", "CANCELLED_BY_DRIVER");
        result.put("tripId", tripId);
        result.put("reason", reason);
        return result;
    }

    /** Sayohatni davom ettirish — shu haydovchi bilan yangi manzilga */
    @Transactional
    public Map<String, Object> continueTrip(User passenger, Long completedTripId,
            Double toLat, Double toLon, String toAddress, Double distanceKm) {

        Trip oldTrip = tripRepository.findById(completedTripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));
        if (!oldTrip.getPassenger().getId().equals(passenger.getId()))
            throw new RuntimeException("Bu sizning buyurtmangiz emas");
        if (oldTrip.getStatus() != TripStatus.COMPLETED)
            throw new RuntimeException("Faqat yakunlangan sayohatni davom ettirish mumkin");
        if (oldTrip.getDriver() == null)
            throw new RuntimeException("Haydovchi topilmadi");

        // 5 daqiqa ichida davom etish mumkin
        if (oldTrip.getCompletedAt() != null &&
                java.time.Duration.between(oldTrip.getCompletedAt(), LocalDateTime.now()).toMinutes() > 5)
            throw new RuntimeException("Davom etish vaqti tugadi (5 daqiqa)");

        Driver driver = oldTrip.getDriver();
        Tariff tariff = oldTrip.getTariff();

        // Narx hisoblash — 10 km dan ortiq bo'lsa har km +1000 so'm
        long effectivePricePerKm = tariff.getPricePerKm();
        if (distanceKm > 10) {
            effectivePricePerKm += 100000;
        }
        long basePrice = tariff.getBasePrice() + (long) (distanceKm * effectivePricePerKm);

        // from = eski trip ning destination (hozirgi joylashuv)
        Double fromLat = oldTrip.getToLat() != null && oldTrip.getToLat() != 0 ?
                oldTrip.getToLat() : (driver.getLatitude() != null ? driver.getLatitude() : 0.0);
        Double fromLon = oldTrip.getToLon() != null && oldTrip.getToLon() != 0 ?
                oldTrip.getToLon() : (driver.getLongitude() != null ? driver.getLongitude() : 0.0);
        String fromAddress = oldTrip.getToAddress() != null ? oldTrip.getToAddress() : "Joriy joylashuv";

        Trip newTrip = new Trip();
        newTrip.setPassenger(passenger);
        newTrip.setTariff(tariff);
        newTrip.setDriver(driver);
        newTrip.setFromLat(fromLat);
        newTrip.setFromLon(fromLon);
        newTrip.setFromAddress(fromAddress);
        newTrip.setToLat(toLat);
        newTrip.setToLon(toLon);
        newTrip.setToAddress(toAddress);
        newTrip.setDistanceKm(java.math.BigDecimal.valueOf(distanceKm));
        newTrip.setBasePrice(basePrice);
        newTrip.setTotalPrice(basePrice);
        // Darhol STARTED — haydovchi va yo'lovchi birga turibdi
        newTrip.setStatus(TripStatus.STARTED);
        newTrip.setAcceptedAt(LocalDateTime.now());
        newTrip.setStartedAt(LocalDateTime.now());
        Trip saved = tripRepository.save(newTrip);

        // Haydovchiga WebSocket xabari — davom etish
        Map<String, Object> continueMsg = new HashMap<>();
        continueMsg.put("type", "CONTINUE_REQUEST");
        continueMsg.put("tripId", saved.getId());
        continueMsg.put("oldTripId", completedTripId);
        continueMsg.put("toAddress", toAddress);
        continueMsg.put("toLat", toLat);
        continueMsg.put("toLon", toLon);
        continueMsg.put("price", basePrice / 100);
        continueMsg.put("distanceKm", distanceKm);
        continueMsg.put("passengerName", passenger.getName());
        messagingTemplate.convertAndSend("/topic/driver/" + driver.getId(), continueMsg);

        // Yo'lovchiga ham bildirishnoma
        messagingTemplate.convertAndSend("/topic/trip/" + saved.getId(),
                Map.of("status", "STARTED", "tripId", saved.getId()));

        // Push notification
        pushService.notifyDriver(driver.getId(),
                "Mijoz davom etmoqchi! 🔄",
                passenger.getName() + " → " + toAddress + " (" + (basePrice / 100) + " so'm)",
                Map.of("tripId", saved.getId(), "type", "CONTINUE_REQUEST"));

        // Kafka event
        final long fPrice = basePrice;
        eventProducer.ifPresent(p -> p.publish(TripEvent.of(
                "TRIP_CONTINUE", saved.getId(), passenger.getId(), driver.getId(),
                "STARTED", fPrice, fromLat, fromLon, fromAddress,
                toAddress != null ? toAddress : "")));

        Map<String, Object> result = tripToMap(saved);
        result.put("continued", true);
        result.put("oldTripId", completedTripId);
        if (distanceKm > 10) {
            result.put("longDistanceExtra", 1000);
            result.put("effectivePricePerKm", effectivePricePerKm / 100);
        }
        return result;
    }

    /** Trip ma'lumotini yo'lovchi uchun olish (tracking polling) */
    @Transactional(readOnly = true)
    public Map<String, Object> getTripForPassenger(User user, Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));
        if (!trip.getPassenger().getId().equals(user.getId()))
            throw new RuntimeException("Bu sizning buyurtmangiz emas");
        return tripToMap(trip);
    }

    /** Yo'lovchi haydovchiga baho berish */
    @Transactional
    public Map<String, Object> rateTrip(User passenger, Long tripId, int score, String comment) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));
        if (!trip.getPassenger().getId().equals(passenger.getId()))
            throw new RuntimeException("Bu sizning buyurtmangiz emas");
        if (trip.getStatus() != TripStatus.COMPLETED)
            throw new RuntimeException("Faqat yakunlangan sayohatlarni baholash mumkin");
        // source=CALL bo'lgan trip uchun baho berish taqiqlangan
        if ("CALL".equals(trip.getSource()))
            throw new com.taxi.backend.exception.ForbiddenException(
                    "Baho berish uchun ilovani yuklab oling: https://tezyo.uz/app");
        if (ratingRepository.existsByTripIdAndDirection(tripId, "P2D"))
            throw new RuntimeException("Bu sayohat allaqachon baholangan");
        if (score < 1 || score > 5)
            throw new RuntimeException("Baho 1-5 oralig'ida bo'lishi kerak");

        Rating rating = new Rating();
        rating.setTrip(trip);
        rating.setFromUser(passenger);
        rating.setToDriver(trip.getDriver());
        rating.setDirection("P2D");
        rating.setScore(score);
        rating.setComment(comment);
        ratingRepository.save(rating);

        // Haydovchi o'rtacha reytingini yangilash (faqat shu haydovchining baholaridan)
        Driver driver = trip.getDriver();
        if (driver != null) {
            double avg = ratingRepository.findByToDriverId(driver.getId()).stream()
                    .mapToInt(Rating::getScore)
                    .average().orElse(5.0);
            driver.setRating(java.math.BigDecimal.valueOf(Math.round(avg * 100.0) / 100.0));
            // Reyting 3.0 dan past tushsa hisob to'xtatiladi
            if (avg < 3.0) {
                driver.setStatus(com.taxi.backend.enums.DriverStatus.SUSPENDED);
            }
            driverRepository.save(driver);
        }

        return Map.of("message", "Bahoingiz uchun rahmat!", "score", score);
    }

    /** Haydovchi yo'lovchini baholaydi (D2P — driver→passenger). */
    @Transactional
    public Map<String, Object> ratePassenger(User driverUser, Long tripId, int score, String comment) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));
        Driver driver = trip.getDriver();
        if (driver == null || driver.getUser() == null
                || !driver.getUser().getId().equals(driverUser.getId()))
            throw new RuntimeException("Bu sizning safaringiz emas");
        if (trip.getStatus() != TripStatus.COMPLETED)
            throw new RuntimeException("Faqat yakunlangan sayohatlarni baholash mumkin");
        if (ratingRepository.existsByTripIdAndDirection(tripId, "D2P"))
            throw new RuntimeException("Bu sayohat allaqachon baholangan");
        if (score < 1 || score > 5)
            throw new RuntimeException("Baho 1-5 oralig'ida bo'lishi kerak");

        User passenger = trip.getPassenger();
        Rating rating = new Rating();
        rating.setTrip(trip);
        rating.setFromUser(driverUser);
        rating.setToUser(passenger);
        rating.setDirection("D2P");
        rating.setScore(score);
        rating.setComment(comment);
        ratingRepository.save(rating);

        // Yo'lovchi o'rtacha reytingini yangilash (managed entity — dirty-checking bilan flush bo'ladi)
        if (passenger != null) {
            var list = ratingRepository.findByToUserId(passenger.getId());
            double avg = list.stream().mapToInt(Rating::getScore).average().orElse(5.0);
            passenger.setRating(java.math.BigDecimal.valueOf(Math.round(avg * 100.0) / 100.0));
            passenger.setRatingCount(list.size());
        }

        return Map.of("message", "Baho saqlandi", "score", score);
    }

    /** Yo'lovchi statistikasi (ProfileScreen) */
    public Map<String, Object> passengerStats(User user) {
        long totalTrips = tripRepository.countByPassengerId(user.getId());
        Long totalSpent = tripRepository.sumSpentByPassenger(user.getId());
        return Map.of("totalTrips", totalTrips, "totalSpent", totalSpent != null ? totalSpent : 0L);
    }

    /** Atomic komissiya hisoblash — race condition himoyasi */
    // Terminal (yakuniy) holatlar — bu holatdagi tripni updateTripStatus o'zgartira olmaydi
    private static final List<TripStatus> TERMINAL_STATUSES = List.of(
            TripStatus.COMPLETED,
            TripStatus.CANCELLED_BY_PASSENGER,
            TripStatus.CANCELLED_BY_DRIVER,
            TripStatus.CANCELLED_BY_ADMIN);

    /** Trip lifecycle'da oldinga tartib indeksi — faqat indeks oshib boruvchi o'tish qonuniy. */
    private static int forwardRank(TripStatus s) {
        return switch (s) {
            case SEARCHING -> 0;
            case ACCEPTED -> 1;
            case DRIVER_ARRIVED -> 2;
            case STARTED -> 3;
            case COMPLETED -> 4;
            default -> -1; // bekor qilingan holatlar — terminal tekshiruvi oldinda ushlaydi
        };
    }

    private void creditDriverBalance(Driver driver, Trip trip) {
        // Null-guard: totalPrice null bo'lsa ham yakunlash CRASH bo'lmasin — komissiya 0 dan hisoblanadi
        Long totalPriceObj = trip.getTotalPrice();
        if (totalPriceObj == null) {
            log.warn("[COMMISSION] Trip #{} totalPrice=null — komissiya 0 dan hisoblanadi", trip.getId());
        }
        long totalPrice = totalPriceObj != null ? totalPriceObj : 0L;
        long commission = Math.round(totalPrice * commissionPercent / 100.0);

        // Atomic DB update
        driverRepository.addToBalance(driver.getId(), -commission);

        // Atomik update'dan keyin yangilangan balansni o'qish (stale data himoyasi)
        driverRepository.flush();
        Driver updated = driverRepository.findById(driver.getId()).orElse(driver);
        // Null-guard: balans null bo'lsa 0 deb olinadi (unboxing NPE'siz)
        Long updatedBalance = updated.getBalance();
        long balanceAfter = updatedBalance != null ? updatedBalance : 0L;
        long balanceBefore = balanceAfter + commission;

        Transaction tx = new Transaction();
        tx.setDriver(driver);
        tx.setTrip(trip);
        tx.setType(TransactionType.COMMISSION);
        tx.setAmount(commission);
        tx.setBalanceBefore(balanceBefore);
        tx.setBalanceAfter(balanceAfter);
        tx.setDescription("Komissiya: #" + trip.getId() + " sayohat uchun");
        transactionRepository.save(tx);
    }

    private Map<String, Object> buildTripPage(Page<Trip> page) {
        return Map.of(
                "items", page.getContent().stream().map(this::tripToMap).collect(Collectors.toList()),
                "totalPages", page.getTotalPages(),
                "totalElements", page.getTotalElements(),
                "currentPage", page.getNumber());
    }

    private Map<String, Object> tripToMap(Trip t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", t.getId());
        m.put("fromAddress", t.getFromAddress());
        m.put("toAddress", t.getToAddress());
        m.put("fromLat", t.getFromLat() != null ? t.getFromLat() : 0.0);
        m.put("fromLon", t.getFromLon() != null ? t.getFromLon() : 0.0);
        m.put("toLat", t.getToLat() != null ? t.getToLat() : 0.0);
        m.put("toLon", t.getToLon() != null ? t.getToLon() : 0.0);
        m.put("distance", t.getDistanceKm() != null ? t.getDistanceKm().doubleValue() * 1000 : 0);
        m.put("totalPrice", t.getTotalPrice() != null ? t.getTotalPrice() : 0L);
        m.put("basePrice", t.getBasePrice() != null ? t.getBasePrice() : 0L);
        // Qo'shimcha xizmatlar (read-only) — kod/nom/narx (tiyin), umumiy narxi extra_price da
        m.put("services", ServiceCatalog.toDto(ServiceCatalog.parseCsv(t.getSelectedServices())));
        m.put("servicesTotal", t.getExtraPrice() != null ? t.getExtraPrice() : 0L);
        m.put("arrivedAt", t.getArrivedAt() != null ? t.getArrivedAt().toString() : null);
        m.put("waitingStartedAt", t.getWaitingStartedAt() != null ? t.getWaitingStartedAt().toString() : null);
        m.put("waitingEndedAt", t.getWaitingEndedAt() != null ? t.getWaitingEndedAt().toString() : null);
        m.put("waitingPrice", t.getWaitingPrice() != null ? t.getWaitingPrice() : 0L);
        // Pullik kutish konfiguratsiyasi (klient countdown uchun — server narxni o'zi hisoblaydi)
        m.put("waitingFee", t.getWaitingPrice() != null ? t.getWaitingPrice() : 0L);
        m.put("freeSeconds", waitingFreeSeconds);
        m.put("waitingPricePerMinute", waitingPricePerMinute);
        m.put("tripWaitingStartedAt", t.getTripWaitingStartedAt() != null ? t.getTripWaitingStartedAt().toString() : null);
        m.put("tripWaitingEndedAt", t.getTripWaitingEndedAt() != null ? t.getTripWaitingEndedAt().toString() : null);
        m.put("tripWaitingPrice", t.getTripWaitingPrice() != null ? t.getTripWaitingPrice() : 0L);
        m.put("status", t.getStatus().name());
        m.put("source", t.getSource());
        m.put("createdAt", t.getCreatedAt().toString());
        m.put("scheduledAt", t.getScheduledAt() != null ? t.getScheduledAt().toString() : null);
        if (t.getTariff() != null) {
            try {
                m.put("tariffName", t.getTariff().getName());
                // calloutFee = tarif baza (boshlang'ich) summasi — haydovchiga "chaqiruv/yetib borish
                // summasi" sifatida ko'rsatiladi (tiyinda). Modelda alohida call-out maydoni yo'q,
                // shuning uchun tarif basePrice ishlatiladi (eng yaqin mavjud qiymat).
                m.put("calloutFee", t.getTariff().getBasePrice() != null ? t.getTariff().getBasePrice() : 0L);
            } catch (Exception e) {
                m.put("tariffName", "Noma'lum");
                m.put("calloutFee", 0L);
            }
        }
        if (t.getDriver() != null) {
            Driver d = t.getDriver();
            m.put("driverId", d.getId());
            m.put("carModel", d.getCarModel());
            m.put("carNumber", d.getCarNumber());
            m.put("carColor", d.getCarColor());
            m.put("driverRating", d.getRating());
            m.put("driverLat", d.getLatitude() != null ? d.getLatitude() : 0.0);
            m.put("driverLon", d.getLongitude() != null ? d.getLongitude() : 0.0);
            try {
                m.put("driverName", d.getUser() != null ? d.getUser().getName() : "");
                m.put("driverPhone", d.getUser() != null ? d.getUser().getPhone() : "");
            } catch (Exception e) {
                // LazyInitializationException — transaction yopilgan, driver.user yuklanmagan
                log.debug("tripToMap: driver.user lazy load xato (tripId={}): {}", t.getId(), e.getMessage());
                m.put("driverName", "");
                m.put("driverPhone", "");
            }
        }
        if (t.getPassenger() != null) {
            try {
                m.put("passengerName", t.getPassenger().getName());
                m.put("passengerPhone", t.getPassenger().getPhone());
                m.put("passengerId", t.getPassenger().getId());
            } catch (Exception e) {
                log.debug("tripToMap: passenger lazy load xato (tripId={}): {}", t.getId(), e.getMessage());
                m.put("passengerName", "Noma'lum");
                m.put("passengerPhone", "—");
            }
        }
        // Komissiya hisoblash (tarix uchun)
        if (t.getTotalPrice() != null && t.getTotalPrice() > 0) {
            long commission = Math.round(t.getTotalPrice() * commissionPercent / 100.0);
            m.put("commission", commission);
            m.put("commissionPercent", commissionPercent);
            m.put("driverIncome", t.getTotalPrice() - commission);
        }
        // Yo'lovchi bahosi va izohi
        try {
            ratingRepository.findByTripId(t.getId()).ifPresent(r -> {
                m.put("ratingScore", r.getScore());
                m.put("ratingComment", r.getComment() != null ? r.getComment() : "");
                m.put("ratingDate", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
            });
        } catch (Exception e) { /* rating yo'q bo'lsa o'tkazib yuborish */ }
        return m;
    }
}
