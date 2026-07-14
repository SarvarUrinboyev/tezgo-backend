package com.taxi.backend.service;

import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.enums.TripDriverOfferStatus;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.TripDriverOffer;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TripDriverOfferRepository;
import com.taxi.backend.repository.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Single-owner dispatch state machine. A live driver offer is committed before
 * any WebSocket/FCM hand-off and only its owner can ACK, decline or accept.
 */
@Service
public class TripOfferLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(TripOfferLifecycleService.class);
    private static final Set<TripDriverOfferStatus> LIVE = EnumSet.of(
            TripDriverOfferStatus.PENDING_DELIVERY,
            TripDriverOfferStatus.ACTIVE,
            TripDriverOfferStatus.ACKNOWLEDGED);
    private static final Set<String> AUTOMATIC_ADVANCEMENT_BLOCKED_OUTCOMES = Set.of(
            OrderPushDeliveryOutcomeCategory.GLOBAL_CONFIGURATION_FAILURE.name(),
            OrderPushDeliveryOutcomeCategory.UNSUPPORTED_DELIVERY_PATH.name());

    private final TripRepository tripRepository;
    private final DriverRepository driverRepository;
    private final TripDriverOfferRepository offerRepository;
    private final MatchingService matchingService;
    private final TripOfferDeliveryService deliveryService;

    @Value("${matching.radius-km:5.0}")
    private double matchingRadiusKm;

    private final DispatchOfferTimingProperties timing;
    private final Clock clock;

    /** Narrow test compatibility constructor; production injects the shared timing and Clock. */
    public TripOfferLifecycleService(TripRepository tripRepository,
                                      DriverRepository driverRepository,
                                      TripDriverOfferRepository offerRepository,
                                      MatchingService matchingService,
                                      TripOfferDeliveryService deliveryService) {
        this(tripRepository, driverRepository, offerRepository, matchingService, deliveryService,
                new DispatchOfferTimingProperties(), Clock.systemDefaultZone());
    }

    @Autowired
    public TripOfferLifecycleService(TripRepository tripRepository,
                                     DriverRepository driverRepository,
                                     TripDriverOfferRepository offerRepository,
                                     MatchingService matchingService,
                                     TripOfferDeliveryService deliveryService,
                                     DispatchOfferTimingProperties timing,
                                     Clock clock) {
        this.tripRepository = tripRepository;
        this.driverRepository = driverRepository;
        this.offerRepository = offerRepository;
        this.matchingService = matchingService;
        this.deliveryService = deliveryService;
        this.timing = timing;
        this.clock = clock;
    }

    /** Create exactly one offer for the highest-ranked eligible, not-yet-offered driver. */
    @Transactional
    public Optional<TripDriverOffer> dispatchNextOffer(Long tripId) {
        Trip trip = lockSearchingTrip(tripId);
        if (hasLiveOffer(tripId)) return Optional.empty();
        return createNextRankedOffer(trip);
    }

    /** Operator/admin explicit reassignment still becomes a normal durable offer. */
    @Transactional
    public Optional<TripDriverOffer> dispatchSpecificDriver(Long tripId, Long driverId) {
        Trip trip = lockSearchingTrip(tripId);
        closeLiveOffers(tripId, TripDriverOfferStatus.SUPERSEDED);

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Haydovchi topilmadi"));
        if (!isCurrentlyEligible(driver, trip, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Haydovchi bu buyurtma uchun hozir mos emas");
        }
        if (offerRepository.findAllOfferedDriverIdsByTripId(tripId).contains(driverId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Haydovchiga shu buyurtma uchun avval taklif berilgan");
        }
        return Optional.of(createOffer(trip, driver, 0, null));
    }

    /** Only the active offer owner can turn an offer into a trip acceptance. */
    @Transactional
    public TripDriverOffer requireCurrentOfferForAcceptance(Long tripId, Long driverId) {
        Trip trip = lockSearchingTrip(tripId);
        TripDriverOffer offer = currentOffer(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Siz uchun faol buyurtma taklifi yo'q"));
        assertOwnedAndNotExpired(offer, driverId);
        return offer;
    }

    @Transactional
    public void markAccepted(Long offerId) {
        TripDriverOffer offer = offerRepository.findByIdForUpdate(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Taklif topilmadi"));
        if (!LIVE.contains(offer.getStatus())) return;
        offer.setStatus(TripDriverOfferStatus.ACCEPTED);
        offer.setClosedAt(now());
        offerRepository.save(offer);
    }

    /** Reject current offer and immediately create the next sequential candidate. */
    @Transactional
    public void rejectCurrentOfferAndDispatchNext(Long tripId, Long driverId) {
        Trip trip = lockSearchingTrip(tripId);
        TripDriverOffer offer = currentOffer(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Siz uchun faol buyurtma taklifi yo'q"));
        assertOwnedAndNotExpired(offer, driverId);
        close(offer, TripDriverOfferStatus.REJECTED);
        createNextRankedOffer(trip);
    }

    /** ACK is auditable and is intentionally limited to the current owner. */
    @Transactional
    public boolean acknowledgeCurrentOffer(Long tripId, Long driverId) {
        try {
            lockSearchingTrip(tripId);
            Optional<TripDriverOffer> current = currentOffer(tripId);
            if (current.isEmpty()) return false;
            TripDriverOffer offer = current.get();
            assertOwnedAndNotExpired(offer, driverId);
            if (offer.getStatus() == TripDriverOfferStatus.PENDING_DELIVERY
                    || offer.getStatus() == TripDriverOfferStatus.ACTIVE) {
                offer.setStatus(TripDriverOfferStatus.ACKNOWLEDGED);
                offer.setAcknowledgedAt(now());
                offerRepository.save(offer);
            }
            return true;
        } catch (ResponseStatusException ignored) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public Set<Long> liveOfferTripIdsForDriver(Long driverId) {
        return new HashSet<>(offerRepository.findLiveTripIdsByDriverId(driverId, LIVE, now()));
    }

    @Transactional
    public void cancelOpenOffer(Long tripId) {
        tripRepository.findByIdForUpdate(tripId).ifPresent(trip -> closeLiveOffers(tripId, TripDriverOfferStatus.CANCELLED));
    }

    /** Expiry is a state transition, not a broadcast trigger. */
    @Transactional
    public void expireOfferAndDispatchNext(Long offerId) {
        TripDriverOffer snapshot = offerRepository.findById(offerId).orElse(null);
        if (snapshot == null) return;
        Trip trip = tripRepository.findByIdForUpdate(snapshot.getTrip().getId()).orElse(null);
        if (trip == null) return;
        TripDriverOffer offer = offerRepository.findByIdForUpdate(offerId).orElse(null);
        if (offer == null || !LIVE.contains(offer.getStatus()) || offer.getResponseExpiresAt() == null
                || offer.getResponseExpiresAt().isAfter(now())) return;
        if (offer.getLastDeliveryOutcome() != null
                && AUTOMATIC_ADVANCEMENT_BLOCKED_OUTCOMES.contains(offer.getLastDeliveryOutcome())) return;
        close(offer, TripDriverOfferStatus.EXPIRED);
        if (trip.getStatus() == TripStatus.SEARCHING && trip.getDriver() == null) {
            createNextRankedOffer(trip);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> expiredLiveOfferIds() {
        return offerRepository.findExpiredLiveOfferIds(LIVE, AUTOMATIC_ADVANCEMENT_BLOCKED_OUTCOMES, now());
    }

    /** A restart can leave a durable offer queued but undelivered; retry its same owner only. */
    @Transactional(readOnly = true)
    public void recoverPendingOfferDeliveries() {
        for (Long offerId : offerRepository.findPendingDeliveryOfferIds(now())) {
            deliveryService.deliverOfferAsync(offerId);
        }
    }

    private Trip lockSearchingTrip(Long tripId) {
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Buyurtma topilmadi"));
        if (trip.getStatus() != TripStatus.SEARCHING || trip.getDriver() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Buyurtma endi qidiruvda emas");
        }
        return trip;
    }

    private Optional<TripDriverOffer> createNextRankedOffer(Trip trip) {
        Long tripId = trip.getId();
        Set<Long> tried = new HashSet<>(offerRepository.findAllOfferedDriverIdsByTripId(tripId));
        tried.addAll(ExcludedDriverFilter.parse(trip.getExcludedDriverIds()));
        Set<Long> busy = new HashSet<>(tripRepository.findBusyDriverIds(TripStatus.ACTIVE_DRIVER_STATUSES));
        boolean operatorOrder = trip.getSource() != null && trip.getSource().startsWith("CALL");

        List<MatchingService.MatchedDriver> ranked = matchingService.findNearbyDrivers(
                trip.getFromLat() == null ? 0.0 : trip.getFromLat(),
                trip.getFromLon() == null ? 0.0 : trip.getFromLon(),
                matchingRadiusKm,
                operatorOrder);

        for (int index = 0; index < ranked.size(); index++) {
            MatchingService.MatchedDriver candidate = ranked.get(index);
            if (tried.contains(candidate.driverId()) || busy.contains(candidate.driverId())) continue;
            Driver driver = driverRepository.findById(candidate.driverId()).orElse(null);
            if (!isCurrentlyEligible(driver, trip, operatorOrder)) continue;
            return Optional.of(createOffer(trip, driver, index + 1, candidate.distanceKm()));
        }

        log.info("[SEQUENTIAL_DISPATCH] tripId={} uchun yangi mos nomzod yo'q", tripId);
        return Optional.empty();
    }

    private boolean isCurrentlyEligible(Driver driver, Trip trip, boolean ignoreCooldown) {
        if (driver == null || driver.getStatus() != DriverStatus.ACTIVE || !driver.isOnline()) return false;
        if (driver.getBalance() != null && driver.getBalance() < 0) return false;
        if (!ignoreCooldown && driver.isInCooldown()) return false;
        if (!DriverTariffFilter.accepts(driver, trip)) return false;
        Collection<String> enabledServices = driverRepository.findEnabledServiceTypesByDriverId(driver.getId())
                .stream().map(Enum::name).collect(Collectors.toSet());
        return DriverServiceFilter.accepts(enabledServices, trip.getSelectedServices());
    }

    private TripDriverOffer createOffer(Trip trip, Driver driver, int rank, Double distanceKm) {
        LocalDateTime now = now();
        TripDriverOffer offer = new TripDriverOffer();
        offer.setTrip(trip);
        offer.setDriver(driver);
        offer.setGeneration(offerRepository.findMaxGenerationByTripId(trip.getId()) + 1);
        offer.setCandidateRank(rank);
        offer.setDistanceKm(distanceKm);
        offer.setStatus(TripDriverOfferStatus.PENDING_DELIVERY);
        offer.setOfferedAt(now);
        offer.setCreatedAt(now);
        offer.setUpdatedAt(now);
        offer.setDeliveryAttemptState("NOT_QUEUED");
        offer.setDeliveryAttemptCount(0);
        TripDriverOffer saved = offerRepository.saveAndFlush(offer);

        // Legacy column remains a single-current-owner compatibility mirror; it no longer means fan-out.
        trip.setNotifiedDriverIds(String.valueOf(driver.getId()));
        if (trip.getDispatchedAt() == null) trip.setDispatchedAt(now);
        tripRepository.save(trip);
        queueDeliveryAfterCommit(saved.getId());
        log.info("[SEQUENTIAL_DISPATCH] tripId={} offerId={} generation={} driverId={} rank={}",
                trip.getId(), saved.getId(), saved.getGeneration(), driver.getId(), rank);
        return saved;
    }

    private boolean hasLiveOffer(Long tripId) {
        return !offerRepository.findLiveByTripIdForUpdate(tripId, LIVE).isEmpty();
    }

    private Optional<TripDriverOffer> currentOffer(Long tripId) {
        return offerRepository.findLiveByTripIdForUpdate(tripId, LIVE).stream().findFirst();
    }

    private void closeLiveOffers(Long tripId, TripDriverOfferStatus terminalStatus) {
        for (TripDriverOffer offer : offerRepository.findLiveByTripIdForUpdate(tripId, LIVE)) {
            close(offer, terminalStatus);
        }
    }

    private void close(TripDriverOffer offer, TripDriverOfferStatus terminalStatus) {
        offer.setStatus(terminalStatus);
        offer.setClosedAt(now());
        offerRepository.save(offer);
    }

    private void assertOwnedAndNotExpired(TripDriverOffer offer, Long driverId) {
        if (!offer.getDriver().getId().equals(driverId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu buyurtma sizga taklif qilinmagan");
        }
        if (offer.getResponseExpiresAt() != null && !offer.getResponseExpiresAt().isAfter(now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Buyurtma taklifi muddati tugagan");
        }
    }

    private void queueDeliveryAfterCommit(Long offerId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliveryService.deliverOfferAsync(offerId);
                }
            });
            return;
        }
        deliveryService.deliverOfferAsync(offerId);
    }

    /**
     * Only a typed UNREGISTERED outcome whose recipient is still current may advance one rank.
     * The lock order remains trip -> offer and a duplicate result becomes a no-op.
     */
    @Transactional
    public void handlePermanentRecipientFailure(Long offerId, OrderPushDeliveryOutcome outcome) {
        if (outcome == null || !outcome.isPermanentRecipientFailure() || !outcome.recipientStillCurrent()) return;
        TripDriverOffer snapshot = offerRepository.findById(offerId).orElse(null);
        if (snapshot == null) return;
        Trip trip = tripRepository.findByIdForUpdate(snapshot.getTrip().getId()).orElse(null);
        TripDriverOffer offer = offerRepository.findByIdForUpdate(offerId).orElse(null);
        if (trip == null || offer == null || trip.getStatus() != TripStatus.SEARCHING || trip.getDriver() != null
                || !LIVE.contains(offer.getStatus())
                || !Objects.equals(offer.getDeliveryRecipientFingerprint(), outcome.recipientFingerprint())) {
            return;
        }
        offer.setStatus(TripDriverOfferStatus.DELIVERY_FAILED);
        offer.setDeliveryAttemptState("PERMANENT_RECIPIENT_FAILURE");
        offer.setLastDeliveryOutcome(outcome.category().name());
        offer.setDeliveryError(outcome.providerCode());
        offer.setClosedAt(now());
        offer.setUpdatedAt(now());
        offerRepository.save(offer);
        createNextRankedOffer(trip);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
