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
    @DisplayName("Cooldown'dagi haydovchi yo'lovchi (APP) buyurtmani qabul qila olmaydi")
    void acceptInCooldown_passengerOrder_rejected() {
        driver.setOrderCooldownUntil(LocalDateTime.now().plusSeconds(60));
        when(tripRepository.existsByDriverIdAndStatusIn(5L, ACTIVE)).thenReturn(false);
        Trip trip = new Trip();
        trip.setId(2L);
        trip.setStatus(TripStatus.SEARCHING);
        trip.setSource("APP");
        when(tripRepository.findById(2L)).thenReturn(Optional.of(trip));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tripService.acceptTrip(driverUser, 2L));
        assertTrue(ex.getMessage().contains("kuting"));
        assertEquals(TripStatus.SEARCHING, trip.getStatus(), "rad etilgan — status o'zgarmaydi");
    }

    @Test
    @DisplayName("Cooldown'da ham operator (CALL) buyurtmani QABUL qila oladi — list/matching bilan bir xil")
    void acceptInCooldown_operatorOrder_allowed() {
        driver.setOrderCooldownUntil(LocalDateTime.now().plusSeconds(60));
        when(tripRepository.existsByDriverIdAndStatusIn(5L, ACTIVE)).thenReturn(false);
        Trip trip = new Trip();
        trip.setId(2L);
        trip.setStatus(TripStatus.SEARCHING);
        trip.setSource("CALL");
        User p = new User();
        p.setId(10L);
        trip.setPassenger(p);
        when(tripRepository.findById(2L)).thenReturn(Optional.of(trip));

        Map<String, Object> res = tripService.acceptTrip(driverUser, 2L);

        assertEquals("ACCEPTED", res.get("status"));
        assertEquals(TripStatus.ACCEPTED, trip.getStatus());
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

    // ── getAvailableTrips × cooldown × operator(CALL)-bypass — Deploy 5 push bypass'ining FOREGROUND juftligi ──

    private Trip searching(long id, String source) {
        Trip t = new Trip();
        t.setId(id);
        t.setStatus(TripStatus.SEARCHING);
        t.setSource(source);
        t.setFromAddress("Chilonzor");
        t.setTotalPrice(100_000L);
        return t;
    }

    @Test
    @DisplayName("Cooldown'da yo'lovchi (APP) buyurtma KO'RINMAYDI")
    void getAvailableTrips_inCooldown_passengerHidden() {
        driver.setOrderCooldownUntil(LocalDateTime.now().plusSeconds(60));
        when(tripRepository.findByStatusWithRelations(TripStatus.SEARCHING))
                .thenReturn(List.of(searching(1L, "APP")));

        assertTrue(tripService.getAvailableTrips(driverUser).isEmpty(),
                "cooldown'da oddiy yo'lovchi buyurtmasi ko'rinmasligi kerak");
    }

    @Test
    @DisplayName("Cooldown'da operator (CALL) buyurtma KO'RINADI — matching bypass bilan bir xil")
    void getAvailableTrips_inCooldown_operatorShown() {
        driver.setOrderCooldownUntil(LocalDateTime.now().plusSeconds(60));
        when(tripRepository.findByStatusWithRelations(TripStatus.SEARCHING))
                .thenReturn(List.of(searching(1L, "CALL")));

        List<Map<String, Object>> res = tripService.getAvailableTrips(driverUser);

        assertEquals(1, res.size(), "cooldown'da operator buyurtmasi foreground ro'yxatda ko'rinishi kerak");
        assertEquals(1L, ((Number) res.get(0).get("id")).longValue());
    }

    @Test
    @DisplayName("Cooldown'da CALL_TAXOMETER ham KO'RINADI (startsWith(\"CALL\"))")
    void getAvailableTrips_inCooldown_operatorTaxometerShown() {
        driver.setOrderCooldownUntil(LocalDateTime.now().plusSeconds(60));
        when(tripRepository.findByStatusWithRelations(TripStatus.SEARCHING))
                .thenReturn(List.of(searching(1L, "CALL_TAXOMETER")));

        assertEquals(1, tripService.getAvailableTrips(driverUser).size());
    }

    @Test
    @DisplayName("Cooldown'da aralash ro'yxat -> FAQAT operator (CALL) qoladi, yo'lovchi (APP) filtrlanadi")
    void getAvailableTrips_inCooldown_mixed_onlyOperator() {
        driver.setOrderCooldownUntil(LocalDateTime.now().plusSeconds(60));
        when(tripRepository.findByStatusWithRelations(TripStatus.SEARCHING))
                .thenReturn(List.of(searching(1L, "APP"), searching(2L, "CALL"), searching(3L, "APP")));

        List<Map<String, Object>> res = tripService.getAvailableTrips(driverUser);

        assertEquals(1, res.size());
        assertEquals(2L, ((Number) res.get(0).get("id")).longValue());
    }

    @Test
    @DisplayName("Cooldown YO'Q -> APP ham CALL ham KO'RINADI (bypass faqat cooldown'da ta'sir qiladi)")
    void getAvailableTrips_notInCooldown_bothShown() {
        // orderCooldownUntil = null → cooldown faol emas
        when(tripRepository.findByStatusWithRelations(TripStatus.SEARCHING))
                .thenReturn(List.of(searching(1L, "APP"), searching(2L, "CALL")));

        assertEquals(2, tripService.getAvailableTrips(driverUser).size());
    }

    @Test
    @DisplayName("Band (faol trip) haydovchi cooldown'da operator buyurtmani ham KO'RMAYDI (band darvozasi istisno qilinmaydi)")
    void getAvailableTrips_busyOverridesOperatorBypass() {
        driver.setOrderCooldownUntil(LocalDateTime.now().plusSeconds(60));
        when(tripRepository.existsByDriverIdAndStatusIn(5L, ACTIVE)).thenReturn(true);

        assertTrue(tripService.getAvailableTrips(driverUser).isEmpty());
        verify(tripRepository, never()).findByStatusWithRelations(any());
    }
}
