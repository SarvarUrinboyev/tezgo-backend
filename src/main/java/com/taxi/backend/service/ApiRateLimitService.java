package com.taxi.backend.service;

import com.taxi.backend.exception.RateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API Rate Limiting — ko'p qatlamli himoya.
 *
 * QATLAMLAR:
 *   1. Per-User — foydalanuvchi ID bo'yicha (asosiy)
 *   2. Per-IP — IP manzil bo'yicha (anonim/botnet himoyasi)
 *   3. Global — butun endpoint bo'yicha (DDoS himoyasi)
 *
 * IP-SPOOFING HIMOYASI:
 *   - X-Forwarded-For headerini ishlatamiz, lekin IP spoofing to'liq bu darajada
 *     hal bo'lmaydi — API Gateway (Nginx/CloudFlare) darajasida real IP aniqlash kerak.
 *   - Bu servis "application-level" himoya — "network-level" himoya emas.
 *
 * BOTNET HIMOYASI:
 *   - Per-user limit → har bir akkaunt alohida cheklanadi
 *   - Global endpoint limit → umumiy so'rovlar soni cheklanadi
 *   - To'liq himoya uchun: CloudFlare WAF / AWS WAF / Nginx rate_limit kerak
 *
 * PRODUCTION TAVSIYA:
 *   Nginx config: limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
 *   CloudFlare: Rate Limiting Rule → 100 req/min per IP
 */
@Service
public class ApiRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(ApiRateLimitService.class);

    private final ConcurrentHashMap<String, Deque<Instant>> requestLog = new ConcurrentHashMap<>();

    // Global endpoint limitlari (DDoS himoyasi)
    private final ConcurrentHashMap<String, Deque<Instant>> globalLog = new ConcurrentHashMap<>();

    /**
     * Per-user rate limit tekshiruvi.
     *
     * @param key          Unikal kalit (masalan: "book:123")
     * @param maxRequests  Ruxsat etilgan so'rovlar soni
     * @param windowSeconds Vaqt oynasi (soniya)
     */
    public void checkLimit(String key, int maxRequests, int windowSeconds) {
        doCheck(requestLog, key, maxRequests, windowSeconds);
    }

    /**
     * Per-IP rate limit — anonim so'rovlar va botnet himoyasi.
     * Controller dan clientIp bilan chaqiriladi.
     */
    public void checkIpLimit(String ip, String endpoint, int maxRequests, int windowSeconds) {
        String key = "ip:" + ip + ":" + endpoint;
        doCheck(requestLog, key, maxRequests, windowSeconds);
    }

    /**
     * Global endpoint limit — butun tizim uchun (DDoS himoyasi).
     * Masalan: bookTrip — 1 soniyada max 100 ta (barcha foydalanuvchilardan).
     */
    public void checkGlobalLimit(String endpoint, int maxRequests, int windowSeconds) {
        doCheck(globalLog, "global:" + endpoint, maxRequests, windowSeconds);
    }

    private void doCheck(ConcurrentHashMap<String, Deque<Instant>> log,
                         String key, int maxRequests, int windowSeconds) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(windowSeconds);

        Deque<Instant> timestamps = log.computeIfAbsent(key, k -> new LinkedList<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= maxRequests) {
                ApiRateLimitService.log.warn("[RATE-LIMIT] key={}, count={}/{}, window={}s",
                        key, timestamps.size(), maxRequests, windowSeconds);
                throw new RateLimitException(
                        "So'rovlar soni chegaradan oshdi. " + windowSeconds + " soniyadan keyin qayta urining.");
            }

            timestamps.addLast(now);
        }
    }

    /** Eskirgan yozuvlarni tozalash — har 5 daqiqada */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300000)
    public void cleanup() {
        Instant cutoff = Instant.now().minusSeconds(600);
        cleanupMap(requestLog, cutoff);
        cleanupMap(globalLog, cutoff);
    }

    private void cleanupMap(ConcurrentHashMap<String, Deque<Instant>> map, Instant cutoff) {
        map.entrySet().removeIf(entry -> {
            Deque<Instant> deque = entry.getValue();
            synchronized (deque) {
                deque.removeIf(ts -> ts.isBefore(cutoff));
                return deque.isEmpty();
            }
        });
    }
}
