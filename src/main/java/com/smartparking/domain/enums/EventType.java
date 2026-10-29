package com.smartparking.domain.enums;

/**
 * Types of entries stored in the parking event history.
 */
public enum EventType {
    RESERVED,
    CANCELLED,
    CHECKED_IN,
    CHECKED_OUT,
    EXPIRED,
    WAITING_JOINED,
    WAITING_ALLOCATED,
    WAITING_CANCELLED,
    WAITING_EXPIRED
}
