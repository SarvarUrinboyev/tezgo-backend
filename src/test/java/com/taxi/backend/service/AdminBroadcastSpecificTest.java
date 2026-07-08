package com.taxi.backend.service;

import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.model.BroadcastMessage;
import com.taxi.backend.model.Driver;
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
 * Band 7 — Broadcast with SPECIFIC (single-driver) target.
 *
 * Verifies: SPECIFIC + driverId narrows delivery to /topic/broadcast/{driverId},
 * stores driverId in BroadcastMessage.targetDriverIds, and rejects missing driverId.
 */
@ExtendWith(MockitoExtension.class)
class AdminBroadcastSpecificTest {

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

    private AdminService service;

    @BeforeEach
    void setup() {
        service = new AdminService(driverRepository, driverPhotoRepository, driverServiceRepository,
                tripRepository, broadcastMessageRepository, userRepository, messagingTemplate,
                ratingRepository, transactionRepository, chatService, tariffRepository, asyncNotifier,
                photoService);
    }

    private User admin() {
        User u = new User();
        u.setId(1L);
        u.setName("Admin");
        return u;
    }

    private Driver activeDriver(long id) {
        Driver d = new Driver();
        d.setId(id);
        d.setStatus(DriverStatus.ACTIVE);
        d.setDriverCode("TZ-9999");
        User u = new User();
        u.setId(id + 1000);
        u.setName("Davron");
        d.setUser(u);
        return d;
    }

    @Test
    @DisplayName("ALL target -> /topic/broadcast (eski xulq), targetDriverIds saqlanmaydi")
    void targetAll_globalTopic_noTargetIds() {
        when(broadcastMessageRepository.save(any())).thenAnswer(inv -> {
            BroadcastMessage m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });

        Map<String, Object> res = service.sendBroadcast("Salom", "Tinch yo'lda bo'ling", "ALL", null, admin());

        ArgumentCaptor<BroadcastMessage> msgCap = ArgumentCaptor.forClass(BroadcastMessage.class);
        verify(broadcastMessageRepository).save(msgCap.capture());
        assertEquals("ALL", msgCap.getValue().getTarget());
        assertNull(msgCap.getValue().getTargetDriverIds());

        verify(messagingTemplate).convertAndSend(eq("/topic/broadcast"), any(Map.class));
        verifyNoMoreInteractions(messagingTemplate);
        assertEquals("ALL", res.get("target"));
    }

    @Test
    @DisplayName("SPECIFIC target + driverId -> /topic/broadcast/{driverId}, targetDriverIds saqlanadi, driver tekshiriladi")
    @SuppressWarnings("unchecked")
    void targetSpecific_singleDriverTopic_storesTargetIds() {
        Driver d = activeDriver(42L);
        when(driverRepository.findById(42L)).thenReturn(Optional.of(d));
        when(broadcastMessageRepository.save(any())).thenAnswer(inv -> {
            BroadcastMessage m = inv.getArgument(0);
            m.setId(101L);
            return m;
        });

        Map<String, Object> res = service.sendBroadcast("Maxsus", "Faqat senga", "SPECIFIC", 42L, admin());

        ArgumentCaptor<BroadcastMessage> msgCap = ArgumentCaptor.forClass(BroadcastMessage.class);
        verify(broadcastMessageRepository).save(msgCap.capture());
        BroadcastMessage saved = msgCap.getValue();
        assertEquals("SPECIFIC", saved.getTarget());
        assertNotNull(saved.getTargetDriverIds(), "targetDriverIds saqlanmadi");
        assertEquals(1, saved.getTargetDriverIds().size());
        assertEquals(42L, saved.getTargetDriverIds().get(0));

        ArgumentCaptor<String> topicCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> payloadCap = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(topicCap.capture(), payloadCap.capture());
        assertEquals("/topic/broadcast/42", topicCap.getValue(), "WS topic driver-specific bo'lishi kerak");
        assertEquals(42L, payloadCap.getValue().get("targetedDriverId"));
        assertEquals("Maxsus", payloadCap.getValue().get("title"));
        assertEquals("Faqat senga", payloadCap.getValue().get("content"));

        assertEquals("SPECIFIC", res.get("target"));
        assertEquals("TZ-9999", res.get("driverCode"));
        assertEquals("Davron", res.get("driverName"));
    }

    @Test
    @DisplayName("'DRIVER' qisqartmasi SPECIFIC ga normalizatsiya qilinadi")
    void targetDriverAlias_normalizesToSpecific() {
        Driver d = activeDriver(42L);
        when(driverRepository.findById(42L)).thenReturn(Optional.of(d));
        when(broadcastMessageRepository.save(any())).thenAnswer(inv -> {
            BroadcastMessage m = inv.getArgument(0);
            m.setId(102L);
            return m;
        });

        service.sendBroadcast("X", "Y", "DRIVER", 42L, admin());

        ArgumentCaptor<BroadcastMessage> msgCap = ArgumentCaptor.forClass(BroadcastMessage.class);
        verify(broadcastMessageRepository).save(msgCap.capture());
        assertEquals("SPECIFIC", msgCap.getValue().getTarget(), "'DRIVER' qabul qilinsa ham, entity'da SPECIFIC saqlanishi kerak");
    }

    @Test
    @DisplayName("SPECIFIC + driverId yo'q -> rad qilish, hech narsa yuborilmaydi")
    void targetSpecific_missingDriverId_throws() {
        assertThrows(RuntimeException.class,
                () -> service.sendBroadcast("X", "Y", "SPECIFIC", null, admin()));

        verifyNoInteractions(broadcastMessageRepository);
        verifyNoInteractions(messagingTemplate);
        verifyNoInteractions(driverRepository);
    }

    @Test
    @DisplayName("SPECIFIC + mavjud bo'lmagan driverId -> rad qilish, broadcast saqlanmaydi")
    void targetSpecific_unknownDriver_throws() {
        when(driverRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> service.sendBroadcast("X", "Y", "SPECIFIC", 9999L, admin()));

        verifyNoInteractions(broadcastMessageRepository);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Eski 4 parametrli signatura mavjud (backward-compat) — target=ALL bilan ishlaydi")
    void legacy4argOverload_stillWorks() {
        when(broadcastMessageRepository.save(any())).thenAnswer(inv -> {
            BroadcastMessage m = inv.getArgument(0);
            m.setId(103L);
            return m;
        });

        Map<String, Object> res = service.sendBroadcast("Eski", "API", "ACTIVE", admin());

        verify(messagingTemplate).convertAndSend(eq("/topic/broadcast"), any(Map.class));
        assertEquals("ACTIVE", res.get("target"));
    }
}
