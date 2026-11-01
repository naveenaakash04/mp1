/*
 * Static demo backend for GitHub Pages.
 *
 * The real project uses Spring Boot + MySQL. GitHub Pages can only serve
 * static files, so this file intercepts /api/* requests and reproduces the
 * same behaviour in the browser. Data is stored in localStorage.
 */
(function () {
    'use strict';

    const STORAGE_KEY = 'smart-parking-static-demo-v1';
    const GRACE_MINUTES = 15;
    const CHECK_IN_EARLY_MINUTES = 30;
    const MAX_DURATION_HOURS = 24;
    const MAX_ADVANCE_DAYS = 30;
    const PAST_GRACE_MINUTES = 5;

    const LOT_DEFS = [
        {
            id: 1,
            code: 'LOT-A',
            name: 'North Campus Lot',
            address: 'Near main gate and academic block',
            travelMinutes: 3,
            spaces: [
                ['A-C-01', 'CAR', 0], ['A-C-02', 'CAR', 0], ['A-C-03', 'CAR', 0],
                ['A-T-01', 'TWO_WHEELER', 0], ['A-T-02', 'TWO_WHEELER', 0],
                ['A-A-01', 'ACCESSIBLE', 0],
                ['A-C-04', 'CAR', 1], ['A-C-05', 'CAR', 1]
            ]
        },
        {
            id: 2,
            code: 'LOT-B',
            name: 'Library Lot',
            address: 'Beside central library and study halls',
            travelMinutes: 7,
            spaces: [
                ['B-C-01', 'CAR', 0], ['B-C-02', 'CAR', 0], ['B-C-03', 'CAR', 0],
                ['B-C-04', 'CAR', 0],
                ['B-T-01', 'TWO_WHEELER', 0], ['B-T-02', 'TWO_WHEELER', 0],
                ['B-T-03', 'TWO_WHEELER', 0],
                ['B-A-01', 'ACCESSIBLE', 0],
                ['B-C-05', 'CAR', 1]
            ]
        },
        {
            id: 3,
            code: 'LOT-C',
            name: 'Sports Complex Lot',
            address: 'Next to the sports ground and gym',
            travelMinutes: 11,
            spaces: [
                ['C-C-01', 'CAR', 0], ['C-C-02', 'CAR', 0], ['C-C-03', 'CAR', 0],
                ['C-T-01', 'TWO_WHEELER', 0], ['C-T-02', 'TWO_WHEELER', 0],
                ['C-A-01', 'ACCESSIBLE', 0],
                ['C-T-03', 'TWO_WHEELER', 1]
            ]
        }
    ];

    const SPACES = [];
    LOT_DEFS.forEach((lot) => {
        lot.spaces.forEach(([code, type, floorNumber]) => {
            SPACES.push({
                id: SPACES.length + 1,
                code,
                type,
                floorNumber,
                lotId: lot.id,
                lotCode: lot.code,
                lotName: lot.name,
                travelMinutes: lot.travelMinutes
            });
        });
    });

    function httpError(status, message, details) {
        const error = new Error(message);
        error.status = status;
        error.details = details || [];
        return error;
    }

    function defaultState() {
        const createdAt = new Date().toISOString();
        return {
            version: 1,
            vehicles: [
                { id: 1, vehicleNumber: 'TN01AB1234', type: 'CAR', ownerName: 'Aarav Kumar', accessibilityPermit: false, createdAt },
                { id: 2, vehicleNumber: 'TN09PQ7788', type: 'CAR', ownerName: 'Meera Iyer', accessibilityPermit: true, createdAt },
                { id: 3, vehicleNumber: 'TN22XY4455', type: 'TWO_WHEELER', ownerName: 'Rahul Das', accessibilityPermit: false, createdAt },
                { id: 4, vehicleNumber: 'TN55CD9090', type: 'CAR', ownerName: 'Priya Nair', accessibilityPermit: true, createdAt }
            ],
            reservations: [],
            waiting: [],
            events: [],
            clockOffsetMinutes: 0,
            nextVehicleId: 5,
            nextReservationId: 1,
            nextWaitingId: 1,
            nextEventId: 1
        };
    }

    function loadState() {
        try {
            const raw = window.localStorage.getItem(STORAGE_KEY);
            if (raw) {
                const parsed = JSON.parse(raw);
                if (parsed && parsed.version === 1) {
                    return parsed;
                }
            }
        } catch (ignored) {
            // Start fresh when storage is unavailable or corrupt.
        }
        return defaultState();
    }

    const state = loadState();

    function persist() {
        try {
            window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
        } catch (ignored) {
            // The demo still works for this page load when storage is full.
        }
    }

    function simulatedNow() {
        return new Date(Date.now() + Number(state.clockOffsetMinutes || 0) * 60000);
    }

    function toDate(value) {
        const date = value instanceof Date ? value : new Date(value);
        return Number.isNaN(date.getTime()) ? null : date;
    }

    function randomHex(length) {
        const bytes = new Uint8Array(Math.ceil(length / 2));
        if (window.crypto && window.crypto.getRandomValues) {
            window.crypto.getRandomValues(bytes);
        } else {
            for (let i = 0; i < bytes.length; i += 1) {
                bytes[i] = Math.floor(Math.random() * 256);
            }
        }
        return Array.from(bytes).map((byte) => byte.toString(16).padStart(2, '0')).join('').slice(0, length).toUpperCase();
    }

    function findVehicle(id) {
        const vehicle = state.vehicles.find((item) => item.id === Number(id));
        if (!vehicle) {
            throw httpError(404, 'Vehicle was not found.');
        }
        return vehicle;
    }

    function addEvent(eventType, reservation, message, fee, extra) {
        const event = {
            id: state.nextEventId++,
            eventType,
            reservationId: reservation ? reservation.id : (extra && extra.reservationId) || null,
            vehicleNumber: reservation ? reservation.vehicleNumber : (extra && extra.vehicleNumber) || null,
            spaceCode: reservation ? reservation.spaceCode : (extra && extra.spaceCode) || null,
            lotCode: reservation ? reservation.lotCode : (extra && extra.lotCode) || null,
            occurredAt: simulatedNow().toISOString(),
            message: message || '',
            fee: fee === undefined ? null : fee
        };
        state.events.push(event);
        return event;
    }

    function overlaps(aStart, aEnd, bStart, bEnd) {
        return aStart < bEnd && aEnd > bStart;
    }

    function blockingReservations() {
        return state.reservations.filter((item) => item.status === 'RESERVED' || item.status === 'CHECKED_IN');
    }

    function spaceHasOverlap(spaceId, start, end) {
        return blockingReservations().some((item) => item.spaceId === spaceId
            && overlaps(start, end, new Date(item.startTime), new Date(item.endTime)));
    }

    function vehicleHasOverlap(vehicleId, start, end) {
        return blockingReservations().some((item) => item.vehicleId === vehicleId
            && overlaps(start, end, new Date(item.startTime), new Date(item.endTime)));
    }

    function isCompatible(vehicle, space) {
        if (space.type === 'ACCESSIBLE') {
            return vehicle.accessibilityPermit === true;
        }
        if (space.type === 'CAR') {
            return vehicle.type === 'CAR';
        }
        if (space.type === 'TWO_WHEELER') {
            return vehicle.type === 'TWO_WHEELER';
        }
        return false;
    }

    function validateWindow(startValue, endValue) {
        const start = toDate(startValue);
        const end = toDate(endValue);
        if (!start || !end) {
            throw httpError(400, 'Choose valid start and end times.');
        }
        if (start >= end) {
            throw httpError(400, 'End time must be after start time.');
        }
        const now = simulatedNow();
        if (end - start > MAX_DURATION_HOURS * 60 * 60000) {
            throw httpError(400, 'Reservation duration cannot exceed 24 hours.');
        }
        if (start > new Date(now.getTime() + MAX_ADVANCE_DAYS * 24 * 60 * 60000)) {
            throw httpError(400, 'Reservations can be booked at most 30 days ahead.');
        }
        if (start < new Date(now.getTime() - PAST_GRACE_MINUTES * 60000)) {
            throw httpError(400, 'Start time cannot be more than 5 minutes in the past.');
        }
        return { start, end };
    }

    function rankedCandidates(vehicle, start, end) {
        return SPACES
            .filter((space) => isCompatible(vehicle, space) && !spaceHasOverlap(space.id, start, end))
            .sort((a, b) => a.travelMinutes - b.travelMinutes
                || a.floorNumber - b.floorNumber
                || a.code.localeCompare(b.code));
    }

    function chooseSpace(vehicle, start, end, preferredSpaceId) {
        if (preferredSpaceId) {
            const preferred = SPACES.find((space) => space.id === Number(preferredSpaceId));
            if (!preferred) {
                throw httpError(404, 'Parking space was not found.');
            }
            if (!isCompatible(vehicle, preferred)) {
                throw httpError(409, preferred.type === 'ACCESSIBLE'
                    ? 'Accessible spaces require a vehicle with an accessibility permit.'
                    : 'This vehicle type cannot use the selected space.');
            }
            if (spaceHasOverlap(preferred.id, start, end)) {
                throw httpError(409, 'That space is already reserved for the selected window.');
            }
            return preferred;
        }
        const candidates = rankedCandidates(vehicle, start, end);
        if (!candidates.length) {
            throw httpError(409, 'No compatible space is available for the selected window.');
        }
        return candidates[0];
    }

    function currentReservationForSpace(spaceId, now) {
        let found = null;
        state.reservations.forEach((item) => {
            if (item.spaceId !== spaceId) {
                return;
            }
            if (item.status === 'CHECKED_IN') {
                found = item;
            } else if (item.status === 'RESERVED'
                && new Date(item.startTime) <= now
                && new Date(item.endTime) > now
                && !found) {
                found = item;
            }
        });
        return found;
    }

    function spaceResponse(space, now) {
        const reservation = currentReservationForSpace(space.id, now);
        const status = reservation
            ? (reservation.status === 'CHECKED_IN' ? 'OCCUPIED' : 'RESERVED')
            : 'AVAILABLE';
        return {
            id: space.id,
            code: space.code,
            type: space.type,
            floorNumber: space.floorNumber,
            requiresPermit: space.type === 'ACCESSIBLE',
            status,
            activeReservationId: reservation ? reservation.id : null,
            activeVehicleNumber: reservation ? reservation.vehicleNumber : null,
            reservationStart: reservation ? reservation.startTime : null,
            reservationEnd: reservation ? reservation.endTime : null,
            lotId: space.lotId,
            lotCode: space.lotCode,
            lotName: space.lotName,
            travelMinutes: space.travelMinutes
        };
    }

    function lotResponse(lot, now) {
        const spaces = SPACES
            .filter((space) => space.lotId === lot.id)
            .sort((a, b) => a.floorNumber - b.floorNumber || a.code.localeCompare(b.code))
            .map((space) => spaceResponse(space, now));
        let available = 0;
        let occupied = 0;
        let reserved = 0;
        spaces.forEach((space) => {
            if (space.status === 'OCCUPIED') {
                occupied += 1;
            } else if (space.status === 'RESERVED') {
                reserved += 1;
            } else {
                available += 1;
            }
        });
        return {
            id: lot.id,
            code: lot.code,
            name: lot.name,
            address: lot.address,
            travelMinutes: lot.travelMinutes,
            totalSpaces: spaces.length,
            availableSpaces: available,
            occupiedSpaces: occupied,
            reservedSpaces: reserved,
            spaces
        };
    }

    function dashboardResponse(now) {
        let available = 0;
        let occupied = 0;
        let reserved = 0;
        SPACES.forEach((space) => {
            const reservation = currentReservationForSpace(space.id, now);
            if (!reservation) {
                available += 1;
            } else if (reservation.status === 'CHECKED_IN') {
                occupied += 1;
            } else {
                reserved += 1;
            }
        });
        return {
            totalSpaces: SPACES.length,
            availableSpaces: available,
            occupiedSpaces: occupied,
            reservedSpaces: reserved,
            totalLots: LOT_DEFS.length,
            activeReservations: state.reservations.filter((item) => item.status === 'RESERVED'
                || item.status === 'CHECKED_IN').length,
            waitingRequests: state.waiting.filter((item) => item.status === 'WAITING').length,
            simulatedNow: now.toISOString(),
            clockOffsetMinutes: Number(state.clockOffsetMinutes || 0)
        };
    }

    function vehicleResponse(vehicle) {
        return {
            id: vehicle.id,
            vehicleNumber: vehicle.vehicleNumber,
            type: vehicle.type,
            ownerName: vehicle.ownerName,
            accessibilityPermit: vehicle.accessibilityPermit,
            createdAt: vehicle.createdAt
        };
    }

    function waitingQueue() {
        return state.waiting
            .filter((item) => item.status === 'WAITING')
            .slice()
            .sort((a, b) => b.accessPriority - a.accessPriority
                || new Date(a.arrivalTime) - new Date(b.arrivalTime)
                || a.id - b.id);
    }

    function waitingResponse(item, position) {
        return {
            id: item.id,
            queuePosition: position,
            vehicleId: item.vehicleId,
            vehicleNumber: item.vehicleNumber,
            vehicleType: item.vehicleType,
            accessibilityPermit: item.accessibilityPermit,
            accessPriority: item.accessPriority,
            arrivalTime: item.arrivalTime,
            startTime: item.startTime,
            endTime: item.endTime,
            status: item.status,
            allocatedReservationId: item.allocatedReservationId
        };
    }

    function createReservation(vehicle, space, start, end, eventType, message) {
        const reservation = {
            id: state.nextReservationId++,
            reservationCode: 'RSV-' + randomHex(8),
            vehicleId: vehicle.id,
            vehicleNumber: vehicle.vehicleNumber,
            vehicleType: vehicle.type,
            ownerName: vehicle.ownerName,
            spaceId: space.id,
            spaceCode: space.code,
            spaceType: space.type,
            spaceRequiresPermit: space.type === 'ACCESSIBLE',
            lotId: space.lotId,
            lotCode: space.lotCode,
            lotName: space.lotName,
            startTime: start.toISOString(),
            endTime: end.toISOString(),
            status: 'RESERVED',
            checkedInAt: null,
            checkedOutAt: null,
            fee: null,
            createdAt: simulatedNow().toISOString()
        };
        state.reservations.push(reservation);
        addEvent(eventType || 'RESERVED', reservation,
            message || `Reserved ${space.code} in ${space.lotCode}`, null);
        return reservation;
    }

    function expireReservations() {
        const now = simulatedNow();
        let count = 0;
        let waitingExpired = 0;
        state.reservations.forEach((item) => {
            if (item.status === 'RESERVED'
                && now > new Date(new Date(item.startTime).getTime() + GRACE_MINUTES * 60000)) {
                item.status = 'EXPIRED';
                addEvent('EXPIRED', item, `Reservation expired for ${item.spaceCode}`, null);
                count += 1;
            }
        });
        state.waiting.forEach((item) => {
            if (item.status === 'WAITING' && new Date(item.endTime) <= now) {
                item.status = 'EXPIRED';
                addEvent('WAITING_EXPIRED', null, `Waiting request expired for ${item.vehicleNumber}`,
                    null, { vehicleNumber: item.vehicleNumber });
                waitingExpired += 1;
            }
        });
        if (count > 0 || waitingExpired > 0) {
            persist();
        }
        return count;
    }

    function processWaitingInternal() {
        const queue = waitingQueue();
        let allocated = 0;
        queue.forEach((item) => {
            const vehicle = state.vehicles.find((candidate) => candidate.id === item.vehicleId);
            if (!vehicle) {
                item.status = 'CANCELLED';
                return;
            }
            const start = new Date(item.startTime);
            const end = new Date(item.endTime);
            if (end <= simulatedNow() || vehicleHasOverlap(vehicle.id, start, end)) {
                return;
            }
            const candidates = rankedCandidates(vehicle, start, end);
            if (!candidates.length) {
                return;
            }
            const reservation = createReservation(vehicle, candidates[0], start, end,
                'WAITING_ALLOCATED',
                `Waiting request allocated ${candidates[0].code} in ${candidates[0].lotCode}`);
            item.status = 'ALLOCATED';
            item.allocatedReservationId = reservation.id;
            allocated += 1;
        });
        if (allocated > 0) {
            persist();
        }
        return allocated;
    }

    function refreshExpired() {
        const expired = expireReservations();
        if (expired > 0) {
            processWaitingInternal();
        }
    }

    function clockResponse() {
        return {
            simulatedNow: simulatedNow().toISOString(),
            realNow: new Date().toISOString(),
            offsetMinutes: Number(state.clockOffsetMinutes || 0)
        };
    }

    function bodyOf(init) {
        if (!init || init.body === undefined || init.body === null) {
            return {};
        }
        try {
            return JSON.parse(init.body);
        } catch (ignored) {
            throw httpError(400, 'Request body must be valid JSON.');
        }
    }

    function respond(body, status) {
        if (status === 204) {
            return new Response(null, { status: 204 });
        }
        return new Response(JSON.stringify(body), {
            status: status || 200,
            headers: { 'Content-Type': 'application/json' }
        });
    }

    async function handle(path, searchParams, method, init) {
        const now = simulatedNow();

        if (method === 'GET' && path === '/api/dashboard') {
            return respond(dashboardResponse(now));
        }

        if (method === 'GET' && path === '/api/lots') {
            return respond(LOT_DEFS.map((lot) => lotResponse(lot, now)));
        }

        if (method === 'GET' && path === '/api/lots/travel-times') {
            return respond({
                source: 'ENTRANCE',
                lots: LOT_DEFS.map((lot) => ({
                    lotId: lot.id,
                    lotCode: lot.code,
                    lotName: lot.name,
                    minutes: lot.travelMinutes
                }))
            });
        }

        if (method === 'GET' && path === '/api/spaces') {
            const lotId = searchParams.get('lotId');
            const kind = searchParams.get('kind');
            const status = searchParams.get('status');
            const query = (searchParams.get('q') || '').trim().toLowerCase();
            const result = SPACES
                .slice()
                .sort((a, b) => a.lotId - b.lotId || a.floorNumber - b.floorNumber || a.code.localeCompare(b.code))
                .map((space) => spaceResponse(space, now))
                .filter((space) => {
                    if (lotId && space.lotId !== Number(lotId)) {
                        return false;
                    }
                    if (kind && space.type !== kind) {
                        return false;
                    }
                    if (status && space.status !== status) {
                        return false;
                    }
                    if (query
                        && !space.code.toLowerCase().includes(query)
                        && !space.lotCode.toLowerCase().includes(query)
                        && !space.lotName.toLowerCase().includes(query)) {
                        return false;
                    }
                    return true;
                });
            return respond(result);
        }

        const spaceMatch = path.match(/^\/api\/spaces\/(\d+)$/);
        if (method === 'GET' && spaceMatch) {
            const space = SPACES.find((item) => item.id === Number(spaceMatch[1]));
            if (!space) {
                throw httpError(404, 'Parking space was not found.');
            }
            return respond(spaceResponse(space, now));
        }

        if (path === '/api/vehicles' && method === 'GET') {
            return respond(state.vehicles.map(vehicleResponse));
        }

        if (path === '/api/vehicles' && method === 'POST') {
            const body = bodyOf(init);
            const vehicleNumber = String(body.vehicleNumber || '').replace(/\s+/g, '').toUpperCase();
            const ownerName = String(body.ownerName || '').trim();
            const type = body.type === 'TWO_WHEELER' ? 'TWO_WHEELER' : 'CAR';
            if (!vehicleNumber) {
                throw httpError(400, 'Vehicle number is required.');
            }
            if (!ownerName) {
                throw httpError(400, 'Owner name is required.');
            }
            if (state.vehicles.some((item) => item.vehicleNumber === vehicleNumber)) {
                throw httpError(409, `Vehicle ${vehicleNumber} is already registered.`);
            }
            const vehicle = {
                id: state.nextVehicleId++,
                vehicleNumber,
                type,
                ownerName,
                accessibilityPermit: Boolean(body.accessibilityPermit),
                createdAt: simulatedNow().toISOString()
            };
            state.vehicles.push(vehicle);
            persist();
            return respond(vehicleResponse(vehicle), 201);
        }

        const permitMatch = path.match(/^\/api\/vehicles\/(\d+)\/permit$/);
        if (method === 'PATCH' && permitMatch) {
            const vehicle = findVehicle(permitMatch[1]);
            const body = bodyOf(init);
            vehicle.accessibilityPermit = Boolean(body.accessibilityPermit);
            state.waiting.forEach((item) => {
                if (item.vehicleId === vehicle.id && item.status === 'WAITING') {
                    item.accessibilityPermit = vehicle.accessibilityPermit;
                    item.accessPriority = vehicle.accessibilityPermit ? 1 : 0;
                }
            });
            persist();
            return respond(vehicleResponse(vehicle));
        }

        if (path === '/api/reservations' && method === 'GET') {
            const status = searchParams.get('status');
            const result = state.reservations
                .filter((item) => !status || item.status === status)
                .slice()
                .sort((a, b) => b.id - a.id);
            return respond(result);
        }

        if (path === '/api/reservations' && method === 'POST') {
            const body = bodyOf(init);
            const vehicle = findVehicle(body.vehicleId);
            const window = validateWindow(body.startTime, body.endTime);
            if (vehicleHasOverlap(vehicle.id, window.start, window.end)) {
                throw httpError(409, `Vehicle ${vehicle.vehicleNumber} already has a reservation in this window.`);
            }
            const space = chooseSpace(vehicle, window.start, window.end, body.preferredSpaceId);
            const reservation = createReservation(vehicle, space, window.start, window.end);
            persist();
            return respond(reservation, 201);
        }

        if (path === '/api/reservations/expire' && method === 'POST') {
            const expiredCount = expireReservations();
            const allocated = processWaitingInternal();
            persist();
            return respond({ expiredCount, allocatedFromWaitingList: allocated });
        }

        const reservationActionMatch = path.match(/^\/api\/reservations\/(\d+)\/(check-in|check-out|cancel)$/);
        if (method === 'POST' && reservationActionMatch) {
            const reservation = state.reservations.find((item) => item.id === Number(reservationActionMatch[1]));
            if (!reservation) {
                throw httpError(404, 'Reservation was not found.');
            }
            const action = reservationActionMatch[2];
            const current = simulatedNow();
            if (action === 'check-in') {
                if (reservation.status !== 'RESERVED') {
                    throw httpError(409, 'Only reserved bookings can be checked in.');
                }
                if (current < new Date(new Date(reservation.startTime).getTime() - CHECK_IN_EARLY_MINUTES * 60000)) {
                    throw httpError(409, 'Check-in opens 30 minutes before the reservation starts.');
                }
                if (current > new Date(reservation.endTime)) {
                    throw httpError(409, 'This reservation window has already ended.');
                }
                reservation.status = 'CHECKED_IN';
                reservation.checkedInAt = current.toISOString();
                addEvent('CHECKED_IN', reservation, `Checked in to ${reservation.spaceCode}`, null);
            } else if (action === 'check-out') {
                if (reservation.status !== 'CHECKED_IN') {
                    throw httpError(409, 'Only checked-in bookings can be checked out.');
                }
                const checkedInAt = new Date(reservation.checkedInAt || reservation.startTime);
                const minutes = Math.max(0, (current - checkedInAt) / 60000);
                const hours = Math.max(1, Math.ceil(minutes / 60));
                const rate = reservation.vehicleType === 'TWO_WHEELER' ? 20 : 50;
                reservation.status = 'COMPLETED';
                reservation.checkedOutAt = current.toISOString();
                reservation.fee = Math.round(hours * rate * 100) / 100;
                addEvent('CHECKED_OUT', reservation,
                    `Checked out of ${reservation.spaceCode}`, reservation.fee);
                processWaitingInternal();
            } else {
                if (reservation.status !== 'RESERVED') {
                    throw httpError(409, 'Only reserved bookings can be cancelled.');
                }
                reservation.status = 'CANCELLED';
                addEvent('CANCELLED', reservation,
                    `Reservation cancelled for ${reservation.spaceCode}`, null);
                processWaitingInternal();
            }
            persist();
            return respond(reservation);
        }

        if (path === '/api/waiting-list' && method === 'GET') {
            const queue = waitingQueue();
            return respond(queue.map((item, index) => waitingResponse(item, index + 1)));
        }

        if (path === '/api/waiting-list' && method === 'POST') {
            const body = bodyOf(init);
            const vehicle = findVehicle(body.vehicleId);
            const window = validateWindow(body.startTime, body.endTime);
            const item = {
                id: state.nextWaitingId++,
                vehicleId: vehicle.id,
                vehicleNumber: vehicle.vehicleNumber,
                vehicleType: vehicle.type,
                accessibilityPermit: vehicle.accessibilityPermit,
                accessPriority: vehicle.accessibilityPermit ? 1 : 0,
                arrivalTime: simulatedNow().toISOString(),
                startTime: window.start.toISOString(),
                endTime: window.end.toISOString(),
                status: 'WAITING',
                allocatedReservationId: null
            };
            state.waiting.push(item);
            addEvent('WAITING_JOINED', null,
                `Waiting request joined for ${vehicle.vehicleNumber}`, null,
                { vehicleNumber: vehicle.vehicleNumber });
            persist();
            const queue = waitingQueue();
            const position = queue.findIndex((candidate) => candidate.id === item.id) + 1;
            return respond(waitingResponse(item, position), 201);
        }

        if (path === '/api/waiting-list/process' && method === 'POST') {
            const allocated = processWaitingInternal();
            persist();
            return respond({ allocated });
        }

        const waitingMatch = path.match(/^\/api\/waiting-list\/(\d+)$/);
        if (method === 'DELETE' && waitingMatch) {
            const item = state.waiting.find((candidate) => candidate.id === Number(waitingMatch[1]));
            if (!item) {
                throw httpError(404, 'Waiting request was not found.');
            }
            item.status = 'CANCELLED';
            addEvent('WAITING_CANCELLED', null,
                `Waiting request cancelled for ${item.vehicleNumber}`, null,
                { vehicleNumber: item.vehicleNumber });
            persist();
            return respond(null, 204);
        }

        if (path === '/api/history' && method === 'GET') {
            const limit = Number(searchParams.get('limit') || 100);
            const query = (searchParams.get('q') || '').trim().toLowerCase();
            const result = state.events
                .filter((event) => {
                    if (!query) {
                        return true;
                    }
                    return [event.vehicleNumber, event.spaceCode, event.lotCode,
                        event.message, event.eventType]
                        .filter(Boolean)
                        .some((value) => String(value).toLowerCase().includes(query));
                })
                .slice()
                .sort((a, b) => new Date(b.occurredAt) - new Date(a.occurredAt) || b.id - a.id)
                .slice(0, Number.isNaN(limit) ? 100 : limit);
            return respond(result);
        }

        if (path === '/api/system/clock' && method === 'GET') {
            return respond(clockResponse());
        }

        if (path === '/api/system/clock/advance' && method === 'POST') {
            const body = bodyOf(init);
            const minutes = Number(body.minutes);
            if (!Number.isFinite(minutes)) {
                throw httpError(400, 'Minutes must be a number.');
            }
            state.clockOffsetMinutes = Number(state.clockOffsetMinutes || 0) + minutes;
            persist();
            refreshExpired();
            return respond(clockResponse());
        }

        if (path === '/api/system/clock/reset' && method === 'POST') {
            state.clockOffsetMinutes = 0;
            persist();
            return respond(clockResponse());
        }

        throw httpError(404, `No static demo endpoint for ${method} ${path}.`);
    }

    const realFetch = window.fetch ? window.fetch.bind(window) : null;

    window.fetch = async function (input, init) {
        const requestUrl = typeof input === 'string' ? input : (input && input.url) || '';
        let url;
        try {
            url = new URL(requestUrl, window.location.origin);
        } catch (ignored) {
            return realFetch ? realFetch(input, init) : Promise.reject(new Error('Invalid URL'));
        }
        if (!url.pathname.startsWith('/api/')) {
            return realFetch ? realFetch(input, init) : Promise.reject(new Error('Network unavailable'));
        }

        const method = String((init && init.method) || (input && input.method) || 'GET').toUpperCase();
        try {
            if (url.pathname !== '/api/reservations/expire'
                && url.pathname !== '/api/waiting-list/process') {
                refreshExpired();
            }
            return await handle(url.pathname, url.searchParams, method, init);
        } catch (error) {
            return respond({
                message: error.message || 'The static demo could not complete the request.',
                details: error.details || []
            }, error.status || 500);
        }
    };

    window.mockParking = {
        reset: function () {
            window.localStorage.removeItem(STORAGE_KEY);
            window.location.reload();
        }
    };

    document.addEventListener('DOMContentLoaded', function () {
        const button = document.getElementById('resetDemoBtn');
        if (button) {
            button.addEventListener('click', function () {
                if (window.confirm('Reset all demo vehicles, reservations and history?')) {
                    window.mockParking.reset();
                }
            });
        }
    });
})();
