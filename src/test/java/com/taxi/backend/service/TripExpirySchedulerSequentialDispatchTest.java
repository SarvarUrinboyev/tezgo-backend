package com.taxi.backend.service;

import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Trip;
import com.taxi.backend.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Source coverage: scheduled and recovery workers only invoke the single-owner facade. */
@ExtendWith(MockitoExtension.class)
class TripExpirySchedulerSequentialDispatchTest {

    @Mock private TripRepository trips;
    @Mock private SimpMessagingTemplate messaging;
    @Mock private TripNotificationHelper notifications;

    @Test
    void scheduledTrip_transitionsToSearchingThenUsesSequentialDispatcher() {
        Trip trip = new Trip();
        trip.setId(73L);
        trip.setStatus(TripStatus.SCHEDULED);
        when(trips.findByStatusAndScheduledAtLessThanEqual(org.mockito.ArgumentMatchers.eq(TripStatus.SCHEDULED),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(trip));
        when(trips.save(trip)).thenReturn(trip);

        new TripExpiryScheduler(trips, messaging, notifications).dispatchScheduledTrips();

        assertEquals(TripStatus.SEARCHING, trip.getStatus());
        verify(notifications).notifyNearbyDrivers(trip);
        verifyNoInteractions(messaging);
    }

    @Test
    void recoveryWorker_neverBroadcastsAndOnlyAdvancesThroughFacade() {
        Trip searching = new Trip();
        searching.setId(74L);
        searching.setStatus(TripStatus.SEARCHING);
        when(trips.findByStatusOrderByCreatedAtAsc(TripStatus.SEARCHING)).thenReturn(List.of(searching));

        new TripExpiryScheduler(trips, messaging, notifications).advanceSequentialOffers();

        InOrder sequence = inOrder(notifications);
        sequence.verify(notifications).recoverPendingOfferDeliveries();
        sequence.verify(notifications).expireDueOffers();
        sequence.verify(notifications).notifyNearbyDrivers(searching);
        verifyNoInteractions(messaging);
    }
}
