package com.taxi.backend.service;

import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Trip;
import com.taxi.backend.repository.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TripExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(TripExpiryScheduler.class);

    /** Phase 1 backstop: STARTED taxometer trip shu soatdan oshsa, osilib qolgan deb yopiladi (konservativ default 6h). */
    @Value("${app.taxometer.stuck-trip-max-hours:6}")
    private long taxometerStuckMaxHours;

    private final TripRepository tripRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PushNotificationService pushNotificationService;
    private final TripNotificationHelper notificationHelper;

    public TripExpiryScheduler(TripRepository tripRepository,
                                SimpMessagingTemplate messagingTemplate,
                                PushNotificationService pushNotificationService,
                                TripNotificationHelper notificationHelper) {
        this.tripRepository = tripRepository;
        this.messagingTemplate = messagingTemplate;
        this.pushNotificationService = pushNotificationService;
        this.notificationHelper = notificationHelper;
    }

    /**
     * Har 30 soniyada: rejalashtirilgan (SCHEDULED) buyurtmalarni vaqti kelganda
     * (scheduledAt — 5 daqiqa oldin) SEARCHING ga o'tkazib, haydovchilarga yuboradi.
     */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void dispatchScheduledTrips() {
        LocalDateTime dispatchBefore = LocalDateTime.now().plusMinutes(5);
        List<Trip> due = tripRepository.findByStatusAndScheduledAtLessThanEqual(
                TripStatus.SCHEDULED, dispatchBefore);
        if (due.isEmpty()) return;
        log.info("Rejalashtirilgan {} ta buyurtma dispatch qilinmoqda", due.size());
        for (Trip trip : due) {
            trip.setStatus(TripStatus.SEARCHING);
            Trip saved = tripRepository.save(trip);
            try {
                notificationHelper.notifyNearbyDrivers(saved);
                if (saved.getPassenger() != null) {
                    messagingTemplate.convertAndSend(
                            "/topic/passenger/" + saved.getPassenger().getId(),
                            Map.of("type", "SCHEDULED_DISPATCHED", "tripId", saved.getId(),
                                    "message", "Rejalashtirilgan buyurtmangiz uchun haydovchi qidirilmoqda"));
                }
            } catch (Exception e) {
                log.error("Rejalashtirilgan trip #{} dispatch xatosi: {}", saved.getId(), e.getMessage());
            }
        }
    }

    /**
     * Har 2 daqiqada bir marta ishlaydi.
     * 10 daqiqadan oshiq vaqt SEARCHING holatida turgan triplarni avtomatik bekor qiladi.
     */
    @Scheduled(fixedDelay = 120_000) // 2 daqiqa
    @Transactional
    public void expireStuckSearchingTrips() {
        LocalDateTime expiryCutoff = LocalDateTime.now().minusMinutes(10);
        List<Trip> stuckTrips = tripRepository.findByStatusAndCreatedAtBefore(TripStatus.SEARCHING, expiryCutoff);

        if (stuckTrips.isEmpty()) return;

        log.info("Eskirgan {} ta SEARCHING trip avtomatik bekor qilinmoqda", stuckTrips.size());

        for (Trip trip : stuckTrips) {
            trip.setStatus(TripStatus.CANCELLED_BY_ADMIN);
            tripRepository.save(trip);

            // Yo'lovchiga xabar
            try {
                if (trip.getPassenger() != null) {
                    messagingTemplate.convertAndSend(
                            "/topic/passenger/" + trip.getPassenger().getId(),
                            Map.of("type", "TRIP_EXPIRED", "tripId", trip.getId(),
                                    "message", "Haydovchi topilmadi. Buyurtma bekor qilindi."));
                }
            } catch (Exception ignored) { }
        }
    }

    /**
     * Har 15 soniyada bir marta ishlaydi.
     * 1 daqiqadan oshiq SEARCHING bo'lgan va hali broadcast qilinmagan triplarni
     * umumiy taxtaga chiqaradi va barcha online haydovchilarga push yuboradi.
     */
    @Scheduled(fixedDelay = 15_000)
    @Transactional
    public void broadcastSearchingTrips() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(15);
        List<Trip> trips = tripRepository.findSearchingTripsToBroadcast(cutoff);
        if (trips.isEmpty()) return;

        // Band haydovchilar (faol tripi bor) — barcha broadcast'larda skip qilinadi (bir marta hisoblanadi)
        Set<Long> busyIds = new HashSet<>(
                tripRepository.findBusyDriverIds(TripStatus.ACTIVE_DRIVER_STATUSES));

        for (Trip trip : trips) {
            trip.setBroadcastAt(LocalDateTime.now());
            tripRepository.save(trip);

            String pickup = trip.getFromAddress() != null ? trip.getFromAddress() : "—";
            String dropoff = trip.getToAddress() != null ? trip.getToAddress() : "—";
            String fare = trip.getTotalPrice() != null ? trip.getTotalPrice().toString() : "?";

            Map<String, Object> data = new HashMap<>();
            data.put("type", "BROADCAST");
            data.put("tripId", trip.getId());
            String tariffName = trip.getTariff() != null ? trip.getTariff().getName() : null;
            if (tariffName != null) data.put("tariffName", tariffName);

            // Matching fazasida xabardor qilinganlar + bekor qilgan (chiqarilgan) + band haydovchilar — skip
            Set<Long> skipIds = new HashSet<>(parseNotifiedIds(trip.getNotifiedDriverIds()));
            skipIds.addAll(ExcludedDriverFilter.parse(trip.getExcludedDriverIds()));
            skipIds.addAll(busyIds);

            try {
                pushNotificationService.notifyAllOnlineDriversExcludingByTariff(
                        skipIds,
                        tariffName,
                        trip.getSelectedServices(),
                        "🚕 Umumiy buyurtma!",
                        "A: " + pickup + " → B: " + dropoff + " | " + fare + " so'm",
                        data
                );
            } catch (Exception e) {
                log.warn("[BROADCAST] Push yuborishda xato trip #{}: {}", trip.getId(), e.getMessage());
            }

            log.info("[BROADCAST] Trip #{} umumiy taxtaga tashlandi (skip: {} ta haydovchi)",
                    trip.getId(), skipIds.size());
        }
    }

    /**
     * Phase 1 backstop — osilib qolgan (STARTED) taxometer triplarni avtomatik yopadi.
     *
     * Agar taxometer "Yakunlash" biror sabab bilan muvaffaqiyatsiz bo'lsa (masalan, eski
     * "EKONOM tarifi topilmadi" xatosi), trip STARTED holatda qolib, haydovchini doimiy
     * "band" qiladi (ACTIVE_DRIVER_STATUSES ⊇ {STARTED}) → unga yangi buyurtma kelmaydi.
     * Bu konservativ chegara (default 6 soat — normal taxometer safari bunchalik uzoq emas)
     * bilan ularni tozalaydi va har birini log qiladi (id, driver, yosh).
     */
    @Scheduled(fixedDelay = 600_000) // har 10 daqiqada
    @Transactional
    public void cancelStuckTaximeterTrips() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusHours(taxometerStuckMaxHours);
        List<Trip> stuck = tripRepository.findStuckStartedTaximeterTrips(cutoff);
        if (stuck.isEmpty()) return;

        for (Trip trip : stuck) {
            Long driverId = trip.getDriver() != null ? trip.getDriver().getId() : null;
            LocalDateTime since = trip.getStartedAt() != null ? trip.getStartedAt() : trip.getCreatedAt();
            long ageHours = since != null ? Duration.between(since, now).toHours() : -1;
            trip.setStatus(TripStatus.CANCELLED_BY_ADMIN);
            trip.setCancelReason("Auto: osilib qolgan STARTED taxometer (Phase 1 backstop, " + ageHours + "h)");
            trip.setCompletedAt(now);
            tripRepository.save(trip);
            log.warn("[STUCK-TAXOMETER] Trip #{} avtomatik yopildi → CANCELLED_BY_ADMIN (driver={}, source={}, age={}h)",
                    trip.getId(), driverId, trip.getSource(), ageHours);
        }
        log.info("[STUCK-TAXOMETER] {} ta osilib qolgan taxometer trip yopildi (chegara={}h)",
                stuck.size(), taxometerStuckMaxHours);
    }

    private Set<Long> parseNotifiedIds(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        Set<Long> ids = new HashSet<>();
        for (String s : csv.split(",")) {
            try { ids.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) {}
        }
        return ids;
    }
}
