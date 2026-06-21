package com.taxi.backend.repository;

import com.taxi.backend.model.DriverService;
import com.taxi.backend.enums.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DriverServiceRepository extends JpaRepository<DriverService, Long> {
    List<DriverService> findByDriverId(Long driverId);

    Optional<DriverService> findByDriverIdAndServiceType(Long driverId, ServiceType serviceType);
}
