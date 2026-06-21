package com.taxi.backend.service;

import com.taxi.backend.enums.TransactionType;
import com.taxi.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminDashboardStatsTest {

    @Mock private DriverRepository driverRepository;
    @Mock private DriverPhotoRepository driverPhotoRepository;
    @Mock private DriverServiceRepository driverServiceRepository;
    @Mock private TripRepository tripRepository;
    @Mock private BroadcastMessageRepository broadcastMessageRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private RatingRepository ratingRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private ChatService chatService;

    @InjectMocks private AdminService adminService;

    @Test
    @SuppressWarnings("unchecked")
    void commissionStats_convertsTiiyinToUzs() {
        // 500_000 tiyin = 5_000 UZS commission stored as positive (COMMISSION)
        // 200_000 tiyin = stored as -200_000 (TAXOMETER_COMMISSION) → ABS = 200_000
        // total ABS = 700_000 tiyin = 7_000 UZS
        when(transactionRepository.sumAbsAmountByTypesAfter(anyList(), any(LocalDateTime.class)))
                .thenReturn(700_000L);

        Map<String, Object> stats = adminService.getDashboardExtendedStats();
        Map<String, Object> commission = (Map<String, Object>) stats.get("commission");

        assertEquals(7_000L, commission.get("today"));
        assertEquals(7_000L, commission.get("month"));
        assertEquals(7_000L, commission.get("total"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void topupStats_sumAmountAndDistinctDriversAndCashCardSplit() {
        // Admin topup: 3 000 000 tiyin = 30 000 UZS, 5 drivers
        // cash: 2 000 000 tiyin = 20 000 UZS, card: 1 000 000 tiyin = 10 000 UZS
        when(transactionRepository.sumAbsAmountByTypesAfter(anyList(), any(LocalDateTime.class)))
                .thenReturn(0L);
        when(transactionRepository.sumAdminTopupAfter(any(LocalDateTime.class)))
                .thenReturn(3_000_000L);
        when(transactionRepository.countDistinctDriversAdminTopupAfter(any(LocalDateTime.class)))
                .thenReturn(5L);
        when(transactionRepository.sumAdminTopupByMethodAfter(eq("CASH"), any(LocalDateTime.class)))
                .thenReturn(2_000_000L);
        when(transactionRepository.sumAdminTopupByMethodAfter(eq("CARD"), any(LocalDateTime.class)))
                .thenReturn(1_000_000L);

        Map<String, Object> stats = adminService.getDashboardExtendedStats();
        Map<String, Object> topup = (Map<String, Object>) stats.get("topup");
        Map<String, Object> today = (Map<String, Object>) topup.get("today");

        assertEquals(30_000L, today.get("amount"));
        assertEquals(5L, today.get("drivers"));
        assertEquals(20_000L, today.get("cash"));
        assertEquals(10_000L, today.get("card"));
    }
}
