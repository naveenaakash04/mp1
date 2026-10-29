package com.smartparking.domain;

import com.smartparking.domain.enums.SpaceKind;
import com.smartparking.domain.enums.VehicleKind;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * A normal parking space that only accepts cars.
 */
@Entity
@DiscriminatorValue("CAR")
public class CarSpace extends ParkingSpace {

    protected CarSpace() {
        // Required by JPA.
    }

    public CarSpace(String code, ParkingLot lot, int floorNumber) {
        super(code, lot, floorNumber);
    }

    @Override
    public boolean canAccommodate(Vehicle vehicle) {
        return vehicle != null && vehicle.getKind() == VehicleKind.CAR;
    }

    @Override
    public boolean requiresPermit() {
        return false;
    }

    @Override
    public SpaceKind getKind() {
        return SpaceKind.CAR;
    }
}
