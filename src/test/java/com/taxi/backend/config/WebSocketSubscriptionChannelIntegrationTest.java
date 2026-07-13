package com.taxi.backend.config;

import com.taxi.backend.model.Driver;
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
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * STOMP inbound-channel integration: rejected SUBSCRIBE frames are dropped by
 * the interceptor and never delivered to the simulated broker registration.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketSubscriptionChannelIntegrationTest {

    @Mock private JwtService jwtService;
    @Mock private UserRepository userRepository;
    @Mock private TokenBlacklistService blacklistService;
    @Mock private TripRepository tripRepository;
    @Mock private DriverRepository driverRepository;

    private ExecutorSubscribableChannel inbound;
    private AtomicInteger brokerRegistrationCount;

    @BeforeEach
    void setUp() {
        WebSocketAuthInterceptor interceptor = new WebSocketAuthInterceptor(jwtService, userRepository,
                blacklistService, tripRepository, driverRepository);
        inbound = new ExecutorSubscribableChannel(Runnable::run);
        inbound.addInterceptor(interceptor);
        brokerRegistrationCount = new AtomicInteger();
        inbound.subscribe(message -> brokerRegistrationCount.incrementAndGet());
    }

    @Test
    void unknownTopicNeverReachesBrokerButAuthorizedDriverSubscriptionDoes() {
        User driverUser = new User();
        driverUser.setId(52L);
        Driver driver = new Driver();
        driver.setId(73L);
        driver.setUser(driverUser);
        when(driverRepository.findByUserId(52L)).thenReturn(Optional.of(driver));

        inbound.send(subscription("/topic/driver-location/74", driverUser, "ROLE_DRIVER"));
        assertEquals(0, brokerRegistrationCount.get());

        inbound.send(subscription("/topic/driver/73", driverUser, "ROLE_DRIVER"));
        assertEquals(1, brokerRegistrationCount.get());

        inbound.send(subscription("/topic/private/dispatch", driverUser, "ROLE_DRIVER"));

        assertEquals(1, brokerRegistrationCount.get());
    }

    private Message<byte[]> subscription(String destination, User user, String role) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority(role))));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
