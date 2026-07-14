package com.taxi.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class DispatchClockConfiguration {
    @Bean
    Clock dispatchClock() {
        return Clock.systemDefaultZone();
    }
}
