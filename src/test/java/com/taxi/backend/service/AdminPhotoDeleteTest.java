package com.taxi.backend.service;

import com.taxi.backend.enums.PhotoType;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Admin rasm o'chirish — DRIVER_FACE/SELFIE ham kiradi, driver-app immutability qulfini chetlab o'tadi. */
@ExtendWith(MockitoExtension.class)
class AdminPhotoDeleteTest {

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

    @Test
    @DisplayName("Admin DRIVER_FACE'ni o'chira oladi (driver-app immutability qulfi bu yerga tegmaydi)")
    void adminCanDeleteDriverFace() {
        Driver driver = new Driver();
        driver.setId(6L);
        DriverPhoto photo = new DriverPhoto();
        photo.setId(99L);
        photo.setDriver(driver);
        photo.setPhotoType(PhotoType.DRIVER_FACE);
        photo.setPhotoUrl("/api/photos/view/6/driver_face_x.jpg");
        when(photoService.adminDeletePhoto(99L)).thenReturn(photo);

        User admin = new User();
        admin.setId(1L);
        admin.setName("Admin");

        Map<String, Object> res = service.deletePhoto(99L, "xato rasm, qayta yuklash kerak", admin);

        assertEquals(Boolean.TRUE, res.get("deleted"));
        assertEquals("DRIVER_FACE", res.get("type"));
        verify(photoService).adminDeletePhoto(99L);
    }

    @Test
    @DisplayName("Admin SELFIE'ni o'chira oladi")
    void adminCanDeleteSelfie() {
        Driver driver = new Driver();
        driver.setId(6L);
        DriverPhoto photo = new DriverPhoto();
        photo.setId(100L);
        photo.setDriver(driver);
        photo.setPhotoType(PhotoType.SELFIE);
        photo.setPhotoUrl("/api/photos/view/6/selfie_x.jpg");
        when(photoService.adminDeletePhoto(100L)).thenReturn(photo);

        User admin = new User();
        admin.setId(1L);
        admin.setName("Admin");

        Map<String, Object> res = service.deletePhoto(100L, "yuz aniq ko'rinmayapti", admin);

        assertEquals(Boolean.TRUE, res.get("deleted"));
        assertEquals("SELFIE", res.get("type"));
    }
}
