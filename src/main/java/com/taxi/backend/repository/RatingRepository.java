package com.taxi.backend.repository;

import com.taxi.backend.model.Rating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    boolean existsByTripId(Long tripId);
    boolean existsByTripIdAndDirection(Long tripId, String direction);
    Optional<Rating> findByTripId(Long tripId);
    List<Rating> findByToDriverId(Long driverId);
    List<Rating> findByToUserId(Long userId);
}
