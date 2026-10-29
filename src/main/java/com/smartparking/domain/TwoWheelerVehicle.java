package com.smartparking.domain;

import com.smartparking.domain.enums.VehicleKind;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * A two-wheeler. Its kind is used by
 * {@link TwoWheelerSpace#canAccommodate(Vehicle)}.
 */
@Entity
@DiscriminatorValue("TWO_WHEELER")
public class TwoWheelerVehicle extends Vehicle {

    protected TwoWheelerVehicle() {
        // Required by JPA.
    }

    public TwoWheelerVehicle(String vehicleNumber, String ownerName, boolean accessibilityPermit) {
        super(vehicleNumber, ownerName, accessibilityPermit);
    }

    @Override
    public VehicleKind getKind() {
        return VehicleKind.TWO_WHEELER;
    }
}
