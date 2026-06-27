package com.taxi.backend.service;

import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Scope 3a/3b — operator ACCEPTED (osilib qolgan) tripni bekor qilib haydovchini bo'shata oladi. */
@ExtendWith(MockitoExtension.class)
class OperatorCancelFreesDriverTest {

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

    @Test
    @DisplayName("Operator ACCEPTED tripni bekor qiladi -> CANCELLED_BY_ADMIN + haydovchi bo'shatiladi")
    void cancelAccepted_freesDriver() {
        Driver d = new Driver();
        d.setId(5L);
        Trip trip = new Trip();
        trip.setId(360L);
        trip.setSource("CALL");
        trip.setStatus(TripStatus.ACCEPTED);
        trip.setDriver(d);
        trip.setAcceptedAt(LocalDateTime.now());
        when(tripRepository.findById(360L)).thenReturn(Optional.of(trip));

        Map<String, Object> res = operatorService.cancelTrip(operator, 360L);

        assertEquals(TripStatus.CANCELLED_BY_ADMIN, trip.getStatus());
        assertNull(trip.getDriver(), "haydovchi bo'shatilishi kerak (busy-set'dan chiqadi)");
        assertNull(trip.getAcceptedAt());
        assertEquals("CANCELLED_BY_ADMIN", res.get("status"));
    }

    @Test
    @DisplayName("Terminal (COMPLETED) tripni operator qayta bekor qila olmaydi")
    void cancelTerminal_rejected() {
        Trip trip = new Trip();
        trip.setId(1L);
        trip.setSource("CALL");
        trip.setStatus(TripStatus.COMPLETED);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        assertThrows(RuntimeException.class, () -> operatorService.cancelTrip(operator, 1L));
    }

    @Test
    @DisplayName("P4: Cancel — jamlangan kutish haqi KECHIRILADI (charged=0), haydovchi bo'shatiladi")
    void cancel_waivesAccruedWaiting() {
        Driver d = new Driver();
        d.setId(8L);
        Trip trip = new Trip();
        trip.setId(400L);
        trip.setSource("CALL");
        trip.setStatus(TripStatus.DRIVER_ARRIVED);
        trip.setDriver(d);
        trip.setAcceptedAt(LocalDateTime.now());
        trip.setWaitingPrice(90000L);     // 900 so'm jamlangan kutish
        when(tripRepository.findById(400L)).thenReturn(Optional.of(trip));

        Map<String, Object> res = operatorService.cancelTrip(operator, 400L);

        assertEquals(TripStatus.CANCELLED_BY_ADMIN, trip.getStatus());
        assertNull(trip.getDriver(), "haydovchi bo'shatildi (jazo yo'q)");
        assertEquals(900L, res.get("waivedWaiting"), "kutish haqi so'mda kechirildi (charged=0)");
    }
}
