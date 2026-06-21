package com.taxi.backend.model;

import com.taxi.backend.enums.TripStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic Lock — concurrent access himoyasi.
     * Ikki haydovchi bir vaqtda bitta tripni qabul qilishda
     * ikkinchisi OptimisticLockException oladi (DB connection band bo'lmaydi).
     *
     * Pessimistic Lock (FOR UPDATE) → DB connection pool tiqilinchi
     * Optimistic Lock (@Version) → xato qaytaradi, retry mumkin
     */
    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id")
    private User passenger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tariff_id")
    private Tariff tariff;

    // Boshlang'ich nuqta
    @Column(name = "from_lat", nullable = false)
    private Double fromLat;

    @Column(name = "from_lon", nullable = false)
    private Double fromLon;

    @Column(name = "from_address", nullable = false)
    private String fromAddress;

    // Yakuniy nuqta
    @Column(name = "to_lat")
    private Double toLat;

    @Column(name = "to_lon")
    private Double toLon;

    @Column(name = "to_address")
    private String toAddress;

    // Masofa va vaqt
    @Column(name = "distance_km", precision = 8, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "duration_min")
    private Integer durationMin;

    // Narxlar (tiyinlarda)
    @Column(name = "base_price")
    private Long basePrice;

    @Column(name = "extra_price")
    private Long extraPrice = 0L;

    @Column(name = "total_price")
    private Long totalPrice;

    // Fare-bidding: yo'lovchi taklif qilgan narx (tiyin). NULL = metered narx.
    @Column(name = "offered_fare")
    private Long offeredFare;

    // Rejalashtirilgan buyurtma: mijoz keyingi vaqtga taksi chaqiradi. NULL = darhol buyurtma.
    @Column(name = "scheduled_at")
    private java.time.LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private TripStatus status = TripStatus.SEARCHING;

    @Column(name = "cancel_reason")
    private String cancelReason;

    // Admin buyurtmasi yoki app
    @Column(length = 20)
    private String source = "APP";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    // Haydovchi yetib kelgan vaqt (DRIVER_ARRIVED) — pullik kutish anchor'i
    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // Kutish (waiting) — haydovchi yetib kelgandan keyin yo'lovchini kutish
    @Column(name = "waiting_started_at")
    private LocalDateTime waitingStartedAt;

    @Column(name = "waiting_ended_at")
    private LocalDateTime waitingEndedAt;

    @Column(name = "waiting_price")
    private Long waitingPrice = 0L;

    // Safar davomidagi kutish (trip waiting) — yo'lovchi to'xtab turish so'raganda
    @Column(name = "trip_waiting_started_at")
    private LocalDateTime tripWaitingStartedAt;

    @Column(name = "trip_waiting_ended_at")
    private LocalDateTime tripWaitingEndedAt;

    @Column(name = "trip_waiting_price")
    private Long tripWaitingPrice = 0L;

    // Umumiy taxta — 1 daqiqa SEARCHING bo'lsa barcha haydovchilarga broadcast
    @Column(name = "broadcast_at")
    private LocalDateTime broadcastAt;

    // To'g'ridan-to'g'ri matching fazasida xabardor qilingan haydovchi IDlari (vergul bilan)
    @Column(name = "notified_driver_ids", length = 1000)
    private String notifiedDriverIds;

    // Haydovchilar bekor qilish soni — 3 ga yetganda trip yakuniy CANCELLED
    @Column(name = "cancel_count", nullable = false)
    private int cancelCount = 0;

    // Bekor qilgan (qayta yuborishdan chiqarilgan) haydovchi IDlari (vergul bilan)
    @Column(name = "excluded_driver_ids", length = 1000)
    private String excludedDriverIds;

    // Tanlangan qo'shimcha xizmatlar — ServiceType kodlari (vergul bilan). Narxi extra_price da.
    @Column(name = "selected_services", length = 255)
    private String selectedServices;

    public Trip() {
    }

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public User getPassenger() {
        return passenger;
    }

    public void setPassenger(User passenger) {
        this.passenger = passenger;
    }

    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public Tariff getTariff() {
        return tariff;
    }

    public void setTariff(Tariff tariff) {
        this.tariff = tariff;
    }

    public Double getFromLat() {
        return fromLat;
    }

    public void setFromLat(Double fromLat) {
        this.fromLat = fromLat;
    }

    public Double getFromLon() {
        return fromLon;
    }

    public void setFromLon(Double fromLon) {
        this.fromLon = fromLon;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public Double getToLat() {
        return toLat;
    }

    public void setToLat(Double toLat) {
        this.toLat = toLat;
    }

    public Double getToLon() {
        return toLon;
    }

    public void setToLon(Double toLon) {
        this.toLon = toLon;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(BigDecimal distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Integer getDurationMin() {
        return durationMin;
    }

    public void setDurationMin(Integer durationMin) {
        this.durationMin = durationMin;
    }

    public Long getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(Long basePrice) {
        this.basePrice = basePrice;
    }

    public Long getExtraPrice() {
        return extraPrice;
    }

    public void setExtraPrice(Long extraPrice) {
        this.extraPrice = extraPrice;
    }

    public Long getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(Long totalPrice) {
        this.totalPrice = totalPrice;
    }

    public Long getOfferedFare() {
        return offeredFare;
    }

    public void setOfferedFare(Long offeredFare) {
        this.offeredFare = offeredFare;
    }

    public TripStatus getStatus() {
        return status;
    }

    public void setStatus(TripStatus status) {
        this.status = status;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public LocalDateTime getArrivedAt() {
        return arrivedAt;
    }

    public void setArrivedAt(LocalDateTime arrivedAt) {
        this.arrivedAt = arrivedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getWaitingStartedAt() {
        return waitingStartedAt;
    }

    public void setWaitingStartedAt(LocalDateTime waitingStartedAt) {
        this.waitingStartedAt = waitingStartedAt;
    }

    public LocalDateTime getWaitingEndedAt() {
        return waitingEndedAt;
    }

    public void setWaitingEndedAt(LocalDateTime waitingEndedAt) {
        this.waitingEndedAt = waitingEndedAt;
    }

    public Long getWaitingPrice() {
        return waitingPrice;
    }

    public void setWaitingPrice(Long waitingPrice) {
        this.waitingPrice = waitingPrice;
    }

    public LocalDateTime getTripWaitingStartedAt() {
        return tripWaitingStartedAt;
    }

    public void setTripWaitingStartedAt(LocalDateTime tripWaitingStartedAt) {
        this.tripWaitingStartedAt = tripWaitingStartedAt;
    }

    public LocalDateTime getTripWaitingEndedAt() {
        return tripWaitingEndedAt;
    }

    public void setTripWaitingEndedAt(LocalDateTime tripWaitingEndedAt) {
        this.tripWaitingEndedAt = tripWaitingEndedAt;
    }

    public Long getTripWaitingPrice() {
        return tripWaitingPrice;
    }

    public void setTripWaitingPrice(Long tripWaitingPrice) {
        this.tripWaitingPrice = tripWaitingPrice;
    }

    public LocalDateTime getBroadcastAt() {
        return broadcastAt;
    }

    public void setBroadcastAt(LocalDateTime broadcastAt) {
        this.broadcastAt = broadcastAt;
    }

    public String getNotifiedDriverIds() {
        return notifiedDriverIds;
    }

    public void setNotifiedDriverIds(String notifiedDriverIds) {
        this.notifiedDriverIds = notifiedDriverIds;
    }

    public int getCancelCount() {
        return cancelCount;
    }

    public void setCancelCount(int cancelCount) {
        this.cancelCount = cancelCount;
    }

    public String getExcludedDriverIds() {
        return excludedDriverIds;
    }

    public void setExcludedDriverIds(String excludedDriverIds) {
        this.excludedDriverIds = excludedDriverIds;
    }

    public String getSelectedServices() {
        return selectedServices;
    }

    public void setSelectedServices(String selectedServices) {
        this.selectedServices = selectedServices;
    }

    public java.time.LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(java.time.LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }
}
