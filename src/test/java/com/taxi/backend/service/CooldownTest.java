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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Commit 2 — rad etish cooldown'i (60s): set on decline va barcha gate'larda taqiqlanadi.
 */
@ExtendWith(MockitoExtension.class)
class CooldownTest {

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
                new com.taxi.backend.pricing.NightFareService(0, 0, 0, java.time.Clock.systemUTC()), org.mockito.Mockito.mock(com.taxi.backend.service.ReferralService.class));
        ReflectionTestUtils.setField(tripService, "commissionPercent", 10.0);
        ReflectionTestUtils.setField(tripService, "declineCooldownSeconds", 60L);

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
    @DisplayName("Decline -> order_cooldown_until ~ now+60s o'rnatiladi")
    void decline_setsCooldown() {
        Trip trip = new Trip();
        trip.setId(1L);
        trip.setStatus(TripStatus.SEARCHING);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        tripService.declineTrip(driverUser, 1L);

        assertNotNull(driver.getOrderCooldownUntil());
        assertTrue(driver.isInCooldown(), "rad etgandan keyin cooldown faol bo'lishi kerak");
        assertTrue(driver.getOrderCooldownUntil().isAfter(LocalDateTime.now().plusSeconds(55)));
    }

    @Test
    @DisplayName("Cooldown'dagi haydovchi qabul qila olmaydi")
    void acceptInCooldown_rejected() {
        driver.setOrderCooldownUntil(LocalDateTime.now().plusSeconds(60));
        when(tripRepository.existsByDriverIdAndStatusIn(5L, ACTIVE)).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tripService.acceptTrip(driverUser, 2L));
        assertTrue(ex.getMessage().contains("kuting"));
        verify(tripRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("Cooldown tugagach (60s o'tgach) haydovchi yana qabul qila oladi")
    void acceptAfterCooldownExpired_eligible() {
        driver.setOrderCooldownUntil(LocalDateTime.now().minusSeconds(10)); // o'tib ketgan
        when(tripRepository.existsByDriverIdAndStatusIn(5L, ACTIVE)).thenReturn(false);
        Trip trip = new Trip();
        trip.setId(2L);
        trip.setStatus(TripStatus.SEARCHING);
        User p = new User();
        p.setId(10L);
        trip.setPassenger(p);
        when(tripRepository.findById(2L)).thenReturn(Optional.of(trip));

        Map<String, Object> res = tripService.acceptTrip(driverUser, 2L);

        assertEquals("ACCEPTED", res.get("status"));
        assertEquals(TripStatus.ACCEPTED, trip.getStatus());
    }

    @Test
    @DisplayName("Cooldown'dagi haydovchiga mavjud buyurtmalar ro'yxati bo'sh")
    void getAvailableTrips_inCooldown_empty() {
        driver.setOrderCooldownUntil(LocalDateTime.now().plusSeconds(60));

        List<Map<String, Object>> res = tripService.getAvailableTrips(driverUser);

        assertTrue(res.isEmpty());
        verify(tripRepository, never()).findByStatusWithRelations(any());
    }
}
