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
 * Commit 1 — haydovchi bekor qilganda tripni boshqa haydovchilarga qayta yuborish.
 * Sof Mockito unit testlari (Spring context / DB kerak emas).
 */
@ExtendWith(MockitoExtension.class)
class DriverCancelRedispatchTest {

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
        ReflectionTestUtils.setField(tripService, "maxDriverCancellations", 3);
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

    private Trip acceptedTrip() {
        Trip t = new Trip();
        t.setId(1L);
        t.setStatus(TripStatus.ACCEPTED);
        t.setSource("APP");
        t.setDriver(driver);
        t.setAcceptedAt(java.time.LocalDateTime.now());
        t.setBroadcastAt(java.time.LocalDateTime.now());
        User p = new User();
        p.setId(10L);
        p.setName("Yo'lovchi");
        p.setPhone("+998901234567");
        t.setPassenger(p);
        return t;
    }

    @Test
    @DisplayName("ACCEPTED tripni bekor qilish -> SEARCHING, cancel_count=1, haydovchi chiqariladi, qayta matching")
    void driverCancel_redispatchesToOtherDrivers() {
        Trip trip = acceptedTrip();
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        Map<String, Object> res = tripService.cancelTripByDriver(driverUser, 1L, null);

        assertEquals(TripStatus.SEARCHING, trip.getStatus(), "trip qayta SEARCHING bo'lishi kerak");
        assertEquals(1, trip.getCancelCount());
        assertTrue(ExcludedDriverFilter.contains(trip.getExcludedDriverIds(), 5L),
                "bekor qilgan haydovchi excluded_driver_ids da bo'lishi kerak");
        assertNull(trip.getDriver(), "haydovchi biriktirilishi tozalanishi kerak");
        assertNull(trip.getAcceptedAt());
        assertNull(trip.getBroadcastAt(), "broadcast_at NULL — scheduler yangi sifatida olishi uchun");
        assertEquals("SEARCHING", res.get("status"));
        assertEquals(Boolean.TRUE, res.get("redispatched"));
        verify(notificationHelper).notifyNearbyDrivers(trip);
        verify(pushService, never()).notifyPassenger(anyLong(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("3-marta bekor qilish -> trip CANCELLED_BY_DRIVER, reason=DRIVER_CANCELLED_LIMIT, qayta yuborilmaydi")
    void thirdCancellation_finalizesTrip() {
        Trip trip = acceptedTrip();
        trip.setCancelCount(2); // allaqachon 2 marta bekor qilingan
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        Map<String, Object> res = tripService.cancelTripByDriver(driverUser, 1L, null);

        assertEquals(TripStatus.CANCELLED_BY_DRIVER, trip.getStatus());
        assertEquals(3, trip.getCancelCount());
        assertEquals("DRIVER_CANCELLED_LIMIT", trip.getCancelReason());
        assertEquals("CANCELLED_BY_DRIVER", res.get("status"));
        assertEquals("DRIVER_CANCELLED_LIMIT", res.get("reason"));
        verify(notificationHelper, never()).notifyNearbyDrivers(any());
        verify(pushService).notifyPassenger(eq(10L), anyString(), eq("Haydovchi topilmadi"), anyMap());
    }

    @Test
    @DisplayName("STARTED safarni bekor qilib bo'lmaydi")
    void cancelAfterStarted_isRejected() {
        Trip trip = acceptedTrip();
        trip.setStatus(TripStatus.STARTED);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tripService.cancelTripByDriver(driverUser, 1L, null));
        assertTrue(ex.getMessage().contains("Safar boshlangan"));
        verify(notificationHelper, never()).notifyNearbyDrivers(any());
    }

    @Test
    @DisplayName("Chiqarilgan haydovchi qayta yuborilgan tripni ACCEPT qila olmaydi")
    void excludedDriverCannotAcceptRedispatchedTrip() {
        Trip trip = new Trip();
        trip.setId(2L);
        trip.setStatus(TripStatus.SEARCHING);
        trip.setExcludedDriverIds("5"); // 5-haydovchi chiqarilgan
        when(tripRepository.findById(2L)).thenReturn(Optional.of(trip));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tripService.acceptTrip(driverUser, 2L));
        assertTrue(ex.getMessage().toLowerCase().contains("bekor"));
        assertNull(trip.getDriver(), "trip hali ham bo'sh bo'lishi kerak");
        assertEquals(TripStatus.SEARCHING, trip.getStatus());
    }
}
