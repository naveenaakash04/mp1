package com.smartparking.algorithm;

import java.time.LocalDateTime;

/**
 * Immutable interval stored in the in-memory {@link ParkingIndex}.
 * ArrayList is used per space because intervals are naturally a list.
 */
public record ReservationInterval(
        Long reservationId,
        Long spaceId,
        String vehicleNumber,
        LocalDateTime start,
        LocalDateTime end
) {

    public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
        return start.isBefore(otherEnd) && end.isAfter(otherStart);
    }
}
