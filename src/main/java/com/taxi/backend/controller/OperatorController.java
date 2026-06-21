package com.taxi.backend.controller;

import com.taxi.backend.dto.OperatorTripRequest;
import com.taxi.backend.model.User;
import com.taxi.backend.service.ApiRateLimitService;
import com.taxi.backend.service.OperatorService;
import com.taxi.backend.service.PassengerHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Operator", description = "Operator — telefon orqali buyurtma yaratish va kuzatish")
@RestController
@RequestMapping("/api/operator")
public class OperatorController {

    private final OperatorService operatorService;
    private final ApiRateLimitService rateLimitService;
    private final PassengerHistoryService passengerHistoryService;

    public OperatorController(OperatorService operatorService,
                               ApiRateLimitService rateLimitService,
                               PassengerHistoryService passengerHistoryService) {
        this.operatorService = operatorService;
        this.rateLimitService = rateLimitService;
        this.passengerHistoryService = passengerHistoryService;
    }

    @Operation(summary = "Buyurtma yaratish (qo'ng'iroq)", description = "Mijoz telefon qilganda operator buyurtma yaratadi. source=CALL bilan yaratiladi. Rate limit: 20/daqiqa.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Buyurtma yaratildi: {tripId, estimatedPrice, ...}"),
        @ApiResponse(responseCode = "400", description = "Validatsiya xatosi"),
        @ApiResponse(responseCode = "429", description = "Rate limit oshdi")
    })
    @PostMapping("/trip/create")
    public ResponseEntity<?> createTrip(@AuthenticationPrincipal User operator,
                                         @Valid @RequestBody OperatorTripRequest req) {
        // Rate limit: 1 daqiqada max 20 ta buyurtma
        rateLimitService.checkLimit("operator:" + operator.getId(), 20, 60);

        try {
            return ResponseEntity.ok(operatorService.createTrip(operator, req));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Aktiv buyurtmalar", description = "Barcha faol CALL buyurtmalar ro'yxati. Har 10 soniyada polling qilinadi.")
    @GetMapping("/trips/active")
    public ResponseEntity<?> activeTrips(@AuthenticationPrincipal User operator) {
        return ResponseEntity.ok(operatorService.getAllCallTrips());
    }

    @Operation(summary = "Qo'shimcha xizmatlar katalogi", description = "Buyurtmaga qo'shsa bo'ladigan xizmatlar ro'yxati (kod, nom, narx tiyinda).")
    @GetMapping("/services")
    public ResponseEntity<?> services(@AuthenticationPrincipal User operator) {
        return ResponseEntity.ok(operatorService.getServiceCatalog());
    }

    @Operation(summary = "Buyurtmani bekor qilish", description = "Operator faqat CALL source li va SEARCHING holatdagi buyurtmani bekor qila oladi.")
    @PutMapping("/trip/{tripId}/cancel")
    public ResponseEntity<?> cancelTrip(@AuthenticationPrincipal User operator,
                                         @PathVariable Long tripId) {
        try {
            return ResponseEntity.ok(operatorService.cancelTrip(operator, tripId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Buyurtma manzillarini tahrirlash", description = "Operator faqat SEARCHING holatdagi CALL buyurtma manzillarini o'zgartira oladi.")
    @PutMapping("/trip/{tripId}/edit")
    public ResponseEntity<?> editTrip(@AuthenticationPrincipal User operator,
                                       @PathVariable Long tripId,
                                       @Valid @RequestBody OperatorTripRequest req) {
        try {
            return ResponseEntity.ok(operatorService.editTrip(operator, tripId, req));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Mijoz tarixini qidirish", description = "Telefon raqami bo'yicha oxirgi safar manzillarini topadi. Operator avtomatik taklif sifatida ishlatadi.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "{found: true, passengerName, lastPickup, lastDestination} yoki {found: false}")
    })
    @GetMapping("/passenger/history")
    public ResponseEntity<?> passengerHistory(@RequestParam String phone) {
        return ResponseEntity.ok(passengerHistoryService.findByPhone(phone));
    }
}
