# Smart Parking Allocation System

A complete Smart Parking Allocation System mini-project built with **Java 17 + Spring Boot + MySQL + HTML/CSS/JavaScript + Maven**. It upgrades the original console parking program into a browser-based dashboard while keeping every business rule in the Java backend.

> The frontend only fetches data and sends commands. Allocation, permit checks, overlap detection, waiting-list priority and fee calculation are all implemented in Java.

---

## 1. Features

### Parking setup
- Three connected lots: **LOT-A (8 spaces)**, **LOT-B (9 spaces)**, **LOT-C (7 spaces)** — 24 spaces in total.
- Car spaces, two-wheeler spaces and accessible spaces.
- Live dashboard with total, available, occupied and reserved counts.
- Visual space grid with lot/type/status/search filters.
- Space detail popup showing the active reservation and walk time.

### Vehicles
- Register a vehicle with number, owner, type and accessibility permit.
- Vehicle numbers are normalised (spaces removed, uppercase) and unique.
- Grant or remove an accessibility permit later; waiting-list priority is recalculated automatically.

### Smart allocation
- Rejects incompatible vehicle/space pairs (for example, a car cannot use a two-wheeler space).
- Accessible spaces require a valid accessibility permit.
- Checks overlapping active reservations using the half-open interval rule.
- Ranks eligible spaces by **nearest lot using Dijkstra shortest travel time**, then floor, then space code.
- Optional preferred space for demonstrations and conflict testing.
- Concurrency protection: vehicle row lock, space row lock, `READ_COMMITTED`, authoritative overlap re-check, and optimistic version on reservations.

### Reservations
- Start/end time validation (maximum 24 hours, up to 30 days ahead, at most 5 minutes in the past).
- Reservation states: `RESERVED -> CHECKED_IN -> COMPLETED`, plus `CANCELLED` and `EXPIRED`.
- Cancel, check-in and check-out actions.
- Configurable expiry grace period (default 15 minutes) and a background expiry scanner.
- Simulated clock so expiry and fees can be demonstrated without waiting in real time.
- Fee rules: car **₹50/hour**, two-wheeler **₹20/hour**, minimum 1 hour, part hours rounded up.

### Waiting list
- Java `PriorityQueue<WaitingTicket>` backed by MySQL `waiting_requests`.
- Permit holders rank first, then earlier arrival time, then lower id.
- Rebuilt from MySQL on startup, so restarts do not lose the queue.
- `HashMap` index for O(1) ticket lookup/removal.
- Reconsidered automatically after a reservation is cancelled, checked out or expired.
- Never allocates an incompatible or overlapping space.

### History and audit
- Every reservation, cancellation, check-in, check-out, expiry and waiting-list event is recorded.
- Searchable event history with fee information.

---

## 2. Technology stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Backend framework | Spring Boot 3.3.5 |
| Web | Spring MVC REST controllers |
| Persistence | Spring Data JPA + Hibernate |
| Database | MySQL 8.x / MariaDB 10.4+ (XAMPP is fine) |
| Validation | Jakarta Bean Validation |
| Frontend | HTML5, CSS3, vanilla JavaScript |
| Build | Maven |
| Tests | JUnit 5, Spring Boot Test, MockMvc, H2 (MySQL mode) |

No Python, Node.js, PHP or other backend language is used.

---

## 3. Project structure

