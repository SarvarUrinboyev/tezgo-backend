package com.taxi.backend.controller;

import com.taxi.backend.dto.*;
import com.taxi.backend.enums.Role;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.UserRepository;
import com.taxi.backend.security.JwtService;
import com.taxi.backend.service.AuthService;
import com.taxi.backend.service.GovApiService;
import com.taxi.backend.service.OtpRateLimitService;
import com.taxi.backend.service.OtpVerifyRateLimitService;
import com.taxi.backend.service.TokenBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Auth", description = "Autentifikatsiya — OTP, ro'yxatdan o'tish, token boshqarish")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final OtpRateLimitService rateLimitService;
    private final GovApiService govApiService;
    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;
    private final OtpVerifyRateLimitService verifyRateLimitService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, OtpRateLimitService rateLimitService,
                          GovApiService govApiService, JwtService jwtService,
                          TokenBlacklistService blacklistService,
                          OtpVerifyRateLimitService verifyRateLimitService,
                          UserRepository userRepository) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
        this.govApiService = govApiService;
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
        this.verifyRateLimitService = verifyRateLimitService;
        this.userRepository = userRepository;
    }

    @Operation(summary = "Admin/Operator login", description = "Username va parol orqali kirish — faqat ADMIN va OPERATOR rollari uchun")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Muvaffaqiyatli: {token, refreshToken, role, name, phone}"),
        @ApiResponse(responseCode = "401", description = "Login yoki parol noto'g'ri"),
        @ApiResponse(responseCode = "403", description = "Bu login turi faqat admin va operator uchun")
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            return ResponseEntity.ok(authService.loginWithPassword(req.getUsername(), req.getPassword()));
        } catch (IllegalStateException e) {
            // WRONG_ROLE — haydovchi/yo'lovchi username/parol bilan kirishga urindi
            return ResponseEntity.status(403).body(Map.of("error", "Bu login turi faqat admin va operator uchun"));
        } catch (Exception e) {
            // LOGIN_FAILED yoki boshqa xato — ma'lumot berilmaydi (brute-force himoyasi)
            return ResponseEntity.status(401).body(Map.of("error", "Login yoki parol noto'g'ri"));
        }
    }

    @Operation(summary = "Parolni o'zgartirish", description = "ADMIN yoki OPERATOR o'z parolini o'zgartiradi")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Parol muvaffaqiyatli o'zgartirildi"),
        @ApiResponse(responseCode = "400", description = "Validatsiya xatosi (parol juda qisqa yoki raqam yo'q)"),
        @ApiResponse(responseCode = "401", description = "Joriy parol noto'g'ri")
    })
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequest req,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.springframework.security.core.userdetails.UserDetails userDetails) {
        if (userDetails == null)
            return ResponseEntity.status(401).body(Map.of("error", "Autentifikatsiya talab qilinadi"));
        try {
            authService.changePassword(userDetails.getUsername(), req.getCurrentPassword(), req.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Parol muvaffaqiyatli o'zgartirildi"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "OTP yuborish", description = "Telefon raqamga SMS orqali 6 xonali OTP kod yuboradi. Bitta raqamga 15 daqiqada 5 ta so'rov.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OTP yuborildi"),
        @ApiResponse(responseCode = "429", description = "Rate limit — juda ko'p so'rov")
    })
    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@Valid @RequestBody SendOtpRequest req) {
        rateLimitService.checkLimit(req.getPhone());
        return ResponseEntity.ok(Map.of("message", authService.sendOtp(req.getPhone())));
    }

    @Operation(summary = "OTP tekshirish", description = "OTP kodni tekshiradi va JWT access + refresh token qaytaradi. Brute-force himoyasi bilan.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token qaytarildi: {token, refreshToken, role}"),
        @ApiResponse(responseCode = "400", description = "OTP noto'g'ri yoki muddati tugagan"),
        @ApiResponse(responseCode = "403", description = "ADMIN bo'lmagan foydalanuvchi admin sifatida kirishga urinmoqda"),
        @ApiResponse(responseCode = "429", description = "Juda ko'p urinish")
    })
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequest req,
                                        jakarta.servlet.http.HttpServletRequest httpReq) {
        String clientIp = extractClientIp(httpReq);
        verifyRateLimitService.checkLimit(req.getPhone(), clientIp);

        // XAVFSIZLIK: Rolni validatsiya qilish
        Role role;
        try {
            role = Role.valueOf(req.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Noto'g'ri rol"));
        }

        // ADMIN roli bilan kirish — faqat MAVJUD admin foydalanuvchilar uchun
        // Yangi ADMIN yaratish mumkin EMAS (faqat DataInitializer orqali)
        if (role == Role.ADMIN) {
            var existingUser = authService.findUserByPhone(req.getPhone());
            if (existingUser == null || existingUser.getRole() != Role.ADMIN) {
                log.warn("XAVFSIZLIK: ADMIN bo'lmagan foydalanuvchi ADMIN sifatida kirishga urinmoqda — phone={}, IP={}",
                        req.getPhone(), clientIp);
                return ResponseEntity.status(403).body(Map.of("error", "Ruxsat yo'q"));
            }
        }

        return ResponseEntity.ok(authService.verifyOtp(req.getPhone(), req.getCode(), role));
    }

    /** Client IP manzilini olish (proxy orqasida ham ishlaydi) */
    private String extractClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return request.getRemoteAddr();
    }

    @Operation(summary = "Captcha olish", description = "Pasport tekshirish uchun captcha rasmini qaytaradi (soliq.uz)")
    @GetMapping("/passport-captcha")
    public ResponseEntity<?> getPassportCaptcha() {
        return ResponseEntity.ok(govApiService.fetchCaptcha());
    }

    @Operation(summary = "Pasport tekshirish", description = "Pasport seriya/raqam orqali fuqaro ma'lumotlarini my.soliq.uz dan oladi")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Fuqaro topildi: {fullName, birthDate, ...}"),
        @ApiResponse(responseCode = "400", description = "Pasport boshqa haydovchida ro'yxatdan o'tgan"),
        @ApiResponse(responseCode = "404", description = "Fuqaro topilmadi")
    })
    @PostMapping("/verify-passport")
    public ResponseEntity<?> verifyPassport(@Valid @RequestBody VerifyPassportRequest req) {
        String series = req.getSeries().toUpperCase().trim();
        String number = req.getNumber().trim();

        if (authService.isPassportAlreadyRegistered(series, number))
            return ResponseEntity.badRequest().body(Map.of("error", "Bu pasport boshqa haydovchida ro'yxatdan o'tgan"));

        var result = govApiService.lookupPassport(series, number, req.getBirthDate(), req.getCaptcha(), req.getSessionId());
        if (result == null)
            return ResponseEntity.status(404).body(Map.of("error",
                    "Fuqaro topilmadi. Captcha noto'g'ri yoki seriya/raqam/sana xato"));
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Avtomobil tekshirish", description = "Texnik pasport va davlat raqami orqali avtomobil ma'lumotlarini oladi")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Avtomobil topildi: {model, color, year, ...}"),
        @ApiResponse(responseCode = "400", description = "Format noto'g'ri yoki avtomobil boshqa haydovchida"),
        @ApiResponse(responseCode = "404", description = "Avtomobil topilmadi")
    })
    @PostMapping("/verify-vehicle")
    public ResponseEntity<?> verifyVehicle(@Valid @RequestBody VerifyVehicleRequest req) {
        String tp = req.getTechPassport().toUpperCase().trim().replaceAll("\\s+", "");
        if (!tp.matches("[A-Z]{3}\\d{7}"))
            return ResponseEntity.badRequest().body(Map.of("error", "Texnik pasport formati noto'g'ri (AAG1234567)"));

        if (authService.isTechPassportAlreadyRegistered(tp))
            return ResponseEntity.badRequest().body(Map.of("error", "Bu avtomobil boshqa haydovchida ro'yxatdan o'tgan"));

        String techSeries = tp.substring(0, 3);
        String techNumber = tp.substring(3);
        var result = govApiService.lookupVehicleKapital(req.getPlateNumber(), techSeries, techNumber);

        if (result == null) {
            log.warn("[verify-vehicle] Kapital API ishlamadi, test data ishlatilmoqda");
            result = govApiService.lookupVehicle(tp);
        }

        if (result == null)
            return ResponseEntity.status(404).body(Map.of("error",
                    "Avtomobil topilmadi. Davlat raqami yoki texnik pasport ma'lumotlarini tekshiring"));
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Haydovchi ro'yxatdan o'tishi", description = "Haydovchi to'liq ma'lumotlarini saqlaydi. JWT tokendan telefon avtomatik olinadi.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ro'yxatdan o'tdi: {token, driver, ...}"),
        @ApiResponse(responseCode = "400", description = "Validatsiya xatosi yoki pasport duplicate")
    })
    @PostMapping("/register/driver")
    public ResponseEntity<?> registerDriver(@Valid @RequestBody RegisterDriverRequest req,
                                            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Telefon: Authorization headerdan (JWT) yoki body dan
        String phone = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (jwtService.isValid(token)) phone = jwtService.extractPhone(token);
            } catch (Exception ignored) { /* expired token — body dan phone olinadi */ }
        }
        if (phone == null) phone = req.getPhone();
        if (phone == null || phone.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Avval OTP orqali kirish kerak"));

        // Passport duplicate tekshiruvi
        if (req.getPassportSeries() != null && !req.getPassportSeries().isBlank()
                && req.getPassportNumber() != null && !req.getPassportNumber().isBlank()) {
            if (authService.isPassportAlreadyRegistered(req.getPassportSeries().toUpperCase().trim(), req.getPassportNumber().trim()))
                return ResponseEntity.badRequest().body(Map.of("error", "Bu pasport boshqa haydovchida ro'yxatdan o'tgan"));
        }

        return ResponseEntity.ok(authService.registerDriver(
                phone, req.getName(), req.getBirthDate(), req.getAddress(),
                req.getPassportSeries(), req.getPassportNumber(),
                req.getCarModel(), req.getCarNumber(), req.getCarColor(),
                req.getCarYear(), req.getTechPassportNumber()));
    }

    @Operation(summary = "Token yangilash", description = "Refresh token orqali yangi access token olish. Access token 15 daqiqa, refresh token 7 kun.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Yangi access token: {token}"),
        @ApiResponse(responseCode = "401", description = "Refresh token noto'g'ri yoki muddati tugagan")
    })
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody com.taxi.backend.dto.RefreshTokenRequest req) {
        String refreshToken = req.getRefreshToken();

        try {
            if (!jwtService.isValid(refreshToken))
                return ResponseEntity.status(401).body(Map.of("error", "Refresh token noto'g'ri"));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Refresh token muddati tugagan"));
        }

        if (!"refresh".equals(jwtService.extractType(refreshToken)))
            return ResponseEntity.status(401).body(Map.of("error", "Bu refresh token emas"));

        String phone = jwtService.extractPhone(refreshToken);
        String role = jwtService.extractRole(refreshToken);

        String newAccessToken = jwtService.generateToken(phone, role);
        return ResponseEntity.ok(Map.of("token", newAccessToken));
    }

    @Operation(summary = "Tizimdan chiqish", description = "Access va refresh tokenlarni bekor qiladi (Redis blacklist)")
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                     @RequestBody(required = false) Map<String, String> body) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            return ResponseEntity.badRequest().body(Map.of("error", "Token kerak"));

        // Access tokenni blacklist qilish
        String token = authHeader.substring(7);
        blacklistToken(token);

        // Refresh tokenni ham blacklist qilish (agar yuborilgan bo'lsa)
        if (body != null && body.containsKey("refreshToken")) {
            String refreshToken = body.get("refreshToken");
            if (refreshToken != null && !refreshToken.isBlank()) {
                try {
                    blacklistToken(refreshToken);
                } catch (Exception ignored) { }
            }
        }

        return ResponseEntity.ok(Map.of("message", "Tizimdan chiqdingiz"));
    }

    private void blacklistToken(String token) {
        try {
            String jti = jwtService.extractJti(token);
            java.util.Date exp = jwtService.extractExpiration(token);
            if (jti != null && exp != null) {
                long ttl = (exp.getTime() - System.currentTimeMillis()) / 1000;
                if (ttl > 0) blacklistService.blacklist(jti, ttl);
            }
        } catch (Exception e) {
            log.debug("Token blacklist xatolik: {}", e.getMessage());
        }
    }

    @Value("${app.support.phone:+998935859985}")
    private String supportPhone;

    @Value("${app.support.telegram:https://t.me/developing_man}")
    private String supportTelegram;

    @Value("${app.commission.percent:10}") // TezYol commission rate — change here only
    private double commissionPercent;

    @Operation(summary = "Ilova konfiguratsiyasi", description = "Ilova uchun umumiy sozlamalar: support telefon, telegram, komissiya foizi")
    @GetMapping("/config")
    public ResponseEntity<?> getAppConfig() {
        return ResponseEntity.ok(Map.of(
            "supportPhone", supportPhone,
            "supportTelegram", supportTelegram,
            "commissionPercent", commissionPercent,
            "appVersion", "1.0.0"
        ));
    }
}
