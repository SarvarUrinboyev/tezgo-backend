package com.taxi.backend.service;

import com.taxi.backend.enums.Role;
import com.taxi.backend.model.OtpCode;
import com.taxi.backend.model.User;
import com.taxi.backend.model.Driver;
import com.taxi.backend.repository.*;
import com.taxi.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private OtpRepository otpRepository;
    @Mock private DriverServiceRepository driverServiceRepository;
    @Mock private JwtService jwtService;
    @Mock private SmsService smsService;

    @InjectMocks private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "smsEnabled", false);
        ReflectionTestUtils.setField(authService, "activeProfile", "local");
    }

    @Test
    void sendOtp_shouldCreateAndSaveOtp() {
        String phone = "+998901234567";
        String result = authService.sendOtp(phone);
        assertEquals("OTP yuborildi", result);
        verify(otpRepository).invalidateAllByPhone(phone);
        verify(otpRepository).save(any(OtpCode.class));
    }

    @Test
    void verifyOtp_withValidCode_shouldReturnTokens() {
        String phone = "+998901234567";
        OtpCode otp = new OtpCode(phone, "654321", LocalDateTime.now().plusMinutes(5));

        when(otpRepository.findTopByPhoneAndIsUsedFalseOrderByCreatedAtDesc(phone))
                .thenReturn(Optional.of(otp));

        User user = new User();
        user.setId(1L);
        user.setPhone(phone);
        user.setRole(Role.PASSENGER);
        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(phone, "PASSENGER")).thenReturn("access-token");
        when(jwtService.generateRefreshToken(phone, "PASSENGER")).thenReturn("refresh-token");

        Map<String, Object> result = authService.verifyOtp(phone, "654321", Role.PASSENGER);
        assertEquals("access-token", result.get("token"));
        assertEquals("refresh-token", result.get("refreshToken"));
    }

    @Test
    void verifyOtp_withWrongCode_shouldThrow() {
        String phone = "+998901234567";
        OtpCode otp = new OtpCode(phone, "654321", LocalDateTime.now().plusMinutes(5));
        when(otpRepository.findTopByPhoneAndIsUsedFalseOrderByCreatedAtDesc(phone))
                .thenReturn(Optional.of(otp));

        assertThrows(RuntimeException.class,
                () -> authService.verifyOtp(phone, "000000", Role.PASSENGER));
    }

    @Test
    void verifyOtp_withExpiredCode_shouldThrow() {
        String phone = "+998901234567";
        OtpCode otp = new OtpCode(phone, "654321", LocalDateTime.now().minusMinutes(1));
        when(otpRepository.findTopByPhoneAndIsUsedFalseOrderByCreatedAtDesc(phone))
                .thenReturn(Optional.of(otp));

        assertThrows(RuntimeException.class,
                () -> authService.verifyOtp(phone, "654321", Role.PASSENGER));
    }

    @Test
    void verifyOtp_testCodeBlockedInProduction() {
        ReflectionTestUtils.setField(authService, "activeProfile", "production");
        String phone = "+998901234567";

        // Test code should NOT work in production profile even with SMS disabled
        OtpCode otp = new OtpCode(phone, "999999", LocalDateTime.now().plusMinutes(5));
        when(otpRepository.findTopByPhoneAndIsUsedFalseOrderByCreatedAtDesc(phone))
                .thenReturn(Optional.of(otp));

        assertThrows(RuntimeException.class,
                () -> authService.verifyOtp(phone, "111111", Role.PASSENGER));
    }

    @Test
    void verifyOtp_testCodeWorksInLocalProfile() {
        ReflectionTestUtils.setField(authService, "smsEnabled", false);
        ReflectionTestUtils.setField(authService, "activeProfile", "local");
        String phone = "+998901234567";

        User user = new User();
        user.setId(1L);
        user.setPhone(phone);
        user.setRole(Role.PASSENGER);
        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));
        when(otpRepository.findTopByPhoneAndIsUsedFalseOrderByCreatedAtDesc(phone))
                .thenReturn(Optional.empty());
        when(jwtService.generateToken(anyString(), anyString())).thenReturn("token");
        when(jwtService.generateRefreshToken(anyString(), anyString())).thenReturn("refresh");

        // 111111 should work in local profile
        Map<String, Object> result = authService.verifyOtp(phone, "111111", Role.PASSENGER);
        assertNotNull(result.get("token"));
    }
}
