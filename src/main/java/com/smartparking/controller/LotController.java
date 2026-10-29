package com.smartparking.controller;

import com.smartparking.domain.enums.SpaceKind;
import com.smartparking.domain.enums.SpaceStatus;
import com.smartparking.dto.LotResponse;
import com.smartparking.dto.SpaceResponse;
import com.smartparking.dto.TravelTimeResponse;
import com.smartparking.service.ParkingQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lots")
public class LotController {

    private final ParkingQueryService queryService;

    public LotController(ParkingQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public List<LotResponse> lots() {
        return queryService.lots();
    }

    @GetMapping("/{id}/spaces")
    public List<SpaceResponse> spaces(@PathVariable Long id,
                                      @RequestParam(required = false) SpaceKind kind,
                                      @RequestParam(required = false) SpaceStatus status,
                                      @RequestParam(required = false) String q) {
        return queryService.spaces(id, kind, status, q);
    }

    @GetMapping("/travel-times")
    public TravelTimeResponse travelTimes() {
        return queryService.travelTimes();
    }
}
