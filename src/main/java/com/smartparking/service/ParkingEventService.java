package com.smartparking.service;

import com.smartparking.domain.ParkingEvent;
import com.smartparking.domain.Reservation;
import com.smartparking.domain.WaitingRequest;
import com.smartparking.domain.enums.EventType;
import com.smartparking.dto.EventResponse;
import com.smartparking.repository.ParkingEventRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the audit trail to MySQL and keeps an ArrayList of the most recent
 * 200 events in memory (the ArrayList requirement for occupancy history).
 */
@Service
public class ParkingEventService {

    private static final int MAX_CACHE_SIZE = 200;

    private final ParkingEventRepository eventRepository;
    private final ClockService clockService;

    private final List<ParkingEvent> recentEvents = new ArrayList<>();

    public ParkingEventService(ParkingEventRepository eventRepository, ClockService clockService) {
        this.eventRepository = eventRepository;
        this.clockService = clockService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void loadRecentEvents() {
        recentEvents.clear();
        recentEvents.addAll(eventRepository.findTop200ByOrderByOccurredAtDesc());
    }

    @Transactional
    public void recordReservation(EventType type, Reservation reservation,
                                  String message, BigDecimal fee) {
        ParkingEvent event = new ParkingEvent(
                type,
                reservation.getId(),
                reservation.getVehicle().getVehicleNumber(),
                reservation.getSpace().getCode(),
                reservation.getSpace().getLot().getCode(),
                clockService.now(),
                message,
                fee
        );
        saveAndCache(event);
    }

    @Transactional
    public void recordWaiting(EventType type, WaitingRequest waitingRequest, String message) {
        ParkingEvent event = new ParkingEvent(
                type,
                waitingRequest.getAllocatedReservationId(),
                waitingRequest.getVehicle().getVehicleNumber(),
                null,
                null,
                clockService.now(),
                message,
                null
        );
        saveAndCache(event);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> list(int limit, String query) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<ParkingEvent> events;
        if (query == null || query.isBlank()) {
            events = eventRepository.findTop200ByOrderByOccurredAtDesc();
        } else {
            events = eventRepository.search(query.trim());
        }
        return events.stream()
                .limit(safeLimit)
                .map(EventResponse::from)
                .toList();
    }

    private void saveAndCache(ParkingEvent event) {
        ParkingEvent saved = eventRepository.save(event);
        synchronized (this) {
            recentEvents.add(0, saved);
            while (recentEvents.size() > MAX_CACHE_SIZE) {
                recentEvents.remove(recentEvents.size() - 1);
            }
        }
    }

    /**
     * Used by the demo/documentation to show the in-memory history list.
     */
    public synchronized List<EventResponse> cachedHistory() {
        return recentEvents.stream().map(EventResponse::from).toList();
    }

    public LocalDateTime now() {
        return clockService.now();
    }
}
