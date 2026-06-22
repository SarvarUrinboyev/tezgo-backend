package com.taxi.backend.service;

import com.taxi.backend.dto.OperatorTripRequest;
import com.taxi.backend.enums.Role;
import com.taxi.backend.enums.ServiceType;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Tariff;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.pricing.SurgeResult;
import com.taxi.backend.repository.TariffRepository;
import com.taxi.backend.repository.TripRepository;
import com.taxi.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Operator Service — telefon orqali buyurtma yaratish.
 *
 * Oqim:
 *   1. Mijoz qo'ng'iroq qiladi
 *   2. Operator manzillarni kiritadi
 *   3. Manzillar places.json dan koordinataga aylantiriladi
 *   4. Masofa Haversine bilan hisoblanadi
 *   5. Narx SurgePricingEngine orqali hisoblanadi
 *   6. Trip yaratiladi (source=CALL)
 *   7. Haydovchilarga notification yuboriladi
 */
@Service
public class OperatorService {

    private static final Logger log = LoggerFactory.getLogger(OperatorService.class);

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final TariffRepository tariffRepository;
    private final SurgePricingService surgePricingService;
    private final TripNotificationHelper notificationHelper;
    private final SecurityMonitorService securityMonitor;
    private final com.taxi.backend.pricing.NightFareService nightFareService;

    public OperatorService(TripRepository tripRepository,
                           UserRepository userRepository,
                           TariffRepository tariffRepository,
                           SurgePricingService surgePricingService,
                           TripNotificationHelper notificationHelper,
                           SecurityMonitorService securityMonitor,
                           com.taxi.backend.pricing.NightFareService nightFareService) {
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
        this.tariffRepository = tariffRepository;
        this.surgePricingService = surgePricingService;
        this.notificationHelper = notificationHelper;
        this.securityMonitor = securityMonitor;
        this.nightFareService = nightFareService;
    }

