package com.taxi.backend.service;

import com.taxi.backend.enums.TransactionType;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Transaction;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single transaction boundary for Click credits.  The driver row lock and TOPUP
 * snapshot are committed together and are shared by app-link and catalog flows.
 */
@Service
public class ClickWalletCreditService {

    private final DriverRepository driverRepository;
    private final TransactionRepository transactionRepository;

    public ClickWalletCreditService(DriverRepository driverRepository,
                                    TransactionRepository transactionRepository) {
        this.driverRepository = driverRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public CreditResult credit(Long driverId, long amount, String description) {
        if (driverId == null || amount <= 0) {
            throw new IllegalArgumentException("Click payment amount must be positive");
        }
        Driver driver = driverRepository.findByIdForUpdate(driverId)
                .orElseThrow(() -> new IllegalStateException("Click payment driver not found"));
        if (driver.getBalance() == null) {
            throw new IllegalStateException("Click payment driver balance is null");
        }

        long balanceBefore = driver.getBalance();
        long balanceAfter = Math.addExact(balanceBefore, amount);
        driver.setBalance(balanceAfter);

        Transaction tx = new Transaction();
        tx.setDriver(driver);
        tx.setType(TransactionType.TOPUP);
        tx.setAmount(amount);
        tx.setBalanceBefore(balanceBefore);
        tx.setBalanceAfter(balanceAfter);
        tx.setDescription(description);
        transactionRepository.save(tx);

        return new CreditResult(balanceBefore, balanceAfter);
    }

    public record CreditResult(long balanceBefore, long balanceAfter) { }
}
