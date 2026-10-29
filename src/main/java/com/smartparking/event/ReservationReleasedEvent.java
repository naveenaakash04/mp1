package com.smartparking.event;

/**
 * Published after a reservation is cancelled, checked out or expired.
 * The waiting-list processor listens after the transaction commits.
 */
public record ReservationReleasedEvent(Long reservationId, Long spaceId) {
}
