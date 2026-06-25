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

    /** Admin kanalga xabar yuboradi — eski (driverId yo'q) signatura. ALL/ACTIVE/OFFLINE uchun. */
    @Transactional
    public Map<String, Object> sendToChannel(String channel, String title, String body, String target, User admin) {
        return sendToChannel(channel, title, body, target, null, admin);
    }

    /**
     * Band 7 — Admin kanalga xabar yuboradi. target=DRIVER + driverId set bo'lsa, FAQAT shu
     * haydovchiga 1 ta channel_messages satri ochiladi (driver app polling orqali ko'radi).
     * Boshqa target qiymatlari (ALL/ACTIVE/OFFLINE) eski xulqni saqlaydi.
     *
     * Push YO'Q — bu kanal REST/poll xulqida ishlaydi (TEXNIK_YORDAM bilan bir xil); FSI/order-alert
     * tegmaydi. SupportChatService bilan parallel yo'l: bu broadcast (1 yo'nalishli), u esa
     * 2 tomonlama murojaat.
     */
    @Transactional
    public Map<String, Object> sendToChannel(String channel, String title, String body, String target,
            Long driverId, User admin) {
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

        List<Driver> drivers = resolveAudience(target, driverId);
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
        log.info("[CHANNEL] Admin {} '{}' kanaliga {} ta haydovchiga xabar yubordi (target={}, driverId={})",
                adminId, channel, batch.size(), target, driverId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("channel", channel);
        out.put("sent", batch.size());
        out.put("target", target == null ? "ALL" : target);
        if (drivers.size() == 1 && drivers.get(0).getDriverCode() != null) {
            out.put("driverCode", drivers.get(0).getDriverCode());
        }
        return out;
    }

    /**
     * target bo'yicha auditoriyani aniqlash. Band 7: target=DRIVER + driverId set bo'lsa, 1 ta
     * haydovchini qaytaradi (mavjudligini tekshiramiz). Driver topilmasa yoki driverId yo'q bo'lsa
     * IllegalArgumentException.
     */
    private List<Driver> resolveAudience(String target, Long driverId) {
        String t = target == null ? "ALL" : target.toUpperCase();
        switch (t) {
            case "ACTIVE":  return driverRepository.findByIsOnlineTrue();
            case "OFFLINE": return driverRepository.findByIsOnlineFalse();
            case "DRIVER": {
                if (driverId == null) {
                    throw new IllegalArgumentException("target=DRIVER uchun driverId majburiy");
                }
                Driver d = driverRepository.findById(driverId)
                        .orElseThrow(() -> new IllegalArgumentException("Haydovchi topilmadi: id=" + driverId));
                return List.of(d);
            }
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
