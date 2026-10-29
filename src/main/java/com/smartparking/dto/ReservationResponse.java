package com.smartparking.dto;

import com.smartparking.domain.Reservation;
import com.smartparking.domain.enums.ReservationStatus;
import com.smartparking.domain.enums.SpaceKind;
import com.smartparking.domain.enums.VehicleKind;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        String reservationCode,
        Long vehicleId,
        String vehicleNumber,
        VehicleKind vehicleType,
        String ownerName,
        Long spaceId,
        String spaceCode,
        SpaceKind spaceType,
        boolean spaceRequiresPermit,
        Long lotId,
        String lotCode,
        String lotName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        ReservationStatus status,
        LocalDateTime checkedInAt,
        LocalDateTime checkedOutAt,
        BigDecimal fee,
        LocalDateTime createdAt
) {

    /**
     * Must be called inside a transaction because vehicle/space/lot are lazy.
     */
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getReservationCode(),
                reservation.getVehicle().getId(),
                reservation.getVehicle().getVehicleNumber(),
                reservation.getVehicle().getKind(),
                reservation.getVehicle().getOwnerName(),
                reservation.getSpace().getId(),
                reservation.getSpace().getCode(),
                reservation.getSpace().getKind(),
                reservation.getSpace().requiresPermit(),
                reservation.getSpace().getLot().getId(),
                reservation.getSpace().getLot().getCode(),
                reservation.getSpace().getLot().getName(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus(),
                reservation.getCheckedInAt(),
                reservation.getCheckedOutAt(),
                reservation.getFee(),
                reservation.getCreatedAt()
        );
    }
}
