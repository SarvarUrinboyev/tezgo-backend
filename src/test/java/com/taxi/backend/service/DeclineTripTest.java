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
 * Commit 2 — haydovchi yangi buyurtmani rad etadi (decline): excluded ga qo'shiladi,
 * status va cancel_count o'zgarmaydi (strike yo'q).
 */
@ExtendWith(MockitoExtension.class)
class DeclineTripTest {

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
        lenient().when(driverRepository.findByUserId(100L)).thenReturn(Optional.of(driver));
    }

    @Test
    @DisplayName("Decline -> haydovchi excluded ga qo'shiladi, status va cancel_count o'zgarmaydi")
    void decline_addsExclusion_withoutStatusOrStrikeChange() {
        Trip trip = new Trip();
        trip.setId(1L);
        trip.setStatus(TripStatus.SEARCHING);
        trip.setCancelCount(0);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        tripService.declineTrip(driverUser, 1L);

        assertTrue(ExcludedDriverFilter.contains(trip.getExcludedDriverIds(), 5L),
                "rad etgan haydovchi excluded_driver_ids da bo'lishi kerak");
        assertEquals(TripStatus.SEARCHING, trip.getStatus(), "status o'zgarmasligi kerak");
        assertEquals(0, trip.getCancelCount(), "cancel_count o'zgarmasligi kerak (strike yo'q)");
    }

    @Test
    @DisplayName("Decline idempotent — ikki marta rad etish bir martagina qo'shadi, cancel_count 0")
    void decline_isIdempotent() {
        Trip trip = new Trip();
        trip.setId(1L);
        trip.setStatus(TripStatus.SEARCHING);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        tripService.declineTrip(driverUser, 1L);
        tripService.declineTrip(driverUser, 1L);

        assertEquals("5", trip.getExcludedDriverIds(), "takrorlanmasligi kerak");
        assertEquals(0, trip.getCancelCount());
        assertEquals(TripStatus.SEARCHING, trip.getStatus());
    }
}
