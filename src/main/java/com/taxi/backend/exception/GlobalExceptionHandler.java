package com.taxi.backend.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import io.sentry.Sentry;
import io.sentry.SentryLevel;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // DTO @Valid validatsiya xatolari
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        e -> e.getField(),
                        e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "Noto'g'ri",
                        (a, b) -> a));
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Validatsiya xatosi");
        body.put("status", 400);
        body.put("fields", fields);
        body.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.badRequest().body(body);
    }

    // @Validated constraint xatolari
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraint(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, msg);
    }

    // JSON parse xatolari
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "Request body noto'g'ri formatda");
    }

    // 404 — Resurs topilmadi
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // 400 — Biznes logika xatolari
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // 403 — Ruxsat yo'q (custom)
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // Biznes logika xatolari (eski kod uchun backward compat)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Kod o'zi belgilagan status (masalan 503 — SMS yuborib bo'lmadi).
    // RuntimeException handleridan ALOHIDA: o'z statusini saqlaydi, 500 ga aylanmaydi.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return error(status, ex.getReason());
    }

    // Umumiy Runtime xatolari — custom exception'lar ushlanmagan holat
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        String msg = ex.getMessage();
        if (msg == null) {
            log.error("Kutilmagan Runtime xatolik (message=null)", ex);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Ichki server xatosi");
        }

        // Biznes logika RuntimeException'larini aniqlash (backward compat)
        // Yangi kodda NotFoundException/BusinessException ishlatiladi
        if (msg.contains("topilmadi") || msg.contains("not found")) {
            return error(HttpStatus.NOT_FOUND, msg);
        }

        // 409 — race/conflict: trip allaqachon olingan/qabul qilingan (boshqa haydovchi oldin oldi).
        // Generic "allaqachon" 400 tekshiruvidan OLDIN — shu sabab aniqroq substring.
        if (msg.contains("allaqachon qabul qilingan") || msg.contains("allaqachon olindi")) {
            return error(HttpStatus.CONFLICT, msg);
        }

        if (msg.contains("OTP") || msg.contains("kerak") || msg.contains("noto'g'ri")
                || msg.contains("tugagan") || msg.contains("ishlatilgan") || msg.contains("Ruxsat")
                || msg.contains("yetarli emas") || msg.contains("bo'sh") || msg.contains("tasdiqlanmagansiz")
                || msg.contains("allaqachon") || msg.contains("Miqdor") || msg.contains("mumkin emas")
                || msg.contains("buyurtma emas")              // "Bu siz qabul qilgan buyurtma emas" (ownership)
                || msg.contains("faol buyurtma bor")          // accept: band haydovchi
                || msg.contains("biroz kuting")               // accept: rad etish cooldown'i
                || msg.contains("Balansingiz manfiy")         // accept: manfiy balans (strict >= 0)
                || msg.contains("qayta qabul qila olmaysiz")  // accept: chiqarilgan (excluded) haydovchi
                || msg.contains("tariflarga mos emas")        // accept/claim: tarif mos emas
                || msg.contains("kerakli xizmatlar sizda yoqilmagan") // accept: xizmat mos emas (service hard-filter)
                || msg.contains("allaqachon yakunlangan")     // status guard: terminal (COMPLETED) tripni qayta o'zgartirish
                || msg.contains("allaqachon bekor qilingan")  // status guard: terminal (CANCELLED) tripni qayta o'zgartirish
                || msg.contains("tartibda o'zgartirib bo'lmaydi")) { // status guard: teskari/noto'g'ri o'tish
            return error(HttpStatus.BAD_REQUEST, msg);
        }

        // Kutilmagan ichki xato — stack trace logga yoziladi
        log.error("Kutilmagan Runtime xatolik: {}", msg, ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Ichki server xatosi");
    }

    // 409 — Resurs ziddiyati (faol safar, duplikat va h.k.)
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Rate limit
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitException ex) {
        return error(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    // Ruxsat yo'q (Spring Security)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Ruxsat yo'q");
    }

    // Noto'g'ri argument turi
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "Noto'g'ri parametr: " + ex.getName());
    }

    // Kutilmagan barcha xatolar — Sentry + log
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Kutilmagan xatolik: {}", ex.getMessage(), ex);
        reportToSentry(ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Ichki server xatosi");
    }

    /** Sentry ga xato yuborish — CorrelationId bilan */
    private void reportToSentry(Exception ex) {
        try {
            Sentry.configureScope(scope -> {
                String cid = MDC.get("correlationId");
                if (cid != null) scope.setTag("correlationId", cid);
            });
            Sentry.captureException(ex);
        } catch (Exception ignored) {
            // Sentry DSN yo'q bo'lsa — xato yutiladi
        }
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        String correlationId = MDC.get("correlationId");
        Map<String, Object> body = new HashMap<>();
        body.put("error", message != null ? message : "Noma'lum xato");
        body.put("status", status.value());
        body.put("timestamp", LocalDateTime.now().toString());
        if (correlationId != null) body.put("correlationId", correlationId);
        return ResponseEntity.status(status).body(body);
    }
}
