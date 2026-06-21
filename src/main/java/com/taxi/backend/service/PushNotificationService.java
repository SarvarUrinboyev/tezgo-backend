package com.taxi.backend.service;

import com.taxi.backend.repository.DriverRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

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
    private final boolean directFcmEnabled = Boolean.parseBoolean(
            System.getenv().getOrDefault("DIRECT_FCM_ENABLED", "false"));
    private volatile FirebaseMessaging firebaseMessaging;

    // Redis ishlamasa, in-memory fallback
    private final Map<String, String> tokenCache = new ConcurrentHashMap<>();

    public PushNotificationService(StringRedisTemplate redis, DriverRepository driverRepository) {
        this.redis = redis;
        this.driverRepository = driverRepository;
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
                sendDirectFcm(token.substring(4), title, body, data, priority);
            } else {
                log.warning("[PUSH] Direct FCM disabled for current native APK; push skipped safely");
            }
            return;
        }
        if (!token.startsWith("ExponentPushToken")) return;
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
    private void sendDirectFcm(String rawToken, String title, String body,
                               Map<String, Object> data, String priority) {
        if (rawToken == null || rawToken.isBlank()) return;
        try {
            boolean isOrder = isOrder(data);

            Map<String, String> fcmData = new HashMap<>();
            if (data != null) {
                data.forEach((key, value) -> {
                    if (key != null && value != null) {
                        fcmData.put(key, String.valueOf(value));
                    }
                });
            }

            // Oddiy driver push'lari Expo notification delegate orqali ko'rinishi uchun
            // data payloadga uning Android kalitlarini ham qo'shamiz.
            if (!isOrder) {
                if (title != null) fcmData.put("title", title);
                if (body != null) fcmData.put("message", body);
                fcmData.put("channelId", "orders_v3");
                if (!"low".equals(priority)) fcmData.put("sound", "default");
            }

            Message message = Message.builder()
                    .setToken(rawToken)
                    .putAllData(fcmData)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .build();

            String messageId = getFirebaseMessaging().send(message);
            Object tripId = data != null ? data.get("tripId") : null;
            log.info("[PUSH] FCM accepted: tripId=" + tripId
                    + ", order=" + isOrder + ", messageId=" + messageId);
        } catch (Exception e) {
            log.warning("[PUSH] FCM send xato: " + e.getMessage());
        }
    }

    private FirebaseMessaging getFirebaseMessaging() {
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
}
