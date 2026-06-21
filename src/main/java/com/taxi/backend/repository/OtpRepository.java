package com.taxi.backend.repository;

import com.taxi.backend.model.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<OtpCode, Long> {
    Optional<OtpCode> findTopByPhoneAndIsUsedFalseOrderByCreatedAtDesc(String phone);
    List<OtpCode> findTop50ByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);

    @Modifying
    @Transactional
    @Query("UPDATE OtpCode o SET o.isUsed = true WHERE o.phone = :phone")
    void invalidateAllByPhone(String phone);
}
