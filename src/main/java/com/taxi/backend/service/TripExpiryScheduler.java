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
import java.util.List;
import java.util.Map;

/** Scheduled lifecycle backstops. It intentionally owns no broadcast or fan-out path. */
@Component
public class TripExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(TripExpiryScheduler.class);

    @Value("${app.taxometer.stuck-trip-max-hours:6}")
    private long taxometerStuckMaxHours;

    @Value("${app.dispatch.stuck-accepted-max-minutes:15}")
    private long acceptedStuckMaxMinutes;

    private final TripRepository tripRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TripNotificationHelper notificationHelper;

    public TripExpiryScheduler(TripRepository tripRepository,
                                SimpMessagingTemplate messagingTemplate,
                                TripNotificationHelper notificationHelper) {
        this.tripRepository = tripRepository;
        this.messagingTemplate = messagingTemplate;
        this.notificationHelper = notificationHelper;
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void dispatchScheduledTrips() {
        List<Trip> due = tripRepository.findByStatusAndScheduledAtLessThanEqual(
                TripStatus.SCHEDULED, LocalDateTime.now().plusMinutes(5));
        for (Trip trip : due) {
            trip.setStatus(TripStatus.SEARCHING);
            Trip saved = tripRepository.save(trip);
            notificationHelper.notifyNearbyDrivers(saved);
            if (saved.getPassenger() != null) {
                messagingTemplate.convertAndSend("/topic/passenger/" + saved.getPassenger().getId(),
                        Map.of("type", "SCHEDULED_DISPATCHED", "tripId", saved.getId(),
                                "message", "Rejalashtirilgan buyurtmangiz uchun haydovchi qidirilmoqda"));
            }
        }
    }

    @Scheduled(fixedDelay = 120_000)
    @Transactional
    public void expireStuckSearchingTrips() {
        List<Trip> stuckTrips = tripRepository.findByStatusAndCreatedAtBefore(
                TripStatus.SEARCHING, LocalDateTime.now().minusMinutes(10));
        for (Trip trip : stuckTrips) {
            trip.setStatus(TripStatus.CANCELLED_BY_ADMIN);
            tripRepository.save(trip);
            notificationHelper.cancelOpenOffer(trip.getId());
            if (trip.getPassenger() != null) {
                messagingTemplate.convertAndSend("/topic/passenger/" + trip.getPassenger().getId(),
                        Map.of("type", "TRIP_EXPIRED", "tripId", trip.getId(),
                                "message", "Haydovchi topilmadi. Buyurtma bekor qilindi."));
            }
        }
    }

    /**
     * The former broadcast scheduler is now a pure sequential advancement
     * worker. A live offer makes dispatchNextOffer a no-op; no other driver is
     * notified while the owner can still act.
     */
    @Scheduled(fixedDelay = 15_000)
    public void advanceSequentialOffers() {
        notificationHelper.recoverPendingOfferDeliveries();
        notificationHelper.expireDueOffers();
        for (Trip trip : tripRepository.findByStatusOrderByCreatedAtAsc(TripStatus.SEARCHING)) {
            notificationHelper.notifyNearbyDrivers(trip);
        }
    }

    @Scheduled(fixedDelay = 600_000)
    @Transactional
    public void cancelStuckTaximeterTrips() {
        LocalDateTime now = LocalDateTime.now();
        for (Trip trip : tripRepository.findStuckStartedTaximeterTrips(now.minusHours(taxometerStuckMaxHours))) {
            Long driverId = trip.getDriver() != null ? trip.getDriver().getId() : null;
            LocalDateTime since = trip.getStartedAt() != null ? trip.getStartedAt() : trip.getCreatedAt();
            long ageHours = since != null ? Duration.between(since, now).toHours() : -1;
            trip.setStatus(TripStatus.CANCELLED_BY_ADMIN);
            trip.setCancelReason("Auto: osilib qolgan STARTED taxometer (" + ageHours + "h)");
            trip.setCompletedAt(now);
            tripRepository.save(trip);
            log.warn("[STUCK-TAXOMETER] tripId={} driverId={} ageHours={}", trip.getId(), driverId, ageHours);
        }
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void cancelStuckAcceptedTrips() {
        LocalDateTime now = LocalDateTime.now();
        for (Trip trip : tripRepository.findStuckAcceptedTrips(now.minusMinutes(acceptedStuckMaxMinutes))) {
            Long driverId = trip.getDriver() != null ? trip.getDriver().getId() : null;
            LocalDateTime since = trip.getAcceptedAt() != null ? trip.getAcceptedAt() : trip.getCreatedAt();
            long ageMinutes = since != null ? Duration.between(since, now).toMinutes() : -1;
            trip.setStatus(TripStatus.CANCELLED_BY_ADMIN);
            trip.setCancelReason("Auto: osilib qolgan ACCEPTED (" + ageMinutes + "min)");
            TripAssignmentUtil.clearDriverAssignment(trip);
            tripRepository.save(trip);
            log.warn("[STUCK-ACCEPTED] tripId={} driverId={} ageMinutes={}", trip.getId(), driverId, ageMinutes);
        }
    }
}
