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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Commit 1 — haydovchi aktivlik balli: COMPLETED +1.0, "BOSHQA" bekor -0.2,
 * "MASHINA_BUZILDI"/"FAVQULODDA" 0, decline 0.
 */
@ExtendWith(MockitoExtension.class)
class ActivityScoreTest {

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

    @BeforeEach
    void setup() {
        tripService = new TripService(tripRepository, driverRepository, tariffRepository,
                transactionRepository, ratingRepository, messagingTemplate, matchingService,
                surgePricingService, Optional.empty(), pushService, promoCodeService,
                asyncNotifier, notificationHelper, securityMonitor, smsInviteService,
                new com.taxi.backend.pricing.NightFareService(0, 0, 0, java.time.Clock.systemUTC()), org.mockito.Mockito.mock(com.taxi.backend.service.ReferralService.class), org.mockito.Mockito.mock(com.taxi.backend.repository.DriverPhotoRepository.class));
        ReflectionTestUtils.setField(tripService, "commissionPercent", 10.0);
        ReflectionTestUtils.setField(tripService, "maxDriverCancellations", 3);

        driverUser = new User();
        driverUser.setId(100L);
        driver = new Driver();
        driver.setId(5L);
        driver.setBalance(0L);
        driver.setTotalTrips(0);
        driver.setActivityScore(0.0);
        lenient().when(driverRepository.findByUserId(100L)).thenReturn(Optional.of(driver));
        lenient().when(driverRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(driver));
    }

    private Trip tripWithDriver(TripStatus status) {
        Trip t = new Trip();
        t.setId(1L);
        t.setStatus(status);
        t.setSource("APP");
        t.setDriver(driver);
        t.setTotalPrice(1_000_000L);
        User p = new User();
        p.setId(10L);
        p.setName("Yo'lovchi");
        p.setPhone("+998901234567");
        t.setPassenger(p);
        return t;
    }

    @Test
    @DisplayName("Safarni yakunlash -> activity_score += 1.0")
    void completion_incrementsActivityScoreByOne() {
        Trip trip = tripWithDriver(TripStatus.STARTED);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        tripService.updateTripStatus(driverUser, 1L, TripStatus.COMPLETED);

        assertEquals(1.0, driver.getActivityScore(), 1e-9);
    }

    @Test
    @DisplayName("\"BOSHQA\" sababli bekor -> activity_score -= 0.2")
    void cancelBoshqa_decrementsByPointTwo() {
        Trip trip = tripWithDriver(TripStatus.ACCEPTED);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        tripService.cancelTripByDriver(driverUser, 1L, "BOSHQA");

        assertEquals(-0.2, driver.getActivityScore(), 1e-9);
        verify(driverRepository).save(driver);
    }

    @Test
    @DisplayName("\"MASHINA_BUZILDI\" va \"FAVQULODDA\" -> activity_score o'zgarmaydi")
    void cancelExcused_noChange() {
        Trip t1 = tripWithDriver(TripStatus.ACCEPTED);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(t1));
        tripService.cancelTripByDriver(driverUser, 1L, "MASHINA_BUZILDI");
        assertEquals(0.0, driver.getActivityScore(), 1e-9);

        Trip t2 = tripWithDriver(TripStatus.ACCEPTED);
        t2.setId(2L);
        when(tripRepository.findById(2L)).thenReturn(Optional.of(t2));
        tripService.cancelTripByDriver(driverUser, 2L, "FAVQULODDA");
        assertEquals(0.0, driver.getActivityScore(), 1e-9);
    }

    @Test
    @DisplayName("Decline (rad etish) -> activity_score o'zgarmaydi")
    void decline_noChange() {
        Trip trip = tripWithDriver(TripStatus.SEARCHING);
        trip.setDriver(null); // SEARCHING — hali biriktirilmagan
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        tripService.declineTrip(driverUser, 1L);

        assertEquals(0.0, driver.getActivityScore(), 1e-9);
    }
}