```text
smart-parking-system/
├── pom.xml
├── database/
│   ├── schema.sql              # MySQL tables, keys, indexes, checks
│   └── sample-data.sql         # 3 lots, 24 spaces, roads, demo vehicles
├── docs/
│   ├── API.md                  # REST API reference
│   ├── DESIGN.md               # Architecture, DSA and OOP explanations
│   ├── TEST-CASES.md           # Test cases and expected/actual results
│   └── UML.md                  # UML class diagram
├── src/
│   ├── main/
│   │   ├── java/com/smartparking/
│   │   │   ├── SmartParkingApplication.java
│   │   │   ├── algorithm/      # Dijkstra, graph, priority policy, parking index
│   │   │   ├── config/         # DataInitializer
│   │   │   ├── controller/     # REST controllers
│   │   │   ├── domain/         # JPA entities + enums
│   │   │   ├── dto/            # Request/response records
│   │   │   ├── event/          # Release / priority events
│   │   │   ├── exception/      # Business errors + global handler
│   │   │   ├── repository/     # Spring Data repositories
│   │   │   ├── scheduler/      # Reservation expiry job
│   │   │   └── service/        # Business logic
│   │   └── resources/
│   │       ├── application.properties
│   │       └── static/         # index.html, css/styles.css, js/app.js
│   └── test/
│       ├── java/com/smartparking/SmartParkingIntegrationTest.java
│       └── resources/application-test.properties
└── target/                     # build output (generated)
```

The original console project remains untouched in `../SmartParkingSystem/`.

---

## 4. Prerequisites

- **JDK 17** (`java -version`)
- **Maven 3.9+** (`mvn -version`)
- **MySQL 8.x** or **MariaDB 10.4+**
  - XAMPP users: start MySQL from the XAMPP Control Panel, or run
    `C:\xampp\mysql\bin\mysqld.exe --defaults-file=C:\xampp\mysql\bin\my.ini --standalone`

---

## 5. Database setup

The default connection is in `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/smart_parking?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata&characterEncoding=utf8
spring.datasource.username=root
spring.datasource.password=
```

Change the username/password if your MySQL installation uses different credentials.

### Option A — automatic setup (easiest)

Just start the application. Hibernate creates/updates the tables (`spring.jpa.hibernate.ddl-auto=update`) and `DataInitializer` seeds the three lots, 24 spaces, roads and demo vehicles when the tables are empty.

### Option B — run the SQL scripts manually

Linux / macOS / Git Bash:

```bash
mysql -u root -p < database/schema.sql
mysql -u root -p < database/sample-data.sql
```

Windows PowerShell (XAMPP):

```powershell
Get-Content database\schema.sql | C:\xampp\mysql\bin\mysql.exe -u root
Get-Content database\sample-data.sql | C:\xampp\mysql\bin\mysql.exe -u root
```

Or open both files in MySQL Workbench/phpMyAdmin and execute them in order.

The sample data is safe to re-run. It creates:

- 3 lots, 24 spaces (13 car, 8 two-wheeler, 3 accessible)
- 6 road segments for Dijkstra
- 4 demo vehicles (`TN01AB1234`, `TN09PQ7788`, `TN22XY4455`, `TN55CD9090`)

### Reset the demo data

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

Then run `database/sample-data.sql` again, or restart the application and let `DataInitializer` seed it.

---

## 6. Run the application

From the `smart-parking-system` directory:

```bash
mvn clean spring-boot:run
```

Open **http://localhost:8080** in a browser.

Alternative packaged run:

```bash
mvn clean package
java -jar target/smart-parking-system-1.0.0.jar
```

The dashboard, REST API and static files are served by the same Spring Boot process, so there is no CORS or separate frontend server.

---

## 7. Suggested college demonstration (5–10 minutes)

1. **Show the dashboard** — 3 lots, 24 spaces, all available. Point out the Dijkstra travel-time chip (`LOT-A 3 min`, `LOT-B 7 min`, `LOT-C 11 min`).
2. **Register a car** — for example `TN77DEMO01`, owner `Demo Student`, type `Car`, no permit.
3. **Reserve the best space** — leave “Preferred space” on *Smart allocation*. The backend chooses `A-C-01` in the nearest lot (`LOT-A`).
4. **Check in** — click *Check in*. The space turns red/occupied.
5. **Advance the clock** — click `+60 min` twice, then *Check out*. The fee is calculated from the simulated clock (car: ₹50/hour, part hours rounded up).
6. **Show a conflict** — reserve a specific space, then try to reserve the same space and window with another vehicle. The API returns **409 Conflict** and the UI shows a clear message.
7. **Show the waiting list** — try a preferred space that is already taken, then click *Join waiting list*. Cancel the blocking reservation. The after-commit listener allocates the released space to the waiting request automatically.
8. **Show expiry** — create a reservation that starts in about 1 minute, advance the clock by 20 minutes, and click *Expire unclaimed now*. The reservation becomes `EXPIRED` and the waiting list is reconsidered.
9. **Show priority** — register a vehicle with an accessibility permit and join the waiting list. Permit holders appear before standard requests (priority `P1` vs `P0`).
10. **Show history** — every action appears in the History table.

