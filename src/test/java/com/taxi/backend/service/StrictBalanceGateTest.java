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
import static org.mockito.Mockito.*;

/**
 * Fix — STRICT balans >= 0 gate (TripService): manfiy balansda buyurtma berilmaydi/olinmaydi,
 * balans == 0 — ruxsat. Eski -5,000,000 tiyin tolerantligi olib tashlandi.
 */
@ExtendWith(MockitoExtension.class)
class StrictBalanceGateTest {

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
        ReflectionTestUtils.setField(tripService, "matchingRadiusKm", 5.0);

        driverUser = new User();
        driverUser.setId(100L);
        driverUser.setName("Test Haydovchi");
        driverUser.setPhone("+998901112233");
        driver = new Driver();
        driver.setId(5L);
        driver.setOnline(true);
        driver.setCarModel("Cobalt");
        driver.setCarNumber("01A777AA");
        lenient().when(driverRepository.findByUserId(100L)).thenReturn(Optional.of(driver));
    }

    // ── getAvailableTrips ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getAvailableTrips: balans -1 tiyin -> bo'sh ro'yxat (manfiy)")
    void getAvailableTrips_negativeBalance_empty() {
        driver.setBalance(-1L);
        List<Map<String, Object>> res = tripService.getAvailableTrips(driverUser);
        assertTrue(res.isEmpty());
        verify(tripRepository, never()).findByStatusWithRelations(any());
    }

    @Test
    @DisplayName("getAvailableTrips: balans 0 -> buyurtmalar ko'rinadi (eligible)")
    void getAvailableTrips_zeroBalance_returnsOrders() {
        driver.setBalance(0L);
        when(tripRepository.existsByDriverIdAndStatusIn(5L, ACTIVE)).thenReturn(false);
        Trip t = new Trip();
        t.setId(1L);
        t.setStatus(TripStatus.SEARCHING);
        t.setSource("APP");
        t.setTotalPrice(100_000L);
        when(tripRepository.findByStatusWithRelations(TripStatus.SEARCHING)).thenReturn(List.of(t));

        List<Map<String, Object>> res = tripService.getAvailableTrips(driverUser);

        assertEquals(1, res.size(), "balans 0 — buyurtma ko'rinishi kerak");
    }

    // ── acceptTrip ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("acceptTrip: balans -1 -> rad etiladi (toza biznes xatosi, NPE/500 emas)")
    void acceptTrip_negativeBalance_rejected() {
        driver.setBalance(-1L);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tripService.acceptTrip(driverUser, 2L));
        assertFalse(ex instanceof NullPointerException);
        assertTrue(ex.getMessage().contains("manfiy"));
        verify(tripRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("acceptTrip: balans 0 -> ruxsat (ACCEPTED)")
    void acceptTrip_zeroBalance_allowed() {
        driver.setBalance(0L);
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
}
