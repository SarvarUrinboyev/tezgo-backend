package com.taxi.backend.service;

import com.taxi.backend.enums.Role;
import com.taxi.backend.enums.ServiceType;
import com.taxi.backend.model.*;
import com.taxi.backend.repository.*;
import com.taxi.backend.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Value("${sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    // Google Play tekshiruvchisi (reviewer) uchun demo-login. Faqat sozlangan
    // raqam(lar) + qat'iy OTP ishlaydi; SMS yuborilmaydi. Bo'sh bo'lsa — butunlay
    // o'chiq. Vergul bilan bir nechta raqam (passenger + driver demo) bo'lishi mumkin.
    // Server env: REVIEW_TEST_PHONE / REVIEW_TEST_OTP (tezyol.env, gitga emas).
    @Value("${review.test.phone:}")
    private String reviewTestPhone;

    @Value("${review.test.otp:}")
    private String reviewTestOtp;

    // Rozilik yozuvi konfiguratsiyasi (UZ qonun / Buyruq No.3478). Operator nomi va STIR
    // tezyol.env orqali to'ldiriladi (CONSENT_OPERATOR_NAME / CONSENT_OPERATOR_TIN).
    @Value("${consent.policy-version:2026-06-16}")
    private String consentPolicyVersion;

    @Value("${consent.operator.name:}")
    private String consentOperatorName;

    @Value("${consent.operator.tin:}")
    private String consentOperatorTin;

    @Value("${consent.cross-border:false}")
    private boolean consentCrossBorder;

    private static final String CONSENT_PURPOSES =
            "Hisob yaratish va kirish (OTP); buyurtmani qabul qilish va safarni amalga oshirish; "
            + "yo'lovchi va haydovchini bog'lash; bildirishnomalar; xavfsizlik va firibgarlikning oldini olish.";
    private static final String CONSENT_DATA_CATEGORIES =
            "Telefon raqami; ism; joylashuv (GPS); qurilma ma'lumotlari; "
            + "(haydovchi) pasport, tug'ilgan sana, manzil, avtomobil ma'lumotlari, balans, safar tarixi.";
    private static final String CONSENT_VALIDITY_TERM =
            "Hisob faol bo'lgan davr yoki rozilik qaytarib olinmaguncha.";

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final OtpRepository otpRepository;
    private final DriverServiceRepository driverServiceRepository;
    private final JwtService jwtService;
    private final SmsService smsService;
    private final ConsentLogService consentLogService;

    public AuthService(UserRepository userRepository,
            DriverRepository driverRepository,
            OtpRepository otpRepository,
            DriverServiceRepository driverServiceRepository,
            JwtService jwtService,
            SmsService smsService,
            ConsentLogService consentLogService) {
        this.userRepository = userRepository;
        this.driverRepository = driverRepository;
        this.otpRepository = otpRepository;
        this.driverServiceRepository = driverServiceRepository;
        this.jwtService = jwtService;
        this.smsService = smsService;
        this.consentLogService = consentLogService;
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);

    /** Google Play reviewer demo raqami sozlanganmi (env orqali; vergulli ro'yxat) */
    private boolean isReviewTestPhone(String phone) {
        if (reviewTestPhone == null || reviewTestPhone.isBlank() || phone == null) {
            return false;
        }
        for (String p : reviewTestPhone.split(",")) {
            if (p.trim().equals(phone)) {
                return true;
            }
        }
        return false;
    }

    /** Reviewer demo-login: sozlangan raqam + qat'iy OTP mos kelsa true */
    private boolean isReviewTestLogin(String phone, String code) {
        return isReviewTestPhone(phone)
                && reviewTestOtp != null && !reviewTestOtp.isBlank()
                && reviewTestOtp.equals(code);
    }

    /** SMS OTP yuborish — kriptografik xavfsiz random kod */
    public String sendOtp(String phone) {
        otpRepository.invalidateAllByPhone(phone);

        // Google Play reviewer demo raqami — real SMS yubormaymiz, fixed OTP
        // verifyOtp'da qabul qilinadi. Eskizga so'rov ketmaydi.
        if (isReviewTestPhone(phone)) {
            log.warn("⚠️ REVIEW demo raqamiga OTP so'rovi — SMS yuborilmaydi: {}", phone);
            return "OTP yuborildi";
        }

        // Har doim kriptografik xavfsiz random kod (SecureRandom)
        // Dev rejimda ham random — 111111 HECH QACHON ishlatilmaydi
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));

        OtpCode otp = new OtpCode(phone, code, LocalDateTime.now().plusMinutes(5));
        otpRepository.save(otp);

        if (smsEnabled) {
            boolean sent;
            try {
                sent = smsService.sendOtp(phone, code);
            } catch (Exception e) {
                log.error("Eskiz SMS xatolik: {} — {}", phone, e.getMessage());
                sent = false;
            }
            if (!sent) {
                // Soxta "yuborildi" o'rniga rostini qaytaramiz — mijoz qayta urinadi.
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "SMS yuborib bo'lmadi. Birozdan keyin qayta urinib ko'ring.");
            }
            log.info("OTP yuborildi: {} ga SMS", phone);
        } else {
            // DEV rejim: SMS yo'q, OTP konsolda ko'rsatiladi (test uchun)
            // Production'da SMS_ENABLED=true qilinadi va bu branch ishlamaydi
            log.info("OTP [DEV MODE]: {} => {}", phone, code);
        }

        return "OTP yuborildi";
    }

    /** OTP tekshirish va JWT qaytarish */
    @Transactional
    public Map<String, Object> verifyOtp(String phone, String code, Role role) {
        log.info("OTP verify: phone={}, smsEnabled={}", phone, smsEnabled);

        // Test rejim: faqat smsEnabled=false VA local/dev profilida ishlaydi
        // Production profilida test kodlar HECH QACHON ishlamaydi
        boolean isProductionProfile = "prod".equalsIgnoreCase(activeProfile)
                || "production".equalsIgnoreCase(activeProfile);
        // Reviewer demo-login — prod'da ham ishlaydi, lekin FAQAT sozlangan bitta
        // raqam + qat'iy OTP uchun. Boshqa hech kimga ta'sir qilmaydi.
        boolean isReviewLogin = isReviewTestLogin(phone, code);
        boolean isTestCode = !smsEnabled && !isProductionProfile
                && ("111111".equals(code) || (role == Role.ADMIN && "123456".equals(code)));

        if (isReviewLogin) {
            log.warn("⚠️ REVIEW demo login — fixed OTP qabul qilindi: phone={}, role={}", phone, role);
            otpRepository.findTopByPhoneAndIsUsedFalseOrderByCreatedAtDesc(phone)
                    .ifPresent(otp -> { otp.setUsed(true); otpRepository.save(otp); });
        } else if (isTestCode) {
            log.warn("⚠️ OTP [TEST MODE]: test kod qabul qilindi — phone={}, role={}. Production'da SMS_ENABLED=true qiling!", phone, role);
            // Test kodda OTP record shart emas, mavjud bo'lsa ishlatilgan deb belgilaymiz
            otpRepository.findTopByPhoneAndIsUsedFalseOrderByCreatedAtDesc(phone)
                    .ifPresent(otp -> { otp.setUsed(true); otpRepository.save(otp); });
        } else {
            // Real OTP tekshiruvi
            OtpCode otp = otpRepository
                    .findTopByPhoneAndIsUsedFalseOrderByCreatedAtDesc(phone)
                    .orElseThrow(() -> new RuntimeException("OTP topilmadi yoki u allaqachon ishlatilgan"));

            if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new RuntimeException("OTP muddati o'tib ketgan");
            }

            if (!otp.getCode().equals(code)) {
                log.warn("OTP noto'g'ri: phone={}, kutilgan={}, kiritilgan={}", phone, otp.getCode(), code);
                throw new RuntimeException("OTP noto'g'ri");
            }

            otp.setUsed(true);
            otpRepository.save(otp);
        }

        // Yangi yoki mavjud foydalanuvchi
        User user = userRepository.findByPhone(phone).orElseGet(() -> {
            User u = new User();
            u.setPhone(phone);
            u.setRole(role);
            u.setName(role == Role.DRIVER ? "Haydovchi" : "Yo'lovchi");
            return userRepository.save(u);
        });

        // Agar mavjud foydalanuvchi Haydovchi dasturiga kirmoqchi bo'lsa roliga DRIVER qo'shamiz
        // XAVFSIZLIK: ADMIN rolini o'zgartirish mumkin emas
        if (role == Role.DRIVER && user.getRole() != Role.DRIVER && user.getRole() != Role.ADMIN) {
            user.setRole(Role.DRIVER);
            userRepository.save(user);
        }

        // Haydovchi uchun Driver record yaratish
        if (role == Role.DRIVER && driverRepository.findByUserId(user.getId()).isEmpty()) {
            Driver driver = new Driver();
            driver.setUser(user);
            // DB sequence — concurrent registrations uchun atomik va takrorlanmaydigan
            driver.setDriverCode(driverRepository.nextDriverCode());
            driverRepository.save(driver);
            initDriverServices(driver);
        }

        // Rozilik yozuvini saqlash (UZ qonun talabi). Alohida tranzaksiyada — login buzilmaydi.
        recordLoginConsent(user);

        String token = jwtService.generateToken(user.getPhone(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getPhone(), user.getRole().name());

        // Driver ma'lumotlari
        var driverOpt = driverRepository.findByUserId(user.getId());
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("token", token);
        result.put("refreshToken", refreshToken);
        result.put("userId", user.getId());
        result.put("phone", user.getPhone());
        result.put("role", user.getRole().name());
        result.put("name", user.getName() != null ? user.getName() : "");
        result.put("isRegistered",
                driverOpt.map(d -> d.getCarNumber() != null && !d.getCarNumber().isEmpty()).orElse(false));
        if (driverOpt.isPresent()) {
            Driver d = driverOpt.get();
            result.put("driverId", d.getId());
            result.put("status", d.getStatus() != null ? d.getStatus().name() : "PENDING");
            result.put("balance", d.getBalance() != null ? d.getBalance() : 0L);
        }
        return result;
    }

    /**
     * Foydalanuvchi kirishda bergan rozilikning isbotlanadigan yozuvini saqlaydi
     * (UZ "Shaxsga doir ma'lumotlar" qonuni / Buyruq No.3478). Xato HECH QACHON loginni buzmaydi.
     */
    private void recordLoginConsent(User user) {
        try {
            ConsentLog c = new ConsentLog();
            c.setPhone(user.getPhone());
            c.setRole(user.getRole() != null ? user.getRole().name() : null);
            c.setConsentType("LOGIN");
            c.setPolicyVersion(consentPolicyVersion);
            c.setPurposes(CONSENT_PURPOSES);
            c.setDataCategories(CONSENT_DATA_CATEGORIES);
            c.setValidityTerm(CONSENT_VALIDITY_TERM);
            c.setOperatorName(consentOperatorName);
            c.setOperatorTin(consentOperatorTin);
            c.setThirdPartyAllowed(Boolean.TRUE);
            c.setCrossBorderAllowed(consentCrossBorder);
            c.setPublicDistributionAllowed(Boolean.FALSE);
            consentLogService.record(c);
        } catch (Exception e) {
            log.warn("Rozilik yozuvini saqlashda xato (login davom etadi): {}", e.getMessage());
        }
    }

    /** Haydovchi pasport/biometrik ma'lumotlari uchun ALOHIDA rozilik yozuvi (UZ qonun). */
    private void recordDriverDocsConsent(User user) {
        try {
            ConsentLog c = new ConsentLog();
            c.setPhone(user.getPhone());
            c.setRole(user.getRole() != null ? user.getRole().name() : "DRIVER");
            c.setConsentType("DRIVER_DOCS");
            c.setPolicyVersion(consentPolicyVersion);
            c.setPurposes("Haydovchini ro'yxatdan o'tkazish: pasport va texnik pasport ma'lumotlarini tekshirish va saqlash.");
            c.setDataCategories("Pasport seriyasi/raqami, tug'ilgan sana, manzil, avtomobil va texnik pasport ma'lumotlari.");
            c.setValidityTerm(CONSENT_VALIDITY_TERM);
            c.setOperatorName(consentOperatorName);
            c.setOperatorTin(consentOperatorTin);
            c.setThirdPartyAllowed(Boolean.TRUE);
            c.setCrossBorderAllowed(Boolean.FALSE);
            c.setPublicDistributionAllowed(Boolean.FALSE);
            consentLogService.record(c);
        } catch (Exception e) {
            log.warn("Haydovchi rozilik yozuvini saqlashda xato: {}", e.getMessage());
        }
    }

    /** Haydovchi uchun barcha xizmatlarni boshlang'ich qiymat bilan yaratish */
    private void initDriverServices(Driver driver) {
        for (ServiceType type : ServiceType.values()) {
            com.taxi.backend.model.DriverService ds = new com.taxi.backend.model.DriverService(
                    driver, type, type.getDefaultPriceTiyin());
            driverServiceRepository.save(ds);
        }
    }

    /** Telefon raqam bo'yicha foydalanuvchini topish (admin tekshiruvi uchun) */
    public User findUserByPhone(String phone) {
        return userRepository.findByPhone(phone).orElse(null);
    }

    /** Pasport allaqachon boshqa haydovchida ro'yxatdan o'tganligini tekshirish */
    public boolean isPassportAlreadyRegistered(String series, String number) {
        return driverRepository.countByPassportSeriesAndPassportNumber(series, number) > 0;
    }

    /** Username/parol orqali kirish — faqat ADMIN va OPERATOR uchun */
    public Map<String, Object> loginWithPassword(String username, String password) {
        User user = userRepository.findByUsernameIgnoreCase(username.trim()).orElse(null);

        // Foydalanuvchi topilmadi yoki paroli yo'q — bir xil xato (brute-force uchun ma'lumot berilmaydi)
        if (user == null || user.getPasswordHash() == null
                || !PASSWORD_ENCODER.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("LOGIN_FAILED");
        }

        if (user.getRole() != Role.ADMIN && user.getRole() != Role.OPERATOR) {
            throw new IllegalStateException("WRONG_ROLE");
        }

        String token = jwtService.generateToken(user.getPhone(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getPhone(), user.getRole().name());

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("token", token);
        result.put("refreshToken", refreshToken);
        result.put("role", user.getRole().name());
        result.put("name", user.getName() != null ? user.getName() : username);
        result.put("phone", user.getPhone());
        return result;
    }

    /** Parolni o'zgartirish — ADMIN yoki OPERATOR foydalanuvchi o'zi uchun */
    @Transactional
    public void changePassword(String phone, String currentPassword, String newPassword) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("Foydalanuvchi topilmadi"));

        if (user.getPasswordHash() == null || !PASSWORD_ENCODER.matches(currentPassword, user.getPasswordHash())) {
            throw new RuntimeException("Joriy parol noto'g'ri");
        }

        validateNewPassword(newPassword);
        user.setPasswordHash(PASSWORD_ENCODER.encode(newPassword));
        userRepository.save(user);
        log.info("Parol o'zgartirildi: phone={}", phone);
    }

    /** Admin tomonidan foydalanuvchi parolini o'rnatish */
    @Transactional
    public void setPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Foydalanuvchi topilmadi"));

        validateNewPassword(newPassword);
        user.setPasswordHash(PASSWORD_ENCODER.encode(newPassword));
        userRepository.save(user);
        log.info("Admin tomonidan parol o'rnatildi: userId={}", userId);
    }

    private void validateNewPassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Parol kamida 8 belgi bo'lishi kerak");
        }
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasDigit) {
            throw new IllegalArgumentException("Parolda kamida 1 ta raqam bo'lishi kerak");
        }
    }

    /** Texnik pasport allaqachon boshqa haydovchida ro'yxatdan o'tganligini tekshirish */
    public boolean isTechPassportAlreadyRegistered(String techPassport) {
        return driverRepository.countByTechPassportNumber(techPassport) > 0;
    }

    /** Haydovchi to'liq ro'yxatdan o'tishi */
    @Transactional
    public Map<String, Object> registerDriver(String phone, String name,
            String birthDate, String address, String passportSeries, String passportNumber,
            String carModel, String carNumber, String carColor, Integer carYear, String techPassportNumber) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("Avval OTP orqali kirish kerak"));

        user.setName(name);
        userRepository.save(user);

        Driver driver = driverRepository.findByUserId(user.getId()).orElseGet(() -> {
            Driver d = new Driver();
            d.setUser(user);
            d.setDriverCode(driverRepository.nextDriverCode());
            return d;
        });

        driver.setCarModel(carModel);
        driver.setCarNumber(carNumber);
        driver.setCarColor(carColor);
        driver.setCarYear(carYear);
        driver.setPassportSeries(passportSeries);
        driver.setPassportNumber(passportNumber);
        driver.setBirthDate(birthDate);
        driver.setAddress(address);
        driver.setTechPassportNumber(techPassportNumber);
        driverRepository.save(driver);

        // Pasport/biometrik ma'lumotlar uchun alohida rozilik yozuvi (UZ qonun)
        recordDriverDocsConsent(user);

        return Map.of("message", "Ma'lumotlar saqlandi. Admin tasdiqlashini kuting.");
    }
}
