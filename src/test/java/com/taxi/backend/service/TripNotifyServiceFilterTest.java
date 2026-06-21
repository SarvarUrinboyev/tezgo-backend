package com.taxi.backend.service;

import com.taxi.backend.enums.ServiceType;
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
 * Service hard-filter (matching push): notifyNearbyDrivers buyurtma xizmatlarini
 * to'liq qoplamagan nomzodga xabar yubormaydi (tariff filtri yonida).
 */
@ExtendWith(MockitoExtension.class)
class TripNotifyServiceFilterTest {

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
    @DisplayName("order [ROOF_LUGGAGE]: xizmatsiz nomzod (7) skip, xizmatli (8) ga xabar")
    void notifyNearbyDrivers_skipsDriverMissingService() {
        Trip trip = new Trip();
        trip.setId(1L);
        trip.setFromLat(41.30);
        trip.setFromLon(69.60);
        trip.setTotalPrice(1_000_000L);
        trip.setSelectedServices("ROOF_LUGGAGE");

        var missing = new MatchingService.MatchedDriver(7L, "Xizmatsiz", "Cobalt", "01A777AA",
                41.30, 69.60, 1.0, 3.0, 5.0, "EKONOM,DAMAS,BIZNES", null, 0.0, null);
        var has = new MatchingService.MatchedDriver(8L, "Xizmatli", "Nexia", "01A888BB",
                41.30, 69.60, 1.2, 4.0, 5.0, "EKONOM,DAMAS,BIZNES", null, 0.0, null);
        when(matchingService.findNearbyDrivers(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(missing, has));
        when(tripRepository.findBusyDriverIds(TripStatus.ACTIVE_DRIVER_STATUSES)).thenReturn(List.of());
        when(driverRepository.findByIsOnlineTrue()).thenReturn(List.of()); // fallback bo'sh
        // Faqat 8-haydovchida ROOF_LUGGAGE yoqilgan
        List<Object[]> rows = java.util.Collections.singletonList(new Object[]{8L, ServiceType.ROOF_LUGGAGE});
        when(driverRepository.findEnabledServiceRowsByDriverIds(anyCollection())).thenReturn(rows);

        helper.notifyNearbyDrivers(trip);

        verify(asyncNotifier).notifyDriverAsync(eq(8L), anyMap());
        verify(asyncNotifier, never()).notifyDriverAsync(eq(7L), anyMap());
    }
}