    /**
     * Operator buyurtma yaratadi — telefon qo'ng'iroqdan.
     *
     * @param operator — login qilgan OPERATOR foydalanuvchi
     * @param req — mijoz telefoni, manzillar
     * @return trip ma'lumotlari
     */
    @Transactional
    public Map<String, Object> createTrip(User operator, OperatorTripRequest req) {
        // 1. Mijozni topish yoki yaratish
        User passenger = userRepository.findByPhone(req.getPassengerPhone())
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setPhone(req.getPassengerPhone());
                    newUser.setName("Mijoz " + req.getPassengerPhone().substring(req.getPassengerPhone().length() - 4));
                    newUser.setRole(Role.PASSENGER);
                    return userRepository.save(newUser);
                });

        // 2. Tarif (default: birinchi aktiv tarif = EKONOM)
        Tariff tariff;
        if (req.getTariffId() != null) {
            tariff = tariffRepository.findById(req.getTariffId())
                    .orElseThrow(() -> new RuntimeException("Tarif topilmadi"));
        } else {
            tariff = tariffRepository.findByIsActiveTrue().stream().findFirst()
                    .orElseThrow(() -> new RuntimeException("Faol tarif topilmadi"));
        }

        // 2b. Tanlangan qo'shimcha xizmatlar — kodlari CSV, narxi tiyinda (extra_price)
        List<ServiceType> services = ServiceCatalog.parse(req.getSelectedServices());
        long servicesTotal = ServiceCatalog.totalTiyin(services);
        String servicesCsv = ServiceCatalog.csv(services);
        // Javob (operator paneli) so'm da — estimatedPrice bilan bir xil birlik
        List<Map<String, Object>> servicesDto = services.stream()
                .map(st -> Map.<String, Object>of(
                        "code", st.name(), "name", st.getUzName(), "price", st.getDefaultPriceTiyin() / 100))
                .collect(Collectors.toList());

        // 3. Koordinatalar — operator xaritadan yuborsa ishlatiladi, aks holda default
        double fromLat = (req.getFromLat() != null && req.getFromLon() != null) ? req.getFromLat() : 41.295;
        double fromLon = (req.getFromLat() != null && req.getFromLon() != null) ? req.getFromLon() : 69.677;

        // Taxometr rejimi: aniq manzil shart emas
        if (req.isTaxometerMode()) {
            // Tungi tarif — base (boshlang'ich narx) ga ustama (biznes-zona, hozir).
            // Yakuniy narx taxometr finish da hisoblanadi (u ham yaratish vaqti bo'yicha).
            long taxometerBase = nightFareService.applyToBaseNow(tariff.getBasePrice());

            Trip trip = new Trip();
            trip.setPassenger(passenger);
            trip.setTariff(tariff);
            trip.setFromLat(fromLat);
            trip.setFromLon(fromLon);
            trip.setFromAddress(req.getPickupAddress());
            trip.setToLat(fromLat);
            trip.setToLon(fromLon);
            trip.setToAddress("Taxometr rejimi");
            trip.setDistanceKm(BigDecimal.ZERO);
            trip.setBasePrice(taxometerBase);
            trip.setExtraPrice(servicesTotal);
            trip.setTotalPrice(taxometerBase + servicesTotal);
            trip.setStatus(TripStatus.SEARCHING);
            trip.setSource("CALL_TAXOMETER");
            trip.setSelectedServices(servicesCsv);

            Trip saved = tripRepository.save(trip);

            log.info("[OPERATOR] Taxometr buyurtma #{} yaratildi: {} (operator={}, mijoz={})",
                    saved.getId(), req.getPickupAddress(), operator.getPhone(), req.getPassengerPhone());

            notificationHelper.notifyNearbyDrivers(saved);
            securityMonitor.trackTripCreation(operator.getId());

            Map<String, Object> result = new HashMap<>();
            result.put("tripId", saved.getId());
            result.put("status", "SEARCHING");
            result.put("passengerPhone", req.getPassengerPhone());
            result.put("pickupAddress", req.getPickupAddress());
            result.put("source", "CALL_TAXOMETER");
            result.put("tripMode", "TAXOMETER");
            result.put("tariffName", tariff.getName());
            result.put("services", servicesDto);
            result.put("servicesTotal", servicesTotal / 100);
            result.put("startingPrice", (taxometerBase + servicesTotal) / 100);
            return result;
        }

        double toLat = (req.getToLat() != null && req.getToLon() != null) ? req.getToLat() : 41.300;
        double toLon = (req.getToLat() != null && req.getToLon() != null) ? req.getToLon() : 69.685;

        // 4. Masofa — Haversine
        double distanceKm = DriverLocationCache.haversineKm(fromLat, fromLon, toLat, toLon);
        distanceKm = Math.max(distanceKm, 1.0); // Minimal 1 km

        // 5. Narx — SurgePricingEngine
        long effectivePricePerKm = tariff.getPricePerKm();
        if (distanceKm > 10) effectivePricePerKm += 100000;
        long basePrice = tariff.getBasePrice() + (long) (distanceKm * effectivePricePerKm);
        basePrice = Math.max(basePrice, tariff.getMinPrice());

        // Tungi tarif — base ga bir martalik ustama (biznes-zona, hozir). Per-km tegilmaydi.
        basePrice = nightFareService.applyToBaseNow(basePrice);

        SurgeResult surge = surgePricingService.calculate(basePrice, fromLat, fromLon);
        long finalPrice = surge.finalPriceTiyin();

        // 6. Trip yaratish — source = CALL
        Trip trip = new Trip();
        trip.setPassenger(passenger);
        trip.setTariff(tariff);
        trip.setFromLat(fromLat);
        trip.setFromLon(fromLon);
        trip.setFromAddress(req.getPickupAddress());
        trip.setToLat(toLat);
        trip.setToLon(toLon);
        trip.setToAddress(req.getDestinationAddress());
        trip.setDistanceKm(BigDecimal.valueOf(Math.round(distanceKm * 10.0) / 10.0));
        trip.setBasePrice(finalPrice);
        trip.setExtraPrice(servicesTotal);
        trip.setTotalPrice(finalPrice + servicesTotal);
        trip.setStatus(TripStatus.SEARCHING);
        trip.setSource("CALL"); // Telefon orqali
        trip.setSelectedServices(servicesCsv);

        Trip saved = tripRepository.save(trip);

        log.info("[OPERATOR] Buyurtma #{} yaratildi: {} -> {} (operator={}, mijoz={})",
                saved.getId(), req.getPickupAddress(), req.getDestinationAddress(),
                operator.getPhone(), req.getPassengerPhone());

        // 7. Haydovchilarga notification (async)
        notificationHelper.notifyNearbyDrivers(saved);

        // 8. Anomaliya tracking
        securityMonitor.trackTripCreation(operator.getId());

        // Response
        Map<String, Object> result = new HashMap<>();
        result.put("tripId", saved.getId());
        result.put("status", "SEARCHING");
        result.put("estimatedPrice", (finalPrice + servicesTotal) / 100);
        result.put("estimatedPriceFormatted", String.format("%,d so'm", (finalPrice + servicesTotal) / 100));
        result.put("basePrice", finalPrice / 100);
        result.put("services", servicesDto);
        result.put("servicesTotal", servicesTotal / 100);
        result.put("estimatedDistance", Math.round(distanceKm * 10.0) / 10.0 + " km");
        result.put("estimatedTime", Math.max(2, (int) (distanceKm * 2.8)) + " daqiqa");
        result.put("passengerPhone", req.getPassengerPhone());
        result.put("pickupAddress", req.getPickupAddress());
        result.put("destinationAddress", req.getDestinationAddress());
        result.put("source", "CALL");
        result.put("tripMode", "FIXED");
        result.put("surgeMultiplier", surge.multiplier());
        result.put("tariffName", tariff.getName());
        return result;
    }

    /** Operator buyurtmani bekor qiladi — CALL manbali, terminal BO'LMAGAN har qanday holatda
     *  (SEARCHING/ACCEPTED/DRIVER_ARRIVED/STARTED). Biriktirilgan haydovchi BO'SHATILADI (busy-set'dan chiqadi),
     *  shunda osilib qolgan ACCEPTED trip haydovchini doimiy band qilib qo'ymaydi. */
    @Transactional
    public Map<String, Object> cancelTrip(User operator, Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));

        if (!"CALL".equals(trip.getSource()) && !"CALL_TAXOMETER".equals(trip.getSource())) {
            throw new RuntimeException("Faqat operator yaratgan buyurtmani bekor qilish mumkin");
        }
        if (TripStatus.TERMINAL_STATUSES.contains(trip.getStatus())) {
            throw new RuntimeException("Buyurtma allaqachon yakunlangan yoki bekor qilingan");
        }

        Long freedDriverId = trip.getDriver() != null ? trip.getDriver().getId() : null;
        trip.setStatus(TripStatus.CANCELLED_BY_ADMIN);
        trip.setCancelReason("Operator bekor qildi");
        // Haydovchini bo'shatish — ACCEPTED/DRIVER_ARRIVED bo'lsa busy-set'dan chiqadi (orphan trip leak'ini oldini oladi).
        TripAssignmentUtil.clearDriverAssignment(trip);
        tripRepository.save(trip);

        log.info("[OPERATOR] Buyurtma #{} bekor qilindi (operator={}, freedDriver={})",
                tripId, operator.getPhone(), freedDriverId);

        return Map.of("tripId", tripId, "status", "CANCELLED_BY_ADMIN", "message", "Buyurtma bekor qilindi");
    }

    /** Operator buyurtma manzillarini tahrirlaydi — faqat CALL + SEARCHING */
    @Transactional
    public Map<String, Object> editTrip(User operator, Long tripId, OperatorTripRequest req) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));

        if (!"CALL".equals(trip.getSource())) {
            throw new RuntimeException("Faqat operator yaratgan buyurtmani tahrirlash mumkin");
        }
        if (trip.getStatus() != TripStatus.SEARCHING) {
            throw new RuntimeException("Faqat haydovchi qidirilayotgan buyurtmani tahrirlash mumkin");
        }

        // Manzillarni yangilash
        trip.setFromAddress(req.getPickupAddress());
        trip.setToAddress(req.getDestinationAddress());

        // Koordinatalar yangilash
        double fromLat = (req.getFromLat() != null) ? req.getFromLat() : trip.getFromLat();
        double fromLon = (req.getFromLon() != null) ? req.getFromLon() : trip.getFromLon();
        double toLat = (req.getToLat() != null) ? req.getToLat() : trip.getToLat();
        double toLon = (req.getToLon() != null) ? req.getToLon() : trip.getToLon();

        trip.setFromLat(fromLat);
        trip.setFromLon(fromLon);
        trip.setToLat(toLat);
        trip.setToLon(toLon);

        // Narxni qayta hisoblash
        double distanceKm = DriverLocationCache.haversineKm(fromLat, fromLon, toLat, toLon);
        distanceKm = Math.max(distanceKm, 1.0);

        Tariff tariff = trip.getTariff();
        long effectivePricePerKm = tariff.getPricePerKm();
        if (distanceKm > 10) effectivePricePerKm += 100000;
        long basePrice = tariff.getBasePrice() + (long) (distanceKm * effectivePricePerKm);
        basePrice = Math.max(basePrice, tariff.getMinPrice());

        // Tungi tarif — YARATISH vaqti bo'yicha (tahrirlash vaqti emas), biznes-zonada.
        // 01:00 da yaratilgan safar 06:30 da tahrirlansa ham tungi tarifni saqlaydi.
        basePrice = nightFareService.applyToBaseAtCreation(basePrice, trip.getCreatedAt());

        SurgeResult surge = surgePricingService.calculate(basePrice, fromLat, fromLon);
        long finalPrice = surge.finalPriceTiyin();

        // Mavjud qo'shimcha xizmatlar narxini saqlab qolish (manzil o'zgarsa ham)
        long existingServices = trip.getExtraPrice() != null ? trip.getExtraPrice() : 0L;
        trip.setDistanceKm(java.math.BigDecimal.valueOf(Math.round(distanceKm * 10.0) / 10.0));
        trip.setBasePrice(finalPrice);
        trip.setTotalPrice(finalPrice + existingServices);

        tripRepository.save(trip);

        log.info("[OPERATOR] Buyurtma #{} tahrirlandi (operator={})", tripId, operator.getPhone());

        Map<String, Object> result = new HashMap<>();
        result.put("tripId", tripId);
        result.put("status", trip.getStatus().name());
        result.put("pickupAddress", req.getPickupAddress());
        result.put("destinationAddress", req.getDestinationAddress());
        result.put("estimatedPrice", finalPrice / 100);
        result.put("estimatedDistance", Math.round(distanceKm * 10.0) / 10.0 + " km");
        result.put("message", "Buyurtma tahrirlandi");
        return result;
    }

    /** Operator uchun aktiv buyurtmalar — faqat source=CALL */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getActiveTrips(User operator) {
        List<TripStatus> activeStatuses = List.of(
                TripStatus.SEARCHING, TripStatus.ACCEPTED,
                TripStatus.DRIVER_ARRIVED, TripStatus.STARTED);

        return tripRepository.findByStatusWithRelations(TripStatus.SEARCHING).stream()
                .filter(t -> "CALL".equals(t.getSource()) || "CALL_TAXOMETER".equals(t.getSource()))
                .map(this::tripToSimpleMap)
                .collect(Collectors.toList());
    }

    /** Operator uchun barcha CALL buyurtmalar (aktiv holatdagilar) */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllCallTrips() {
        List<TripStatus> statuses = List.of(
                TripStatus.SEARCHING, TripStatus.ACCEPTED,
                TripStatus.DRIVER_ARRIVED, TripStatus.STARTED);

        List<Map<String, Object>> result = new ArrayList<>();
        for (TripStatus status : statuses) {
            tripRepository.findByStatusWithRelations(status).stream()
                    .filter(t -> "CALL".equals(t.getSource()) || "CALL_TAXOMETER".equals(t.getSource()))
                    .map(this::tripToSimpleMap)
                    .forEach(result::add);
        }
        return result;
    }

    private Map<String, Object> tripToSimpleMap(Trip t) {
        Map<String, Object> m = new HashMap<>();
        m.put("tripId", t.getId());
        m.put("status", t.getStatus().name());
        m.put("pickupAddress", t.getFromAddress());
        m.put("destinationAddress", t.getToAddress());
        m.put("price", t.getTotalPrice() != null ? t.getTotalPrice() / 100 : 0);
        m.put("createdAt", t.getCreatedAt().toString());
        m.put("source", t.getSource());
        m.put("tripMode", "CALL_TAXOMETER".equals(t.getSource()) ? "TAXOMETER" : "FIXED");
        if (t.getPassenger() != null) {
            m.put("passengerPhone", t.getPassenger().getPhone());
        }
        if (t.getDriver() != null) {
            try {
                m.put("driverName", t.getDriver().getUser() != null ? t.getDriver().getUser().getName() : "");
                m.put("driverPhone", t.getDriver().getUser() != null ? t.getDriver().getUser().getPhone() : "");
                m.put("carNumber", t.getDriver().getCarNumber());
            } catch (Exception e) { /* lazy load */ }
        }
        m.put("services", ServiceCatalog.toDto(ServiceCatalog.parseCsv(t.getSelectedServices())));
        m.put("servicesTotal", t.getExtraPrice() != null ? t.getExtraPrice() / 100 : 0);
        return m;
    }

    /** Qo'shimcha xizmatlar katalogi — operator paneli uchun (narx tiyinda). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getServiceCatalog() {
        return ServiceCatalog.catalog();
    }
}
