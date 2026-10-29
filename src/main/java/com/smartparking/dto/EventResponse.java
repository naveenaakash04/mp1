package com.smartparking.dto;

import com.smartparking.domain.ParkingEvent;
import com.smartparking.domain.enums.EventType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        EventType eventType,
        Long reservationId,
        String vehicleNumber,
        String spaceCode,
        String lotCode,
        LocalDateTime occurredAt,
        String message,
        BigDecimal fee
) {

    public static EventResponse from(ParkingEvent event) {
        return new EventResponse(
                event.getId(),
                event.getEventType(),
                event.getReservationId(),
                event.getVehicleNumber(),
                event.getSpaceCode(),
                event.getLotCode(),
                event.getOccurredAt(),
                event.getMessage(),
                event.getFee()
        );
    }
}
