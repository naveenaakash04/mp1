package com.smartparking.domain;

import com.smartparking.domain.enums.SpaceKind;
import com.smartparking.domain.enums.VehicleKind;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * A normal parking space that only accepts two-wheelers.
 */
@Entity
@DiscriminatorValue("TWO_WHEELER")
public class TwoWheelerSpace extends ParkingSpace {

    protected TwoWheelerSpace() {
        // Required by JPA.
    }

    public TwoWheelerSpace(String code, ParkingLot lot, int floorNumber) {
        super(code, lot, floorNumber);
    }

    @Override
    public boolean canAccommodate(Vehicle vehicle) {
        return vehicle != null && vehicle.getKind() == VehicleKind.TWO_WHEELER;
    }

    @Override
    public boolean requiresPermit() {
        return false;
    }

    @Override
    public SpaceKind getKind() {
        return SpaceKind.TWO_WHEELER;
    }
}
