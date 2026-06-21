package com.taxi.backend.service;

import com.taxi.backend.exception.RateLimitException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTP TEKSHIRISH uchun qat'iy rate limiter.
 * Brute-force hujumlarni bloklash:
 * - 15 daqiqada 5 ta urinish (telefon raqamiga)
 * - 15 daqiqada 30 ta urinish (IP manzilga — global)
 *
 * 6 xonali OTP = 1,000,000 variant
 * 5 urinish/15 daqiqa = kuniga max 480 urinish
 * Brute-force uchun ~5.7 yil kerak bo'ladi
 */
@Service
public class OtpVerifyRateLimitService {

    @Value("${otp.verify.rate-limit.max-requests:5}")
    private int maxRequests;

    @Value("${otp.verify.rate-limit.window-minutes:15}")
    private int windowMinutes;

    @Value("${otp.verify.rate-limit.ip-max-requests:30}")
    private int ipMaxRequests;

    // phone → oxirgi urinishlar vaqti
    private final ConcurrentHashMap<String, Deque<Instant>> verifyLog = new ConcurrentHashMap<>();

    // IP → oxirgi urinishlar vaqti (global IP-based rate limiting)
    private final ConcurrentHashMap<String, Deque<Instant>> ipLog = new ConcurrentHashMap<>();

    // Xotira tozalash uchun oxirgi vaqt
    private volatile long lastCleanup = System.currentTimeMillis();
    private static final long CLEANUP_INTERVAL_MS = 300_000; // 5 daqiqa

    /**
     * OTP tekshirish urinishini cheklash — telefon + IP bo'yicha.
     * @param phone — tekshirilayotgan telefon raqam
     * @param clientIp — so'rov yuborgan IP manzil
     * @throws RateLimitException agar limit oshgan bo'lsa
     */
    public void checkLimit(String phone, String clientIp) {
        periodicCleanup();
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(windowMinutes * 60L);

        // 1. Telefon raqam bo'yicha limit
        checkSlidingWindow(verifyLog, phone, maxRequests, windowStart, now,
                "Juda ko'p noto'g'ri urinish. " + windowMinutes + " daqiqadan keyin qayta urinib ko'ring.");

        // 2. IP manzil bo'yicha global limit
        if (clientIp != null && !clientIp.isBlank()) {
            checkSlidingWindow(ipLog, clientIp, ipMaxRequests, windowStart, now,
                    "Juda ko'p so'rov. Keyinroq qayta urinib ko'ring.");
        }
    }

    /** Backward compatibility: faqat phone bilan chaqirish */
    public void checkLimit(String phone) {
        checkLimit(phone, null);
    }

    private void checkSlidingWindow(ConcurrentHashMap<String, Deque<Instant>> log,
                                     String key, int max, Instant windowStart, Instant now, String errorMsg) {
        log.compute(key, (k, deque) -> {
            if (deque == null) deque = new ArrayDeque<>();
            while (!deque.isEmpty() && deque.peekFirst().isBefore(windowStart)) {
                deque.pollFirst();
            }
            if (deque.size() >= max) {
                throw new RateLimitException(errorMsg);
            }
            deque.addLast(now);
            return deque;
        });
    }

    /** Muvaffaqiyatli verify dan keyin log ni tozalash */
    public void clearLimit(String phone) {
        verifyLog.remove(phone);
    }

    /** Xotira leak oldini olish — eskirgan yozuvlarni vaqti-vaqti bilan tozalash */
    private void periodicCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < CLEANUP_INTERVAL_MS) return;
        lastCleanup = now;

        Instant cutoff = Instant.now().minusSeconds(windowMinutes * 60L);
        cleanupMap(verifyLog, cutoff);
        cleanupMap(ipLog, cutoff);
    }

    private void cleanupMap(ConcurrentHashMap<String, Deque<Instant>> map, Instant cutoff) {
        map.entrySet().removeIf(entry -> {
            Deque<Instant> deque = entry.getValue();
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            return deque.isEmpty();
        });
    }
}
