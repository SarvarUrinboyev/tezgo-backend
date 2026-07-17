package com.taxi.backend.controller;

import com.taxi.backend.enums.Role;
import com.taxi.backend.model.OtpCode;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.BannerRepository;
import com.taxi.backend.repository.OtpRepository;
import com.taxi.backend.repository.TariffRepository;
import com.taxi.backend.repository.TripRepository;
import com.taxi.backend.repository.UserRepository;
import com.taxi.backend.service.AdminService;
import com.taxi.backend.service.AuthService;
import com.taxi.backend.service.OperatorAdminService;
import com.taxi.backend.service.PhotoService;
import com.taxi.backend.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOtpMonitorContainmentTest {

    @Mock private AdminService adminService;
    @Mock private PhotoService photoService;
    @Mock private TariffRepository tariffRepository;
    @Mock private UserRepository userRepository;
    @Mock private TripRepository tripRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private OtpRepository otpRepository;
    @Mock private AuthService authService;
    @Mock private SystemSettingService systemSettingService;
    @Mock private OperatorAdminService operatorAdminService;
    @Mock private BannerRepository bannerRepository;

    private AdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminController(adminService, photoService, tariffRepository, userRepository,
                tripRepository, redisTemplate, otpRepository, authService, systemSettingService,
                operatorAdminService, bannerRepository);
    }

    @Test
    void otpMonitorRequiresAdmin() {
        ResponseEntity<?> response = controller.otpMonitor(user(2L, Role.OPERATOR));
        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(otpRepository);
    }

    @Test
    void otpMonitorReturnsMaskedMetadataWithoutLiveCode() {
        OtpCode otp = new OtpCode("+998901234567", "654321", LocalDateTime.now().plusMinutes(2));
        otp.setId(9L);
        when(otpRepository.findTop50ByCreatedAtAfterOrderByCreatedAtDesc(any())).thenReturn(List.of(otp));

        ResponseEntity<?> response = controller.otpMonitor(user(1L, Role.ADMIN));

        assertEquals(200, response.getStatusCode().value());
        List<?> rows = (List<?>) response.getBody();
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertEquals("***67", row.get("phone"));
        assertNull(row.get("code"));
        assertFalse(row.toString().contains("654321"));
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
