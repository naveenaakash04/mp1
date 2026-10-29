# REST API Reference

Base URL: `http://localhost:8080`

All request and response bodies are JSON. Date/time values use the ISO local
format `yyyy-MM-ddTHH:mm:ss` (no timezone suffix). Enum values are uppercase.

---

## 1. Common response rules

| HTTP status | Meaning in this project |
|---|---|
| `200 OK` | Read or state change succeeded |
| `201 Created` | Vehicle, reservation or waiting request created |
| `204 No Content` | Waiting request deleted |
| `400 Bad Request` | Malformed JSON, unknown enum, or Bean Validation failure |
| `404 Not Found` | Id/code does not exist |
| `409 Conflict` | Duplicate vehicle, overlapping booking, or no compatible space |
| `422 Unprocessable Entity` | Valid JSON but a business rule was violated (type, permit, time window) |
| `500 Internal Server Error` | Unexpected server error (check the backend log) |

Error body:

```json
{
  "timestamp": "2026-10-29T21:52:39.1511484",
  "status": 409,
  "error": "Conflict",
  "message": "This vehicle already has an overlapping reservation",
  "path": "/api/reservations",
  "details": []
}
```

Validation errors may include `details`:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Please correct the highlighted fields",
  "details": ["vehicleNumber: Vehicle number must be 4-20 characters"]
}
```

---

## 2. Endpoint summary

| Method | Path | Purpose |
|---|---|---|
| GET | `/` | Dashboard HTML page |
| GET | `/api/dashboard` | Counts, simulated time, clock offset |
| GET | `/api/lots` | Lots with counts and spaces |
| GET | `/api/lots/{id}/spaces` | Spaces in one lot, optional filters |
| GET | `/api/lots/travel-times` | Dijkstra travel times from the entrance |
| GET | `/api/spaces` | All spaces, optional filters |
| GET | `/api/spaces/{id}` | One space with active reservation |
| GET | `/api/vehicles` | Registered vehicles, optional search |
| GET | `/api/vehicles/{id}` | One vehicle |
| POST | `/api/vehicles` | Register a vehicle |
| PATCH | `/api/vehicles/{id}/permit` | Grant/remove accessibility permit |
| GET | `/api/reservations` | Reservations with filters |
| GET | `/api/reservations/{id}` | One reservation |
| POST | `/api/reservations` | Create a reservation (smart or preferred space) |
| POST | `/api/reservations/{id}/cancel` | Cancel a `RESERVED` reservation |
| POST | `/api/reservations/{id}/check-in` | Check in a `RESERVED` reservation |
| POST | `/api/reservations/{id}/check-out` | Check out a `CHECKED_IN` reservation |
| POST | `/api/reservations/expire` | Expire unclaimed reservations now |
| GET | `/api/waiting-list` | Current waiting queue in priority order |
| POST | `/api/waiting-list` | Join the waiting list |
| DELETE | `/api/waiting-list/{id}` | Cancel a waiting request |
| POST | `/api/waiting-list/process` | Try to allocate every waiting request |
| GET | `/api/history` | Audit history |
| GET | `/api/system/clock` | Simulated/real time and offset |
| POST | `/api/system/clock/advance` | Advance simulated time |
| POST | `/api/system/clock/reset` | Reset simulated time |
| GET | `/api/system/travel-times` | Alias of `/api/lots/travel-times` |

---

## 3. Dashboard and parking queries

### GET `/api/dashboard`

```json
{
  "totalSpaces": 24,
  "availableSpaces": 24,
  "occupiedSpaces": 0,
  "reservedSpaces": 0,
  "totalLots": 3,
  "activeReservations": 0,
  "waitingRequests": 0,
  "simulatedNow": "2026-10-29T21:50:14.3366209",
  "clockOffsetMinutes": 0
}
```

`reservedSpaces` and `occupiedSpaces` describe the state at the **simulated
now**. Future reservations are counted in `activeReservations` but do not
mark a space as reserved until their start time.

### GET `/api/lots`

Returns lots ordered by code. Each lot includes its full space list.

```json
[
  {
    "id": 1,
    "code": "LOT-A",
    "name": "North Campus Lot",
    "address": "Near main gate and academic block",
    "travelMinutes": 3,
    "totalSpaces": 8,
    "availableSpaces": 8,
    "occupiedSpaces": 0,
    "reservedSpaces": 0,
    "spaces": [
      {
        "id": 1,
        "code": "A-C-01",
        "type": "CAR",
        "floorNumber": 0,
        "requiresPermit": false,
        "status": "AVAILABLE",
        "activeReservationId": null,
        "activeVehicleNumber": null,
        "reservationStart": null,
        "reservationEnd": null,
        "lotId": 1,
        "lotCode": "LOT-A",
        "lotName": "North Campus Lot",
        "travelMinutes": 3
      }
    ]
  }
]
```

### GET `/api/lots/{id}/spaces`

Query parameters (all optional):

| Parameter | Values | Example |
|---|---|---|
| `kind` | `CAR`, `TWO_WHEELER`, `ACCESSIBLE` | `?kind=CAR` |
| `status` | `AVAILABLE`, `RESERVED`, `OCCUPIED` | `?status=AVAILABLE` |
| `q` | space code, lot code or lot name | `?q=north` |

### GET `/api/spaces`

Query parameters: `lotId`, `kind`, `status`, `q`.

```bash
curl "http://localhost:8080/api/spaces?lotId=1&kind=CAR&status=AVAILABLE"
```

### GET `/api/spaces/{id}`

Returns a single `SpaceResponse`. Returns `404` when the id does not exist.

### GET `/api/lots/travel-times`

```json
{
  "source": "ENTRANCE",
  "lots": [
    { "lotId": 1, "lotCode": "LOT-A", "lotName": "North Campus Lot", "minutes": 3 },
    { "lotId": 2, "lotCode": "LOT-B", "lotName": "Library Lot", "minutes": 7 },
    { "lotId": 3, "lotCode": "LOT-C", "lotName": "Sports Complex Lot", "minutes": 11 }
  ]
}
```

`LOT-C` is 11 minutes because the shortest path is `ENTRANCE -> LOT-B -> LOT-C`
(7 + 4), not the direct 12-minute road.

---

## 4. Vehicles

### POST `/api/vehicles`

Request:

```json
{
  "vehicleNumber": "TN77DEMO01",
  "type": "CAR",
  "ownerName": "Demo Student",
  "accessibilityPermit": false
}
```

Rules:

- `vehicleNumber`: 4–20 characters, letters/digits/spaces/hyphens; normalised to uppercase with spaces removed.
- `type`: `CAR` or `TWO_WHEELER`.
- `ownerName`: required, maximum 100 characters.
- Duplicate number -> `409 Conflict`.
- Invalid body -> `400 Bad Request`.

Response `201 Created`:

```json
{
  "id": 5,
  "vehicleNumber": "TN77DEMO01",
  "type": "CAR",
  "ownerName": "Demo Student",
  "accessibilityPermit": false,
  "createdAt": "2026-10-29T21:55:00"
}
```

### GET `/api/vehicles?q=demo`

`q` searches number and owner name. Response is a list of `VehicleResponse`.

### GET `/api/vehicles/{id}`

Returns one vehicle or `404`.

### PATCH `/api/vehicles/{id}/permit`

```json
{ "accessibilityPermit": true }
```

Changing a permit also recalculates waiting-list priority and may immediately
reconsider waiting requests.

---

## 5. Reservations

### POST `/api/reservations`

Request:

```json
{
  "vehicleId": 1,
  "startTime": "2026-10-29T22:00:00",
  "endTime": "2026-10-30T00:00:00",
  "preferredSpaceId": null
}
```

- `preferredSpaceId` is optional. Omit it or send `null` for smart allocation.
- If supplied, allocation is restricted to that space; an incompatible space returns `422`.
- Overlap with the same vehicle or an active space reservation returns `409`.
- No eligible space returns `409` with `No compatible parking space is available...`.

Response `201 Created`:

```json
{
  "id": 1,
  "reservationCode": "RSV-7A08B1DF",
  "vehicleId": 1,
  "vehicleNumber": "TN01AB1234",
  "vehicleType": "CAR",
  "ownerName": "Aarav Kumar",
  "spaceId": 1,
  "spaceCode": "A-C-01",
  "spaceType": "CAR",
  "spaceRequiresPermit": false,
  "lotId": 1,
  "lotCode": "LOT-A",
  "lotName": "North Campus Lot",
  "startTime": "2026-10-29T22:00:00",
  "endTime": "2026-10-30T00:00:00",
  "status": "RESERVED",
  "checkedInAt": null,
  "checkedOutAt": null,
  "fee": null,
  "createdAt": "2026-10-29T21:55:00.123456"
}
```

### GET `/api/reservations`

Optional query parameters:

| Parameter | Example | Notes |
|---|---|---|
| `status` | `RESERVED` | One of the five reservation states |
| `vehicleNumber` | `TN01AB1234` | Case-insensitive contains |
| `lotId` | `1` | Only reservations in that lot |
| `q` | `RSV-7A08` | Reservation code, vehicle, space or lot code |

### GET `/api/reservations/{id}`

Returns one reservation or `404`.

### POST `/api/reservations/{id}/cancel`

Only `RESERVED` can be cancelled. A checked-in reservation must be checked out.

```json
{ "...": "same ReservationResponse with status CANCELLED" }
```

A cancellation publishes a release event; the waiting list is reconsidered
after the transaction commits.

### POST `/api/reservations/{id}/check-in`

Rules:

- Only `RESERVED`.
- Check-in opens 30 minutes before the start.
- The reservation window must not have ended.

Response status becomes `CHECKED_IN` and `checkedInAt` is set.

### POST `/api/reservations/{id}/check-out`

Rules:

- Only `CHECKED_IN`.
- Fee uses the check-in time and the simulated clock.
- Car ₹50/hour, two-wheeler ₹20/hour, minimum 1 hour, part hours rounded up.

Response status becomes `COMPLETED` with `checkedOutAt` and `fee`.

### POST `/api/reservations/expire`

Demo/admin endpoint. Expires every `RESERVED` reservation whose start time is
older than the grace period, then processes the waiting list.

```json
{ "expiredCount": 1, "allocatedFromWaitingList": 0 }
```

The same scan runs automatically in the background (default every 30 seconds).

---

## 6. Waiting list

### POST `/api/waiting-list`

```json
{
  "vehicleId": 2,
  "startTime": "2026-10-29T22:00:00",
  "endTime": "2026-10-30T00:00:00"
}
```

Response `201 Created`:

```json
{
  "id": 1,
  "queuePosition": 1,
  "vehicleId": 2,
  "vehicleNumber": "TN09PQ7788",
  "vehicleType": "CAR",
  "accessibilityPermit": true,
  "accessPriority": 1,
  "arrivalTime": "2026-10-29T21:56:00.123456",
  "startTime": "2026-10-29T22:00:00",
  "endTime": "2026-10-30T00:00:00",
  "status": "WAITING",
  "allocatedReservationId": null
}
```

A duplicate waiting request for the same vehicle and overlapping window returns
`409 Conflict`.

### GET `/api/waiting-list`

Returns only `WAITING` requests, already in priority order:

1. Higher `accessPriority` first (permit holder = 1, standard = 0).
2. Earlier `arrivalTime`.
3. Lower id.

### DELETE `/api/waiting-list/{id}`

Cancels a `WAITING` request. Returns `204 No Content`.

### POST `/api/waiting-list/process`

Tries to allocate every queued request now.

```json
{ "allocated": 1 }
```

Requests that still have no compatible space stay in the queue.

---

## 7. History

### GET `/api/history?limit=50&q=TN01`

| Parameter | Default | Notes |
|---|---|---|
| `limit` | `50` | Clamped to 1–200 |
| `q` | none | Searches vehicle, space, lot and message |

Response:

```json
[
  {
    "id": 12,
    "eventType": "CHECKED_OUT",
    "reservationId": 1,
    "vehicleNumber": "TN01AB1234",
    "spaceCode": "A-C-01",
    "lotCode": "LOT-A",
    "occurredAt": "2026-10-29T22:30:00",
    "message": "Checked out from A-C-01",
    "fee": 100.00
  }
]
```

Event types: `RESERVED`, `CANCELLED`, `CHECKED_IN`, `CHECKED_OUT`, `EXPIRED`,
`WAITING_JOINED`, `WAITING_ALLOCATED`, `WAITING_CANCELLED`, `WAITING_EXPIRED`.

---

## 8. Simulated clock

### GET `/api/system/clock`

```json
{
  "simulatedNow": "2026-10-29T22:10:00",
  "realNow": "2026-10-29T21:55:00",
  "offsetMinutes": 15
}
```

### POST `/api/system/clock/advance`

```json
{ "minutes": 15 }
```

`minutes` must be between 1 and 1440.

### POST `/api/system/clock/reset`

Sets the offset back to zero.

---

## 9. Quick `curl` examples

```bash
# Register a vehicle
curl -X POST http://localhost:8080/api/vehicles \
  -H "Content-Type: application/json" \
  -d '{"vehicleNumber":"TN77DEMO01","type":"CAR","ownerName":"Demo Student","accessibilityPermit":false}'

# Smart reservation
curl -X POST http://localhost:8080/api/reservations \
  -H "Content-Type: application/json" \
  -d '{"vehicleId":5,"startTime":"2026-10-29T22:00:00","endTime":"2026-10-30T00:00:00"}'

# Check in
curl -X POST http://localhost:8080/api/reservations/1/check-in

# Join the waiting list
curl -X POST http://localhost:8080/api/waiting-list \
  -H "Content-Type: application/json" \
  -d '{"vehicleId":2,"startTime":"2026-10-29T22:00:00","endTime":"2026-10-30T00:00:00"}'

# Advance the simulated clock
curl -X POST http://localhost:8080/api/system/clock/advance \
  -H "Content-Type: application/json" \
  -d '{"minutes":20}'
```
