package com.smartparking.dto;

import com.smartparking.domain.enums.VehicleKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for registering a vehicle.
 */
public record VehicleRequest(
        @NotBlank(message = "Vehicle number is required")
        @Size(min = 4, max = 20, message = "Vehicle number must be 4-20 characters")
        @Pattern(regexp = "^[A-Za-z0-9 -]+$", message = "Vehicle number may contain letters, digits, spaces and hyphens only")
        String vehicleNumber,

        @NotNull(message = "Vehicle type is required (CAR or TWO_WHEELER)")
        VehicleKind type,

        @NotBlank(message = "Owner name is required")
        @Size(max = 100, message = "Owner name must be at most 100 characters")
        String ownerName,

        boolean accessibilityPermit
) {
}
