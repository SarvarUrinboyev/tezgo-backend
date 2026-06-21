package com.taxi.backend.service;

import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BroadcastBoardService {

    private final TripRepository tripRepository;
    private final DriverRepository driverRepository;
    private final PushNotificationService pushService;

    public BroadcastBoardService(TripRepository tripRepository,
                                  DriverRepository driverRepository,
                                  PushNotificationService pushService) {
        this.tripRepository = tripRepository;
        this.driverRepository = driverRepository;
        this.pushService = pushService;
    }

    /** Broadcast taxtadagi mavjud triplar — faqat haydovchi tanlagan tariflarga moslari */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBroadcastBoard(User driverUser) {
        Driver driver = driverRepository.findByUserId(driverUser.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));

        // Manfiy balans (strict >= 0), faol tripi bor yoki rad etish cooldown'idagi haydovchiga taxta ko'rsatilmaydi
        if ((driver.getBalance() != null && driver.getBalance() < 0)
                || driver.isInCooldown()
                || tripRepository.existsByDriverIdAndStatusIn(driver.getId(), TripStatus.ACTIVE_DRIVER_STATUSES)) {
            return List.of();
        }

        // Haydovchining yoqilgan xizmatlari — service hard-filter uchun (bir marta o'qiladi)
        java.util.Set<String> enabledServices = enabledServiceCodes(driver.getId());

        return tripRepository.findBroadcastTripBoard()
                .stream()
                .filter(t -> DriverTariffFilter.accepts(driver, t))
                .filter(t -> DriverServiceFilter.accepts(enabledServices, t.getSelectedServices()))
                .filter(t -> !ExcludedDriverFilter.contains(t.getExcludedDriverIds(), driver.getId()))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** Haydovchida yoqilgan xizmat kodlari (ServiceType.name()) — service eligibility filtri uchun. */
    private java.util.Set<String> enabledServiceCodes(Long driverId) {
        return driverRepository.findEnabledServiceTypesByDriverId(driverId).stream()
                .map(Enum::name)
                .collect(Collectors.toSet());
    }

    /**
     * Haydovchi broadcast tripni da'vo qiladi.
     * SELECT FOR UPDATE (PESSIMISTIC_WRITE) — race condition himoyasi.
     * Birinchi haydovchi qulflaydi, ikkinchisi 409 oladi.
     */
    @Transactional
    public Map<String, Object> claimTrip(User driverUser, Long tripId) {
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));

        if (trip.getStatus() != TripStatus.SEARCHING || trip.getDriver() != null) {
            throw new IllegalStateException("Buyurtma allaqachon olindi");
        }

        Driver driver = driverRepository.findByUserId(driverUser.getId())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));

        if (tripRepository.existsByDriverIdAndStatusIn(driver.getId(), TripStatus.ACTIVE_DRIVER_STATUSES)) {
            throw new IllegalArgumentException("Sizda faol buyurtma bor");
        }

        if (driver.isInCooldown()) {
            throw new IllegalArgumentException("Iltimos biroz kuting — yangi buyurtma tez orada");
        }

        if (ExcludedDriverFilter.contains(trip.getExcludedDriverIds(), driver.getId())) {
            throw new IllegalArgumentException("Siz bu buyurtmani bekor qildingiz — qayta qabul qila olmaysiz");
        }

        if (driver.getBalance() != null && driver.getBalance() < 0) {
            throw new IllegalArgumentException("Manfiy balans: buyurtma qabul qilib bo'lmaydi");
        }

        if (!DriverTariffFilter.accepts(driver, trip)) {
            throw new IllegalArgumentException("Bu buyurtma siz tanlagan tariflarga mos emas");
        }

        // Service hard-filter: buyurtma talab qilgan barcha xizmatlar haydovchida yoqilgan bo'lishi shart
        if (!DriverServiceFilter.accepts(enabledServiceCodes(driver.getId()), trip.getSelectedServices())) {
            throw new IllegalArgumentException("Bu buyurtma uchun kerakli xizmatlar sizda yoqilmagan");
        }

        trip.setDriver(driver);
        trip.setStatus(TripStatus.ACCEPTED);
        trip.setAcceptedAt(LocalDateTime.now());
        tripRepository.save(trip);

        // Boshqa online haydovchilarga past-prioritet xabar
        pushService.notifyAllOnlineDriversExcept(
                driver.getId(),
                "Buyurtma allaqachon olindi",
                "Buyurtma #" + tripId + " boshqa haydovchiga biriktirildi",
                Map.of("type", "BROADCAST_TAKEN", "tripId", tripId)
        );

        return Map.of(
                "tripId", tripId,
                "status", "ACCEPTED",
                "message", "Buyurtma sizga biriktirildi!"
        );
    }

    private Map<String, Object> toDto(Trip trip) {
        String passengerPhone = trip.getPassenger() != null
                ? maskPhone(trip.getPassenger().getPhone())
                : "+998 ** *** ** **";
        String tariffName = trip.getTariff() != null ? trip.getTariff().getName() : "STANDARD";
        return Map.of(
                "id", trip.getId(),
                "pickupAddress", trip.getFromAddress() != null ? trip.getFromAddress() : "",
                "dropoffAddress", trip.getToAddress() != null ? trip.getToAddress() : "",
                "fare", trip.getTotalPrice() != null ? trip.getTotalPrice() : 0L,
                "tariffName", tariffName,
                "broadcastAt", trip.getBroadcastAt() != null ? trip.getBroadcastAt().toString() : "",
                "passengerPhone", passengerPhone
        );
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 13) return "+998 ** *** ** **";
        return "+998 " + phone.charAt(4) + phone.charAt(5) + " *** ** "
                + phone.charAt(11) + phone.charAt(12);
    }
}
