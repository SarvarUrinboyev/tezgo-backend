package com.taxi.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * SMS Invite Service — safar tugagach mijozga ilova yuklab olish taklifini yuboradi.
 *
 * Faqat source=CALL bo'lgan triplar uchun ishlaydi.
 * Redis orqali bir raqamga kuniga 1 marta SMS cheklovi.
 * Xato bo'lsa trip to'xtatilmaydi — faqat log yoziladi.
 */
@Service
public class SmsInviteService {

    private static final Logger log = LoggerFactory.getLogger(SmsInviteService.class);
    private static final String REDIS_KEY_PREFIX = "sms:invite:";

    private final SmsService smsService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${sms.invite.text.uz:TezYol dan foydalanganingiz uchun rahmat!}")
    private String inviteTextUz;

    public SmsInviteService(SmsService smsService,
                            RedisTemplate<String, Object> redisTemplate) {
        this.smsService = smsService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Mijozga ilova yuklab olish SMS yuborish.
     * Bir raqamga kuniga 1 marta — Redis orqali tekshiriladi.
     *
     * @param phoneNumber mijoz telefon raqami (+998XXXXXXXXX)
     */
    public void sendAppInvite(String phoneNumber) {
        try {
            if (phoneNumber == null || phoneNumber.isBlank()) {
                log.warn("[SMS_INVITE] Telefon raqam bo'sh — SMS yuborilmadi");
                return;
            }

            String redisKey = REDIS_KEY_PREFIX + phoneNumber;

            // Redis da tekshirish — bugun allaqachon yuborilganmi?
            Boolean exists = redisTemplate.hasKey(redisKey);
            if (Boolean.TRUE.equals(exists)) {
                log.info("[SMS_INVITE] SMS today already sent: {}", phoneNumber);
                return;
            }

            // SMS yuborish
            smsService.sendSms(phoneNumber, inviteTextUz);

            // Redis ga yozish — 24 soat muddatli
            redisTemplate.opsForValue().set(redisKey, "1", Duration.ofHours(24));

            log.info("[SMS_INVITE] Ilova taklif SMS yuborildi: {}", phoneNumber);
        } catch (Exception e) {
            // Xato bo'lsa trip to'xtatilmasin — faqat Sentry/log ga yozish
            log.error("[SMS_INVITE] SMS yuborishda xato (trip davom etadi): {}", e.getMessage(), e);
        }
    }
}
