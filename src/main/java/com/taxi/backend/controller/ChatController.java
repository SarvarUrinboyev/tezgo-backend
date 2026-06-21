package com.taxi.backend.controller;

import com.taxi.backend.model.User;
import com.taxi.backend.repository.UserRepository;
import com.taxi.backend.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * Chat endpointlari.
 *
 * REST (mobile polling uchun):
 *   GET  /api/chat/{tripId}        — xabarlar tarixi
 *   POST /api/chat/{tripId}        — xabar yuborish
 *
 * WebSocket (real-time uchun):
 *   /app/chat.{tripId}             — xabar yuborish
 *   /topic/chat/{tripId}           — xabarlar olish (subscribe)
 */
@Tag(name = "Chat", description = "Chat — haydovchi va yo'lovchi o'rtasida xabar almashish (REST + WebSocket)")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final UserRepository userRepository;

    public ChatController(ChatService chatService, UserRepository userRepository) {
        this.chatService = chatService;
        this.userRepository = userRepository;
    }

    @Operation(summary = "Chat tarixi", description = "Trip chat xabarlari tarixi. Faqat trip ishtirokchilari ko'ra oladi. Max 100 ta xabar.")
    @GetMapping("/{tripId}")
    public ResponseEntity<?> getMessages(@AuthenticationPrincipal User user,
                                          @PathVariable Long tripId,
                                          @RequestParam(defaultValue = "50") int limit) {
        if (!chatService.isUserInTrip(user, tripId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Bu trip chatiga kirish huquqi yo'q"));
        }
        return ResponseEntity.ok(chatService.getMessages(tripId, Math.min(limit, 100)));
    }

    @Operation(summary = "Xabar yuborish", description = "REST orqali chat xabar yuborish. WebSocket ham ishlaydi: /app/chat.{tripId}")
    @PostMapping("/{tripId}")
    public ResponseEntity<?> sendMessage(@AuthenticationPrincipal User user,
                                          @PathVariable Long tripId,
                                          @Valid @RequestBody com.taxi.backend.dto.ChatMessageRequest req) {
        if (!chatService.isUserInTrip(user, tripId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Bu trip chatiga kirish huquqi yo'q"));
        }
        try {
            return ResponseEntity.ok(chatService.sendMessage(tripId, user, req.getText()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Xabar yuborilmadi"));
        }
    }

    /** WebSocket orqali xabar yuborish (STOMP) — to'liq ishlaydi */
    @MessageMapping("/chat.{tripId}")
    public void wsMessage(@DestinationVariable Long tripId,
                          @Payload Map<String, Object> payload,
                          Principal principal) {
        String text = payload.get("text") != null ? payload.get("text").toString() : "";
        if (text.isBlank()) return;

        // Principal.getName() — Spring Security dan user phone
        String phone = principal != null ? principal.getName() : null;
        if (phone == null) return;

        // Phone orqali User topamiz
        userRepository.findByPhone(phone).ifPresent(user -> {
            try {
                chatService.sendMessage(tripId, user, text);
            } catch (Exception e) {
                log.warn("[WS Chat] xatolik: {}", e.getMessage());
            }
        });
    }
}
