package com.taxi.backend.controller;

import com.taxi.backend.dto.OperatorReassignRequest;
import com.taxi.backend.dto.OperatorTripRequest;
import com.taxi.backend.model.User;
import com.taxi.backend.service.AdminService;
import com.taxi.backend.service.OperatorReassignService;
import com.taxi.backend.service.OperatorService;
import com.taxi.backend.service.OperatorTripCreateIdempotencyService;
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
    private final OperatorTripCreateIdempotencyService tripCreateIdempotencyService;
    private final PassengerHistoryService passengerHistoryService;
    private final AdminService adminService;
    private final OperatorReassignService operatorReassignService;

    public OperatorController(OperatorService operatorService,
                               OperatorTripCreateIdempotencyService tripCreateIdempotencyService,
                               PassengerHistoryService passengerHistoryService,
                               AdminService adminService,
                               OperatorReassignService operatorReassignService) {
        this.operatorService = operatorService;
        this.tripCreateIdempotencyService = tripCreateIdempotencyService;
        this.passengerHistoryService = passengerHistoryService;
        this.adminService = adminService;
        this.operatorReassignService = operatorReassignService;
    }

    @Operation(summary = "Buyurtma yaratish (qo'ng'iroq)", description = "Mijoz telefon qilganda operator buyurtma yaratadi. source=CALL bilan yaratiladi. Rate limit: 20/daqiqa.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Buyurtma yaratildi: {tripId, estimatedPrice, ...}"),
        @ApiResponse(responseCode = "400", description = "Validatsiya xatosi"),
        @ApiResponse(responseCode = "409", description = "Idempotency key qayta ishlatilgan yoki faol buyurtma mavjud"),
        @ApiResponse(responseCode = "429", description = "Rate limit oshdi")
    })
    @PostMapping("/trip/create")
    public ResponseEntity<?> createTrip(@AuthenticationPrincipal User operator,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey,
                                         @Valid @RequestBody OperatorTripRequest req) {
        return ResponseEntity.ok(tripCreateIdempotencyService.createTrip(operator, idempotencyKey, req));
    }

    @Operation(summary = "Aktiv buyurtmalar", description = "Barcha faol CALL buyurtmalar ro'yxati. Har 10 soniyada polling qilinadi.")
    @GetMapping("/trips/active")
    public ResponseEntity<?> activeTrips(@AuthenticationPrincipal User operator) {
        return ResponseEntity.ok(operatorService.getAllCallTrips());
    }

    @Operation(summary = "Onlayn haydovchilar (xarita)", description = "Operator xaritasi uchun onlayn haydovchilar joylashuvi. Mavjud getOnlineDriversForMap() ni qayta ishlatadi — haydovchi ilovasi va location cache'ga TEGMAYDI (faqat o'qish).")
    @GetMapping("/drivers/online")
    public ResponseEntity<?> onlineDrivers(@AuthenticationPrincipal User operator) {
        return ResponseEntity.ok(adminService.getOnlineDriversForMap());
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

    @Operation(summary = "Boshqa haydovchiga berish", description = "Buyurtmani driverCode bo'yicha boshqa haydovchiga beradi. ACCEPTED bo'lsa: A bo'shatiladi+istisno, B'ga MAVJUD data-only ORDER_PUSH yuboriladi. Frozen core tegilmaydi.")
    @PostMapping("/trip/{tripId}/reassign")
    public ResponseEntity<?> reassign(@AuthenticationPrincipal User operator,
                                       @PathVariable Long tripId,
                                       @RequestBody OperatorReassignRequest req) {
        try {
            return ResponseEntity.ok(operatorReassignService.reassignByCode(operator, tripId, req.getDriverCode()));
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
