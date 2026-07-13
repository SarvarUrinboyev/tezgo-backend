package com.taxi.backend.repository;

import com.taxi.backend.model.ClickPaymentOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClickPaymentOrderRepository extends JpaRepository<ClickPaymentOrder, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM ClickPaymentOrder o WHERE o.merchantTransId = :merchantTransId")
    Optional<ClickPaymentOrder> findByMerchantTransIdForUpdate(
            @Param("merchantTransId") String merchantTransId);
}
