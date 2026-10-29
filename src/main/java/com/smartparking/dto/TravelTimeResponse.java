package com.smartparking.dto;

import java.util.List;

public record TravelTimeResponse(
        String source,
        List<LotTravelTime> lots
) {

    public record LotTravelTime(
            Long lotId,
            String lotCode,
            String lotName,
            int minutes
    ) {
    }
}
