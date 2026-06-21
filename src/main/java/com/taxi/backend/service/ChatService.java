package com.taxi.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TripRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Haydovchi ↔ Yo'lovchi real-time chat.
 *
 * Xabarlar Redis'da list sifatida saqlanadi (24 soat TTL).
 * Key: chat:{tripId}
 *
 * WebSocket: xabar yuborilganda /topic/chat/{tripId} ga ham broadcast qilinadi.
 * Mobile polling uchun GET /api/chat/{tripId} endpointi ham mavjud.
 */
@Service
public class ChatService {

    private static final Logger log = Logger.getLogger(ChatService.class.getName());
    private static final int MAX_MESSAGES = 100;
    private static final long TTL_HOURS = 24;

    private final StringRedisTemplate redis;
    private final SimpMessagingTemplate messagingTemplate;
    private final TripRepository tripRepository;
    private final DriverRepository driverRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatService(StringRedisTemplate redis,
                       SimpMessagingTemplate messagingTemplate,
                       TripRepository tripRepository,
                       DriverRepository driverRepository) {
        this.redis = redis;
        this.messagingTemplate = messagingTemplate;
        this.tripRepository = tripRepository;
        this.driverRepository = driverRepository;
    }

    /**
     * Xabar yuborish.
     * @param tripId — qaysi trip
     * @param sender — kim yubordi (User)
     * @param text   — xabar matni
     * @return saqlangan xabar (Map)
     */
    public Map<String, Object> sendMessage(Long tripId, User sender, String text) {
        if (text == null || text.isBlank()) throw new RuntimeException("Xabar bo'sh bo'lmasligi kerak");
        if (text.length() > 500) throw new RuntimeException("Xabar juda uzun (max 500 belgi)");

        // Yuboruvchi rolini aniqlash
        String role = determineRole(sender, tripId);

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("id", UUID.randomUUID().toString().substring(0, 8));
        msg.put("tripId", tripId);
        msg.put("senderId", sender.getId());
        msg.put("senderName", sender.getName() != null ? sender.getName() : sender.getPhone());
        msg.put("role", role); // DRIVER | PASSENGER
        msg.put("text", text.trim());
        msg.put("sentAt", LocalDateTime.now().toString());
        msg.put("ts", System.currentTimeMillis());

        // Redis'ga saqlash (fallback: in-memory)
        String key = "chat:" + tripId;
        try {
            String json = objectMapper.writeValueAsString(msg);
            // In-memory ham saqlash
            chatCache.computeIfAbsent(key, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(json);
            try {
                redis.opsForList().rightPush(key, json);
                Long size = redis.opsForList().size(key);
                if (size != null && size > MAX_MESSAGES) redis.opsForList().leftPop(key);
                redis.expire(key, TTL_HOURS, TimeUnit.HOURS);
            } catch (Exception e) {
                log.warning("Redis chat save xato (in-memory fallback): " + e.getMessage());
            }
        } catch (Exception e) {
            log.warning("Chat save error: " + e.getMessage());
        }

        // WebSocket broadcast (shunda real-time ishlaydi)
        messagingTemplate.convertAndSend("/topic/chat/" + tripId, msg);

        return msg;
    }

    // In-memory fallback for chat when Redis is unavailable
    private final java.util.concurrent.ConcurrentHashMap<String, List<String>> chatCache
            = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Trip chat tarixini olish.
     * @param tripId — trip ID
     * @param limit  — oxirgi N ta xabar
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getMessages(Long tripId, int limit) {
        String key = "chat:" + tripId;
        List<String> jsons = null;
        try {
            Long size = redis.opsForList().size(key);
            if (size != null && size > 0) {
                long start = Math.max(0, size - limit);
                jsons = redis.opsForList().range(key, start, -1);
            }
        } catch (Exception e) {
            log.warning("Redis getMessages xato (in-memory fallback): " + e.getMessage());
        }
        // Fallback to in-memory
        if (jsons == null || jsons.isEmpty()) {
            List<String> cached = chatCache.get(key);
            if (cached == null || cached.isEmpty()) return List.of();
            int start = Math.max(0, cached.size() - limit);
            jsons = cached.subList(start, cached.size());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String json : jsons) {
            try {
                result.add(objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}));
            } catch (Exception e) {
                log.warning("Chat parse error: " + e.getMessage());
            }
        }
        return result;
    }

    /** Yuboruvchi rolini aniqlash — trip kontekstida */
    private String determineRole(User sender, Long tripId) {
        // ADMIN alohida rol sifatida
        if (sender.getRole() == com.taxi.backend.enums.Role.ADMIN) {
            return "ADMIN";
        }
        // Trip'dagi haydovchi ekanligini tekshirish (faqat driver record emas, aynan shu trip'ning haydovchisi)
        return tripRepository.findById(tripId)
                .map(trip -> {
                    if (trip.getDriver() != null) {
                        return driverRepository.findByUserId(sender.getId())
                                .map(d -> d.getId().equals(trip.getDriver().getId()) ? "DRIVER" : "PASSENGER")
                                .orElse("PASSENGER");
                    }
                    return "PASSENGER";
                })
                .orElse("PASSENGER");
    }

    /** XAVFSIZLIK: Foydalanuvchi bu tripning ishtirokchisi ekanligini tekshirish */
    public boolean isUserInTrip(User user, Long tripId) {
        if (user == null || tripId == null) return false;
        // ADMIN har qanday chatni ko'rishi mumkin
        if (user.getRole() == com.taxi.backend.enums.Role.ADMIN) return true;
        return tripRepository.findById(tripId).map(trip -> {
            // Yo'lovchi tekshiruvi
            if (trip.getPassenger() != null && trip.getPassenger().getId().equals(user.getId())) return true;
            // Haydovchi tekshiruvi
            if (trip.getDriver() != null) {
                return driverRepository.findByUserId(user.getId())
                        .map(d -> d.getId().equals(trip.getDriver().getId()))
                        .orElse(false);
            }
            return false;
        }).orElse(false);
    }
}
