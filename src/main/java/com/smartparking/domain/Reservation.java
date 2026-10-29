package com.smartparking.domain;

import com.smartparking.domain.enums.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A booking of one parking space by one vehicle for a time interval.
 *
 * <p>Overlap rule: two active reservations overlap when
 * {@code existing.start < requested.end AND existing.end > requested.start}.
 * CANCELLED, COMPLETED and EXPIRED reservations do not block a space.</p>
 */
@Entity
@Table(
        name = "reservations",
        uniqueConstraints = @UniqueConstraint(name = "uk_reservation_code", columnNames = "reservation_code"),
        indexes = {
                @Index(name = "idx_reservation_space_time", columnList = "space_id, status, start_time, end_time"),
                @Index(name = "idx_reservation_vehicle_time", columnList = "vehicle_id, status, start_time, end_time"),
                @Index(name = "idx_reservation_status", columnList = "status")
        }
)
@Check(constraints = "end_time > start_time")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_code", nullable = false, length = 30)
    private String reservationCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reservation_vehicle"))
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reservation_space"))
    private ParkingSpace space;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @Column(name = "checked_out_at")
    private LocalDateTime checkedOutAt;

    @Column(precision = 10, scale = 2)
    private BigDecimal fee;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Optimistic-lock guard in addition to the pessimistic space lock.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    protected Reservation() {
        // Required by JPA.
    }

    public Reservation(String reservationCode, Vehicle vehicle, ParkingSpace space,
                       LocalDateTime startTime, LocalDateTime endTime,
                       ReservationStatus status, LocalDateTime createdAt) {
        this.reservationCode = reservationCode;
        this.vehicle = vehicle;
        this.space = space;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public boolean isActive() {
        return status == ReservationStatus.RESERVED || status == ReservationStatus.CHECKED_IN;
    }

    public boolean isOverlapping(LocalDateTime otherStart, LocalDateTime otherEnd) {
        return isActive() && startTime.isBefore(otherEnd) && endTime.isAfter(otherStart);
    }

    public void touch(LocalDateTime now) {
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getReservationCode() {
        return reservationCode;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public ParkingSpace getSpace() {
        return space;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(LocalDateTime checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    public LocalDateTime getCheckedOutAt() {
        return checkedOutAt;
    }

    public void setCheckedOutAt(LocalDateTime checkedOutAt) {
        this.checkedOutAt = checkedOutAt;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
