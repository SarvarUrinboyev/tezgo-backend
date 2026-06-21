package com.taxi.backend.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Correlation ID Filter — distributed tracing uchun asos.
 *
 * MUAMMO: Xato yuz berganda qaysi so'rov muammoni keltirib chiqarganini
 * aniqlash imkoni yo'q edi. "Logs are missing" holati.
 *
 * YECHIM: Har bir HTTP so'rovga unikal ID berish (X-Correlation-Id).
 * Bu ID barcha log yozuvlarida MDC orqali ko'rinadi.
 * Client o'zi ham yuborishi mumkin — distributed tracing uchun.
 *
 * Log formati: [correlationId=abc123] TripService: buyurtma yaratildi
 * Jaeger/Zipkin dan oldin — bu oddiy amma samarali yechim.
 */
@Component
@Order(1) // JwtFilter dan oldin ishlashi kerak
public class CorrelationIdFilter implements Filter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // Client yuborgan ID ni ishlatish yoki yangi yaratish
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString().substring(0, 8);
        }

        // MDC ga qo'yish — barcha log larda ko'rinadi
        MDC.put(MDC_KEY, correlationId);
        // Response headerga qo'shish — client debug uchun
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
