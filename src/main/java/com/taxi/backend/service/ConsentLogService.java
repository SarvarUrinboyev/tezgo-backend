package com.taxi.backend.service;

import com.taxi.backend.model.ConsentLog;
import com.taxi.backend.repository.ConsentLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rozilik yozuvini ALOHIDA tranzaksiyada saqlaydi (REQUIRES_NEW), shunda bu yerdagi
 * xato kirish (verifyOtp) tranzaksiyasini hech qachon orqaga qaytarmaydi.
 */
@Service
public class ConsentLogService {

    private final ConsentLogRepository repo;

    public ConsentLogService(ConsentLogRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ConsentLog consent) {
        if (repo.existsByPhoneAndConsentTypeAndPolicyVersion(
                consent.getPhone(), consent.getConsentType(), consent.getPolicyVersion())) {
            return; // shu telefon + tur + versiya uchun yozuv allaqachon bor
        }
        repo.save(consent);
    }
}
