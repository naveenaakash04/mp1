package com.smartparking.service;

import com.smartparking.domain.ParkingLot;
import com.smartparking.domain.ParkingSpace;
import com.smartparking.domain.Reservation;
import com.smartparking.domain.enums.ReservationStatus;
import com.smartparking.domain.enums.SpaceKind;
import com.smartparking.domain.enums.SpaceStatus;
import com.smartparking.domain.enums.WaitingStatus;
import com.smartparking.dto.DashboardResponse;
import com.smartparking.dto.LotResponse;
import com.smartparking.dto.SpaceResponse;
import com.smartparking.dto.TravelTimeResponse;
import com.smartparking.exception.NotFoundException;
import com.smartparking.repository.ParkingLotRepository;
import com.smartparking.repository.ParkingSpaceRepository;
import com.smartparking.repository.ReservationRepository;
import com.smartparking.repository.WaitingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only queries used by the dashboard, lot overview and space grid.
 *
 * <p>Space status is derived from current reservations:
 * CHECKED_IN -> OCCUPIED, active RESERVED -> RESERVED, otherwise AVAILABLE.
 * This avoids storing a second copy of the truth in the space table.</p>
 */
@Service
public class ParkingQueryService {

    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(ReservationStatus.RESERVED, ReservationStatus.CHECKED_IN);
    private static final int UNREACHABLE = 9999;

    private final ParkingLotRepository lotRepository;
    private final ParkingSpaceRepository spaceRepository;
    private final ReservationRepository reservationRepository;
    private final WaitingRequestRepository waitingRequestRepository;
    private final TravelTimeService travelTimeService;
    private final ClockService clockService;

    public ParkingQueryService(ParkingLotRepository lotRepository,
                               ParkingSpaceRepository spaceRepository,
                               ReservationRepository reservationRepository,
                               WaitingRequestRepository waitingRequestRepository,
                               TravelTimeService travelTimeService,
                               ClockService clockService) {
        this.lotRepository = lotRepository;
        this.spaceRepository = spaceRepository;
        this.reservationRepository = reservationRepository;
        this.waitingRequestRepository = waitingRequestRepository;
        this.travelTimeService = travelTimeService;
        this.clockService = clockService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        LocalDateTime now = clockService.now();
        List<ParkingSpace> spaces = spaceRepository.findAllWithLot();
        List<Reservation> active = reservationRepository.findWithDetailsByStatusIn(ACTIVE_STATUSES);
        Map<Long, Reservation> current = currentBySpace(active, now);

        int available = 0;
        int occupied = 0;
        int reserved = 0;
        for (ParkingSpace space : spaces) {
            switch (statusOf(current.get(space.getId()))) {
                case OCCUPIED -> occupied++;
                case RESERVED -> reserved++;
                case AVAILABLE -> available++;
            }
        }

        return new DashboardResponse(
                spaces.size(),
                available,
                occupied,
                reserved,
                (int) lotRepository.count(),
                active.size(),
                waitingRequestRepository.countByStatus(WaitingStatus.WAITING),
                now,
                clockService.getOffsetMinutes()
        );
    }

