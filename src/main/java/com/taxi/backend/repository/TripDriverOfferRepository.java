package com.taxi.backend.repository;

import com.taxi.backend.enums.TripDriverOfferStatus;
import com.taxi.backend.model.TripDriverOffer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TripDriverOfferRepository extends JpaRepository<TripDriverOffer, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM TripDriverOffer o WHERE o.id = :id")
    Optional<TripDriverOffer> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM TripDriverOffer o WHERE o.trip.id = :tripId "
            + "AND o.status IN :liveStatuses ORDER BY o.generation DESC")
    List<TripDriverOffer> findLiveByTripIdForUpdate(@Param("tripId") Long tripId,
                                                     @Param("liveStatuses") Collection<TripDriverOfferStatus> liveStatuses);

    @Query("SELECT o.driver.id FROM TripDriverOffer o WHERE o.trip.id = :tripId")
    List<Long> findAllOfferedDriverIdsByTripId(@Param("tripId") Long tripId);

    @Query("SELECT COALESCE(MAX(o.generation), 0) FROM TripDriverOffer o WHERE o.trip.id = :tripId")
    int findMaxGenerationByTripId(@Param("tripId") Long tripId);

    @Query("SELECT o.trip.id FROM TripDriverOffer o WHERE o.driver.id = :driverId "
            + "AND o.status IN :liveStatuses AND o.expiresAt > :now")
    List<Long> findLiveTripIdsByDriverId(@Param("driverId") Long driverId,
                                         @Param("liveStatuses") Collection<TripDriverOfferStatus> liveStatuses,
                                         @Param("now") LocalDateTime now);

    @Query("SELECT o.id FROM TripDriverOffer o WHERE o.status IN :liveStatuses AND o.expiresAt <= :now")
    List<Long> findExpiredLiveOfferIds(@Param("liveStatuses") Collection<TripDriverOfferStatus> liveStatuses,
                                       @Param("now") LocalDateTime now);

    @Query("SELECT o.id FROM TripDriverOffer o WHERE o.status = com.taxi.backend.enums.TripDriverOfferStatus.PENDING_DELIVERY "
            + "AND o.expiresAt > :now")
    List<Long> findPendingDeliveryOfferIds(@Param("now") LocalDateTime now);
}
