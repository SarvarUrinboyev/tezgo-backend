package com.taxi.backend.service;

import com.taxi.backend.model.User;
import com.taxi.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Referal (do'st chaqirish) + bonus hamyon.
 *
 * Oqim:
 *  1) Har yo'lovchiga noyob referal kod beriladi (birinchi so'rovda generatsiya qilinadi).
 *  2) Yangi foydalanuvchi do'stining kodini kiritadi -> referred_by_code o'rnatiladi.
 *  3) U birinchi safarni YAKUNLAGANDA ikkalasiga ham bonus (balansга) tushadi (faqat bir marta).
 *
 * Bonus tiyinda saqlanadi. Hozircha balans namoyish uchun; online to'lov (Click/Payme)
 * ulanganda sarflanadi.
 */
@Service
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);

    // Har bonus miqdori (tiyin). 500000 tiyin = 5000 so'm. application.properties dan o'zgartirsa bo'ladi.
    @Value("${referral.bonus.tiyin:500000}")
    private long bonusTiyin;

    private final UserRepository userRepository;

    public ReferralService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Foydalanuvchining referal kodi (yo'q bo'lsa yaratiladi) + statistika. */
    @Transactional
    public Map<String, Object> getMyReferral(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Foydalanuvchi topilmadi"));

        String code = ensureCode(user);

        Map<String, Object> out = new HashMap<>();
        out.put("code", code);
        out.put("balance", user.getBalance());                 // tiyin
        out.put("invited", userRepository.countByReferredByCode(code));
        out.put("rewarded", userRepository.countByReferredByCodeAndReferralRewardedTrue(code));
        out.put("bonusTiyin", bonusTiyin);
        out.put("appliedCode", user.getReferredByCode());       // o'zi kiritgan do'st kodi (bo'lsa)
        return out;
    }

    /** Do'stning referal kodini qo'llash. Faqat bir marta, va birinchi safardan oldin. */
    @Transactional
    public Map<String, Object> applyCode(Long userId, String rawCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Foydalanuvchi topilmadi"));

        if (rawCode == null || rawCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Kod kiritilmadi");
        }
        String code = rawCode.trim().toUpperCase();

        // Allaqachon kod kiritgan / bonus olgan bo'lsa
        if (user.getReferredByCode() != null) {
            throw new IllegalStateException("Siz allaqachon do'st kodini kiritgansiz");
        }
        if (user.isReferralRewarded()) {
            throw new IllegalStateException("Bonus allaqachon berilgan");
        }

        // O'zining kodini kirita olmaydi
        String myCode = ensureCode(user);
        if (code.equals(myCode)) {
            throw new IllegalArgumentException("O'zingizning kodingizni kirita olmaysiz");
        }

        Optional<User> referrer = userRepository.findByReferralCode(code);
        if (referrer.isEmpty()) {
            throw new IllegalArgumentException("Bunday kod topilmadi");
        }

        user.setReferredByCode(code);
        userRepository.save(user);
        log.info("Referral: user {} applied code {} (referrer {})", userId, code, referrer.get().getId());

        Map<String, Object> out = new HashMap<>();
        out.put("ok", true);
        out.put("appliedCode", code);
        out.put("bonusTiyin", bonusTiyin);
        return out;
    }

    /**
     * Safar yakunlanganda chaqiriladi. Agar yo'lovchi do'st kodini kiritgan va hali bonus
     * olmagan bo'lsa — ikkalasiga ham bonus beriladi (bir martagina).
     * TripService ning @Transactional ichidan chaqiriladi; xatolik safarni buzmasligi uchun
     * chaqiruvchi tomonda try/catch bilan o'raladi.
     */
    @Transactional
    public void rewardOnFirstTrip(User passenger) {
        if (passenger == null) return;
        if (passenger.isReferralRewarded()) return;
        String code = passenger.getReferredByCode();
        if (code == null || code.isEmpty()) return;

        Optional<User> referrerOpt = userRepository.findByReferralCode(code);
        if (referrerOpt.isEmpty()) {
            // Kod egasi o'chirilgan bo'lishi mumkin — faqat flag qo'yamiz, qayta urinmaymiz
            passenger.setReferralRewarded(true);
            userRepository.save(passenger);
            return;
        }
        User referrer = referrerOpt.get();
        if (referrer.getId().equals(passenger.getId())) {
            passenger.setReferralRewarded(true);
            userRepository.save(passenger);
            return;
        }

        // Ikkalasiga ham bonus
        passenger.setBalance(passenger.getBalance() + bonusTiyin);
        passenger.setReferralRewarded(true);
        referrer.setBalance(referrer.getBalance() + bonusTiyin);

        userRepository.save(passenger);
        userRepository.save(referrer);
        log.info("Referral reward: +{} tiyin to passenger {} and referrer {}",
                bonusTiyin, passenger.getId(), referrer.getId());
    }

    /** Kod yo'q bo'lsa yaratadi (id dan deterministik, noyob). */
    private String ensureCode(User user) {
        if (user.getReferralCode() != null && !user.getReferralCode().isEmpty()) {
            return user.getReferralCode();
        }
        // "TZ" + id (base36, katta harf) — id noyob bo'lgani uchun kod ham noyob
        String code = "TZ" + Long.toString(user.getId(), 36).toUpperCase();
        user.setReferralCode(code);
        userRepository.save(user);
        return code;
    }
}
