# Test Cases and Results

This document lists the automated integration tests, the manual API smoke
tests and the frontend rendering checks. All results below were produced on
**29 October 2026** with:

- Java 17.0.20.1
- Spring Boot 3.3.5
- Maven 3.9.9
- MariaDB 10.4.32 (manual tests) and H2 2.2.224 in MySQL mode (automated tests)
- Windows 11 / PowerShell

---

## 1. How to run the automated tests

From the `smart-parking-system` directory:

```bash
mvn test
```

The suite uses `src/test/resources/application-test.properties`:

```properties
spring.datasource.url=jdbc:h2:mem:smartparking;MODE=MySQL;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
```

`@DirtiesContext(AFTER_EACH_TEST_METHOD)` gives every test a fresh Spring
context and a fresh in-memory database, so the seeded 24 spaces are always
available and tests do not interfere with each other.

### Latest automated result

```text
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 2. Automated integration tests

File: `src/test/java/com/smartparking/SmartParkingIntegrationTest.java`

| ID | Test method | Scenario | Input | Expected result | Actual result | Status |
|---|---|---|---|---|---|---|
| AT-01 | `dashboardShowsSeededLotsAndSpaces` | Seed data is visible through the read API | `GET /api/dashboard`, `GET /api/lots`, `GET /api/spaces` | `200`; 24 spaces, 24 available, 3 lots, 0 active reservations, 0 waiting; 3 lots; 24 spaces | Exactly those values | PASS |
| AT-02 | `dijkstraTravelTimesUseShortestRoadPath` | Dijkstra uses the shortest path, including an indirect route | `GET /api/lots/travel-times` | `200`; source `ENTRANCE`; LOT-A 3, LOT-B 7, LOT-C 11 minutes | `LOT-A=3`, `LOT-B=7`, `LOT-C=11` | PASS |
| AT-03 | `smartAllocationChoosesNearestCompatibleSpace` | A car with no preferred space gets the nearest compatible space | Vehicle `TN01AB1234`, start `now + 2 h`, end `+2 h` | `201`; status `RESERVED`; space `A-C-01`; lot `LOT-A` | `A-C-01`, `LOT-A`, `RESERVED` | PASS |
| AT-04 | `overlappingSpaceAndVehicleReservationsAreRejected` | Same space, different vehicles; and same vehicle, different spaces | Two cars, identical window; second request uses `A-C-01`; third uses `A-C-02` | First booking succeeds; space conflict and vehicle conflict are thrown | `ConflictException` for both; messages contain "taken" and "overlapping" | PASS |
| AT-05 | `incompatibleVehicleTypeAndMissingPermitAreRejected` | Type mismatch and missing accessibility permit | Two-wheeler into `A-C-01`; car without permit into `A-A-01` | `BusinessRuleException`; messages mention "cars" and "accessibility permit" | Both exceptions with the expected messages | PASS |
| AT-06 | `invalidReservationWindowReturns422` | End time is before start time | `POST /api/reservations` with reversed window | `422 Unprocessable Entity`; message `End time must be after start time` | `422` and exact message | PASS |
| AT-07 | `duplicateVehicleRegistrationIsRejected` | Duplicate vehicle number and invalid enum value | Existing `TN01AB1234`; then `type = TRUCK` | Duplicate `409`; invalid enum `400` | `409` and `400` | PASS |
| AT-08 | `fullCarLotQueuesWaitingRequestAndAllocatesReleasedSpace` | Full car lot, waiting request, cancellation triggers automatic allocation | 13 car spaces filled; 14th car gets `409`; join waiting list; cancel first booking | Waiting position 1; queue becomes empty after release; waiting vehicle gets the released space | Queue empty; exactly one `RESERVED` booking for the waiting vehicle in the released space | PASS |
| AT-09 | `waitingListOrdersPermitHoldersBeforeStandardRequests` | Priority ordering | Standard car `TN01AB1234` joins, then permit car `TN09PQ7788` joins | Permit holder is position 1 with priority 1; standard is position 2 | `TN09PQ7788` first, priority 1; standard second | PASS |
| AT-10 | `unclaimedReservationExpiresAfterGracePeriod` | Expiry after the grace period | Reservation starts at `now + 1 min`; advance clock 20 min; run expiry | 1 reservation expires; status `EXPIRED` | `expired = 1`; status `EXPIRED` | PASS |
| AT-11 | `checkInAndCheckOutCalculatesRoundedUpFee` | Check-in and fee rounding | Car reservation; check in; advance 90 min; check out | Status `COMPLETED`; fee `100.00` (2 hours × ₹50) | `COMPLETED`; fee `100.00` | PASS |
| AT-12 | `clockCanBeAdvancedAndReset` | Simulated clock endpoints | Advance 15 min; read clock; reset | Offset `15`, then `15`, then `0` | Exact values | PASS |

### Notes on AT-04 and AT-05

AT-04 verifies two separate guards:

1. **Space conflict** — `countOverlapping(spaceId, start, end, ACTIVE)`.
2. **Vehicle conflict** — `countOverlappingForVehicle(vehicleId, start, end, ACTIVE)`.

AT-05 uses the service layer directly, so the vehicle-overlap check does not
mask the compatibility error. Through the HTTP API, a vehicle that already has
an overlapping booking returns `409` before the preferred-space compatibility
check; use a fresh vehicle when demonstrating the `422` permit error.

---

## 3. Manual API smoke tests (MariaDB)

These tests were run against the real MySQL/MariaDB profile after executing
`database/schema.sql` and `database/sample-data.sql`.

Base URL: `http://localhost:8080`

