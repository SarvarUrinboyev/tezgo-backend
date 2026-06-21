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

import org.junit.jupiter.api.function.Executable;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Fix — qayta tayinlangan/bekor qilingan (driver=null) trip ustida arrive/start/complete va
 * kutish chaqiruvlari NPE/500 emas, balki toza biznes xatosi qaytaradi. O'z tripi ishlaydi.
 */
@ExtendWith(MockitoExtension.class)
class TripOwnershipGuardTest {

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

    private static final String NOT_YOURS = "Bu siz qabul qilgan buyurtma emas";

    @BeforeEach
    void setup() {
        tripService = new TripService(tripRepository, driverRepository, tariffRepository,
                transactionRepository, ratingRepository, messagingTemplate, matchingService,
                surgePricingService, Optional.empty(), pushService, promoCodeService,
                asyncNotifier, notificationHelper, securityMonitor, smsInviteService,
                new com.taxi.backend.pricing.NightFareService(0, 0, 0, java.time.Clock.systemUTC()), org.mockito.Mockito.mock(com.taxi.backend.service.ReferralService.class));
        ReflectionTestUtils.setField(tripService, "commissionPercent", 10.0);
        ReflectionTestUtils.setField(tripService, "waitingPricePerMinute", 60_000L);
        ReflectionTestUtils.setField(tripService, "waitingFreeSeconds", 60L);

        driverUser = new User();
        driverUser.setId(100L);
        driver = new Driver();
        driver.setId(5L);
        driver.setBalance(0L);
        lenient().when(driverRepository.findByUserId(100L)).thenReturn(Optional.of(driver));
    }

    /** Qayta dispatch qilingan trip: driver=null (clearDriverAssignment dan keyingi holat). */
    private Trip driverNullTrip(TripStatus status) {
        Trip t = new Trip();
        t.setId(1L);
        t.setStatus(status);
        t.setSource("APP");
        t.setDriver(null);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(t));
        return t;
    }

    /** driver=null tripda chaqiruv -> NPE EMAS, balki "Bu siz qabul qilgan buyurtma emas". */
    private void assertNotYoursNotNpe(Executable call) {
        RuntimeException ex = assertThrows(RuntimeException.class, call);
        assertFalse(ex instanceof NullPointerException, "NPE bo'lmasligi kerak (toza biznes xatosi)");
        assertEquals(NOT_YOURS, ex.getMessage());
    }

    @Test
    @DisplayName("Arrive (DRIVER_ARRIVED) driver=null tripda -> biznes xatosi, NPE/500 emas")
    void arrive_onDriverNullTrip_throwsBusinessError() {
        driverNullTrip(TripStatus.SEARCHING);
        assertNotYoursNotNpe(() -> tripService.updateTripStatus(driverUser, 1L, TripStatus.DRIVER_ARRIVED));
    }

    @Test
    @DisplayName("Start (STARTED) driver=null tripda -> biznes xatosi")
    void start_onDriverNullTrip_throwsBusinessError() {
        driverNullTrip(TripStatus.SEARCHING);
        assertNotYoursNotNpe(() -> tripService.updateTripStatus(driverUser, 1L, TripStatus.STARTED));
    }

    @Test
    @DisplayName("Complete (COMPLETED) driver=null tripda -> biznes xatosi")
    void complete_onDriverNullTrip_throwsBusinessError() {
        driverNullTrip(TripStatus.SEARCHING);
        assertNotYoursNotNpe(() -> tripService.updateTripStatus(driverUser, 1L, TripStatus.COMPLETED));
    }

    @Test
    @DisplayName("startWaiting / startTripWaiting / stopTripWaiting driver=null tripda -> biznes xatosi")
    void waitingCalls_onDriverNullTrip_throwBusinessError() {
        driverNullTrip(TripStatus.DRIVER_ARRIVED);
        assertNotYoursNotNpe(() -> tripService.startWaiting(driverUser, 1L));
        assertNotYoursNotNpe(() -> tripService.startTripWaiting(driverUser, 1L));
        assertNotYoursNotNpe(() -> tripService.stopTripWaiting(driverUser, 1L));
    }

    @Test
    @DisplayName("O'z ACCEPTED tripida arrive normal ishlaydi -> DRIVER_ARRIVED")
    void arrive_onOwnAcceptedTrip_succeeds() {
        Trip trip = new Trip();
        trip.setId(2L);
        trip.setStatus(TripStatus.ACCEPTED);
        trip.setSource("APP");
        trip.setDriver(driver);
        User p = new User();
        p.setId(10L);
        trip.setPassenger(p);
        when(tripRepository.findById(2L)).thenReturn(Optional.of(trip));

        Map<String, Object> res = tripService.updateTripStatus(driverUser, 2L, TripStatus.DRIVER_ARRIVED);

        assertEquals("DRIVER_ARRIVED", res.get("status"));
        assertEquals(TripStatus.DRIVER_ARRIVED, trip.getStatus());
        assertNotNull(trip.getArrivedAt());
    }
}
