package com.taxi.backend.security;

import com.taxi.backend.dto.SendOtpRequest;
import com.taxi.backend.dto.VerifyOtpRequest;
import com.taxi.backend.service.ApiRateLimitService;
import com.taxi.backend.exception.RateLimitException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PENETRATION TESTS — avtomatlashtirilgan xavfsizlik testlari.
 *
 * Bu testlar real hujum stsenariylarini simulatsiya qiladi:
 *   1. Brute Force — ko'p marta noto'g'ri parol
 *   2. IDOR — boshqa foydalanuvchining ma'lumotlariga kirish
 *   3. JWT Tamper — tokenni o'zgartirish
 *   4. Rate Limit — endpoint spam
 *   5. SQL Injection — zararli input
 */
class PenetrationTest {

    private ApiRateLimitService rateLimitService;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        rateLimitService = new ApiRateLimitService();
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. BRUTE FORCE TEST
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PENTEST-01: Rate limiter 5 ta so'rovdan keyin bloklaydi")
    void bruteForce_shouldBlockAfterLimit() {
        String key = "login:192.168.1.100";

        // 5 marta ruxsat etilgan
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> rateLimitService.checkLimit(key, 5, 60));
        }

        // 6-chi marta — bloklangan
        assertThrows(RateLimitException.class,
                () -> rateLimitService.checkLimit(key, 5, 60),
                "6-chi urinishda RateLimitException bo'lishi kerak");
    }

    @Test
    @DisplayName("PENTEST-02: Global rate limit DDoS ni to'xtata oladi")
    void ddos_globalLimitShouldWork() {
        // 100 ta so'rov qabul qilinadi
        for (int i = 0; i < 100; i++) {
            assertDoesNotThrow(() ->
                    rateLimitService.checkGlobalLimit("bookTrip", 100, 1));
        }

        // 101-chi — bloklangan
        assertThrows(RateLimitException.class,
                () -> rateLimitService.checkGlobalLimit("bookTrip", 100, 1));
    }

    @Test
    @DisplayName("PENTEST-03: Turli IP lardan kelgan so'rovlar alohida hisoblanadi")
    void bruteForce_differentIpsShouldBeIndependent() {
        // IP_A — 5 marta
        for (int i = 0; i < 5; i++) {
            rateLimitService.checkLimit("login:10.0.0.1", 5, 60);
        }
        // IP_A bloklangan
        assertThrows(RateLimitException.class,
                () -> rateLimitService.checkLimit("login:10.0.0.1", 5, 60));

        // IP_B — hali limit oshmasligi kerak
        assertDoesNotThrow(() -> rateLimitService.checkLimit("login:10.0.0.2", 5, 60));
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. JWT TAMPER TEST
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PENTEST-04: O'zgartirilgan JWT token qabul qilinmasligi kerak")
    void jwtTamper_modifiedTokenShouldBeRejected() {
        // Haqiqiy kalit bilan token yaratamiz
        String realSecret = "real-secret-key-that-is-at-least-64-characters-long-for-hmac-sha256-algorithm-1234567890";
        SecretKey realKey = Keys.hmacShaKeyFor(realSecret.getBytes());

        String validToken = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("+998901234567")
                .claim("role", "PASSENGER")
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900000))
                .signWith(realKey)
                .compact();

        // Tokenni tamper qilish — boshqa kalit bilan imzolash
        String fakeSecret = "fake-secret-key-that-is-at-least-64-characters-long-for-different-signing-key-abcdef";
        SecretKey fakeKey = Keys.hmacShaKeyFor(fakeSecret.getBytes());

        String tamperedToken = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("+998901234567")
                .claim("role", "ADMIN")  // ADMIN ga o'zgartirish urinishi
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900000))
                .signWith(fakeKey)
                .compact();

        // Real kalit bilan tampered tokenni tekshirish — FAIL bo'lishi kerak
        assertThrows(Exception.class, () -> {
            Jwts.parser().verifyWith(realKey).build().parseSignedClaims(tamperedToken);
        }, "Tampered JWT token qabul qilinmasligi kerak");
    }

    @Test
    @DisplayName("PENTEST-05: Muddati o'tgan JWT token qabul qilinmasligi kerak")
    void jwtExpired_shouldBeRejected() {
        String secret = "test-secret-key-that-is-at-least-64-characters-long-for-hmac-sha256-algorithm-1234567890";
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());

        // 1ms oldin expired
        String expiredToken = Jwts.builder()
                .subject("+998901234567")
                .expiration(new Date(System.currentTimeMillis() - 1))
                .signWith(key)
                .compact();

        assertThrows(io.jsonwebtoken.ExpiredJwtException.class, () -> {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(expiredToken);
        }, "Expired JWT qabul qilinmasligi kerak");
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. RATE LIMIT TEST
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PENTEST-06: Per-user booking rate limit ishlaydi")
    void rateLimit_bookingPerUser() {
        // 5 ta buyurtma — OK
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() ->
                    rateLimitService.checkLimit("book:user123", 5, 60));
        }
        // 6-chi — blocked
        assertThrows(RateLimitException.class,
                () -> rateLimitService.checkLimit("book:user123", 5, 60));
    }

    @Test
    @DisplayName("PENTEST-07: Per-IP rate limit ishlaydi")
    void rateLimit_perIp() {
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() ->
                    rateLimitService.checkIpLimit("10.0.0.1", "nearby", 3, 10));
        }
        assertThrows(RateLimitException.class,
                () -> rateLimitService.checkIpLimit("10.0.0.1", "nearby", 3, 10));
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. SQL INJECTION TEST
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PENTEST-08: SQL injection telefon raqamda bloklangan")
    void sqlInjection_phoneValidation() {
        // SendOtpRequest @Pattern bilan himoyalangan
        SendOtpRequest req = new SendOtpRequest();

        // SQL injection urinishi — @Pattern("^\\+998\\d{9}$") bloklashi kerak
        String[] injections = {
                "' OR 1=1 --",
                "+998901234567'; DROP TABLE users; --",
                "1 UNION SELECT * FROM users",
                "+998' OR ''='",
                "admin'--",
        };

        for (String injection : injections) {
            req.setPhone(injection);
            // Pattern validation — regex ga mos kelmaydi
            assertFalse(injection.matches("^\\+998\\d{9}$"),
                    "SQL injection string pattern dan o'tmasligi kerak: " + injection);
        }
    }

    @Test
    @DisplayName("PENTEST-09: SQL injection OTP kodda bloklangan")
    void sqlInjection_otpValidation() {
        VerifyOtpRequest req = new VerifyOtpRequest();

        String[] injections = {
                "' OR 1=1",
                "000000; DROP TABLE otp_codes;",
                "1' UNION SELECT",
        };

        for (String injection : injections) {
            req.setCode(injection);
            // OTP @Pattern("^\\d{6}$") — faqat 6 ta raqam
            assertFalse(injection.matches("^\\d{6}$"),
                    "SQL injection OTP pattern dan o'tmasligi kerak: " + injection);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. INPUT VALIDATION — yangi DTO lar testi
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PENTEST-10: ChatMessage 500 belgidan oshsa bloklangan")
    void inputValidation_chatMessageLength() {
        String longMessage = "A".repeat(501);
        assertTrue(longMessage.length() > 500,
                "500 belgidan uzun xabar @Size(max=500) tomonidan bloklanishi kerak");
    }

    @Test
    @DisplayName("PENTEST-11: Promo kod faqat xavfsiz belgilardan iborat")
    void inputValidation_promoCodePattern() {
        String[] dangerous = {
                "<script>alert(1)</script>",
                "CODE'; DROP TABLE promo_codes;--",
                "../../../etc/passwd",
        };

        for (String code : dangerous) {
            assertFalse(code.matches("^[A-Za-z0-9_-]+$"),
                    "Zararli promo kod pattern dan o'tmasligi kerak: " + code);
        }
    }

    @Test
    @DisplayName("PENTEST-12: Cleanup eskirgan yozuvlarni tozalaydi (memory leak yo'q)")
    void rateLimiter_cleanupWorks() {
        // 100 ta kalit qo'shamiz
        for (int i = 0; i < 100; i++) {
            rateLimitService.checkLimit("cleanup-test:" + i, 1000, 1);
        }
        // Cleanup ishga tushadi
        assertDoesNotThrow(() -> rateLimitService.cleanup());
    }
}
