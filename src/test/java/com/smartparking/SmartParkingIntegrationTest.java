package com.smartparking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.domain.ParkingSpace;
import com.smartparking.domain.enums.ReservationStatus;
import com.smartparking.domain.enums.SpaceKind;
import com.smartparking.domain.enums.VehicleKind;
import com.smartparking.dto.ReservationRequest;
import com.smartparking.dto.ReservationResponse;
import com.smartparking.dto.VehicleRequest;
import com.smartparking.dto.VehicleResponse;
import com.smartparking.dto.WaitingRequestCreate;
import com.smartparking.dto.WaitingResponse;
import com.smartparking.exception.BusinessRuleException;
import com.smartparking.exception.ConflictException;
import com.smartparking.repository.ParkingSpaceRepository;
import com.smartparking.service.ClockService;
import com.smartparking.service.ReservationService;
import com.smartparking.service.VehicleService;
import com.smartparking.service.WaitingListService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the main rules. Each test gets a fresh Spring context
 * and a fresh H2 database, so the seeded 24 spaces are always available.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SmartParkingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private WaitingListService waitingListService;

    @Autowired
    private ParkingSpaceRepository spaceRepository;

    @Autowired
    private ClockService clockService;

    /* ------------------------------------------------------------------ */
    /* Helpers                                                             */
    /* ------------------------------------------------------------------ */

    private LocalDateTime futureStart() {
        return LocalDateTime.now().plusHours(2).withSecond(0).withNano(0);
    }

    private VehicleResponse seededVehicle(String number) {
        return vehicleService.list(null).stream()
                .filter(vehicle -> vehicle.vehicleNumber().equals(number))
                .findFirst()
                .orElseThrow();
    }

    private Long spaceId(String code) {
        return spaceRepository.findByCodeIgnoreCase(code)
                .orElseThrow()
                .getId();
    }

    /* ------------------------------------------------------------------ */
    /* Seed data and read endpoints                                        */
    /* ------------------------------------------------------------------ */

    @Test
    void dashboardShowsSeededLotsAndSpaces() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpaces").value(24))
                .andExpect(jsonPath("$.availableSpaces").value(24))
                .andExpect(jsonPath("$.totalLots").value(3))
                .andExpect(jsonPath("$.activeReservations").value(0))
                .andExpect(jsonPath("$.waitingRequests").value(0));

        mockMvc.perform(get("/api/lots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/api/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(24));
    }

    @Test
    void dijkstraTravelTimesUseShortestRoadPath() throws Exception {
        mockMvc.perform(get("/api/lots/travel-times"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("ENTRANCE"))
                .andExpect(jsonPath("$.lots[0].lotCode").value("LOT-A"))
                .andExpect(jsonPath("$.lots[0].minutes").value(3))
                .andExpect(jsonPath("$.lots[1].lotCode").value("LOT-B"))
                .andExpect(jsonPath("$.lots[1].minutes").value(7))
                .andExpect(jsonPath("$.lots[2].lotCode").value("LOT-C"))
                // ENTRANCE -> LOT-B (7) -> LOT-C (4) is shorter than the direct 12.
                .andExpect(jsonPath("$.lots[2].minutes").value(11));
    }

    /* ------------------------------------------------------------------ */
    /* Allocation and conflict rules                                       */
    /* ------------------------------------------------------------------ */

    @Test
    void smartAllocationChoosesNearestCompatibleSpace() throws Exception {
        VehicleResponse car = seededVehicle("TN01AB1234");
        ReservationRequest request = new ReservationRequest(
                car.id(), futureStart(), futureStart().plusHours(2), null);

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.spaceCode").value("A-C-01"))
                .andExpect(jsonPath("$.lotCode").value("LOT-A"))
                .andExpect(jsonPath("$.vehicleNumber").value("TN01AB1234"));
    }

    @Test
    void overlappingSpaceAndVehicleReservationsAreRejected() {
        VehicleResponse carOne = seededVehicle("TN01AB1234");
        VehicleResponse carTwo = seededVehicle("TN09PQ7788");
        LocalDateTime start = futureStart();
        LocalDateTime end = start.plusHours(2);

        ReservationResponse first = reservationService.create(new ReservationRequest(
                carOne.id(), start, end, spaceId("A-C-01")));
        assertNotNull(first.id());

        ConflictException spaceConflict = assertThrows(ConflictException.class, () ->
                reservationService.create(new ReservationRequest(
                        carTwo.id(), start, end, spaceId("A-C-01"))));
        assertTrue(spaceConflict.getMessage().contains("taken"));

        ConflictException vehicleConflict = assertThrows(ConflictException.class, () ->
                reservationService.create(new ReservationRequest(
                        carOne.id(), start, end, spaceId("A-C-02"))));
        assertTrue(vehicleConflict.getMessage().contains("overlapping"));
    }

    @Test
    void incompatibleVehicleTypeAndMissingPermitAreRejected() {
        VehicleResponse twoWheeler = seededVehicle("TN22XY4455");
        VehicleResponse carWithoutPermit = seededVehicle("TN01AB1234");
        LocalDateTime start = futureStart();
        LocalDateTime end = start.plusHours(2);

        BusinessRuleException typeError = assertThrows(BusinessRuleException.class, () ->
                reservationService.create(new ReservationRequest(
                        twoWheeler.id(), start, end, spaceId("A-C-01"))));
        assertTrue(typeError.getMessage().contains("cars"));

        BusinessRuleException permitError = assertThrows(BusinessRuleException.class, () ->
                reservationService.create(new ReservationRequest(
                        carWithoutPermit.id(), start, end, spaceId("A-A-01"))));
        assertTrue(permitError.getMessage().contains("accessibility permit"));
    }

    @Test
    void invalidReservationWindowReturns422() throws Exception {
        VehicleResponse car = seededVehicle("TN01AB1234");
        LocalDateTime start = futureStart();
        ReservationRequest backwards = new ReservationRequest(
                car.id(), start.plusHours(2), start, null);

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(backwards)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("End time must be after start time"));
    }

    @Test
    void duplicateVehicleRegistrationIsRejected() throws Exception {
        String duplicate = objectMapper.writeValueAsString(new VehicleRequest(
                "TN01AB1234", VehicleKind.CAR, "Another Owner", false));

        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicate))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleNumber":"TN99ZZ0001","type":"TRUCK","ownerName":"Test"}
                                """))
                .andExpect(status().isBadRequest());
    }

    /* ------------------------------------------------------------------ */
    /* Full lot, waiting list and release                                  */
    /* ------------------------------------------------------------------ */

    @Test
    void fullCarLotQueuesWaitingRequestAndAllocatesReleasedSpace() {
        LocalDateTime start = futureStart();
        LocalDateTime end = start.plusHours(2);

        for (int i = 1; i <= 10; i++) {
            vehicleService.register(new VehicleRequest(
                    "TESTCAR" + i, VehicleKind.CAR, "Test Owner " + i, false));
        }

        List<VehicleResponse> cars = vehicleService.list(null).stream()
                .filter(vehicle -> vehicle.type() == VehicleKind.CAR)
                .toList();
        List<ParkingSpace> carSpaces = spaceRepository.findAllWithLot().stream()
                .filter(space -> space.getKind() == SpaceKind.CAR)
                .toList();

        assertEquals(13, carSpaces.size());
        assertEquals(13, cars.size());

        List<ReservationResponse> filled = new ArrayList<>();
        for (int i = 0; i < carSpaces.size(); i++) {
            filled.add(reservationService.create(new ReservationRequest(
                    cars.get(i).id(), start, end, carSpaces.get(i).getId())));
        }

        VehicleResponse waitingVehicle = vehicleService.register(new VehicleRequest(
                "TESTCAR14", VehicleKind.CAR, "Waiting Driver", false));

        ConflictException full = assertThrows(ConflictException.class, () ->
                reservationService.create(new ReservationRequest(
                        waitingVehicle.id(), start, end, null)));
        assertTrue(full.getMessage().contains("No compatible"));

        WaitingResponse waiting = waitingListService.enqueue(
                new WaitingRequestCreate(waitingVehicle.id(), start, end));
        assertEquals(1, waiting.queuePosition());

        ReservationResponse released = filled.get(0);
        reservationService.cancel(released.id());

        assertTrue(waitingListService.list().isEmpty(),
                "The after-commit listener should remove the allocated request from the queue");

        List<ReservationResponse> allocated = reservationService.list(
                ReservationStatus.RESERVED, waitingVehicle.vehicleNumber(), null, null);
        assertEquals(1, allocated.size());
        assertEquals(released.spaceCode(), allocated.get(0).spaceCode());
    }

    @Test
    void waitingListOrdersPermitHoldersBeforeStandardRequests() {
        VehicleResponse standard = seededVehicle("TN01AB1234");
        VehicleResponse permitHolder = seededVehicle("TN09PQ7788");
        LocalDateTime start = futureStart();
        LocalDateTime end = start.plusHours(2);

        waitingListService.enqueue(new WaitingRequestCreate(standard.id(), start, end));
        waitingListService.enqueue(new WaitingRequestCreate(permitHolder.id(), start, end));

        List<WaitingResponse> ordered = waitingListService.list();
        assertEquals(2, ordered.size());
        assertEquals(permitHolder.vehicleNumber(), ordered.get(0).vehicleNumber());
        assertEquals(1, ordered.get(0).accessPriority());
        assertEquals(1, ordered.get(0).queuePosition());
        assertEquals(standard.vehicleNumber(), ordered.get(1).vehicleNumber());
    }

    /* ------------------------------------------------------------------ */
    /* Expiry, check-in and check-out                                      */
    /* ------------------------------------------------------------------ */

    @Test
    void unclaimedReservationExpiresAfterGracePeriod() {
        VehicleResponse car = seededVehicle("TN01AB1234");
        LocalDateTime start = clockService.now().plusMinutes(1).withSecond(0).withNano(0);
        ReservationResponse reservation = reservationService.create(new ReservationRequest(
                car.id(), start, start.plusHours(2), spaceId("A-C-01")));

        clockService.advance(20);
        int expired = reservationService.expireOverdueReservations();

        assertEquals(1, expired);
        assertEquals(ReservationStatus.EXPIRED,
                reservationService.get(reservation.id()).status());
        clockService.reset();
    }

    @Test
    void checkInAndCheckOutCalculatesRoundedUpFee() {
        VehicleResponse car = seededVehicle("TN01AB1234");
        LocalDateTime start = clockService.now().plusMinutes(1).withSecond(0).withNano(0);
        ReservationResponse reservation = reservationService.create(new ReservationRequest(
                car.id(), start, start.plusHours(3), spaceId("A-C-01")));

        ReservationResponse checkedIn = reservationService.checkIn(reservation.id());
        assertEquals(ReservationStatus.CHECKED_IN, checkedIn.status());

        clockService.advance(90);
        ReservationResponse checkedOut = reservationService.checkOut(reservation.id());

        assertEquals(ReservationStatus.COMPLETED, checkedOut.status());
        assertEquals(0, new BigDecimal("100.00").compareTo(checkedOut.fee()),
                "90 minutes of car parking rounds up to 2 hours at Rs.50/hour");
        clockService.reset();
    }

    /* ------------------------------------------------------------------ */
    /* Simulated clock                                                     */
    /* ------------------------------------------------------------------ */

    @Test
    void clockCanBeAdvancedAndReset() throws Exception {
        mockMvc.perform(post("/api/system/clock/advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offsetMinutes").value(15));

        mockMvc.perform(get("/api/system/clock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offsetMinutes").value(15));

        mockMvc.perform(post("/api/system/clock/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offsetMinutes").value(0));
    }
}
