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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Commit 1 — faol tripi bor haydovchi yangi buyurtma olmaydi (TripService gate'lari).
 */
@ExtendWith(MockitoExtension.class)
class BusyDriverGuardTest {

    @Mock private TripRepository tripRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private TariffRepository tariffRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private RatingRepository ratingRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private MatchingService matchingService;
    @Mock private SurgePricingService surgePricingService;
    @Mock private PushNotificationService pushService;
    @Mock private PromoCodeService promoCodeService;
    @Mock private AsyncNotificationService asyncNotifier;
    @Mock private TripNotificationHelper notificationHelper;
    @Mock private SecurityMonitorService securityMonitor;
    @Mock private SmsInviteService smsInviteService;

    private TripService tripService;
    private User driverUser;
    private Driver driver;

    private static final List<TripStatus> ACTIVE = TripStatus.ACTIVE_DRIVER_STATUSES;

    @BeforeEach
    void setup() {
        tripService = new TripService(tripRepository, driverRepository, tariffRepository,
                transactionRepository, ratingRepository, messagingTemplate, matchingService,
                surgePricingService, Optional.empty(), pushService, promoCodeService,
                asyncNotifier, notificationHelper, securityMonitor, smsInviteService,
                new com.taxi.backend.pricing.NightFareService(0, 0, 0, java.time.Clock.systemUTC()), org.mockito.Mockito.mock(com.taxi.backend.service.ReferralService.class), org.mockito.Mockito.mock(com.taxi.backend.repository.DriverPhotoRepository.class));
        ReflectionTestUtils.setField(tripService, "commissionPercent", 10.0);
        ReflectionTestUtils.setField(tripService, "matchingRadiusKm", 5.0);

        driverUser = new User();
        driverUser.setId(100L);
        driverUser.setName("Test Haydovchi");
        driverUser.setPhone("+998901112233");
        driver = new Driver();
        driver.setId(5L);
        driver.setBalance(0L);
        driver.setOnline(true);
        driver.setCarModel("Cobalt");
        driver.setCarNumber("01A777AA");
        lenient().when(driverRepository.findByUserId(100L)).thenReturn(Optional.of(driver));
    }

    @Test
    @DisplayName("Faol (STARTED) tripi bor haydovchi ikkinchi buyurtmani qabul qila olmaydi")
    void acceptTrip_whenDriverBusy_rejected() {
        when(tripRepository.existsByDriverIdAndStatusIn(5L, ACTIVE)).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tripService.acceptTrip(driverUser, 2L));
        assertTrue(ex.getMessage().contains("Sizda faol buyurtma bor"));
        verify(tripRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("Bo'sh haydovchi (faol trip yo'q) buyurtmani qabul qila oladi — complete/cancel dan keyin ham")
    void acceptTrip_whenDriverFree_succeeds() {
        when(tripRepository.existsByDriverIdAndStatusIn(5L, ACTIVE)).thenReturn(false);
        Trip trip = new Trip();
        trip.setId(2L);
        trip.setStatus(TripStatus.SEARCHING);
        User p = new User();
        p.setId(10L);
        trip.setPassenger(p);
        when(tripRepository.findById(2L)).thenReturn(Optional.of(trip));

        Map<String, Object> res = tripService.acceptTrip(driverUser, 2L);

        assertEquals(TripStatus.ACCEPTED, trip.getStatus());
        assertEquals(5L, trip.getDriver().getId());
        assertEquals("ACCEPTED", res.get("status"));
    }

    @Test
    @DisplayName("Faol tripi bor haydovchiga mavjud buyurtmalar ro'yxati bo'sh qaytadi")
    void getAvailableTrips_whenBusy_returnsEmpty() {
        when(tripRepository.existsByDriverIdAndStatusIn(5L, ACTIVE)).thenReturn(true);

        List<Map<String, Object>> res = tripService.getAvailableTrips(driverUser);

        assertTrue(res.isEmpty());
        verify(tripRepository, never()).findByStatusWithRelations(any());
    }
}