    @Transactional(readOnly = true)
    public List<LotResponse> lots() {
        LocalDateTime now = clockService.now();
        List<ParkingLot> lots = lotRepository.findAllWithSpaces();
        List<Reservation> active = reservationRepository.findWithDetailsByStatusIn(ACTIVE_STATUSES);
        Map<Long, Reservation> current = currentBySpace(active, now);
        Map<Long, Integer> travel = travelTimeService.minutesByLotId();

        List<LotResponse> result = new ArrayList<>();
        for (ParkingLot lot : lots) {
            List<ParkingSpace> spaces = lot.getSpaces().stream()
                    .sorted(Comparator.comparingInt(ParkingSpace::getFloorNumber)
                            .thenComparing(ParkingSpace::getCode))
                    .toList();
            result.add(toLotResponse(lot, spaces, current, travel));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<SpaceResponse> spaces(Long lotId, SpaceKind kind, SpaceStatus status, String query) {
        LocalDateTime now = clockService.now();
        List<ParkingSpace> spaces = lotId == null
                ? spaceRepository.findAllWithLot()
                : spaceRepository.findByLotIdWithLot(lotId);
        List<Reservation> active = reservationRepository.findWithDetailsByStatusIn(ACTIVE_STATUSES);
        Map<Long, Reservation> current = currentBySpace(active, now);
        Map<Long, Integer> travel = travelTimeService.minutesByLotId();

        String text = query == null ? null : query.trim().toLowerCase();
        List<SpaceResponse> result = new ArrayList<>();
        for (ParkingSpace space : spaces) {
            SpaceResponse response = toSpaceResponse(space, current.get(space.getId()), travel);
            if (kind != null && response.type() != kind) {
                continue;
            }
            if (status != null && response.status() != status) {
                continue;
            }
            if (text != null && !text.isBlank()
                    && !space.getCode().toLowerCase().contains(text)
                    && !space.getLot().getCode().toLowerCase().contains(text)
                    && !space.getLot().getName().toLowerCase().contains(text)) {
                continue;
            }
            result.add(response);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SpaceResponse space(Long id) {
        LocalDateTime now = clockService.now();
        ParkingSpace space = spaceRepository.findByIdWithLot(id)
                .orElseThrow(() -> new NotFoundException("Parking space " + id + " was not found"));
        List<Reservation> active = reservationRepository.findWithDetailsByStatusIn(ACTIVE_STATUSES);
        Map<Long, Reservation> current = currentBySpace(active, now);
        return toSpaceResponse(space, current.get(id), travelTimeService.minutesByLotId());
    }

    @Transactional(readOnly = true)
    public TravelTimeResponse travelTimes() {
        return travelTimeService.response();
    }

    private LotResponse toLotResponse(ParkingLot lot, List<ParkingSpace> spaces,
                                      Map<Long, Reservation> current,
                                      Map<Long, Integer> travel) {
        List<SpaceResponse> responses = spaces.stream()
                .map(space -> toSpaceResponse(space, current.get(space.getId()), travel))
                .toList();

        int available = 0;
        int occupied = 0;
        int reserved = 0;
        for (SpaceResponse response : responses) {
            switch (response.status()) {
                case OCCUPIED -> occupied++;
                case RESERVED -> reserved++;
                case AVAILABLE -> available++;
            }
        }

        return new LotResponse(
                lot.getId(),
                lot.getCode(),
                lot.getName(),
                lot.getAddress(),
                travel.getOrDefault(lot.getId(), UNREACHABLE),
                responses.size(),
                available,
                occupied,
                reserved,
                responses
        );
    }

    private SpaceResponse toSpaceResponse(ParkingSpace space, Reservation reservation,
                                          Map<Long, Integer> travel) {
        SpaceStatus status = statusOf(reservation);
        return new SpaceResponse(
                space.getId(),
                space.getCode(),
                space.getKind(),
                space.getFloorNumber(),
                space.requiresPermit(),
                status,
                reservation == null ? null : reservation.getId(),
                reservation == null ? null : reservation.getVehicle().getVehicleNumber(),
                reservation == null ? null : reservation.getStartTime(),
                reservation == null ? null : reservation.getEndTime(),
                space.getLot().getId(),
                space.getLot().getCode(),
                space.getLot().getName(),
                travel.getOrDefault(space.getLot().getId(), UNREACHABLE)
        );
    }

    private Map<Long, Reservation> currentBySpace(List<Reservation> active, LocalDateTime now) {
        Map<Long, Reservation> current = new HashMap<>();
        for (Reservation reservation : active) {
            Long spaceId = reservation.getSpace().getId();
            if (reservation.getStatus() == ReservationStatus.CHECKED_IN) {
                current.put(spaceId, reservation);
            } else if (!reservation.getStartTime().isAfter(now)
                    && reservation.getEndTime().isAfter(now)) {
                current.putIfAbsent(spaceId, reservation);
            }
        }
        return current;
    }

    private SpaceStatus statusOf(Reservation reservation) {
        if (reservation == null) {
            return SpaceStatus.AVAILABLE;
        }
        return reservation.getStatus() == ReservationStatus.CHECKED_IN
                ? SpaceStatus.OCCUPIED
                : SpaceStatus.RESERVED;
    }
}
