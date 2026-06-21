package com.taxi.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxi.backend.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class PushOrderDataOnlyTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private DriverRepository driverRepository;

    private PushNotificationService pushService;
    private MockRestServiceServer server;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        pushService = new PushNotificationService(redis, driverRepository);
        RestTemplate restTemplate =
                (RestTemplate) ReflectionTestUtils.getField(pushService, "restTemplate");
        assertNotNull(restTemplate);
        server = MockRestServiceServer.bindTo(restTemplate).build();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("push:driver:6"))
                .thenReturn("ExponentPushToken[test-data-only]");
    }

    @Test
    void orderPushToExpoIsStrictlyDataOnly() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "ORDER_PUSH");
        data.put("event", "ORDER_PUSH");
        data.put("tripId", 777L);
        data.put("fromAddress", "Pickup");
        data.put("toAddress", "Destination");
        data.put("price", 12000L);
        data.put("offerExpiresAt", 123456789L);
        data.put("etaMinutes", 3);
        data.put("fromLat", 41.1);
        data.put("fromLon", 69.1);
        data.put("toLat", 41.2);
        data.put("toLon", 69.2);
        data.put("tariffName", "KOMFORT");
        data.put("calloutFee", 1200000L);

        server.expect(once(), requestTo("https://exp.host/--/api/v2/push/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request)
                            .getBodyAsString(StandardCharsets.UTF_8);
                    JsonNode payload = objectMapper.readTree(body);

                    assertEquals("ExponentPushToken[test-data-only]",
                            payload.path("to").asText());
                    assertEquals("high", payload.path("priority").asText());
                    assertTrue(payload.path("_contentAvailable").asBoolean());
                    assertTrue(payload.has("data"));

                    assertFalse(payload.has("title"));
                    assertFalse(payload.has("body"));
                    assertFalse(payload.has("sound"));
                    assertFalse(payload.has("channelId"));
                    assertFalse(payload.has("badge"));

                    JsonNode sentData = payload.path("data");
                    assertEquals("ORDER_PUSH", sentData.path("type").asText());
                    assertEquals("ORDER_PUSH", sentData.path("event").asText());
                    assertEquals(777L, sentData.path("tripId").asLong());
                    assertEquals("Pickup", sentData.path("fromAddress").asText());
                    assertEquals("Destination", sentData.path("toAddress").asText());
                    assertEquals("KOMFORT", sentData.path("tariffName").asText());
                    assertEquals(1200000L, sentData.path("calloutFee").asLong());
                })
                .andRespond(withSuccess(
                        "{\"data\":{\"status\":\"ok\",\"id\":\"ticket-1\"}}",
                        MediaType.APPLICATION_JSON));

        pushService.notifyDriver(6L, null, null, data);
        server.verify();
    }
}
