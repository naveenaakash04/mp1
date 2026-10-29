package com.smartparking.domain;

import com.smartparking.domain.enums.VehicleKind;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * A car. Its kind is used by {@link CarSpace#canAccommodate(Vehicle)}.
 */
@Entity
@DiscriminatorValue("CAR")
public class CarVehicle extends Vehicle {

    protected CarVehicle() {
        // Required by JPA.
    }

    public CarVehicle(String vehicleNumber, String ownerName, boolean accessibilityPermit) {
        super(vehicleNumber, ownerName, accessibilityPermit);
    }

    @Override
    public VehicleKind getKind() {
        return VehicleKind.CAR;
    }
}
