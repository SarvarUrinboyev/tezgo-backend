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
 * Commit-safe status transitions — side-effect (WS/push/SMS/Kafka) xatosi DB commit'ni
 * (status, balans) ROLLBACK qila olmaydi. Yakunlash 200 + COMPLETED qaytaradi hatto barcha
 * side-effect ishlamasa ham. totalPrice/balans null bo'lsa NPE bo'lmaydi.
 */
@ExtendWith(MockitoExtension.class)
class CommitSafeStatusTest {

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
                new com.taxi.backend.pricing.NightFareService(0, 0, 0, java.time.Clock.systemUTC()), org.mockito.Mockito.mock(com.taxi.backend.service.ReferralService.class));
        ReflectionTestUtils.setField(tripService, "commissionPercent", 10.0);

        driverUser = new User();
        driverUser.setId(100L);
        driver = new Driver();
        driver.setId(5L);
        driver.setOnline(true);
        driver.setBalance(200_000L);
        when(driverRepository.findByUserId(100L)).thenReturn(Optional.of(driver));
    }

    private Trip startedTrip(Long totalPrice, String source) {
        Trip t = new Trip();
        t.setId(2L);
        t.setStatus(TripStatus.STARTED);
        t.setSource(source);
        t.setTotalPrice(totalPrice);
        t.setDriver(driver);
        User p = new User();
        p.setId(10L);
        p.setPhone("+998901112233");
        t.setPassenger(p);
        return t;
    }

    // ── Commit-safe: side-effect xatosi rollback qilmaydi ──────────────────────

    @Test
    @DisplayName("complete + push xato beradi -> baribir COMPLETED commit, 200 (map) qaytadi, balans yechiladi")
    void complete_pushThrows_stillCommitsCompleted() {
        Trip trip = startedTrip(100_000L, "APP");
        when(tripRepository.findById(2L)).thenReturn(Optional.of(trip));
        doThrow(new RuntimeException("redis/push down"))
                .when(pushService).notifyPassenger(anyLong(), anyString(), anyString(), anyMap());

        Map<String, Object> res = assertDoesNotThrow(
                () -> tripService.updateTripStatus(driverUser, 2L, TripStatus.COMPLETED));

        assertEquals("COMPLETED", res.get("status"));
        assertEquals(TripStatus.COMPLETED, trip.getStatus());
        verify(driverRepository).addToBalance(5L, -10_000L); // 10% komissiya saqlanadi
    }

    @Test
    @DisplayName("complete + WS broadcast xato beradi -> baribir COMPLETED commit")
    void complete_broadcastThrows_stillCommitsCompleted() {
        Trip trip = startedTrip(100_000L, "APP");
        when(tripRepository.findById(2L)).thenReturn(Optional.of(trip));
        doThrow(new RuntimeException("ws down"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        Map<String, Object> res = assertDoesNotThrow(
                () -> tripService.updateTripStatus(driverUser, 2L, TripStatus.COMPLETED));

        assertEquals("COMPLETED", res.get("status"));
        assertEquals(TripStatus.COMPLETED, trip.getStatus());
    }

    // ── Null-guard: totalPrice null -> NPE yo'q, komissiya 0 ───────────────────

    @Test
    @DisplayName("complete + totalPrice null -> NPE yo'q, komissiya 0, COMPLETED")
    void complete_nullTotalPrice_noNpe_commissionZero() {
        Trip trip = startedTrip(null, "APP");
        when(tripRepository.findById(2L)).thenReturn(Optional.of(trip));

        Map<String, Object> res = assertDoesNotThrow(
                () -> tripService.updateTripStatus(driverUser, 2L, TripStatus.COMPLETED));

        assertEquals("COMPLETED", res.get("status"));
        assertEquals(TripStatus.COMPLETED, trip.getStatus());
        verify(driverRepository).addToBalance(5L, 0L); // komissiya 0 dan
    }

    // ── Happy-path: o'zgarmagan xulq, to'g'ri komissiya ────────────────────────

    @Test
    @DisplayName("complete normal (barcha side-effect ok) -> COMPLETED, komissiya 10% = 10 000 tiyin")
    void complete_normal_correctCommission() {
        Trip trip = startedTrip(100_000L, "APP");
        when(tripRepository.findById(2L)).thenReturn(Optional.of(trip));

        Map<String, Object> res = tripService.updateTripStatus(driverUser, 2L, TripStatus.COMPLETED);

        assertEquals("COMPLETED", res.get("status"));
        verify(driverRepository).addToBalance(5L, -10_000L);
        verify(pushService).notifyPassenger(eq(10L), anyString(), anyString(), anyMap());
    }

    // ── ARRIVED/STARTED ham commit-safe ───────────────────────────────────────

    @Test
    @DisplayName("DRIVER_ARRIVED + push xato -> status baribir o'zgaradi, 500 yo'q")
    void arrived_pushThrows_statusStillChanges() {
        Trip trip = startedTrip(100_000L, "APP");
        trip.setStatus(TripStatus.ACCEPTED);
        when(tripRepository.findById(2L)).thenReturn(Optional.of(trip));
        doThrow(new RuntimeException("push down"))
                .when(pushService).notifyPassenger(anyLong(), anyString(), anyString(), anyMap());

        Map<String, Object> res = assertDoesNotThrow(
                () -> tripService.updateTripStatus(driverUser, 2L, TripStatus.DRIVER_ARRIVED));

        assertEquals("DRIVER_ARRIVED", res.get("status"));
        assertEquals(TripStatus.DRIVER_ARRIVED, trip.getStatus());
    }

    @Test
    @DisplayName("STARTED + WS broadcast xato -> status baribir o'zgaradi, 500 yo'q")
    void started_broadcastThrows_statusStillChanges() {
        Trip trip = startedTrip(100_000L, "APP");
        trip.setStatus(TripStatus.DRIVER_ARRIVED);
        when(tripRepository.findById(2L)).thenReturn(Optional.of(trip));
        doThrow(new RuntimeException("ws down"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        Map<String, Object> res = assertDoesNotThrow(
                () -> tripService.updateTripStatus(driverUser, 2L, TripStatus.STARTED));

        assertEquals("STARTED", res.get("status"));
        assertEquals(TripStatus.STARTED, trip.getStatus());
    }
}
