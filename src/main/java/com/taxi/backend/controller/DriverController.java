package com.taxi.backend.controller;

import com.taxi.backend.dto.LocationUpdateRequest;
import com.taxi.backend.dto.UpdateProfileRequest;
import com.taxi.backend.model.User;
import com.taxi.backend.service.BroadcastBoardService;
import com.taxi.backend.service.DriverAppService;
import com.taxi.backend.service.PushNotificationService;
import com.taxi.backend.service.TaxometerService;
import com.taxi.backend.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Driver", description = "Haydovchi — profil, joylashuv, buyurtma qabul qilish, balans")
@RestController
@RequestMapping("/api/driver")
public class DriverController {

    private final DriverAppService driverService;
    private final TripService tripService;
    private final TaxometerService taxometerService;
    private final PushNotificationService pushService;
    private final com.taxi.backend.service.ApiRateLimitService rateLimitService;
    private final BroadcastBoardService broadcastBoardService;

    public DriverController(DriverAppService driverService, TripService tripService,
            TaxometerService taxometerService,
            PushNotificationService pushService,
            com.taxi.backend.service.ApiRateLimitService rateLimitService,
            BroadcastBoardService broadcastBoardService) {
        this.driverService = driverService;
        this.tripService = tripService;
        this.taxometerService = taxometerService;
        this.pushService = pushService;
        this.rateLimitService = rateLimitService;
        this.broadcastBoardService = broadcastBoardService;
    }

