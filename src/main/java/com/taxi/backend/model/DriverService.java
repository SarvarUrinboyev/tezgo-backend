package com.taxi.backend.model;

import com.taxi.backend.enums.ServiceType;
import jakarta.persistence.*;

@Entity
@Table(name = "driver_services", uniqueConstraints = @UniqueConstraint(columnNames = { "driver_id", "service_type" }))
public class DriverService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 30)
    private ServiceType serviceType;

    @Column(name = "is_enabled")
    private boolean isEnabled = false;

    // Qo'shimcha narx (tiyinlarda)
    @Column(name = "extra_price")
    private Long extraPrice = 0L;

    public DriverService() {
    }

    public DriverService(Driver driver, ServiceType serviceType, Long extraPrice) {
        this.driver = driver;
        this.serviceType = serviceType;
        this.extraPrice = extraPrice;
        this.isEnabled = false;
    }

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public Long getExtraPrice() {
        return extraPrice;
    }

    public void setExtraPrice(Long extraPrice) {
        this.extraPrice = extraPrice;
    }
}
