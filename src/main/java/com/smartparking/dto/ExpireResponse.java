package com.smartparking.dto;

public record ExpireResponse(
        int expiredCount,
        int allocatedFromWaitingList
) {
}
