package com.taxi.backend.controller;

import com.taxi.backend.dto.ChatMessageRequest;
import com.taxi.backend.model.User;
import com.taxi.backend.service.SupportChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Qo'llab-quvvatlash (support) chat REST endpointlari.
 *
 * Class-level @RequestMapping YO'Q — har metod to'liq yo'l bilan:
 *   - Yo'lovchi tomoni  /api/passenger/support/**  -> SecurityConfig: PASSENGER/DRIVER/ADMIN
 *   - Staff tomoni       /api/admin/support/**       -> SecurityConfig: ADMIN
 * Shu sababli SecurityConfig o'zgartirilmaydi.
 *
 * Transport: REST + polling (real-time WS yo'q). Manba — DB (SupportMessage).
 */
@RestController
public class SupportChatController {

    private final SupportChatService supportChat;

    public SupportChatController(SupportChatService supportChat) {
        this.supportChat = supportChat;
    }

    // ─── Yo'lovchi tomoni ──────────────────────────────────────────────
    @GetMapping("/api/passenger/support")
    public ResponseEntity<?> myThread(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("messages", supportChat.getThreadForPassenger(user)));
    }

    @PostMapping("/api/passenger/support")
    public ResponseEntity<?> send(@AuthenticationPrincipal User user,
                                  @Valid @RequestBody ChatMessageRequest req) {
        try {
            return ResponseEntity.ok(supportChat.sendFromPassenger(user, req.getText()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/api/passenger/support/unread")
    public ResponseEntity<?> myUnread(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("count", supportChat.passengerUnreadCount(user.getId())));
    }

    // ─── Staff (admin) tomoni ──────────────────────────────────────────
    @GetMapping("/api/admin/support/conversations")
    public ResponseEntity<?> conversations() {
        return ResponseEntity.ok(Map.of("conversations", supportChat.listConversations()));
    }

    @GetMapping("/api/admin/support/unread")
    public ResponseEntity<?> staffUnread() {
        return ResponseEntity.ok(Map.of("count", supportChat.operatorUnreadTotal()));
    }

    @GetMapping("/api/admin/support/{userId}")
    public ResponseEntity<?> thread(@PathVariable Long userId) {
        return ResponseEntity.ok(Map.of("messages", supportChat.getThreadForStaff(userId)));
    }

    @PostMapping("/api/admin/support/{userId}/reply")
    public ResponseEntity<?> reply(@AuthenticationPrincipal User staff,
                                   @PathVariable Long userId,
                                   @Valid @RequestBody ChatMessageRequest req) {
        try {
            return ResponseEntity.ok(supportChat.replyFromStaff(staff, userId, req.getText()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
