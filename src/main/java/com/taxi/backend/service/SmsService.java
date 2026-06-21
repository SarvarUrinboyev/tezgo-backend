package com.taxi.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Eskiz.uz SMS Gateway integratsiyasi
 * Hujjat: https://notify.eskiz.uz/api/docs
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);
    private static final String ESKIZ_LOGIN_URL = "https://notify.eskiz.uz/api/auth/login";
    private static final String ESKIZ_SEND_URL = "https://notify.eskiz.uz/api/message/sms/send";

    @Value("${sms.eskiz.email}")
    private String eskizEmail;

    @Value("${sms.eskiz.password}")
    private String eskizPassword;

    @Value("${sms.enabled:true}")
    private boolean smsEnabled;

    @Value("${sms.sender:4546}")
    private String sender;

    private final RestTemplate restTemplate = new RestTemplate();

    // Token cache — har kuni token yangilanadi (thread-safe)
    private volatile String cachedToken = null;
    private volatile LocalDateTime tokenExpiry = null;
    private final Object tokenLock = new Object();

    /**
     * OTP SMS yuborish
     *
     * @param phone +998XXXXXXXXX formatda
     * @param code  6 xonali OTP kodi
     */
    public boolean sendOtp(String phone, String code) {
        // MATN tasdiqlangan Eskiz shabloni 76482 bilan AYNAN mos bo'lishi shart:
        // "TezYol ilovasiga kirish uchun tasdiqlash kodi: %d Kodni hech kimga bermang!"
        // (kod'dan keyin NUQTA YO'Q — faqat bo'shliq; nuqta shablonga mos kelmay rad etilardi).
        String text = "TezYol ilovasiga kirish uchun tasdiqlash kodi: " + code + " Kodni hech kimga bermang!";
        return sendSms(phone, text);
    }

    /**
     * SMS yuborish. Haqiqatan yuborilsa true, aks holda false qaytaradi —
     * shunda chaqiruvchi (AuthService) muvaffaqiyatsizlikni biladi va
     * foydalanuvchiga soxta "yuborildi" demaydi.
     */
    public boolean sendSms(String phone, String message) {
        if (!smsEnabled) {
            // Development rejimida — faqat console ga chiqarish, real SMS yo'q
            log.warn("[SMS DISABLED] {} → {}", phone, message);
            return false;
        }
        try {
            String token = getEskizToken();
            if (token == null) {
                log.warn("Eskiz.uz token olishda xato");
                return false;
            }
            if (sendEskizSms(token, phone, message)) {
                log.info("SMS yuborildi: {}", phone);
                return true;
            }
            // Token eskirgan bo'lishi mumkin — yangilab bir marta qayta urinish.
            cachedToken = null;
            token = getEskizToken();
            if (token != null && sendEskizSms(token, phone, message)) {
                log.info("SMS (retry) yuborildi: {}", phone);
                return true;
            }
            log.error("SMS yuborilmadi (Eskiz rad etdi yoki xato): {}", phone);
            return false;
        } catch (Exception e) {
            log.error("SMS yuborishda xato: {}", e.getMessage(), e);
            return false;
        }
    }

    /** Eskiz.uz JWT token olish (thread-safe cache bilan) */
    private String getEskizToken() {
        // Cache tekshirish — token 23 soat davomida amal qiladi
        if (cachedToken != null && tokenExpiry != null && LocalDateTime.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }

        synchronized (tokenLock) {
            // Double-check locking
            if (cachedToken != null && tokenExpiry != null && LocalDateTime.now().isBefore(tokenExpiry)) {
                return cachedToken;
            }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("email", eskizEmail);
            body.add("password", eskizPassword);

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(ESKIZ_LOGIN_URL, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                if (data != null) {
                    String token = (String) data.get("token");
                    // Token ni cache ga saqlash
                    cachedToken = token;
                    tokenExpiry = LocalDateTime.now().plusHours(23);
                    log.info("Eskiz.uz token olindi");
                    return token;
                }
            }
            log.warn("Eskiz.uz login muvaffaqiyatsiz: {}", response.getStatusCode());
        } catch (Exception e) {
            log.warn("Eskiz.uz token olishda xato: {}", e.getMessage());
        }
        return null;
        } // synchronized
    }

    /** Eskiz.uz orqali SMS yuborish, true qaytarsa muvaffaqiyatli */
    private boolean sendEskizSms(String token, String phone, String message) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBearerAuth(token);

            // Telefon raqamdan + belgisini olib tashlash
            String cleanPhone = phone.startsWith("+") ? phone.substring(1) : phone;

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("mobile_phone", cleanPhone);
            body.add("message", message);
            body.add("from", sender);
            body.add("callback_url", "");

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(ESKIZ_SEND_URL, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> resBody = response.getBody();
                if (resBody != null) {
                    String status = (String) resBody.get("status");
                    log.info("Eskiz response: {}", resBody);
                    return "waiting".equals(status) || "success".equals(status);
                }
                return true;
            }
            log.warn("Eskiz SMS status: {}", response.getStatusCode());
            return false;
        } catch (Exception e) {
            log.error("SMS yuborishda xato: {}", e.getMessage(), e);
            return false;
        }
    }
}