| ID | Scenario | Request | Expected | Actual | Status |
|---|---|---|---|---|---|
| MT-01 | Database scripts | Run `schema.sql`, then `sample-data.sql` | No SQL errors; 3 lots, 24 spaces, 6 roads, 4 vehicles, 1 event | Exit code 0; counts `3 / 24 / 6 / 4 / 1` | PASS |
| MT-02 | Seed data through API | `GET /api/dashboard`, `GET /api/lots`, `GET /api/spaces` | 24 spaces, 3 lots, 24 available | 24 / 3 / 24 | PASS |
| MT-03 | Register a car | `POST /api/vehicles` `TN99AA0001`, CAR, no permit | `201 Created` with id | `201`, id 5 | PASS |
| MT-04 | Register a two-wheeler | `POST /api/vehicles` `TN99BB0002`, TWO_WHEELER | `201 Created` | `201`, id 6 | PASS |
| MT-05 | Register a permit car | `POST /api/vehicles` `TN99CC0003`, CAR, permit | `201 Created`; permit true | `201`, id 7 | PASS |
| MT-06 | Smart allocation | `POST /api/reservations` for `TN99AA0001`, `now + 1 min` to `now + 3 h`, no preferred space | `201`; nearest car space in `LOT-A` | `201`; `A-C-01`, `LOT-A`, `RESERVED` | PASS |
| MT-07 | Overlapping vehicle | Same vehicle and window again | `409 Conflict` | `409`; `This vehicle already has an overlapping reservation` | PASS |
| MT-08 | Incompatible type | Two-wheeler preferred space `A-C-02` | `422 Unprocessable Entity` | `422`; `Space A-C-02 is reserved for cars` | PASS |
| MT-09 | Missing permit | Fresh car without permit, preferred space `A-A-01` | `422 Unprocessable Entity` | `422`; `Space A-A-01 requires an accessibility permit` | PASS |
| MT-10 | Unknown vehicle | Reservation with `vehicleId = 999999` | `404 Not Found` | `404`; `Vehicle 999999 was not found` | PASS |
| MT-11 | Invalid window | End before start | `422 Unprocessable Entity` | `422`; `End time must be after start time` | PASS |
| MT-12 | Check-in | `POST /api/reservations/{id}/check-in` | `200`; status `CHECKED_IN` | `200`; `CHECKED_IN` | PASS |
| MT-13 | Check-out fee | `POST /api/reservations/{id}/check-out` immediately after check-in | `200`; status `COMPLETED`; minimum 1 hour = ₹50.00 | `200`; `COMPLETED`; fee `50.00` | PASS |
| MT-14 | Waiting-list priority | Permit car joins waiting list | `201`; position 1; priority 1 | `201`; position 1; priority 1 | PASS |
| MT-15 | Release triggers waiting allocation | Cancel the blocking reservation | `200`; waiting request removed and allocated automatically | Waiting list count `0`; new `RESERVED` booking for `TN99CC0003` | PASS |
| MT-16 | Expiry scan | Create a bike reservation; advance clock 30 min; `POST /api/reservations/expire` | `200`; expired count ≥ 1 | `expired = 2`; `allocatedFromWaitingList = 0` | PASS |
| MT-17 | Dashboard after reset | `GET /api/dashboard` after clock reset | 24 spaces, 0 occupied, 0 reserved, 0 active, 0 waiting | Exact values | PASS |

### Sample error responses

