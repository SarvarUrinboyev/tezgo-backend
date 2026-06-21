package com.taxi.backend.repository;

import com.taxi.backend.model.Tariff;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TariffRepository extends JpaRepository<Tariff, Long> {
    List<Tariff> findByIsActiveTrue();
    Optional<Tariff> findByName(String name);
}
