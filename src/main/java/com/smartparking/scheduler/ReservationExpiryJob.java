package com.smartparking.scheduler;

import com.smartparking.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background scanner for unclaimed reservations. The scan interval and grace
 * period are configurable in application.properties.
 */
@Component
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final ReservationService reservationService;

    public ReservationExpiryJob(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(
            fixedDelayString = "${parking.reservation.expiry-scan-ms:30000}",
            initialDelayString = "${parking.reservation.expiry-initial-delay-ms:15000}"
    )
    public void expireReservations() {
        int expired = reservationService.expireOverdueReservations();
        if (expired > 0) {
            log.info("Expired {} unclaimed reservation(s)", expired);
        }
    }
}
