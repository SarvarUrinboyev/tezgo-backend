package com.taxi.backend.repository;

import com.taxi.backend.model.ConsentLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentLogRepository extends JpaRepository<ConsentLog, Long> {

    boolean existsByPhoneAndConsentTypeAndPolicyVersion(String phone, String consentType, String policyVersion);
}
