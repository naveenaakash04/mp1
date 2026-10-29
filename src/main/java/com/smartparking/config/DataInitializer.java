package com.smartparking.config;

import com.smartparking.domain.AccessibleSpace;
import com.smartparking.domain.CarSpace;
import com.smartparking.domain.CarVehicle;
import com.smartparking.domain.ParkingLot;
import com.smartparking.domain.RoadSegment;
import com.smartparking.domain.TwoWheelerSpace;
import com.smartparking.domain.TwoWheelerVehicle;
import com.smartparking.domain.Vehicle;
import com.smartparking.domain.enums.VehicleKind;
import com.smartparking.repository.ParkingLotRepository;
import com.smartparking.repository.RoadSegmentRepository;
import com.smartparking.repository.VehicleRepository;
import com.smartparking.service.ClockService;
import com.smartparking.service.TravelTimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds three lots, 24 spaces, the road graph and a few demo vehicles.
 * The checks make the initializer safe to run on every startup and safe when
 * the optional SQL sample data has already been loaded.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final ParkingLotRepository lotRepository;
    private final RoadSegmentRepository roadRepository;
    private final VehicleRepository vehicleRepository;
    private final TravelTimeService travelTimeService;
    private final ClockService clockService;

    public DataInitializer(ParkingLotRepository lotRepository,
                           RoadSegmentRepository roadRepository,
                           VehicleRepository vehicleRepository,
                           TravelTimeService travelTimeService,
                           ClockService clockService) {
        this.lotRepository = lotRepository;
        this.roadRepository = roadRepository;
        this.vehicleRepository = vehicleRepository;
        this.travelTimeService = travelTimeService;
        this.clockService = clockService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (lotRepository.count() == 0) {
            seedLotsAndSpaces();
            log.info("Seeded 3 parking lots with 24 spaces");
        }
        if (roadRepository.count() == 0) {
            seedRoads();
            log.info("Seeded road graph for Dijkstra travel times");
        }
        if (vehicleRepository.count() == 0) {
            seedVehicles();
            log.info("Seeded demo vehicles");
        }
        travelTimeService.refresh();
    }

    private void seedLotsAndSpaces() {
        ParkingLot lotA = new ParkingLot("LOT-A", "North Campus Lot",
                "Near main gate and academic block", "LOT-A");
        addCar(lotA, "A-C-01", 0);
        addCar(lotA, "A-C-02", 0);
        addCar(lotA, "A-C-03", 0);
        addTwoWheeler(lotA, "A-T-01", 0);
        addTwoWheeler(lotA, "A-T-02", 0);
        addAccessible(lotA, "A-A-01", 0);
        addCar(lotA, "A-C-04", 1);
        addCar(lotA, "A-C-05", 1);

        ParkingLot lotB = new ParkingLot("LOT-B", "Library Lot",
                "Beside central library and study halls", "LOT-B");
        addCar(lotB, "B-C-01", 0);
        addCar(lotB, "B-C-02", 0);
        addCar(lotB, "B-C-03", 0);
        addCar(lotB, "B-C-04", 0);
        addTwoWheeler(lotB, "B-T-01", 0);
        addTwoWheeler(lotB, "B-T-02", 0);
        addTwoWheeler(lotB, "B-T-03", 0);
        addAccessible(lotB, "B-A-01", 0);
        addCar(lotB, "B-C-05", 1);

        ParkingLot lotC = new ParkingLot("LOT-C", "Sports Complex Lot",
                "Next to the sports ground and gym", "LOT-C");
        addCar(lotC, "C-C-01", 0);
        addCar(lotC, "C-C-02", 0);
        addCar(lotC, "C-C-03", 0);
        addTwoWheeler(lotC, "C-T-01", 0);
        addTwoWheeler(lotC, "C-T-02", 0);
        addAccessible(lotC, "C-A-01", 0);
        addTwoWheeler(lotC, "C-T-03", 1);

        lotRepository.saveAll(List.of(lotA, lotB, lotC));
    }

    private void addCar(ParkingLot lot, String code, int floor) {
        lot.addSpace(new CarSpace(code, lot, floor));
    }

    private void addTwoWheeler(ParkingLot lot, String code, int floor) {
        lot.addSpace(new TwoWheelerSpace(code, lot, floor));
    }

    private void addAccessible(ParkingLot lot, String code, int floor) {
        lot.addSpace(new AccessibleSpace(code, lot, floor));
    }

    private void seedRoads() {
        roadRepository.saveAll(List.of(
                new RoadSegment("ENTRANCE", "LOT-A", 3),
                new RoadSegment("ENTRANCE", "LOT-B", 7),
                new RoadSegment("ENTRANCE", "LOT-C", 12),
                new RoadSegment("LOT-A", "LOT-B", 5),
                new RoadSegment("LOT-B", "LOT-C", 4),
                new RoadSegment("LOT-A", "LOT-C", 9)
        ));
    }

    private void seedVehicles() {
        Vehicle car1 = new CarVehicle("TN01AB1234", "Aarav Kumar", false);
        Vehicle car2 = new CarVehicle("TN09PQ7788", "Meera Iyer", true);
        Vehicle bike1 = new TwoWheelerVehicle("TN22XY4455", "Rahul Das", false);
        Vehicle car3 = new CarVehicle("TN55CD9090", "Priya Nair", true);
        car1.setCreatedAt(clockService.now());
        car2.setCreatedAt(clockService.now());
        bike1.setCreatedAt(clockService.now());
        car3.setCreatedAt(clockService.now());
        vehicleRepository.saveAll(List.of(car1, car2, bike1, car3));
    }
}
