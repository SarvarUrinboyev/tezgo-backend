package com.taxi.backend.repository;

import com.taxi.backend.model.DriverPhoto;
import com.taxi.backend.enums.PhotoStatus;
import com.taxi.backend.enums.PhotoType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DriverPhotoRepository extends JpaRepository<DriverPhoto, Long> {
    List<DriverPhoto> findByDriverId(Long driverId);

    Optional<DriverPhoto> findByDriverIdAndPhotoType(Long driverId, PhotoType photoType);

    List<DriverPhoto> findByDriverIdAndStatus(Long driverId, PhotoStatus status);

    long countByDriverIdAndStatus(Long driverId, PhotoStatus status);
}
