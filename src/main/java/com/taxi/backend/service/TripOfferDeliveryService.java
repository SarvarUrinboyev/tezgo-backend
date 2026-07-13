package com.taxi.backend.service;

import com.taxi.backend.enums.TripDriverOfferStatus;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.TripDriverOffer;
import com.taxi.backend.repository.TripDriverOfferRepository;
import com.taxi.backend.repository.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** Sends the unchanged mobile payload only after a durable, single-owner offer exists. */
@Service
public class TripOfferDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(TripOfferDeliveryService.class);

    private final TripDriverOfferRepository offerRepository;
    private final TripRepository tripRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PushNotificationService pushService;

    public TripOfferDeliveryService(TripDriverOfferRepository offerRepository,
                                    TripRepository tripRepository,
                                    SimpMessagingTemplate messagingTemplate,
                                    PushNotificationService pushService) {
        this.offerRepository = offerRepository;
        this.tripRepository = tripRepository;
        this.messagingTemplate = messagingTemplate;
        this.pushService = pushService;
    }

    @Async
    @Transactional
    public void deliverOfferAsync(Long offerId) {
        TripDriverOffer offer = offerRepository.findByIdForUpdate(offerId).orElse(null);
        if (offer == null || offer.getStatus() != TripDriverOfferStatus.PENDING_DELIVERY) return;
        if (!offer.getExpiresAt().isAfter(LocalDateTime.now())) return;

        Trip trip = tripRepository.findByIdForUpdate(offer.getTrip().getId()).orElse(null);
        if (trip == null || trip.getStatus() != TripStatus.SEARCHING || trip.getDriver() != null) {
            closeSkippedOffer(offer, trip);
            return;
        }
        Long driverId = offer.getDriver().getId();
        long offerExpiresAt = offer.getExpiresAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        int etaMinutes = offer.getDistanceKm() == null ? -1 : Math.max(1, (int) Math.ceil(offer.getDistanceKm() / 0.5));

        Map<String, Object> ws = new HashMap<>();
        ws.put("type", "NEW_ORDER");
        ws.put("tripId", trip.getId());
        ws.put("fromAddress", trip.getFromAddress());
        ws.put("toAddress", trip.getToAddress() != null ? trip.getToAddress() : "—");
        ws.put("price", (trip.getTotalPrice() != null ? trip.getTotalPrice() : 0L) / 100);
        ws.put("offerExpiresAt", offerExpiresAt);
        if (etaMinutes >= 0) ws.put("etaMinutes", etaMinutes);

        Map<String, Object> push = new HashMap<>();
        push.put("tripId", trip.getId());
        push.put("type", "ORDER_PUSH");
        push.put("event", "ORDER_PUSH");
        push.put("fromAddress", trip.getFromAddress() != null ? trip.getFromAddress() : "manzilsiz");
        push.put("toAddress", trip.getToAddress() != null ? trip.getToAddress() : "manzilsiz");
        push.put("price", (trip.getTotalPrice() != null ? trip.getTotalPrice() : 0L) / 100);
        push.put("fromLat", trip.getFromLat() != null ? trip.getFromLat() : 0.0);
        push.put("fromLon", trip.getFromLon() != null ? trip.getFromLon() : 0.0);
        push.put("toLat", trip.getToLat() != null ? trip.getToLat() : 0.0);
        push.put("toLon", trip.getToLon() != null ? trip.getToLon() : 0.0);
        if (trip.getTariff() != null) {
            push.put("tariffName", trip.getTariff().getName());
            push.put("calloutFee", trip.getTariff().getBasePrice() != null ? trip.getTariff().getBasePrice() : 0L);
        }
        if (etaMinutes >= 0) push.put("etaMinutes", etaMinutes);
        push.put("offerExpiresAt", offerExpiresAt);

        // The worker owns both locks while it performs its final state check.
        // Thus an already committed ACCEPT/CANCEL cannot enqueue a stale push.
        offer.setStatus(TripDriverOfferStatus.ACTIVE);
        offer.setDeliveryAttemptState("ATTEMPTED");
        offer.setUpdatedAt(LocalDateTime.now());
        try {
            messagingTemplate.convertAndSend("/topic/driver/" + driverId, ws);
        } catch (RuntimeException exception) {
            recordUnknownDeliveryFailure(offer, exception);
        }
        try {
            // Frozen PushNotificationService remains untouched; keep its data-only call unchanged.
            pushService.notifyDriver(driverId, null, null, push);
        } catch (RuntimeException exception) {
            recordUnknownDeliveryFailure(offer, exception);
        }
        offerRepository.save(offer);
        log.info("[SEQUENTIAL_DISPATCH] offerId={} attempted for exactly one driver", offerId);
    }

    private void closeSkippedOffer(TripDriverOffer offer, Trip trip) {
        boolean acceptedByOwner = trip != null
                && trip.getStatus() == TripStatus.ACCEPTED
                && trip.getDriver() != null
                && trip.getDriver().getId().equals(offer.getDriver().getId());
        offer.setStatus(acceptedByOwner ? TripDriverOfferStatus.ACCEPTED : TripDriverOfferStatus.CANCELLED);
        offer.setClosedAt(LocalDateTime.now());
        offer.setDeliveryAttemptState("SKIPPED_CLOSED");
        offer.setUpdatedAt(LocalDateTime.now());
        offerRepository.save(offer);
    }

    private void recordUnknownDeliveryFailure(TripDriverOffer offer, RuntimeException exception) {
        // Provider outcome may be unknown. Preserve the same owner and never advance/fan out here.
        offer.setDeliveryAttemptState("UNKNOWN_FAILURE");
        offer.setDeliveryError(exception.getClass().getSimpleName());
        log.warn("[SEQUENTIAL_DISPATCH] offerId={} delivery attempt failed type={}",
                offer.getId(), exception.getClass().getSimpleName());
    }
}
