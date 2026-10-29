-- ============================================================================
-- Smart Parking Allocation System - MySQL schema
-- Tested with MySQL 8.x and MariaDB 10.4 (XAMPP).
--
-- Usage (from the project root):
--   mysql -u root < database/schema.sql
--   mysql -u root < database/sample-data.sql
--
-- The Spring Boot application can also create the tables automatically with
-- spring.jpa.hibernate.ddl-auto=update. This script is provided for the
-- college report / manual database setup and uses the same names as the JPA
-- entities.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS smart_parking
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE smart_parking;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS parking_events;
DROP TABLE IF EXISTS waiting_requests;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS parking_spaces;
DROP TABLE IF EXISTS road_segments;
DROP TABLE IF EXISTS vehicles;
DROP TABLE IF EXISTS parking_lots;
SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------------------------------------------------------
-- Parking lots (3 lots in the sample data)
-- ----------------------------------------------------------------------------
CREATE TABLE parking_lots (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    address     VARCHAR(200) NOT NULL,
    travel_node VARCHAR(40)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lot_code UNIQUE (code),
    CONSTRAINT uk_lot_travel_node UNIQUE (travel_node)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- Weighted road graph used by Dijkstra (ENTRANCE -> LOT-A / LOT-B / LOT-C)
-- ----------------------------------------------------------------------------
CREATE TABLE road_segments (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    from_node      VARCHAR(40) NOT NULL,
    to_node        VARCHAR(40) NOT NULL,
    travel_minutes INT         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_road_pair UNIQUE (from_node, to_node),
    CONSTRAINT chk_road_minutes CHECK (travel_minutes > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- Vehicles (single-table inheritance: vehicle_kind = CAR / TWO_WHEELER)
-- ----------------------------------------------------------------------------
CREATE TABLE vehicles (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    vehicle_number       VARCHAR(20)  NOT NULL,
    owner_name           VARCHAR(100) NOT NULL,
    accessibility_permit BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           DATETIME(6)  NOT NULL,
    vehicle_kind         VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_vehicle_number UNIQUE (vehicle_number)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- Parking spaces (single-table inheritance:
-- space_kind = CAR / TWO_WHEELER / ACCESSIBLE)
-- ----------------------------------------------------------------------------
CREATE TABLE parking_spaces (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    code         VARCHAR(20) NOT NULL,
    lot_id       BIGINT      NOT NULL,
    floor_number INT         NOT NULL DEFAULT 0,
    space_kind   VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_space_code UNIQUE (code),
    CONSTRAINT chk_space_floor CHECK (floor_number >= 0),
    CONSTRAINT fk_space_lot FOREIGN KEY (lot_id) REFERENCES parking_lots (id),
    INDEX idx_space_lot (lot_id),
    INDEX idx_space_lot_floor (lot_id, floor_number)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- Reservations. Only RESERVED and CHECKED_IN rows block a time interval.
-- Half-open overlap rule: existing.start < requested.end AND existing.end > requested.start
-- ----------------------------------------------------------------------------
CREATE TABLE reservations (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    reservation_code VARCHAR(30)   NOT NULL,
    vehicle_id       BIGINT        NOT NULL,
    space_id         BIGINT        NOT NULL,
    start_time       DATETIME(6)   NOT NULL,
    end_time         DATETIME(6)   NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    checked_in_at    DATETIME(6)   NULL,
    checked_out_at   DATETIME(6)   NULL,
    fee              DECIMAL(10, 2) NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_reservation_code UNIQUE (reservation_code),
    CONSTRAINT chk_reservation_window CHECK (end_time > start_time),
    CONSTRAINT fk_reservation_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT fk_reservation_space FOREIGN KEY (space_id) REFERENCES parking_spaces (id),
    INDEX idx_reservation_space_time (space_id, status, start_time, end_time),
    INDEX idx_reservation_vehicle_time (vehicle_id, status, start_time, end_time),
    INDEX idx_reservation_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- Waiting-list requests. The in-memory PriorityQueue is rebuilt from this
-- table on startup, so a restart does not lose the queue.
-- ----------------------------------------------------------------------------
CREATE TABLE waiting_requests (
    id                      BIGINT      NOT NULL AUTO_INCREMENT,
    vehicle_id              BIGINT      NOT NULL,
    requested_start_time    DATETIME(6) NOT NULL,
    requested_end_time      DATETIME(6) NOT NULL,
    access_priority         INT         NOT NULL DEFAULT 0,
    arrival_time            DATETIME(6) NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    allocated_reservation_id BIGINT     NULL,
    created_at              DATETIME(6) NOT NULL,
    updated_at              DATETIME(6) NOT NULL,
    version                 BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT chk_waiting_window CHECK (requested_end_time > requested_start_time),
    CONSTRAINT chk_waiting_priority CHECK (access_priority >= 0),
    CONSTRAINT fk_waiting_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    INDEX idx_waiting_status_arrival (status, arrival_time),
    INDEX idx_waiting_vehicle (vehicle_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- Audit trail. reservation_id is intentionally not a foreign key so history
-- survives even if a reservation row is removed during maintenance.
-- ----------------------------------------------------------------------------
CREATE TABLE parking_events (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    event_type     VARCHAR(30)   NOT NULL,
    reservation_id BIGINT        NULL,
    vehicle_number VARCHAR(20)   NULL,
    space_code     VARCHAR(20)   NULL,
    lot_code       VARCHAR(20)   NULL,
    occurred_at    DATETIME(6)   NOT NULL,
    message        VARCHAR(255)  NULL,
    fee            DECIMAL(10, 2) NULL,
    PRIMARY KEY (id),
    INDEX idx_event_occurred (occurred_at),
    INDEX idx_event_vehicle (vehicle_number)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
