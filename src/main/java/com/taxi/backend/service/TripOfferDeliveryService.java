package com.taxi.backend.service;

import com.taxi.backend.enums.TripDriverOfferStatus;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.TripDriverOffer;
import com.taxi.backend.repository.TripDriverOfferRepository;
import com.taxi.backend.repository.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
    private final ObjectProvider<TripOfferLifecycleService> lifecycleProvider;
    private final DispatchOfferTimingProperties timing;
    private final Clock clock;

    /** Existing narrow unit tests retain this constructor. */
    public TripOfferDeliveryService(TripDriverOfferRepository offerRepository,
                                    TripRepository tripRepository,
                                    SimpMessagingTemplate messagingTemplate,
                                    PushNotificationService pushService) {
        this(offerRepository, tripRepository, messagingTemplate, pushService, null,
                new DispatchOfferTimingProperties(), Clock.systemDefaultZone());
    }

    @Autowired
    public TripOfferDeliveryService(TripDriverOfferRepository offerRepository,
                                    TripRepository tripRepository,
                                    SimpMessagingTemplate messagingTemplate,
                                    PushNotificationService pushService,
                                    ObjectProvider<TripOfferLifecycleService> lifecycleProvider,
                                    DispatchOfferTimingProperties timing,
                                    @Qualifier("dispatchClock") Clock clock) {
        this.offerRepository = offerRepository;
        this.tripRepository = tripRepository;
        this.messagingTemplate = messagingTemplate;
        this.pushService = pushService;
        this.lifecycleProvider = lifecycleProvider;
        this.timing = timing;
        this.clock = clock;
    }

    @Async
    @Transactional
    public void deliverOfferAsync(Long offerId) {
        // Every lifecycle transition locks trip -> offer. Preserve that order.
        TripDriverOffer snapshot = offerRepository.findById(offerId).orElse(null);
        if (snapshot == null) return;
        Trip trip = tripRepository.findByIdForUpdate(snapshot.getTrip().getId()).orElse(null);
        TripDriverOffer offer = offerRepository.findByIdForUpdate(offerId).orElse(null);
        if (offer == null || offer.getStatus() != TripDriverOfferStatus.PENDING_DELIVERY) return;
        LocalDateTime now = now();
        if (offer.getResponseExpiresAt() != null && !offer.getResponseExpiresAt().isAfter(now)) return;
        if (offer.getNextDeliveryAttemptAt() != null && offer.getNextDeliveryAttemptAt().isAfter(now)) return;
        if (offer.getDeliveryAttemptCount() >= timing.maxDeliveryAttempts()) return;
        if (trip == null || trip.getStatus() != TripStatus.SEARCHING || trip.getDriver() != null) {
            closeSkippedOffer(offer, trip, now);
            return;
        }

        initializeFixedDeadline(offer, now);
        if (!timing.canStartAnotherDeliveryAttempt(now, offer.getResponseExpiresAt())) {
            offer.setDeliveryAttemptState("DEADLINE_EXHAUSTED");
            offer.setNextDeliveryAttemptAt(null);
            offer.setUpdatedAt(now);
            offerRepository.save(offer);
            return;
        }

        offer.setDeliveryAttemptCount(offer.getDeliveryAttemptCount() + 1);
        offer.setDeliveryAttemptState("ATTEMPTED");
        offer.setNextDeliveryAttemptAt(null);
        offer.setUpdatedAt(now);

        Long driverId = offer.getDriver().getId();
        long offerExpiresAt = offer.getResponseExpiresAt().atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        int etaMinutes = offer.getDistanceKm() == null ? -1
                : Math.max(1, (int) Math.ceil(offer.getDistanceKm() / 0.5));

        Map<String, Object> ws = websocketPayload(trip, offerExpiresAt, etaMinutes);
        Map<String, Object> push = orderPushPayload(trip, offerExpiresAt, etaMinutes);

        try {
            messagingTemplate.convertAndSend("/topic/driver/" + driverId, ws);
        } catch (RuntimeException exception) {
            log.warn("[SEQUENTIAL_DISPATCH] offerId={} websocket delivery unknown", offerId,
                    exception.getClass().getSimpleName());
        }

        OrderPushDeliveryOutcome outcome = pushService.sendOrderPushWithOutcome(driverId, push);
        applyOutcome(offer, outcome, now);
        if (outcome.isPermanentRecipientFailure() && outcome.recipientStillCurrent()) {
            offerRepository.save(offer); // persist fingerprint/outcome before the guarded terminal transition
            if (lifecycleProvider == null) {
                log.warn("[SEQUENTIAL_DISPATCH] offerId={} permanent outcome ignored: lifecycle unavailable in test wiring", offerId);
                return;
            }
            lifecycleProvider.getObject().handlePermanentRecipientFailure(offerId, outcome);
            return;
        }
        offerRepository.save(offer);
        log.info("[SEQUENTIAL_DISPATCH] offerId={} attempted for exactly one driver outcome={}",
                offerId, outcome.category());
    }

    private void initializeFixedDeadline(TripDriverOffer offer, LocalDateTime now) {
        if (offer.getFirstDeliveryAttemptAt() == null) {
            offer.setFirstDeliveryAttemptAt(now);
        }
        if (offer.getResponseExpiresAt() == null) {
            LocalDateTime deadline = timing.safeResponseDeadline(offer.getFirstDeliveryAttemptAt());
            offer.setResponseExpiresAt(deadline);
            // Legacy expires_at remains an exact compatibility mirror; it is no longer calculated at row creation.
            offer.setExpiresAt(deadline);
        }
    }

    private Map<String, Object> websocketPayload(Trip trip, long offerExpiresAt, int etaMinutes) {
        Map<String, Object> ws = new HashMap<>();
        ws.put("type", "NEW_ORDER");
        ws.put("tripId", trip.getId());
        ws.put("fromAddress", trip.getFromAddress());
        ws.put("toAddress", trip.getToAddress() != null ? trip.getToAddress() : "вЂ”");
        ws.put("price", (trip.getTotalPrice() != null ? trip.getTotalPrice() : 0L) / 100);
        ws.put("offerExpiresAt", offerExpiresAt);
        if (etaMinutes >= 0) ws.put("etaMinutes", etaMinutes);
        return ws;
    }

    private Map<String, Object> orderPushPayload(Trip trip, long offerExpiresAt, int etaMinutes) {
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
        return push;
    }

    private void applyOutcome(TripDriverOffer offer, OrderPushDeliveryOutcome outcome, LocalDateTime now) {
        offer.setLastDeliveryOutcome(outcome.category().name());
        offer.setDeliveryRecipientFingerprint(outcome.recipientFingerprint());
        offer.setDeliveryError(outcome.providerCode());
        switch (outcome.category()) {
            case SUCCESS -> {
                offer.setStatus(TripDriverOfferStatus.ACTIVE);
                offer.setProviderAcceptedAt(now);
                offer.setDeliveryAttemptState("PROVIDER_ACCEPTED");
            }
            case PERMANENT_RECIPIENT_FAILURE -> offer.setDeliveryAttemptState("PERMANENT_RECIPIENT_FAILURE");
            case TRANSIENT_FAILURE -> scheduleTypedTransientRetryIfSafe(offer, now);
            case UNKNOWN_FAILURE -> offer.setDeliveryAttemptState("UNKNOWN_FAILURE");
            case GLOBAL_CONFIGURATION_FAILURE -> {
                offer.setDeliveryAttemptState("GLOBAL_CONFIGURATION_FAILURE");
                log.error("[SEQUENTIAL_DISPATCH] global order-push configuration failure offerId={} code={}",
                        offer.getId(), outcome.providerCode());
            }
            case UNSUPPORTED_DELIVERY_PATH -> {
                offer.setDeliveryAttemptState("UNSUPPORTED_DELIVERY_PATH");
                log.error("[SEQUENTIAL_DISPATCH] unsupported unbounded order-push path offerId={} code={}",
                        offer.getId(), outcome.providerCode());
            }
        }
    }

    private void scheduleTypedTransientRetryIfSafe(TripDriverOffer offer, LocalDateTime now) {
        LocalDateTime candidate = now.plus(timing.transientRetryBackoff());
        if (offer.getDeliveryAttemptCount() < timing.maxDeliveryAttempts()
                && timing.canStartAnotherDeliveryAttempt(candidate, offer.getResponseExpiresAt())) {
            offer.setDeliveryAttemptState("TRANSIENT_RETRY_SCHEDULED");
            offer.setNextDeliveryAttemptAt(candidate);
            return;
        }
        // With the conservative default maxAttempts=1 there is intentionally no auto re-alarm.
        offer.setDeliveryAttemptState("TRANSIENT_RETRY_EXHAUSTED");
        offer.setNextDeliveryAttemptAt(null);
    }

    private void closeSkippedOffer(TripDriverOffer offer, Trip trip, LocalDateTime now) {
        boolean acceptedByOwner = trip != null
                && trip.getStatus() == TripStatus.ACCEPTED
                && trip.getDriver() != null
                && trip.getDriver().getId().equals(offer.getDriver().getId());
        offer.setStatus(acceptedByOwner ? TripDriverOfferStatus.ACCEPTED : TripDriverOfferStatus.CANCELLED);
        offer.setClosedAt(now);
        offer.setDeliveryAttemptState("SKIPPED_CLOSED");
        offer.setUpdatedAt(now);
        offerRepository.save(offer);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
