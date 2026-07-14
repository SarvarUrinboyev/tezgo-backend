package com.taxi.backend.controller;

import com.taxi.backend.dto.ClickGetInfoRequest;
import com.taxi.backend.service.ClickGetInfoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Click SuperApp's strictly read-only account lookup.
 *
 * <p>Only {@code /getinfo} remains exposed under this route. Catalog money
 * callbacks deliberately stay on PaymentController's established
 * {@code /api/payment/click/prepare} and {@code /api/payment/click/complete}
 * routes, so the obsolete AdvancedShop credit engine cannot be selected.
 */
@RestController
@RequestMapping("/api/payment/click-shop")
public class AdvancedShopController {

    private final ClickGetInfoService clickGetInfoService;

    public AdvancedShopController(ClickGetInfoService clickGetInfoService) {
        this.clickGetInfoService = clickGetInfoService;
    }

    @PostMapping(value = "/getinfo", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getinfo(@RequestBody ClickGetInfoRequest body,
                                                        @RequestHeader(value = "Authorization", required = false) String authorization,
                                                        HttpServletRequest request) {
        // Click has confirmed that business-level GetInfo failures use HTTP 200
        // with error/error_note. Transport and authentication rejection details
        // remain an external contract-acceptance gate while catalog mode is OFF.
        return ResponseEntity.ok(clickGetInfoService.handle(body, authorization, request.getRemoteAddr()));
    }
}
