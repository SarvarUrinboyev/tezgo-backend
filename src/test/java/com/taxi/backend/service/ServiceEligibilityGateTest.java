package com.taxi.backend.service;

import com.taxi.backend.enums.ServiceType;
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
 * Service eligibility hard-filter — per-driver xizmat darvozalari (tariff/balans kabi).
 * Buyurtma xizmatlari (selected_services) haydovchining yoqilgan xizmatlari bilan AND solishtiriladi:
 * yetishmagan xizmat -> match/board/claim/accept rad etiladi.
 *
 * Gates: TripService.getAvailableTrips, TripService.acceptTrip,
 *        BroadcastBoardService.getBroadcastBoard, BroadcastBoardService.claimTrip.
 */
@ExtendWith(MockitoExtension.class)
class ServiceEligibilityGateTest {

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
    private BroadcastBoardService broadcastService;
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

        broadcastService = new BroadcastBoardService(tripRepository, driverRepository, pushService);

        driverUser = new User();
        driverUser.setId(100L);
        driverUser.setName("Test Haydovchi");
        driverUser.setPhone("+998901112233");
        driver = new Driver();
        driver.setId(5L);
        driver.setOnline(true);
        driver.setBalance(0L); // balans 0 — boshqa darvozalardan o'tadi
        driver.setCarModel("Cobalt");
        driver.setCarNumber("01A777AA");
        lenient().when(driverRepository.findByUserId(100L)).thenReturn(Optional.of(driver));
        lenient().when(tripRepository.existsByDriverIdAndStatusIn(5L, ACTIVE)).thenReturn(false);
    }

    private Trip searchingTrip(long id, String services) {
        Trip t = new Trip();
        t.setId(id);
        t.setStatus(TripStatus.SEARCHING);
        t.setSource("APP");
        t.setFromAddress("Chilonzor");
        t.setTotalPrice(100_000L);
        t.setSelectedServices(services);
        return t;
    }

    private void driverHas(ServiceType... types) {
        when(driverRepository.findEnabledServiceTypesByDriverId(5L)).thenReturn(List.of(types));
    }

    // ── getAvailableTrips ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getAvailableTrips: order [ROOF_LUGGAGE], driver'da yo'q -> bo'sh ro'yxat")
    void available_missingService_empty() {
        driverHas(); // hech qanday xizmat yoqilmagan
        when(tripRepository.findByStatusWithRelations(TripStatus.SEARCHING))
                .thenReturn(List.of(searchingTrip(1L, "ROOF_LUGGAGE")));
        assertTrue(tripService.getAvailableTrips(driverUser).isEmpty());
    }

    @Test
    @DisplayName("getAvailableTrips: order [AC], driver'da AC(+more) bor -> ko'rinadi")
    void available_hasService_present() {
        driverHas(ServiceType.AC, ServiceType.DELIVERY);
        when(tripRepository.findByStatusWithRelations(TripStatus.SEARCHING))
                .thenReturn(List.of(searchingTrip(1L, "AC")));
        assertEquals(1, tripService.getAvailableTrips(driverUser).size());
    }

    @Test
    @DisplayName("getAvailableTrips: order [ROOF_LUGGAGE,AC], driver'da faqat AC -> bo'sh (AND)")
    void available_andLogic_missingOne_empty() {
        driverHas(ServiceType.AC);
        when(tripRepository.findByStatusWithRelations(TripStatus.SEARCHING))
                .thenReturn(List.of(searchingTrip(1L, "ROOF_LUGGAGE,AC")));
        assertTrue(tripService.getAvailableTrips(driverUser).isEmpty());
    }

    @Test
    @DisplayName("getAvailableTrips: order xizmatsiz -> driver service filtrdan o'tadi")
    void available_noServices_present() {
        driverHas(); // xizmat yo'q bo'lsa ham mayli
        when(tripRepository.findByStatusWithRelations(TripStatus.SEARCHING))
                .thenReturn(List.of(searchingTrip(1L, null)));
        assertEquals(1, tripService.getAvailableTrips(driverUser).size());
    }

    // ── acceptTrip ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("acceptTrip: order [ROOF_LUGGAGE], driver'da yo'q -> rad (4xx-message, NPE emas)")
    void accept_missingService_rejected() {
        driverHas();
        when(tripRepository.findById(2L)).thenReturn(Optional.of(searchingTrip(2L, "ROOF_LUGGAGE")));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tripService.acceptTrip(driverUser, 2L));
        assertFalse(ex instanceof NullPointerException);
        assertTrue(ex.getMessage().contains("kerakli xizmatlar"));
    }

    @Test
    @DisplayName("acceptTrip: order [AC], driver'da AC bor -> ACCEPTED")
    void accept_hasService_allowed() {
        driverHas(ServiceType.AC);
        Trip trip = searchingTrip(2L, "AC");
        User p = new User();
        p.setId(10L);
        trip.setPassenger(p);
        when(tripRepository.findById(2L)).thenReturn(Optional.of(trip));

        Map<String, Object> res = tripService.acceptTrip(driverUser, 2L);

        assertEquals("ACCEPTED", res.get("status"));
        assertEquals(TripStatus.ACCEPTED, trip.getStatus());
    }

    // ── getBroadcastBoard ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getBroadcastBoard: order [ROOF_LUGGAGE], driver'da yo'q -> bo'sh taxta")
    void board_missingService_empty() {
        driverHas();
        when(tripRepository.findBroadcastTripBoard())
                .thenReturn(List.of(searchingTrip(1L, "ROOF_LUGGAGE")));
        assertTrue(broadcastService.getBroadcastBoard(driverUser).isEmpty());
    }

    @Test
    @DisplayName("getBroadcastBoard: order [AC], driver'da AC bor -> ko'rinadi")
    void board_hasService_present() {
        driverHas(ServiceType.AC);
        when(tripRepository.findBroadcastTripBoard())
                .thenReturn(List.of(searchingTrip(1L, "AC")));
        assertEquals(1, broadcastService.getBroadcastBoard(driverUser).size());
    }

    // ── claimTrip ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("claimTrip: order [ROOF_LUGGAGE], driver'da yo'q -> rad (IllegalArgument -> 4xx)")
    void claim_missingService_rejected() {
        driverHas();
        when(tripRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(searchingTrip(3L, "ROOF_LUGGAGE")));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> broadcastService.claimTrip(driverUser, 3L));
        assertTrue(ex.getMessage().contains("kerakli xizmatlar"));
    }

    @Test
    @DisplayName("claimTrip: order [AC], driver'da AC bor -> ACCEPTED (barcha darvozalardan o'tadi)")
    void claim_hasService_allowed() {
        driverHas(ServiceType.AC, ServiceType.ROOF_LUGGAGE);
        Trip trip = searchingTrip(3L, "AC");
        when(tripRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(trip));

        Map<String, Object> res = broadcastService.claimTrip(driverUser, 3L);

        assertEquals("ACCEPTED", res.get("status"));
        assertEquals(TripStatus.ACCEPTED, trip.getStatus());
    }
}