### Useful manual API checks

```powershell
Invoke-RestMethod http://localhost:8080/api/dashboard
Invoke-RestMethod http://localhost:8080/api/lots
Invoke-RestMethod http://localhost:8080/api/spaces
Invoke-RestMethod http://localhost:8080/api/waiting-list
Invoke-RestMethod http://localhost:8080/api/history
```

---

## 8. Business rules at a glance

| Rule | Behaviour |
|---|---|
| Vehicle number | Normalised (spaces removed, uppercase), unique |
| Compatibility | Car -> car/accessible; two-wheeler -> two-wheeler/accessible |
| Permit | Accessible spaces require `accessibilityPermit = true` |
| Overlap | `existing.start < requested.end AND existing.end > requested.start` |
| Blocking statuses | `RESERVED`, `CHECKED_IN` |
| Reservation duration | Maximum 24 hours |
| Advance booking | Maximum 30 days ahead |
| Past booking | At most 5 minutes in the past |
| Expiry | Unclaimed `RESERVED` older than 15 minutes after start |
| Check-in | Opens 30 minutes before the start |
| Check-out | Only from `CHECKED_IN`; fee uses check-in time |
| Waiting order | Permit holder first, then arrival time, then id |
| Fee | Car ₹50/h, two-wheeler ₹20/h, minimum 1 h, round up |

---

## 9. Tests

```bash
mvn test
```

The integration suite uses an in-memory H2 database in MySQL mode and a fresh Spring context per test. It covers:

- Seed data and dashboard counts
- Dijkstra travel times
- Smart nearest-space allocation
- Overlapping space and vehicle reservations
- Incompatible vehicle type
- Missing accessibility permit
- Invalid reservation window
- Duplicate/invalid vehicle registration
- Full lot + waiting-list allocation after cancellation
- Waiting-list priority order
- Reservation expiry after the grace period
- Check-in/check-out fee calculation
- Simulated clock advance/reset

Latest result:

```text
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

See `docs/TEST-CASES.md` for the full table of test cases and expected outputs.

---

## 10. Documentation index

- `docs/API.md` — endpoint reference with request/response examples and HTTP status codes.
- `docs/UML.md` — UML class diagram (Mermaid) and relationship notes.
- `docs/DESIGN.md` — layered architecture, allocation flow, DSA/OOP explanations, complexity.
- `docs/TEST-CASES.md` — test cases, expected results and actual result from `mvn test`.

---

## 11. Troubleshooting

| Problem | Fix |
|---|---|
| `Communications link failure` | MySQL/MariaDB is not running. Start it from XAMPP or the service manager. |
| `Access denied for user 'root'` | Set the correct username/password in `application.properties`. |
| Port 8080 already in use | Stop the other process or set `server.port=8081`. |
| Tables look out of date | Run `database/schema.sql` manually, or set `spring.jpa.hibernate.ddl-auto=update`. |
| Waiting list looks empty after restart | It is rebuilt from `waiting_requests`; only `WAITING` rows are loaded. |
| Dates look different from the wall clock | The simulated clock has an offset. Click **Reset clock** on the dashboard. |
| `mvn` not recognised | Install Maven 3.9+ or use an IDE (IntelliJ/Eclipse/VS Code) with Maven support. |