```json
{
  "timestamp": "2026-10-29T22:10:00",
  "status": 409,
  "error": "Conflict",
  "message": "This vehicle already has an overlapping reservation",
  "path": "/api/reservations",
  "details": []
}
```

```json
{
  "timestamp": "2026-10-29T22:11:00",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Space A-A-01 requires an accessibility permit",
  "path": "/api/reservations",
  "details": []
}
```

---

## 4. Frontend rendering checks

The dashboard was loaded in headless Microsoft Edge against the running
application. The rendered DOM was inspected after JavaScript completed.

Command (simplified):

```powershell
msedge.exe --headless --disable-gpu --virtual-time-budget=8000 `
  --dump-dom http://localhost:8080/
```

| ID | Check | Expected | Actual | Status |
|---|---|---|---|---|
| UI-01 | Page loads | `200`, dashboard HTML with all sections | `200`; 19 KB HTML | PASS |
| UI-02 | CSS loads | `200` | `200`; 21 KB | PASS |
| UI-03 | JavaScript loads | `200` | `200`; 43 KB | PASS |
| UI-04 | Dashboard counters | Total 24, available 24, occupied 0, reserved 0, active 0, waiting 0 | Exact values | PASS |
| UI-05 | Lot cards | 3 cards | 3 cards | PASS |
| UI-06 | Space grid | 24 cards | 24 cards | PASS |
| UI-07 | Vehicle directory | 4 seeded vehicles | 4 rows | PASS |
| UI-08 | Travel-time chip | `From ENTRANCE: LOT-A 3 min · LOT-B 7 min · LOT-C 11 min` | Exact text | PASS |
| UI-09 | Empty waiting list | Clear empty message | `The waiting list is empty.` | PASS |
| UI-10 | Clock chip | Simulated time is shown | `Simulated 10:00:51 pm` | PASS |
| UI-11 | Modal hidden initially | Space modal is not visible | `hidden` attribute respected by CSS | PASS |
| UI-12 | Favicon request | No server error in the log | `favicon.svg` served; missing files return a clean `404` | PASS |

The frontend uses `fetch()` for every action. It does not compute allocation,
overlap or priority; those checks remain in Java.

---

## 5. Suggested manual demo test cases

Use these in the browser for a live demonstration. They mirror the automated
tests but are easy to show on screen.

| Case | Steps | Expected on screen |
|---|---|---|
| Success | Register a car, choose smart allocation, reserve | Green success message with the chosen space, e.g. `A-C-01` in `LOT-A` |
| Overlap | Reserve the same space and window for another car | Red `409` message: `All compatible spaces were taken...` or a clear conflict message |
| Incompatible type | Register a two-wheeler, choose a car space | Red `422` message: `Space ... is reserved for cars` |
| Missing permit | Choose an accessible space with a non-permit vehicle | Red `422` message: `... requires an accessibility permit` |
| Full lot | Fill all car spaces for one window, then try another car | Red `409` message: `No compatible parking space is available...` |
| Cancellation | Cancel a `RESERVED` booking | Status becomes `CANCELLED`; the space returns to `AVAILABLE` |
| Expiry | Create a booking starting in 1 minute, click `+60 min`, then `Expire unclaimed now` | Status becomes `EXPIRED`; history shows the event |
| Check-in/out | Check in, advance 90 minutes, check out | Status `COMPLETED`; fee `₹100.00` for a car |
| Waiting list | Fill the last compatible space, join the waiting list, cancel the blocker | Waiting row disappears; a new reservation appears for the waiting vehicle |
| Priority | Join with a standard car, then with a permit car | Permit holder appears at position 1 with `P1`; standard at position 2 with `P0` |
| Search/filter | Filter spaces by lot, type, status; search history | Grid and table update without a page reload |

---

## 6. Resetting after a manual test

The manual tests create extra vehicles, reservations and events. To restore the
clean sample state:

```powershell
Get-Content database\schema.sql | C:\xampp\mysql\bin\mysql.exe -u root
Get-Content database\sample-data.sql | C:\xampp\mysql\bin\mysql.exe -u root
```

Or, from MySQL:

```sql
USE smart_parking;
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE parking_events;
TRUNCATE TABLE waiting_requests;
TRUNCATE TABLE reservations;
TRUNCATE TABLE parking_spaces;
TRUNCATE TABLE road_segments;
TRUNCATE TABLE vehicles;
TRUNCATE TABLE parking_lots;
SET FOREIGN_KEY_CHECKS = 1;
```

Then run `database/sample-data.sql` again. Restart the application if you also
want to reset the simulated clock offset (it is stored in memory).
