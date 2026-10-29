package com.smartparking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Request body for creating a reservation.
 *
 * <p>{@code preferredSpaceId} is optional. When it is supplied, allocation is
 * restricted to that space (useful for demonstrations and for testing a
 * specific conflict). When it is null, the smart allocator chooses the best
 * eligible space.</p>
 */
public record ReservationRequest(
        @NotNull(message = "vehicleId is required")
        Long vehicleId,

        @NotNull(message = "startTime is required")
        LocalDateTime startTime,

        @NotNull(message = "endTime is required")
        LocalDateTime endTime,

        Long preferredSpaceId
) {
}
