package com.smartparking.controller;

import com.smartparking.domain.enums.SpaceKind;
import com.smartparking.domain.enums.SpaceStatus;
import com.smartparking.dto.SpaceResponse;
import com.smartparking.service.ParkingQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/spaces")
public class SpaceController {

    private final ParkingQueryService queryService;

    public SpaceController(ParkingQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public List<SpaceResponse> spaces(@RequestParam(required = false) Long lotId,
                                      @RequestParam(required = false) SpaceKind kind,
                                      @RequestParam(required = false) SpaceStatus status,
                                      @RequestParam(required = false) String q) {
        return queryService.spaces(lotId, kind, status, q);
    }

    @GetMapping("/{id}")
    public SpaceResponse space(@PathVariable Long id) {
        return queryService.space(id);
    }
}
