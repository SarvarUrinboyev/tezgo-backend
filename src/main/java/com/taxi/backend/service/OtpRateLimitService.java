package com.taxi.backend.service;

import com.taxi.backend.exception.RateLimitException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTP so'rovlari uchun in-memory rate limiter.
 * Har bir telefon raqamiga: N daqiqada M ta so'rovdan ko'p bo'lmaydi.
 * Xotira leak oldini olish uchun davriy tozalash mavjud.
 */
@Service
public class OtpRateLimitService {

    @Value("${otp.rate-limit.max-requests:5}")
    private int maxRequests;

    @Value("${otp.rate-limit.window-minutes:15}")
    private int windowMinutes;

    // phone → oxirgi so'rovlar vaqti
    private final ConcurrentHashMap<String, Deque<Instant>> requestLog = new ConcurrentHashMap<>();

    // Xotira tozalash uchun oxirgi vaqt
    private volatile long lastCleanup = System.currentTimeMillis();
    private static final long CLEANUP_INTERVAL_MS = 300_000; // 5 daqiqa

    public void checkLimit(String phone) {
        periodicCleanup();
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(windowMinutes * 60L);

        requestLog.compute(phone, (key, deque) -> {
            if (deque == null) deque = new ArrayDeque<>();

            // Oyna tashqarisidagi eskilarni o'chirish
            while (!deque.isEmpty() && deque.peekFirst().isBefore(windowStart)) {
                deque.pollFirst();
            }

            if (deque.size() >= maxRequests) {
                throw new RateLimitException(
                        "Juda ko'p urinish. " + windowMinutes + " daqiqadan keyin qayta urinib ko'ring.");
            }

            deque.addLast(now);
            return deque;
        });
    }

    /** Xotira leak oldini olish — eskirgan yozuvlarni davriy tozalash */
    private void periodicCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < CLEANUP_INTERVAL_MS) return;
        lastCleanup = now;

        Instant cutoff = Instant.now().minusSeconds(windowMinutes * 60L);
        requestLog.entrySet().removeIf(entry -> {
            Deque<Instant> deque = entry.getValue();
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            return deque.isEmpty();
        });
    }
}
