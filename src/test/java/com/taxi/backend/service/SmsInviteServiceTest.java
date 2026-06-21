package com.taxi.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmsInviteServiceTest {

    @Mock
    private SmsService smsService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    @InjectMocks
    private SmsInviteService smsInviteService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(smsInviteService, "inviteTextUz",
                "TezYol dan foydalanganingiz uchun rahmat!");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("CALL + COMPLETED -> SMS yuboriladi")
    void shouldSendSmsForCallTrip() {
        when(redisTemplate.hasKey("sms:invite:+998901234567")).thenReturn(false);

        smsInviteService.sendAppInvite("+998901234567");

        verify(smsService).sendSms(eq("+998901234567"), anyString());
        verify(valueOps).set(eq("sms:invite:+998901234567"), eq("1"), eq(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("Bir raqamga kuniga 2-marta SMS bormaydi")
    void shouldNotSendDuplicateSms() {
        when(redisTemplate.hasKey("sms:invite:+998901234567")).thenReturn(true);

        smsInviteService.sendAppInvite("+998901234567");

        verify(smsService, never()).sendSms(anyString(), anyString());
    }

    @Test
    @DisplayName("SMS xatosi trip ni to'xtatmaydi")
    void smsErrorShouldNotBreakTrip() {
        when(redisTemplate.hasKey("sms:invite:+998901234567")).thenReturn(false);
        doThrow(new RuntimeException("Eskiz timeout")).when(smsService).sendSms(anyString(), anyString());

        // Bu exception tashlamaydi — try-catch ichida
        smsInviteService.sendAppInvite("+998901234567");
        // Test muvaffaqiyatli tugashi = trip to'xtatilmadi
    }

    @Test
    @DisplayName("Bo'sh telefon raqam — SMS yuborilmaydi")
    void shouldNotSendSmsForEmptyPhone() {
        smsInviteService.sendAppInvite(null);
        smsInviteService.sendAppInvite("");

        verify(smsService, never()).sendSms(anyString(), anyString());
    }
}
