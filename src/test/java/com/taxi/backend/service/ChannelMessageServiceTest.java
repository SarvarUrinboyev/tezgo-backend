package com.taxi.backend.service;

import com.taxi.backend.model.ChannelMessage;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.ChannelMessageRepository;
import com.taxi.backend.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** V39 — kanal xabarlari: fan-out, TEXNIK_YORDAM rad etish, per-kanal unread. */
@ExtendWith(MockitoExtension.class)
class ChannelMessageServiceTest {

    @Mock private ChannelMessageRepository repo;
    @Mock private DriverRepository driverRepository;

    private ChannelMessageService service;
    private User admin;

    @BeforeEach
    void setup() {
        service = new ChannelMessageService(repo, driverRepository);
        admin = new User();
        admin.setId(1L);
    }

    @Test
    @DisplayName("sendToChannel ALL -> har haydovchiga bitta satr (fan-out)")
    @SuppressWarnings("unchecked")
    void sendToChannel_fansOutToAllDrivers() {
        Driver d1 = new Driver(); d1.setId(5L);
        Driver d2 = new Driver(); d2.setId(6L);
        when(driverRepository.findAll()).thenReturn(List.of(d1, d2));

        Map<String, Object> res = service.sendToChannel("BONUSLAR", "Sarlavha", "matn", "ALL", admin);

        assertEquals(2, res.get("sent"));
        ArgumentCaptor<List<ChannelMessage>> cap = ArgumentCaptor.forClass(List.class);
        verify(repo).saveAll(cap.capture());
        List<ChannelMessage> saved = cap.getValue();
        assertEquals(2, saved.size());
        assertEquals("BONUSLAR", saved.get(0).getChannel());
        assertEquals(5L, saved.get(0).getDriverId());
        assertEquals("matn", saved.get(0).getBody());
    }

    @Test
    @DisplayName("sendToChannel TEXNIK_YORDAM -> RAD etiladi (2 tomonlama kanal broadcast emas)")
    void sendToChannel_rejectsTexnikYordam() {
        assertThrows(IllegalArgumentException.class,
                () -> service.sendToChannel("TEXNIK_YORDAM", null, "x", "ALL", admin));
        verify(repo, never()).saveAll(any());
    }

    @Test
    @DisplayName("sendToChannel ACTIVE -> faqat online haydovchilar")
    void sendToChannel_activeAudience_usesOnlineDrivers() {
        Driver d1 = new Driver(); d1.setId(5L);
        when(driverRepository.findByIsOnlineTrue()).thenReturn(List.of(d1));

        Map<String, Object> res = service.sendToChannel("OGOHLANTIRISHLAR", null, "x", "ACTIVE", admin);

        assertEquals(1, res.get("sent"));
        verify(driverRepository).findByIsOnlineTrue();
        verify(driverRepository, never()).findAll();
    }

    @Test
    @DisplayName("Band 7 — sendToChannel DRIVER + driverId -> faqat 1 ta haydovchiga 1 satr (driverCode javobda)")
    void sendToChannel_driverTarget_singleDriverFanout() {
        Driver d = new Driver();
        d.setId(42L);
        d.setDriverCode("TZ-0042");
        when(driverRepository.findById(42L)).thenReturn(Optional.of(d));

        Map<String, Object> res = service.sendToChannel("BONUSLAR", "Maxsus", "Faqat senga", "DRIVER", 42L, admin);

        assertEquals(1, res.get("sent"));
        assertEquals("DRIVER", res.get("target"));
        assertEquals("TZ-0042", res.get("driverCode"));
        // Boshqa auditoriya querilari chaqirilmasligi kerak
        verify(driverRepository, never()).findAll();
        verify(driverRepository, never()).findByIsOnlineTrue();
        verify(driverRepository, never()).findByIsOnlineFalse();
        verify(repo).saveAll(any());
    }

    @Test
    @DisplayName("Band 7 — sendToChannel DRIVER + driverId yo'q -> rad qilish, hech narsa saqlanmaydi")
    void sendToChannel_driverTarget_missingId_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.sendToChannel("BONUSLAR", null, "x", "DRIVER", null, admin));
        verify(repo, never()).saveAll(any());
        verifyNoInteractions(repo);
    }

    @Test
    @DisplayName("Band 7 — sendToChannel DRIVER + mavjud bo'lmagan driverId -> rad qilish")
    void sendToChannel_driverTarget_unknownDriver_throws() {
        when(driverRepository.findById(9999L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.sendToChannel("BONUSLAR", null, "x", "DRIVER", 9999L, admin));
        verify(repo, never()).saveAll(any());
    }

    @Test
    @DisplayName("Eski 5-parametrli sendToChannel signaturasi backward-compat — ALL bilan ishlaydi")
    void sendToChannel_legacy5argOverload_stillWorks() {
        Driver d1 = new Driver(); d1.setId(5L);
        when(driverRepository.findAll()).thenReturn(List.of(d1));

        Map<String, Object> res = service.sendToChannel("BONUSLAR", null, "matn", "ALL", admin);

        assertEquals(1, res.get("sent"));
        verify(driverRepository).findAll();
    }

    @Test
    @DisplayName("unreadByChannel -> barcha 5 broadcast kanal 0 bilan to'ldiriladi, mavjudlari hisoblanadi")
    void unreadByChannel_fillsAllBroadcastChannels() {
        User u = new User(); u.setId(100L);
        Driver dr = new Driver(); dr.setId(5L);
        when(driverRepository.findByUserId(100L)).thenReturn(Optional.of(dr));
        when(repo.unreadCountsByChannel(5L)).thenReturn(List.<Object[]>of(new Object[]{"BONUSLAR", 3L}));

        Map<String, Long> res = service.unreadByChannel(u);

        assertEquals(5, res.size(), "5 broadcast kanal");
        assertEquals(3L, res.get("BONUSLAR"));
        assertEquals(0L, res.get("PRO_YANGILIKLARI"));
        assertEquals(0L, res.get("HISOB_BALANS"));
    }
}
