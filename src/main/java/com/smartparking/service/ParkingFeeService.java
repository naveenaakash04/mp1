package com.smartparking.service;

import com.smartparking.domain.enums.VehicleKind;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Fee rules carried over and extended from the original console project:
 * car Rs.50/hour, two-wheeler Rs.20/hour, minimum one billable hour,
 * part hours rounded up.
 */
@Service
public class ParkingFeeService {

    public static final BigDecimal CAR_RATE = new BigDecimal("50.00");
    public static final BigDecimal TWO_WHEELER_RATE = new BigDecimal("20.00");

    public BigDecimal calculateFee(VehicleKind kind, LocalDateTime entryTime, LocalDateTime exitTime) {
        if (exitTime.isBefore(entryTime)) {
            throw new IllegalArgumentException("Exit time cannot be before entry time");
        }
        long hours = billableHours(entryTime, exitTime);
        return rate(kind)
                .multiply(BigDecimal.valueOf(hours))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public long billableHours(LocalDateTime entryTime, LocalDateTime exitTime) {
        long minutes = Math.max(0, Duration.between(entryTime, exitTime).toMinutes());
        long roundedUpHours = (minutes + 59) / 60;
        return Math.max(1, roundedUpHours);
    }

    private BigDecimal rate(VehicleKind kind) {
        return switch (kind) {
            case CAR -> CAR_RATE;
            case TWO_WHEELER -> TWO_WHEELER_RATE;
        };
    }
}
