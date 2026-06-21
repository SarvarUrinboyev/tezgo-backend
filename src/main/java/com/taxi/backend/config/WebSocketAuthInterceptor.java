package com.taxi.backend.config;

import com.taxi.backend.model.User;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TripRepository;
import com.taxi.backend.repository.UserRepository;
import com.taxi.backend.security.JwtService;
import com.taxi.backend.service.TokenBlacklistService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * WebSocket STOMP xabarlarini autentifikatsiya va avtorizatsiya qilish.
 *
 * CONNECT — JWT token tekshiriladi
 * SUBSCRIBE — foydalanuvchi faqat o'ziga tegishli topic'larga subscribe qila oladi
 * SEND — xabar yuborishda trip ownership tekshiriladi
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = Logger.getLogger(WebSocketAuthInterceptor.class.getName());

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TokenBlacklistService blacklistService;
    private final TripRepository tripRepository;
    private final DriverRepository driverRepository;

    public WebSocketAuthInterceptor(JwtService jwtService,
                                     UserRepository userRepository,
                                     TokenBlacklistService blacklistService,
                                     TripRepository tripRepository,
                                     DriverRepository driverRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.blacklistService = blacklistService;
        this.tripRepository = tripRepository;
        this.driverRepository = driverRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        StompCommand command = accessor.getCommand();
        if (command == null) return message;

        switch (command) {
            case CONNECT -> handleConnect(accessor);
            case SUBSCRIBE -> handleSubscribe(accessor);
            case SEND -> handleSend(accessor);
            default -> { /* DISCONNECT va boshqalar — tekshiruvsiz */ }
        }

        return message;
    }

    /** CONNECT — JWT token bilan autentifikatsiya */
    private void handleConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warning("[WS] CONNECT — token yo'q");
            throw new SecurityException("WebSocket ulanish uchun token kerak");
        }

        String token = authHeader.substring(7);
        try {
            if (!jwtService.isValid(token)) {
                throw new SecurityException("Token noto'g'ri");
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Token muddati tugagan");
        }

        String jti = jwtService.extractJti(token);
        if (jti != null && blacklistService.isBlacklisted(jti)) {
            throw new SecurityException("Token bekor qilingan");
        }

        String phone = jwtService.extractPhone(token);
        String role = jwtService.extractRole(token);
        Optional<User> userOpt = userRepository.findByPhone(phone);

        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            throw new SecurityException("Foydalanuvchi topilmadi");
        }

        User user = userOpt.get();
        var auth = new UsernamePasswordAuthenticationToken(
                user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        accessor.setUser(auth);
    }

    /** SUBSCRIBE — faqat o'z topic'lariga ruxsat */
    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) return;

        // /topic/chat/{tripId} — trip'ga tegishli foydalanuvchilar
        if (destination.startsWith("/topic/chat/")) {
            String tripIdStr = destination.replace("/topic/chat/", "");
            try {
                Long tripId = Long.parseLong(tripIdStr);
                validateTripAccess(accessor, tripId);
            } catch (NumberFormatException e) {
                throw new SecurityException("Noto'g'ri trip ID");
            }
        }

        // /topic/driver/{id}/* — faqat o'z driver topic'iga
        if (destination.startsWith("/topic/driver/")) {
            validateDriverTopicAccess(accessor, destination);
        }
    }

    /** SEND — xabar yuborishda trip ownership tekshirish */
    private void handleSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) return;

        // /app/chat.{tripId} — trip'ga tegishli foydalanuvchilar
        if (destination.startsWith("/app/chat.")) {
            String tripIdStr = destination.replace("/app/chat.", "");
            try {
                Long tripId = Long.parseLong(tripIdStr);
                validateTripAccess(accessor, tripId);
            } catch (NumberFormatException e) {
                throw new SecurityException("Noto'g'ri trip ID");
            }
        }
    }

    /** Trip'ga foydalanuvchining huquqi borligini tekshirish */
    private void validateTripAccess(StompHeaderAccessor accessor, Long tripId) {
        User user = extractUser(accessor);
        if (user == null) {
            throw new SecurityException("Autentifikatsiya kerak");
        }

        // Admin — barcha trip'larga ruxsat
        if (isAdmin(accessor)) return;

        // Trip mavjudligini tekshirish
        tripRepository.findById(tripId).ifPresentOrElse(trip -> {
            boolean isPassenger = trip.getPassenger() != null
                && trip.getPassenger().getId().equals(user.getId());
            boolean isDriver = trip.getDriver() != null
                && trip.getDriver().getUser() != null
                && trip.getDriver().getUser().getId().equals(user.getId());

            if (!isPassenger && !isDriver) {
                throw new SecurityException("Bu trip'ga kirishga ruxsat yo'q");
            }
        }, () -> {
            throw new SecurityException("Trip topilmadi: " + tripId);
        });
    }

    /** Driver topic'iga faqat o'zi subscribe qila oladi */
    private void validateDriverTopicAccess(StompHeaderAccessor accessor, String destination) {
        User user = extractUser(accessor);
        if (user == null) throw new SecurityException("Autentifikatsiya kerak");
        if (isAdmin(accessor)) return;

        // /topic/driver/{id} yoki /topic/driver/{id}/trip
        String[] parts = destination.split("/");
        if (parts.length >= 4) {
            try {
                Long driverId = Long.parseLong(parts[3]);
                driverRepository.findByUserId(user.getId()).ifPresentOrElse(driver -> {
                    if (!driver.getId().equals(driverId)) {
                        throw new SecurityException("Boshqa haydovchining topic'iga kirish mumkin emas");
                    }
                }, () -> {
                    throw new SecurityException("Haydovchi topilmadi");
                });
            } catch (NumberFormatException e) {
                throw new SecurityException("Noto'g'ri driver ID");
            }
        }
    }

    private User extractUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
            Object principal = auth.getPrincipal();
            if (principal instanceof User) return (User) principal;
        }
        return null;
    }

    private boolean isAdmin(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
            return auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }
}
