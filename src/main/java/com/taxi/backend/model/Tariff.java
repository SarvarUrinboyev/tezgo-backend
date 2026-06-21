package com.taxi.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tariffs")
public class Tariff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name; // EKONOM, KOMFORT, BIZNES

    // Boshlang'ich narx (tiyinlarda)
    @Column(name = "base_price", nullable = false)
    private Long basePrice;

    // Har km uchun narx
    @Column(name = "price_per_km", nullable = false)
    private Long pricePerKm;

    // Har daqiqa uchun narx
    @Column(name = "price_per_min", nullable = false)
    private Long pricePerMin;

    // Minimal narx
    @Column(name = "min_price", nullable = false)
    private Long minPrice;

    @Column(name = "is_active")
    private boolean isActive = true;

    public Tariff() {
    }

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(Long basePrice) {
        this.basePrice = basePrice;
    }

    public Long getPricePerKm() {
        return pricePerKm;
    }

    public void setPricePerKm(Long pricePerKm) {
        this.pricePerKm = pricePerKm;
    }

    public Long getPricePerMin() {
        return pricePerMin;
    }

    public void setPricePerMin(Long pricePerMin) {
        this.pricePerMin = pricePerMin;
    }

    public Long getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(Long minPrice) {
        this.minPrice = minPrice;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}
