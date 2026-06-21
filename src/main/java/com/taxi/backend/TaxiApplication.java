package com.taxi.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class TaxiApplication {

    private static final Logger log = LoggerFactory.getLogger(TaxiApplication.class);

    public static void main(String[] args) {
        log.info("TezYol Platform ishga tushmoqda...");
        SpringApplication.run(TaxiApplication.class, args);
    }
}
