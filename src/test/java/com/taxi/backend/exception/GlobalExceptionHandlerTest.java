package com.taxi.backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_shouldReturn404() {
        var response = handler.handleNotFound(new NotFoundException("Test not found"));
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Test not found", response.getBody().get("error"));
    }

    @Test
    void handleBusiness_shouldReturn400() {
        var response = handler.handleBusiness(new BusinessException("Bad input"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void handleForbidden_shouldReturn403() {
        var response = handler.handleForbidden(new ForbiddenException("No access"));
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void handleRateLimit_shouldReturn429() {
        var response = handler.handleRateLimit(new RateLimitException("Too many"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
    }

    @Test
    void handleRuntime_notFoundPattern_shouldReturn404() {
        var response = handler.handleRuntime(new RuntimeException("Haydovchi topilmadi"));
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void handleRuntime_otpError_shouldReturn400() {
        var response = handler.handleRuntime(new RuntimeException("OTP noto'g'ri"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void handleRuntime_notYourTrip_shouldReturn400_not500() {
        // Qayta tayinlangan/bekor qilingan trip ustida arrive/start chaqirilsa — toza 4xx, 500 emas
        var response = handler.handleRuntime(new RuntimeException("Bu siz qabul qilgan buyurtma emas"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Bu siz qabul qilgan buyurtma emas", response.getBody().get("error"));
    }

    // ── accept/claim biznes xatolari -> 4xx (500 emas) ───────────────────────

    @Test
    void handleRuntime_busy_shouldReturn400() {
        var r = handler.handleRuntime(new RuntimeException("Sizda faol buyurtma bor"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
    }

    @Test
    void handleRuntime_cooldown_shouldReturn400() {
        var r = handler.handleRuntime(new RuntimeException("Iltimos biroz kuting — yangi buyurtma tez orada"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
    }

    @Test
    void handleRuntime_negativeBalance_shouldReturn400() {
        var r = handler.handleRuntime(new RuntimeException("Balansingiz manfiy — buyurtma olish uchun to'ldiring"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
    }

    @Test
    void handleRuntime_excludedDriver_shouldReturn400() {
        var r = handler.handleRuntime(new RuntimeException("Siz bu buyurtmani bekor qildingiz — qayta qabul qila olmaysiz"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
    }

    @Test
    void handleRuntime_tariffMismatch_shouldReturn400() {
        var r = handler.handleRuntime(new RuntimeException("Bu buyurtma siz tanlagan tariflarga mos emas"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
    }

    @Test
    void handleRuntime_serviceMismatch_shouldReturn400() {
        var r = handler.handleRuntime(new RuntimeException("Bu buyurtma uchun kerakli xizmatlar sizda yoqilmagan"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
    }

    @Test
    void handleRuntime_alreadyCompleted_shouldReturn400_not500() {
        var r = handler.handleRuntime(new RuntimeException("Buyurtma allaqachon yakunlangan"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("Buyurtma allaqachon yakunlangan", r.getBody().get("error"));
    }

    @Test
    void handleRuntime_alreadyCancelled_shouldReturn400() {
        var r = handler.handleRuntime(new RuntimeException("Buyurtma allaqachon bekor qilingan"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
    }

    @Test
    void handleRuntime_illegalTransition_shouldReturn400() {
        var r = handler.handleRuntime(new RuntimeException("Buyurtma holatini bu tartibda o'zgartirib bo'lmaydi"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
    }

    @Test
    void handleRuntime_tripAlreadyTakenOnAccept_shouldReturn409() {
        var r = handler.handleRuntime(new RuntimeException("Bu buyurtma allaqachon qabul qilingan"));
        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
    }

    @Test
    void handleRuntime_tripAlreadyTakenOnClaim_shouldReturn409() {
        var r = handler.handleRuntime(new RuntimeException("Buyurtma allaqachon olindi"));
        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
    }

    @Test
    void handleRuntime_genericAllaqachon_stillReturns400_notBrokenBy409() {
        // "allaqachon qabul qilingan/olindi" emas — generic "allaqachon" hali 400 bo'lib qolishi kerak
        var r = handler.handleRuntime(new RuntimeException("Kutish allaqachon boshlangan"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
    }

    @Test
    void handleRuntime_unknownError_shouldReturn500() {
        var response = handler.handleRuntime(new RuntimeException("Some internal error xyz"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Ichki server xatosi", response.getBody().get("error"));
    }

    @Test
    void handleRuntime_nullMessage_shouldReturn500() {
        var response = handler.handleRuntime(new RuntimeException((String) null));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void handleGeneral_shouldReturn500() {
        var response = handler.handleGeneral(new Exception("unexpected"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }
}
