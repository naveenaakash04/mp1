package com.smartparking.domain;

import com.smartparking.domain.enums.WaitingStatus;
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
import jakarta.persistence.Version;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

/**
 * A request that waits until a compatible space becomes available.
 * The in-memory priority queue uses {@code accessPriority} and
 * {@code arrivalTime}; this entity is the durable copy in MySQL.
 */
@Entity
@Table(
        name = "waiting_requests",
        indexes = {
                @Index(name = "idx_waiting_status_arrival", columnList = "status, arrival_time"),
                @Index(name = "idx_waiting_vehicle", columnList = "vehicle_id")
        }
)
@Check(constraints = "requested_end_time > requested_start_time AND access_priority >= 0")
public class WaitingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false, foreignKey = @ForeignKey(name = "fk_waiting_vehicle"))
    private Vehicle vehicle;

    @Column(name = "requested_start_time", nullable = false)
    private LocalDateTime requestedStartTime;

    @Column(name = "requested_end_time", nullable = false)
    private LocalDateTime requestedEndTime;

    /**
     * 1 for accessibility-permit holders, 0 otherwise. Higher value wins.
     */
    @Column(name = "access_priority", nullable = false)
    private int accessPriority;

    @Column(name = "arrival_time", nullable = false)
    private LocalDateTime arrivalTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WaitingStatus status;

    @Column(name = "allocated_reservation_id")
    private Long allocatedReservationId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected WaitingRequest() {
        // Required by JPA.
    }

    public WaitingRequest(Vehicle vehicle, LocalDateTime requestedStartTime,
                          LocalDateTime requestedEndTime, int accessPriority,
                          LocalDateTime arrivalTime) {
        this.vehicle = vehicle;
        this.requestedStartTime = requestedStartTime;
        this.requestedEndTime = requestedEndTime;
        this.accessPriority = accessPriority;
        this.arrivalTime = arrivalTime;
        this.status = WaitingStatus.WAITING;
        this.createdAt = arrivalTime;
        this.updatedAt = arrivalTime;
    }

    public void touch(LocalDateTime now) {
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public LocalDateTime getRequestedStartTime() {
        return requestedStartTime;
    }

    public LocalDateTime getRequestedEndTime() {
        return requestedEndTime;
    }

    public int getAccessPriority() {
        return accessPriority;
    }

    public void setAccessPriority(int accessPriority) {
        this.accessPriority = accessPriority;
    }

    public LocalDateTime getArrivalTime() {
        return arrivalTime;
    }

    public WaitingStatus getStatus() {
        return status;
    }

    public void setStatus(WaitingStatus status) {
        this.status = status;
    }

    public Long getAllocatedReservationId() {
        return allocatedReservationId;
    }

    public void setAllocatedReservationId(Long allocatedReservationId) {
        this.allocatedReservationId = allocatedReservationId;
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
