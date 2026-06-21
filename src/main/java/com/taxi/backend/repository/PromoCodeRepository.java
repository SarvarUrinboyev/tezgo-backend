package com.taxi.backend.repository;

import com.taxi.backend.model.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {

    Optional<PromoCode> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    /** Atomik increment — race condition himoyasi. Faqat limit tugamagan bo'lsa oshiradi. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
        "UPDATE PromoCode p SET p.usedCount = p.usedCount + 1 " +
        "WHERE p.id = :id AND p.usedCount < p.maxUses AND p.isActive = true")
    int incrementUsedCount(@org.springframework.data.repository.query.Param("id") Long id);
}
