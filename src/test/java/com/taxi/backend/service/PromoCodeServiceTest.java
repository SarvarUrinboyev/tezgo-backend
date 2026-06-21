package com.taxi.backend.service;

import com.taxi.backend.model.PromoCode;
import com.taxi.backend.repository.PromoCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromoCodeServiceTest {

    @Mock private PromoCodeRepository promoCodeRepository;
    @InjectMocks private PromoCodeService promoCodeService;

    private PromoCode createPromo(String code, int percent, int maxUses, int usedCount) {
        PromoCode p = new PromoCode();
        p.setId(1L);
        p.setCode(code);
        p.setDiscountPercent(percent);
        p.setMaxUses(maxUses);
        p.setUsedCount(usedCount);
        p.setActive(true);
        return p;
    }

    @Test
    void validate_withValidCode_shouldReturnDiscount() {
        PromoCode promo = createPromo("TEST20", 20, 100, 0);
        when(promoCodeRepository.findByCodeIgnoreCase("TEST20")).thenReturn(Optional.of(promo));

        Map<String, Object> result = promoCodeService.validate("TEST20", 1000000L);
        assertTrue((boolean) result.get("valid"));
        assertEquals(200000L, result.get("discount"));
        assertEquals(800000L, result.get("finalPrice"));
    }

    @Test
    void validate_withInvalidCode_shouldReturnInvalid() {
        when(promoCodeRepository.findByCodeIgnoreCase("NOEXIST")).thenReturn(Optional.empty());

        Map<String, Object> result = promoCodeService.validate("NOEXIST", 1000000L);
        assertFalse((boolean) result.get("valid"));
    }

    @Test
    void validate_withBlankCode_shouldReturnInvalid() {
        Map<String, Object> result = promoCodeService.validate("", 1000000L);
        assertFalse((boolean) result.get("valid"));
    }

    @Test
    void applyPromo_shouldUseAtomicIncrement() {
        PromoCode promo = createPromo("TEST20", 20, 100, 0);
        when(promoCodeRepository.findByCodeIgnoreCase("TEST20")).thenReturn(Optional.of(promo));
        when(promoCodeRepository.incrementUsedCount(1L)).thenReturn(1);

        long discount = promoCodeService.applyPromo("TEST20", 1000000L);
        assertEquals(200000L, discount);
        verify(promoCodeRepository).incrementUsedCount(1L);
        // Should NOT call save() with manual increment
        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void applyPromo_whenLimitReached_shouldReturnZero() {
        PromoCode promo = createPromo("TEST20", 20, 100, 0);
        when(promoCodeRepository.findByCodeIgnoreCase("TEST20")).thenReturn(Optional.of(promo));
        when(promoCodeRepository.incrementUsedCount(1L)).thenReturn(0); // limit reached

        long discount = promoCodeService.applyPromo("TEST20", 1000000L);
        assertEquals(0L, discount);
    }
}
