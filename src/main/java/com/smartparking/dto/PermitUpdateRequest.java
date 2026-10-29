package com.smartparking.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for changing a vehicle's accessibility permit.
 */
public record PermitUpdateRequest(
        @NotNull(message = "accessibilityPermit must be true or false")
        Boolean accessibilityPermit
) {
}
