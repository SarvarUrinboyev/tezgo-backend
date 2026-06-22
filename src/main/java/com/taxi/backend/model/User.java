package com.taxi.backend.model;

import com.taxi.backend.enums.Role;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 15)
    private String phone;

    @Column(length = 100)
    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    // Yo'lovchining o'rtacha reytingi (haydovchilar bahosidan) — drivers.rating ga o'xshash
    @Column(precision = 3, scale = 2)
    private BigDecimal rating = BigDecimal.valueOf(5.0);

    @Column(name = "rating_count")
    private Integer ratingCount = 0;

    // Bonus hamyon (tiyin) — referal bonuslari yig'iladi; online to'lov ulanganda sarflanadi
    @Column(name = "balance", nullable = false)
    private Long balance = 0L;

    // Referal: shaxsiy kod, do'st kiritgan kod, bonus berilganmi
    @Column(name = "referral_code", length = 12)
    private String referralCode;

    @Column(name = "referred_by_code", length = 12)
    private String referredByCode;

    @Column(name = "referral_rewarded", nullable = false)
    private boolean referralRewarded = false;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Constructors
    public User() {
    }

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public Integer getRatingCount() {
        return ratingCount;
    }

    public void setRatingCount(Integer ratingCount) {
        this.ratingCount = ratingCount;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Long getBalance() {
        return balance != null ? balance : 0L;
    }

    public void setBalance(Long balance) {
        this.balance = balance != null ? balance : 0L;
    }

    public String getReferralCode() {
        return referralCode;
    }

    public void setReferralCode(String referralCode) {
        this.referralCode = referralCode;
    }

    public String getReferredByCode() {
        return referredByCode;
    }

    public void setReferredByCode(String referredByCode) {
        this.referredByCode = referredByCode;
    }

    public boolean isReferralRewarded() {
        return referralRewarded;
    }

    public void setReferralRewarded(boolean referralRewarded) {
        this.referralRewarded = referralRewarded;
    }
}
