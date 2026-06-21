package com.taxi.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret",
            "test-jwt-secret-key-that-is-at-least-64-characters-long-for-hmac-sha256-algorithm");
        ReflectionTestUtils.setField(jwtService, "expiration", 86400000L);
    }

    @Test
    void generateToken_shouldCreateValidAccessToken() {
        String token = jwtService.generateToken("+998901234567", "DRIVER");
        assertNotNull(token);
        assertTrue(jwtService.isValid(token));
        assertEquals("access", jwtService.extractType(token));
        assertEquals("+998901234567", jwtService.extractPhone(token));
        assertEquals("DRIVER", jwtService.extractRole(token));
    }

    @Test
    void generateRefreshToken_shouldHaveRefreshType() {
        String token = jwtService.generateRefreshToken("+998901234567", "PASSENGER");
        assertNotNull(token);
        assertEquals("refresh", jwtService.extractType(token));
    }

    @Test
    void extractJti_shouldReturnUniqueId() {
        String token1 = jwtService.generateToken("+998901234567", "DRIVER");
        String token2 = jwtService.generateToken("+998901234567", "DRIVER");
        String jti1 = jwtService.extractJti(token1);
        String jti2 = jwtService.extractJti(token2);
        assertNotNull(jti1);
        assertNotNull(jti2);
        assertNotEquals(jti1, jti2);
    }

    @Test
    void isValid_shouldReturnFalseForTamperedToken() {
        String token = jwtService.generateToken("+998901234567", "DRIVER");
        assertFalse(jwtService.isValid(token + "tampered"));
    }

    @Test
    void extractExpiration_shouldBeInFuture() {
        String token = jwtService.generateToken("+998901234567", "DRIVER");
        var exp = jwtService.extractExpiration(token);
        assertNotNull(exp);
        assertTrue(exp.getTime() > System.currentTimeMillis());
    }
}
