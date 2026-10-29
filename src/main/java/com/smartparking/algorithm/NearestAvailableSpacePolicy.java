package com.smartparking.algorithm;

import com.smartparking.domain.ParkingSpace;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;

/**
 * Default policy: nearest lot by Dijkstra travel time, then lowest floor,
 * then space code. This is the "smart" part of the allocation.
 */
@Component
public class NearestAvailableSpacePolicy extends SlotSelectionPolicy {

    private static final int UNREACHABLE_MINUTES = 9999;

    @Override
    protected Comparator<ParkingSpace> comparator(Map<Long, Integer> travelMinutesByLotId) {
        return Comparator
                .comparingInt((ParkingSpace space) ->
                        travelMinutesByLotId.getOrDefault(space.getLot().getId(), UNREACHABLE_MINUTES))
                .thenComparingInt(ParkingSpace::getFloorNumber)
                .thenComparing(ParkingSpace::getCode);
    }
}
