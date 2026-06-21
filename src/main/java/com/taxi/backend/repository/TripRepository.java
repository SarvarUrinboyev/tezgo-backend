package com.taxi.backend.repository;

import com.taxi.backend.model.Trip;
import com.taxi.backend.enums.TripStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    /**
     * Pessimistic Lock — race condition himoyasi.
     * acceptTrip() da ikki haydovchi bir vaqtda bitta buyurtmani qabul qilishini oldini oladi.
     * SELECT ... FOR UPDATE — ikkinchi tranzaksiya birinchisi tugaguncha kutadi.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Trip t WHERE t.id = :id")
    Optional<Trip> findByIdForUpdate(@Param("id") Long id);

    // ─── Haydovchi ─────────────────────────────────────────────────────────

    /** JOIN FETCH — N+1 muammoni oldini oladi */
    @Query("SELECT t FROM Trip t " +
           "LEFT JOIN FETCH t.passenger " +
           "LEFT JOIN FETCH t.driver d LEFT JOIN FETCH d.user " +
           "LEFT JOIN FETCH t.tariff " +
           "WHERE t.driver.id = :driverId ORDER BY t.createdAt DESC")
    Page<Trip> findByDriverIdWithRelations(@Param("driverId") Long driverId, Pageable pageable);

    Page<Trip> findByDriverIdOrderByCreatedAtDesc(Long driverId, Pageable pageable);

    Optional<Trip> findByDriverIdAndStatusNotIn(Long driverId, List<TripStatus> statuses);

    /** Haydovchining faol buyurtmasi */
    Optional<Trip> findFirstByDriverIdAndStatusIn(Long driverId, List<TripStatus> statuses);

    /** Haydovchida shu statuslardan birida trip bormi? (band-haydovchi gate'i uchun) */
    boolean existsByDriverIdAndStatusIn(Long driverId, List<TripStatus> statuses);

    /** Band haydovchilar IDlari — push gate'larida (matching/broadcast) bir so'rovda tekshirish uchun */
    @Query("SELECT t.driver.id FROM Trip t WHERE t.driver IS NOT NULL AND t.status IN :statuses")
    List<Long> findBusyDriverIds(@Param("statuses") List<TripStatus> statuses);

    /** Bugungi tugallangan triplar (statistika uchun) */
    List<Trip> findByDriverIdAndStatusAndCreatedAtAfter(Long driverId, TripStatus status, LocalDateTime after);

    /** Kunlik/haftalik/oylik breakdown uchun */
    List<Trip> findByDriverIdAndStatusAndCreatedAtBetween(Long driverId, TripStatus status, LocalDateTime from, LocalDateTime to);

    // ─── Yo'lovchi ────────────────────────────────────────────────────────

    @Query("SELECT t FROM Trip t " +
           "LEFT JOIN FETCH t.passenger " +
           "LEFT JOIN FETCH t.driver d LEFT JOIN FETCH d.user " +
           "LEFT JOIN FETCH t.tariff " +
           "WHERE t.passenger.id = :passengerId ORDER BY t.createdAt DESC")
    Page<Trip> findByPassengerIdWithRelations(@Param("passengerId") Long passengerId, Pageable pageable);

    Page<Trip> findByPassengerIdOrderByCreatedAtDesc(Long passengerId, Pageable pageable);

    Optional<Trip> findByPassengerIdAndStatus(Long passengerId, TripStatus status);

    Optional<Trip> findFirstByPassengerIdAndStatusIn(Long passengerId, List<TripStatus> statuses);

    /** Yo'lovchining jami triplar soni */
    long countByPassengerId(Long passengerId);

    /** Yo'lovchining jami xarajatlari */
    @Query("SELECT COALESCE(SUM(t.totalPrice), 0) FROM Trip t WHERE t.passenger.id = :pid AND t.status = 'COMPLETED'")
    Long sumSpentByPassenger(@Param("pid") Long passengerId);

    /** Haydovchining statuslar bo'yicha trip soni */
    long countByDriverIdAndStatus(Long driverId, TripStatus status);

    /** Haydovchiga tayinlangan jami triplar */
    long countByDriverId(Long driverId);

    // ─── Statistika ───────────────────────────────────────────────────────
    @Query("SELECT COUNT(t) FROM Trip t WHERE t.status = :status AND t.createdAt >= :from")
    long countByStatusAndCreatedAtAfter(@Param("status") TripStatus status, @Param("from") LocalDateTime from);

    @Query("SELECT COALESCE(SUM(t.totalPrice), 0) FROM Trip t WHERE t.status = 'COMPLETED' AND t.completedAt >= :from")
    Long sumRevenueAfter(@Param("from") LocalDateTime from);

    // ─── Umumiy ────────────────────────────────────────────────────────────

    @Query("SELECT t FROM Trip t " +
           "LEFT JOIN FETCH t.passenger " +
           "LEFT JOIN FETCH t.tariff " +
           "WHERE t.status = :status ORDER BY t.createdAt ASC")
    List<Trip> findByStatusWithRelations(@Param("status") TripStatus status);

    List<Trip> findByStatusOrderByCreatedAtAsc(TripStatus status);

    /** Eskirgan SEARCHING triplarni topish (auto-expire uchun) */
    List<Trip> findByStatusAndCreatedAtBefore(TripStatus status, LocalDateTime before);

    /** Rejalashtirilgan buyurtmalar — vaqti kelganlar (scheduler dispatch uchun) */
    List<Trip> findByStatusAndScheduledAtLessThanEqual(TripStatus status, LocalDateTime time);

    /** Yo'lovchining kelgusi rejalashtirilgan buyurtmalari (ro'yxat) */
    List<Trip> findByPassengerIdAndStatusOrderByScheduledAtAsc(Long passengerId, TripStatus status);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Trip t SET t.driver = null WHERE t.driver.id = :driverId")
    void nullifyDriver(@org.springframework.data.repository.query.Param("driverId") Long driverId);

    long countByStatus(TripStatus status);

    // ─── Admin moliyaviy hisobot ───────────────────────────────────────────
    @Query("SELECT COUNT(t) FROM Trip t WHERE t.status = :status AND t.createdAt BETWEEN :from AND :to")
    long countByStatusAndCreatedAtBetween(@Param("status") TripStatus status,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(t.totalPrice), 0) FROM Trip t WHERE t.status = 'COMPLETED' AND t.completedAt BETWEEN :from AND :to")
    Long sumRevenueBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(t) FROM Trip t WHERE t.status IN :statuses AND t.createdAt BETWEEN :from AND :to")
    long countByStatusInAndCreatedAtBetween(@Param("statuses") List<TripStatus> statuses,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Mijozning oxirgi CALL tripi (operator tarix uchun) */
    @Query("SELECT t FROM Trip t WHERE t.passenger.id = :passengerId AND t.source = 'CALL' ORDER BY t.createdAt DESC LIMIT 1")
    Optional<Trip> findLastCallTripByPassengerId(@Param("passengerId") Long passengerId);

    /** Source bo'yicha bugungi trip soni (statistika uchun) */
    @Query("SELECT COUNT(t) FROM Trip t WHERE t.source = :source AND t.createdAt >= :from")
    long countBySourceAndCreatedAtAfter(@Param("source") String source, @Param("from") LocalDateTime from);

    Optional<Trip> findFirstByDriverIdAndSourceAndStatus(Long driverId, String source, TripStatus status);

    @Query("SELECT t FROM Trip t WHERE t.source = :source ORDER BY t.createdAt DESC")
    Page<Trip> findBySource(@Param("source") String source, Pageable pageable);

    /** Bugungi yuborilgan SMS soni hisoblanmaydi — Redis da hisoblanadi */

    /** Bulk fetch — moliyaviy hisobot uchun (N*3 query o'rniga 1 ta) */
    @Query("SELECT t FROM Trip t WHERE t.status = :status AND t.createdAt BETWEEN :from AND :to")
    List<Trip> findByStatusAndCreatedAtBetweenList(@Param("status") TripStatus status,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT t FROM Trip t WHERE t.status IN :statuses AND t.createdAt BETWEEN :from AND :to")
    List<Trip> findByStatusInAndCreatedAtBetweenList(@Param("statuses") List<TripStatus> statuses,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // ─── Broadcast taxta ──────────────────────────────────────────────────────

    /** 1 daqiqadan oshiq SEARCHING, hali broadcast qilinmagan triplar (scheduler uchun) */
    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.passenger LEFT JOIN FETCH t.tariff " +
           "WHERE t.status = 'SEARCHING' AND t.createdAt <= :cutoff AND t.broadcastAt IS NULL")
    List<Trip> findSearchingTripsToBroadcast(@Param("cutoff") LocalDateTime cutoff);

    /** Broadcast taxta — haydovchi qabul qilishi uchun */
    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.passenger LEFT JOIN FETCH t.tariff " +
           "WHERE t.status = 'SEARCHING' AND t.broadcastAt IS NOT NULL AND t.driver IS NULL " +
           "ORDER BY t.broadcastAt DESC")
    List<Trip> findBroadcastTripBoard();

    // ─── Phase 1: osilib qolgan taxometer triplar backstop ──────────────────

    /**
     * STARTED holatida osilib qolgan TAXOMETER/CALL_TAXOMETER triplar (boshlanish vaqti cutoff'dan eski).
     * Sabab: agar taxometer "Yakunlash" muvaffaqiyatsiz bo'lsa, trip STARTED qolib haydovchini
     * doimiy "band" qiladi (ACTIVE_DRIVER_STATUSES) — bu backstop ularni konservativ chegaradan keyin yopadi.
     */
    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.driver " +
           "WHERE t.status = com.taxi.backend.enums.TripStatus.STARTED " +
           "AND t.source IN ('TAXOMETER', 'CALL_TAXOMETER') " +
           "AND COALESCE(t.startedAt, t.createdAt) < :cutoff")
    List<Trip> findStuckStartedTaximeterTrips(@Param("cutoff") LocalDateTime cutoff);
}
