package com.taxi.backend.security;

import com.taxi.backend.enums.Role;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.UserRepository;
import com.taxi.backend.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtFilterContainmentTest {

    @Mock private JwtService jwtService;
    @Mock private UserRepository userRepository;
    @Mock private TokenBlacklistService blacklistService;
    @Mock private FilterChain filterChain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void staleJwtRoleCannotUseCurrentDatabaseIdentity() throws Exception {
        User user = user(7L, "owner", Role.PASSENGER);
        when(jwtService.isValid("token")).thenReturn(true);
        when(jwtService.extractType("token")).thenReturn("access");
        when(jwtService.extractJti("token")).thenReturn(null);
        when(jwtService.extractPhone("token")).thenReturn("owner");
        when(jwtService.extractRole("token")).thenReturn("DRIVER");
        when(userRepository.findByPhone("owner")).thenReturn(Optional.of(user));

        MockHttpServletResponse response = run("token");

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("TOKEN_ROLE_STALE"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void currentDatabaseRoleIsTheGrantedAuthority() throws Exception {
        User user = user(7L, "owner", Role.DRIVER);
        when(jwtService.isValid("token")).thenReturn(true);
        when(jwtService.extractType("token")).thenReturn("access");
        when(jwtService.extractJti("token")).thenReturn(null);
        when(jwtService.extractPhone("token")).thenReturn("owner");
        when(jwtService.extractRole("token")).thenReturn("DRIVER");
        when(userRepository.findByPhone("owner")).thenReturn(Optional.of(user));

        run("token");

        assertEquals("ROLE_DRIVER", SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().iterator().next().getAuthority());
        verify(filterChain).doFilter(any(), any());
    }

    private MockHttpServletResponse run(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new JwtFilter(jwtService, userRepository, blacklistService)
                .doFilterInternal(request, response, filterChain);
        return response;
    }

    private User user(Long id, String phone, Role role) {
        User user = new User();
        user.setId(id);
        user.setPhone(phone);
        user.setRole(role);
        return user;
    }
}
