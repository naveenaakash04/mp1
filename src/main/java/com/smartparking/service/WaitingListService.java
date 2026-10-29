package com.smartparking.service;

import com.smartparking.algorithm.WaitingTicket;
import com.smartparking.domain.Vehicle;
import com.smartparking.domain.WaitingRequest;
import com.smartparking.domain.enums.EventType;
import com.smartparking.domain.enums.WaitingStatus;
import com.smartparking.dto.WaitingRequestCreate;
import com.smartparking.dto.WaitingResponse;
import com.smartparking.exception.BusinessRuleException;
import com.smartparking.exception.ConflictException;
import com.smartparking.exception.NotFoundException;
import com.smartparking.repository.VehicleRepository;
import com.smartparking.repository.WaitingRequestRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * In-memory waiting list backed by a Java PriorityQueue and a HashMap index.
 *
 * <p>Ordering: permit holders first, then earlier arrival time, then id.
 * MySQL stores the durable request; the queue is rebuilt from MySQL on
 * startup and whenever priorities change.</p>
 */
@Service
public class WaitingListService {

    private final WaitingRequestRepository waitingRequestRepository;
    private final VehicleRepository vehicleRepository;
    private final ReservationWindowValidator windowValidator;
    private final ParkingEventService eventService;
    private final ClockService clockService;

    private final PriorityQueue<WaitingTicket> queue =
            new PriorityQueue<>(WaitingTicket.PRIORITY);
    private final Map<Long, WaitingTicket> ticketsById = new HashMap<>();

    public WaitingListService(WaitingRequestRepository waitingRequestRepository,
                              VehicleRepository vehicleRepository,
                              ReservationWindowValidator windowValidator,
                              ParkingEventService eventService,
                              ClockService clockService) {
        this.waitingRequestRepository = waitingRequestRepository;
        this.vehicleRepository = vehicleRepository;
        this.windowValidator = windowValidator;
        this.eventService = eventService;
        this.clockService = clockService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadQueue() {
        List<WaitingRequest> waiting =
                waitingRequestRepository.findByStatusWithVehicle(WaitingStatus.WAITING);
        rebuildQueue(waiting);
    }

    @Transactional
    public WaitingResponse enqueue(WaitingRequestCreate request) {
        windowValidator.validate(request.startTime(), request.endTime());

        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new NotFoundException(
                        "Vehicle " + request.vehicleId() + " was not found"));

        long duplicate = waitingRequestRepository.countDuplicateWaiting(
                vehicle.getId(), request.startTime(), request.endTime(), WaitingStatus.WAITING);
        if (duplicate > 0) {
            throw new ConflictException("This vehicle already has a waiting request for that time window");
        }

        LocalDateTime now = clockService.now();
        int accessPriority = vehicle.hasAccessibilityPermit() ? 1 : 0;
        WaitingRequest waitingRequest = new WaitingRequest(
                vehicle,
                request.startTime(),
                request.endTime(),
                accessPriority,
                now
        );
        WaitingRequest saved = waitingRequestRepository.save(waitingRequest);
        WaitingTicket ticket = toTicket(saved);

        synchronized (this) {
            queue.add(ticket);
            ticketsById.put(ticket.id(), ticket);
        }

        eventService.recordWaiting(EventType.WAITING_JOINED, saved,
                "Joined waiting list with priority " + accessPriority);
        return toResponse(ticket, positionOf(ticket.id()), WaitingStatus.WAITING, null);
    }

