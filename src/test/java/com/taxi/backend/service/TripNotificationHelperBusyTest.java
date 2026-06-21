package com.taxi.backend.service;

import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Trip;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Commit 1 — matching push faol tripi bor (band) haydovchini o'tkazib yuboradi,
 * bo'sh haydovchiga esa yuboradi.
 */
@ExtendWith(MockitoExtension.class)
class TripNotificationHelperBusyTest {

    @Mock private MatchingService matchingService;
    @Mock private DriverRepository driverRepository;
    @Mock private AsyncNotificationService asyncNotifier;
    @Mock private TripRepository tripRepository;

    private TripNotificationHelper helper;

    @BeforeEach
    void setup() {
        helper = new TripNotificationHelper(matchingService, driverRepository, asyncNotifier, tripRepository);
        ReflectionTestUtils.setField(helper, "matchingRadiusKm", 5.0);
    }

    @Test
    @DisplayName("Band haydovchi (7) o'tkazib yuboriladi, bo'sh haydovchi (8) ga xabar yuboriladi")
    void notifyNearbyDrivers_skipsBusyDriver() {
        Trip trip = new Trip();
        trip.setId(1L);
        trip.setFromLat(41.30);
        trip.setFromLon(69.60);
        trip.setTotalPrice(1_000_000L);

        var busy = new MatchingService.MatchedDriver(7L, "Band", "Cobalt", "01A777AA",
                41.30, 69.60, 1.0, 3.0, 5.0, "EKONOM,DAMAS,BIZNES", 0.0, null);
        var free = new MatchingService.MatchedDriver(8L, "Bo'sh", "Nexia", "01A888BB",
                41.30, 69.60, 1.2, 4.0, 5.0, "EKONOM,DAMAS,BIZNES", 0.0, null);
        when(matchingService.findNearbyDrivers(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(busy, free));
        when(tripRepository.findBusyDriverIds(TripStatus.ACTIVE_DRIVER_STATUSES))
                .thenReturn(List.of(7L)); // 7-haydovchi band
        when(driverRepository.findByIsOnlineTrue()).thenReturn(List.of()); // fallback uchun bo'sh

        helper.notifyNearbyDrivers(trip);

        verify(asyncNotifier).notifyDriverAsync(eq(8L), anyMap());
        verify(asyncNotifier, never()).notifyDriverAsync(eq(7L), anyMap());
    }
}
