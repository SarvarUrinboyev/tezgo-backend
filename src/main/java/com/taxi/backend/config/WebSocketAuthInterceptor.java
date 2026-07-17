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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket STOMP xabarlarini autentifikatsiya va avtorizatsiya qilish.
 *
 * CONNECT — JWT token tekshiriladi
 * SUBSCRIBE — foydalanuvchi faqat o'ziga tegishli topic'larga subscribe qila oladi
 * SEND — xabar yuborishda trip ownership tekshiriladi
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private static final Pattern CHAT_TOPIC = Pattern.compile("^/topic/chat/(\\d+)$");
    private static final Pattern DRIVER_TOPIC = Pattern.compile("^/topic/driver/(\\d+)(?:/trip)?$");
    private static final Pattern TRIP_TOPIC = Pattern.compile("^/topic/trip/(\\d+)$");
    private static final Pattern DRIVER_LOCATION_TOPIC = Pattern.compile("^/topic/driver-location/(\\d+)$");
    private static final Pattern TARGETED_BROADCAST_TOPIC = Pattern.compile("^/topic/broadcast/(\\d+)$");
    private static final String GLOBAL_BROADCAST_TOPIC = "/topic/broadcast";
    private static final String ADMIN_DRIVER_STATUS_TOPIC = "/topic/admin/driver-status";

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
            case SUBSCRIBE -> {
                // Returning null rejects only this SUBSCRIBE frame. Existing, authorized
                // subscriptions remain alive instead of turning one bad destination into
                // a session-wide disconnect.
                if (!handleSubscribe(accessor)) return null;
            }
            case SEND -> handleSend(accessor);
            default -> { /* DISCONNECT va boshqalar — tekshiruvsiz */ }
        }

        return message;
    }

    /** CONNECT — JWT token bilan autentifikatsiya */
    private void handleConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[WS_DENY] command=CONNECT reason=MISSING_BEARER_TOKEN");
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

        if (!"access".equals(jwtService.extractType(token))) {
            log.warn("[WS_DENY] command=CONNECT reason=ACCESS_TOKEN_REQUIRED");
            throw new SecurityException("WebSocket uchun access token kerak");
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
        if (user.getRole() == null || role == null || !user.getRole().name().equalsIgnoreCase(role)) {
            log.warn("[WS_DENY] command=CONNECT reason=STALE_ROLE");
            throw new SecurityException("Token roli eskirgan");
        }
        var auth = new UsernamePasswordAuthenticationToken(
                user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        accessor.setUser(auth);
    }

    /**
     * SUBSCRIBE — explicit allowlist. Every destination not represented below is denied.
     * The simple broker has no resource authorization of its own, so this boundary must
     * make the complete decision before a subscription reaches it.
     */
    private boolean handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        User user = extractUser(accessor);
        if (destination == null) return denySubscribe(accessor, null, "MISSING_DESTINATION");
        if (user == null) return denySubscribe(accessor, destination, "UNAUTHENTICATED");

        Matcher matcher = CHAT_TOPIC.matcher(destination);
        if (matcher.matches()) {
            Long tripId = parsePositiveId(accessor, destination, matcher.group(1));
            return tripId != null && allowTripParticipant(accessor, user, tripId, destination);
        }

        matcher = DRIVER_TOPIC.matcher(destination);
        if (matcher.matches()) {
            Long driverId = parsePositiveId(accessor, destination, matcher.group(1));
            return driverId != null && allowOwnedDriverTopic(accessor, user, driverId, destination);
        }

        matcher = TRIP_TOPIC.matcher(destination);
        if (matcher.matches()) {
            Long tripId = parsePositiveId(accessor, destination, matcher.group(1));
            return tripId != null && allowTripParticipant(accessor, user, tripId, destination);
        }

        matcher = DRIVER_LOCATION_TOPIC.matcher(destination);
        if (matcher.matches()) {
            Long driverId = parsePositiveId(accessor, destination, matcher.group(1));
            return driverId != null && allowDriverLocation(accessor, user, driverId, destination);
        }

        if (ADMIN_DRIVER_STATUS_TOPIC.equals(destination)) {
            return isAdmin(accessor) || denySubscribe(accessor, destination, "ADMIN_REQUIRED");
        }

        if (GLOBAL_BROADCAST_TOPIC.equals(destination)) {
            return allowAnyDriver(accessor, user, destination);
        }

        matcher = TARGETED_BROADCAST_TOPIC.matcher(destination);
        if (matcher.matches()) {
            Long driverId = parsePositiveId(accessor, destination, matcher.group(1));
            return driverId != null && allowOwnedDriverTopic(accessor, user, driverId, destination);
        }

        String reason = destination.startsWith("/topic/") || destination.startsWith("/queue/")
                ? "UNKNOWN_OR_MALFORMED_DESTINATION" : "UNSUPPORTED_DESTINATION";
        return denySubscribe(accessor, destination, reason);
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
                if (tripId <= 0 || !canAccessTrip(accessor, extractUser(accessor), tripId)) {
                    throw new SecurityException("Bu trip'ga kirishga ruxsat yo'q");
                }
            } catch (NumberFormatException e) {
                throw new SecurityException("Noto'g'ri trip ID");
            }
        }
    }

    private boolean allowTripParticipant(StompHeaderAccessor accessor, User user, Long tripId, String destination) {
        return canAccessTrip(accessor, user, tripId)
                || denySubscribe(accessor, destination, "TRIP_PARTICIPANT_REQUIRED");
    }

    private boolean allowOwnedDriverTopic(StompHeaderAccessor accessor, User user, Long driverId, String destination) {
        if (isAdmin(accessor)) return true;
        return ownsDriver(user, driverId)
                || denySubscribe(accessor, destination, "DRIVER_OWNERSHIP_REQUIRED");
    }

    private boolean allowAnyDriver(StompHeaderAccessor accessor, User user, String destination) {
        return driverRepository.findByUserId(user.getId()).isPresent()
                || denySubscribe(accessor, destination, "DRIVER_ROLE_REQUIRED");
    }

    private boolean allowDriverLocation(StompHeaderAccessor accessor, User user, Long driverId, String destination) {
        if (isAdmin(accessor) || ownsDriver(user, driverId)) return true;

        boolean isActivePassenger = tripRepository
                .findFirstByDriverIdAndStatusIn(driverId, com.taxi.backend.enums.TripStatus.ACTIVE_DRIVER_STATUSES)
                .map(trip -> trip.getPassenger() != null && trip.getPassenger().getId().equals(user.getId()))
                .orElse(false);
        return isActivePassenger
                || denySubscribe(accessor, destination, "ACTIVE_TRIP_PASSENGER_REQUIRED");
    }

    private boolean canAccessTrip(StompHeaderAccessor accessor, User user, Long tripId) {
        if (user == null) return false;
        if (isAdmin(accessor)) return true;
        return tripRepository.findById(tripId).map(trip -> {
            boolean isPassenger = trip.getPassenger() != null
                    && trip.getPassenger().getId().equals(user.getId());
            boolean isDriver = trip.getDriver() != null
                    && trip.getDriver().getUser() != null
                    && trip.getDriver().getUser().getId().equals(user.getId());
            return isPassenger || isDriver;
        }).orElse(false);
    }

    private boolean ownsDriver(User user, Long driverId) {
        return user != null && user.getId() != null && driverRepository.findByUserId(user.getId())
                .map(driver -> driver.getId().equals(driverId))
                .orElse(false);
    }

    private Long parsePositiveId(StompHeaderAccessor accessor, String destination, String rawId) {
        try {
            long id = Long.parseLong(rawId);
            if (id <= 0) {
                denySubscribe(accessor, destination, "NON_POSITIVE_ID");
                return null;
            }
            return id;
        } catch (NumberFormatException e) {
            denySubscribe(accessor, destination, "MALFORMED_ID");
            return null;
        }
    }

    private boolean denySubscribe(StompHeaderAccessor accessor, String destination, String reason) {
        User user = extractUser(accessor);
        String principalId = user != null && user.getId() != null ? user.getId().toString() : "anonymous";
        String role = accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth
                ? auth.getAuthorities().stream().findFirst()
                        .map(authority -> authority.getAuthority()).orElse("unknown")
                : "unknown";
        log.warn("[WS_DENY] principalId={} role={} destinationFamily={} reason={}",
                principalId, role, destinationFamily(destination), reason);
        return false;
    }

    private String destinationFamily(String destination) {
        if (destination == null) return "missing";
        if (destination.startsWith("/topic/chat/")) return "topic/chat";
        if (destination.startsWith("/topic/driver-location/")) return "topic/driver-location";
        if (destination.startsWith("/topic/driver/")) return "topic/driver";
        if (destination.startsWith("/topic/trip/")) return "topic/trip";
        if (destination.startsWith("/topic/admin/")) return "topic/admin";
        if (destination.startsWith("/topic/broadcast")) return "topic/broadcast";
        if (destination.startsWith("/queue/")) return "queue";
        return "other";
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
