package com.smartparking.algorithm;

import com.smartparking.domain.enums.VehicleKind;

import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * Lightweight item stored in the waiting-list {@link java.util.PriorityQueue}.
 *
 * <p>We store a ticket instead of a JPA entity so the queue can be used
 * outside a database transaction without lazy-loading problems.</p>
 *
 * <p>Ranking: accessibility permit first (higher priority), then earlier
 * arrival time, then lower id for a deterministic tie-break.</p>
 */
public record WaitingTicket(
        Long id,
        Long vehicleId,
        String vehicleNumber,
        VehicleKind vehicleKind,
        boolean accessibilityPermit,
        int accessPriority,
        LocalDateTime arrivalTime,
        LocalDateTime requestedStartTime,
        LocalDateTime requestedEndTime
) {

    public static final Comparator<WaitingTicket> PRIORITY =
            Comparator.comparingInt(WaitingTicket::accessPriority).reversed()
                    .thenComparing(WaitingTicket::arrivalTime)
                    .thenComparing(WaitingTicket::id);
}
