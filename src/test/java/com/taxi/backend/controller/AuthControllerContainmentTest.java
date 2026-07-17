package com.taxi.backend.controller;

import com.taxi.backend.dto.RegisterDriverRequest;
import com.taxi.backend.dto.VerifyOtpRequest;
import com.taxi.backend.dto.VerifyPassportRequest;
import com.taxi.backend.dto.VerifyVehicleRequest;
import com.taxi.backend.enums.Role;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.UserRepository;
import com.taxi.backend.security.JwtService;
import com.taxi.backend.service.AuthService;
import com.taxi.backend.service.GovApiService;
import com.taxi.backend.service.OtpRateLimitService;
import com.taxi.backend.service.OtpVerifyRateLimitService;
import com.taxi.backend.service.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerContainmentTest {

    @Mock private AuthService authService;
    @Mock private OtpRateLimitService otpRateLimitService;
    @Mock private GovApiService govApiService;
    @Mock private JwtService jwtService;
    @Mock private TokenBlacklistService blacklistService;
    @Mock private OtpVerifyRateLimitService verifyRateLimitService;
    @Mock private UserRepository userRepository;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService, otpRateLimitService, govApiService,
                jwtService, blacklistService, verifyRateLimitService, userRepository);
    }

    @Test
    void publicOtpCannotSelectPrivilegedRoles() {
        for (String requestedRole : new String[]{"ADMIN", "OPERATOR"}) {
            VerifyOtpRequest request = new VerifyOtpRequest();
            request.setPhone("caller");
            request.setCode("654321");
            request.setRole(requestedRole);

            HttpServletRequest httpRequest = mock(HttpServletRequest.class);
            when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
            ResponseEntity<?> response = controller.verifyOtp(request, httpRequest);

            assertEquals(403, response.getStatusCode().value());
        }
        verifyNoInteractions(authService);
    }

    @Test
    void driverRegistrationUsesAuthenticatedOwnerAndIgnoresBodyPhone() {
        RegisterDriverRequest request = new RegisterDriverRequest();
        request.setPhone("another-user");
        request.setName("Driver");
        request.setCarModel("Model");
        request.setCarNumber("01A123BC");
        User owner = user(7L, "owner", Role.DRIVER);
        doReturn(Map.of("message", "ok")).when(authService).registerDriver(
                eq("owner"), eq("Driver"), nullable(String.class), nullable(String.class),
                nullable(String.class), nullable(String.class), eq("Model"), eq("01A123BC"),
                nullable(String.class), nullable(Integer.class), nullable(String.class));

        ResponseEntity<?> response = controller.registerDriver(owner, request);

        assertEquals(200, response.getStatusCode().value());
        verify(authService).registerDriver(eq("owner"), eq("Driver"), any(), any(), any(), any(),
                eq("Model"), eq("01A123BC"), any(), any(), any());
    }

    @Test
    void anonymousDriverRegistrationIsDenied() {
        ResponseEntity<?> response = controller.registerDriver(null, new RegisterDriverRequest());
        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(authService);
    }

    @Test
    void passportResponseIsBoundToDriverAndOmitsPinfl() {
        VerifyPassportRequest request = new VerifyPassportRequest();
        request.setSeries("AA");
        request.setNumber("1234567");
        request.setBirthDate("15.03.1990");
        Map<String, Object> provider = new HashMap<>();
        provider.put("fullName", "Driver");
        provider.put("birthDate", "15.03.1990");
        provider.put("address", "Address");
        provider.put("passportSeries", "AA");
        provider.put("passportNumber", "1234567");
        provider.put("pinfl", "restricted");
        when(govApiService.lookupPassport(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(provider);

        ResponseEntity<?> response = controller.verifyPassport(user(1L, "driver", Role.DRIVER), request);

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertFalse(body.containsKey("pinfl"));
        assertEquals("Driver", body.get("fullName"));
    }

    @Test
    void vehicleResponseOmitsOwnerPiiAndDoesNotUseFixtureFallback() {
        VerifyVehicleRequest request = new VerifyVehicleRequest();
        request.setTechPassport("AAA1234567");
        request.setPlateNumber("01A123BC");
        Map<String, Object> provider = new HashMap<>();
        provider.put("carModel", "Model");
        provider.put("carYear", 2020);
        provider.put("carNumber", "01A123BC");
        provider.put("techPassportNumber", "AAA1234567");
        provider.put("ownerFullName", "restricted");
        provider.put("ownerPinfl", "restricted");
        when(govApiService.lookupVehicleKapital(anyString(), anyString(), anyString())).thenReturn(provider);

        ResponseEntity<?> response = controller.verifyVehicle(user(1L, "driver", Role.DRIVER), request);

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertFalse(body.containsKey("ownerFullName"));
        assertFalse(body.containsKey("ownerPinfl"));
        verify(govApiService, never()).lookupVehicle(anyString());
    }

    @Test
    void blacklistedRefreshCannotMintAccessToken() {
        com.taxi.backend.dto.RefreshTokenRequest request = new com.taxi.backend.dto.RefreshTokenRequest();
        request.setRefreshToken("refresh");
        when(jwtService.isValid("refresh")).thenReturn(true);
        when(jwtService.extractType("refresh")).thenReturn("refresh");
        when(jwtService.extractJti("refresh")).thenReturn("revoked-jti");
        when(blacklistService.isBlacklisted("revoked-jti")).thenReturn(true);

        ResponseEntity<?> response = controller.refreshToken(request);

        assertEquals(401, response.getStatusCode().value());
        verify(jwtService, never()).generateToken(anyString(), anyString());
    }

    private User user(Long id, String phone, Role role) {
        User user = new User();
        user.setId(id);
        user.setPhone(phone);
        user.setRole(role);
        return user;
    }
}
