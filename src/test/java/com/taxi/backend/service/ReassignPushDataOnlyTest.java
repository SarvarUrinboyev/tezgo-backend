package com.taxi.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * P4 — REASSIGN push'ining DATA-ONLY shakli MAVJUD order push bilan AYNAN bir xil.
 * AdminService.adminReassignTripToDriver pushData'ni quradi -> uni HAQIQIY
 * PushNotificationService.notifyDriver orqali yuboramiz -> Expo wire payload
 * PushOrderDataOnlyTest'dagi bilan bir xil data-only (title/body/sound/channelId/badge YO'Q).
 */
@ExtendWith(MockitoExtension.class)
class ReassignPushDataOnlyTest {

    // AdminService deps
    @Mock private DriverRepository driverRepository;
    @Mock private DriverPhotoRepository driverPhotoRepository;
    @Mock private DriverServiceRepository driverServiceRepository;
    @Mock private TripRepository tripRepository;
    @Mock private BroadcastMessageRepository broadcastMessageRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private RatingRepository ratingRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private ChatService chatService;
    @Mock private TariffRepository tariffRepository;
    @Mock private AsyncNotificationService asyncNotifier;

    // PushNotificationService deps
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private DriverRepository pushDriverRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Reassign push B'ga -> notifyDriver orqali AYNAN data-only order push shakli")
    @SuppressWarnings("unchecked")
    void reassignPush_isByteShapeIdenticalDataOnly() {
        // 1) AdminService.adminReassignTripToDriver reassign pushData'ni quradi (kuzatamiz)
        AdminService admin = new AdminService(driverRepository, driverPhotoRepository, driverServiceRepository,
                tripRepository, broadcastMessageRepository, userRepository, messagingTemplate,
                ratingRepository, transactionRepository, chatService, tariffRepository, asyncNotifier);

        Trip trip = new Trip();
        trip.setId(777L);
        trip.setStatus(TripStatus.SEARCHING);
        trip.setFromAddress("Pickup");
        trip.setToAddress("Destination");
        trip.setTotalPrice(12000_00L);
        trip.setFromLat(41.1); trip.setFromLon(69.1);
        trip.setToLat(41.2); trip.setToLon(69.2);
        Driver b = new Driver();
        b.setId(6L);
        b.setStatus(DriverStatus.ACTIVE);
        b.setDriverCode("TZ-9");
        User u = new User(); u.setName("Bek"); b.setUser(u);
        when(tripRepository.findById(777L)).thenReturn(Optional.of(trip));
        when(driverRepository.findById(6L)).thenReturn(Optional.of(b));

        admin.adminReassignTripToDriver(777L, 6L);

        ArgumentCaptor<String> titleCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> dataCap = ArgumentCaptor.forClass(Map.class);
        verify(asyncNotifier).pushDriverAsync(eq(6L), titleCap.capture(), bodyCap.capture(), dataCap.capture());
        String reassignTitle = titleCap.getValue();
        String reassignBody = bodyCap.getValue();
        Map<String, Object> reassignData = dataCap.getValue();
        assertNull(reassignTitle, "reassign push: title null bo'lishi kerak");
        assertNull(reassignBody, "reassign push: body null bo'lishi kerak");
        assertEquals("ORDER_PUSH", reassignData.get("type"));

        // 2) HAQIQIY PushNotificationService.notifyDriver orqali yuboramiz -> wire shaklini tekshiramiz
        PushNotificationService pushService = new PushNotificationService(redis, pushDriverRepository);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(pushService, "restTemplate");
        assertNotNull(restTemplate);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("push:driver:6")).thenReturn("ExponentPushToken[reassign-data-only]");

        server.expect(once(), requestTo("https://exp.host/--/api/v2/push/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
                    JsonNode payload = objectMapper.readTree(body);
                    // DATA-ONLY wire shape — PushOrderDataOnlyTest bilan AYNAN bir xil
                    assertEquals("ExponentPushToken[reassign-data-only]", payload.path("to").asText());
                    assertEquals("high", payload.path("priority").asText());
                    assertTrue(payload.path("_contentAvailable").asBoolean());
                    assertTrue(payload.has("data"));
                    assertFalse(payload.has("title"));
                    assertFalse(payload.has("body"));
                    assertFalse(payload.has("sound"));
                    assertFalse(payload.has("channelId"));
                    assertFalse(payload.has("badge"));
                    assertEquals("ORDER_PUSH", payload.path("data").path("type").asText());
                    assertEquals(777L, payload.path("data").path("tripId").asLong());
                })
                .andRespond(withSuccess("{\"data\":{\"status\":\"ok\"}}", MediaType.APPLICATION_JSON));

        pushService.notifyDriver(6L, reassignTitle, reassignBody, reassignData);
        server.verify();
    }
}
