package com.taxi.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.taxi.backend.enums.DriverStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "drivers")
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "car_model", length = 100)
    private String carModel;

    @Column(name = "car_number", length = 20)
    private String carNumber;

    @Column(name = "car_color", length = 50)
    private String carColor;

    @Column(name = "car_year")
    private Integer carYear;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DriverStatus status = DriverStatus.PENDING;

    @Column(name = "is_online")
    private boolean isOnline = false;

    private Double latitude;
    private Double longitude;

    @Column(precision = 3, scale = 2)
    private BigDecimal rating = BigDecimal.valueOf(5.0);

    @Column(name = "total_trips")
    private Integer totalTrips = 0;

    // Aktivlik balli — matching scoring uchun. COMPLETED +1.0, "BOSHQA" bekor -0.2.
    // Chegarasiz, manfiy bo'lishi mumkin.
    @Column(name = "activity_score", nullable = false)
    private double activityScore = 0;

    // Balans tiyinlarda (20730 UZS = 2073000 tiyin)
    private Long balance = 0L;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    // Pasport ma'lumotlari — @JsonIgnore: API response da chiqmasligi kerak
    @JsonIgnore
    @Column(name = "passport_series", length = 4)
    private String passportSeries;

    @JsonIgnore
    @Column(name = "passport_number", length = 10, unique = true)
    private String passportNumber;

    @JsonIgnore
    @Column(name = "birth_date", length = 15)
    private String birthDate;

    @JsonIgnore
    @Column(name = "address", length = 200)
    private String address;

    // Texnik pasport — @JsonIgnore
    @JsonIgnore
    @Column(name = "tech_passport_number", length = 20, unique = true)
    private String techPassportNumber;

    @Column(name = "driver_code", length = 20, unique = true)
    private String driverCode;

    @Column(name = "accepted_tariffs")
    private String acceptedTariffs = "EKONOM,DAMAS,BIZNES";

    @Column(name = "push_token", length = 500)
    private String pushToken;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Haydovchi oxirgi marta "bo'sh" bo'lgan vaqt (online bo'lgan yoki safarni yakunlagan).
    // Matching tenglik holatida (epsilon ichida) eng erta bo'shagani g'olib.
    @Column(name = "free_since")
    private LocalDateTime freeSince;

    // Rad etishdan keyingi cooldown — shu vaqtgacha yangi buyurtma olmaydi.
    @Column(name = "order_cooldown_until")
    private LocalDateTime orderCooldownUntil;

    @Column(name = "allows_pets", nullable = false)
    private boolean allowsPets = false;

    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Driver() {
    }

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getCarModel() {
        return carModel;
    }

    public void setCarModel(String carModel) {
        this.carModel = carModel;
    }

    public String getCarNumber() {
        return carNumber;
    }

    public void setCarNumber(String carNumber) {
        this.carNumber = carNumber;
    }

    public String getCarColor() {
        return carColor;
    }

    public void setCarColor(String carColor) {
        this.carColor = carColor;
    }

    public Integer getCarYear() {
        return carYear;
    }

    public void setCarYear(Integer carYear) {
        this.carYear = carYear;
    }

    public DriverStatus getStatus() {
        return status;
    }

    public void setStatus(DriverStatus status) {
        this.status = status;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public Integer getTotalTrips() {
        return totalTrips;
    }

    public void setTotalTrips(Integer totalTrips) {
        this.totalTrips = totalTrips;
    }

    public double getActivityScore() {
        return activityScore;
    }

    public void setActivityScore(double activityScore) {
        this.activityScore = activityScore;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public String getPassportSeries() { return passportSeries; }
    public void setPassportSeries(String passportSeries) { this.passportSeries = passportSeries; }

    public String getPassportNumber() { return passportNumber; }
    public void setPassportNumber(String passportNumber) { this.passportNumber = passportNumber; }

    public String getBirthDate() { return birthDate; }
    public void setBirthDate(String birthDate) { this.birthDate = birthDate; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getTechPassportNumber() { return techPassportNumber; }
    public void setTechPassportNumber(String techPassportNumber) { this.techPassportNumber = techPassportNumber; }

    public String getDriverCode() { return driverCode; }
    public void setDriverCode(String driverCode) { this.driverCode = driverCode; }

    public String getAcceptedTariffs() { return acceptedTariffs; }
    public void setAcceptedTariffs(String acceptedTariffs) { this.acceptedTariffs = acceptedTariffs; }

    public String getPushToken() { return pushToken; }
    public void setPushToken(String pushToken) { this.pushToken = pushToken; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getFreeSince() { return freeSince; }
    public void setFreeSince(LocalDateTime freeSince) { this.freeSince = freeSince; }

    public LocalDateTime getOrderCooldownUntil() { return orderCooldownUntil; }
    public void setOrderCooldownUntil(LocalDateTime orderCooldownUntil) { this.orderCooldownUntil = orderCooldownUntil; }

    public boolean isAllowsPets() { return allowsPets; }
    public void setAllowsPets(boolean allowsPets) { this.allowsPets = allowsPets; }

    /** Rad etish cooldown'i hozir faolmi? (faol bo'lsa — yangi buyurtma olmaydi) */
    public boolean isInCooldown() {
        return orderCooldownUntil != null && orderCooldownUntil.isAfter(LocalDateTime.now());
    }
}
