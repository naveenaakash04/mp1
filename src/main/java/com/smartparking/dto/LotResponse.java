package com.smartparking.dto;

import java.util.List;

public record LotResponse(
        Long id,
        String code,
        String name,
        String address,
        int travelMinutes,
        int totalSpaces,
        int availableSpaces,
        int occupiedSpaces,
        int reservedSpaces,
        List<SpaceResponse> spaces
) {
}
