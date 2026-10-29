package com.smartparking.dto;

import com.smartparking.domain.Vehicle;
import com.smartparking.domain.enums.VehicleKind;

import java.time.LocalDateTime;

public record VehicleResponse(
        Long id,
        String vehicleNumber,
        VehicleKind type,
        String ownerName,
        boolean accessibilityPermit,
        LocalDateTime createdAt
) {

    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getVehicleNumber(),
                vehicle.getKind(),
                vehicle.getOwnerName(),
                vehicle.hasAccessibilityPermit(),
                vehicle.getCreatedAt()
        );
    }
}
