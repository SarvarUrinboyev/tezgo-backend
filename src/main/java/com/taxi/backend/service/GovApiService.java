package com.taxi.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Davlat bazalari bilan integratsiya servisi.
 *
 * Pasport: my.soliq.uz
 *   - GET /api/auth/captcha/generate  → captchaId + captchaImageBase64 (PNG rasm)
 *   - POST /api/search-tin-api/individual/search-by-passport-data
 *     headers: x-captcha-id, x-captcha-value (foydalanuvchi kiritgan javob)
 *
 * YPX: hozircha test ma'lumotlar.
 */
@Service
public class GovApiService {

    private static final Logger log = LoggerFactory.getLogger(GovApiService.class);

    private static final String SOLIQ3_BASE = "https://my.soliq.uz";
    private static final String CAPTCHA_URL = SOLIQ3_BASE + "/api/auth/captcha/generate";
    private static final String SEARCH_URL  = SOLIQ3_BASE + "/api/search-tin-api/individual/search-by-passport-data";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36";

    private static final ObjectMapper mapper = new ObjectMapper();

    private final CaptchaService captchaService;

    // sessionId → {captchaId, HttpClient} — bir xil client cookie saqlaydi
    private final Map<String, String>     sessionCaptchaIds = new ConcurrentHashMap<>();
    private final Map<String, HttpClient> sessionClients    = new ConcurrentHashMap<>();

    public GovApiService(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    // soliq.uz O'zbek IP'larini talab qiladi (xorijiy server bloklangan). Agar SOLIQ_PROXY_HOST
    // berilgan bo'lsa, soliq.uz so'rovlari O'zbekistondagi proxy orqali yo'naltiriladi.
    @Value("${soliq.proxy.host:}")
    private String proxyHost;
    @Value("${soliq.proxy.port:8888}")
    private int proxyPort;

    /**
     * soliq.uz uchun HttpClient.Builder — SOLIQ_PROXY_HOST berilgan bo'lsa O'zbek proxy orqali.
     * faqat soliq.uz so'rovlari shu orqali ketadi; boshqa hamma narsa to'g'ridan-to'g'ri.
     */
    private HttpClient.Builder soliqClientBuilder() {
        HttpClient.Builder b = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS);
        if (proxyHost != null && !proxyHost.isBlank()) {
            b.proxy(ProxySelector.of(new InetSocketAddress(proxyHost.trim(), proxyPort)));
        }
        return b;
    }

