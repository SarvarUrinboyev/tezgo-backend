package com.taxi.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    /** Access token: 30 kun */
    @Value("${jwt.access.expiration:2592000000}")
    private long accessExpiration = 2592000000L; // 30 days

    /** Refresh token: 7 kun */
    @Value("${jwt.refresh.expiration:604800000}")
    private long refreshExpiration = 604800000L; // 7 days

    /** Backward compat — eski config dan fallback */
    @Value("${jwt.expiration:2592000000}")
    private long expiration;

    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.length() < 64) {
            throw new IllegalStateException(
                "JWT_SECRET kamida 64 belgidan iborat bo'lishi kerak! " +
                "Yarating: openssl rand -base64 64");
        }
        // Faqat eski default kalitni rad etish
        if ("tezyol-dev-secret-key-minimum-64-characters-long-for-hmac-sha256".equals(secret)) {
            throw new IllegalStateException(
                "Default JWT_SECRET ishlatilmoqda — production uchun o'zgartiring! " +
                "Yarating: openssl rand -base64 64");
        }
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /** Access token — 30 kun */
    public String generateToken(String phone, String role) {
        long exp = accessExpiration > 0 ? accessExpiration : expiration;
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(phone)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + exp))
                .signWith(getKey())
                .compact();
    }

    /** Refresh token — 7 kun */
    public String generateRefreshToken(String phone, String role) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(phone)
                .claim("role", role)
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getKey())
                .compact();
    }

    /** Token JTI (unique ID) */
    public String extractJti(String token) {
        try {
            return Jwts.parser().verifyWith(getKey()).build()
                    .parseSignedClaims(token).getPayload().getId();
        } catch (Exception e) { return null; }
    }

    /** Token expiration vaqti */
    public Date extractExpiration(String token) {
        try {
            return Jwts.parser().verifyWith(getKey()).build()
                    .parseSignedClaims(token).getPayload().getExpiration();
        } catch (Exception e) { return null; }
    }

    /** Token type (access/refresh) */
    public String extractType(String token) {
        try {
            Object type = Jwts.parser().verifyWith(getKey()).build()
                    .parseSignedClaims(token).getPayload().get("type");
            return type != null ? type.toString() : "access";
        } catch (Exception e) { return "access"; }
    }

    public String extractPhone(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public String extractRole(String token) {
        return (String) Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role");
    }

    /**
     * Token validatsiyasi.
     * ExpiredJwtException ni qayta tashlaydi (JwtFilter aniq 401 qaytaradi).
     * Boshqa xatolar → false.
     */
    public boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token);
            return true;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            throw e; // JwtFilter aniq 401 qaytarishi uchun
        } catch (JwtException e) {
            return false;
        }
    }
}
