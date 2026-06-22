package com.taxi.backend.service;

import com.taxi.backend.enums.Role;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Feature A — operatorlar boshqaruvi xavfsizlik guard'lari: hashing delegatsiya, no-hash-leak, self-lockout, oxirgi ADMIN. */
@ExtendWith(MockitoExtension.class)
class OperatorAdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthService authService;

    private OperatorAdminService service;

    @BeforeEach
    void setup() {
        service = new OperatorAdminService(userRepository, authService);
    }

    private User staff(Long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setActive(true);
        return u;
    }

    @Test
    @DisplayName("create -> parol AuthService.setPassword orqali hash, javobda hash YO'Q")
    void create_hashesViaAuthService_andNoHashInResponse() {
        when(userRepository.findByPhone("+998901111111")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("op1")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> { User u = inv.getArgument(0); u.setId(50L); return u; });

        Map<String, Object> res = service.create("Op", "+998901111111", "op1", "Parol123", "OPERATOR");

        verify(authService).setPassword(50L, "Parol123"); // BCrypt + validatsiya AuthService da
        assertFalse(res.containsKey("passwordHash"), "javobda parol hash bo'lmasligi kerak");
        assertEquals("OPERATOR", res.get("role"));
        assertEquals(Boolean.TRUE, res.get("active"));
    }

    @Test
    @DisplayName("delete -> o'zini o'chirib bo'lmaydi (self-lockout)")
    void delete_self_blocked() {
        User acting = staff(1L, Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(acting));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.delete(acting, 1L));
        assertTrue(ex.getMessage().contains("O'zingizni"));
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete -> oxirgi ADMINni o'chirib bo'lmaydi")
    void delete_lastAdmin_blocked() {
        User acting = staff(1L, Role.ADMIN);
        User target = staff(2L, Role.ADMIN);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndIsActiveTrue(Role.ADMIN)).thenReturn(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.delete(acting, 2L));
        assertTrue(ex.getMessage().contains("Oxirgi ADMIN"));
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("update -> oxirgi ADMINni OPERATORga tushirib bo'lmaydi (demote guard)")
    void update_demoteLastAdmin_blocked() {
        User acting = staff(1L, Role.ADMIN);
        User target = staff(2L, Role.ADMIN);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndIsActiveTrue(Role.ADMIN)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class, () -> service.update(acting, 2L, "OPERATOR", null));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("update -> boshqa ADMINlar bor bo'lsa demote ruxsat etiladi")
    void update_demoteAdmin_okWhenOthersExist() {
        User acting = staff(1L, Role.ADMIN);
        User target = staff(2L, Role.ADMIN);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndIsActiveTrue(Role.ADMIN)).thenReturn(3L);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> res = service.update(acting, 2L, "OPERATOR", null);
        assertEquals("OPERATOR", res.get("role"));
    }

    @Test
    @DisplayName("list -> hech qachon parol hash qaytarmaydi (faqat hasPassword bayrog'i)")
    void list_neverReturnsHash() {
        User op = staff(5L, Role.OPERATOR);
        op.setPasswordHash("$2a$12$secrethash");
        when(userRepository.findByRoleInOrderByCreatedAtDescIdDesc(anyList())).thenReturn(List.of(op));

        List<Map<String, Object>> res = service.list();

        assertEquals(1, res.size());
        assertFalse(res.get(0).containsKey("passwordHash"));
        assertEquals(Boolean.TRUE, res.get(0).get("hasPassword"));
    }
}
