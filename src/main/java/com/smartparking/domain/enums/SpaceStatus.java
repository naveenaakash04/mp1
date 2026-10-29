package com.smartparking.domain.enums;

/**
 * Status shown on the dashboard. It is calculated from active reservations
 * instead of being stored in the database, so it can never become stale.
 */
public enum SpaceStatus {
    AVAILABLE,
    RESERVED,
    OCCUPIED
}
