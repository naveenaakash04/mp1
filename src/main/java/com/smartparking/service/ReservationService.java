package com.smartparking.service;

import com.smartparking.algorithm.ParkingIndex;
import com.smartparking.domain.ParkingSpace;
import com.smartparking.domain.Reservation;
import com.smartparking.domain.Vehicle;
import com.smartparking.domain.WaitingRequest;
import com.smartparking.domain.enums.EventType;
import com.smartparking.domain.enums.ReservationStatus;
import com.smartparking.domain.enums.WaitingStatus;
import com.smartparking.dto.ReservationRequest;
import com.smartparking.dto.ReservationResponse;
import com.smartparking.event.ReservationReleasedEvent;
import com.smartparking.exception.BusinessRuleException;
import com.smartparking.exception.ConflictException;
import com.smartparking.exception.NotFoundException;
import com.smartparking.repository.ParkingSpaceRepository;
import com.smartparking.repository.ReservationRepository;
import com.smartparking.repository.VehicleRepository;
import com.smartparking.repository.WaitingRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * All reservation state changes live here so the HTTP controllers stay thin.
 *
 * <p>Booking transaction:</p>
 * <ol>
 *   <li>Lock the vehicle row (prevents two bookings for the same vehicle).</li>
 *   <li>Rank compatible spaces.</li>
 *   <li>Lock the chosen space row with SELECT ... FOR UPDATE.</li>
 *   <li>Re-check overlap using READ_COMMITTED so committed bookings are visible.</li>
 *   <li>Insert the reservation, write history, commit.</li>
 * </ol>
 */
@Service
public class ReservationService {

    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(ReservationStatus.RESERVED, ReservationStatus.CHECKED_IN);

    private final ReservationRepository reservationRepository;
    private final ParkingSpaceRepository spaceRepository;
    private final VehicleRepository vehicleRepository;
    private final WaitingRequestRepository waitingRequestRepository;
    private final ParkingAllocationService allocationService;
    private final WaitingListService waitingListService;
    private final ParkingEventService eventService;
    private final ParkingFeeService feeService;
    private final ParkingIndex parkingIndex;
    private final ReservationWindowValidator windowValidator;
    private final ClockService clockService;
    private final ApplicationEventPublisher eventPublisher;
    private final long graceMinutes;

