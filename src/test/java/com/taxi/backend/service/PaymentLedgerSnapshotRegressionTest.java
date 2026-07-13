package com.taxi.backend.service;

import com.taxi.backend.enums.Role;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Transaction;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.ClickPaymentOrderRepository;
import com.taxi.backend.repository.ClickTransactionRepository;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Regression for the production P1: a JPQL bulk update must not be followed by
 * ledger snapshots read from an already-managed, stale Driver entity.
 *
 * <p>Before the fix this test fails with 9_227_037 / 9_327_037, exactly as the
 * production Click payment did. It deliberately uses PostgreSQL and Hibernate,
 * rather than mocking the repository, because the persistence-context behavior
 * is the defect under test.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration"
})
class PaymentLedgerSnapshotRegressionTest {

    private static final long INITIAL_BALANCE = 9_327_037L;
    private static final long CLICK_AMOUNT = 100_000L;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private EntityManager entityManager;
    @Autowired private DriverRepository driverRepository;
    @Autowired private TransactionRepository transactionRepository;

    private PaymentService paymentService;
    private Long driverId;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                mock(org.springframework.data.redis.core.StringRedisTemplate.class),
                driverRepository,
                transactionRepository,
                mock(ClickTransactionRepository.class),
                mock(ClickPaymentOrderRepository.class)
        );

        User user = new User();
        user.setPhone("+998900000001");
        user.setRole(Role.DRIVER);
        user.setName("Ledger regression driver");
        entityManager.persist(user);

        Driver driver = new Driver();
        driver.setUser(user);
        driver.setDriverCode("TZ-LEDGER-001");
        driver.setBalance(INITIAL_BALANCE);
        entityManager.persist(driver);
        entityManager.flush();
        entityManager.clear();
        driverId = driver.getId();
    }

    @Test
    void clickCreditWritesAuthoritativeLedgerSnapshots() {
        paymentService.creditDriverBalance(driverId, CLICK_AMOUNT, "Click regression");
        entityManager.flush();
        entityManager.clear();

        Driver persistedDriver = driverRepository.findById(driverId).orElseThrow();
        Transaction ledger = transactionRepository.findByDriverId(driverId).stream()
                .max(Comparator.comparing(Transaction::getId))
                .orElseThrow();

        assertEquals(INITIAL_BALANCE + CLICK_AMOUNT, persistedDriver.getBalance());
        assertEquals(INITIAL_BALANCE, ledger.getBalanceBefore());
        assertEquals(INITIAL_BALANCE + CLICK_AMOUNT, ledger.getBalanceAfter());
        assertEquals(CLICK_AMOUNT, ledger.getAmount());
    }
}
