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

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Status-transition guard — updateTripStatus faqat OLDINGA o'tishga ruxsat beradi va terminal
 * (COMPLETED/CANCELLED) tripni qayta o'zgartirishni rad etadi. Bu re-completion va dublikat
 * komissiyani to'xtatadi (#179 da 3 ta komissiya qatori sababi).
 */
@ExtendWith(MockitoExtension.class)
class StatusTransitionGuardTest {

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

        driverUser = new User();
        driverUser.setId(100L);
        driver = new Driver();
        driver.setId(5L);
        driver.setBalance(200_000L);
        lenient().when(driverRepository.findByUserId(100L)).thenReturn(Optional.of(driver));
        lenient().when(driverRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(driver));
    }

    private Trip tripWith(TripStatus status) {
        Trip t = new Trip();
        t.setId(1L);
        t.setStatus(status);
        t.setSource("APP");
        t.setTotalPrice(100_000L);
        t.setDriver(driver);
        User p = new User();
        p.setId(10L);
        p.setPhone("+998901112233");
        t.setPassenger(p);
        return t;
    }

    // ── Legitimate ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("STARTED tripni bir marta yakunlash -> 1 komissiya, COMPLETED")
    void completeStartedOnce_oneCommission() {
        Trip trip = tripWith(TripStatus.STARTED);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        Map<String, Object> res = tripService.updateTripStatus(driverUser, 1L, TripStatus.COMPLETED);

        assertEquals("COMPLETED", res.get("status"));
        assertEquals(TripStatus.COMPLETED, trip.getStatus());
        verify(driverRepository, times(1)).findByIdForUpdate(5L);
    }

    @Test
    @DisplayName("To'liq oldinga oqim ACCEPTED->ARRIVED->STARTED->COMPLETED -> ishlaydi, 1 komissiya")
    void forwardFlow_oneCommission() {
        Trip trip = tripWith(TripStatus.ACCEPTED);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        tripService.updateTripStatus(driverUser, 1L, TripStatus.DRIVER_ARRIVED);
        tripService.updateTripStatus(driverUser, 1L, TripStatus.STARTED);
        Map<String, Object> res = tripService.updateTripStatus(driverUser, 1L, TripStatus.COMPLETED);

        assertEquals("COMPLETED", res.get("status"));
        verify(driverRepository, times(1)).findByIdForUpdate(5L);
    }

    // ── Re-completion / terminal rad etish ─────────────────────────────────────

    @Test
    @DisplayName("COMPLETED tripni qayta yakunlash -> rad (allaqachon yakunlangan), yangi komissiya yo'q")
    void completeAgainOnCompleted_rejected_noNewCommission() {
        Trip trip = tripWith(TripStatus.COMPLETED);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tripService.updateTripStatus(driverUser, 1L, TripStatus.COMPLETED));
        assertTrue(ex.getMessage().contains("allaqachon yakunlangan"));
        assertEquals(TripStatus.COMPLETED, trip.getStatus());
        verify(driverRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("COMPLETED tripda pickup (Yetib keldim) -> rad, komissiya yo'q")
    void pickupOnCompleted_rejected() {
        Trip trip = tripWith(TripStatus.COMPLETED);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tripService.updateTripStatus(driverUser, 1L, TripStatus.DRIVER_ARRIVED));
        assertTrue(ex.getMessage().contains("allaqachon yakunlangan"));
        assertEquals(TripStatus.COMPLETED, trip.getStatus());
        verify(driverRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("COMPLETED tripda start -> rad")
    void startOnCompleted_rejected() {
        Trip trip = tripWith(TripStatus.COMPLETED);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tripService.updateTripStatus(driverUser, 1L, TripStatus.STARTED));
        assertTrue(ex.getMessage().contains("allaqachon yakunlangan"));
        verify(driverRepository, never()).findByIdForUpdate(anyLong());
    }

    // ── Teskari (backward) o'tish rad etish ────────────────────────────────────

    @Test
    @DisplayName("Teskari o'tish STARTED->DRIVER_ARRIVED -> rad (bu tartibda o'zgartirib bo'lmaydi)")
    void backwardStartedToArrived_rejected() {
        Trip trip = tripWith(TripStatus.STARTED);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tripService.updateTripStatus(driverUser, 1L, TripStatus.DRIVER_ARRIVED));
        assertTrue(ex.getMessage().contains("tartibda o'zgartirib bo'lmaydi"));
        assertEquals(TripStatus.STARTED, trip.getStatus());
        verify(driverRepository, never()).findByIdForUpdate(anyLong());
    }
}
