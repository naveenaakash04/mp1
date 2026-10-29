package com.smartparking.exception;

/**
 * Thrown when a request conflicts with current data, for example an
 * overlapping reservation or a duplicate vehicle number. Mapped to HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
