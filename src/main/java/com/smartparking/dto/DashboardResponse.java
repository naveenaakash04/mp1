package com.smartparking.dto;

import java.time.LocalDateTime;

public record DashboardResponse(
        int totalSpaces,
        int availableSpaces,
        int occupiedSpaces,
        int reservedSpaces,
        int totalLots,
        long activeReservations,
        long waitingRequests,
        LocalDateTime simulatedNow,
        long clockOffsetMinutes
) {
}
