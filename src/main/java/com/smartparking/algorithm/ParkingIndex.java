package com.smartparking.algorithm;

import com.smartparking.domain.ParkingSpace;
import com.smartparking.domain.Reservation;
import com.smartparking.domain.Vehicle;
import com.smartparking.domain.enums.ReservationStatus;
import com.smartparking.repository.ParkingSpaceRepository;
import com.smartparking.repository.ReservationRepository;
import com.smartparking.repository.VehicleRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory data-structure showcase used as a fast pre-check:
 *
 * <ul>
 *   <li>HashMap for O(1) lookup of spaces, vehicles and reservation intervals.</li>
 *   <li>HashMap&lt;Long, ArrayList&lt;ReservationInterval&gt;&gt; for intervals per space.</li>
 * </ul>
 *
 * <p>MySQL remains the source of truth. The index is rebuilt on startup and
 * updated after a transaction commits, so a rolled-back booking does not
 * leave a ghost interval behind.</p>
 */
@Component
public class ParkingIndex {

    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(ReservationStatus.RESERVED, ReservationStatus.CHECKED_IN);

    private final ParkingSpaceRepository spaceRepository;
    private final VehicleRepository vehicleRepository;
    private final ReservationRepository reservationRepository;

    private final Map<Long, ParkingSpace> spacesById = new HashMap<>();
    private final Map<String, Vehicle> vehiclesByNumber = new HashMap<>();
    private final Map<Long, ReservationInterval> reservationsById = new HashMap<>();
    private final Map<Long, List<ReservationInterval>> intervalsBySpaceId = new HashMap<>();

    public ParkingIndex(ParkingSpaceRepository spaceRepository,
                        VehicleRepository vehicleRepository,
                        ReservationRepository reservationRepository) {
        this.spaceRepository = spaceRepository;
        this.vehicleRepository = vehicleRepository;
        this.reservationRepository = reservationRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void rebuild() {
        spacesById.clear();
        vehiclesByNumber.clear();
        reservationsById.clear();
        intervalsBySpaceId.clear();

        spaceRepository.findAllWithLot().forEach(space -> spacesById.put(space.getId(), space));
        vehicleRepository.findAll().forEach(vehicle ->
                vehiclesByNumber.put(vehicle.getVehicleNumber().toUpperCase(), vehicle));
        reservationRepository.findWithDetailsByStatusIn(ACTIVE_STATUSES).forEach(reservation ->
                putInterval(reservation.getId(), reservation.getSpace().getId(),
                        reservation.getVehicle().getVehicleNumber(),
                        reservation.getStartTime(), reservation.getEndTime()));
    }

    public synchronized void indexVehicle(Vehicle vehicle) {
        vehiclesByNumber.put(vehicle.getVehicleNumber().toUpperCase(), vehicle);
    }

    public synchronized void indexSpace(ParkingSpace space) {
        spacesById.put(space.getId(), space);
    }

    /**
     * Adds the interval only after the surrounding transaction commits.
     */
    public void indexReservationAfterCommit(Reservation reservation) {
        Long reservationId = reservation.getId();
        Long spaceId = reservation.getSpace().getId();
        String vehicleNumber = reservation.getVehicle().getVehicleNumber();
        LocalDateTime start = reservation.getStartTime();
        LocalDateTime end = reservation.getEndTime();
        afterCommit(() -> {
            synchronized (ParkingIndex.this) {
                putInterval(reservationId, spaceId, vehicleNumber, start, end);
            }
        });
    }

    /**
     * Removes the interval only after the surrounding transaction commits.
     */
    public void removeReservationAfterCommit(Long reservationId) {
        afterCommit(() -> {
            synchronized (ParkingIndex.this) {
                removeInterval(reservationId);
            }
        });
    }

    public synchronized Optional<ParkingSpace> findSpace(Long id) {
        return Optional.ofNullable(spacesById.get(id));
    }

    public synchronized Optional<Vehicle> findVehicle(String vehicleNumber) {
        return Optional.ofNullable(vehiclesByNumber.get(vehicleNumber.toUpperCase()));
    }

    public synchronized Optional<ReservationInterval> findReservationInterval(Long reservationId) {
        return Optional.ofNullable(reservationsById.get(reservationId));
    }

    public synchronized List<ReservationInterval> intervalsForSpace(Long spaceId) {
        List<ReservationInterval> intervals = intervalsBySpaceId.get(spaceId);
        return intervals == null ? List.of() : List.copyOf(intervals);
    }

    /**
     * Fast in-memory overlap check. The database check inside the locked
     * transaction is still the final authority.
     */
    public synchronized boolean hasOverlap(Long spaceId, LocalDateTime start, LocalDateTime end) {
        List<ReservationInterval> intervals = intervalsBySpaceId.get(spaceId);
        if (intervals == null) {
            return false;
        }
        for (ReservationInterval interval : intervals) {
            if (interval.overlaps(start, end)) {
                return true;
            }
        }
        return false;
    }

    public synchronized int spaceCount() {
        return spacesById.size();
    }

    public synchronized int reservationIntervalCount() {
        return reservationsById.size();
    }

    private void putInterval(Long reservationId, Long spaceId, String vehicleNumber,
                             LocalDateTime start, LocalDateTime end) {
        removeInterval(reservationId);
        ReservationInterval interval =
                new ReservationInterval(reservationId, spaceId, vehicleNumber, start, end);
        reservationsById.put(reservationId, interval);
        intervalsBySpaceId
                .computeIfAbsent(spaceId, ignored -> new ArrayList<>())
                .add(interval);
    }

    private void removeInterval(Long reservationId) {
        ReservationInterval removed = reservationsById.remove(reservationId);
        if (removed == null) {
            return;
        }
        List<ReservationInterval> intervals = intervalsBySpaceId.get(removed.spaceId());
        if (intervals != null) {
            intervals.removeIf(interval -> interval.reservationId().equals(reservationId));
        }
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /**
     * Exposed for documentation/tests that need an unmodifiable snapshot.
     */
    public synchronized Map<Long, List<ReservationInterval>> snapshotIntervals() {
        Map<Long, List<ReservationInterval>> copy = new HashMap<>();
        intervalsBySpaceId.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(copy);
    }
}
