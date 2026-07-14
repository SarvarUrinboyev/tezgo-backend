package com.taxi.backend.service;

import com.taxi.backend.repository.DriverRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Expo Push Notification Service.
 * Redis mavjud bo'lsa - Redis'da saqlaydi.
 * Redis yo'q bo'lsa - in-memory fallback ishlatadi.
 */
@Service
public class PushNotificationService {

    private static final Logger log = Logger.getLogger(PushNotificationService.class.getName());
    private static final String EXPO_API = "https://exp.host/--/api/v2/push/send";

    private final StringRedisTemplate redis;
    private final DriverRepository driverRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final boolean directFcmEnabled;
    private final long orderFcmTransportTtlMillis;
    private volatile FirebaseMessaging firebaseMessaging;

    // Redis ishlamasa, in-memory fallback
    private final Map<String, String> tokenCache = new ConcurrentHashMap<>();

    /** Existing callers and narrow unit tests retain this constructor. */
    public PushNotificationService(StringRedisTemplate redis, DriverRepository driverRepository) {
        this(redis, driverRepository,
                Boolean.parseBoolean(System.getenv().getOrDefault("DIRECT_FCM_ENABLED", "false")),
                5);
    }

    @Autowired
    public PushNotificationService(StringRedisTemplate redis, DriverRepository driverRepository,
                                   @Value("${DIRECT_FCM_ENABLED:false}") boolean directFcmEnabled,
                                   @Value("${app.dispatch.order-fcm-transport-ttl-seconds:5}") long orderFcmTransportTtlSeconds) {
        this.redis = redis;
        this.driverRepository = driverRepository;
        this.directFcmEnabled = directFcmEnabled;
        this.orderFcmTransportTtlMillis = TimeUnit.SECONDS.toMillis(
                requireValidOrderTtlSeconds(orderFcmTransportTtlSeconds));
    }

    private static long requireValidOrderTtlSeconds(long seconds) {
        if (seconds <= 0 || seconds > 2_419_200) {
            throw new IllegalArgumentException("order FCM transport TTL must be 1..2419200 seconds");
        }
        return seconds;
    }

