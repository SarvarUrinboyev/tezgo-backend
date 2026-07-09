package com.taxi.backend.repository;

import com.taxi.backend.model.Banner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    @Query("SELECT b FROM Banner b WHERE b.active = true " +
           "AND (b.startsAt IS NULL OR b.startsAt <= :now) " +
           "AND (b.endsAt IS NULL OR b.endsAt >= :now) " +
           "ORDER BY b.sort ASC")
    List<Banner> findActiveInRange(@Param("now") LocalDateTime now);
}
