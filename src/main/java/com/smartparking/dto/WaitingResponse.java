package com.smartparking.dto;

import com.smartparking.domain.enums.VehicleKind;
import com.smartparking.domain.enums.WaitingStatus;

import java.time.LocalDateTime;

public record WaitingResponse(
        Long id,
        int queuePosition,
        Long vehicleId,
        String vehicleNumber,
        VehicleKind vehicleType,
        boolean accessibilityPermit,
        int accessPriority,
        LocalDateTime arrivalTime,
        LocalDateTime startTime,
        LocalDateTime endTime,
        WaitingStatus status,
        Long allocatedReservationId
) {
}
