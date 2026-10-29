-- ============================================================================
-- Smart Parking Allocation System - sample data
--
-- Run after database/schema.sql:
--   mysql -u root < database/schema.sql
--   mysql -u root < database/sample-data.sql
--
-- This file is safe to run more than once. The Spring Boot DataInitializer
-- also seeds the same master data when the tables are empty, so you can skip
-- this file and let the application do it automatically.
-- ============================================================================

USE smart_parking;

-- ----------------------------------------------------------------------------
-- Three connected parking lots
-- ----------------------------------------------------------------------------
INSERT INTO parking_lots (id, code, name, address, travel_node) VALUES
    (1, 'LOT-A', 'North Campus Lot', 'Near main gate and academic block', 'LOT-A'),
    (2, 'LOT-B', 'Library Lot', 'Beside central library and study halls', 'LOT-B'),
    (3, 'LOT-C', 'Sports Complex Lot', 'Next to the sports ground and gym', 'LOT-C')
ON DUPLICATE KEY UPDATE
    code = VALUES(code),
    name = VALUES(name),
    address = VALUES(address),
    travel_node = VALUES(travel_node);

-- ----------------------------------------------------------------------------
-- 24 spaces: 13 car, 8 two-wheeler and 3 accessible
-- Accessible spaces require a vehicle with accessibility_permit = TRUE.
-- ----------------------------------------------------------------------------
INSERT INTO parking_spaces (id, code, lot_id, floor_number, space_kind) VALUES
    -- LOT-A (8)
    (1,  'A-C-01', 1, 0, 'CAR'),
    (2,  'A-C-02', 1, 0, 'CAR'),
    (3,  'A-C-03', 1, 0, 'CAR'),
    (4,  'A-T-01', 1, 0, 'TWO_WHEELER'),
    (5,  'A-T-02', 1, 0, 'TWO_WHEELER'),
    (6,  'A-A-01', 1, 0, 'ACCESSIBLE'),
    (7,  'A-C-04', 1, 1, 'CAR'),
    (8,  'A-C-05', 1, 1, 'CAR'),

    -- LOT-B (9)
    (9,  'B-C-01', 2, 0, 'CAR'),
    (10, 'B-C-02', 2, 0, 'CAR'),
    (11, 'B-C-03', 2, 0, 'CAR'),
    (12, 'B-C-04', 2, 0, 'CAR'),
    (13, 'B-T-01', 2, 0, 'TWO_WHEELER'),
    (14, 'B-T-02', 2, 0, 'TWO_WHEELER'),
    (15, 'B-T-03', 2, 0, 'TWO_WHEELER'),
    (16, 'B-A-01', 2, 0, 'ACCESSIBLE'),
    (17, 'B-C-05', 2, 1, 'CAR'),

    -- LOT-C (7)
    (18, 'C-C-01', 3, 0, 'CAR'),
    (19, 'C-C-02', 3, 0, 'CAR'),
    (20, 'C-C-03', 3, 0, 'CAR'),
    (21, 'C-T-01', 3, 0, 'TWO_WHEELER'),
    (22, 'C-T-02', 3, 0, 'TWO_WHEELER'),
    (23, 'C-A-01', 3, 0, 'ACCESSIBLE'),
    (24, 'C-T-03', 3, 1, 'TWO_WHEELER')
ON DUPLICATE KEY UPDATE
    code = VALUES(code),
    lot_id = VALUES(lot_id),
    floor_number = VALUES(floor_number),
    space_kind = VALUES(space_kind);

-- ----------------------------------------------------------------------------
-- Weighted, undirected road graph. Dijkstra from ENTRANCE gives:
-- LOT-A = 3, LOT-B = 7, LOT-C = 11 (via LOT-B, shorter than direct 12).
-- ----------------------------------------------------------------------------
INSERT INTO road_segments (id, from_node, to_node, travel_minutes) VALUES
    (1, 'ENTRANCE', 'LOT-A', 3),
    (2, 'ENTRANCE', 'LOT-B', 7),
    (3, 'ENTRANCE', 'LOT-C', 12),
    (4, 'LOT-A', 'LOT-B', 5),
    (5, 'LOT-B', 'LOT-C', 4),
    (6, 'LOT-A', 'LOT-C', 9)
ON DUPLICATE KEY UPDATE
    from_node = VALUES(from_node),
    to_node = VALUES(to_node),
    travel_minutes = VALUES(travel_minutes);

-- ----------------------------------------------------------------------------
-- Demo vehicles (two permit holders)
-- ----------------------------------------------------------------------------
INSERT INTO vehicles (id, vehicle_number, owner_name, accessibility_permit, created_at, vehicle_kind) VALUES
    (1, 'TN01AB1234', 'Aarav Kumar',  FALSE, NOW(6), 'CAR'),
    (2, 'TN09PQ7788', 'Meera Iyer',   TRUE,  NOW(6), 'CAR'),
    (3, 'TN22XY4455', 'Rahul Das',    FALSE, NOW(6), 'TWO_WHEELER'),
    (4, 'TN55CD9090', 'Priya Nair',   TRUE,  NOW(6), 'CAR')
ON DUPLICATE KEY UPDATE
    vehicle_number = VALUES(vehicle_number),
    owner_name = VALUES(owner_name),
    accessibility_permit = VALUES(accessibility_permit),
    vehicle_kind = VALUES(vehicle_kind);

-- ----------------------------------------------------------------------------
-- Optional: a couple of sample history rows so the History table is not empty.
-- Reservations themselves are created through the dashboard/API, which runs
-- the real allocation and overlap checks.
-- ----------------------------------------------------------------------------
INSERT INTO parking_events
    (event_type, reservation_id, vehicle_number, space_code, lot_code, occurred_at, message, fee)
SELECT 'RESERVED', NULL, 'TN01AB1234', 'A-C-01', 'LOT-A', NOW(6),
       'Sample event: A-C-01 was reserved in LOT-A', NULL
WHERE NOT EXISTS (SELECT 1 FROM parking_events);
