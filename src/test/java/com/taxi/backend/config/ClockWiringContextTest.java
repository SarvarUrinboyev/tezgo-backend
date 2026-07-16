package com.taxi.backend.config;

import com.taxi.backend.pricing.NightFareService;
import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.TripDriverOfferRepository;
import com.taxi.backend.repository.TripRepository;
import com.taxi.backend.service.DispatchOfferTimingProperties;
import com.taxi.backend.service.MatchingService;
import com.taxi.backend.service.PushNotificationService;
import com.taxi.backend.service.TripOfferDeliveryService;
import com.taxi.backend.service.TripOfferLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ClockWiringContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("pricing.night.assert-utc=false")
            .withUserConfiguration(WiringConfiguration.class);

    @Test
    void productionClockConsumersUseTheirNamedClocksWhenBothBeansExist() {
        contextRunner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();

            Clock dispatchClock = context.getBean("dispatchClock", Clock.class);
            Clock nightClock = context.getBean("nightClock", Clock.class);
            assertThat(dispatchClock).isNotSameAs(nightClock);

            assertThat(ReflectionTestUtils.getField(
                    context.getBean(TripOfferDeliveryService.class), "clock"))
                    .isSameAs(dispatchClock);
            assertThat(ReflectionTestUtils.getField(
                    context.getBean(TripOfferLifecycleService.class), "clock"))
                    .isSameAs(dispatchClock);
            assertThat(ReflectionTestUtils.getField(
                    context.getBean(NightFareService.class), "clock"))
                    .isSameAs(nightClock);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            DispatchClockConfiguration.class,
            TimeConfig.class,
            DispatchOfferTimingProperties.class,
            TripOfferDeliveryService.class,
            TripOfferLifecycleService.class,
            NightFareService.class
    })
    static class WiringConfiguration {

        @Bean
        TripDriverOfferRepository tripDriverOfferRepository() {
            return mock(TripDriverOfferRepository.class);
        }

        @Bean
        TripRepository tripRepository() {
            return mock(TripRepository.class);
        }

        @Bean
        DriverRepository driverRepository() {
            return mock(DriverRepository.class);
        }

        @Bean
        SimpMessagingTemplate simpMessagingTemplate() {
            return mock(SimpMessagingTemplate.class);
        }

        @Bean
        PushNotificationService pushNotificationService() {
            return mock(PushNotificationService.class);
        }

        @Bean
        MatchingService matchingService() {
            return mock(MatchingService.class);
        }
    }
}
