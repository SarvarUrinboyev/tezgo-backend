package com.taxi.backend.config;

import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TripRepository;
import com.taxi.backend.repository.UserRepository;
import com.taxi.backend.security.JwtService;
import com.taxi.backend.service.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Regression matrix for every currently shipped mobile subscription family.
 * A non-null result reaches the broker; null is an explicit pre-broker deny.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock private JwtService jwtService;
    @Mock private UserRepository userRepository;
    @Mock private TokenBlacklistService blacklistService;
    @Mock private TripRepository tripRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private MessageChannel channel;

    private WebSocketAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthInterceptor(jwtService, userRepository, blacklistService,
                tripRepository, driverRepository);
    }

    @Test
    void allowsEveryCurrentMobileSubscriptionOnlyForItsAuthorizedRecipient() {
        User passenger = user(41L);
        User driverUser = user(52L);
        Driver driver = driver(73L, driverUser);
        Trip trip = trip(500L, passenger, driver);
        when(tripRepository.findById(500L)).thenReturn(Optional.of(trip));
        when(tripRepository.findFirstByDriverIdAndStatusIn(73L, TripStatus.ACTIVE_DRIVER_STATUSES))
                .thenReturn(Optional.of(trip));
        when(driverRepository.findByUserId(41L)).thenReturn(Optional.empty());
        when(driverRepository.findByUserId(52L)).thenReturn(Optional.of(driver));

        // passenger-app: /topic/trip, /topic/chat, active assigned driver location
        assertAllowed("/topic/trip/500", passenger, "ROLE_PASSENGER");
        assertAllowed("/topic/chat/500", passenger, "ROLE_PASSENGER");
        assertAllowed("/topic/driver-location/73", passenger, "ROLE_PASSENGER");

        // driver-app: direct order, direct trip order, global broadcast and chat
        assertAllowed("/topic/driver/73", driverUser, "ROLE_DRIVER");
        assertAllowed("/topic/driver/73/trip", driverUser, "ROLE_DRIVER");
        assertAllowed("/topic/broadcast", driverUser, "ROLE_DRIVER");
        assertAllowed("/topic/broadcast/73", driverUser, "ROLE_DRIVER");
        assertAllowed("/topic/chat/500", driverUser, "ROLE_DRIVER");
        assertAllowed("/topic/trip/500", driverUser, "ROLE_DRIVER");
        assertAllowed("/topic/driver-location/73", driverUser, "ROLE_DRIVER");
    }

    @Test
    void allowsAdministratorOnlyForKnownAdminOrResourceDestinations() {
        User admin = user(1L);

        assertAllowed("/topic/admin/driver-status", admin, "ROLE_ADMIN");
        assertAllowed("/topic/driver/73", admin, "ROLE_ADMIN");
        assertAllowed("/topic/broadcast/73", admin, "ROLE_ADMIN");
        assertAllowed("/topic/trip/500", admin, "ROLE_ADMIN");
        assertAllowed("/topic/chat/500", admin, "ROLE_ADMIN");
        assertAllowed("/topic/driver-location/73", admin, "ROLE_ADMIN");
        assertDenied("/topic/unknown/anything", admin, "ROLE_ADMIN");
        assertDenied("/topic/broadcast", admin, "ROLE_ADMIN");
    }

    @Test
    void deniesForeignAndMalformedOrUnsupportedSubscriptionsBeforeTheBroker() {
        User passenger = user(41L);
        User unrelatedPassenger = user(42L);
        User driverUser = user(52L);
        Driver driver = driver(73L, driverUser);
        Trip trip = trip(500L, passenger, driver);
        when(tripRepository.findById(500L)).thenReturn(Optional.of(trip));
        when(driverRepository.findByUserId(41L)).thenReturn(Optional.empty());
        when(driverRepository.findByUserId(42L)).thenReturn(Optional.empty());
        when(driverRepository.findByUserId(52L)).thenReturn(Optional.of(driver));

        assertDenied("/topic/chat/500", unrelatedPassenger, "ROLE_PASSENGER");
        assertDenied("/topic/trip/500", unrelatedPassenger, "ROLE_PASSENGER");
        assertDenied("/topic/driver/73", passenger, "ROLE_PASSENGER");
        assertDenied("/topic/driver/74", driverUser, "ROLE_DRIVER");
        assertDenied("/topic/driver-location/73", unrelatedPassenger, "ROLE_PASSENGER");
        assertDenied("/topic/admin/driver-status", passenger, "ROLE_PASSENGER");
        assertDenied("/topic/chat/0", passenger, "ROLE_PASSENGER");
        assertDenied("/topic/chat/500/extra", passenger, "ROLE_PASSENGER");
        assertDenied("/topic/driver/999999999999999999999999999999", driverUser, "ROLE_DRIVER");
        assertDenied("/queue/private-operator-feed", passenger, "ROLE_PASSENGER");
    }

    @Test
    void unauthenticatedSubscriptionIsDeniedBeforeAnyRepositoryLookup() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/trip/500");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertNull(interceptor.preSend(message, channel));
    }

    @Test
    void connectRejectsRefreshTokensAndStaleRoles() {
        when(jwtService.isValid("refresh-token")).thenReturn(true);
        when(jwtService.extractType("refresh-token")).thenReturn("refresh");
        assertThrows(SecurityException.class, () -> interceptor.preSend(connect("refresh-token"), channel));

        User user = user(41L);
        when(jwtService.extractType("access-token")).thenReturn("access");
        when(jwtService.extractJti("access-token")).thenReturn(null);
        when(jwtService.extractPhone("access-token")).thenReturn("user");
        when(jwtService.extractRole("access-token")).thenReturn("DRIVER");
        user.setRole(com.taxi.backend.enums.Role.PASSENGER);
        user.setPhone("user");
        when(jwtService.isValid("access-token")).thenReturn(true);
        when(userRepository.findByPhone("user")).thenReturn(Optional.of(user));
        assertThrows(SecurityException.class, () -> interceptor.preSend(connect("access-token"), channel));
    }

    @Test
    void connectUsesDatabaseRoleForAuthenticatedSession() {
        User user = user(52L);
        user.setPhone("driver");
        user.setRole(com.taxi.backend.enums.Role.DRIVER);
        when(jwtService.isValid("access-token")).thenReturn(true);
        when(jwtService.extractType("access-token")).thenReturn("access");
        when(jwtService.extractJti("access-token")).thenReturn(null);
        when(jwtService.extractPhone("access-token")).thenReturn("driver");
        when(jwtService.extractRole("access-token")).thenReturn("DRIVER");
        when(userRepository.findByPhone("driver")).thenReturn(Optional.of(user));

        Message<?> result = interceptor.preSend(connect("access-token"), channel);

        assertNotNull(result);
        StompHeaderAccessor accessor = org.springframework.messaging.support.MessageHeaderAccessor
                .getAccessor(result, StompHeaderAccessor.class);
        assertNotNull(accessor.getUser());
        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) accessor.getUser();
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_DRIVER".equals(a.getAuthority())));
    }

    private void assertAllowed(String destination, User user, String role) {
        assertNotNull(interceptor.preSend(subscription(destination, user, role), channel), destination);
    }

    private void assertDenied(String destination, User user, String role) {
        assertNull(interceptor.preSend(subscription(destination, user, role), channel), destination);
    }

    private Message<byte[]> subscription(String destination, User user, String role) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority(role))));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> connect(String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + token);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Driver driver(Long id, User user) {
        Driver driver = new Driver();
        driver.setId(id);
        driver.setUser(user);
        return driver;
    }

    private Trip trip(Long id, User passenger, Driver driver) {
        Trip trip = new Trip();
        trip.setId(id);
        trip.setPassenger(passenger);
        trip.setDriver(driver);
        trip.setStatus(TripStatus.ACCEPTED);
        return trip;
    }
}
