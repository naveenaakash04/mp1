package com.smartparking.controller;

import com.smartparking.dto.DashboardResponse;
import com.smartparking.service.ParkingQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final ParkingQueryService queryService;

    public DashboardController(ParkingQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public DashboardResponse dashboard() {
        return queryService.dashboard();
    }
}
