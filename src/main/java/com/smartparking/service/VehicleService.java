package com.smartparking.service;

import com.smartparking.algorithm.ParkingIndex;
import com.smartparking.domain.Vehicle;
import com.smartparking.dto.VehicleRequest;
import com.smartparking.dto.VehicleResponse;
import com.smartparking.event.PriorityChangedEvent;
import com.smartparking.exception.ConflictException;
import com.smartparking.exception.NotFoundException;
import com.smartparking.repository.VehicleRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Vehicle registration, lookup and accessibility-permit changes.
 */
@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final ParkingIndex parkingIndex;
    private final WaitingListService waitingListService;
    private final ApplicationEventPublisher eventPublisher;
    private final ClockService clockService;

    public VehicleService(VehicleRepository vehicleRepository,
                          ParkingIndex parkingIndex,
                          WaitingListService waitingListService,
                          ApplicationEventPublisher eventPublisher,
                          ClockService clockService) {
        this.vehicleRepository = vehicleRepository;
        this.parkingIndex = parkingIndex;
        this.waitingListService = waitingListService;
        this.eventPublisher = eventPublisher;
        this.clockService = clockService;
    }

    @Transactional
    public VehicleResponse register(VehicleRequest request) {
        String normalizedNumber = normalize(request.vehicleNumber());
        if (vehicleRepository.existsByVehicleNumberIgnoreCase(normalizedNumber)) {
            throw new ConflictException("Vehicle " + normalizedNumber + " is already registered");
        }

        Vehicle vehicle = Vehicle.create(
                request.type(),
                normalizedNumber,
                request.ownerName().trim(),
                request.accessibilityPermit()
        );
        vehicle.setCreatedAt(clockService.now());
        Vehicle saved = vehicleRepository.save(vehicle);
        parkingIndex.indexVehicle(saved);
        return VehicleResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> list(String query) {
        List<Vehicle> vehicles = (query == null || query.isBlank())
                ? vehicleRepository.findAllByOrderByVehicleNumberAsc()
                : vehicleRepository.search(query.trim());
        return vehicles.stream().map(VehicleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse get(Long id) {
        return VehicleResponse.from(requireVehicle(id));
    }

    @Transactional(readOnly = true)
    public Vehicle requireVehicle(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Vehicle " + id + " was not found"));
    }

    @Transactional
    public VehicleResponse updatePermit(Long id, boolean permit) {
        Vehicle vehicle = requireVehicle(id);
        vehicle.setAccessibilityPermit(permit);
        Vehicle saved = vehicleRepository.save(vehicle);
        parkingIndex.indexVehicle(saved);

        // Ranking changed: rebuild the heap, then reconsider waiting requests.
        waitingListService.refreshRanking();
        eventPublisher.publishEvent(new PriorityChangedEvent(saved.getId()));
        return VehicleResponse.from(saved);
    }

    public String normalize(String raw) {
        return raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
