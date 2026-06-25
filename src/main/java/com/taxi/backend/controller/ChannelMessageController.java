package com.taxi.backend.controller;

import com.taxi.backend.dto.ChannelSendRequest;
import com.taxi.backend.enums.MessageChannel;
import com.taxi.backend.model.User;
import com.taxi.backend.service.ChannelMessageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Kanal xabarlari REST endpointlari (admin -> driver, faqat o'qish; V39).
 *
 * Class-level @RequestMapping YO'Q — har metod to'liq yo'l:
 *   - Haydovchi  /api/driver/channels/**  -> SecurityConfig: DRIVER/ADMIN. Faqat GET/mark-read (POST/yuborish YO'Q).
 *   - Admin       /api/admin/channels/**   -> SecurityConfig: ADMIN. Kanalga yuborish.
 * TEXNIK_YORDAM bu yerda RAD etiladi — u 2 tomonlama (/api/driver/support, operator paneli).
 */
@RestController
public class ChannelMessageController {

    private final ChannelMessageService service;

    public ChannelMessageController(ChannelMessageService service) {
        this.service = service;
    }

    // ─── Haydovchi tomoni (faqat o'qish — javob yozish endpointi YO'Q) ───
    @GetMapping("/api/driver/channels/{channel}")
    public ResponseEntity<?> driverChannel(@AuthenticationPrincipal User user, @PathVariable String channel) {
        if (!MessageChannel.isBroadcastChannel(channel)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bu kanal mavjud emas yoki 2 tomonlama (TEXNIK_YORDAM uchun /api/driver/support)"));
        }
        try {
            return ResponseEntity.ok(Map.of("messages", service.getChannelMessages(user, channel)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/api/driver/channels/unread")
    public ResponseEntity<?> driverChannelsUnread(@AuthenticationPrincipal User user) {
        try {
            return ResponseEntity.ok(Map.of("unread", service.unreadByChannel(user)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/api/driver/channels/{channel}/read")
    public ResponseEntity<?> driverMarkRead(@AuthenticationPrincipal User user, @PathVariable String channel) {
        if (!MessageChannel.isBroadcastChannel(channel)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Noto'g'ri kanal"));
        }
        try {
            service.markRead(user, channel);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    // ─── Admin tomoni — kanalga broadcast (TEXNIK_YORDAM server tomonida rad etiladi) ───
    @PostMapping("/api/admin/channels/{channel}/send")
    public ResponseEntity<?> adminSend(@AuthenticationPrincipal User admin, @PathVariable String channel,
                                       @Valid @RequestBody ChannelSendRequest req) {
        try {
            return ResponseEntity.ok(service.sendToChannel(channel, req.getTitle(), req.getBody(),
                    req.getTarget(), req.getDriverId(), admin));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
