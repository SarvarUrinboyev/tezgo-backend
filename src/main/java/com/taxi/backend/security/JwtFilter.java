package com.taxi.backend.security;

import com.taxi.backend.model.User;
import com.taxi.backend.repository.UserRepository;
import com.taxi.backend.service.TokenBlacklistService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TokenBlacklistService blacklistService;

    public JwtFilter(JwtService jwtService, UserRepository userRepository,
                     TokenBlacklistService blacklistService) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.blacklistService = blacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        // Token validatsiya — expired va invalid farqlanadi
        try {
            if (!jwtService.isValid(token)) {
                sendError(response, 401, "Token noto'g'ri", "TOKEN_INVALID");
                return;
            }
        } catch (ExpiredJwtException ex) {
            sendError(response, 401, "Token muddati tugagan", "TOKEN_EXPIRED");
            return;
        } catch (JwtException ex) {
            sendError(response, 401, "Token noto'g'ri", "TOKEN_INVALID");
            return;
        }

        // XAVFSIZLIK: Faqat access tokenlar API uchun qabul qilinadi
        // Refresh token bilan API endpoint'larga kirib bo'lmaydi
        String tokenType = jwtService.extractType(token);
        if (!"access".equals(tokenType)) {
            sendError(response, 401, "Faqat access token ishlatilishi mumkin", "TOKEN_WRONG_TYPE");
            return;
        }

        // Blacklist tekshirish (logout qilingan token)
        String jti = jwtService.extractJti(token);
        if (jti != null && blacklistService.isBlacklisted(jti)) {
            sendError(response, 401, "Token bekor qilingan (logout)", "TOKEN_REVOKED");
            return;
        }

        String phone = jwtService.extractPhone(token);
        String role = jwtService.extractRole(token);

        Optional<User> userOpt = userRepository.findByPhone(phone);
        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            sendError(response, 401, "Foydalanuvchi topilmadi yoki bloklangan", "USER_INACTIVE");
            return;
        }

        User user = userOpt.get();
        if (user.getRole() == null || role == null || !user.getRole().name().equalsIgnoreCase(role)) {
            sendError(response, 401, "Token roli eskirgan", "TOKEN_ROLE_STALE");
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, int status, String message, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        // JSON serializer orqali xavfsiz yozish (message ichidagi maxsus belgilar escaped bo'ladi)
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        java.util.Map<String, Object> body = java.util.Map.of(
                "error", message, "code", code, "status", status);
        response.getWriter().write(mapper.writeValueAsString(body));
    }
}