    @Transactional(readOnly = true)
    public List<WaitingResponse> list() {
        List<WaitingTicket> ordered = orderedTickets();
        List<WaitingResponse> result = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            WaitingTicket ticket = ordered.get(i);
            result.add(toResponse(ticket, i + 1, WaitingStatus.WAITING, null));
        }
        return result;
    }

    @Transactional
    public WaitingResponse cancel(Long id) {
        WaitingRequest waitingRequest = waitingRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Waiting request " + id + " was not found"));

        if (waitingRequest.getStatus() != WaitingStatus.WAITING) {
            throw new BusinessRuleException("Only WAITING requests can be cancelled");
        }

        waitingRequest.setStatus(WaitingStatus.CANCELLED);
        waitingRequest.touch(clockService.now());
        waitingRequestRepository.save(waitingRequest);
        removeFromQueue(id);

        eventService.recordWaiting(EventType.WAITING_CANCELLED, waitingRequest,
                "Waiting request cancelled");

        return new WaitingResponse(
                waitingRequest.getId(),
                0,
                waitingRequest.getVehicle().getId(),
                waitingRequest.getVehicle().getVehicleNumber(),
                waitingRequest.getVehicle().getKind(),
                waitingRequest.getVehicle().hasAccessibilityPermit(),
                waitingRequest.getAccessPriority(),
                waitingRequest.getArrivalTime(),
                waitingRequest.getRequestedStartTime(),
                waitingRequest.getRequestedEndTime(),
                waitingRequest.getStatus(),
                waitingRequest.getAllocatedReservationId()
        );
    }

    /**
     * Removes and reinserts every queued request. Called when a permit changes,
     * because access priority (and therefore heap order) may change.
     */
    @Transactional
    public void refreshRanking() {
        List<WaitingRequest> waiting =
                waitingRequestRepository.findByStatusWithVehicle(WaitingStatus.WAITING);
        LocalDateTime now = clockService.now();
        for (WaitingRequest request : waiting) {
            int newPriority = request.getVehicle().hasAccessibilityPermit() ? 1 : 0;
            if (newPriority != request.getAccessPriority()) {
                request.setAccessPriority(newPriority);
                request.touch(now);
                waitingRequestRepository.save(request);
            }
        }
        rebuildQueue(waiting);
    }

    public synchronized void removeFromQueue(Long id) {
        ticketsById.remove(id);
        queue.removeIf(ticket -> ticket.id().equals(id));
    }

    public synchronized List<WaitingTicket> orderedTickets() {
        PriorityQueue<WaitingTicket> copy = new PriorityQueue<>(queue);
        List<WaitingTicket> ordered = new ArrayList<>();
        while (!copy.isEmpty()) {
            ordered.add(copy.poll());
        }
        return ordered;
    }

    public synchronized List<Long> orderedIds() {
        return orderedTickets().stream().map(WaitingTicket::id).toList();
    }

    @Transactional(readOnly = true)
    public long countWaiting() {
        return waitingRequestRepository.countByStatus(WaitingStatus.WAITING);
    }

    public synchronized int queueSize() {
        return queue.size();
    }

    private synchronized void rebuildQueue(List<WaitingRequest> waiting) {
        queue.clear();
        ticketsById.clear();
        for (WaitingRequest request : waiting) {
            WaitingTicket ticket = toTicket(request);
            queue.add(ticket);
            ticketsById.put(ticket.id(), ticket);
        }
    }

    private synchronized int positionOf(Long id) {
        List<WaitingTicket> ordered = orderedTickets();
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id().equals(id)) {
                return i + 1;
            }
        }
        return 0;
    }

    private WaitingTicket toTicket(WaitingRequest request) {
        Vehicle vehicle = request.getVehicle();
        return new WaitingTicket(
                request.getId(),
                vehicle.getId(),
                vehicle.getVehicleNumber(),
                vehicle.getKind(),
                vehicle.hasAccessibilityPermit(),
                request.getAccessPriority(),
                request.getArrivalTime(),
                request.getRequestedStartTime(),
                request.getRequestedEndTime()
        );
    }

    private WaitingResponse toResponse(WaitingTicket ticket, int position,
                                       WaitingStatus status, Long allocatedReservationId) {
        return new WaitingResponse(
                ticket.id(),
                position,
                ticket.vehicleId(),
                ticket.vehicleNumber(),
                ticket.vehicleKind(),
                ticket.accessibilityPermit(),
                ticket.accessPriority(),
                ticket.arrivalTime(),
                ticket.requestedStartTime(),
                ticket.requestedEndTime(),
                status,
                allocatedReservationId
        );
    }
}
