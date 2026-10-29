package com.smartparking.controller;

import com.smartparking.dto.ClockAdvanceRequest;
import com.smartparking.dto.ClockResponse;
import com.smartparking.dto.TravelTimeResponse;
import com.smartparking.service.ClockService;
import com.smartparking.service.ParkingQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final ClockService clockService;
    private final ParkingQueryService queryService;

    public SystemController(ClockService clockService, ParkingQueryService queryService) {
        this.clockService = clockService;
        this.queryService = queryService;
    }

    @GetMapping("/clock")
    public ClockResponse clock() {
        return new ClockResponse(
                clockService.now(),
                clockService.realNow(),
                clockService.getOffsetMinutes()
        );
    }

    @PostMapping("/clock/advance")
    public ClockResponse advance(@Valid @RequestBody ClockAdvanceRequest request) {
        clockService.advance(request.minutes());
        return clock();
    }

    @PostMapping("/clock/reset")
    public ClockResponse reset() {
        clockService.reset();
        return clock();
    }

    @GetMapping("/travel-times")
    public TravelTimeResponse travelTimes() {
        return queryService.travelTimes();
    }
}
