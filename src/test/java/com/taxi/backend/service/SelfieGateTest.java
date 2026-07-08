package com.taxi.backend.service;

import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.enums.PhotoType;
import com.taxi.backend.exception.ForbiddenException;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.DriverPhoto;
import com.taxi.backend.model.User;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Selfie gate — offline->online SELFIE talab qiladi; offline'ga chiqish hech qachon bloklanmaydi. */
@ExtendWith(MockitoExtension.class)
class SelfieGateTest {

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
        user.setId(200L);
        driver = new Driver();
        driver.setId(7L);
        driver.setStatus(DriverStatus.ACTIVE);
        when(driverRepository.findByUserId(200L)).thenReturn(Optional.of(driver));
    }

    @Test
    @DisplayName("Selfie yo'q -> onlaynga chiqish ForbiddenException bilan rad etiladi, holat o'zgarmaydi")
    void noSelfie_blocksGoingOnline() {
        driver.setOnline(false);
        when(driverPhotoRepository.findByDriverIdAndPhotoType(7L, PhotoType.SELFIE)).thenReturn(Optional.empty());

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> service.toggleOnlinePlain(user));

        assertTrue(ex.getMessage().toLowerCase().contains("selfie"));
        assertFalse(driver.isOnline(), "rad etilgach holat o'zgarmasligi kerak");
        verify(driverRepository, never()).save(any());
    }

    @Test
    @DisplayName("Selfie bor -> onlaynga chiqish muvaffaqiyatli")
    void hasSelfie_allowsGoingOnline() {
        driver.setOnline(false);
        driver.setLatitude(41.3);
        driver.setLongitude(69.6);
        when(driverPhotoRepository.findByDriverIdAndPhotoType(7L, PhotoType.SELFIE))
                .thenReturn(Optional.of(new DriverPhoto()));

        Map<String, Object> res = service.toggleOnlinePlain(user);

        assertEquals(Boolean.TRUE, res.get("isOnline"));
        assertTrue(driver.isOnline());
    }

    @Test
    @DisplayName("Online->Offline hech qachon selfie tekshiruvi bilan bloklanmaydi")
    void goingOffline_neverBlockedBySelfieCheck() {
        driver.setOnline(true); // hozir online -> toggle OFFLINE qiladi

        Map<String, Object> res = service.toggleOnlinePlain(user);

        assertEquals(Boolean.FALSE, res.get("isOnline"));
        verify(driverPhotoRepository, never()).findByDriverIdAndPhotoType(anyLong(), eq(PhotoType.SELFIE));
    }
}
