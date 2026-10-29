package com.smartparking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the Smart Parking Allocation System.
 *
 * <p>Run this class from an IDE, or use {@code mvn spring-boot:run}.
 * The REST API and the static frontend are both served on port 8080.</p>
 */
@SpringBootApplication
@EnableScheduling
public class SmartParkingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartParkingApplication.class, args);
    }
}