    // ── CAPTCHA OLISH ─────────────────────────────────────────────────────
    /**
     * my.soliq.uz dan matematik captcha rasmini oladi.
     * captchaId serverda saqlanadi — lookupPassport da ishlatiladi.
     * Agar my.soliq.uz ishlamasa → o'zimizning AWT captcha (fallback).
     */
    public Map<String, Object> fetchCaptcha() {
        // Eski yozuvlarni tozalash
        if (sessionCaptchaIds.size() > 500) { sessionCaptchaIds.clear(); sessionClients.clear(); }

        String sessionId = UUID.randomUUID().toString();
        try {
            CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            // smart_top=1 cookieni CookieManager'ga oldindan qo'yamiz (manual header emas)
            HttpCookie smartTop = new HttpCookie("smart_top", "1");
            smartTop.setDomain(".my.soliq.uz");
            smartTop.setPath("/");
            smartTop.setVersion(0);
            cookieManager.getCookieStore().add(URI.create(SOLIQ3_BASE), smartTop);

            HttpClient client = soliqClientBuilder()
                    .cookieHandler(cookieManager)
                    .build();

            // 1-qadam: asosiy sahifani ochib session cookie'larini olamiz (brauzer kabi)
            HttpResponse<Void> pageRes = client.send(HttpRequest.newBuilder()
                    .uri(URI.create(SOLIQ3_BASE + "/individual/cabinet/find-tin/home"))
                    .header("User-Agent", UA)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .GET().build(), HttpResponse.BodyHandlers.discarding());
            log.debug("page status={} cookies={}", pageRes.statusCode(),
                    cookieManager.getCookieStore().getCookies());

            // 2-qadam: captcha olish
            HttpResponse<String> res = client.send(HttpRequest.newBuilder()
                    .uri(URI.create(CAPTCHA_URL))
                    .header("User-Agent", UA)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Referer", SOLIQ3_BASE + "/individual/cabinet/find-tin/home")
                    .GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.debug("captcha status={} cookies={}", res.statusCode(),
                    cookieManager.getCookieStore().getCookies());

            Map<String, Object> json = mapper.readValue(res.body(), new TypeReference<>() {});
            String captchaId     = json.get("captchaId").toString();
            String rawBase64     = json.get("captchaImageBase64").toString();
            // React Native Image uri uchun data URI prefix kerak
            String captchaBase64 = rawBase64.startsWith("data:") ? rawBase64
                    : "data:image/png;base64," + rawBase64;
            log.debug("captchaId={}", captchaId);

            // captchaId va clientni keyinroq ishlatish uchun saqlaymiz
            sessionCaptchaIds.put(sessionId, captchaId);
            sessionClients.put(sessionId, client);

            return Map.of("captchaBase64", captchaBase64, "sessionId", sessionId);

        } catch (Exception e) {
            log.error("my.soliq.uz captcha xatolik: {} -> AWT captcha", e.getMessage());
            return captchaService.generate(sessionId);
        }
    }

    // ── PASPORT TEKSHIRISH ────────────────────────────────────────────────
    /**
     * Foydalanuvchi kiritgan captcha javobi bilan my.soliq.uz dan fuqaro ma'lumotini oladi.
     * Agar my.soliq.uz ishlamasa → test ma'lumotlarga qaytadi.
     */
    public Map<String, Object> lookupPassport(String series, String number,
                                               String birthDate, String captchaValue, String sessionId) {
        if (series == null || number == null || birthDate == null) return null;
        series    = series.trim().toUpperCase();
        number    = number.trim();
        birthDate = birthDate.trim();

        // MM.DD.YYYY → DD.MM.YYYY avtomatik konversiya
        String[] dp = birthDate.split("\\.");
        if (dp.length == 3) {
            int p1 = Integer.parseInt(dp[0]), p2 = Integer.parseInt(dp[1]);
            if (p2 > 12 && p1 <= 12) {
                // MM.DD.YYYY formatida kelgan — almashtir
                birthDate = String.format("%02d.%02d.%s", p2, p1, dp[2]);
            }
        }

        log.debug("lookupPassport: series={} number={} birthDate={} captcha={}", series, number, birthDate, captchaValue);

        if (!series.matches("[A-Z]{2}") || !number.matches("\\d{7}")) return null;

        // my.soliq.uz captchaId ni olish
        String captchaId = sessionCaptchaIds.remove(sessionId);
        HttpClient storedClient = sessionClients.remove(sessionId);

        log.debug("lookup: sessionId={} captchaId={} hasClient={} storedSessions={}",
                sessionId, captchaId, storedClient != null, sessionCaptchaIds.keySet());

        if (captchaId != null) {
            // ── REAL API ─────────────────────────────────────────────
            try {
                // CookieManager OLMAGAN yangi client — browser kabi faqat smart_top=1 yuborish uchun.
                // soliq.uz so'rovi ham O'zbek proxy orqali (captcha bilan bir xil IP'dan kelishi shart).
                HttpClient searchClient = soliqClientBuilder().build();

                log.debug("x-captcha-id={} x-captcha-value={}", captchaId, captchaValue);

                // docCode="01" → O'zbekiston pasporti (F12 dan aniqlandi)
                String reqBody = mapper.writeValueAsString(Map.of(
                        "pasSer",   series,
                        "pasNum",   number,
                        "pasDob",   birthDate,
                        "docCode",  "01"
                ));

                HttpResponse<String> res = searchClient.send(HttpRequest.newBuilder()
                        .uri(URI.create(SEARCH_URL))
                        .header("Content-Type", "application/json")
                        .header("User-Agent", UA)
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Accept-Language", "uz-UZ,uz;q=0.9,en-US;q=0.8,en;q=0.7")
                        .header("Referer", SOLIQ3_BASE + "/individual/cabinet/find-tin/home")
                        .header("Origin", SOLIQ3_BASE)
                        .header("language", "uz_cyrl")
                        .header("Cookie", "smart_top=1")
                        .header("x-captcha-id", captchaId)
                        .header("x-captcha-value", captchaValue != null ? captchaValue.trim() : "")
                        .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                        .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                log.debug("my.soliq.uz [{}] reqBody={}", res.statusCode(), reqBody);
                log.debug("my.soliq.uz response: {}", res.body());

                if (res.statusCode() == 200 && res.body() != null && !res.body().isBlank()) {
                    Map<String, Object> root = mapper.readValue(res.body(), new TypeReference<>() {});
                    log.debug("root keys: {}", root.keySet());

                    // API javob: {"success":true,"data":{...}} yoki to'g'ridan field
                    @SuppressWarnings("unchecked")
                    Map<String, Object> json = (root.get("data") instanceof Map)
                            ? (Map<String, Object>) root.get("data") : root;
                    log.debug("data keys: {} -> {}", json.keySet(), json);

                    String surName = str(json, "surName", "lastName", "familiya", "surname", "family");
                    if (surName != null) {
                        String firstName = str(json, "firstName", "name", "ism");
                        String midName   = str(json, "middleName", "patronymic", "otasiningIsmi");
                        String pinfl     = str(json, "personalNum", "pinfl", "jshshir", "tin");
                        // Manzil: ns10Name (viloyat) + ns11Name (tuman) objectlardan olinadi
                        String address = extractAddress(json);

                        Map<String, Object> result = new HashMap<>();
                        result.put("lastName",       toTitleCase(surName));
                        result.put("firstName",      toTitleCase(firstName != null ? firstName : ""));
                        result.put("middleName",     toTitleCase(midName   != null ? midName   : ""));
                        result.put("fullName",       toTitleCase(surName) + " "
                                + toTitleCase(firstName != null ? firstName : "") + " "
                                + toTitleCase(midName   != null ? midName   : ""));
                        result.put("birthDate",      birthDate);
                        result.put("address",        address != null ? address : "—");
                        result.put("passportSeries", series);
                        result.put("passportNumber", number);
                        result.put("pinfl",          pinfl != null ? pinfl : "");
                        return result;
                    }
                }

                // Ma'lumot topilmadi yoki captcha noto'g'ri — null qaytaramiz
                log.debug("my.soliq.uz: ma'lumot topilmadi -> {}", res.body());
                return null;

            } catch (Exception e) {
                log.error("my.soliq.uz so'rov xatolik: {}", e.getMessage());
                return null;
            }
        }

        // Sessiya yo'q (captcha olinmagan) → test ma'lumotlardan qidirish
        return TEST_PASSPORTS.getOrDefault(series + number, null);
    }

    /** Foydalanuvchi kiritgan pasport ma'lumotlarini qaytaradi — admin tasdiqlaydi */
    private Map<String, Object> buildManualResult(String series, String number, String birthDate) {
        Map<String, Object> result = new HashMap<>();
        result.put("lastName",       "");
        result.put("firstName",      "");
        result.put("middleName",     "");
        result.put("fullName",       "");
        result.put("birthDate",      birthDate);
        result.put("address",        "");
        result.put("passportSeries", series);
        result.put("passportNumber", number);
        result.put("pinfl",          "");
        result.put("manualVerification", true);
        return result;
    }

    // ── KAPITAL SUG'URTA: TEXNIK PASPORT + DAVLAT RAQAMI ─────────────────
    private static final String KAPITAL_FORM = "https://kapitalsugurta.uz/policy/osgo/form";
    private static final String KAPITAL_API  = "https://kapitalsugurta.uz/policy/osgo/get-tech-pass-data";

    /**
     * Kapital Sug'urta ochiq API orqali avtomobil ma'lumotlarini oladi.
     * plateNumber: davlat raqami (10N803AB)
     * techSeries:  tex pasport seriyasi (AAG)
     * techNumber:  tex pasport raqami (0593740)
     *
     * Qaytaradi: carModel, carYear, vehicleType, carNumber, techPassportNumber,
     *            ownerFullName, ownerBirthDate, ownerPinfl, + qo'shimcha maydonlar
     * Agar API ishlamasa → null
     */
    public Map<String, Object> lookupVehicleKapital(String plateNumber, String techSeries, String techNumber) {
        try {
            plateNumber = plateNumber.trim().toUpperCase().replaceAll("\\s+", "");
            techSeries  = techSeries.trim().toUpperCase();
            techNumber  = techNumber.trim();

            // 1-qadam: form sahifasini ochib session cookie + CSRF token olamiz
            CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(12))
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .cookieHandler(cookieManager)
                    .build();

            HttpResponse<String> pageRes = client.send(HttpRequest.newBuilder()
                    .uri(URI.create(KAPITAL_FORM))
                    .header("User-Agent", UA)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "uz-UZ,uz;q=0.9,en-US;q=0.8")
                    .GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            // Meta tagdan CSRF tokenni olamiz: <meta name="csrf-token" content="...">
            String csrfToken = extractMetaCsrf(pageRes.body());
            log.debug("[Kapital] page={} csrfToken={}", pageRes.statusCode(), csrfToken);

            if (csrfToken == null || csrfToken.isBlank()) {
                log.error("[Kapital] CSRF token topilmadi");
                return null;
            }

            // 2-qadam: POST so'rov
            String formBody = "csrfParam=" + URLEncoder.encode(csrfToken, StandardCharsets.UTF_8)
                    + "&tech_pass_series=" + URLEncoder.encode(techSeries, StandardCharsets.UTF_8)
                    + "&tech_pass_number=" + URLEncoder.encode(techNumber, StandardCharsets.UTF_8)
                    + "&vehicle_gov_number=" + URLEncoder.encode(plateNumber, StandardCharsets.UTF_8);

            HttpResponse<String> res = client.send(HttpRequest.newBuilder()
                    .uri(URI.create(KAPITAL_API))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("User-Agent", UA)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "uz-UZ,uz;q=0.9,en-US;q=0.8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("X-CSRF-Token", csrfToken)
                    .header("Referer", KAPITAL_FORM)
                    .header("Origin", "https://kapitalsugurta.uz")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            log.debug("[Kapital] api={} body={}", res.statusCode(), res.body());

            if (res.statusCode() != 200 || res.body() == null || res.body().isBlank()) return null;

            Map<String, Object> json = mapper.readValue(res.body(), new TypeReference<>() {});

            // ERROR: 0 — muvaffaqiyat
            Object error = json.get("ERROR");
            if (error != null && !error.toString().equals("0")) {
                log.error("[Kapital] ERROR={} MESSAGE={}", error, json.get("ERROR_MESSAGE"));
                return null;
            }

            // Natijani quramiz
            Map<String, Object> result = new HashMap<>();
            result.put("carModel",            str(json, "MODEL_NAME"));
            result.put("carYear",             json.get("ISSUE_YEAR"));
            result.put("carNumber",           plateNumber);
            result.put("techPassportNumber",  techSeries + techNumber);
            result.put("vehicleType",         str(json, "VEHICLE_TYPE_NAME"));
            result.put("region",              str(json, "REGION_NAME"));
            result.put("seats",               str(json, "SEATS"));
            result.put("bodyNumber",          str(json, "BODY_NUMBER"));
            result.put("engineNumber",        str(json, "ENGINE_NUMBER"));
            result.put("techPassportDate",    str(json, "TECH_PASSPORT_ISSUE_DATE"));
            // Egasining ma'lumotlari
            result.put("ownerFullName",       str(json, "ORGNAME"));
            result.put("ownerFirstName",      str(json, "FIRST_NAME"));
            result.put("ownerLastName",       str(json, "LAST_NAME"));
            result.put("ownerMiddleName",     str(json, "MIDDLE_NAME"));
            result.put("ownerBirthDate",      str(json, "BIRTHDAY"));
            result.put("ownerPinfl",          str(json, "PINFL"));
            return result;

        } catch (Exception e) {
            log.error("[Kapital] xatolik: {}", e.getMessage());
            return null;
        }
    }

    /** HTML meta tagdan CSRF tokenni ajratib oladi */
    private String extractMetaCsrf(String html) {
        if (html == null) return null;
        Matcher m = Pattern.compile("<meta\\s+name=\"csrf-token\"\\s+content=\"([^\"]+)\"").matcher(html);
        if (m.find()) return m.group(1);
        return null;
    }

    // ── TEST MA'LUMOTLARGA FALLBACK ───────────────────────────────────────
    public Map<String, Object> lookupVehicle(String techPassport) {
        if (techPassport == null) return null;
        techPassport = techPassport.trim().toUpperCase().replaceAll("\\s+", "");
        if (!techPassport.matches("[A-Z]{3}\\d{7}")) return null;
        return TEST_VEHICLES.get(techPassport);
    }

    // ── YORDAMCHI ─────────────────────────────────────────────────────────

    /** ns10Name/ns11Name nested objectlardan manzil quradi */
    @SuppressWarnings("unchecked")
    private String extractAddress(Map<String, Object> json) {
        String region   = nestedName(json, "ns10Name");
        String district = nestedName(json, "ns11Name");
        if (region != null && district != null) return region + ", " + district;
        if (region   != null) return region;
        if (district != null) return district;
        return str(json, "address", "manzil");
    }

    @SuppressWarnings("unchecked")
    private String nestedName(Map<String, Object> json, String key) {
        Object val = json.get(key);
        if (val instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) val;
            String s = str(m, "name_uz_latn", "name_uz_cyrl", "name_ru");
            return s;
        }
        return null;
    }

    /** JSON dan bir nechta mumkin bo'lgan kalit nomlardan qiymat topadi */
    private String str(Map<String, Object> json, String... keys) {
        for (String key : keys) {
            Object v = json.get(key);
            if (v != null && !v.toString().isBlank() && !v.toString().equalsIgnoreCase("null"))
                return v.toString();
        }
        return null;
    }

    private String toTitleCase(String s) {
        if (s == null || s.isEmpty()) return "";
        s = s.toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String w : s.split("\\s+"))
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        return sb.toString().trim();
    }

