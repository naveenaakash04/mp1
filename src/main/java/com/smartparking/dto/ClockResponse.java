package com.smartparking.dto;

import java.time.LocalDateTime;

public record ClockResponse(
        LocalDateTime simulatedNow,
        LocalDateTime realNow,
        long offsetMinutes
) {
}
