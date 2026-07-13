package com.taxi.backend.service;

import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Tariff;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.TripDriverOffer;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Band 5 — Admin order management guards.
 *
 * Verifies the 3 new admin trip endpoints honor the documented contract:
 *   - SEARCHING-only mutation (post-acceptance trips are immutable from admin)
 *   - reassign uses the EXISTING data-only dispatch path (title=null, body=null, type=ORDER_PUSH)
 *   - reassign requires the target driver to be ACTIVE
 */
@ExtendWith(MockitoExtension.class)
class AdminTripMgmtServiceTest {

    @Mock private DriverRepository driverRepository;
    @Mock private DriverPhotoRepository driverPhotoRepository;
    @Mock private DriverServiceRepository driverServiceRepository;
    @Mock private TripRepository tripRepository;
    @Mock private BroadcastMessageRepository broadcastMessageRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private RatingRepository ratingRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private ChatService chatService;
    @Mock private TariffRepository tariffRepository;
    @Mock private AsyncNotificationService asyncNotifier;
    @Mock private PhotoService photoService;
    @Mock private TripNotificationHelper notificationHelper;

    private AdminService service;

    @BeforeEach
    void setup() {
        service = new AdminService(driverRepository, driverPhotoRepository, driverServiceRepository,
                tripRepository, broadcastMessageRepository, userRepository, messagingTemplate,
                ratingRepository, transactionRepository, chatService, tariffRepository, asyncNotifier,
                photoService, notificationHelper);
    }

    private Trip searchingTrip(long id) {
        Trip t = new Trip();
        t.setId(id);
        t.setStatus(TripStatus.SEARCHING);
        t.setBasePrice(5000_00L);    // 5000 UZS in tiyin
        t.setTotalPrice(7500_00L);   // 7500 UZS in tiyin
        t.setFromAddress("Eski");
        t.setFromLat(41.0);
        t.setFromLon(69.0);
        return t;
    }

    private Tariff tariff(long id, String name, long basePriceTiyin) {
        Tariff x = new Tariff();
        x.setId(id);
        x.setName(name);
        x.setBasePrice(basePriceTiyin);
        return x;
    }

    private Driver activeDriver(long id) {
        Driver d = new Driver();
        d.setId(id);
        d.setStatus(DriverStatus.ACTIVE);
        d.setDriverCode("TZ-0123");
        User u = new User();
        u.setId(id + 1000);
        u.setName("Aliyev");
        d.setUser(u);
        return d;
    }

    // ─── changeTariff ────────────────────────────────────────────────────────

    @Test
    @DisplayName("changeTariff SEARCHING -> basePrice yangi tarifdan, totalPrice delta bilan tuziladi")
    void changeTariff_searching_recomputesBaseAndTotal() {
        Trip t = searchingTrip(10L);
        Tariff newTariff = tariff(2L, "KOMFORT", 8000_00L);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(t));
        when(tariffRepository.findById(2L)).thenReturn(Optional.of(newTariff));

        Map<String, Object> res = service.adminChangeTripTariff(10L, 2L);

