package com.smartparking.service;

import com.smartparking.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Shared validation for reservation and waiting-list time windows.
 */
@Component
public class ReservationWindowValidator {

    private final ClockService clockService;
    private final long maxDurationHours;
    private final long maxAdvanceDays;
    private final long pastGraceMinutes;

    public ReservationWindowValidator(
            ClockService clockService,
            @Value("${parking.reservation.max-duration-hours:24}") long maxDurationHours,
            @Value("${parking.reservation.max-advance-days:30}") long maxAdvanceDays,
            @Value("${parking.reservation.past-grace-minutes:5}") long pastGraceMinutes) {
        this.clockService = clockService;
        this.maxDurationHours = maxDurationHours;
        this.maxAdvanceDays = maxAdvanceDays;
        this.pastGraceMinutes = pastGraceMinutes;
    }

    public void validate(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new BusinessRuleException("Start time and end time are required");
        }
        if (!end.isAfter(start)) {
            throw new BusinessRuleException("End time must be after start time");
        }
        LocalDateTime now = clockService.now();
        if (start.isBefore(now.minusMinutes(pastGraceMinutes))) {
            throw new BusinessRuleException(
                    "Start time cannot be more than " + pastGraceMinutes + " minutes in the past");
        }
        if (start.isAfter(now.plusDays(maxAdvanceDays))) {
            throw new BusinessRuleException(
                    "Reservations can only be made up to " + maxAdvanceDays + " days ahead");
        }
        if (Duration.between(start, end).compareTo(Duration.ofHours(maxDurationHours)) > 0) {
            throw new BusinessRuleException(
                    "A reservation cannot be longer than " + maxDurationHours + " hours");
        }
    }
}
