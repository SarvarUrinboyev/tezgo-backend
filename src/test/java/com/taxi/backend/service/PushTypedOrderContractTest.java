package com.taxi.backend.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.taxi.backend.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushTypedOrderContractTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;
    @Mock private DriverRepository drivers;
    @Mock private FirebaseMessaging firebase;

    private PushNotificationService push;

    @BeforeEach
    void setUp() throws Exception {
        push = spy(new PushNotificationService(redis, drivers, true, 5));
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("push:driver:9")).thenReturn("FCM:raw-test-token");
        lenient().doReturn(firebase).when(push).getFirebaseMessaging();
        lenient().when(firebase.send(any(Message.class))).thenReturn("provider-message-id");
    }

    @Test
    void typedOrderPushUsesTheExistingDataOnlyBuilderWithBoundedTransportTtl() throws Exception {
        OrderPushDeliveryOutcome outcome = push.sendOrderPushWithOutcome(9L, orderData());

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(firebase).send(sent.capture());
        Message message = sent.getValue();
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) ReflectionTestUtils.getField(message, "data");
        Object android = ReflectionTestUtils.getField(message, "androidConfig");

        assertThat(ReflectionTestUtils.getField(message, "notification")).isNull();
        assertThat(data).containsEntry("type", "ORDER_PUSH").containsEntry("event", "ORDER_PUSH")
                .containsEntry("offerExpiresAt", "123456789");
        assertThat(data).doesNotContainKeys("title", "message", "sound", "channelId");
        assertThat(ReflectionTestUtils.getField(android, "priority")).isEqualTo("high");
        assertThat(ReflectionTestUtils.getField(android, "ttl")).isEqualTo("5s");
        assertThat(outcome.category()).isEqualTo(OrderPushDeliveryOutcomeCategory.SUCCESS);
        assertThat(outcome.providerAccepted()).isTrue();
        assertThat(outcome.providerMessageId()).isEqualTo("provider-message-id");
        assertThat(outcome.recipientFingerprint()).doesNotContain("raw-test-token");
    }

    @Test
    void directFcmDisabledNeverClaimsATypedDeliveryResult() {
        PushNotificationService disabled = new PushNotificationService(redis, drivers, false, 5);
        OrderPushDeliveryOutcome outcome = disabled.sendOrderPushWithOutcome(9L, orderData());
        assertThat(outcome.category()).isEqualTo(OrderPushDeliveryOutcomeCategory.UNSUPPORTED_DELIVERY_PATH);
        assertThat(outcome.providerAccepted()).isFalse();
    }

    private static Map<String, Object> orderData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "ORDER_PUSH");
        data.put("event", "ORDER_PUSH");
        data.put("tripId", 77L);
        data.put("fromAddress", "A");
        data.put("toAddress", "B");
        data.put("price", 1000L);
        data.put("offerExpiresAt", 123456789L);
        data.put("fromLat", 41.0d);
        data.put("fromLon", 69.0d);
        data.put("toLat", 41.1d);
        data.put("toLon", 69.1d);
        return data;
    }
}
