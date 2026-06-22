package com.taxi.backend.service;

import com.taxi.backend.enums.MessageChannel;
import com.taxi.backend.model.ChannelMessage;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.ChannelMessageRepository;
import com.taxi.backend.repository.DriverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kanal xabarlari (admin -> driver, faqat o'qish) — V39.
 *
 * Admin kanalga yuborganda har targetlangan haydovchiga bitta satr ochiladi (fan-out).
 * TEXNIK_YORDAM bu yerda EMAS — u SupportChatService (2 tomonlama). Push YO'Q (REST/poll).
 */
@Service
public class ChannelMessageService {

    private static final Logger log = LoggerFactory.getLogger(ChannelMessageService.class);
    private static final int MAX_BODY = 1000;

    private final ChannelMessageRepository repo;
    private final DriverRepository driverRepository;

    public ChannelMessageService(ChannelMessageRepository repo, DriverRepository driverRepository) {
        this.repo = repo;
        this.driverRepository = driverRepository;
    }

    /** Admin kanalga xabar yuboradi — har targetlangan haydovchiga bitta channel_messages satri. */
    @Transactional
    public Map<String, Object> sendToChannel(String channel, String title, String body, String target, User admin) {
        if (!MessageChannel.isBroadcastChannel(channel)) {
            throw new IllegalArgumentException("Bu kanalga broadcast qilib bo'lmaydi: " + channel
                    + " (TEXNIK_YORDAM 2 tomonlama — operator panelidan javob beriladi)");
        }
        if (body == null || body.trim().isEmpty()) {
            throw new IllegalArgumentException("Xabar matni bo'sh");
        }
        String cleanBody = body.trim();
        if (cleanBody.length() > MAX_BODY) cleanBody = cleanBody.substring(0, MAX_BODY);
        String cleanTitle = (title == null || title.isBlank()) ? null : title.trim();

        List<Driver> drivers = resolveAudience(target);
        Long adminId = admin != null ? admin.getId() : null;
        LocalDateTime now = LocalDateTime.now();

        List<ChannelMessage> batch = new ArrayList<>(drivers.size());
        for (Driver d : drivers) {
            ChannelMessage m = new ChannelMessage();
            m.setDriverId(d.getId());
            m.setChannel(channel);
            m.setTitle(cleanTitle);
            m.setBody(cleanBody);
            m.setSentBy(adminId);
            m.setCreatedAt(now);
            batch.add(m);
        }
        repo.saveAll(batch);
        log.info("[CHANNEL] Admin {} '{}' kanaliga {} ta haydovchiga xabar yubordi (target={})",
                adminId, channel, batch.size(), target);
        return Map.of("channel", channel, "sent", batch.size(), "target", target == null ? "ALL" : target);
    }

    private List<Driver> resolveAudience(String target) {
        String t = target == null ? "ALL" : target.toUpperCase();
        switch (t) {
            case "ACTIVE":  return driverRepository.findByIsOnlineTrue();
            case "OFFLINE": return driverRepository.findByIsOnlineFalse();
            default:        return driverRepository.findAll();
        }
    }

    // ─── Haydovchi tomoni (User -> driverId resolve) ─────────────────

    private Long driverIdOf(User user) {
        return driverRepository.findByUserId(user.getId())
                .map(Driver::getId)
                .orElseThrow(() -> new IllegalArgumentException("Haydovchi topilmadi"));
    }

    /** Haydovchi kanal xabarlari (eskidan yangiga) + ochilganda o'qilgan deb belgilash. */
    @Transactional
    public List<Map<String, Object>> getChannelMessages(User user, String channel) {
        Long driverId = driverIdOf(user);
        List<ChannelMessage> msgs = repo.findByDriverIdAndChannelOrderByCreatedAtAsc(driverId, channel);
        repo.markChannelRead(driverId, channel, LocalDateTime.now()); // ochildi -> o'qilgan (badge tozalanadi)
        List<Map<String, Object>> out = new ArrayList<>(msgs.size());
        for (ChannelMessage m : msgs) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("channel", m.getChannel());
            map.put("title", m.getTitle());
            map.put("body", m.getBody());
            map.put("createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
            out.add(map);
        }
        return out;
    }

    /** Har kanal bo'yicha o'qilmaganlar soni (badge) — broadcast kanallar (0 bilan to'ldirilgan). */
    @Transactional(readOnly = true)
    public Map<String, Long> unreadByChannel(User user) {
        Long driverId = driverIdOf(user);
        Map<String, Long> result = new LinkedHashMap<>();
        for (MessageChannel ch : MessageChannel.BROADCAST_CHANNELS) result.put(ch.name(), 0L);
        for (Object[] row : repo.unreadCountsByChannel(driverId)) {
            result.put((String) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    @Transactional
    public void markRead(User user, String channel) {
        repo.markChannelRead(driverIdOf(user), channel, LocalDateTime.now());
    }
}
