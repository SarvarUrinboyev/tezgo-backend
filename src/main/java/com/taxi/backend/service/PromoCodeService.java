package com.taxi.backend.service;

import com.taxi.backend.dto.response.PromoCodeResponse;
import com.taxi.backend.model.PromoCode;
import com.taxi.backend.repository.PromoCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;

    public PromoCodeService(PromoCodeRepository promoCodeRepository) {
        this.promoCodeRepository = promoCodeRepository;
    }

    /**
     * Promo kodni tekshirish va chegirma miqdorini qaytarish.
     * Foydalanilgan soni increment qilinmaydi (faqat bookTrip'da qilinadi).
     *
     * @param code      — promo kod
     * @param tripPrice — asl narx (tiyinlarda)
     * @return { valid, discount, finalPrice, percent, message }
     */
    public Map<String, Object> validate(String code, long tripPrice) {
        if (code == null || code.isBlank()) {
            return Map.of("valid", false, "message", "Promo kod kiritilmagan");
        }

        PromoCode promo = promoCodeRepository.findByCodeIgnoreCase(code.trim())
                .orElse(null);

        if (promo == null) {
            return Map.of("valid", false, "message", "Promo kod topilmadi");
        }

        if (!promo.isValid(tripPrice)) {
            String msg;
            if (!promo.isActive()) msg = "Promo kod faol emas";
            else if (promo.getUsedCount() >= promo.getMaxUses()) msg = "Promo kodning limiti tugagan";
            else if (promo.getMinPrice() != null && tripPrice < promo.getMinPrice())
                msg = "Promo kod uchun minimal narx: " + promo.getMinPrice() / 100 + " so'm";
            else msg = "Promo kod muddati o'tgan";
            return Map.of("valid", false, "message", msg);
        }

        long discount = promo.calculateDiscount(tripPrice);
        long finalPrice = tripPrice - discount;

        return Map.of(
                "valid", true,
                "code", promo.getCode(),
                "percent", promo.getDiscountPercent(),
                "discount", discount,
                "finalPrice", finalPrice,
                "message", promo.getDiscountPercent() + "% chegirma qo'llanildi!"
        );
    }

    /**
     * Promo kodni qo'llash va foydalanish sonini oshirish.
     * @return chegirma miqdori (tiyinda), 0 agar noto'g'ri
     */
    @Transactional
    public long applyPromo(String code, long tripPrice) {
        if (code == null || code.isBlank()) return 0;
        PromoCode promo = promoCodeRepository.findByCodeIgnoreCase(code.trim()).orElse(null);
        if (promo == null || !promo.isValid(tripPrice)) return 0;

        long discount = promo.calculateDiscount(tripPrice);

        // Atomik increment — concurrent buyurtmalarda race condition himoyasi
        int updated = promoCodeRepository.incrementUsedCount(promo.getId());
        if (updated == 0) return 0; // Limit tugagan yoki promo faol emas

        return discount;
    }

    // ─── Admin methods ───────────────────────────────────────────

    public List<PromoCodeResponse> getAll() {
        return promoCodeRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public PromoCodeResponse create(Map<String, Object> body) {
        String code = body.get("code").toString().toUpperCase().trim();
        if (promoCodeRepository.existsByCodeIgnoreCase(code)) {
            throw new RuntimeException("Bu kod allaqachon mavjud: " + code);
        }

        PromoCode promo = new PromoCode();
        promo.setCode(code);
        promo.setDiscountPercent(((Number) body.get("discountPercent")).intValue());
        promo.setMaxUses(body.get("maxUses") != null ? ((Number) body.get("maxUses")).intValue() : 100);
        if (body.get("minPrice") != null)
            promo.setMinPrice(((Number) body.get("minPrice")).longValue() * 100); // UZS → tiyin
        if (body.get("description") != null) promo.setDescription(body.get("description").toString());
        promo.setActive(true);

        return toResponse(promoCodeRepository.save(promo));
    }

    @Transactional
    public PromoCodeResponse toggle(Long id) {
        PromoCode promo = promoCodeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Promo kod topilmadi"));
        promo.setActive(!promo.isActive());
        return toResponse(promoCodeRepository.save(promo));
    }

    @Transactional
    public void delete(Long id) {
        promoCodeRepository.deleteById(id);
    }

    private PromoCodeResponse toResponse(PromoCode p) {
        return new PromoCodeResponse(p.getId(), p.getCode(), p.getDiscountPercent(),
                p.getMaxUses(), p.getUsedCount(),
                p.getMinPrice() != null ? p.getMinPrice() / 100 : 0,
                p.isActive(), p.getDescription(), p.getCreatedAt().toString());
    }
}
