package com.taxi.backend.service;

import com.taxi.backend.dto.OperatorTripRequest;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Tariff;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.pricing.SurgeResult;
import com.taxi.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** P3 — operator post-acceptance edit: state guards + price recompute (locked rules). */
@ExtendWith(MockitoExtension.class)
class OperatorEditTripTest {

    @Mock private TripRepository tripRepository;
    @Mock private UserRepository userRepository;
    @Mock private TariffRepository tariffRepository;
    @Mock private SurgePricingService surgePricingService;
    @Mock private TripNotificationHelper notificationHelper;
    @Mock private SecurityMonitorService securityMonitor;

    private OperatorService operatorService;
    private User operator;

    @BeforeEach
    void setup() {
        operatorService = new OperatorService(tripRepository, userRepository, tariffRepository,
                surgePricingService, notificationHelper, securityMonitor,
                new com.taxi.backend.pricing.NightFareService(0, 0, 0, java.time.Clock.systemUTC()));
        operator = new User();
        operator.setId(1L);
        operator.setPhone("+998901234567");
    }

    private Tariff tariff() {
        Tariff t = mock(Tariff.class);
        lenient().when(t.getId()).thenReturn(7L);
        lenient().when(t.getName()).thenReturn("EKONOM");
        lenient().when(t.getBasePrice()).thenReturn(5000L);
        lenient().when(t.getPricePerKm()).thenReturn(10000L);
        lenient().when(t.getMinPrice()).thenReturn(3000L);
        return t;
    }

    private Trip callTrip(long id, TripStatus status) {
        Trip trip = new Trip();
        trip.setId(id);
        trip.setSource("CALL");
        trip.setStatus(status);
        trip.setTariff(tariff());
        trip.setFromLat(41.29); trip.setFromLon(69.68);
        trip.setToLat(41.30); trip.setToLon(69.69);
        trip.setCreatedAt(LocalDateTime.now());
        trip.setExtraPrice(0L);
        return trip;
    }

    @Test
    @DisplayName("ACCEPTED edit (yangi B) -> narx SurgePricingService.calculate orqali qayta hisoblanadi")
    void editAccepted_recomputesPrice() {
        Trip trip = callTrip(100L, TripStatus.ACCEPTED);
        when(tripRepository.findById(100L)).thenReturn(Optional.of(trip));
        when(surgePricingService.calculate(anyLong(), anyDouble(), anyDouble()))
                .thenReturn(new SurgeResult(1.0, 50000L, "NORMAL", List.of()));

        OperatorTripRequest req = new OperatorTripRequest();
        req.setDestinationAddress("Yangi B");
        req.setToLat(41.305); req.setToLon(69.695);

        Map<String, Object> res = operatorService.editTrip(operator, 100L, req);

        assertEquals(50000L, trip.getBasePrice(), "base = surge.finalPriceTiyin");
        assertEquals(50000L, trip.getTotalPrice(), "total = base + 0 services");
        assertEquals(TripStatus.ACCEPTED, trip.getStatus(), "status o'zgarmaydi");
        assertEquals("Yangi B", trip.getToAddress());
        assertEquals("ACCEPTED", res.get("status"));
        verify(tripRepository).save(trip);
    }

    @Test
    @DisplayName("STARTED safarni tahrirlab bo'lmaydi")
    void editStarted_blocked() {
        Trip trip = callTrip(101L, TripStatus.STARTED);
        when(tripRepository.findById(101L)).thenReturn(Optional.of(trip));
        OperatorTripRequest req = new OperatorTripRequest();
        req.setDestinationAddress("X");
        assertThrows(RuntimeException.class, () -> operatorService.editTrip(operator, 101L, req));
        verify(tripRepository, never()).save(any());
    }

    @Test
    @DisplayName("DRIVER_ARRIVED da manzil o'zgartirish BLOKLANGAN (faqat tarif/xizmat)")
    void editDriverArrived_addressBlocked() {
        Trip trip = callTrip(102L, TripStatus.DRIVER_ARRIVED);
        when(tripRepository.findById(102L)).thenReturn(Optional.of(trip));
        OperatorTripRequest req = new OperatorTripRequest();
        req.setDestinationAddress("Yangi B");
        req.setToLat(41.31); req.setToLon(69.70);
        assertThrows(RuntimeException.class, () -> operatorService.editTrip(operator, 102L, req));
        verify(tripRepository, never()).save(any());
    }

    @Test
    @DisplayName("Terminal (COMPLETED) tripni tahrirlab bo'lmaydi")
    void editTerminal_blocked() {
        Trip trip = callTrip(103L, TripStatus.COMPLETED);
        when(tripRepository.findById(103L)).thenReturn(Optional.of(trip));
        OperatorTripRequest req = new OperatorTripRequest();
        req.setDestinationAddress("X");
        assertThrows(RuntimeException.class, () -> operatorService.editTrip(operator, 103L, req));
        verify(tripRepository, never()).save(any());
    }
}
