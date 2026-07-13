package com.taxi.backend.controller;

import com.taxi.backend.model.User;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * To'lov tizimi endpointlari.
 *
 * POST /api/payment/create          — Driver: to'lov orderi yaratish (JWT kerak)
 * POST /api/payment/payme           — Payme JSON-RPC callback (Payme serveri chaqiradi, public)
 * POST /api/payment/click/prepare   — Click prepare callback (public)
 * POST /api/payment/click/complete  — Click complete callback (public)
 */
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentService paymentService;
    private final DriverRepository driverRepository;

    public PaymentController(PaymentService paymentService, DriverRepository driverRepository) {
        this.paymentService = paymentService;
        this.driverRepository = driverRepository;
    }

    /**
     * Haydovchi to'lov orderi yaratadi.
     * Body: { "amount": 5000000 }  — tiyinda (50,000 UZS = 5,000,000 tiyin)
     * Response: { "orderId", "paymeUrl", "clickUrl", "amount", "amountUzs" }
     */
    @PostMapping("/create")
    public ResponseEntity<?> createOrder(@AuthenticationPrincipal User user,
                                         @Valid @RequestBody com.taxi.backend.dto.TopupRequest req) {
        try {
            if (req.getAmount() < 100_000L) {
                return ResponseEntity.badRequest().body(Map.of("error", "Minimal summa 1000 so'm"));
            }
            var driver = driverRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
            return ResponseEntity.ok(paymentService.createOrder(driver.getId(), req.getAmount()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Authenticated driver-only status source for the app after a Click redirect. */
    @GetMapping("/click/orders/{orderId}")
    public ResponseEntity<?> clickOrderStatus(@AuthenticationPrincipal User user,
                                               @PathVariable String orderId) {
        var driver = driverRepository.findByUserId(user.getId())
                .orElse(null);
        if (driver == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            return ResponseEntity.ok(paymentService.getClickOrderStatus(driver.getId(), orderId));
        } catch (java.util.NoSuchElementException e) {
            // Do not disclose whether an order belongs to another driver.
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Payme JSON-RPC 2.0 callback.
     * Payme serveri bu endpointni chaqiradi: Basic auth orqali.
     * SecurityConfig da permitAll qilingan.
     */
    @PostMapping("/payme")
    public ResponseEntity<?> paymeCallback(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(paymentService.handlePayme(auth, body));
    }

    /**
     * Click prepare callback.
     * Click serveri form-data POST yuboradi.
     */
    @PostMapping("/click/prepare")
    public ResponseEntity<?> clickPrepare(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(paymentService.handleClickPrepare(params));
    }

    /**
     * Click complete callback.
     * Click serveri form-data POST yuboradi.
     */
    @PostMapping("/click/complete")
    public ResponseEntity<?> clickComplete(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(paymentService.handleClickComplete(params));
    }
}
