package com.taxi.backend.service;

import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.TripDriverOffer;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TripDriverOfferRepository;
import com.taxi.backend.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripNotificationHelperBusyTest {
    @Mock private TripRepository trips;
    @Mock private DriverRepository drivers;
    @Mock private TripDriverOfferRepository offers;
    @Mock private MatchingService matching;
    @Mock private TripOfferDeliveryService delivery;

    @Test
    void busyRankOneIsSkippedAndOnlyNextEligibleDriverGetsOffer() {
        Trip trip = new Trip();
        trip.setId(1L); trip.setStatus(TripStatus.SEARCHING); trip.setTotalPrice(100L);
        TripOfferLifecycleService lifecycle = new TripOfferLifecycleService(trips, drivers, offers, matching, delivery);
        ReflectionTestUtils.setField(lifecycle, "matchingRadiusKm", 5.0d);
        when(trips.findByIdForUpdate(1L)).thenReturn(Optional.of(trip));
        when(offers.findLiveByTripIdForUpdate(anyLong(), any())).thenReturn(List.of());
        when(offers.findAllOfferedDriverIdsByTripId(1L)).thenReturn(List.of());
        when(offers.findMaxGenerationByTripId(1L)).thenReturn(0);
        when(trips.findBusyDriverIds(TripStatus.ACTIVE_DRIVER_STATUSES)).thenReturn(List.of(7L));
        when(matching.findNearbyDrivers(anyDouble(), anyDouble(), anyDouble(), anyBoolean())).thenReturn(List.of(
                candidate(7L), candidate(8L)));
        when(drivers.findById(8L)).thenReturn(Optional.of(driver(8L)));
        when(drivers.findEnabledServiceTypesByDriverId(8L)).thenReturn(List.of());
        when(offers.saveAndFlush(any(TripDriverOffer.class))).thenAnswer(i -> i.getArgument(0));

        lifecycle.dispatchNextOffer(1L);

        ArgumentCaptor<TripDriverOffer> offer = ArgumentCaptor.forClass(TripDriverOffer.class);
        org.mockito.Mockito.verify(offers).saveAndFlush(offer.capture());
        assertThat(offer.getValue().getDriver().getId()).isEqualTo(8L);
    }

    private static MatchingService.MatchedDriver candidate(long id) {
        return new MatchingService.MatchedDriver(id, "d", "Cobalt", "01A", 0, 0, 1, 2, 5,
                "STANDART", null, 0, null);
    }
    private static Driver driver(long id) {
        Driver d = new Driver(); d.setId(id); d.setStatus(DriverStatus.ACTIVE); d.setOnline(true);
        d.setBalance(0L); d.setCarModel("Cobalt"); return d;
    }
}
