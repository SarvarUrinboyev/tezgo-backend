package com.taxi.backend.repository;

import com.taxi.backend.model.Driver;
import com.taxi.backend.enums.DriverStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByUserId(Long userId);

    @Query("SELECT d FROM Driver d JOIN FETCH d.user")
    List<Driver> findAllWithUser();

    @Query(value = "SELECT d FROM Driver d JOIN FETCH d.user",
           countQuery = "SELECT COUNT(d) FROM Driver d")
    Page<Driver> findAllWithUser(Pageable pageable);

    @Query(value = "SELECT d FROM Driver d JOIN FETCH d.user WHERE d.status = :status",
           countQuery = "SELECT COUNT(d) FROM Driver d WHERE d.status = :status")
    Page<Driver> findByStatusWithUser(@Param("status") com.taxi.backend.enums.DriverStatus status, Pageable pageable);

    List<Driver> findByStatus(DriverStatus status);

    List<Driver> findByIsOnlineTrue();

    List<Driver> findByIsOnlineFalse();

    @Query("SELECT d FROM Driver d WHERE d.isOnline = true AND d.status = 'ACTIVE' " +
            "AND d.latitude IS NOT NULL")
    List<Driver> findActiveOnlineDrivers();

    long countByStatus(DriverStatus status);

    long countByIsOnlineTrue();

    @Query("SELECT COUNT(d) FROM Driver d WHERE d.isOnline = true AND d.status = com.taxi.backend.enums.DriverStatus.ACTIVE")
    long countByIsOnlineTrueAndStatusACTIVE();

    // ── Batch fetch (N+1 fix) ──
    @Query("SELECT d FROM Driver d JOIN FETCH d.user WHERE d.id IN :ids")
    List<Driver> findAllByIdsWithUser(@Param("ids") List<Long> ids);

    // ── Atomic balance operations (race condition fix) ──
    @Modifying
    @Query("UPDATE Driver d SET d.balance = d.balance + :amount WHERE d.id = :driverId")
    int addToBalance(@Param("driverId") Long driverId, @Param("amount") long amount);

    @Modifying
    @Query("UPDATE Driver d SET d.balance = d.balance - :amount WHERE d.id = :driverId AND d.balance >= :amount")
    int deductBalanceIfSufficient(@Param("driverId") Long driverId, @Param("amount") long amount);

    long countByPassportSeriesAndPassportNumber(String passportSeries, String passportNumber);

    long countByTechPassportNumber(String techPassportNumber);

    // ── Driver Code ──
    @Query(value = "SELECT 'TZ-' || LPAD(nextval('driver_code_seq')::TEXT, 4, '0')", nativeQuery = true)
    String nextDriverCode();

    Optional<Driver> findByDriverCode(String driverCode);

    // Click ADVANCED SHOP getinfo — eager-fetch the User so getName() works OUTSIDE a transaction.
    // (getinfo runs via dispatch()'s self-invocation, so @Transactional on the handler is a no-op
    //  in proxy mode — JOIN FETCH is the robust fix; no lazy proxy to initialize.)
    @Query("SELECT d FROM Driver d JOIN FETCH d.user WHERE d.driverCode = :driverCode")
    Optional<Driver> findByDriverCodeWithUser(@Param("driverCode") String driverCode);

    // DB fallback for matching when Redis + in-memory both empty (e.g. after server restart)
    @Query("SELECT d FROM Driver d WHERE d.isOnline = true AND d.status = com.taxi.backend.enums.DriverStatus.ACTIVE " +
            "AND d.latitude IS NOT NULL AND d.longitude IS NOT NULL AND d.updatedAt >= :since")
    List<Driver> findOnlineDriversUpdatedSince(@Param("since") LocalDateTime since);

    // ── Push token — DB fallback for Redis outage ──
    @Modifying
    @Transactional
    @Query("UPDATE Driver d SET d.pushToken = :token WHERE d.id = :driverId")
    void updatePushToken(@Param("driverId") Long driverId, @Param("token") String token);

    @Query("SELECT d.pushToken FROM Driver d WHERE d.id = :driverId")
    String findPushTokenById(@Param("driverId") Long driverId);

    // ── Haydovchining yoqilgan xizmatlari (driver_services.is_enabled = true) ──
    // Service eligibility hard-filter uchun — DriverServiceFilter bilan ishlatiladi.
    @Query("SELECT ds.serviceType FROM DriverService ds WHERE ds.driver.id = :driverId AND ds.isEnabled = true")
    List<com.taxi.backend.enums.ServiceType> findEnabledServiceTypesByDriverId(@Param("driverId") Long driverId);

    // Batch (N+1 fix) — bir nechta haydovchining yoqilgan xizmatlari: [driverId, serviceType] juftliklari.
    @Query("SELECT ds.driver.id, ds.serviceType FROM DriverService ds " +
            "WHERE ds.driver.id IN :driverIds AND ds.isEnabled = true")
    List<Object[]> findEnabledServiceRowsByDriverIds(@Param("driverIds") java.util.Collection<Long> driverIds);

    @Query(value = "SELECT d FROM Driver d JOIN FETCH d.user WHERE " +
           "LOWER(d.user.name) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "d.user.phone LIKE CONCAT('%',:q,'%') OR " +
           "LOWER(d.driverCode) LIKE LOWER(CONCAT('%',:q,'%'))",
           countQuery = "SELECT COUNT(d) FROM Driver d JOIN d.user WHERE " +
           "LOWER(d.user.name) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "d.user.phone LIKE CONCAT('%',:q,'%') OR " +
           "LOWER(d.driverCode) LIKE LOWER(CONCAT('%',:q,'%'))")
    Page<Driver> searchByNameOrPhoneOrCode(@Param("q") String q, Pageable pageable);
}
