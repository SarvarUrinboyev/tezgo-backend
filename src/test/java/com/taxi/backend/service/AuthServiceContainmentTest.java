package com.taxi.backend.service;

import com.taxi.backend.enums.Role;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.DriverServiceRepository;
import com.taxi.backend.repository.OtpRepository;
import com.taxi.backend.repository.UserRepository;
import com.taxi.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceContainmentTest {

    @Mock private UserRepository userRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private OtpRepository otpRepository;
    @Mock private DriverServiceRepository driverServiceRepository;
    @Mock private JwtService jwtService;
    @Mock private SmsService smsService;
    @Mock private ConsentLogService consentLogService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, driverRepository, otpRepository,
                driverServiceRepository, jwtService, smsService, consentLogService);
        ReflectionTestUtils.setField(service, "smsEnabled", true);
        ReflectionTestUtils.setField(service, "activeProfile", "prod");
    }

    @Test
    void serviceRejectsPrivilegedRoleEvenWhenCalledOutsideController() {
        assertThrows(ResponseStatusException.class,
                () -> service.verifyOtp("caller", "654321", Role.OPERATOR));
        assertThrows(ResponseStatusException.class,
                () -> service.verifyOtp("caller", "654321", Role.ADMIN));
        verifyNoInteractions(otpRepository, userRepository);
    }

    @Test
    void nonDriverCannotRegisterDriverProfile() {
        User user = user(7L, Role.PASSENGER);
        when(userRepository.findByPhone("owner")).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.registerDriver("owner", "Name", null, null, null, null,
                        "Model", "01A123BC", null, 2020, "AAA1234567"));

        assertEquals(403, ex.getStatusCode().value());
        verifyNoInteractions(driverRepository);
    }

    @Test
    void replayedDriverRegistrationIsRejectedBeforeMutation() {
        User user = user(7L, Role.DRIVER);
        when(userRepository.findByPhone("owner")).thenReturn(Optional.of(user));
        when(driverRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.of(new Driver()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.registerDriver("owner", "Name", null, null, null, null,
                        "Model", "01A123BC", null, 2020, "AAA1234567"));

        assertEquals(409, ex.getStatusCode().value());
        verify(userRepository, never()).save(any(User.class));
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setPhone("owner");
        user.setRole(role);
        return user;
    }
}