    /** Push tokenni saqlash */
    public void saveToken(String role, Long userId, String token) {
        if (token == null || token.isBlank()) return;
        String key = "push:" + role + ":" + userId;
        tokenCache.put(key, token);
        try {
            redis.opsForValue().set(key, token, 90, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warning("Redis saveToken xato (in-memory fallback): " + e.getMessage());
        }
        // Driver tokenini DB ga ham saqlash — Redis qayta ishga tushganda fallback
        if ("driver".equals(role)) {
            try {
                driverRepository.updatePushToken(userId, token);
            } catch (Exception e) {
                log.warning("DB saveDriverPushToken xato: " + e.getMessage());
            }
        }
    }

    /** Driver uchun push token olish */
    public String getDriverToken(Long driverId) {
        String key = "push:driver:" + driverId;
        try {
            String val = redis.opsForValue().get(key);
            if (val != null) return val;
        } catch (Exception e) {
            log.warning("Redis getDriverToken xato (in-memory fallback)");
        }
        String memVal = tokenCache.get(key);
        if (memVal != null) return memVal;
        // DB fallback — server restart yoki Redis+memory ikkalasi bo'sh bo'lganda
        try {
            return driverRepository.findPushTokenById(driverId);
        } catch (Exception e) {
            log.warning("DB getDriverToken xato: " + e.getMessage());
        }
        return null;
    }

    /** Passenger uchun push token olish */
    public String getPassengerToken(Long passengerId) {
        String key = "push:passenger:" + passengerId;
        try {
            String val = redis.opsForValue().get(key);
            if (val != null) return val;
        } catch (Exception e) {
            log.warning("Redis getPassengerToken xato (in-memory fallback)");
        }
        return tokenCache.get(key);
    }

    /** Haydovchiga xabar yuborish */
    public void notifyDriver(Long driverId, String title, String body, Map<String, Object> data) {
        String token = getDriverToken(driverId);
        Object tripId = data != null ? data.get("tripId") : null;
        String type = data != null ? String.valueOf(data.get("type")) : "";
        boolean isOrder = isOrder(data);
        log.info("[PUSH] driverId=" + driverId + ", tripId=" + tripId
                + ", type=" + type + ", tokenPresent=" + (token != null && !token.isBlank()));
        if (isOrder) {
            String route = String.valueOf(data.get("fromAddress"))
                    + " -> " + String.valueOf(data.get("toAddress"));
            log.info("[PUSH][ORDER][DATA_ONLY] driverId=" + driverId
                    + ", tripId=" + tripId + ", route=" + route
                    + ", keys=" + data.keySet());
            if (title != null || body != null) {
                log.warning("[PUSH][ORDER][BUG] title/body must be null for data-only order push"
                        + " driverId=" + driverId + ", tripId=" + tripId);
            }
        }
        send(token, title, body, data, "high");
    }

    /**
     * Approved AO-P1-03 order-only API. It preserves the same data-only builder
     * while exposing only a sanitized provider outcome to dispatch lifecycle code.
     */
    public OrderPushDeliveryOutcome sendOrderPushWithOutcome(Long driverId, Map<String, Object> data) {
        if (!isOrder(data)) {
            throw new IllegalArgumentException("typed order push requires ORDER_PUSH or NEW_ORDER data");
        }
        String token = getDriverToken(driverId);
        if (token == null || token.isBlank()) {
            return unsupported("TOKEN_MISSING", null, false);
        }
        String fingerprint = fingerprint(token);
        OrderPushDeliveryOutcome outcome;
        if (token.startsWith("FCM:")) {
            if (!directFcmEnabled) {
                log.warning("[PUSH][ORDER] direct FCM disabled; typed delivery is unsupported");
                return unsupported("DIRECT_FCM_DISABLED", fingerprint, true);
            }
            outcome = sendDirectFcm(token.substring(4), null, null, data, "high", fingerprint);
        } else if (token.startsWith("ExponentPushToken")) {
            // Preserve legacy Expo data-only delivery, but never pretend it has an FCM typed receipt or TTL.
            sendExpo(token, null, null, data, "high");
            outcome = unsupported("EXPO_UNTYPED", fingerprint, true);
        } else {
            outcome = unsupported("TOKEN_PATH_UNSUPPORTED", fingerprint, true);
        }
        return outcome.withRecipientStillCurrent(token.equals(getDriverToken(driverId)));
    }

    /** Yo'lovchiga xabar yuborish */
    public void notifyPassenger(Long passengerId, String title, String body, Map<String, Object> data) {
        String token = getPassengerToken(passengerId);
        send(token, title, body, data, "high");
    }

    /** Barcha online haydovchilarga broadcast xabar yuborish */
    public void notifyAllOnlineDrivers(String title, String body, Map<String, Object> data) {
        notifyAllOnlineDriversExcluding(Set.of(), title, body, data);
    }

    /**
     * Allaqachon xabardor qilinganlarni chiqarib barcha online haydovchilarga yuborish.
     * broadcastSearchingTrips() — to'g'ridan-to'g'ri matching fazasida
     * notificatsiya olgan haydovchilarga qayta yubormaslik uchun.
     */
    public void notifyAllOnlineDriversExcluding(Set<Long> excludeIds, String title, String body, Map<String, Object> data) {
        List<com.taxi.backend.model.Driver> drivers = driverRepository.findByIsOnlineTrue();
        for (com.taxi.backend.model.Driver d : drivers) {
            if (excludeIds.contains(d.getId())) continue;
            String token = getDriverToken(d.getId());
            send(token, title, body, data, "high");
        }
    }

    /**
     * Barcha online haydovchilarga, lekin faqat mos tarif VA mos xizmatlarni qabul qiladiganlariga yuborish.
     * selectedServices — buyurtma tanlagan xizmatlar (CSV). Bo'sh bo'lsa xizmat filtri qo'llanilmaydi.
     */
    public void notifyAllOnlineDriversExcludingByTariff(Set<Long> excludeIds, String tariffName,
                                                        String selectedServices,
                                                        String title, String body, Map<String, Object> data) {
        List<com.taxi.backend.model.Driver> drivers = driverRepository.findByIsOnlineTrue();
        // Service hard-filter: buyurtmada xizmat bo'lsa, har bir haydovchining yoqilgan xizmatlarini batch o'qiymiz
        boolean serviceFilter = selectedServices != null && !selectedServices.isBlank();
        Map<Long, Set<String>> enabledByDriver = serviceFilter
                ? enabledServicesByDriverIds(drivers.stream()
                        .map(com.taxi.backend.model.Driver::getId).collect(java.util.stream.Collectors.toList()))
                : Map.of();
        for (com.taxi.backend.model.Driver d : drivers) {
            if (excludeIds.contains(d.getId())) continue;
            if (d.getBalance() != null && d.getBalance() < 0) continue; // Manfiy balans (strict >= 0) — skip
            if (d.isInCooldown()) continue; // rad etish cooldown'i — skip
            if (!DriverTariffFilter.accepts(d, tariffName)) continue;
            if (!DriverServiceFilter.accepts(enabledByDriver.get(d.getId()), selectedServices)) continue; // xizmat mos emas — skip
            String token = getDriverToken(d.getId());
            send(token, title, body, data, "high");
        }
    }

    /** Bir nechta haydovchining yoqilgan xizmatlari — Map<driverId, {ServiceType kodlari}> (batch, N+1 fix). */
    private Map<Long, Set<String>> enabledServicesByDriverIds(java.util.Collection<Long> driverIds) {
        if (driverIds.isEmpty()) return Map.of();
        Map<Long, Set<String>> map = new HashMap<>();
        for (Object[] row : driverRepository.findEnabledServiceRowsByDriverIds(driverIds)) {
            Long id = (Long) row[0];
            com.taxi.backend.enums.ServiceType st = (com.taxi.backend.enums.ServiceType) row[1];
            map.computeIfAbsent(id, k -> new java.util.HashSet<>()).add(st.name());
        }
        return map;
    }

    /** Bitta haydovchidan tashqari barcha online haydovchilarga past-prioritet xabar */
    public void notifyAllOnlineDriversExcept(Long excludeDriverId, String title, String body, Map<String, Object> data) {
        List<com.taxi.backend.model.Driver> drivers = driverRepository.findByIsOnlineTrue();
        for (com.taxi.backend.model.Driver d : drivers) {
            if (d.getId().equals(excludeDriverId)) continue;
            String token = getDriverToken(d.getId());
            send(token, title, body, data, "low");
        }
    }

    private void send(String token, String title, String body, Map<String, Object> data, String priority) {
        if (token == null || token.isBlank()) return;
        if (token.startsWith("FCM:")) {
            if (directFcmEnabled) {
                sendDirectFcm(token.substring(4), title, body, data, priority, fingerprint(token));
            } else {
                log.warning("[PUSH] Direct FCM disabled for current native APK; push skipped safely");
            }
            return;
        }
        if (!token.startsWith("ExponentPushToken")) return;
        sendExpo(token, title, body, data, priority);
    }

    private void sendExpo(String token, String title, String body, Map<String, Object> data, String priority) {
        try {
            // Buyurtma push'imi? (yangi buyurtma — to'liq-ekran "kiruvchi qo'ng'iroq" oqimi)
            boolean isOrder = isOrder(data);

            Map<String, Object> payload = new HashMap<>();
            payload.put("to", token);

            if (isOrder) {
                // Qat'iy DATA-ONLY: Android system tray emas, app onMessageReceived ishlaydi.
                payload.put("data", data);
                payload.put("priority", "high");
                payload.put("_contentAvailable", true);
            } else {
                // Boshqa push'lar: oddiy notification + data (avvalgidek).
                payload.put("title", title);
                payload.put("body", body);
                payload.put("sound", "low".equals(priority) ? null : "default");
                payload.put("priority", priority);
                payload.put("badge", 1);
                payload.put("channelId", "orders_v3");
                if (data != null) payload.put("data", data);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");
            headers.set("Accept-Encoding", "gzip, deflate");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            Object response = restTemplate.postForObject(EXPO_API, request, Object.class);
            Object tripId = data != null ? data.get("tripId") : null;
            log.info("[PUSH] Expo accepted: tripId=" + tripId
                    + ", order=" + isOrder + ", response=" + response);
        } catch (Exception e) {
            log.warning("Push notification yuborishda xato: " + e.getMessage());
        }
    }

    /**
     * Expo Push Service Android custom data'ni `body` JSON ichiga o'raydi. O'rnatilgan
     * TezgoMessagingService ORDER_PUSH type'ini top-level data'dan kutadi, shuning uchun
     * haydovchining raw FCM tokeniga bevosita data-message yuboramiz.
     */
    private OrderPushDeliveryOutcome sendDirectFcm(String rawToken, String title, String body,
                                                    Map<String, Object> data, String priority,
                                                    String recipientFingerprint) {
        if (rawToken == null || rawToken.isBlank()) {
            return unsupported("TOKEN_MISSING", recipientFingerprint, false);
        }
        try {
            boolean isOrder = isOrder(data);
            Message message = buildDirectFcmMessage(rawToken, title, body, data, priority);

            String messageId = getFirebaseMessaging().send(message);
            Object tripId = data != null ? data.get("tripId") : null;
            log.info("[PUSH] FCM accepted: tripId=" + tripId
                    + ", order=" + isOrder + ", messageId=" + messageId);
            return new OrderPushDeliveryOutcome(OrderPushDeliveryOutcomeCategory.SUCCESS,
                    null, messageId, recipientFingerprint, true, true);
        } catch (FirebaseMessagingException exception) {
            OrderPushDeliveryOutcomeCategory category =
                    OrderPushDeliveryOutcomeClassifier.classify(exception.getMessagingErrorCode());
            String code = exception.getMessagingErrorCode() == null
                    ? null : exception.getMessagingErrorCode().name();
            log.warning("[PUSH] FCM typed send failure category=" + category + ", code=" + code);
            return new OrderPushDeliveryOutcome(category, code, null, recipientFingerprint, true, false);
        } catch (Exception e) {
            log.warning("[PUSH] FCM send xato: " + e.getMessage());
            return new OrderPushDeliveryOutcome(OrderPushDeliveryOutcomeCategory.UNKNOWN_FAILURE,
                    e.getClass().getSimpleName(), null, recipientFingerprint, true, false);
        }
    }

    /** One authoritative direct-FCM builder for legacy and typed order delivery. */
    Message buildDirectFcmMessage(String rawToken, String title, String body,
                                  Map<String, Object> data, String priority) {
        boolean isOrder = isOrder(data);
        Map<String, String> fcmData = new HashMap<>();
        if (data != null) {
            data.forEach((key, value) -> {
                if (key != null && value != null) fcmData.put(key, String.valueOf(value));
            });
        }
        if (!isOrder) {
            if (title != null) fcmData.put("title", title);
            if (body != null) fcmData.put("message", body);
            fcmData.put("channelId", "orders_v3");
            if (!"low".equals(priority)) fcmData.put("sound", "default");
        }
        AndroidConfig.Builder android = AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH);
        if (isOrder) android.setTtl(orderFcmTransportTtlMillis);
        return Message.builder().setToken(rawToken).putAllData(fcmData).setAndroidConfig(android.build()).build();
    }

    protected FirebaseMessaging getFirebaseMessaging() {
        FirebaseMessaging current = firebaseMessaging;
        if (current != null) return current;
        synchronized (this) {
            if (firebaseMessaging == null) {
                FirebaseApp app = FirebaseApp.getApps().isEmpty()
                        ? FirebaseApp.initializeApp()
                        : FirebaseApp.getInstance();
                firebaseMessaging = FirebaseMessaging.getInstance(app);
                log.info("[PUSH] Firebase Admin initialized");
            }
            return firebaseMessaging;
        }
    }

    private boolean isOrder(Map<String, Object> data) {
        if (data == null) return false;
        String type = String.valueOf(data.get("type"));
        return "NEW_ORDER".equals(type) || "ORDER_PUSH".equals(type);
    }

    private static OrderPushDeliveryOutcome unsupported(String code, String fingerprint, boolean stillCurrent) {
        return new OrderPushDeliveryOutcome(OrderPushDeliveryOutcomeCategory.UNSUPPORTED_DELIVERY_PATH,
                code, null, fingerprint, stillCurrent, false);
    }

    private static String fingerprint(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(16);
            for (int index = 0; index < 8; index++) value.append(String.format("%02x", digest[index]));
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
