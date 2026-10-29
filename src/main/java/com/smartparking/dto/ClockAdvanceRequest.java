package com.smartparking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ClockAdvanceRequest(
        @Min(value = 1, message = "Advance at least 1 minute")
        @Max(value = 1440, message = "Advance at most 1440 minutes at a time")
        long minutes
) {
}