    // ── TEST MA'LUMOTLAR ──────────────────────────────────────────────────
    private static final Map<String, Map<String, Object>> TEST_PASSPORTS = new HashMap<>();
    private static final Map<String, Map<String, Object>> TEST_VEHICLES  = new HashMap<>();

    static {
        addPassport("AA","1234567","Karimov","Alisher","Bahodir o'g'li","15.03.1990","Toshkent shahar, Yunusobod tumani");
        addPassport("AA","7654321","Toshmatov","Bobur","Jamshid o'g'li","22.07.1988","Samarqand viloyati, Samarqand shahri");
        addPassport("AB","1111111","Yusupova","Nilufar","Sardor qizi","08.12.1995","Toshkent shahar, Chilonzor tumani");
        addPassport("AB","2222222","Rahimov","Jasur","Hamid o'g'li","30.06.1992","Farg'ona viloyati, Farg'ona shahri");
        addPassport("AC","3333333","Nazarov","Ulugbek","Anvar o'g'li","14.09.1985","Toshkent shahar, Mirzo Ulug'bek tumani");
        addPassport("AC","4444444","Botirov","Sherzod","Zafar o'g'li","01.01.1993","Buxoro viloyati, Buxoro shahri");
        addPassport("AD","5555555","Mirzayev","Otabek","Rustam o'g'li","18.11.1991","Toshkent shahar, Shayxontohur tumani");
        addPassport("AD","9876543","Xasanov","Nodir","Farhodovich","25.04.1989","Namangan viloyati, Namangan shahri");

        addVehicle("AAA1234567","Chevrolet Spark","Oq",2020,"01A234BC");
        addVehicle("AAA7654321","Chevrolet Cobalt","Kumush",2019,"10B567CD");
        addVehicle("AAB1111111","Chevrolet Nexia 3","Qora",2021,"20C890DE");
        addVehicle("AAB2222222","Chevrolet Malibu","Ko'k",2018,"01K456LM");
        addVehicle("AAC3333333","Chevrolet Lacetti","Kulrang",2017,"30D123EF");
        addVehicle("AAC4444444","Chevrolet Damas","Oq",2016,"01T789UV");
        addVehicle("AAD5555555","Kia Rio","Qizil",2022,"01A111BB");
        addVehicle("AAD9876543","Hyundai Accent","Yashil",2023,"01B222CC");
        addVehicle("BAA1234567","Toyota Camry","Qora",2021,"01D333EE");
        addVehicle("CAA7777777","Chevrolet Spark","Oq",2020,"70A999ZZ");
    }

    private static void addPassport(String series, String number, String lastName,
            String firstName, String middleName, String birthDate, String address) {
        Map<String, Object> d = new HashMap<>();
        d.put("firstName", firstName); d.put("lastName", lastName);
        d.put("middleName", middleName);
        d.put("fullName", lastName + " " + firstName + " " + middleName);
        d.put("birthDate", birthDate); d.put("address", address);
        d.put("passportSeries", series); d.put("passportNumber", number);
        TEST_PASSPORTS.put(series + number, d);
    }

    private static void addVehicle(String tp, String model, String color, int year, String plate) {
        Map<String, Object> d = new HashMap<>();
        d.put("carModel", model); d.put("carColor", color);
        d.put("carYear", year); d.put("carNumber", plate);
        d.put("techPassportNumber", tp);
        TEST_VEHICLES.put(tp, d);
    }
}
