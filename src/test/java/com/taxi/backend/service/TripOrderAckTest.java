package com.taxi.backend.service;

import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.kafka.TripEventProducer;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.pricing.NightFareService;
import com.taxi.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Layer 2c — order "received" ACK backbone.
 *
 * markOrderReceived stamps trips.first_received_at when a NOTIFIED driver's app displays the order.
 * This is the signal that makes Layer 2b escalation precise (escalate only when NO app showed the order).
 *
 * Verifies: notified-driver acks once; idempotent on repeat; non-notified driver rejected; never throws.
 * ADDITIVE — touches no FSI/native/push code.
 */
@ExtendWith(MockitoExtension.class)
class TripOrderAckTest {

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
    @Mock private NightFareService nightFareService;
    @Mock private ReferralService referralService;
    @Mock private DriverPhotoRepository driverPhotoRepository;

    private TripService service;
    private User driverUser;
    private Driver driver;
    private Trip trip;

    @BeforeEach
    void setup() {
        service = new TripService(
                tripRepository, driverRepository, tariffRepository, transactionRepository,
                ratingRepository, messagingTemplate, matchingService, surgePricingService,
                Optional.<TripEventProducer>empty(), pushService, promoCodeService,
                asyncNotifier, notificationHelper, securityMonitor, smsInviteService,
                nightFareService, referralService, driverPhotoRepository);

        driverUser = new User();
        driverUser.setId(8L);

        driver = new Driver();
        driver.setId(5L);
        driver.setUser(driverUser);
        driver.setStatus(DriverStatus.ACTIVE);

        trip = new Trip();
        trip.setId(100L);
        trip.setStatus(TripStatus.SEARCHING);
        trip.setNotifiedDriverIds("5,6,22");   // driver 5 WAS notified
        lenient().when(notificationHelper.acknowledgeCurrentOffer(100L, 5L)).thenReturn(true);
    }

    @Test
    @DisplayName("notified driver acks -> first_received_at set, acked=true, trip saved")
    void notifiedDriver_firstAck_stampsAndSaves() {
        when(driverRepository.findByUserId(8L)).thenReturn(Optional.of(driver));
        when(tripRepository.findById(100L)).thenReturn(Optional.of(trip));

        Map<String, Object> res = service.markOrderReceived(driverUser, 100L);

        assertEquals(true, res.get("acked"));
        assertNotNull(trip.getFirstReceivedAt(), "first_received_at must be stamped");
        verify(tripRepository).save(trip);
    }

    @Test
    @DisplayName("second ack (already received) -> idempotent: no save, first_received_at unchanged")
    void secondAck_isIdempotent() {
        java.time.LocalDateTime original = java.time.LocalDateTime.now().minusSeconds(5);
        trip.setFirstReceivedAt(original);
        when(driverRepository.findByUserId(8L)).thenReturn(Optional.of(driver));
        when(tripRepository.findById(100L)).thenReturn(Optional.of(trip));

        Map<String, Object> res = service.markOrderReceived(driverUser, 100L);

        assertEquals(true, res.get("acked"));
        assertEquals(original, trip.getFirstReceivedAt(), "first ack wins — timestamp not overwritten");
        verify(tripRepository, never()).save(any());
    }

    @Test
    @DisplayName("driver NOT in notifiedDriverIds -> rejected (acked=false), nothing saved (anti-spoof)")
    void nonNotifiedDriver_rejected() {
        Driver other = new Driver();
        other.setId(99L);                       // 99 not in "5,6,22"
        User otherUser = new User();
        otherUser.setId(900L);
        other.setUser(otherUser);
        when(driverRepository.findByUserId(900L)).thenReturn(Optional.of(other));
        when(tripRepository.findById(100L)).thenReturn(Optional.of(trip));

        Map<String, Object> res = service.markOrderReceived(otherUser, 100L);

        assertEquals(false, res.get("acked"));
        assertNull(trip.getFirstReceivedAt());
        verify(tripRepository, never()).save(any());
    }

    @Test
    @DisplayName("unknown trip -> acked=false, never throws")
    void unknownTrip_gracefulFalse() {
        when(driverRepository.findByUserId(8L)).thenReturn(Optional.of(driver));
        when(tripRepository.findById(404L)).thenReturn(Optional.empty());

        Map<String, Object> res = service.markOrderReceived(driverUser, 404L);

        assertEquals(false, res.get("acked"));
        verify(tripRepository, never()).save(any());
    }

    @Test
    @DisplayName("blank notifiedDriverIds -> rejected, no save (no order was dispatched to anyone)")
    void blankNotifiedIds_rejected() {
        trip.setNotifiedDriverIds(null);
        when(notificationHelper.acknowledgeCurrentOffer(100L, 5L)).thenReturn(false);
        when(driverRepository.findByUserId(8L)).thenReturn(Optional.of(driver));
        when(tripRepository.findById(100L)).thenReturn(Optional.of(trip));

        Map<String, Object> res = service.markOrderReceived(driverUser, 100L);

        assertEquals(false, res.get("acked"));
        verify(tripRepository, never()).save(any());
    }
}
