package com.taxi.backend.controller;

import com.taxi.backend.dto.AdminTopupRequest;
import com.taxi.backend.dto.BannerRequest;
import com.taxi.backend.dto.SetPasswordRequest;
import com.taxi.backend.dto.TariffRequest;
import com.taxi.backend.exception.ConflictException;
import com.taxi.backend.dto.response.AdminDriversPageResponse;
import com.taxi.backend.model.Banner;
import com.taxi.backend.model.User;
import com.taxi.backend.service.AdminService;
import com.taxi.backend.service.AuthService;
import com.taxi.backend.service.PhotoService;
import com.taxi.backend.service.TripService;
import com.taxi.backend.repository.BannerRepository;
import com.taxi.backend.repository.OtpRepository;
import com.taxi.backend.repository.TariffRepository;
import com.taxi.backend.repository.UserRepository;
import com.taxi.backend.model.Tariff;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "Admin", description = "Admin panel — haydovchilar, yo'lovchilar, buyurtmalar, hisobotlar boshqarish")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final PhotoService photoService;
    private final TariffRepository tariffRepository;
    private final UserRepository userRepository;
    private final com.taxi.backend.repository.TripRepository tripRepository;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;
    private final OtpRepository otpRepository;
    private final AuthService authService;
    private final com.taxi.backend.service.SystemSettingService systemSettingService;
    private final com.taxi.backend.service.OperatorAdminService operatorAdminService;
    private final BannerRepository bannerRepository;

    public AdminController(AdminService adminService,
            PhotoService photoService,
            TariffRepository tariffRepository,
            UserRepository userRepository,
            com.taxi.backend.repository.TripRepository tripRepository,
            org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate,
            OtpRepository otpRepository,
            AuthService authService,
            com.taxi.backend.service.SystemSettingService systemSettingService,
            com.taxi.backend.service.OperatorAdminService operatorAdminService,
            BannerRepository bannerRepository) {
        this.adminService = adminService;
        this.photoService = photoService;
        this.tariffRepository = tariffRepository;
        this.userRepository = userRepository;
        this.tripRepository = tripRepository;
        this.redisTemplate = redisTemplate;
        this.otpRepository = otpRepository;
        this.authService = authService;
        this.systemSettingService = systemSettingService;
        this.operatorAdminService = operatorAdminService;
        this.bannerRepository = bannerRepository;
    }

    // ─── Sozlamalar: talab narxi (surge) toggle — default OFF ─────────────────

    @Operation(summary = "Surge holati", description = "Talab narxi (surge) yoqilgan/o'chiqligini qaytaradi")
    @GetMapping("/settings/surge")
    public ResponseEntity<?> getSurgeSetting() {
        return ResponseEntity.ok(Map.of("enabled", systemSettingService.isSurgeEnabled()));
    }

    @Operation(summary = "Surge yoqish/o'chirish", description = "Talab narxini (surge) admin yoqadi yoki o'chiradi. Tungi tarif bundan mustaqil.")
    @PutMapping("/settings/surge")
    public ResponseEntity<?> setSurgeSetting(@RequestBody Map<String, Object> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        systemSettingService.setSurgeEnabled(enabled);
        return ResponseEntity.ok(Map.of("enabled", enabled));
    }

    @Operation(summary = "Dashboard statistikasi", description = "Haydovchilar, buyurtmalar, daromad haqida real-time statistika")
    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    /** Haydovchilar ro'yxati */
    @GetMapping("/drivers")
    public ResponseEntity<?> drivers(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100; if (size < 1) size = 20;
        var p = adminService.getDrivers(status, PageRequest.of(page, size, Sort.by("id").descending()));
        long liniyada = adminService.getLiniyadaCount();
        return ResponseEntity.ok(new AdminDriversPageResponse(
                p.getContent(), p.getTotalElements(), p.getTotalPages(),
                p.getSize(), p.getNumber(), liniyada));
    }

    /** Haydovchi tasdiqlash */
    @PutMapping("/drivers/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id, @AuthenticationPrincipal User admin) {
        try {
            return ResponseEntity.ok(adminService.approveDriver(id, admin));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Haydovchi bloklash */
    @PutMapping("/drivers/{id}/block")
    public ResponseEntity<?> block(@PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "Sabab ko'rsatilmadi") : "—";
        try {
            return ResponseEntity.ok(adminService.blockDriver(id, reason));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Haydovchi o'chirish */
    @DeleteMapping("/drivers/{id}")
    public ResponseEntity<?> deleteDriver(@PathVariable Long id) {
        try {
            adminService.deleteDriver(id);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Haydovchilar qidirish — ism, telefon yoki driver_code bo'yicha */
    @GetMapping("/drivers/search")
    public ResponseEntity<?> searchDrivers(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (q == null || q.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "q parametri kerak"));
        if (size > 100) size = 100;
        var result = adminService.searchDrivers(q.trim(),
                PageRequest.of(page, size, Sort.by("id").descending()));
        return ResponseEntity.ok(result);
    }

    /** Haydovchi kodi bo'yicha (TZ-XXXX) */
    @GetMapping("/drivers/by-code/{code}")
    public ResponseEntity<?> getDriverByCode(@PathVariable String code) {
        try {
            return ResponseEntity.ok(adminService.getDriverByCode(code));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Admin — haydovchi balansini to'ldirish (CASH yoki CARD) */
    @PostMapping("/drivers/{id}/topup")
    public ResponseEntity<?> adminTopupBalance(@PathVariable Long id,
            @Valid @RequestBody AdminTopupRequest req) {
        try {
            return ResponseEntity.ok(adminService.adminTopupBalance(id, req.getAmount(), req.getPaymentMethod()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Admin — haydovchiga tarif GRANT berish/olib tashlash (A2). Mashina-modeli defaultini KENGAYTIRADI.
     *  Body: {"grantedTariffs": ["KOMFORT","BIZNES"]}. Bo'sh/null → barcha grantlar olib tashlanadi. */
    @PutMapping("/drivers/{id}/tariff-grants")
    public ResponseEntity<?> adminSetTariffGrants(@PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            java.util.List<String> granted = (java.util.List<String>) body.get("grantedTariffs");
            return ResponseEntity.ok(adminService.setDriverTariffGrants(id, granted));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Admin — haydovchining joriy tarif grant holati (car-default vs effektiv) (A2, read-only). */
    @GetMapping("/drivers/{id}/tariff-grants")
    public ResponseEntity<?> adminGetTariffGrants(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(adminService.getDriverTariffGrants(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Admin — haydovchini qo'lda online/offline qilish */
    @PatchMapping("/drivers/{id}/online-status")
    public ResponseEntity<?> setDriverOnlineStatus(@PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        Boolean isOnline = body.get("isOnline");
        if (isOnline == null)
            return ResponseEntity.badRequest().body(Map.of("error", "isOnline (boolean) kerak"));
        try {
            return ResponseEntity.ok(adminService.setDriverOnlineStatus(id, isOnline));
        } catch (ConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Kengaytirilgan dashboard statistikasi: komissiya foydasi + balans aylanmasi */
    @GetMapping("/dashboard/stats")
    public ResponseEntity<?> dashboardExtendedStats() {
        return ResponseEntity.ok(adminService.getDashboardExtendedStats());
    }

    /** Haydovchi rasmlari (foto nazorat) */
    @GetMapping("/drivers/{id}/photos")
    public ResponseEntity<?> photos(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getDriverPhotos(id));
    }

    /** Rasmni tasdiqlash */
    @PutMapping("/photos/{id}/approve")
    public ResponseEntity<?> approvePhoto(@PathVariable Long id, @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(adminService.approvePhoto(id, admin));
    }

    /** Rasmni rad etish */
    @PutMapping("/photos/{id}/reject")
    public ResponseEntity<?> rejectPhoto(@PathVariable Long id,
            @Valid @RequestBody com.taxi.backend.dto.RejectRequest req,
            @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(adminService.rejectPhoto(id, req.getReason(), admin));
    }

    /**
     * Rasmni o'chirish — DRIVER_FACE/SELFIE ham kiradi (driver-app'dagi immutability qulfi faqat
     * haydovchi tomoni uchun; admin har doim o'chira oladi, masalan rad etilgan selfie'ni qayta
     * yuklash imkonini berish uchun). Sabab majburiy.
     */
    @DeleteMapping("/photos/{id}")
    public ResponseEntity<?> deletePhoto(@PathVariable Long id,
            @Valid @RequestBody com.taxi.backend.dto.RejectRequest req,
            @AuthenticationPrincipal User admin) {
        try {
            return ResponseEntity.ok(adminService.deletePhoto(id, req.getReason(), admin));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Broadcast xabar yuborish. target=DRIVER bo'lsa, driverId majburiy (faqat o'sha haydovchiga yuboriladi). */
    @PostMapping("/messages/broadcast")
    public ResponseEntity<?> broadcast(@Valid @RequestBody com.taxi.backend.dto.BroadcastRequest req,
            @AuthenticationPrincipal User admin) {
        try {
            return ResponseEntity.ok(adminService.sendBroadcast(
                    req.getTitle(), req.getContent(),
                    req.getTarget() != null ? req.getTarget() : "ALL",
                    req.getDriverId(), admin));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Barcha buyurtmalar (source filter: ALL, APP, CALL, ADMIN, TAXOMETER) */
    @GetMapping("/trips")
    public ResponseEntity<?> trips(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String source) {
        if (size > 100) size = 100; if (size < 1) size = 20;
        return ResponseEntity.ok(adminService.getAllTrips(
                PageRequest.of(page, size, Sort.by("createdAt").descending()),
                source.isBlank() ? null : source));
    }

    /** Bitta buyurtma batafsil + chat tarixi */
    @GetMapping("/trips/{id}")
    public ResponseEntity<?> tripDetail(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(adminService.getTripDetail(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Admin uchun trip chat (hech qachon o'chmaydi) */
    @GetMapping("/trips/{id}/chat")
    public ResponseEntity<?> tripChat(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getTripChat(id));
    }

    /** Barcha sharhlar (Ratings) */
    @GetMapping("/ratings")
    public ResponseEntity<?> ratings(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100; if (size < 1) size = 20;
        return ResponseEntity.ok(adminService.getAllRatings(
                PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    /** Tariflar */
    @GetMapping("/tariffs")
    public ResponseEntity<?> tariffs() {
        return ResponseEntity.ok(tariffRepository.findAll());
    }

    @PostMapping("/tariffs")
    public ResponseEntity<?> createTariff(@Valid @RequestBody TariffRequest req) {
        Tariff tariff = new Tariff();
        tariff.setName(req.getName());
        tariff.setBasePrice(req.getBasePrice());
        tariff.setPricePerKm(req.getPricePerKm());
        tariff.setPricePerMin(req.getPricePerMin() != null ? req.getPricePerMin() : 0L);
        tariff.setMinPrice(req.getMinPrice() != null ? req.getMinPrice() : 0L);
        tariff.setActive(req.isActive());
        return ResponseEntity.ok(tariffRepository.save(tariff));
    }

    @PutMapping("/tariffs/{id}")
    public ResponseEntity<?> updateTariff(@PathVariable Long id, @Valid @RequestBody TariffRequest req) {
        return tariffRepository.findById(id).map(t -> {
            t.setName(req.getName());
            t.setBasePrice(req.getBasePrice());
            t.setPricePerKm(req.getPricePerKm());
            t.setPricePerMin(req.getPricePerMin() != null ? req.getPricePerMin() : 0L);
            t.setMinPrice(req.getMinPrice() != null ? req.getMinPrice() : 0L);
            t.setActive(req.isActive());
            return ResponseEntity.ok(tariffRepository.save(t));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Reklama bannerlari (bosh sahifa karuseli) — barchasi, faol va nofaol */
    @GetMapping("/banners")
    public ResponseEntity<?> banners() {
        return ResponseEntity.ok(bannerRepository.findAll(Sort.by("sort").ascending()));
    }

    @PostMapping("/banners")
    public ResponseEntity<?> createBanner(@Valid @RequestBody BannerRequest req) {
        Banner banner = new Banner();
        applyBannerRequest(banner, req);
        return ResponseEntity.ok(bannerRepository.save(banner));
    }

    @PutMapping("/banners/{id}")
    public ResponseEntity<?> updateBanner(@PathVariable Long id, @Valid @RequestBody BannerRequest req) {
        return bannerRepository.findById(id).map(b -> {
            applyBannerRequest(b, req);
            return ResponseEntity.ok(bannerRepository.save(b));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Bitta bannerni faol/nofaol qilish — to'liq formani qayta yubormasdan tez almashtirish uchun */
    @PatchMapping("/banners/{id}/active")
    public ResponseEntity<?> toggleBannerActive(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return bannerRepository.findById(id).map(b -> {
            b.setActive(Boolean.TRUE.equals(body.get("active")));
            return ResponseEntity.ok(bannerRepository.save(b));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/banners/{id}")
    public ResponseEntity<?> deleteBanner(@PathVariable Long id) {
        if (!bannerRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        bannerRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Banner rasm yuklash — qaytgan url'ni create/update so'rovidagi imageUrl maydoniga qo'yiladi */
    @PostMapping("/banners/upload")
    public ResponseEntity<?> uploadBannerImage(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(photoService.uploadBannerImage(file));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private void applyBannerRequest(Banner b, BannerRequest req) {
        b.setTitle(req.getTitle());
        b.setSubtitle(req.getSubtitle());
        b.setBgColor(req.getBgColor());
        if (req.getImageUrl() != null) b.setImageUrl(req.getImageUrl());
        b.setLinkUrl(req.getLinkUrl());
        b.setSort(req.getSort());
        b.setActive(req.isActive());
        b.setStartsAt(req.getStartsAt());
        b.setEndsAt(req.getEndsAt());
    }

    /** Yo'lovchilar ro'yxati */
    @GetMapping("/passengers")
    public ResponseEntity<?> passengers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size > 100) size = 100; if (size < 1) size = 20;
        return ResponseEntity.ok(adminService.getPassengers(
                PageRequest.of(page, size, Sort.by("id").descending())));
    }

    /** Yo'lovchini o'chirish */
    @DeleteMapping("/passengers/{id}")
    public ResponseEntity<?> deletePassenger(@PathVariable Long id) {
        try {
            adminService.deletePassenger(id);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Yo'lovchini bloklash */
    @PutMapping("/passengers/{id}/block")
    public ResponseEntity<?> blockPassenger(@PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            return ResponseEntity.ok(adminService.blockPassenger(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Online haydovchilar xaritasi uchun */
    @GetMapping("/drivers/online")
    public ResponseEntity<?> onlineDrivers() {
        return ResponseEntity.ok(adminService.getOnlineDriversForMap());
    }

    /** Moliyaviy hisobotlar */
    @GetMapping("/reports/financial")
    public ResponseEntity<?> financialReports(
            @RequestParam(defaultValue = "30") int days) {
        if (days > 365) days = 365; if (days < 1) days = 30;
        return ResponseEntity.ok(adminService.getFinancialReport(days));
    }

    // ─── Operatorlar/adminlar (staff) boshqaruvi — FAQAT ADMIN; OperatorAdminService guard'lar (Feature A) ──

    /** Staff (operator/admin) yaratish — username + parol + rol. Parol BCrypt; hech qachon qaytarilmaydi. */
    @PostMapping("/operators")
    public ResponseEntity<?> createOperator(
            @Valid @RequestBody com.taxi.backend.dto.CreateStaffRequest req) {
        try {
            return ResponseEntity.ok(operatorAdminService.create(
                    req.getName(), req.getPhone(), req.getUsername(), req.getPassword(), req.getRole()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Staff ro'yxati (operator+admin) — parol hash HECH QACHON qaytarilmaydi. */
    @GetMapping("/operators")
    public ResponseEntity<?> getOperators() {
        return ResponseEntity.ok(operatorAdminService.list());
    }

    @GetMapping("/operators/{id}")
    public ResponseEntity<?> getOperator(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(operatorAdminService.get(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    /** Rol va/yoki active o'zgartirish — self-lockout + oxirgi ADMIN guard (server tomonda majburiy). */
    @PutMapping("/operators/{id}")
    public ResponseEntity<?> updateOperator(@AuthenticationPrincipal com.taxi.backend.model.User admin,
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            String role = body.get("role") != null ? String.valueOf(body.get("role")) : null;
            Boolean active = body.get("active") != null ? Boolean.valueOf(String.valueOf(body.get("active"))) : null;
            return ResponseEntity.ok(operatorAdminService.update(admin, id, role, active));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Parol tiklash (yangi BCrypt hash; eski hech qachon ko'rsatilmaydi). */
    @PostMapping("/operators/{id}/set-password")
    public ResponseEntity<?> resetOperatorPassword(@PathVariable Long id,
            @Valid @RequestBody SetPasswordRequest req) {
        try {
            operatorAdminService.resetPassword(id, req.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Parol o'rnatildi"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/operators/{id}")
    public ResponseEntity<?> deleteOperator(@AuthenticationPrincipal com.taxi.backend.model.User admin,
            @PathVariable Long id) {
        try {
            operatorAdminService.delete(admin, id);
            return ResponseEntity.ok(Map.of("message", "O'chirildi"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Admin — istalgan faol buyurtmani bekor qilish */
    @PostMapping("/trips/{tripId}/cancel")
    public ResponseEntity<?> adminCancelTrip(@PathVariable Long tripId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String reason = body != null ? body.getOrDefault("reason", "").trim() : "";
            if (reason.length() < 3)
                return ResponseEntity.badRequest().body(Map.of("error", "Sabab kamida 3 ta belgidan iborat bo'lishi kerak"));
            return ResponseEntity.ok(adminService.adminCancelTrip(tripId, reason));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Band 5 — Admin SEARCHING buyurtmaning tarifini almashtirish. */
    @PutMapping("/trips/{tripId}/tariff")
    public ResponseEntity<?> adminChangeTripTariff(@PathVariable Long tripId,
            @Valid @RequestBody com.taxi.backend.dto.AdminTripTariffRequest req) {
        try {
            return ResponseEntity.ok(adminService.adminChangeTripTariff(tripId, req.getTariffId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Band 5 — Admin SEARCHING buyurtmaning A (olib ketish) va B (manzil) ma'lumotlarini tahrirlash. */
    @PutMapping("/trips/{tripId}/addresses")
    public ResponseEntity<?> adminEditTripAddresses(@PathVariable Long tripId,
            @Valid @RequestBody com.taxi.backend.dto.AdminTripAddressesRequest req) {
        try {
            return ResponseEntity.ok(adminService.adminEditTripAddresses(tripId,
                    req.getFromAddress(), req.getFromLat(), req.getFromLon(),
                    req.getToAddress(), req.getToLat(), req.getToLon()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Band 5 — Admin SEARCHING buyurtmani aniq haydovchiga yo'naltirish.
     * Mavjud data-only dispatch yo'li orqali (PushNotificationService.notifyDriver, type=ORDER_PUSH).
     * Yangi push yo'li yo'q, FSI/order-alert tegmaydi.
     */
    @PostMapping("/trips/{tripId}/reassign")
    public ResponseEntity<?> adminReassignTrip(@PathVariable Long tripId,
            @Valid @RequestBody com.taxi.backend.dto.AdminTripReassignRequest req) {
        try {
            return ResponseEntity.ok(adminService.adminReassignTripToDriver(tripId, req.getDriverId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** OTP Monitor — code never leaves the server; only masked operational status is exposed. */
    @GetMapping("/otp/monitor")
    public ResponseEntity<?> otpMonitor(@AuthenticationPrincipal User admin) {
        if (admin == null || admin.getRole() != com.taxi.backend.enums.Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Faqat ADMIN uchun"));
        }
        java.time.LocalDateTime since = java.time.LocalDateTime.now().minusMinutes(30);
        var codes = otpRepository.findTop50ByCreatedAtAfterOrderByCreatedAtDesc(since);
        var result = codes.stream().map(o -> {
            java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("phone", maskPhone(o.getPhone()));
            m.put("code", null);
            m.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().toString() : null);
            m.put("expiresAt", o.getExpiresAt().toString());
            m.put("isUsed", o.isUsed());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "***" + phone.substring(phone.length() - 2);
    }

    @Operation(summary = "Source statistikasi", description = "CALL vs APP buyurtmalar soni va SMS yuborilgan soni. Qo'ng'iroqdan ilovaga o'tish foizini kuzatish uchun.")
    @GetMapping("/stats/source-summary")
    public ResponseEntity<?> sourceSummary() {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();

        long todayCallTrips = tripRepository.countBySourceAndCreatedAtAfter("CALL", todayStart);
        long todayAppTrips = tripRepository.countBySourceAndCreatedAtAfter("APP", todayStart);

        // SMS soni — Redis dan "sms:invite:*" kalitlarni hisoblash
        long smsSentToday = 0;
        long totalSmsSent = 0;
        try {
            java.util.Set<String> keys = redisTemplate.keys("sms:invite:*");
            smsSentToday = keys != null ? keys.size() : 0;
            // totalSmsSent uchun aniq hisob yo'q — bugungi sonini ko'rsatamiz
            totalSmsSent = smsSentToday;
        } catch (Exception e) {
            // Redis ulanmagan bo'lsa — 0
        }

        return ResponseEntity.ok(Map.of(
                "todayCallTrips", todayCallTrips,
                "todayAppTrips", todayAppTrips,
                "totalSmsSent", totalSmsSent,
                "smsSentToday", smsSentToday));
    }

    @Operation(summary = "Foydalanuvchi parolini o'rnatish", description = "Admin operator yoki boshqa ADMIN parolini belgilaydi/tiklaydi")
    @PostMapping("/users/{id}/set-password")
    public ResponseEntity<?> setUserPassword(@PathVariable Long id,
            @Valid @RequestBody SetPasswordRequest req) {
        try {
            authService.setPassword(id, req.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Parol muvaffaqiyatli o'rnatildi"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
}
