package com.smartparking.domain.enums;

/**
 * Lifecycle of a reservation.
 *
 * <pre>
 * RESERVED --check-in--> CHECKED_IN --check-out--> COMPLETED
 *    |                        |
 *    +--cancel--> CANCELLED   +--expire--> EXPIRED (only before check-in)
 * </pre>
 */
public enum ReservationStatus {
    RESERVED,
    CHECKED_IN,
    COMPLETED,
    CANCELLED,
    EXPIRED
}
