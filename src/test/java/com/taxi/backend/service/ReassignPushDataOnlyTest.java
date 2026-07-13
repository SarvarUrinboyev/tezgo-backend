package com.taxi.backend.service;

import com.taxi.backend.enums.DriverStatus;
import com.taxi.backend.enums.TripStatus;
import com.taxi.backend.model.Driver;
import com.taxi.backend.model.Trip;
import com.taxi.backend.model.TripDriverOffer;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.BroadcastMessageRepository;
import com.taxi.backend.repository.DriverPhotoRepository;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.DriverServiceRepository;
import com.taxi.backend.repository.RatingRepository;
import com.taxi.backend.repository.TariffRepository;
import com.taxi.backend.repository.TransactionRepository;
import com.taxi.backend.repository.TripRepository;
import com.taxi.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Admin reassign must join the same durable offer engine as normal dispatch. */
@ExtendWith(MockitoExtension.class)
class ReassignPushDataOnlyTest {
    @Mock private DriverRepository drivers;
    @Mock private DriverPhotoRepository driverPhotos;
    @Mock private DriverServiceRepository driverServices;
    @Mock private TripRepository trips;
    @Mock private BroadcastMessageRepository broadcasts;
    @Mock private UserRepository users;
    @Mock private SimpMessagingTemplate messaging;
    @Mock private RatingRepository ratings;
    @Mock private TransactionRepository transactions;
    @Mock private ChatService chat;
    @Mock private TariffRepository tariffs;
    @Mock private AsyncNotificationService notifications;
    @Mock private PhotoService photos;
    @Mock private TripNotificationHelper offerHelper;

    @Test
    void reassignCreatesOneSpecificDurableOfferInsteadOfBuildingItsOwnPush() {
        AdminService admin = new AdminService(drivers, driverPhotos, driverServices, trips, broadcasts, users,
                messaging, ratings, transactions, chat, tariffs, notifications, photos, offerHelper);
        Trip trip = new Trip(); trip.setId(777L); trip.setStatus(TripStatus.SEARCHING);
        Driver driver = new Driver(); driver.setId(6L); driver.setStatus(DriverStatus.ACTIVE); driver.setDriverCode("TZ-9");
        User user = new User(); user.setName("Bek"); driver.setUser(user);
        TripDriverOffer offer = new TripDriverOffer(); offer.setExpiresAt(LocalDateTime.now().plusSeconds(15));
        when(trips.findById(777L)).thenReturn(Optional.of(trip));
        when(drivers.findById(6L)).thenReturn(Optional.of(driver));
        when(offerHelper.notifySpecificDriver(777L, 6L)).thenReturn(Optional.of(offer));

        var result = admin.adminReassignTripToDriver(777L, 6L);

        verify(offerHelper).notifySpecificDriver(777L, 6L);
        assertEquals(777L, result.get("tripId"));
        assertEquals(6L, result.get("driverId"));
    }
}
