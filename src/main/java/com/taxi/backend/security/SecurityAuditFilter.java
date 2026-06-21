package com.taxi.backend.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Security Audit Filter — network-level himoya.
 *
 * HIMOYALAR:
 *   1. User-Agent bo'sh bo'lsa → 403 (bot belgi)
 *   2. Suspicious IP tracking: 1 soatda 100+ xato → avtomatik ban
 *   3. Banned IP lar Redis da saqlanadi (multi-instance)
 *   4. Barcha 4xx/5xx javoblarni hisobga olish
 *
 * Bu filter CorrelationIdFilter dan KEYIN, JwtFilter dan OLDIN ishlaydi.
 */
@Component
@Order(2)
public class SecurityAuditFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditFilter.class);
    private static final String BAN_PREFIX = "security:banned:";
    private static final int ERROR_THRESHOLD = 100; // 100 xato/soat = ban
    private static final int BAN_DURATION_HOURS = 1;

    private final StringRedisTemplate redis;

    // In-memory error tracking (Redis ishlamasa fallback)
    private final ConcurrentHashMap<String, Deque<Instant>> errorLog = new ConcurrentHashMap<>();

    public SecurityAuditFilter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String clientIp = extractClientIp(request);
        String path = request.getRequestURI();

        // ── 1. Banned IP tekshiruvi ──
        if (isIpBanned(clientIp)) {
            log.warn("[SECURITY] Banned IP bloklandi: {} -> {}", clientIp, path);
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Kirish taqiqlangan\",\"status\":403}");
            return;
        }

        // ── 2. User-Agent tekshiruvi — bo'sh bo'lsa bot ──
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            // Health check va payment callback larni o'tkazish
            if (!path.contains("/actuator") && !path.contains("/payment/")) {
                log.warn("[SECURITY] Bo'sh User-Agent bloklandi: {} -> {}", clientIp, path);
                response.setStatus(403);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"User-Agent talab qilinadi\",\"status\":403}");
                return;
            }
        }

        // ── 3. So'rovni o'tkazish va status kodni tekshirish ──
        chain.doFilter(req, res);

        // ── 4. Error tracking — 4xx/5xx javoblar ──
        // MUHIM: 429 (rate-limit) bu hujum EMAS — so'rov allaqachon throttle qilingan,
        // uni yana banga sanash haqiqiy foydalanuvchini bloklaydi (ko'p polling = 429 → ban).
        // /ws WebSocket handshake "xato"lari ham qayta-ulanishdan kelib chiqadi (benign).
        int status = response.getStatus();
        if (status >= 400 && status != 429 && !path.startsWith("/ws")) {
            trackError(clientIp, path, status);
        }
    }

    /** Xato sonini kuzatish va chegaradan oshsa ban qilish */
    private void trackError(String ip, String path, int status) {
        Instant now = Instant.now();
        Instant oneHourAgo = now.minusSeconds(3600);

        Deque<Instant> errors = errorLog.computeIfAbsent(ip, k -> new LinkedList<>());
        synchronized (errors) {
            while (!errors.isEmpty() && errors.peekFirst().isBefore(oneHourAgo)) {
                errors.pollFirst();
            }
            errors.addLast(now);

            if (errors.size() >= ERROR_THRESHOLD) {
                banIp(ip);
                log.error("[SECURITY-BAN] IP {} avtomatik bloklandi: {} xato/soat. Oxirgi: {} -> {}",
                        ip, errors.size(), path, status);
                errors.clear();
            }
        }
    }

    private void banIp(String ip) {
        try {
            redis.opsForValue().set(BAN_PREFIX + ip, "banned",
                    BAN_DURATION_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.debug("Redis ban write xato: {}", e.getMessage());
        }
    }

    private boolean isIpBanned(String ip) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(BAN_PREFIX + ip));
        } catch (Exception e) {
            return false; // Redis ishlamasa — ban tekshirmaymiz
        }
    }

    /** Real client IP ni olish (proxy/load balancer ortida) */
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    /** Har 10 daqiqada eskirgan yozuvlarni tozalash */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 600000)
    public void cleanup() {
        Instant cutoff = Instant.now().minusSeconds(3600);
        errorLog.entrySet().removeIf(entry -> {
            Deque<Instant> deque = entry.getValue();
            synchronized (deque) {
                deque.removeIf(ts -> ts.isBefore(cutoff));
                return deque.isEmpty();
            }
        });
    }
}
