package com.taxi.backend.service;

import com.taxi.backend.enums.*;
import com.taxi.backend.model.*;
import com.taxi.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Scope 1b — offline->online rad etish cooldown'ini tozalaydi (haydovchining manual reset'i). */
@ExtendWith(MockitoExtension.class)
class OnlineClearsCooldownTest {

    @Mock private DriverRepository driverRepository;
    @Mock private DriverServiceRepository driverServiceRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private BroadcastMessageRepository broadcastMessageRepository;
    @Mock private DriverPhotoRepository driverPhotoRepository;
    @Mock private TripRepository tripRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private DriverLocationCache locationCache;
    @Mock private TariffRepository tariffRepository;

    private DriverAppService service;
    private User user;
    private Driver driver;

    @BeforeEach
    void setup() {
        service = new DriverAppService(driverRepository, driverServiceRepository, transactionRepository,
                broadcastMessageRepository, driverPhotoRepository, tripRepository, messagingTemplate,
                locationCache, tariffRepository);
        user = new User();
        user.setId(100L);
        driver = new Driver();
        driver.setId(5L);
        driver.setStatus(DriverStatus.ACTIVE);
        driver.setOnline(false); // hozir offline -> toggle ONLINE qiladi
        driver.setLatitude(41.30);
        driver.setLongitude(69.60);
        driver.setOrderCooldownUntil(LocalDateTime.now().plusSeconds(60)); // cooldown FAOL
        when(driverRepository.findByUserId(100L)).thenReturn(Optional.of(driver));
    }

    @Test
    @DisplayName("Offline->Online -> order_cooldown_until tozalanadi")
    void goOnline_clearsCooldown() {
        assertTrue(driver.isInCooldown(), "boshlanishda cooldown faol bo'lishi kerak");

        Map<String, Object> res = service.toggleOnlinePlain(user);

        assertEquals(Boolean.TRUE, res.get("isOnline"));
        assertNull(driver.getOrderCooldownUntil(), "online qaytganda cooldown tozalanishi kerak");
        assertFalse(driver.isInCooldown());
    }
}
