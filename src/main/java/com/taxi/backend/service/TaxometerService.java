package com.taxi.backend.service;

import com.taxi.backend.enums.TransactionType;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Tariff;
import com.taxi.backend.model.Transaction;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TariffRepository;
import com.taxi.backend.repository.TransactionRepository;
import com.taxi.backend.repository.TripRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TaxometerService {

    private final TripRepository tripRepository;
    private final DriverRepository driverRepository;
    private final TransactionRepository transactionRepository;
    private final TariffRepository tariffRepository;
    private final com.taxi.backend.pricing.NightFareService nightFareService;

    @Value("${app.commission.percent:10}") // TezYol commission rate — change here only
    private double commissionPercent;

    @Value("${app.waiting.price-per-minute:60000}")
    private long waitingPricePerMinute;

    @Value("${app.waiting.free-seconds:60}")
    private long waitingFreeSeconds;

    public TaxometerService(TripRepository tripRepository,
                            DriverRepository driverRepository,
                            TransactionRepository transactionRepository,
                            TariffRepository tariffRepository,
                            com.taxi.backend.pricing.NightFareService nightFareService) {
        this.tripRepository = tripRepository;
        this.driverRepository = driverRepository;
        this.transactionRepository = transactionRepository;
        this.tariffRepository = tariffRepository;
        this.nightFareService = nightFareService;
    }

    @Transactional
    public Map<String, Object> start(User user, double lat, double lon, Long existingTripId) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));

        if (!driver.isOnline()) {
            throw new RuntimeException("Taxometrni boshlash uchun liniyada bo'lishingiz kerak");
        }

        // Operator CALL_TAXOMETER buyurtmasini taxometr bilan boshlash
        if (existingTripId != null) {
            Trip existing = tripRepository.findById(existingTripId)
                    .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));
            if (existing.getDriver() == null || !existing.getDriver().getId().equals(driver.getId())) {
                throw new RuntimeException("Bu sizning buyurtmangiz emas");
            }
            if (!"CALL_TAXOMETER".equals(existing.getSource())) {
                throw new RuntimeException("Bu buyurtma taxometr rejimida emas");
            }
            if (existing.getStatus() != TripStatus.DRIVER_ARRIVED) {
                throw new RuntimeException("Taxometrni boshlash uchun yo'lovchi oldida bo'lishingiz kerak");
            }
            LocalDateTime now = LocalDateTime.now();
            // Pullik kutishni yakunlash (DRIVER_ARRIVED dan beri) — yakuniy narxga qo'shiladi (finish da)
            if (existing.getWaitingStartedAt() != null && existing.getWaitingEndedAt() == null) {
                existing.setWaitingEndedAt(now);
                long waitingSeconds = java.time.Duration.between(
                        existing.getWaitingStartedAt(), now).getSeconds();
                long billableSeconds = Math.max(0, waitingSeconds - waitingFreeSeconds);
                long waitingCost = Math.round(billableSeconds * waitingPricePerMinute / 60.0);
                existing.setWaitingPrice(waitingCost);
            }
            existing.setStatus(TripStatus.STARTED);
            existing.setFromLat(lat);
            existing.setFromLon(lon);
            existing.setStartedAt(now);
            tripRepository.save(existing);
            return Map.of(
                    "tripId", existing.getId(),
                    "status", "STARTED",
                    "source", "CALL_TAXOMETER",
                    "startedAt", existing.getStartedAt().toString()
            );
        }

        List<TripStatus> activeStatuses = List.of(
                TripStatus.ACCEPTED, TripStatus.DRIVER_ARRIVED, TripStatus.STARTED);
        if (tripRepository.findFirstByDriverIdAndStatusIn(driver.getId(), activeStatuses).isPresent()) {
            throw new RuntimeException("Allaqachon faol buyurtma mavjud");
        }

        Trip trip = new Trip();
        trip.setDriver(driver);
        trip.setSource("TAXOMETER");
        trip.setStatus(TripStatus.STARTED);
        trip.setFromLat(lat);
        trip.setFromLon(lon);
        trip.setFromAddress("Taxometr sayohat");
        trip.setBasePrice(0L);
        trip.setExtraPrice(0L);
        trip.setTotalPrice(0L);
        trip.setStartedAt(LocalDateTime.now());
        tripRepository.save(trip);

        return Map.of(
                "tripId", trip.getId(),
                "status", "STARTED",
                "source", "TAXOMETER",
                "startedAt", trip.getStartedAt().toString()
        );
    }

    @Transactional
    public Map<String, Object> finish(User user, Long tripId, double endLat, double endLon, double distanceKm) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));

        if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId())) {
            throw new RuntimeException("Bu sizning taxometringiz emas");
        }
        if (!"TAXOMETER".equals(trip.getSource()) && !"CALL_TAXOMETER".equals(trip.getSource())) {
            throw new RuntimeException("Bu taxometer sayohati emas");
        }
        if (trip.getStatus() != TripStatus.STARTED) {
            throw new RuntimeException("Taxometr allaqachon yakunlangan");
        }
        if (distanceKm <= 0) {
            throw new RuntimeException("Masofa 0 dan katta bo'lishi kerak");
        }

        // Taxometr bazaviy tarifi — mijoz ilovasi taxometr uchun ko'rsatadigan "Start" tarif
        // (DB nomi STANDART: base 7500 + 2500/km). Tariflar qayta nomlangan — "EKONOM" qatori YO'Q,
        // shuning uchun hardcode "EKONOM" 400 ("EKONOM tarifi topilmadi") berardi. Nom o'zgarishiga
        // chidamli zanjir bilan asosiy tarifni topamiz: STANDART → STANDARD → START → EKONOM (eski).
        // Bu mijozdagi 2500/km bilan to'liq mos (STANDART.price_per_km = 250000 tiyin = 2500 so'm/km).
        Tariff base = tariffRepository.findByName("STANDART")
                .or(() -> tariffRepository.findByName("STANDARD"))
                .or(() -> tariffRepository.findByName("START"))
                .or(() -> tariffRepository.findByName("EKONOM"))
                .orElseThrow(() -> new RuntimeException("Asosiy (STANDART) tarif topilmadi"));
        // Pullik kutish haqi + qo'shimcha xizmatlar (operator tanlagan) yakuniy narxga qo'shiladi.
        // Komissiya ularni ham o'z ichiga oladi (fareTiyin ga qo'shilgandan keyin hisoblanadi).
        long waitingFee = trip.getWaitingPrice() != null ? trip.getWaitingPrice() : 0L;
        long servicesFee = trip.getExtraPrice() != null ? trip.getExtraPrice() : 0L;
        // Tungi tarif — base ga bir martalik ustama (safar YARATILGAN vaqti bo'yicha,
        // biznes-zonada; 01:00 da boshlangan safar 06:30 da tugasa ham tungi tarifda qoladi).
        // Per-km va kutish/xizmat haqi O'ZGARMAYDI — faqat base oshadi.
        long baseTiyin = nightFareService.applyToBaseAtCreation(base.getBasePrice(), trip.getCreatedAt());
        long fareTiyin = baseTiyin + (long) (distanceKm * base.getPricePerKm()) + waitingFee + servicesFee;
        long commissionTiyin = Math.round(fareTiyin * commissionPercent / 100.0);

        trip.setToLat(endLat);
        trip.setToLon(endLon);
        trip.setToAddress("Taxometr yakunlandi");
        trip.setDistanceKm(BigDecimal.valueOf(distanceKm));
        trip.setTotalPrice(fareTiyin);
        trip.setBasePrice(fareTiyin);
        trip.setStatus(TripStatus.COMPLETED);
        trip.setCompletedAt(LocalDateTime.now());
        tripRepository.save(trip);

        driverRepository.addToBalance(driver.getId(), -commissionTiyin);
        driverRepository.flush();
        Driver updated = driverRepository.findById(driver.getId()).orElse(driver);
        long balanceAfter = updated.getBalance();

        Transaction tx = new Transaction();
        tx.setDriver(driver);
        tx.setTrip(trip);
        tx.setType(TransactionType.TAXOMETER_COMMISSION);
        tx.setAmount(-commissionTiyin);
        tx.setBalanceBefore(balanceAfter + commissionTiyin);
        tx.setBalanceAfter(balanceAfter);
        tx.setDescription("Taxometr komissiya: " + distanceKm + " km");
        transactionRepository.save(tx);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tripId", trip.getId());
        result.put("distanceKm", distanceKm);
        result.put("fareUzs", fareTiyin / 100);
        result.put("fareTiyin", fareTiyin);
        result.put("waitingFeeTiyin", waitingFee);
        result.put("servicesFeeTiyin", servicesFee);
        result.put("commissionTiyin", commissionTiyin);
        result.put("newBalanceUzs", balanceAfter / 100);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getActive(User user) {
        Driver driver = driverRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));

        return tripRepository
                .findFirstByDriverIdAndStatusIn(driver.getId(), List.of(TripStatus.STARTED)).stream()
                .filter(t -> "TAXOMETER".equals(t.getSource()) || "CALL_TAXOMETER".equals(t.getSource()))
                .findFirst()
                .<Map<String, Object>>map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("tripId", t.getId());
                    m.put("status", t.getStatus().name());
                    m.put("source", t.getSource());
                    m.put("startedAt", t.getStartedAt() != null ? t.getStartedAt().toString() : "");
                    m.put("fromLat", t.getFromLat());
                    m.put("fromLon", t.getFromLon());
                    return m;
                })
                .orElseGet(() -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("active", false);
                    return m;
                });
    }
}
