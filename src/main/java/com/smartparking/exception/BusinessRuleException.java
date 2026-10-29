package com.smartparking.exception;

/**
 * Thrown when a request is valid JSON but violates a parking rule, for
 * example a car trying to use a two-wheeler space. Mapped to HTTP 422.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
