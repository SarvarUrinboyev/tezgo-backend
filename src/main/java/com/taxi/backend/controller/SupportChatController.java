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

    // ─── Haydovchi tomoni (DRIVER) — /api/driver/** -> SecurityConfig: DRIVER/ADMIN ──
    @GetMapping("/api/driver/support")
    public ResponseEntity<?> driverThread(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("messages", supportChat.getThreadForDriver(user)));
    }

    @PostMapping("/api/driver/support")
    public ResponseEntity<?> driverSend(@AuthenticationPrincipal User user,
                                        @Valid @RequestBody ChatMessageRequest req) {
        try {
            return ResponseEntity.ok(supportChat.sendFromDriver(user, req.getText()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/api/driver/support/unread")
    public ResponseEntity<?> driverMyUnread(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("count", supportChat.driverUnreadCount(user.getId())));
    }

    // ─── Operator tomoni (HAYDOVCHI support) — /api/operator/** -> SecurityConfig: OPERATOR ──
    // Admin support (/api/admin/**) ADMIN-only; operator panel OPERATOR roli — shu sababli alohida yo'l.
    @GetMapping("/api/operator/support/driver-conversations")
    public ResponseEntity<?> driverConversations() {
        return ResponseEntity.ok(Map.of("conversations", supportChat.listDriverConversations()));
    }

    @GetMapping("/api/operator/support/driver-unread")
    public ResponseEntity<?> driverInboxUnread() {
        return ResponseEntity.ok(Map.of("count", supportChat.driverInboxUnreadTotal()));
    }

    @GetMapping("/api/operator/support/driver/{userId}")
    public ResponseEntity<?> driverThreadForOperator(@PathVariable Long userId) {
        return ResponseEntity.ok(Map.of("messages", supportChat.getThreadForStaff(userId)));
    }

    @PostMapping("/api/operator/support/driver/{userId}/reply")
    public ResponseEntity<?> driverReply(@AuthenticationPrincipal User staff,
                                         @PathVariable Long userId,
                                         @Valid @RequestBody ChatMessageRequest req) {
        try {
            return ResponseEntity.ok(supportChat.replyFromStaff(staff, userId, req.getText()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
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

    // ─── Admin (superadmin panel) — HAYDOVCHI support (Texnik yordam), /api/admin/** (ADMIN) ──
    // Operator endpointlari OPERATOR-only; superadmin panel ADMIN roli — shu sababli ADMIN-accessible mirror.
    // AYNAN bir xil SupportChatService (driver threadlari) — yangi mantiq/yangi store YO'Q. Feature B.
    @GetMapping("/api/admin/support/driver-conversations")
    public ResponseEntity<?> adminDriverConversations() {
        return ResponseEntity.ok(Map.of("conversations", supportChat.listDriverConversations()));
    }

    @GetMapping("/api/admin/support/driver/{userId}")
    public ResponseEntity<?> adminDriverThread(@PathVariable Long userId) {
        return ResponseEntity.ok(Map.of("messages", supportChat.getThreadForStaff(userId)));
    }

    @PostMapping("/api/admin/support/driver/{userId}/reply")
    public ResponseEntity<?> adminDriverReply(@AuthenticationPrincipal User staff,
                                              @PathVariable Long userId,
                                              @Valid @RequestBody ChatMessageRequest req) {
        try {
            return ResponseEntity.ok(supportChat.replyFromStaff(staff, userId, req.getText()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
