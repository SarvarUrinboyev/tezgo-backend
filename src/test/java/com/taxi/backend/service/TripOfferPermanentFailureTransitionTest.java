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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripOfferPermanentFailureTransitionTest {

    @Mock private TripRepository trips;
    @Mock private DriverRepository drivers;
    @Mock private TripDriverOfferRepository offers;
    @Mock private MatchingService matching;
    @Mock private TripOfferDeliveryService delivery;

    private TripOfferLifecycleService lifecycle;
    private Trip trip;
    private TripDriverOffer first;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-14T08:00:00Z"), ZoneOffset.UTC);
        lifecycle = new TripOfferLifecycleService(trips, drivers, offers, matching, delivery,
                new DispatchOfferTimingProperties(), clock);
        trip = new Trip();
        trip.setId(44L); trip.setStatus(TripStatus.SEARCHING); trip.setFromLat(41.3); trip.setFromLon(69.6);
        first = offer(driver(1L), TripDriverOfferStatus.PENDING_DELIVERY);
        first.setId(101L); first.setDeliveryRecipientFingerprint("same-token-fingerprint");
        lenient().when(offers.findById(101L)).thenReturn(Optional.of(first));
        lenient().when(offers.findByIdForUpdate(101L)).thenReturn(Optional.of(first));
        lenient().when(trips.findByIdForUpdate(44L)).thenReturn(Optional.of(trip));
        lenient().when(offers.findAllOfferedDriverIdsByTripId(44L)).thenReturn(List.of(1L));
        lenient().when(offers.findMaxGenerationByTripId(44L)).thenReturn(1);
        lenient().when(trips.findBusyDriverIds(TripStatus.ACTIVE_DRIVER_STATUSES)).thenReturn(List.of());
        lenient().when(drivers.findEnabledServiceTypesByDriverId(anyLong())).thenReturn(List.of());
        lenient().when(matching.findNearbyDrivers(anyDouble(), anyDouble(), anyDouble(), anyBoolean()))
                .thenReturn(List.of(new MatchingService.MatchedDriver(1L, "A", "Cobalt", "01A1", 41.3, 69.6,
                        1d, 2d, 5d, "STANDART", null, 0d, null),
                        new MatchingService.MatchedDriver(2L, "B", "Cobalt", "01A2", 41.3, 69.6,
                                2d, 3d, 5d, "STANDART", null, 0d, null)));
        lenient().when(drivers.findById(2L)).thenReturn(Optional.of(driver(2L)));
        lenient().when(offers.saveAndFlush(any(TripDriverOffer.class))).thenAnswer(invocation -> {
            TripDriverOffer next = invocation.getArgument(0);
            next.setId(102L);
            return next;
        });
    }

    @Test
    void unregisteredTransitionsExactlyOnceAndQueuesOneNextGeneration() {
        OrderPushDeliveryOutcome unregistered = permanent("same-token-fingerprint", true);

        lifecycle.handlePermanentRecipientFailure(101L, unregistered);
        lifecycle.handlePermanentRecipientFailure(101L, unregistered);

        assertThat(first.getStatus()).isEqualTo(TripDriverOfferStatus.DELIVERY_FAILED);
        assertThat(first.getDeliveryError()).isEqualTo("UNREGISTERED");
        ArgumentCaptor<TripDriverOffer> next = ArgumentCaptor.forClass(TripDriverOffer.class);
        verify(offers).saveAndFlush(next.capture());
        assertThat(next.getValue().getGeneration()).isEqualTo(2);
        assertThat(next.getValue().getDriver().getId()).isEqualTo(2L);
        verify(delivery).deliverOfferAsync(102L);
    }

    @Test
    void tokenChangeDuringFlightCannotCloseOrAdvanceTheCurrentOwner() {
        lifecycle.handlePermanentRecipientFailure(101L, permanent("same-token-fingerprint", false));

        assertThat(first.getStatus()).isEqualTo(TripDriverOfferStatus.PENDING_DELIVERY);
        verify(offers, never()).saveAndFlush(any());
        verify(delivery, never()).deliverOfferAsync(anyLong());
    }

    private TripDriverOffer offer(Driver owner, TripDriverOfferStatus status) {
        TripDriverOffer offer = new TripDriverOffer();
        offer.setTrip(trip); offer.setDriver(owner); offer.setGeneration(1); offer.setCandidateRank(1);
        offer.setStatus(status); offer.setResponseExpiresAt(LocalDateTime.of(2026, 7, 14, 8, 1));
        return offer;
    }

    private static Driver driver(long id) {
        Driver driver = new Driver();
        driver.setId(id); driver.setStatus(DriverStatus.ACTIVE); driver.setOnline(true); driver.setBalance(0L);
        driver.setCarModel("Cobalt");
        return driver;
    }

    private static OrderPushDeliveryOutcome permanent(String fingerprint, boolean stillCurrent) {
        return new OrderPushDeliveryOutcome(OrderPushDeliveryOutcomeCategory.PERMANENT_RECIPIENT_FAILURE,
                "UNREGISTERED", null, fingerprint, stillCurrent, false);
    }
}
