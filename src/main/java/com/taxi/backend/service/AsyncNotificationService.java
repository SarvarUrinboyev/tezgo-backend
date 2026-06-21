package com.taxi.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Async Notification Service — blocking WebSocket/Push xabarlarni alohida threadda yuborish.
 *
 * MUAMMO: TripService.bookTrip() ichida 50+ WebSocket xabar yuborilsa,
 * bitta Tomcat thread 200-500ms band bo'ladi. 200 parallel buyurtmada
 * barcha threadlar band — yangi so'rovlar kutadi.
 *
 * YECHIM: @Async — notification logikasi alohida thread pool da ishlaydi.
 * bookTrip() faqat DB yozadi (5-10ms) va darhol qaytadi.
 * WebSocket/Push xabarlar async yuboriladi — 100-200ms delay bor, lekin
 * foydalanuvchi buni sezmaydi.
 */
@Service
public class AsyncNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AsyncNotificationService.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final PushNotificationService pushService;

    public AsyncNotificationService(SimpMessagingTemplate messagingTemplate,
                                     PushNotificationService pushService) {
        this.messagingTemplate = messagingTemplate;
        this.pushService = pushService;
    }

    /** Haydovchiga WebSocket xabar — async */
    @Async
    public void notifyDriverAsync(Long driverId, Map<String, Object> message) {
        try {
            messagingTemplate.convertAndSend("/topic/driver/" + driverId, message);
        } catch (Exception e) {
            log.warn("Async driver notification xato: driverId={}, error={}", driverId, e.getMessage());
        }
    }

    /** Yo'lovchiga trip status WebSocket xabar — async */
    @Async
    public void notifyTripAsync(Long tripId, Map<String, Object> message) {
        try {
            messagingTemplate.convertAndSend("/topic/trip/" + tripId, message);
        } catch (Exception e) {
            log.warn("Async trip notification xato: tripId={}, error={}", tripId, e.getMessage());
        }
    }

    /** Haydovchiga push notification — async */
    @Async
    public void pushDriverAsync(Long driverId, String title, String body, Map<String, Object> data) {
        try {
            pushService.notifyDriver(driverId, title, body, data);
        } catch (Exception e) {
            log.warn("Async push xato: driverId={}, error={}", driverId, e.getMessage());
        }
    }

    /** Yo'lovchiga push notification — async */
    @Async
    public void pushPassengerAsync(Long passengerId, String title, String body, Map<String, Object> data) {
        try {
            pushService.notifyPassenger(passengerId, title, body, data);
        } catch (Exception e) {
            log.warn("Async push xato: passengerId={}, error={}", passengerId, e.getMessage());
        }
    }
}
