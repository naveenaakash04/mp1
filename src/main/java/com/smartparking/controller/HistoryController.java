package com.smartparking.controller;

import com.smartparking.dto.EventResponse;
import com.smartparking.service.ParkingEventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final ParkingEventService eventService;

    public HistoryController(ParkingEventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public List<EventResponse> history(@RequestParam(defaultValue = "50") int limit,
                                       @RequestParam(required = false) String q) {
        return eventService.list(limit, q);
    }
}
