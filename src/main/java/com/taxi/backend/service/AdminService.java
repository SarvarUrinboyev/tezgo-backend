package com.taxi.backend.service;

import com.taxi.backend.dto.response.*;
import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.enums.PhotoStatus;
import com.taxi.backend.enums.TransactionType;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.exception.ConflictException;
import com.taxi.backend.model.*;
import com.taxi.backend.model.Trip;
import com.taxi.backend.repository.*;
import com.taxi.backend.repository.DriverServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final DriverRepository driverRepository;
    private final DriverPhotoRepository driverPhotoRepository;
    private final DriverServiceRepository driverServiceRepository;
    private final TripRepository tripRepository;
    private final BroadcastMessageRepository broadcastMessageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RatingRepository ratingRepository;
    private final TransactionRepository transactionRepository;
    private final ChatService chatService;
    // Band 5 (admin order mgmt) — tariff change + reassign push
    private final TariffRepository tariffRepository;
    private final AsyncNotificationService asyncNotifier;
    private final PhotoService photoService;
    private final TripNotificationHelper notificationHelper;

    public AdminService(DriverRepository driverRepository,
            DriverPhotoRepository driverPhotoRepository,
            DriverServiceRepository driverServiceRepository,
            TripRepository tripRepository,
            BroadcastMessageRepository broadcastMessageRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate,
            RatingRepository ratingRepository,
            TransactionRepository transactionRepository,
            ChatService chatService,
            TariffRepository tariffRepository,
            AsyncNotificationService asyncNotifier,
            PhotoService photoService) {
        this(driverRepository, driverPhotoRepository, driverServiceRepository, tripRepository,
                broadcastMessageRepository, userRepository, messagingTemplate, ratingRepository,
                transactionRepository, chatService, tariffRepository, asyncNotifier, photoService, null);
    }

    @Autowired
    public AdminService(DriverRepository driverRepository,
            DriverPhotoRepository driverPhotoRepository,
            DriverServiceRepository driverServiceRepository,
            TripRepository tripRepository,
            BroadcastMessageRepository broadcastMessageRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate,
            RatingRepository ratingRepository,
            TransactionRepository transactionRepository,
            ChatService chatService,
            TariffRepository tariffRepository,
            AsyncNotificationService asyncNotifier,
            PhotoService photoService,
            TripNotificationHelper notificationHelper) {
        this.driverRepository = driverRepository;
        this.driverPhotoRepository = driverPhotoRepository;
        this.driverServiceRepository = driverServiceRepository;
        this.tripRepository = tripRepository;
        this.broadcastMessageRepository = broadcastMessageRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.ratingRepository = ratingRepository;
        this.transactionRepository = transactionRepository;
        this.chatService = chatService;
        this.tariffRepository = tariffRepository;
        this.asyncNotifier = asyncNotifier;
        this.photoService = photoService;
        this.notificationHelper = notificationHelper;
    }

    /** Dashboard statistika */
    public DashboardStatsResponse getDashboardStats() {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();

        long totalDrivers = driverRepository.count();
        long activeDrivers = driverRepository.countByStatus(DriverStatus.ACTIVE);
        long pendingDrivers = driverRepository.countByStatus(DriverStatus.PENDING);
        long onlineDrivers = driverRepository.countByIsOnlineTrue();

        long totalTrips = tripRepository.count();
        long todayTrips = tripRepository.countByStatusAndCreatedAtAfter(TripStatus.COMPLETED, todayStart);
        long searchingTrips = tripRepository.countByStatus(TripStatus.SEARCHING);

        Long todayRevenue = tripRepository.sumRevenueAfter(todayStart);

        return new DashboardStatsResponse(
                totalDrivers, activeDrivers, pendingDrivers, onlineDrivers,
                totalTrips, todayTrips, searchingTrips,
                (todayRevenue != null ? todayRevenue : 0) / 100);
    }

    /** Haydovchilar ro'yxati — status bo'yicha filter bilan */
    public Page<DriverListResponse> getDrivers(String status, Pageable pageable) {
        Page<Driver> drivers;
        if (status != null && !status.isEmpty()) {
            try {
                DriverStatus driverStatus = DriverStatus.valueOf(status.toUpperCase());
                drivers = driverRepository.findByStatusWithUser(driverStatus, pageable);
            } catch (IllegalArgumentException e) {
                // Noto'g'ri status — barcha haydovchilarni qaytarish
                drivers = driverRepository.findAllWithUser(pageable);
            }
        } else {
            drivers = driverRepository.findAllWithUser(pageable);
        }

        return drivers.map(d -> new DriverListResponse(
                d.getId(), d.getUser().getName(), d.getUser().getPhone(), d.getDriverCode(),
                d.getCarModel(), d.getCarNumber(), d.getStatus().name(),
                d.isOnline(), d.getRating(), d.getTotalTrips(), d.getBalance() / 100,
                d.getCarColor(), d.getCarYear(), d.getPassportSeries(), d.getPassportNumber(),
                d.getBirthDate(), d.getAddress(), d.getTechPassportNumber(), d.getAcceptedTariffs(),
                d.getVerifiedAt() != null ? d.getVerifiedAt().toString() : null,
                d.getActivityScore(), d.getLatitude(), d.getLongitude(),
                d.getUser().getCreatedAt() != null ? d.getUser().getCreatedAt().toString() : null,
                d.getUser().getAvatarUrl()));
    }

    /** Haydovchini tasdiqlash */
    @Transactional
    public Map<String, Object> approveDriver(Long driverId, User admin) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        driver.setStatus(DriverStatus.ACTIVE);
        driver.setVerifiedAt(LocalDateTime.now());
        driverRepository.save(driver);

        // Haydovchiga xabar
        messagingTemplate.convertAndSend("/topic/driver/" + driverId,
                Map.of("type", "APPROVED", "message", "Tabriklaymiz! Siz tasdiqlangansiz 🎉"));

        return Map.of("message", "Haydovchi tasdiqlandi", "driverId", driverId);
    }

    /** Haydovchini o'chirish — barcha bog'liq ma'lumotlar bilan */
    @Transactional
    public void deleteDriver(Long driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        Long userId = driver.getUser().getId();

        // 1. Trip'lardagi driver FK → null
        tripRepository.nullifyDriver(driverId);

        // 2. Driver user ID bo'yicha boshqa FK lar → null (passenger, reviewedBy, fromUser)
        userRepository.nullifyPassengerInTrips(userId);
        userRepository.nullifyReviewedByInPhotos(userId);
        userRepository.nullifyFromUserInRatings(userId);

        // 3. Reyting/baholarni o'chirish (toDriver = this driver)
        ratingRepository.deleteAll(ratingRepository.findByToDriverId(driverId));

        // 4. Tranzaksiyalarni o'chirish
        transactionRepository.deleteAll(transactionRepository.findByDriverId(driverId));

        // 5. Xizmatlar (DriverService) o'chirish
        driverServiceRepository.deleteAll(driverServiceRepository.findByDriverId(driverId));

        // 6. Rasmlarni o'chirish
        driverPhotoRepository.deleteAll(driverPhotoRepository.findByDriverId(driverId));

        // 7. Driver record o'chirish
        driverRepository.deleteById(driverId);

        // 8. User o'chirish
        userRepository.deleteById(userId);
    }

    /** Haydovchini bloklash */
    @Transactional
    public Map<String, Object> blockDriver(Long driverId, String reason) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        driver.setStatus(DriverStatus.BLOCKED);
        driver.setOnline(false);
        driverRepository.save(driver);

        messagingTemplate.convertAndSend("/topic/driver/" + driverId,
                Map.of("type", "BLOCKED", "message", "Hisobingiz bloklandi: " + reason));

        return Map.of("message", "Haydovchi bloklandi");
    }

    /** Haydovchi rasmlari */
    public List<Map<String, Object>> getDriverPhotos(Long driverId) {
        return driverPhotoRepository.findByDriverId(driverId).stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("type", p.getPhotoType().name());
            m.put("status", p.getStatus().name());
            m.put("photoUrl", p.getPhotoUrl());
            m.put("rejectReason", p.getRejectReason());
            return m;
        }).collect(Collectors.toList());
    }

    /** Rasmni tasdiqlash */
    @Transactional
    public Map<String, Object> approvePhoto(Long photoId, User admin) {
        DriverPhoto photo = driverPhotoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Rasm topilmadi"));
        photo.setStatus(PhotoStatus.APPROVED);
        photo.setReviewedAt(LocalDateTime.now());
        photo.setReviewedBy(admin);
        driverPhotoRepository.save(photo);
        return Map.of("message", "Rasm tasdiqlandi");
    }

    /** Rasmni rad etish */
    @Transactional
    public Map<String, Object> rejectPhoto(Long photoId, String reason, User admin) {
        DriverPhoto photo = driverPhotoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Rasm topilmadi"));
        photo.setStatus(PhotoStatus.REJECTED);
        photo.setRejectReason(reason);
        photo.setReviewedAt(LocalDateTime.now());
        photo.setReviewedBy(admin);
        driverPhotoRepository.save(photo);
        return Map.of("message", "Rasm rad etildi");
    }

    /**
     * Rasmni o'chirish (ADMIN) — driver-app'dagi DRIVER_FACE/SELFIE immutability qulfini chetlab
     * o'tadi (qulf faqat haydovchi tomoni uchun; admin har doim o'chira oladi). Sabab majburiy
     * (RejectRequest orqali @Valid tekshiriladi) — audit uchun log'ga yoziladi (bu loyihada har
     * qanday admin amali xuddi shunday log qilinadi, alohida audit jadvali yo'q).
     */
    public Map<String, Object> deletePhoto(Long photoId, String reason, User admin) {
        DriverPhoto deleted = photoService.adminDeletePhoto(photoId);
        log.info("[ADMIN][PHOTO_DELETE] driverId={}, type={}, adminId={}, adminName={}, reason={}",
                deleted.getDriver() != null ? deleted.getDriver().getId() : null,
                deleted.getPhotoType(), admin.getId(), admin.getName(), reason);
        return Map.of("deleted", true, "type", deleted.getPhotoType().name());
    }

    /** Broadcast xabar yuborish (target=ALL/ACTIVE/OFFLINE). Eski API — driverId yo'q.
     *  Yangi DRIVER target uchun {@link #sendBroadcast(String, String, String, Long, User)} ishlatilsin. */
    @Transactional
    public Map<String, Object> sendBroadcast(String title, String content, String target, User admin) {
        return sendBroadcast(title, content, target, null, admin);
    }

    /**
     * Band 7 — Broadcast xabar yuborish. target=SPECIFIC (yoki "DRIVER" → SPECIFIC) va driverId
     * berilgan bo'lsa, FAQAT shu haydovchiga jo'natiladi (entity'da targetDriverIds tarkibida
     * 1 ta id, WS yo'li /topic/broadcast/{driverId}). Aks holda mavjud /topic/broadcast (global)
     * yo'li bilan jo'natiladi — eski xulq saqlanadi.
     *
     * Entity BroadcastMessage.target column (length=20) hujjatlangan qiymatlar: ALL | ACTIVE |
     * OFFLINE | SPECIFIC. targetDriverIds tarkibi V28__create_broadcast_targets.sql migration
     * orqali broadcast_targets jadvalida (allaqachon mavjud).
     *
     * Driver app /topic/broadcast/{driverId} ga obuna bo'lishi kerak — OTA orqali (websocket.ts).
     * Bu obuna driver primary key (drivers PK) asosida; mavjud /topic/driver/{driverId}
     * (TripNotificationHelper) bilan moslangan.
     */
    @Transactional
    public Map<String, Object> sendBroadcast(String title, String content, String target,
            Long driverId, User admin) {
        String normalizedTarget = (target != null && !target.isBlank()) ? target.trim().toUpperCase() : "ALL";
        // "DRIVER" qabul qilamiz lekin entity'da hujjatlangan SPECIFIC qiymatiga normalizatsiya.
        if ("DRIVER".equals(normalizedTarget)) normalizedTarget = "SPECIFIC";
        boolean singleDriver = "SPECIFIC".equals(normalizedTarget);
        if (singleDriver && driverId == null) {
            throw new RuntimeException("target=SPECIFIC uchun driverId majburiy");
        }
        Driver targetDriver = null;
        if (singleDriver) {
            targetDriver = driverRepository.findById(driverId)
                    .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi: id=" + driverId));
        }

        BroadcastMessage msg = new BroadcastMessage();
        msg.setTitle(title);
        msg.setContent(content);
        msg.setTarget(normalizedTarget);
        if (singleDriver) {
            msg.setTargetDriverIds(java.util.List.of(driverId));
        }
        msg.setSentBy(admin);
        broadcastMessageRepository.save(msg);

        Map<String, Object> wsPayload = new java.util.HashMap<>();
        wsPayload.put("title", title != null ? title : "");
        wsPayload.put("content", content);
        wsPayload.put("sentAt", msg.getSentAt().toString());
        if (singleDriver) {
            wsPayload.put("targetedDriverId", driverId);
            messagingTemplate.convertAndSend("/topic/broadcast/" + driverId, wsPayload);
        } else {
            messagingTemplate.convertAndSend("/topic/broadcast", wsPayload);
        }

        java.util.LinkedHashMap<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("message", "Xabar yuborildi");
        resp.put("id", msg.getId());
        resp.put("target", msg.getTarget());
        if (targetDriver != null) {
            resp.put("driverCode", targetDriver.getDriverCode());
            resp.put("driverName", targetDriver.getUser() != null ? targetDriver.getUser().getName() : null);
        }
        return resp;
    }

    /** Barcha buyurtmalar (source filter: null = barchasi) */
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> getAllTrips(Pageable pageable, String sourceFilter) {
        Page<com.taxi.backend.model.Trip> page = (sourceFilter != null && !sourceFilter.isBlank())
                ? tripRepository.findBySource(sourceFilter, pageable)
                : tripRepository.findAll(pageable);
        return page.map(t -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", t.getId());
            
            try {
                m.put("passengerId", t.getPassenger() != null ? t.getPassenger().getId() : null);
                m.put("passengerName", t.getPassenger() != null ? t.getPassenger().getName() : "—");
            } catch (Exception e) {
                m.put("passengerId", null);
                m.put("passengerName", "Noma'lum");
            }

            try {
                m.put("driverName", t.getDriver() != null && t.getDriver().getUser() != null ? t.getDriver().getUser().getName() : "—");
            } catch (Exception e) {
                m.put("driverName", "Noma'lum");
            }

            m.put("fromAddress", t.getFromAddress());
            m.put("toAddress", t.getToAddress());
            m.put("status", t.getStatus().name());
            m.put("source", t.getSource());
            m.put("totalPrice", t.getTotalPrice() != null ? t.getTotalPrice() / 100 : 0);
            m.put("createdAt", t.getCreatedAt().toString());
            return m;
        });
    }

    /** Bitta buyurtma batafsil */
    @Transactional(readOnly = true)
    public Map<String, Object> getTripDetail(Long tripId) {
        Trip t = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));
        Map<String, Object> m = new HashMap<>();
        m.put("id", t.getId());
        m.put("status", t.getStatus().name());
        m.put("fromAddress", t.getFromAddress());
        m.put("toAddress", t.getToAddress());
        m.put("fromLat", t.getFromLat());
        m.put("fromLon", t.getFromLon());
        m.put("toLat", t.getToLat());
        m.put("toLon", t.getToLon());
        m.put("distanceKm", t.getDistanceKm());
        m.put("durationMin", t.getDurationMin());
        m.put("basePrice", t.getBasePrice() != null ? t.getBasePrice() / 100 : 0);
        m.put("extraPrice", t.getExtraPrice() != null ? t.getExtraPrice() / 100 : 0);
        m.put("totalPrice", t.getTotalPrice() != null ? t.getTotalPrice() / 100 : 0);
        m.put("waitingPrice", t.getWaitingPrice() != null ? t.getWaitingPrice() / 100 : 0);
        m.put("cancelReason", t.getCancelReason());
        m.put("source", t.getSource());
        m.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
        m.put("acceptedAt", t.getAcceptedAt() != null ? t.getAcceptedAt().toString() : null);
        m.put("startedAt", t.getStartedAt() != null ? t.getStartedAt().toString() : null);
        m.put("completedAt", t.getCompletedAt() != null ? t.getCompletedAt().toString() : null);
        m.put("waitingStartedAt", t.getWaitingStartedAt() != null ? t.getWaitingStartedAt().toString() : null);
        m.put("waitingEndedAt", t.getWaitingEndedAt() != null ? t.getWaitingEndedAt().toString() : null);
        // Yo'lovchi
        try {
            m.put("passengerId", t.getPassenger() != null ? t.getPassenger().getId() : null);
            m.put("passengerName", t.getPassenger() != null ? t.getPassenger().getName() : "—");
            m.put("passengerPhone", t.getPassenger() != null ? t.getPassenger().getPhone() : "—");
        } catch (Exception e) {
            m.put("passengerId", null); m.put("passengerName", "Noma'lum"); m.put("passengerPhone", "—");
        }
        // Haydovchi
        try {
            if (t.getDriver() != null) {
                m.put("driverId", t.getDriver().getId());
                m.put("driverName", t.getDriver().getUser() != null ? t.getDriver().getUser().getName() : "—");
                m.put("driverPhone", t.getDriver().getUser() != null ? t.getDriver().getUser().getPhone() : "—");
                m.put("carModel", t.getDriver().getCarModel());
                m.put("carNumber", t.getDriver().getCarNumber());
                m.put("driverRating", t.getDriver().getRating());
            } else {
                m.put("driverId", null); m.put("driverName", "—"); m.put("driverPhone", "—");
                m.put("carModel", null); m.put("carNumber", null); m.put("driverRating", null);
            }
        } catch (Exception e) {
            m.put("driverId", null); m.put("driverName", "Noma'lum"); m.put("driverPhone", "—");
        }
        // Tarif
        try {
            m.put("tariffName", t.getTariff() != null ? t.getTariff().getName() : null);
        } catch (Exception e) { m.put("tariffName", null); }
        // Yo'lovchi bahosi (rating)
        try {
            ratingRepository.findByTripId(tripId).ifPresentOrElse(r -> {
                m.put("ratingScore", r.getScore());
                m.put("ratingComment", r.getComment());
                m.put("ratingDate", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
            }, () -> {
                m.put("ratingScore", null);
                m.put("ratingComment", null);
                m.put("ratingDate", null);
            });
        } catch (Exception e) {
            m.put("ratingScore", null); m.put("ratingComment", null); m.put("ratingDate", null);
        }
        return m;
    }

    /** Trip chat tarixini admin uchun olish (Redis + in-memory) */
    public List<Map<String, Object>> getTripChat(Long tripId) {
        return chatService.getMessages(tripId, 200);
    }

    /** Admin — istalgan faol buyurtmani bekor qilish */
    @Transactional
    public Map<String, Object> adminCancelTrip(Long tripId, String reason) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));

        List<TripStatus> cancellable = List.of(
                TripStatus.SEARCHING, TripStatus.ACCEPTED, TripStatus.DRIVER_ARRIVED, TripStatus.STARTED);
        if (!cancellable.contains(trip.getStatus())) {
            throw new RuntimeException("Bu statusdagi buyurtmani bekor qilib bo'lmaydi: " + trip.getStatus().name());
        }

        if (trip.getDriver() != null) {
            Driver driver = trip.getDriver();
            driver.setOnline(true);
            driverRepository.save(driver);
        }

        trip.setStatus(TripStatus.CANCELLED_BY_ADMIN);
        trip.setCancelReason(reason.trim());
        tripRepository.save(trip);
        if (notificationHelper != null) notificationHelper.cancelOpenOffer(tripId);

        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("tripId", tripId);
        result.put("status", "CANCELLED_BY_ADMIN");
        result.put("cancelReason", reason.trim());
        result.put("message", "Buyurtma muvaffaqiyatli bekor qilindi");
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Band 5 — Admin order management (per-order edits + reassign by driver ID)
    // ─────────────────────────────────────────────────────────────────────────
    //
    // All three operations are RESTRICTED to SEARCHING status (driver not yet
    // accepted). After a driver has accepted, the trip is a contract — admin
    // can still cancel via adminCancelTrip(), but not change tariff/addresses
    // or hand it to a different driver mid-flight.
    //
    // Reassign reuses the EXISTING data-only dispatch path
    // (asyncNotifier.pushDriverAsync -> PushNotificationService.notifyDriver
    // with type=ORDER_PUSH). It does NOT create a new push channel and does
    // NOT touch the 5 FSI Kotlin files or the order-alert chain.

    /** Admin — SEARCHING buyurtmaga yangi tarif belgilash. basePrice yangi tarifdan olinadi. */
    @Transactional
    public Map<String, Object> adminChangeTripTariff(Long tripId, Long tariffId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));
        if (trip.getStatus() != TripStatus.SEARCHING) {
            throw new RuntimeException("Tarifni faqat SEARCHING (haydovchi qabul qilmagan) statusda almashtirish mumkin: " + trip.getStatus().name());
        }
        com.taxi.backend.model.Tariff tariff = tariffRepository.findById(tariffId)
                .orElseThrow(() -> new RuntimeException("Tarif topilmadi: " + tariffId));

        Long oldBasePrice = trip.getBasePrice();
        trip.setTariff(tariff);
        // basePrice yangi tarifdan; totalPrice DOKAZANMAYDI — masofa o'zgarmagan, lekin
        // admin yangi tarifda re-create qilishni hohlasa: alohida endpoint orqali yoki
        // qayta yaratish. Hozircha basePrice deltasini totalPrice'ga ham qo'shamiz.
        Long newBasePrice = tariff.getBasePrice() != null ? tariff.getBasePrice() : 0L;
        trip.setBasePrice(newBasePrice);
        // totalPrice qayta hisobi: eski totalga (yangi basePrice - eski basePrice) qo'shamiz.
        // (extraPrice + waiting + distance/duration komponentlari saqlanadi).
        if (trip.getTotalPrice() != null && oldBasePrice != null) {
            long delta = newBasePrice - oldBasePrice;
            trip.setTotalPrice(Math.max(0L, trip.getTotalPrice() + delta));
        } else if (trip.getTotalPrice() == null) {
            trip.setTotalPrice(newBasePrice);
        }
        tripRepository.save(trip);

        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("tripId", tripId);
        result.put("tariffId", tariff.getId());
        result.put("tariffName", tariff.getName());
        result.put("basePrice", trip.getBasePrice());
        result.put("totalPrice", trip.getTotalPrice());
        result.put("message", "Tarif almashtirildi");
        return result;
    }

    /** Admin — SEARCHING buyurtmaning A (olib ketish) va B (manzil) ma'lumotlarini tahrirlash. */
    @Transactional
    public Map<String, Object> adminEditTripAddresses(Long tripId,
            String fromAddress, Double fromLat, Double fromLon,
            String toAddress, Double toLat, Double toLon) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));
        if (trip.getStatus() != TripStatus.SEARCHING) {
            throw new RuntimeException("Manzillarni faqat SEARCHING statusda tahrirlash mumkin: " + trip.getStatus().name());
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new RuntimeException("Olib ketish manzili bo'sh bo'lmasligi kerak");
        }
        if (fromLat == null || fromLon == null) {
            throw new RuntimeException("Olib ketish koordinatalari (lat/lon) majburiy");
        }
        trip.setFromAddress(fromAddress.trim());
        trip.setFromLat(fromLat);
        trip.setFromLon(fromLon);
        if (toAddress != null) trip.setToAddress(toAddress.trim().isEmpty() ? null : toAddress.trim());
        if (toLat != null) trip.setToLat(toLat);
        if (toLon != null) trip.setToLon(toLon);
        tripRepository.save(trip);

        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("tripId", tripId);
        result.put("fromAddress", trip.getFromAddress());
        result.put("fromLat", trip.getFromLat());
        result.put("fromLon", trip.getFromLon());
        result.put("toAddress", trip.getToAddress());
        result.put("toLat", trip.getToLat());
        result.put("toLon", trip.getToLon());
        result.put("message", "Manzillar yangilandi");
        return result;
    }

    /**
     * Admin — SEARCHING buyurtmani aniq haydovchiga yo'naltirish.
     *
     * Push xabari MAVJUD data-only dispatch yo'lidan o'tadi (asyncNotifier.pushDriverAsync ->
     * PushNotificationService.notifyDriver, type=ORDER_PUSH). Yangi push yo'li yaratilmaydi,
     * 5 FSI Kotlin fayli + order-alert zanjiri tegmaydi.
     *
     * Trip statusi SEARCHING'da qoladi — driver acceptTrip orqali qabul qilsa, mavjud
     * optimistic lock yo'li bilan tayinlanadi. Bu admin BIRTA haydovchini ko'zga qaratish vositasi,
     * majburiy tayinlash emas — drayver baribir buyurtmani rad qilishi mumkin.
     */
    @Transactional
    public Map<String, Object> adminReassignTripToDriver(Long tripId, Long driverId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Buyurtma topilmadi"));
        if (trip.getStatus() != TripStatus.SEARCHING) {
            throw new RuntimeException("Buyurtmani faqat SEARCHING statusda boshqa haydovchiga yo'naltirish mumkin: " + trip.getStatus().name());
        }
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi: id=" + driverId));
        if (driver.getStatus() != DriverStatus.ACTIVE) {
            throw new RuntimeException("Haydovchi ACTIVE emas (status=" + driver.getStatus().name() + ")");
        }

        if (notificationHelper == null) {
            throw new IllegalStateException("Sequential offer lifecycle is required for reassignment");
        }
        if (notificationHelper != null) {
            com.taxi.backend.model.TripDriverOffer offer = notificationHelper
                    .notifySpecificDriver(tripId, driverId)
                    .orElseThrow(() -> new IllegalStateException("Haydovchi uchun taklif yaratilmadi"));
            java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("tripId", tripId);
            result.put("driverId", driverId);
            result.put("driverCode", driver.getDriverCode());
            result.put("driverName", driver.getUser() != null ? driver.getUser().getName() : null);
            result.put("status", trip.getStatus().name());
            result.put("offerExpiresAt", offer.getExpiresAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            result.put("message", "Buyurtma haydovchiga yuborildi");
            return result;
        }

        // Mavjud TripNotificationHelper.sendNewOrderNotification ichidagi payload bilan AYNAN bir xil
        // shaklda quramiz. Bu zarur — driver app'ning IncomingOrderModal'i va native TezgoMessagingService
        // ushbu maydonlarni kutadi. Mavjud helper'ga tegmaymiz (order-alert no-touch).
        long offerExpiresAt = System.currentTimeMillis() + 15_000L; // 15s offer (TripNotificationHelper OFFER_TTL_MS bilan moslangan)
        long priceSom = (trip.getTotalPrice() != null ? trip.getTotalPrice() : 0L) / 100;

        // WebSocket xabar — driver app foreground'da bo'lsa IncomingOrderModal'ni ochish
        Map<String, Object> wsMsg = new java.util.HashMap<>();
        wsMsg.put("type", "NEW_ORDER");
        wsMsg.put("tripId", trip.getId());
        wsMsg.put("fromAddress", trip.getFromAddress());
        wsMsg.put("toAddress", trip.getToAddress() != null ? trip.getToAddress() : "—");
        wsMsg.put("price", priceSom);
        wsMsg.put("offerExpiresAt", offerExpiresAt);
        log.error("[SEQUENTIAL_DISPATCH] disabled legacy reassign WebSocket branch reached for tripId={}", tripId);

        // FCM data-only push — app yopiq/fon/qulflangan holatda native FSI ochish
        Map<String, Object> pushData = new java.util.HashMap<>();
        pushData.put("tripId", trip.getId());
        pushData.put("type", "ORDER_PUSH");
        pushData.put("event", "ORDER_PUSH");
        pushData.put("fromAddress", trip.getFromAddress() != null ? trip.getFromAddress() : "manzilsiz");
        pushData.put("toAddress", trip.getToAddress() != null ? trip.getToAddress() : "manzilsiz");
        pushData.put("price", priceSom);
        pushData.put("fromLat", trip.getFromLat() != null ? trip.getFromLat() : 0.0);
        pushData.put("fromLon", trip.getFromLon() != null ? trip.getFromLon() : 0.0);
        pushData.put("toLat", trip.getToLat() != null ? trip.getToLat() : 0.0);
        pushData.put("toLon", trip.getToLon() != null ? trip.getToLon() : 0.0);
        try {
            if (trip.getTariff() != null) {
                pushData.put("tariffName", trip.getTariff().getName());
                Long base = trip.getTariff().getBasePrice();
                pushData.put("calloutFee", base != null ? base : 0L);
            }
        } catch (Exception ignored) { /* lazy load — silent fallback */ }
        pushData.put("offerExpiresAt", offerExpiresAt);
        // title/body null — data-only kafolatlanadi. PushNotificationService isOrder()=true
        // ekanini ichida tekshiradi va sof data-only payload yuboradi.
        log.error("[SEQUENTIAL_DISPATCH] disabled legacy reassign push branch reached for tripId={}", tripId);

        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("tripId", tripId);
        result.put("driverId", driverId);
        result.put("driverCode", driver.getDriverCode());
        result.put("driverName", driver.getUser() != null ? driver.getUser().getName() : null);
        result.put("status", trip.getStatus().name());
        result.put("offerExpiresAt", offerExpiresAt);
        result.put("message", "Buyurtma haydovchiga yuborildi");
        return result;
    }

    /** A2 — Admin haydovchiga tarif grant beradi (mashina defaultidan tashqari). granted=null/bo'sh → tozalaydi.
     *  Effektiv eligibility = eligibleTariffs(carModel) ∪ grants. STANDART har doim default (grantda saqlanmaydi). */
    public Map<String, Object> setDriverTariffGrants(Long driverId, java.util.List<String> granted) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));

        String grants = null;
        if (granted != null && !granted.isEmpty()) {
            java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
            for (String g : granted) {
                if (g == null) continue;
                String n = g.trim().toUpperCase();
                if (!n.isBlank() && !"STANDART".equals(n)) set.add(n);
            }
            grants = set.isEmpty() ? null : String.join(",", set);
        }
        driver.setTariffGrants(grants);
        driverRepository.save(driver);

        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("driverId", driverId);
        result.put("carModel", driver.getCarModel());
        result.put("tariffGrants", grants);
        result.put("effectiveEligible",
                new java.util.ArrayList<>(DriverTariffFilter.eligibleTariffs(driver.getCarModel(), grants)));
        return result;
    }

    /** A2 — Haydovchining joriy grant holati (read-only): car-default vs effektiv eligibility. */
    public Map<String, Object> getDriverTariffGrants(Long driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("driverId", driverId);
        result.put("carModel", driver.getCarModel());
        result.put("tariffGrants", driver.getTariffGrants());
        result.put("carDefaultEligible",
                new java.util.ArrayList<>(DriverTariffFilter.eligibleTariffs(driver.getCarModel(), null)));
        result.put("effectiveEligible",
                new java.util.ArrayList<>(DriverTariffFilter.eligibleTariffs(driver.getCarModel(), driver.getTariffGrants())));
        return result;
    }

    /** Barcha sharhlar (Ratings) */
    @Transactional(readOnly = true)
    public Page<RatingResponse> getAllRatings(Pageable pageable) {
        return ratingRepository.findAll(pageable).map(r -> {
            String passengerName;
            try { passengerName = r.getFromUser() != null ? r.getFromUser().getName() : "—"; }
            catch (Exception e) { passengerName = "Noma'lum"; }
            String driverName;
            try {
                Driver d = r.getToDriver();
                driverName = (d != null && d.getUser() != null) ? d.getUser().getName() : "—";
            } catch (Exception e) { driverName = "Noma'lum"; }
            Long tripId;
            try { tripId = r.getTrip() != null ? r.getTrip().getId() : null; }
            catch (Exception e) { tripId = null; }
            return new RatingResponse(r.getId(), r.getScore(), r.getComment(),
                    r.getCreatedAt().toString(), passengerName, driverName, tripId);
        });
    }

    /** Yo'lovchilar ro'yxati — faqat PASSENGER roli */
    @Transactional(readOnly = true)
    public Page<PassengerListResponse> getPassengers(Pageable pageable) {
        return userRepository.findByRole(com.taxi.backend.enums.Role.PASSENGER, pageable).map(u -> {
            long trips = tripRepository.countByPassengerId(u.getId());
            Long spent = tripRepository.sumSpentByPassenger(u.getId());
            return new PassengerListResponse(u.getId(), u.getName(), u.getPhone(),
                    u.isActive(), u.getRole() != null ? u.getRole().name() : "",
                    trips, spent != null ? spent / 100 : 0);
        });
    }

    /** Yo'lovchini o'chirish — barcha bog'liq ma'lumotlar bilan */
    @Transactional
    public void deletePassenger(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Foydalanuvchi topilmadi"));

        // 1. Trip'lardagi passenger FK → null
        userRepository.nullifyPassengerInTrips(userId);

        // 2. Rating'lardagi fromUser FK → null
        userRepository.nullifyFromUserInRatings(userId);

        // 3. User o'chirish
        userRepository.deleteById(userId);
    }

    /** Yo'lovchini bloklash */
    @Transactional
    public Map<String, Object> blockPassenger(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Foydalanuvchi topilmadi"));
        user.setActive(false);
        userRepository.save(user);
        return Map.of("message", "Foydalanuvchi bloklandi");
    }

    /** Liniyada (ACTIVE + online) haydovchilar soni */
    public long getLiniyadaCount() {
        return driverRepository.countByIsOnlineTrueAndStatusACTIVE();
    }

    /** Online haydovchilar joylashuvi (admin xaritasi) */
    @Transactional(readOnly = true)
    public List<OnlineDriverResponse> getOnlineDriversForMap() {
        return driverRepository.findActiveOnlineDrivers().stream().map(d -> {
            String name, phone;
            try {
                name = d.getUser() != null ? d.getUser().getName() : "";
                phone = d.getUser() != null ? d.getUser().getPhone() : "";
            } catch (Exception e) {
                name = "";
                phone = "";
            }
            String avatarUrl;
            try {
                avatarUrl = driverPhotoRepository.findByDriverIdAndPhotoType(
                    d.getId(), com.taxi.backend.enums.PhotoType.DRIVER_FACE
                ).map(photo -> photo.getPhotoUrl()).orElse(null);
            } catch (Exception e) {
                avatarUrl = null;
            }
            return new OnlineDriverResponse(d.getId(),
                    d.getLatitude() != null ? d.getLatitude() : 0.0,
                    d.getLongitude() != null ? d.getLongitude() : 0.0,
                    d.getCarModel(), d.getCarNumber(), d.getRating(), d.getCarColor(),
                    d.getStatus() != null ? d.getStatus().name() : "",
                    d.getTotalTrips(), name, phone, d.getDriverCode(), avatarUrl);
        }).collect(Collectors.toList());
    }

    /** Haydovchi kodi bo'yicha qidiruv */
    @Transactional(readOnly = true)
    public Map<String, Object> getDriverByCode(String code) {
        Driver driver = driverRepository.findByDriverCode(code.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi: " + code));
        Map<String, Object> m = new HashMap<>();
        m.put("id", driver.getId());
        m.put("driverCode", driver.getDriverCode());
        m.put("name", driver.getUser() != null ? driver.getUser().getName() : "");
        m.put("phone", driver.getUser() != null ? driver.getUser().getPhone() : "");
        m.put("status", driver.getStatus() != null ? driver.getStatus().name() : "");
        m.put("isOnline", driver.isOnline());
        m.put("rating", driver.getRating());
        m.put("totalTrips", driver.getTotalTrips());
        m.put("balance", driver.getBalance() / 100);
        m.put("carModel", driver.getCarModel());
        m.put("carNumber", driver.getCarNumber());
        m.put("carColor", driver.getCarColor());
        m.put("carYear", driver.getCarYear());
        return m;
    }

    /** Qidiruv — ism, telefon yoki driver_code bo'yicha */
    public Page<DriverListResponse> searchDrivers(String q, Pageable pageable) {
        return driverRepository.searchByNameOrPhoneOrCode(q, pageable).map(d ->
                new DriverListResponse(
                        d.getId(), d.getUser().getName(), d.getUser().getPhone(), d.getDriverCode(),
                        d.getCarModel(), d.getCarNumber(), d.getStatus().name(),
                        d.isOnline(), d.getRating(), d.getTotalTrips(), d.getBalance() / 100,
                        d.getCarColor(), d.getCarYear(), d.getPassportSeries(), d.getPassportNumber(),
                        d.getBirthDate(), d.getAddress(), d.getTechPassportNumber(), d.getAcceptedTariffs(),
                        d.getVerifiedAt() != null ? d.getVerifiedAt().toString() : null,
                        d.getActivityScore(), d.getLatitude(), d.getLongitude(),
                        d.getUser().getCreatedAt() != null ? d.getUser().getCreatedAt().toString() : null,
                        d.getUser().getAvatarUrl()));
    }

    /** Admin tomonidan haydovchi balansini to'ldirish */
    @Transactional
    public Map<String, Object> adminTopupBalance(Long driverId, Long amountUzs, String paymentMethod) {
        if (amountUzs == null || amountUzs <= 0) throw new RuntimeException("Miqdor musbat bo'lishi kerak");
        long amountTiyin;
        try {
            amountTiyin = Math.multiplyExact(amountUzs, 100L);
        } catch (ArithmeticException exception) {
            throw new RuntimeException("Miqdor juda katta", exception);
        }
        Driver driver = driverRepository.findByIdForUpdate(driverId)
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        if (driver.getBalance() == null) throw new IllegalStateException("Haydovchi balansi topilmadi");
        long before = driver.getBalance();
        long after = Math.addExact(before, amountTiyin);
        driver.setBalance(after);

        Transaction tx = new Transaction();
        tx.setDriver(driver);
        tx.setType(TransactionType.TOPUP);
        tx.setAmount(amountTiyin);
        tx.setBalanceBefore(before);
        tx.setBalanceAfter(after);
        tx.setDescription("Admin tomonidan to'ldirildi");
        tx.setPaymentMethod(paymentMethod);
        transactionRepository.save(tx);

        return Map.of(
                "driverId", driverId,
                "driverCode", driver.getDriverCode() != null ? driver.getDriverCode() : "",
                "addedUzs", amountUzs,
                "newBalanceUzs", after / 100,
                "message", "Balans muvaffaqiyatli to'ldirildi"
        );
    }

    /** Admin — haydovchi online/offline holatini o'zgartirish */
    @Transactional
    public Map<String, Object> setDriverOnlineStatus(Long driverId, boolean isOnline) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Haydovchi topilmadi"));
        if (!isOnline) {
            List<TripStatus> activeStatuses = List.of(
                    TripStatus.ACCEPTED, TripStatus.DRIVER_ARRIVED, TripStatus.STARTED);
            boolean hasActiveTrip = tripRepository
                    .findFirstByDriverIdAndStatusIn(driverId, activeStatuses).isPresent();
            if (hasActiveTrip) {
                throw new ConflictException("Haydovchi hozir faol safarda");
            }
        }
        driver.setOnline(isOnline);
        driverRepository.save(driver);
        return Map.of("id", driverId, "isOnline", isOnline);
    }

    /** Kengaytirilgan dashboard statistikasi: komissiya + admin to'ldirish aylanmasi */
    public Map<String, Object> getDashboardExtendedStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime epoch = LocalDateTime.of(2000, 1, 1, 0, 0);

        List<TransactionType> commTypes = List.of(
                TransactionType.COMMISSION, TransactionType.TAXOMETER_COMMISSION);

        // Komissiya — COMMISSION musbat, TAXOMETER_COMMISSION manfiy; ABS ishlatiladi
        long commToday = transactionRepository.sumAbsAmountByTypesAfter(commTypes, todayStart);
        long commMonth = transactionRepository.sumAbsAmountByTypesAfter(commTypes, monthStart);
        long commTotal = transactionRepository.sumAbsAmountByTypesAfter(commTypes, epoch);

        // Admin qo'l to'ldirish (paymentMethod IS NOT NULL)
        long topupAmtToday = transactionRepository.sumAdminTopupAfter(todayStart);
        long topupAmtMonth = transactionRepository.sumAdminTopupAfter(monthStart);
        long topupAmtTotal = transactionRepository.sumAdminTopupAfter(epoch);

        long topupDrvToday = transactionRepository.countDistinctDriversAdminTopupAfter(todayStart);
        long topupDrvMonth = transactionRepository.countDistinctDriversAdminTopupAfter(monthStart);
        long topupDrvTotal = transactionRepository.countDistinctDriversAdminTopupAfter(epoch);

        long cashToday = transactionRepository.sumAdminTopupByMethodAfter("CASH", todayStart);
        long cashMonth = transactionRepository.sumAdminTopupByMethodAfter("CASH", monthStart);
        long cashTotal = transactionRepository.sumAdminTopupByMethodAfter("CASH", epoch);

        long cardToday = transactionRepository.sumAdminTopupByMethodAfter("CARD", todayStart);
        long cardMonth = transactionRepository.sumAdminTopupByMethodAfter("CARD", monthStart);
        long cardTotal = transactionRepository.sumAdminTopupByMethodAfter("CARD", epoch);

        Map<String, Object> commission = new LinkedHashMap<>();
        commission.put("today", commToday / 100);
        commission.put("month", commMonth / 100);
        commission.put("total", commTotal / 100);

        Map<String, Object> topupToday = new LinkedHashMap<>();
        topupToday.put("amount", topupAmtToday / 100);
        topupToday.put("drivers", topupDrvToday);
        topupToday.put("cash", cashToday / 100);
        topupToday.put("card", cardToday / 100);

        Map<String, Object> topupMonth = new LinkedHashMap<>();
        topupMonth.put("amount", topupAmtMonth / 100);
        topupMonth.put("drivers", topupDrvMonth);
        topupMonth.put("cash", cashMonth / 100);
        topupMonth.put("card", cardMonth / 100);

        Map<String, Object> topupTotalMap = new LinkedHashMap<>();
        topupTotalMap.put("amount", topupAmtTotal / 100);
        topupTotalMap.put("drivers", topupDrvTotal);
        topupTotalMap.put("cash", cashTotal / 100);
        topupTotalMap.put("card", cardTotal / 100);

        Map<String, Object> topup = new LinkedHashMap<>();
        topup.put("today", topupToday);
        topup.put("month", topupMonth);
        topup.put("total", topupTotalMap);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("commission", commission);
        result.put("topup", topup);
        return result;
    }

    /** Moliyaviy hisobot (oxirgi N kun) — optimallashtirilgan (3 query, N*3 emas) */
    @Transactional(readOnly = true)
    public List<FinancialReportResponse> getFinancialReport(int days) {
        LocalDateTime periodStart = LocalDate.now().minusDays(days - 1).atStartOfDay();
        LocalDateTime periodEnd = LocalDateTime.now();

        // Bitta query bilan barcha COMPLETED triplarni olish
        List<Trip> completedTrips = tripRepository.findByStatusAndCreatedAtBetweenList(
                TripStatus.COMPLETED, periodStart, periodEnd);

        // Bitta query bilan barcha CANCELLED triplarni olish
        List<Trip> cancelledTrips = tripRepository.findByStatusInAndCreatedAtBetweenList(
                List.of(TripStatus.CANCELLED_BY_PASSENGER, TripStatus.CANCELLED_BY_DRIVER),
                periodStart, periodEnd);

        // Java'da kunlik aggregatsiya
        Map<LocalDate, long[]> dailyStats = new HashMap<>(); // [completedCount, revenue, cancelledCount]
        for (int i = days - 1; i >= 0; i--) {
            dailyStats.put(LocalDate.now().minusDays(i), new long[]{0, 0, 0});
        }

        for (Trip t : completedTrips) {
            LocalDate date = t.getCreatedAt().toLocalDate();
            long[] stats = dailyStats.get(date);
            if (stats != null) {
                stats[0]++;
                stats[1] += t.getTotalPrice() != null ? t.getTotalPrice() : 0;
            }
        }
        for (Trip t : cancelledTrips) {
            LocalDate date = t.getCreatedAt().toLocalDate();
            long[] stats = dailyStats.get(date);
            if (stats != null) {
                stats[2]++;
            }
        }

        List<FinancialReportResponse> report = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            long[] stats = dailyStats.get(date);
            report.add(new FinancialReportResponse(date.toString(),
                    stats != null ? stats[0] : 0,
                    stats != null ? stats[2] : 0,
                    stats != null ? stats[1] / 100 : 0));
        }
        return report;
    }

}
