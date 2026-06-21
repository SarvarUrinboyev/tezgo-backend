package com.taxi.backend.service;

import com.taxi.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceLiniyadaTest {

    @Mock private DriverRepository driverRepository;
    @Mock private DriverPhotoRepository driverPhotoRepository;
    @Mock private DriverServiceRepository driverServiceRepository;
    @Mock private TripRepository tripRepository;
    @Mock private BroadcastMessageRepository broadcastMessageRepository;
    @Mock private UserRepository userRepository;
    @Mock private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    @Mock private RatingRepository ratingRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private ChatService chatService;

    @InjectMocks private AdminService adminService;

    @Test
    void getLiniyadaCount_returnsOnlineActiveDriverCount() {
        when(driverRepository.countByIsOnlineTrueAndStatusACTIVE()).thenReturn(7L);

        long result = adminService.getLiniyadaCount();

        assertEquals(7L, result);
        verify(driverRepository).countByIsOnlineTrueAndStatusACTIVE();
    }

    @Test
    void getLiniyadaCount_returnsZeroWhenNoneOnline() {
        when(driverRepository.countByIsOnlineTrueAndStatusACTIVE()).thenReturn(0L);

        assertEquals(0L, adminService.getLiniyadaCount());
    }
}
