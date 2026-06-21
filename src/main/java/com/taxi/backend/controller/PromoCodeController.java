package com.taxi.backend.controller;

import com.taxi.backend.service.PromoCodeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Promo kod endpointlari.
 *
 * Yo'lovchi uchun:
 *   POST /api/promo/validate  { code, tripPrice } — kodni tekshirish (buyurtma berishdan oldin)
 *
 * Admin uchun:
 *   GET    /api/admin/promo         — barcha promo kodlar
 *   POST   /api/admin/promo         — yangi kod yaratish
 *   PUT    /api/admin/promo/{id}/toggle — faollashtirish/o'chirish
 *   DELETE /api/admin/promo/{id}    — o'chirish
 */
@RestController
public class PromoCodeController {

    private final PromoCodeService promoCodeService;

    public PromoCodeController(PromoCodeService promoCodeService) {
        this.promoCodeService = promoCodeService;
    }

    /** Yo'lovchi: promo kodni tekshirish */
    @PostMapping("/api/promo/validate")
    public ResponseEntity<?> validate(@Valid @RequestBody com.taxi.backend.dto.ValidatePromoRequest req) {
        return ResponseEntity.ok(promoCodeService.validate(
                req.getCode(), req.getTripPrice() != null ? req.getTripPrice() : 0L));
    }

    /** Admin: barcha promo kodlar */
    @GetMapping("/api/admin/promo")
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(promoCodeService.getAll());
    }

    /** Admin: yangi promo kod */
    @PostMapping("/api/admin/promo")
    public ResponseEntity<?> create(@Valid @RequestBody com.taxi.backend.dto.CreatePromoRequest req) {
        try {
            // DTO dan Map ga convert (backward compat — PromoCodeService.create(Map) ishlatadi)
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("code", req.getCode());
            body.put("discountPercent", req.getDiscountPercent());
            body.put("maxUses", req.getMaxUses());
            body.put("minPrice", req.getMinPrice());
            body.put("description", req.getDescription());
            return ResponseEntity.ok(promoCodeService.create(body));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Admin: faollashtirish/o'chirish */
    @PutMapping("/api/admin/promo/{id}/toggle")
    public ResponseEntity<?> toggle(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(promoCodeService.toggle(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Admin: o'chirish */
    @DeleteMapping("/api/admin/promo/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        promoCodeService.delete(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
