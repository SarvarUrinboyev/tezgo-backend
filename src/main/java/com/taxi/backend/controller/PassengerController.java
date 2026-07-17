package com.taxi.backend.controller;

import com.taxi.backend.dto.BookTripRequest;
import com.taxi.backend.dto.ContinueTripRequest;
import com.taxi.backend.dto.EstimateRequest;
import com.taxi.backend.dto.RateTripRequest;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.BannerRepository;
import com.taxi.backend.repository.TariffRepository;
import com.taxi.backend.repository.TripRepository;
import com.taxi.backend.repository.UserRepository;
import com.taxi.backend.service.MatchingService;
import com.taxi.backend.service.PushNotificationService;
import com.taxi.backend.service.SurgePricingService;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Tag(name = "Passenger", description = "Yo'lovchi — buyurtma berish, narx hisoblash, baho berish")
@RestController
@RequestMapping("/api/passenger")
public class PassengerController {

    private final TripService tripService;
    private final TariffRepository tariffRepository;
    private final SurgePricingService surgePricingService;
    private final MatchingService matchingService;
    private final PushNotificationService pushService;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final BannerRepository bannerRepository;

    private final com.taxi.backend.service.ApiRateLimitService rateLimitService;
    private final com.taxi.backend.service.ReferralService referralService;

    public PassengerController(TripService tripService, TariffRepository tariffRepository,
                               SurgePricingService surgePricingService, MatchingService matchingService,
                               PushNotificationService pushService, TripRepository tripRepository,
                               UserRepository userRepository, BannerRepository bannerRepository,
                               com.taxi.backend.service.ApiRateLimitService rateLimitService,
                               com.taxi.backend.service.ReferralService referralService) {
        this.tripService = tripService;
        this.tariffRepository = tariffRepository;
        this.surgePricingService = surgePricingService;
        this.matchingService = matchingService;
        this.pushService = pushService;
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
        this.bannerRepository = bannerRepository;
        this.rateLimitService = rateLimitService;
        this.referralService = referralService;
    }

    // ─── Tariflar (1 soat in-memory kesh) ─────────────────────────────────
    private static final long TARIFF_CACHE_TTL = 3600_000L; // 1 soat
    private final AtomicReference<List<Map<String, Object>>> tariffCache = new AtomicReference<>();
    private final AtomicLong tariffCacheTime = new AtomicLong(0);

