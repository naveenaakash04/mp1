package com.smartparking.service;

import com.smartparking.algorithm.ParkingIndex;
import com.smartparking.algorithm.SlotSelectionPolicy;
import com.smartparking.domain.ParkingSpace;
import com.smartparking.domain.Vehicle;
import com.smartparking.domain.enums.ReservationStatus;
import com.smartparking.domain.enums.SpaceKind;
import com.smartparking.exception.BusinessRuleException;
import com.smartparking.exception.NotFoundException;
import com.smartparking.repository.ParkingSpaceRepository;
import com.smartparking.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The "brain" of the allocation:
 *
 * <ol>
 *   <li>Load candidate spaces.</li>
 *   <li>Reject spaces that are incompatible with the vehicle (type/permit).</li>
 *   <li>Reject spaces with an overlapping reservation.</li>
 *   <li>Order the rest with the configured {@link SlotSelectionPolicy}
 *       (nearest lot using Dijkstra travel times).</li>
 * </ol>
 *
 * <p>The final lock + overlap re-check happens in {@link ReservationService},
 * because only there are we inside the booking transaction.</p>
 */
@Service
public class ParkingAllocationService {

    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(ReservationStatus.RESERVED, ReservationStatus.CHECKED_IN);

    private final ParkingSpaceRepository spaceRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingIndex parkingIndex;
    private final TravelTimeService travelTimeService;
    private final SlotSelectionPolicy selectionPolicy;

    public ParkingAllocationService(ParkingSpaceRepository spaceRepository,
                                    ReservationRepository reservationRepository,
                                    ParkingIndex parkingIndex,
                                    TravelTimeService travelTimeService,
                                    SlotSelectionPolicy selectionPolicy) {
        this.spaceRepository = spaceRepository;
        this.reservationRepository = reservationRepository;
        this.parkingIndex = parkingIndex;
        this.travelTimeService = travelTimeService;
        this.selectionPolicy = selectionPolicy;
    }

    @Transactional(readOnly = true)
    public List<ParkingSpace> rankEligibleSpaces(Vehicle vehicle,
                                                 LocalDateTime start,
                                                 LocalDateTime end,
                                                 Long preferredSpaceId) {
        Map<Long, Integer> travelMinutes = travelTimeService.minutesByLotId();

        if (preferredSpaceId != null) {
            ParkingSpace preferred = spaceRepository.findByIdWithLot(preferredSpaceId)
                    .orElseThrow(() -> new NotFoundException(
                            "Parking space " + preferredSpaceId + " was not found"));
            if (!preferred.canAccommodate(vehicle)) {
                throw new BusinessRuleException(incompatibilityMessage(preferred, vehicle));
            }
            return selectionPolicy.order(vehicle, List.of(preferred), travelMinutes);
        }

        List<ParkingSpace> eligible = new ArrayList<>();
        for (ParkingSpace space : spaceRepository.findAllWithLot()) {
            if (!space.canAccommodate(vehicle)) {
                continue;
            }
            if (parkingIndex.hasOverlap(space.getId(), start, end)) {
                continue;
            }
            long conflicts = reservationRepository.countOverlapping(
                    space.getId(), start, end, ACTIVE_STATUSES);
            if (conflicts == 0) {
                eligible.add(space);
            }
        }
        return selectionPolicy.order(vehicle, eligible, travelMinutes);
    }

    private String incompatibilityMessage(ParkingSpace space, Vehicle vehicle) {
        if (space.requiresPermit() && !vehicle.hasAccessibilityPermit()) {
            return "Space " + space.getCode() + " requires an accessibility permit";
        }
        if (space.getKind() == SpaceKind.CAR && vehicle.getKind() != com.smartparking.domain.enums.VehicleKind.CAR) {
            return "Space " + space.getCode() + " is reserved for cars";
        }
        if (space.getKind() == SpaceKind.TWO_WHEELER
                && vehicle.getKind() != com.smartparking.domain.enums.VehicleKind.TWO_WHEELER) {
            return "Space " + space.getCode() + " is reserved for two-wheelers";
        }
        return "Vehicle " + vehicle.getVehicleNumber() + " is not compatible with space " + space.getCode();
    }
}
