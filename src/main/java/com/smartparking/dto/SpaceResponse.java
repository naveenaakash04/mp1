package com.smartparking.dto;

import com.smartparking.domain.enums.SpaceKind;
import com.smartparking.domain.enums.SpaceStatus;

import java.time.LocalDateTime;

public record SpaceResponse(
        Long id,
        String code,
        SpaceKind type,
        int floorNumber,
        boolean requiresPermit,
        SpaceStatus status,
        Long activeReservationId,
        String activeVehicleNumber,
        LocalDateTime reservationStart,
        LocalDateTime reservationEnd,
        Long lotId,
        String lotCode,
        String lotName,
        int travelMinutes
) {
}
