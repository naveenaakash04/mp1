package com.smartparking.domain;

import com.smartparking.domain.enums.VehicleKind;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Abstract vehicle. Concrete subclasses are {@link CarVehicle} and
 * {@link TwoWheelerVehicle}. Keeping the type in a subclass (instead of a
 * plain string) lets the allocation code use polymorphism.
 */
@Entity
@Table(
        name = "vehicles",
        uniqueConstraints = @UniqueConstraint(name = "uk_vehicle_number", columnNames = "vehicle_number")
)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "vehicle_kind", discriminatorType = DiscriminatorType.STRING, length = 20)
public abstract class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_number", nullable = false, length = 20)
    private String vehicleNumber;

    @Column(name = "owner_name", nullable = false, length = 100)
    private String ownerName;

    @Column(name = "accessibility_permit", nullable = false)
    private boolean accessibilityPermit;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Vehicle() {
        // Required by JPA.
    }

    protected Vehicle(String vehicleNumber, String ownerName, boolean accessibilityPermit) {
        this.vehicleNumber = vehicleNumber;
        this.ownerName = ownerName;
        this.accessibilityPermit = accessibilityPermit;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Factory method used by the service layer. It keeps object creation in
     * one place and returns the correct subclass.
     */
    public static Vehicle create(VehicleKind kind, String vehicleNumber, String ownerName, boolean permit) {
        return switch (kind) {
            case CAR -> new CarVehicle(vehicleNumber, ownerName, permit);
            case TWO_WHEELER -> new TwoWheelerVehicle(vehicleNumber, ownerName, permit);
        };
    }

    public abstract VehicleKind getKind();

    public Long getId() {
        return id;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public boolean hasAccessibilityPermit() {
        return accessibilityPermit;
    }

    public void setAccessibilityPermit(boolean accessibilityPermit) {
        this.accessibilityPermit = accessibilityPermit;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
