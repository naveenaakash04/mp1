package com.smartparking.domain;

import com.smartparking.domain.enums.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable audit/history row. Reservation ids are stored as plain numbers
 * (not foreign keys) so history survives even if a reservation is deleted.
 */
@Entity
@Table(
        name = "parking_events",
        indexes = {
                @Index(name = "idx_event_occurred", columnList = "occurred_at"),
                @Index(name = "idx_event_vehicle", columnList = "vehicle_number")
        }
)
public class ParkingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "vehicle_number", length = 20)
    private String vehicleNumber;

    @Column(name = "space_code", length = 20)
    private String spaceCode;

    @Column(name = "lot_code", length = 20)
    private String lotCode;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(length = 255)
    private String message;

    @Column(precision = 10, scale = 2)
    private BigDecimal fee;

    protected ParkingEvent() {
        // Required by JPA.
    }

    public ParkingEvent(EventType eventType, Long reservationId, String vehicleNumber,
                        String spaceCode, String lotCode, LocalDateTime occurredAt,
                        String message, BigDecimal fee) {
        this.eventType = eventType;
        this.reservationId = reservationId;
        this.vehicleNumber = vehicleNumber;
        this.spaceCode = spaceCode;
        this.lotCode = lotCode;
        this.occurredAt = occurredAt;
        this.message = message;
        this.fee = fee;
    }

    public Long getId() {
        return id;
    }

    public EventType getEventType() {
        return eventType;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public String getSpaceCode() {
        return spaceCode;
    }

    public String getLotCode() {
        return lotCode;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getMessage() {
        return message;
    }

    public BigDecimal getFee() {
        return fee;
    }
}
