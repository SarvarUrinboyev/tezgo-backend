package com.taxi.backend.config;

import com.taxi.backend.model.PromoCode;
import com.taxi.backend.model.Tariff;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.PromoCodeRepository;
import com.taxi.backend.repository.TariffRepository;
import com.taxi.backend.repository.UserRepository;
import com.taxi.backend.enums.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    private final TariffRepository tariffRepository;
    private final UserRepository userRepository;
    private final PromoCodeRepository promoCodeRepository;

    @Value("${admin.phone:}")
    private String adminPhone;

    public DataInitializer(TariffRepository tariffRepository, UserRepository userRepository,
                           PromoCodeRepository promoCodeRepository) {
        this.tariffRepository = tariffRepository;
        this.userRepository = userRepository;
        this.promoCodeRepository = promoCodeRepository;
    }

    @Override
    public void run(String... args) {
        // Tariflar (agar bo'lmasa)
        if (tariffRepository.count() == 0) {
            Tariff ekonom = new Tariff();
            ekonom.setName("EKONOM");
            ekonom.setBasePrice(750000L);    // 7 500 so'm boshlang'ich
            ekonom.setPricePerKm(250000L);   // 2 500 so'm/km
            ekonom.setPricePerMin(0L);
            ekonom.setMinPrice(0L);
            ekonom.setActive(true);
            tariffRepository.save(ekonom);

            Tariff damas = new Tariff();
            damas.setName("DAMAS");
            damas.setBasePrice(1000000L);    // 10 000 so'm boshlang'ich
            damas.setPricePerKm(250000L);    // 2 500 so'm/km
            damas.setPricePerMin(0L);
            damas.setMinPrice(0L);
            damas.setActive(true);
            tariffRepository.save(damas);

            Tariff biznes = new Tariff();
            biznes.setName("BIZNES");
            biznes.setBasePrice(1000000L);   // 10 000 so'm boshlang'ich
            biznes.setPricePerKm(300000L);   // 3 000 so'm/km
            biznes.setPricePerMin(0L);
            biznes.setMinPrice(0L);
            biznes.setActive(true);
            tariffRepository.save(biznes);

            Tariff komfort = new Tariff();
            komfort.setName("KOMFORT");
            komfort.setBasePrice(0L);
            komfort.setPricePerKm(250000L);
            komfort.setPricePerMin(0L);
            komfort.setMinPrice(0L);
            komfort.setActive(false);
            tariffRepository.save(komfort);

            log.info("4 ta tarif yaratildi: EKONOM, DAMAS, BIZNES, KOMFORT (nofaol)");
        }

        // Demo promo kodlar
        if (promoCodeRepository.count() == 0) {
            PromoCode p1 = new PromoCode();
            p1.setCode("TEZYOL20");
            p1.setDiscountPercent(20);
            p1.setMaxUses(500);
            p1.setDescription("TezYol ilovasi uchun 20% chegirma");
            promoCodeRepository.save(p1);

            PromoCode p2 = new PromoCode();
            p2.setCode("YANGI10");
            p2.setDiscountPercent(10);
            p2.setMaxUses(1000);
            p2.setDescription("Yangi foydalanuvchilar uchun 10% chegirma");
            promoCodeRepository.save(p2);

            log.info("2 ta promo kod yaratildi: TEZYOL20, YANGI10");
        }

        // Admin foydalanuvchi — faqat admin.phone sozlangan bo'lsa yaratiladi
        if (adminPhone == null || adminPhone.isBlank()) {
            log.warn("admin.phone sozlanmagan — admin foydalanuvchi yaratilmaydi. " +
                    "application.properties yoki env da ADMIN_PHONE ni belgilang.");
            return;
        }
        User adminUser = userRepository.findByPhone(adminPhone).orElse(null);
        if (adminUser == null) {
            adminUser = new User();
            adminUser.setPhone(adminPhone);
            adminUser.setName("Sirojiddin");
            adminUser.setRole(Role.ADMIN);
            adminUser.setUsername("Sirojiddin");
            adminUser.setPasswordHash(passwordEncoder.encode("Hanafiy@97"));
            userRepository.save(adminUser);
            log.info("Admin yaratildi: {} (username=Sirojiddin)", adminPhone);
        } else {
            boolean changed = false;
            if (adminUser.getRole() != Role.ADMIN) {
                adminUser.setRole(Role.ADMIN);
                changed = true;
            }
            if (adminUser.getUsername() == null) {
                adminUser.setUsername("Sirojiddin");
                adminUser.setPasswordHash(passwordEncoder.encode("Hanafiy@97"));
                adminUser.setName("Sirojiddin");
                changed = true;
            }
            if (changed) {
                userRepository.save(adminUser);
                log.info("Admin yangilandi: username/password set, phone={}", adminPhone);
            }
        }

        // Operator foydalanuvchi — Nargiza
        String operatorPhone = "+998901234567";
        User operator = userRepository.findByPhone(operatorPhone).orElse(null);
        if (operator == null) {
            operator = new User();
            operator.setPhone(operatorPhone);
            operator.setName("Nargiza");
            operator.setRole(Role.OPERATOR);
            operator.setActive(true);
            operator.setUsername("Nargiza");
            operator.setPasswordHash(passwordEncoder.encode("Nargiza@2024"));
            userRepository.save(operator);
            log.info("Operator yaratildi: {} (username=Nargiza)", operatorPhone);
        } else {
            boolean changed = false;
            if (operator.getRole() != Role.OPERATOR) {
                operator.setRole(Role.OPERATOR);
                changed = true;
            }
            if (operator.getUsername() == null) {
                operator.setUsername("Nargiza");
                operator.setPasswordHash(passwordEncoder.encode("Nargiza@2024"));
                operator.setName("Nargiza");
                changed = true;
            }
            if (!operator.isActive()) {
                operator.setActive(true);
                changed = true;
            }
            if (changed) {
                userRepository.save(operator);
                log.info("Operator yangilandi: role/username/active sozlandi, phone={}", operatorPhone);
            }
        }
    }
}
