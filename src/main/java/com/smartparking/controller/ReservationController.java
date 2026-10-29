package com.smartparking.controller;

import com.smartparking.domain.enums.ReservationStatus;
import com.smartparking.dto.ExpireResponse;
import com.smartparking.dto.ReservationRequest;
import com.smartparking.dto.ReservationResponse;
import com.smartparking.service.ReservationService;
import com.smartparking.service.WaitingListProcessor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final WaitingListProcessor waitingListProcessor;

    public ReservationController(ReservationService reservationService,
                                 WaitingListProcessor waitingListProcessor) {
        this.reservationService = reservationService;
        this.waitingListProcessor = waitingListProcessor;
    }

    @GetMapping
    public List<ReservationResponse> list(@RequestParam(required = false) ReservationStatus status,
                                          @RequestParam(required = false) String vehicleNumber,
                                          @RequestParam(required = false) Long lotId,
                                          @RequestParam(required = false) String q) {
        return reservationService.list(status, vehicleNumber, lotId, q);
    }

    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable Long id) {
        return reservationService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@Valid @RequestBody ReservationRequest request) {
        return reservationService.create(request);
    }

    @PostMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable Long id) {
        return reservationService.cancel(id);
    }

    @PostMapping("/{id}/check-in")
    public ReservationResponse checkIn(@PathVariable Long id) {
        return reservationService.checkIn(id);
    }

    @PostMapping("/{id}/check-out")
    public ReservationResponse checkOut(@PathVariable Long id) {
        return reservationService.checkOut(id);
    }

    /**
     * Demo/admin endpoint: expire unclaimed reservations immediately, then
     * give the waiting list a chance to use the released spaces.
     */
    @PostMapping("/expire")
    public ExpireResponse expire() {
        int expired = reservationService.expireOverdueReservations();
        int allocated = waitingListProcessor.processWaitingList();
        return new ExpireResponse(expired, allocated);
    }
}
