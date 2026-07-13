package com.taxi.backend.service;

import com.taxi.backend.model.Trip;
import com.taxi.backend.model.TripDriverOffer;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Compatibility façade for every existing dispatch entry point.
 *
 * <p>The old helper sent to every matching driver (and then to a fallback
 * board). It now delegates all ownership decisions to the durable sequential
 * offer lifecycle. Keeping this façade avoids accidental alternate dispatch
 * paths in TripService, OperatorService and schedulers.</p>
 */
@Component
public class TripNotificationHelper {

    private final TripOfferLifecycleService offerLifecycle;

    public TripNotificationHelper(TripOfferLifecycleService offerLifecycle) {
        this.offerLifecycle = offerLifecycle;
    }

    public void notifyNearbyDrivers(Trip trip) {
        if (trip != null && trip.getId() != null) offerLifecycle.dispatchNextOffer(trip.getId());
    }

    public Optional<TripDriverOffer> notifySpecificDriver(Long tripId, Long driverId) {
        return offerLifecycle.dispatchSpecificDriver(tripId, driverId);
    }

    public TripDriverOffer requireCurrentOfferForAcceptance(Long tripId, Long driverId) {
        return offerLifecycle.requireCurrentOfferForAcceptance(tripId, driverId);
    }

    public void markOfferAccepted(Long offerId) {
        offerLifecycle.markAccepted(offerId);
    }

    public void rejectCurrentOfferAndDispatchNext(Long tripId, Long driverId) {
        offerLifecycle.rejectCurrentOfferAndDispatchNext(tripId, driverId);
    }

    public boolean acknowledgeCurrentOffer(Long tripId, Long driverId) {
        return offerLifecycle.acknowledgeCurrentOffer(tripId, driverId);
    }

    public Set<Long> liveOfferTripIdsForDriver(Long driverId) {
        return offerLifecycle.liveOfferTripIdsForDriver(driverId);
    }

    public void cancelOpenOffer(Long tripId) {
        offerLifecycle.cancelOpenOffer(tripId);
    }

    public void expireDueOffers() {
        for (Long offerId : offerLifecycle.expiredLiveOfferIds()) {
            offerLifecycle.expireOfferAndDispatchNext(offerId);
        }
    }

    public void recoverPendingOfferDeliveries() {
        offerLifecycle.recoverPendingOfferDeliveries();
    }
}
