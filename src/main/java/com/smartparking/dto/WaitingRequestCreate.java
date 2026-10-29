package com.smartparking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Request body for joining the waiting list.
 */
public record WaitingRequestCreate(
        @NotNull(message = "vehicleId is required")
        Long vehicleId,

        @NotNull(message = "startTime is required")
        LocalDateTime startTime,

        @NotNull(message = "endTime is required")
        LocalDateTime endTime
) {
}
