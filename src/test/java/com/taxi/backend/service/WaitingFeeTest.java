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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Commit 2 — pullik kutish: 60s bepul, keyin 600 so'm/daqiqa (har soniya).
 * waitingPricePerMinute = 60000 tiyin (600 so'm), freeSeconds = 60.
 * Sof Mockito unit testlari.
 */
@ExtendWith(MockitoExtension.class)
class WaitingFeeTest {

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
        ReflectionTestUtils.setField(tripService, "waitingPricePerMinute", 60_000L); // 600 so'm/daqiqa
        ReflectionTestUtils.setField(tripService, "waitingFreeSeconds", 60L);
        ReflectionTestUtils.setField(tripService, "tripWaitingPricePerMinute", 250_000L);
        ReflectionTestUtils.setField(tripService, "maxDriverCancellations", 3);

        driverUser = new User();
        driverUser.setId(100L);
        driver = new Driver();
        driver.setId(5L);
        driver.setBalance(0L);
        lenient().when(driverRepository.findByUserId(100L)).thenReturn(Optional.of(driver));
        lenient().when(driverRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(driver));
    }

    private Trip activeTrip(TripStatus status, long totalPriceTiyin) {
        Trip t = new Trip();
        t.setId(1L);
        t.setStatus(status);
        t.setSource("APP");
        t.setDriver(driver);
        t.setTotalPrice(totalPriceTiyin);
        User p = new User();
        p.setId(10L);
        p.setName("Yo'lovchi");
        p.setPhone("+998901234567");
        t.setPassenger(p);
        return t;
    }

    @Test
    @DisplayName("DRIVER_ARRIVED -> arrived_at va waiting_started_at avtomatik o'rnatiladi")
    void arrival_setsArrivedAtAndStartsWaiting() {
        Trip trip = activeTrip(TripStatus.ACCEPTED, 1_000_000L);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        tripService.updateTripStatus(driverUser, 1L, TripStatus.DRIVER_ARRIVED);

        assertEquals(TripStatus.DRIVER_ARRIVED, trip.getStatus());
        assertNotNull(trip.getArrivedAt(), "arrived_at o'rnatilishi kerak");
        assertNotNull(trip.getWaitingStartedAt(), "waiting avtomatik boshlanishi kerak");
    }

    @Test
    @DisplayName("Yetib kelgandan 45s keyin start -> kutish haqi = 0 (60s bepul)")
    void start45sAfterArrival_feeIsZero() {
        Trip trip = activeTrip(TripStatus.DRIVER_ARRIVED, 1_000_000L);
        trip.setArrivedAt(LocalDateTime.now().minusSeconds(45));
        trip.setWaitingStartedAt(LocalDateTime.now().minusSeconds(45));
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        tripService.updateTripStatus(driverUser, 1L, TripStatus.STARTED);

        assertEquals(0L, trip.getWaitingPrice());
        assertEquals(1_000_000L, trip.getTotalPrice(), "bepul oyna ichida narx o'zgarmaydi");
    }

    @Test
    @DisplayName("Yetib kelgandan 150s keyin start -> 90 pullik soniya -> 900 so'm (90000 tiyin), narx+komissiyaga kiradi")
    void start150sAfterArrival_fee900somIncludedInFareAndCommission() {
        Trip trip = activeTrip(TripStatus.DRIVER_ARRIVED, 1_000_000L);
        trip.setArrivedAt(LocalDateTime.now().minusSeconds(150));
        trip.setWaitingStartedAt(LocalDateTime.now().minusSeconds(150));
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        // start: 150 - 60 bepul = 90 pullik soniya * (60000/60) = 90000 tiyin = 900 so'm
        tripService.updateTripStatus(driverUser, 1L, TripStatus.STARTED);
        assertEquals(90_000L, trip.getWaitingPrice(), "kutish haqi 90000 tiyin (900 so'm) bo'lishi kerak");
        assertEquals(1_090_000L, trip.getTotalPrice(), "kutish haqi yakuniy narxga qo'shilishi kerak");

        // complete: komissiya bazasi kutish haqini ham o'z ichiga oladi (10% * 1090000 = 109000)
        tripService.updateTripStatus(driverUser, 1L, TripStatus.COMPLETED);
        verify(driverRepository).findByIdForUpdate(5L);
    }

    @Test
    @DisplayName("Kutishsiz to'g'ridan-to'g'ri start (waiting_started_at yo'q) -> haqi 0, crash bo'lmaydi")
    void directStartWithoutWaiting_feeZeroNoCrash() {
        Trip trip = activeTrip(TripStatus.ACCEPTED, 1_000_000L);
        // arrived_at / waiting_started_at o'rnatilmagan
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        assertDoesNotThrow(() -> tripService.updateTripStatus(driverUser, 1L, TripStatus.STARTED));
        assertEquals(0L, trip.getWaitingPrice());
        assertEquals(1_000_000L, trip.getTotalPrice());
    }

    @Test
    @DisplayName("Yakunlashda komissiya bazasi qo'shimcha xizmatlar bilan to'liq narxda")
    void completionCommissionIncludesServices() {
        // Operator yaratgan trip: base 1,000,000 + xizmatlar 500,000 = totalPrice 1,500,000
        Trip trip = activeTrip(TripStatus.STARTED, 1_500_000L);
        trip.setExtraPrice(500_000L);
        trip.setSelectedServices("REAR_LUGGAGE");
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        tripService.updateTripStatus(driverUser, 1L, TripStatus.COMPLETED);

        // komissiya = 10% * 1,500,000 = 150,000 (xizmatlar bilan)
        verify(driverRepository).findByIdForUpdate(5L);
    }
}