    @Operation(summary = "Profil olish", description = "Haydovchi profili: ism, mashina, reyting, balans, holat")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Profil"), @ApiResponse(responseCode = "401", description = "Token noto'g'ri")})
    @GetMapping({"/me", "/profile"})
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(driverService.getProfile(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(driverService.updateProfile(user,
                req.getCarModel(), req.getCarNumber(), req.getCarColor(), req.getCarYear()));
    }

    @Operation(summary = "Online/Offline almashtirish", description = "Haydovchi online yoki offline holatini almashtiradi")
    @PutMapping({"/toggle-online", "/status"})
    public ResponseEntity<?> toggleOnline(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(driverService.toggleOnlinePlain(user));
    }

    @Operation(summary = "Joylashuv yangilash", description = "GPS koordinatalarni yangilaydi. Redis cache da saqlanadi.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK")})
    @PutMapping("/location")
    public ResponseEntity<?> updateLocation(@AuthenticationPrincipal User user,
            @Valid @RequestBody LocationUpdateRequest req) {
        driverService.updateLocation(user, req.getLat(), req.getLon());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @Operation(summary = "Buyurtma qabul qilish", description = "Haydovchi buyurtmani qabul qiladi. Optimistic Lock — ikki haydovchi bir vaqtda qabul qila olmaydi.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Qabul qilindi"), @ApiResponse(responseCode = "400", description = "Allaqachon qabul qilingan")})
    @PutMapping({"/trips/{tripId}/accept", "/orders/{tripId}/accept"})
    public ResponseEntity<?> acceptTrip(@AuthenticationPrincipal User user,
            @PathVariable Long tripId) {
        return ResponseEntity.ok(tripService.acceptTrip(user, tripId));
    }

    @PutMapping({"/trips/{tripId}/pickup", "/orders/{tripId}/arrived"})
    public ResponseEntity<?> arrived(@AuthenticationPrincipal User user,
            @PathVariable Long tripId) {
        return ResponseEntity.ok(tripService.updateTripStatus(user, tripId,
                com.taxi.backend.enums.TripStatus.DRIVER_ARRIVED));
    }

    @PutMapping({"/trips/{tripId}/waiting", "/orders/{tripId}/waiting"})
    public ResponseEntity<?> startWaiting(@AuthenticationPrincipal User user,
            @PathVariable Long tripId) {
        try {
            return ResponseEntity.ok(tripService.startWaiting(user, tripId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Haydovchi yo'lovchini baholaydi (D2P)
    @PostMapping({"/trips/{tripId}/rate-passenger", "/orders/{tripId}/rate-passenger"})
    public ResponseEntity<?> ratePassenger(@AuthenticationPrincipal User user,
            @PathVariable Long tripId,
            @RequestBody com.taxi.backend.dto.RateTripRequest req) {
        try {
            return ResponseEntity.ok(tripService.ratePassenger(user, tripId, req.getRating(),
                    req.getComment() != null ? req.getComment() : ""));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping({"/trips/{tripId}/trip-waiting", "/orders/{tripId}/trip-waiting"})
    public ResponseEntity<?> startTripWaiting(@AuthenticationPrincipal User user,
            @PathVariable Long tripId) {
        try {
            return ResponseEntity.ok(tripService.startTripWaiting(user, tripId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping({"/trips/{tripId}/stop-trip-waiting", "/orders/{tripId}/stop-trip-waiting"})
    public ResponseEntity<?> stopTripWaiting(@AuthenticationPrincipal User user,
            @PathVariable Long tripId) {
        try {
            return ResponseEntity.ok(tripService.stopTripWaiting(user, tripId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping({"/trips/{tripId}/start", "/orders/{tripId}/start"})
    public ResponseEntity<?> startTrip(@AuthenticationPrincipal User user,
            @PathVariable Long tripId) {
        return ResponseEntity.ok(tripService.updateTripStatus(user, tripId,
                com.taxi.backend.enums.TripStatus.STARTED));
    }

    @PutMapping({"/trips/{tripId}/cancel", "/orders/{tripId}/cancel"})
    public ResponseEntity<?> cancelTrip(@AuthenticationPrincipal User user,
            @PathVariable Long tripId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String reason = body != null ? body.getOrDefault("reason", null) : null;
            return ResponseEntity.ok(tripService.cancelTripByDriver(user, tripId, reason));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Yangi buyurtmani rad etish", description = "Haydovchi yangi kelgan buyurtmani rad etadi. U excluded_driver_ids ga qo'shiladi (strike yo'q). Trip boshqalarga ochiq qoladi.")
    @PostMapping({"/trips/{tripId}/decline", "/orders/{tripId}/decline"})
    public ResponseEntity<?> declineTrip(@AuthenticationPrincipal User user,
            @PathVariable Long tripId) {
        try {
            return ResponseEntity.ok(tripService.declineTrip(user, tripId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Safarni yakunlash", description = "Safarni COMPLETED ga o'tkazadi. Komissiya hisoblanadi, haydovchi balansidan yechiladi.")
    @PutMapping({"/trips/{tripId}/complete", "/orders/{tripId}/complete"})
    public ResponseEntity<?> completeTrip(@AuthenticationPrincipal User user,
            @PathVariable Long tripId) {
        return ResponseEntity.ok(tripService.updateTripStatus(user, tripId,
                com.taxi.backend.enums.TripStatus.COMPLETED));
    }

    @GetMapping("/trips/active")
    public ResponseEntity<?> activeTrip(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(tripService.driverActiveTrip(user));
    }

    @GetMapping("/trips/available")
    public ResponseEntity<?> availableTrips(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(tripService.getAvailableTrips(user));
    }

    @GetMapping("/trips")
    public ResponseEntity<?> history(@AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (size > 100) size = 100; if (size < 1) size = 10;
        return ResponseEntity.ok(tripService.driverHistory(
                user, PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    @GetMapping("/services")
    public ResponseEntity<?> getServices(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(driverService.getServices(user));
    }

    @PutMapping("/services/{id}/toggle")
    public ResponseEntity<?> toggleServiceById(@AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(driverService.toggleServiceById(user, id));
    }

    @GetMapping("/balance")
    public ResponseEntity<?> getBalance(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(driverService.getBalanceSimple(user));
    }

    @PostMapping("/balance/topup")
    public ResponseEntity<?> topupBalance(@AuthenticationPrincipal User user,
            @Valid @RequestBody com.taxi.backend.dto.TopupRequest req) {
        return ResponseEntity.ok(driverService.topupBalance(user, req.getAmount()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(@AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100; if (size < 1) size = 20;
        return ResponseEntity.ok(driverService.getTransactions(
                user, PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    @GetMapping("/messages")
    public ResponseEntity<?> messages(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size > 50) size = 50; if (size < 1) size = 20;
        return ResponseEntity.ok(driverService.getMessages(
                PageRequest.of(page, size, Sort.by("sentAt").descending())));
    }

    /** A3 — Barcha aktiv tariflar + shu haydovchi uchun eligible/accepted bayroqlari (A2 grantlari bilan). */
    @GetMapping("/tariffs")
    public ResponseEntity<?> getDriverTariffs(@AuthenticationPrincipal User user) {
        try {
            return ResponseEntity.ok(driverService.getDriverTariffs(user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/tariffs")
    public ResponseEntity<?> updateTariffs(@AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            java.util.List<String> tariffs = (java.util.List<String>) body.get("acceptedTariffs");
            return ResponseEntity.ok(driverService.updateAcceptedTariffs(user, tariffs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/preferences")
    public ResponseEntity<?> getPreferences(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(driverService.getPreferences(user));
    }

    @PatchMapping("/preferences")
    public ResponseEntity<?> updatePreferences(@AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(driverService.updatePreferences(user, body));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Umumiy buyurtmalar taxtasi", description = "Broadcast qilingan, hali da'vo qilinmagan triplar")
    @GetMapping("/broadcast-trips")
    public ResponseEntity<?> getBroadcastTrips(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(broadcastBoardService.getBroadcastBoard(user));
    }

    @Operation(summary = "Broadcast tripni da'vo qilish", description = "SELECT FOR UPDATE — faqat bitta haydovchi qabul qila oladi")
    @PostMapping("/broadcast-trips/{tripId}/claim")
    public ResponseEntity<?> claimBroadcastTrip(@AuthenticationPrincipal User user,
            @PathVariable Long tripId) {
        try {
            return ResponseEntity.ok(broadcastBoardService.claimTrip(user, tripId));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(driverService.getStats(user));
    }

    /** A4 — Daromad statistikasi: kunlik(oxirgi 30)/haftalik(12)/oylik(12) bucketlar — chart uchun. */
    @GetMapping("/earnings/chart")
    public ResponseEntity<?> earningsChart(@AuthenticationPrincipal User user) {
        try {
            return ResponseEntity.ok(driverService.getEarningsChart(user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/photos")
    public ResponseEntity<?> getPhotos(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(driverService.getPhotos(user));
    }

    /** A6 — Haydovchi hujjatlari (passport seriya/raqam + tug'ilgan sana) saqlash. Rasm yuklash:
     *  mavjud POST /api/driver/photos/upload (photoType=ID_FRONT|ID_BACK|PASSPORT). */
    @PutMapping("/documents")
    public ResponseEntity<?> saveDocuments(@AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> body) {
        try {
            String passportSeries = body.get("passportSeries") != null ? body.get("passportSeries").toString() : null;
            String passportNumber = body.get("passportNumber") != null ? body.get("passportNumber").toString() : null;
            String birthDate = body.get("birthDate") != null ? body.get("birthDate").toString() : null;
            return ResponseEntity.ok(driverService.saveDriverDocuments(user, passportSeries, passportNumber, birthDate));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** A6 — Haydovchi hujjatlari (ko'rsatish): passport + tug'ilgan sana + hujjat rasm URL'lari. */
    @GetMapping("/documents")
    public ResponseEntity<?> getDocuments(@AuthenticationPrincipal User user) {
        try {
            return ResponseEntity.ok(driverService.getDriverDocuments(user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Expo push token saqlash */
    @PostMapping("/push-token")
    public ResponseEntity<?> savePushToken(@AuthenticationPrincipal User user,
            @Valid @RequestBody com.taxi.backend.dto.PushTokenRequest req) {
        try {
            var profile = driverService.getProfile(user);
            pushService.saveToken("driver", profile.id(), req.getToken());
        } catch (Exception e) {
            pushService.saveToken("driver", user.getId(), req.getToken());
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ═══ Taxometer ═══

    @Operation(summary = "Taxometr boshlash")
    @PostMapping("/taxometer/start")
    public ResponseEntity<?> taxometerStart(@AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> body) {
        try {
            Object latObj = body.get("lat");
            Object lonObj = body.get("lon");
            if (latObj == null || lonObj == null)
                return ResponseEntity.badRequest().body(Map.of("error", "lat va lon kerak"));
            double lat = ((Number) latObj).doubleValue();
            double lon = ((Number) lonObj).doubleValue();
            Long existingTripId = body.get("existingTripId") != null
                    ? ((Number) body.get("existingTripId")).longValue() : null;
            return ResponseEntity.ok(taxometerService.start(user, lat, lon, existingTripId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Taxometr yakunlash")
    @PostMapping("/taxometer/finish")
    public ResponseEntity<?> taxometerFinish(@AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> body) {
        try {
            Long tripId = ((Number) body.get("tripId")).longValue();
            double endLat = ((Number) body.get("endLat")).doubleValue();
            double endLon = ((Number) body.get("endLon")).doubleValue();
            double distanceKm = ((Number) body.get("distanceKm")).doubleValue();
            return ResponseEntity.ok(taxometerService.finish(user, tripId, endLat, endLon, distanceKm));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** A5 — Taxometerni PAUZA qilish (kutish boshlanadi). Body: {tripId}. */
    @PostMapping("/taxometer/pause")
    public ResponseEntity<?> taxometerPause(@AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> body) {
        try {
            Long tripId = ((Number) body.get("tripId")).longValue();
            return ResponseEntity.ok(taxometerService.pause(user, tripId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** A5 — Taxometerni DAVOM ettirish (kutish yakunlanadi, waitingFee ga qo'shiladi). Body: {tripId}. */
    @PostMapping("/taxometer/resume")
    public ResponseEntity<?> taxometerResume(@AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> body) {
        try {
            Long tripId = ((Number) body.get("tripId")).longValue();
            return ResponseEntity.ok(taxometerService.resume(user, tripId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Faol taxometrni olish (restart recovery)")
    @GetMapping("/taxometer/active")
    public ResponseEntity<?> taxometerActive(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(taxometerService.getActive(user));
    }

}