    public ReservationService(ReservationRepository reservationRepository,
                              ParkingSpaceRepository spaceRepository,
                              VehicleRepository vehicleRepository,
                              WaitingRequestRepository waitingRequestRepository,
                              ParkingAllocationService allocationService,
                              WaitingListService waitingListService,
                              ParkingEventService eventService,
                              ParkingFeeService feeService,
                              ParkingIndex parkingIndex,
                              ReservationWindowValidator windowValidator,
                              ClockService clockService,
                              ApplicationEventPublisher eventPublisher,
                              @Value("${parking.reservation.grace-minutes:15}") long graceMinutes) {
        this.reservationRepository = reservationRepository;
        this.spaceRepository = spaceRepository;
        this.vehicleRepository = vehicleRepository;
        this.waitingRequestRepository = waitingRequestRepository;
        this.allocationService = allocationService;
        this.waitingListService = waitingListService;
        this.eventService = eventService;
        this.feeService = feeService;
        this.parkingIndex = parkingIndex;
        this.windowValidator = windowValidator;
        this.clockService = clockService;
        this.eventPublisher = eventPublisher;
        this.graceMinutes = graceMinutes;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResponse create(ReservationRequest request) {
        windowValidator.validate(request.startTime(), request.endTime());

        Vehicle vehicle = vehicleRepository.findByIdForUpdate(request.vehicleId())
                .orElseThrow(() -> new NotFoundException(
                        "Vehicle " + request.vehicleId() + " was not found"));

        ensureVehicleHasNoOverlap(vehicle.getId(), request.startTime(), request.endTime());
        Reservation reservation = allocateAndSave(
                vehicle, request.startTime(), request.endTime(), request.preferredSpaceId());
        return ReservationResponse.from(reservation);
    }

    /**
     * Called by {@link WaitingListProcessor} for each queued request.
     * REQUIRES_NEW gives every attempt its own transaction, so one failed
     * attempt does not roll back the successful ones.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED)
    public Optional<ReservationResponse> tryAllocateWaitingRequest(Long waitingRequestId) {
        WaitingRequest waitingRequest = waitingRequestRepository.findByIdForUpdate(waitingRequestId)
                .orElse(null);
        if (waitingRequest == null || waitingRequest.getStatus() != WaitingStatus.WAITING) {
            return Optional.empty();
        }

        LocalDateTime now = clockService.now();
        if (waitingRequest.getRequestedEndTime().isBefore(now)
                || waitingRequest.getRequestedStartTime().isBefore(now.minusMinutes(5))) {
            waitingRequest.setStatus(WaitingStatus.EXPIRED);
            waitingRequest.touch(now);
            waitingRequestRepository.save(waitingRequest);
            waitingListService.removeFromQueue(waitingRequestId);
            eventService.recordWaiting(EventType.WAITING_EXPIRED, waitingRequest,
                    "Waiting request expired before a space became available");
            return Optional.empty();
        }

        Vehicle vehicle = vehicleRepository.findByIdForUpdate(waitingRequest.getVehicle().getId())
                .orElseThrow(() -> new NotFoundException("Waiting request vehicle was not found"));

        try {
            Reservation reservation = allocateAndSave(
                    vehicle,
                    waitingRequest.getRequestedStartTime(),
                    waitingRequest.getRequestedEndTime(),
                    null
            );
            waitingRequest.setStatus(WaitingStatus.ALLOCATED);
            waitingRequest.setAllocatedReservationId(reservation.getId());
            waitingRequest.touch(now);
            waitingRequestRepository.save(waitingRequest);
            waitingListService.removeFromQueue(waitingRequestId);
            eventService.recordWaiting(EventType.WAITING_ALLOCATED, waitingRequest,
                    "Waiting request allocated to " + reservation.getSpace().getCode());
            return Optional.of(ReservationResponse.from(reservation));
        } catch (ConflictException | BusinessRuleException ex) {
            // Still no compatible space: keep the request in the queue.
            return Optional.empty();
        }
    }

    @Transactional
    public ReservationResponse cancel(Long id) {
        Reservation reservation = lockReservation(id);
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new BusinessRuleException(
                    "Only RESERVED reservations can be cancelled. Checked-in reservations must be checked out.");
        }

        LocalDateTime now = clockService.now();
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.touch(now);
        reservationRepository.save(reservation);

        parkingIndex.removeReservationAfterCommit(id);
        eventService.recordReservation(EventType.CANCELLED, reservation,
                "Reservation cancelled by user", null);
        eventPublisher.publishEvent(new ReservationReleasedEvent(
                reservation.getId(), reservation.getSpace().getId()));

        return ReservationResponse.from(reservation);
    }

    @Transactional
    public ReservationResponse checkIn(Long id) {
        Reservation reservation = lockReservation(id);
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new BusinessRuleException("Only RESERVED reservations can be checked in");
        }

        LocalDateTime now = clockService.now();
        if (now.isAfter(reservation.getEndTime())) {
            throw new BusinessRuleException("The reservation window has already ended");
        }
        if (now.isBefore(reservation.getStartTime().minusMinutes(30))) {
            throw new BusinessRuleException("Check-in opens 30 minutes before the reservation starts");
        }

        reservation.setStatus(ReservationStatus.CHECKED_IN);
        reservation.setCheckedInAt(now);
        reservation.touch(now);
        reservationRepository.save(reservation);

        eventService.recordReservation(EventType.CHECKED_IN, reservation,
                "Vehicle checked in at " + reservation.getSpace().getCode(), null);
        return ReservationResponse.from(reservation);
    }

    @Transactional
    public ReservationResponse checkOut(Long id) {
        Reservation reservation = lockReservation(id);
        if (reservation.getStatus() != ReservationStatus.CHECKED_IN) {
            throw new BusinessRuleException("Only CHECKED_IN reservations can be checked out");
        }

        LocalDateTime now = clockService.now();
        LocalDateTime entry = reservation.getCheckedInAt() != null
                ? reservation.getCheckedInAt()
                : reservation.getStartTime();
        BigDecimal fee = feeService.calculateFee(
                reservation.getVehicle().getKind(), entry, now);

        reservation.setStatus(ReservationStatus.COMPLETED);
        reservation.setCheckedOutAt(now);
        reservation.setFee(fee);
        reservation.touch(now);
        reservationRepository.save(reservation);

        parkingIndex.removeReservationAfterCommit(id);
        eventService.recordReservation(EventType.CHECKED_OUT, reservation,
                "Checked out from " + reservation.getSpace().getCode(), fee);
        eventPublisher.publishEvent(new ReservationReleasedEvent(
                reservation.getId(), reservation.getSpace().getId()));

        return ReservationResponse.from(reservation);
    }

    /**
     * Expires unclaimed reservations whose start time plus grace period has
     * passed. Also called by the scheduler and by the demo endpoint.
     */
    @Transactional
    public int expireOverdueReservations() {
        LocalDateTime now = clockService.now();
        LocalDateTime cutoff = now.minusMinutes(graceMinutes);
        List<Reservation> overdue = reservationRepository
                .findByStatusAndStartTimeBeforeOrderByStartTimeAsc(ReservationStatus.RESERVED, cutoff);

        int expired = 0;
        for (Reservation reservation : overdue) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservation.touch(now);
            reservationRepository.save(reservation);
            parkingIndex.removeReservationAfterCommit(reservation.getId());
            eventService.recordReservation(EventType.EXPIRED, reservation,
                    "Reservation expired because it was not checked in", null);
            eventPublisher.publishEvent(new ReservationReleasedEvent(
                    reservation.getId(), reservation.getSpace().getId()));
            expired++;
        }
        return expired;
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(Long id) {
        Reservation reservation = reservationRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Reservation " + id + " was not found"));
        return ReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> list(ReservationStatus status, String vehicleNumber,
                                          Long lotId, String query) {
        List<Reservation> reservations = reservationRepository.findAllWithDetails();
        String vehicleFilter = vehicleNumber == null ? null : vehicleNumber.trim().toUpperCase();
        String textFilter = query == null ? null : query.trim().toLowerCase();

        return reservations.stream()
                .filter(reservation -> status == null || reservation.getStatus() == status)
                .filter(reservation -> vehicleFilter == null || vehicleFilter.isBlank()
                        || reservation.getVehicle().getVehicleNumber().contains(vehicleFilter))
                .filter(reservation -> lotId == null
                        || reservation.getSpace().getLot().getId().equals(lotId))
                .filter(reservation -> textFilter == null || textFilter.isBlank()
                        || reservation.getReservationCode().toLowerCase().contains(textFilter)
                        || reservation.getVehicle().getVehicleNumber().toLowerCase().contains(textFilter)
                        || reservation.getSpace().getCode().toLowerCase().contains(textFilter)
                        || reservation.getSpace().getLot().getCode().toLowerCase().contains(textFilter))
                .map(ReservationResponse::from)
                .toList();
    }

    private Reservation allocateAndSave(Vehicle vehicle, LocalDateTime start,
                                        LocalDateTime end, Long preferredSpaceId) {
        List<ParkingSpace> candidates =
                allocationService.rankEligibleSpaces(vehicle, start, end, preferredSpaceId);

        if (candidates.isEmpty()) {
            throw new ConflictException(
                    "No compatible parking space is available for the requested time window");
        }

        for (ParkingSpace candidate : candidates) {
            ParkingSpace lockedSpace = spaceRepository.findByIdForUpdate(candidate.getId())
                    .orElseThrow(() -> new NotFoundException(
                            "Parking space " + candidate.getId() + " was not found"));

            if (!lockedSpace.canAccommodate(vehicle)) {
                continue;
            }

            // Re-check inside the lock: this is the authoritative conflict check.
            long spaceConflicts = reservationRepository.countOverlapping(
                    lockedSpace.getId(), start, end, ACTIVE_STATUSES);
            if (spaceConflicts > 0) {
                continue;
            }

            ensureVehicleHasNoOverlap(vehicle.getId(), start, end);

            Reservation reservation = new Reservation(
                    generateCode(),
                    vehicle,
                    lockedSpace,
                    start,
                    end,
                    ReservationStatus.RESERVED,
                    clockService.now()
            );
            reservationRepository.save(reservation);
            parkingIndex.indexReservationAfterCommit(reservation);
            eventService.recordReservation(EventType.RESERVED, reservation,
                    "Reserved " + lockedSpace.getCode() + " in " + lockedSpace.getLot().getCode(), null);
            return reservation;
        }

        throw new ConflictException(
                "All compatible spaces were taken by another request. Please try again.");
    }

    private void ensureVehicleHasNoOverlap(Long vehicleId, LocalDateTime start, LocalDateTime end) {
        long vehicleConflicts = reservationRepository.countOverlappingForVehicle(
                vehicleId, start, end, ACTIVE_STATUSES);
        if (vehicleConflicts > 0) {
            throw new ConflictException("This vehicle already has an overlapping reservation");
        }
    }

    private Reservation lockReservation(Long id) {
        return reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Reservation " + id + " was not found"));
    }

    private String generateCode() {
        return "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
