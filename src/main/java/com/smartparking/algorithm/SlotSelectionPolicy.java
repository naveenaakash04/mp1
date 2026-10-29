package com.smartparking.algorithm;

import com.smartparking.domain.ParkingSpace;
import com.smartparking.domain.Vehicle;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Abstraction for "which eligible space should we try first?".
 *
 * <p>The service filters out incompatible/occupied spaces; a policy only
 * orders the remaining candidates. Adding a new strategy (for example
 * "prefer ground floor") only requires a new subclass.</p>
 */
public abstract class SlotSelectionPolicy {

    public List<ParkingSpace> order(Vehicle vehicle,
                                    List<ParkingSpace> candidates,
                                    Map<Long, Integer> travelMinutesByLotId) {
        return candidates.stream()
                .filter(space -> space.canAccommodate(vehicle))
                .sorted(comparator(travelMinutesByLotId))
                .toList();
    }

    protected abstract Comparator<ParkingSpace> comparator(Map<Long, Integer> travelMinutesByLotId);
}
