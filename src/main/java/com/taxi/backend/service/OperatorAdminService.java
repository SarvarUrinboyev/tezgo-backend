package com.taxi.backend.service;

import com.taxi.backend.enums.Role;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operatorlar/adminlar (staff) boshqaruvi — Feature A. FAQAT ADMIN ("superadmin") chaqira oladi
 * (SecurityConfig: /api/admin/** -> hasRole("ADMIN")). Bu sinf qo'shimcha SERVER-side qoidalarni majburlaydi:
 *
 *   - parol BCrypt bilan saqlanadi (AuthService.setPassword orqali, validatsiya bilan); hech qachon qaytarilmaydi;
 *   - SELF-LOCKOUT: amaldagi admin O'ZINI delete/disable/demote qila olmaydi; OXIRGI faol ADMINni ham yo'q;
 *   - faqat OPERATOR/ADMIN roldagi foydalanuvchilar ustida ishlaydi.
 */
@Service
public class OperatorAdminService {

    private static final Logger log = LoggerFactory.getLogger(OperatorAdminService.class);

    private final UserRepository userRepository;
    private final AuthService authService;

    public OperatorAdminService(UserRepository userRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return userRepository.findByRoleInOrderByCreatedAtDescIdDesc(List.of(Role.OPERATOR, Role.ADMIN))
                .stream().map(this::toMap).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long id) {
        return toMap(staffById(id));
    }

    @Transactional
    public Map<String, Object> create(String name, String phone, String username, String password, String roleStr) {
        Role role = parseStaffRole(roleStr);
        if (phone == null || phone.isBlank()) throw new IllegalArgumentException("Telefon kerak");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Login (username) kerak");
        if (userRepository.findByPhone(phone.trim()).isPresent())
            throw new IllegalArgumentException("Bu telefon raqam allaqachon mavjud");
        if (userRepository.findByUsernameIgnoreCase(username.trim()).isPresent())
            throw new IllegalArgumentException("Bu login allaqachon mavjud");

        User u = new User();
        u.setPhone(phone.trim());
        u.setName(name);
        u.setUsername(username.trim());
        u.setRole(role);
        u.setActive(true);
        userRepository.save(u);
        // BCrypt + min-strength validatsiya (zaif parol -> exception, user yaratilgan bo'lsa ham tranzaksiya rollback)
        authService.setPassword(u.getId(), password);
        log.info("[STAFF] yaratildi: id={} username={} role={}", u.getId(), u.getUsername(), role);
        return toMap(u);
    }

    /** Rol va/yoki active holatini o'zgartirish (self-lockout + oxirgi ADMIN guard bilan). */
    @Transactional
    public Map<String, Object> update(User acting, Long id, String roleStr, Boolean active) {
        User u = staffById(id);
        if (roleStr != null && !roleStr.isBlank()) {
            Role newRole = parseStaffRole(roleStr);
            if (u.getRole() == Role.ADMIN && newRole != Role.ADMIN) {
                guardNotSelfOrLastAdmin(acting, u, "rolini pasaytirish");
            }
            u.setRole(newRole);
        }
        if (active != null) {
            if (!active) guardNotSelfOrLastAdmin(acting, u, "o'chirish (disable)");
            u.setActive(active);
        }
        userRepository.save(u);
        log.info("[STAFF] yangilandi: id={} role={} active={}", u.getId(), u.getRole(), u.isActive());
        return toMap(u);
    }

    /** Parolni tiklash (yangi BCrypt hash; eski parol hech qachon ko'rsatilmaydi). */
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        staffById(id); // faqat staff ustida
        authService.setPassword(id, newPassword); // validatsiya + BCrypt
        log.info("[STAFF] parol tiklandi: id={}", id);
    }

    @Transactional
    public void delete(User acting, Long id) {
        User u = staffById(id);
        guardNotSelfOrLastAdmin(acting, u, "o'chirish");
        // Audit FK'larni bo'shatish (sent_by / reviewedBy) — keyin o'chirish
        userRepository.nullifyReviewedByInPhotos(id);
        userRepository.nullifyChannelSentBy(id);
        userRepository.nullifyBroadcastSentBy(id);
        userRepository.delete(u);
        log.info("[STAFF] o'chirildi: id={}", id);
    }

    // ─── guard'lar ──────────────────────────────────────────────────

    private void guardNotSelfOrLastAdmin(User acting, User target, String action) {
        if (acting != null && acting.getId() != null && acting.getId().equals(target.getId())) {
            throw new IllegalArgumentException("O'zingizni " + action + " mumkin emas");
        }
        if (target.getRole() == Role.ADMIN) {
            long activeAdmins = userRepository.countByRoleAndIsActiveTrue(Role.ADMIN);
            if (activeAdmins <= 1) {
                throw new IllegalArgumentException("Oxirgi ADMINni " + action + " mumkin emas");
            }
        }
    }

    private User staffById(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Foydalanuvchi topilmadi"));
        if (u.getRole() != Role.OPERATOR && u.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("Bu foydalanuvchi operator yoki admin emas");
        }
        return u;
    }

    private Role parseStaffRole(String s) {
        if (s == null) throw new IllegalArgumentException("Rol kerak");
        Role r;
        try { r = Role.valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Rol faqat OPERATOR yoki ADMIN bo'lishi mumkin"); }
        if (r != Role.OPERATOR && r != Role.ADMIN)
            throw new IllegalArgumentException("Rol faqat OPERATOR yoki ADMIN bo'lishi mumkin");
        return r;
    }

    /** Javob xaritasi — passwordHash HECH QACHON kiritilmaydi (faqat hasPassword bayrog'i). */
    private Map<String, Object> toMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("phone", u.getPhone());
        m.put("username", u.getUsername());
        m.put("role", u.getRole() != null ? u.getRole().name() : null);
        m.put("active", u.isActive());
        m.put("hasPassword", u.getPasswordHash() != null);
        m.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
        m.put("lastLoginAt", u.getLastLoginAt() != null ? u.getLastLoginAt().toString() : null);
        return m;
    }
}
