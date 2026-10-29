package com.smartparking.domain;

import com.smartparking.domain.enums.SpaceKind;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * An accessible parking space. It can be used by a car or a two-wheeler,
 * but only when the vehicle has a valid accessibility permit.
 */
@Entity
@DiscriminatorValue("ACCESSIBLE")
public class AccessibleSpace extends ParkingSpace {

    protected AccessibleSpace() {
        // Required by JPA.
    }

    public AccessibleSpace(String code, ParkingLot lot, int floorNumber) {
        super(code, lot, floorNumber);
    }

    @Override
    public boolean canAccommodate(Vehicle vehicle) {
        return vehicle != null && vehicle.hasAccessibilityPermit();
    }

    @Override
    public boolean requiresPermit() {
        return true;
    }

    @Override
    public SpaceKind getKind() {
        return SpaceKind.ACCESSIBLE;
    }
}