    @GetMapping("/tariffs")
    public ResponseEntity<?> getTariffs() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> cached = tariffCache.get();
        if (cached != null && (now - tariffCacheTime.get()) < TARIFF_CACHE_TTL) {
            return ResponseEntity.ok(cached);
        }
        List<Map<String, Object>> result = tariffRepository.findByIsActiveTrue().stream()
                .map(t -> Map.of(
                        "id", (Object) t.getId(),
                        "name", (Object) t.getName(),
                        "basePrice", (Object) t.getBasePrice(),
                        "pricePerKm", (Object) t.getPricePerKm(),
                        "pricePerMin", (Object) t.getPricePerMin(),
                        "minPrice", (Object) t.getMinPrice()))
                .collect(Collectors.toList());
        tariffCache.set(result);
        tariffCacheTime.set(now);
        return ResponseEntity.ok(result);
    }

    // ─── Narx hisoblash (surge bilan) ────────────────────────────────────────

    // ─── Bannerlar (bosh sahifa karuseli) ─────────────────────────────────

    @Operation(summary = "Faol bannerlar", description = "Bosh sahifa karuseli uchun faol va sana oralig'idagi bannerlar, sort bo'yicha")
    @GetMapping("/banners")
    public ResponseEntity<?> getBanners() {
        List<Map<String, Object>> result = bannerRepository.findActiveInRange(LocalDateTime.now()).stream()
                .limit(10)
                .map(b -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", b.getId());
                    m.put("title", b.getTitle());
                    m.put("subtitle", b.getSubtitle());
                    m.put("bgColor", b.getBgColor());
                    m.put("imageUrl", b.getImageUrl());
                    m.put("linkUrl", b.getLinkUrl());
                    m.put("sort", b.getSort());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Narx hisoblash", description = "Tarif va masofaga qarab taxminiy narx hisoblaydi. Surge pricing qo'llaniladi.")
    @PostMapping("/trips/estimate")
    public ResponseEntity<?> estimate(@Valid @RequestBody EstimateRequest req) {
        double lat = req.getLat() != null ? req.getLat() : 0.0;
        double lon = req.getLon() != null ? req.getLon() : 0.0;
        return ResponseEntity.ok(tripService.estimate(req.getTariffId(), req.getDistanceKm(), lat, lon));
    }

    // ─── Demand indikatori (Yandex kabi) ─────────────────────────────────────

    @GetMapping("/demand")
    public ResponseEntity<?> getDemand(@RequestParam(defaultValue = "41.3") double lat,
                                        @RequestParam(defaultValue = "69.2") double lon) {
        return ResponseEntity.ok(surgePricingService.getDemandInfo(lat, lon));
    }

    // ─── Yaqin haydovchilar (xaritada ko'rsatish uchun) ──────────────────────

    @GetMapping("/nearby-drivers")
    public ResponseEntity<?> nearbyDrivers(@AuthenticationPrincipal User user,
                                            @RequestParam double lat, @RequestParam double lon) {
        // RATE LIMIT: 10 soniyada max 3 ta so'rov (matching engine spam himoyasi)
        rateLimitService.checkLimit("nearby:" + user.getId(), 3, 10);
        List<Map<String, Object>> drivers = matchingService.findNearbyDrivers(lat, lon)
                .stream()
                .map(d -> Map.<String, Object>of(
                        "driverId", d.driverId(),
                        // Passenger demand maps receive a coarse cell only. Exact
                        // coordinates are delivered through the authorized active-trip
                        // WebSocket channel, never through arbitrary-coordinate lookup.
                        "lat", coarseCoordinate(d.lat()),
                        "lon", coarseCoordinate(d.lon()),
                        "distanceKm", Math.round(d.distanceKm() * 10.0) / 10.0,
                        "etaMinutes", (int) Math.ceil(d.etaMinutes()),
                        "rating", d.rating(),
                        "carModel", d.carModel()))
                .toList();
        return ResponseEntity.ok(Map.of("drivers", drivers, "count", drivers.size()));
    }

    private double coarseCoordinate(double coordinate) {
        return Math.round(coordinate * 100.0) / 100.0;
    }

    // ─── Buyurtma berish (/api/passenger/trips — mobile dan keladi) ──────────

    @Operation(summary = "Buyurtma berish", description = "Yangi buyurtma yaratadi. Promo kod bilan chegirma mumkin. Yaqin haydovchilarga notification yuboriladi.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Buyurtma yaratildi: {tripId, estimatedPrice, surgeLevel}"),
        @ApiResponse(responseCode = "400", description = "Validatsiya xatosi yoki faol buyurtma mavjud"),
        @ApiResponse(responseCode = "401", description = "Token noto'g'ri"), @ApiResponse(responseCode = "429", description = "Rate limit")
    })
    @PostMapping("/trips")
    public ResponseEntity<?> bookTrip(@AuthenticationPrincipal User user,
            @Valid @RequestBody BookTripRequest req) {
        return doBook(user, req);
    }

    /** Alias: eski /trips/book ham ishlaydi */
    @PostMapping("/trips/book")
    public ResponseEntity<?> bookTripAlias(@AuthenticationPrincipal User user,
            @Valid @RequestBody BookTripRequest req) {
        return doBook(user, req);
    }

    private ResponseEntity<?> doBook(User user, BookTripRequest req) {
        // RATE LIMIT: Per-user — 1 daqiqada max 5 ta buyurtma
        rateLimitService.checkLimit("book:" + user.getId(), 5, 60);
        // RATE LIMIT: Global — 1 soniyada max 100 ta buyurtma (DDoS himoyasi)
        rateLimitService.checkGlobalLimit("bookTrip", 100, 1);
        boolean isTaxometer = "TAXOMETER".equalsIgnoreCase(req.getTripMode());
        double fromLat = req.getFromLat() != null ? req.getFromLat() : 0.0;
        double fromLon = req.getFromLon() != null ? req.getFromLon() : 0.0;
        double distKm, toLat, toLon;
        String toAddress, source;
        if (isTaxometer) {
            distKm = 0.01;
            toLat = fromLat;
            toLon = fromLon;
            toAddress = "Taxometr rejimi";
            source = "APP_TAXOMETER";
        } else {
            distKm = req.getDistance() != null ? req.getDistance() : 0.0;
            toLat = req.getToLat() != null ? req.getToLat() : 0.0;
            toLon = req.getToLon() != null ? req.getToLon() : 0.0;
            toAddress = req.getToAddress();
            source = "APP";
        }
        // Rejalashtirilgan buyurtma vaqti (epoch millis, UTC) — taxometrда yo'q.
        java.time.LocalDateTime scheduledAt = null;
        if (!isTaxometer && req.getScheduledAt() != null && req.getScheduledAt() > 0) {
            scheduledAt = java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(req.getScheduledAt()), java.time.ZoneOffset.UTC);
        }
        return ResponseEntity.ok(tripService.bookTrip(user, req.getTariffId(),
                fromLat, fromLon, req.getFromAddress(),
                toLat, toLon, toAddress, distKm, req.getPromoCode(), req.getOfferedFare(), source, scheduledAt));
    }

    // ─── Trip holati tekshirish (TrackingScreen polling) ─────────────────────

    @GetMapping("/trips/{tripId}")
    public ResponseEntity<?> getTrip(@AuthenticationPrincipal User user,
            @PathVariable Long tripId) {
        try {
            return ResponseEntity.ok(tripService.getTripForPassenger(user, tripId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }


    // ─── Faol sayohat ────────────────────────────────────────────────────────

    @GetMapping("/trips/active")
    public ResponseEntity<?> activeTrip(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(tripService.activeTrip(user));
    }

    // ─── Sayohat tarixi (/api/passenger/trips?page=0) ────────────────────────

    @GetMapping("/trips")
    public ResponseEntity<?> history(@AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (size > 50) size = 50; if (size < 1) size = 10;
        return ResponseEntity.ok(tripService.passengerHistory(
                user, PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    // ─── Sayohatni davom ettirish (shu haydovchi bilan yangi manzilga) ────────

    @PostMapping("/trips/{tripId}/continue")
    public ResponseEntity<?> continueTrip(@AuthenticationPrincipal User user,
            @PathVariable Long tripId,
            @Valid @RequestBody ContinueTripRequest req) {
        try {
            String toAddress = req.getToAddress() != null ? req.getToAddress() : "";
            return ResponseEntity.ok(tripService.continueTrip(user, tripId,
                    req.getToLat(), req.getToLon(), toAddress, req.getDistanceKm()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Rejalashtirilgan buyurtmalar ro'yxati ────────────────────────────────
    @GetMapping("/trips/scheduled")
    public ResponseEntity<?> scheduledTrips(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("trips", tripService.getScheduledTrips(user.getId())));
    }

    // ─── Referal (do'st chaqirish) ────────────────────────────────────────────
    @GetMapping("/referral")
    public ResponseEntity<?> myReferral(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(referralService.getMyReferral(user.getId()));
    }

    @PostMapping("/referral/apply")
    public ResponseEntity<?> applyReferral(@AuthenticationPrincipal User user,
            @RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(referralService.applyCode(user.getId(), body.get("code")));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    // ─── Sayohatni bekor qilish ───────────────────────────────────────────────

    @PutMapping("/trips/{tripId}/cancel")
    public ResponseEntity<?> cancelTrip(@AuthenticationPrincipal User user,
            @PathVariable Long tripId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String reason = body != null ? body.getOrDefault("reason", null) : null;
            return ResponseEntity.ok(tripService.cancelTripByPassenger(user, tripId, reason));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Baho berish", description = "Yakunlangan safarni baholash (1-5). source=CALL bo'lsa 403 qaytaradi.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Baho qabul qilindi"),
        @ApiResponse(responseCode = "403", description = "CALL trip uchun baho berish taqiqlangan")
    })
    @PostMapping("/trips/{tripId}/rate")
    public ResponseEntity<?> rateTrip(@AuthenticationPrincipal User user,
            @PathVariable Long tripId,
            @Valid @RequestBody RateTripRequest req) {
        return ResponseEntity.ok(tripService.rateTrip(user, tripId, req.getRating(),
                req.getComment() != null ? req.getComment() : ""));
    }

    // ─── Yo'lovchi statistikasi ───────────────────────────────────────────────

    // ─── Profil tahrirlash ────────────────────────────────────────────────────

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal User user,
            @Valid @RequestBody com.taxi.backend.dto.UpdateNameRequest req) {
        try {
            user.setName(req.getName().trim());
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("name", user.getName(), "phone", user.getPhone()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats(@AuthenticationPrincipal User user) {
        try {
            return ResponseEntity.ok(tripService.passengerStats(user));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("totalTrips", 0, "totalSpent", 0L));
        }
    }

    // ─── Expo push token saqlash ─────────────────────────────────────────────

    @PostMapping("/push-token")
    public ResponseEntity<?> savePushToken(@AuthenticationPrincipal User user,
            @Valid @RequestBody com.taxi.backend.dto.PushTokenRequest req) {
        pushService.saveToken("passenger", user.getId(), req.getToken());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ─── Haydovchining real-time joylashuvi (TrackingScreen polling) ─────────

    @GetMapping("/trips/{tripId}/driver-location")
    public ResponseEntity<?> driverLocation(@AuthenticationPrincipal User user,
            @PathVariable Long tripId) {
        try {
            return tripRepository.findById(tripId).map(trip -> {
                if (!trip.getPassenger().getId().equals(user.getId()))
                    return ResponseEntity.status(403).body(Map.of("error", "Ruxsat yo'q"));
                if (trip.getDriver() == null)
                    return ResponseEntity.ok(Map.of("available", false));
                var d = trip.getDriver();
                return ResponseEntity.ok(Map.<String, Object>of(
                        "available", true,
                        "lat", d.getLatitude() != null ? d.getLatitude() : 0.0,
                        "lon", d.getLongitude() != null ? d.getLongitude() : 0.0,
                        "driverName", d.getUser() != null ? d.getUser().getName() : "",
                        "carModel", d.getCarModel() != null ? d.getCarModel() : "",
                        "carNumber", d.getCarNumber() != null ? d.getCarNumber() : ""
                ));
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
