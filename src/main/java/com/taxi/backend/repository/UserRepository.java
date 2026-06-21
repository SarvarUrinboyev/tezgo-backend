package com.taxi.backend.repository;

import com.taxi.backend.model.User;
import com.taxi.backend.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhone(String phone);

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByPhone(String phone);

    List<User> findByRole(Role role);

    Page<User> findByRole(Role role, Pageable pageable);

    /** Referal kod bo'yicha topish (do'st kodni kiritganda) */
    Optional<User> findByReferralCode(String referralCode);

    /** Necha do'st shu kod bilan ro'yxatdan o'tgan */
    long countByReferredByCode(String referredByCode);

    /** Bonus berilgan (yakunlangan safarli) takliflar soni */
    long countByReferredByCodeAndReferralRewardedTrue(String referredByCode);

    /** Trip'lardagi passenger FK ni null qilish (haydovchini o'chirishda ham kerak) */
    @Modifying
    @Query("UPDATE Trip t SET t.passenger = null WHERE t.passenger.id = :userId")
    void nullifyPassengerInTrips(@Param("userId") Long userId);

    /** Foto reviewedBy FK ni null qilish */
    @Modifying
    @Query("UPDATE DriverPhoto p SET p.reviewedBy = null WHERE p.reviewedBy.id = :userId")
    void nullifyReviewedByInPhotos(@Param("userId") Long userId);

    /** Rating'lardagi fromUser FK ni null qilish */
    @Modifying
    @Query("UPDATE Rating r SET r.fromUser = null WHERE r.fromUser.id = :userId")
    void nullifyFromUserInRatings(@Param("userId") Long userId);
}
