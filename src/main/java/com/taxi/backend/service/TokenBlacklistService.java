package com.taxi.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * JWT token blacklist — Redis orqali.
 * Logout qilingan tokenlar bu yerda saqlanadi.
 * TTL — tokenning qolgan muddati (auto-cleanup).
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String PREFIX = "token:blacklist:";
    private final StringRedisTemplate redis;

    // Production: Redis tushsa tokenlar RAD etiladi (fail-closed)
    // Dev: Redis yo'q bo'lsa tokenlar ishlaydi (fail-open)
    @org.springframework.beans.factory.annotation.Value("${token.blacklist.fail-closed:true}")
    private boolean failClosed;

    // Redis xatolik logini 60 sekundda 1 marta chiqarish (log spam oldini olish)
    private volatile long lastRedisWarnTime = 0;
    private static final long WARN_INTERVAL_MS = 60_000;

    public TokenBlacklistService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Tokenni blacklistga qo'shish */
    public void blacklist(String jti, long ttlSeconds) {
        if (jti == null || jti.isBlank()) return;
        try {
            redis.opsForValue().set(PREFIX + jti, "1", Duration.ofSeconds(Math.max(ttlSeconds, 1)));
        } catch (Exception e) {
            logRedisWarn("blacklist", e.getMessage());
        }
    }

    /** Token blacklistda bormi? */
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) return false;
        try {
            return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
        } catch (Exception e) {
            logRedisWarn("tekshiruv", e.getMessage());
            // fail-closed=true (production): Redis tushsa token RAD etiladi
            // fail-closed=false (dev): Redis yo'q bo'lsa token ishlaydi
            return failClosed;
        }
    }

    /** Redis xatolik logini throttle qilish — 60 sekundda 1 marta */
    private void logRedisWarn(String operation, String message) {
        long now = System.currentTimeMillis();
        if (now - lastRedisWarnTime > WARN_INTERVAL_MS) {
            lastRedisWarnTime = now;
            log.warn("[TokenBlacklist] Redis {} xatolik: {} (keyingi ogohlantirish 60s dan keyin)", operation, message);
        }
    }
}