        assertEquals(8000_00L, t.getBasePrice(), "basePrice yangi tarifdan kelishi kerak");
        // delta = +3000_00; totalPrice 7500_00 + 3000_00 = 10500_00
        assertEquals(10500_00L, t.getTotalPrice(), "totalPrice basePrice deltasi qo'shilishi kerak");
        assertEquals(newTariff, t.getTariff());
        assertEquals(10L, res.get("tripId"));
        assertEquals("KOMFORT", res.get("tariffName"));
        verify(tripRepository).save(t);
    }

    @Test
    @DisplayName("changeTariff non-SEARCHING -> rad qilish, save chaqirilmasligi kerak")
    void changeTariff_nonSearching_throws() {
        Trip t = searchingTrip(10L);
        t.setStatus(TripStatus.ACCEPTED);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(t));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.adminChangeTripTariff(10L, 2L));
        assertTrue(ex.getMessage().contains("SEARCHING"), "xato xabari SEARCHING'ni eslatishi kerak");
        verify(tripRepository, never()).save(any());
        verify(tariffRepository, never()).findById(any());
    }

    // ─── editAddresses ───────────────────────────────────────────────────────

    @Test
    @DisplayName("editAddresses SEARCHING -> 6 ta maydon yangilanadi")
    void editAddresses_searching_updatesAllFields() {
        Trip t = searchingTrip(20L);
        when(tripRepository.findById(20L)).thenReturn(Optional.of(t));

        service.adminEditTripAddresses(20L,
                "Yangi Boshlanish", 41.5, 69.5,
                "Yangi Manzil", 41.6, 69.6);

        assertEquals("Yangi Boshlanish", t.getFromAddress());
        assertEquals(41.5, t.getFromLat());
        assertEquals(69.5, t.getFromLon());
        assertEquals("Yangi Manzil", t.getToAddress());
        assertEquals(41.6, t.getToLat());
        assertEquals(69.6, t.getToLon());
        verify(tripRepository).save(t);
    }

    @Test
    @DisplayName("editAddresses non-SEARCHING -> rad qilish")
    void editAddresses_nonSearching_throws() {
        Trip t = searchingTrip(20L);
        t.setStatus(TripStatus.STARTED);
        when(tripRepository.findById(20L)).thenReturn(Optional.of(t));

        assertThrows(RuntimeException.class,
                () -> service.adminEditTripAddresses(20L, "X", 41.0, 69.0, null, null, null));
        verify(tripRepository, never()).save(any());
    }

    @Test
    @DisplayName("editAddresses bo'sh fromAddress -> rad qilish")
    void editAddresses_blankFromAddress_throws() {
        Trip t = searchingTrip(20L);
        when(tripRepository.findById(20L)).thenReturn(Optional.of(t));

        assertThrows(RuntimeException.class,
                () -> service.adminEditTripAddresses(20L, "   ", 41.0, 69.0, null, null, null));
        verify(tripRepository, never()).save(any());
    }

    // ─── reassign ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reassign SEARCHING + ACTIVE driver -> WS + data-only PUSH (title=null, body=null, type=ORDER_PUSH)")
    @SuppressWarnings("unchecked")
    void reassign_happyPath_callsDataOnlyDispatch() {
        Trip t = searchingTrip(30L);
        t.setFromAddress("From X");
        t.setToAddress("To Y");
        Driver d = activeDriver(77L);
        when(tripRepository.findById(30L)).thenReturn(Optional.of(t));
        when(driverRepository.findById(77L)).thenReturn(Optional.of(d));

        TripDriverOffer offer = new TripDriverOffer();
        offer.setExpiresAt(java.time.LocalDateTime.now().plusSeconds(15));
        when(notificationHelper.notifySpecificDriver(30L, 77L)).thenReturn(Optional.of(offer));

        Map<String, Object> res = service.adminReassignTripToDriver(30L, 77L);

        // Admin path creates the durable offer; delivery itself remains owned by
        // TripOfferDeliveryService, never by a second push builder here.
        verify(notificationHelper).notifySpecificDriver(30L, 77L);
        verifyNoInteractions(asyncNotifier);

        assertEquals(30L, res.get("tripId"));
        assertEquals(77L, res.get("driverId"));
        assertEquals("TZ-0123", res.get("driverCode"));
        assertEquals("SEARCHING", res.get("status"));
    }

    @Test
    @DisplayName("reassign non-SEARCHING -> push YUBORILMAYDI")
    void reassign_nonSearching_noDispatch() {
        Trip t = searchingTrip(30L);
        t.setStatus(TripStatus.ACCEPTED);
        when(tripRepository.findById(30L)).thenReturn(Optional.of(t));

        assertThrows(RuntimeException.class, () -> service.adminReassignTripToDriver(30L, 77L));
        verifyNoInteractions(notificationHelper);
        verify(driverRepository, never()).findById(any());
    }

    @Test
    @DisplayName("reassign non-ACTIVE driver -> push YUBORILMAYDI")
    void reassign_nonActiveDriver_noDispatch() {
        Trip t = searchingTrip(30L);
        Driver d = activeDriver(77L);
        d.setStatus(DriverStatus.BLOCKED);
        when(tripRepository.findById(30L)).thenReturn(Optional.of(t));
        when(driverRepository.findById(77L)).thenReturn(Optional.of(d));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.adminReassignTripToDriver(30L, 77L));
        assertTrue(ex.getMessage().contains("ACTIVE"));
        verifyNoInteractions(notificationHelper);
    }
}
