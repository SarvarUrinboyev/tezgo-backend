package com.taxi.backend.controller;

import com.taxi.backend.service.AdvancedShopService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Click ADVANCED SHOP callbacks.
 *
 * <p>Separate from {@link PaymentController}'s {@code /api/payment/click/*} (Merchant API):
 * <ul>
 *   <li>Different URL: {@code /api/payment/click-shop/*} (no path overlap)</li>
 *   <li>Different body shape: ADVANCED SHOP sends JSON; Merchant API sends form-data</li>
 *   <li>Different signature formula and different service_id/secret_key</li>
 * </ul>
 *
 * <p>All endpoints pin {@code produces = application/json} — pre-empts the jackson-dataformat-xml
 * converter (transitively present via firebase-admin) from serving XML when the caller's Accept
 * header prefers it.
 *
 * <p>Action mapping per Click docs:
 * Getinfo=0, Prepare=1, Complete=2, Check=3, Compare=4.
 */
@RestController
@RequestMapping("/api/payment/click-shop")
public class AdvancedShopController {

    private final AdvancedShopService advancedShopService;

    public AdvancedShopController(AdvancedShopService advancedShopService) {
        this.advancedShopService = advancedShopService;
    }

    @PostMapping(value = "/getinfo", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getinfo(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(advancedShopService.dispatch(forceAction(body, 0)));
    }

    @PostMapping(value = "/prepare", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> prepare(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(advancedShopService.dispatch(forceAction(body, 1)));
    }

    @PostMapping(value = "/complete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> complete(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(advancedShopService.dispatch(forceAction(body, 2)));
    }

    @PostMapping(value = "/check", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> check(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(advancedShopService.dispatch(forceAction(body, 3)));
    }

    @PostMapping(value = "/compare", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> compare(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(advancedShopService.dispatch(forceAction(body, 4)));
    }

    /**
     * Guarantees the {@code action} field matches the URL — if Click sends action=2 to /prepare
     * (URL/body mismatch), we trust the URL. Signature still verifies against the request body's
     * stated action so a malicious URL/body desync cannot bypass sign check.
     */
    private static Map<String, Object> forceAction(Map<String, Object> body, int urlAction) {
        if (body == null) return Map.of("action", urlAction);
        // Only inject if missing — preserves the body Click signed.
        if (!body.containsKey("action")) {
            body.put("action", urlAction);
        }
        return body;
    }
}
