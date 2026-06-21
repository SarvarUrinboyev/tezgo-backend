package com.taxi.backend.repository;

import com.taxi.backend.model.Place;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {
    List<Place> findByIsActiveTrueOrderByNameAsc();
    Page<Place> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
