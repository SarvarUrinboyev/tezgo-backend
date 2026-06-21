package com.taxi.backend.service;

import com.taxi.backend.enums.TransactionType;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Transaction;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminTopupPaymentMethodTest {

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

    private Driver stubDriver(Long id) {
        User u = new User(); u.setId(id + 100);
        Driver d = new Driver(); d.setId(id); d.setUser(u);
        d.setBalance(0L); d.setDriverCode("TZ-0001");
        return d;
    }

    @Test
    void topupWithCash_storesPaymentMethodCash() {
        Driver d = stubDriver(1L);
        when(driverRepository.findById(1L)).thenReturn(Optional.of(d));
        when(driverRepository.addToBalance(anyLong(), anyLong())).thenReturn(1);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);

        adminService.adminTopupBalance(1L, 50_000L, "CASH");

        verify(transactionRepository).save(txCaptor.capture());
        Transaction saved = txCaptor.getValue();
        assertEquals("CASH", saved.getPaymentMethod());
        assertEquals(TransactionType.TOPUP, saved.getType());
        assertEquals(5_000_000L, saved.getAmount()); // 50_000 UZS * 100
    }

    @Test
    void topupWithCard_storesPaymentMethodCard() {
        Driver d = stubDriver(2L);
        when(driverRepository.findById(2L)).thenReturn(Optional.of(d));
        when(driverRepository.addToBalance(anyLong(), anyLong())).thenReturn(1);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);

        adminService.adminTopupBalance(2L, 100_000L, "CARD");

        verify(transactionRepository).save(txCaptor.capture());
        assertEquals("CARD", txCaptor.getValue().getPaymentMethod());
    }
}
