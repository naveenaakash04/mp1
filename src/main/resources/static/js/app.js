/*
 * Smart Parking Allocation System - dashboard controller.
 *
 * The frontend is deliberately thin: it only fetches data, renders it and
 * sends commands to the REST API. Every allocation, overlap, permit and
 * priority decision is made by the Java backend.
 */
(function () {
    'use strict';

    /* ------------------------------------------------------------------ */
    /* State and helpers                                                   */
    /* ------------------------------------------------------------------ */

    const state = {
        dashboard: null,
        lots: [],
        spaces: [],
        vehicles: [],
        reservations: [],
        waiting: [],
        history: [],
        clock: null,
        travel: null
    };

    const $ = (id) => document.getElementById(id);

    const dateTimeFormatter = new Intl.DateTimeFormat('en-IN', {
        day: '2-digit',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
        hour12: true
    });

    const timeFormatter = new Intl.DateTimeFormat('en-IN', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: true
    });

    const currencyFormatter = new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        maximumFractionDigits: 2
    });

    function escapeHtml(value) {
        return String(value === null || value === undefined ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function humanize(value) {
        if (!value) {
            return '—';
        }
        return String(value).replace(/_/g, ' ').toLowerCase()
            .replace(/\b\w/g, (letter) => letter.toUpperCase());
    }

    function typeLabel(type) {
        switch (type) {
            case 'CAR':
                return 'Car';
            case 'TWO_WHEELER':
                return 'Two-wheeler';
            case 'ACCESSIBLE':
                return 'Accessible';
            default:
                return humanize(type);
        }
    }

    function spaceIcon(type) {
        switch (type) {
            case 'TWO_WHEELER':
                return '🏍';
            case 'ACCESSIBLE':
                return '♿';
            default:
                return '🚗';
        }
    }

    function formatDateTime(value) {
        if (!value) {
            return '—';
        }
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? escapeHtml(value) : dateTimeFormatter.format(date);
    }

    function formatTime(value) {
        if (!value) {
            return '—';
        }
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? escapeHtml(value) : timeFormatter.format(date);
    }

    function formatFee(value) {
        if (value === null || value === undefined) {
            return '—';
        }
        const number = Number(value);
        return Number.isNaN(number) ? escapeHtml(value) : currencyFormatter.format(number);
    }

    function statusBadge(status) {
        const safeStatus = String(status || 'UNKNOWN');
        return `<span class="badge badge-${safeStatus.toLowerCase()}">${escapeHtml(humanize(safeStatus))}</span>`;
    }

    function historyBadgeClass(eventType) {
        switch (eventType) {
            case 'WAITING_JOINED':
                return 'waiting';
            case 'WAITING_ALLOCATED':
                return 'completed';
            case 'WAITING_CANCELLED':
                return 'cancelled';
            case 'WAITING_EXPIRED':
                return 'expired';
            default:
                return String(eventType || 'unknown').toLowerCase();
        }
    }

    function toLocalInputValue(date) {
        const pad = (number) => String(number).padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
            + `T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    function toApiDateTime(value) {
        // datetime-local gives "YYYY-MM-DDTHH:mm"; the API accepts it, but
        // adding seconds keeps the payload explicit.
        return value && value.length === 16 ? `${value}:00` : value;
    }

    function debounce(fn, delay) {
        let handle = null;
        return function (...args) {
            window.clearTimeout(handle);
            handle = window.setTimeout(() => fn.apply(this, args), delay);
        };
    }

    async function api(path, options) {
        const config = Object.assign({ headers: {} }, options || {});
        if (config.body !== undefined) {
            config.headers['Content-Type'] = 'application/json';
        }
        const response = await fetch(path, config);
        if (!response.ok) {
            let message = `Request failed with status ${response.status}`;
            try {
                const body = await response.json();
                if (body && body.message) {
                    message = body.message;
                }
                if (body && Array.isArray(body.details) && body.details.length > 0) {
                    message += ` (${body.details.join('; ')})`;
                }
            } catch (ignored) {
                // Keep the generic message when the body is not JSON.
            }
            const error = new Error(message);
            error.status = response.status;
            throw error;
        }
        if (response.status === 204) {
            return null;
        }
        const text = await response.text();
        return text ? JSON.parse(text) : null;
    }

    function setFormMessage(element, message, kind) {
        if (!element) {
            return;
        }
        element.textContent = message || '';
        element.className = 'form-message' + (kind ? ` ${kind}` : '');
    }

    function toast(message, kind) {
        const container = $('toastContainer');
        if (!container) {
            return;
        }
        const type = kind || 'info';
        const icon = type === 'success' ? '✓' : type === 'error' ? '!' : 'i';
        const element = document.createElement('div');
        element.className = `toast ${type}`;
        element.innerHTML = `<span class="toast-icon">${icon}</span><span>${escapeHtml(message)}</span>`;
        container.appendChild(element);
        window.setTimeout(() => element.remove(), 5000);
    }

    async function withBusy(button, task) {
        if (!button) {
            return task();
        }
        const original = button.textContent;
        button.disabled = true;
        button.textContent = 'Working...';
        try {
            return await task();
        } finally {
            button.disabled = false;
            button.textContent = original;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Loaders                                                             */
    /* ------------------------------------------------------------------ */

    async function loadDashboard() {
        state.dashboard = await api('/api/dashboard');
        renderDashboard();
        renderClock();
    }

    async function loadLots() {
        state.lots = await api('/api/lots');
        renderLots();
    }

    async function loadSpaces() {
        const params = new URLSearchParams();
        const lotId = $('spaceLotFilter').value;
        const kind = $('spaceKindFilter').value;
        const status = $('spaceStatusFilter').value;
        const query = $('spaceSearch').value.trim();
        if (lotId) {
            params.set('lotId', lotId);
        }
        if (kind) {
            params.set('kind', kind);
        }
        if (status) {
            params.set('status', status);
        }
        if (query) {
            params.set('q', query);
        }
        const suffix = params.toString() ? `?${params.toString()}` : '';
        state.spaces = await api(`/api/spaces${suffix}`);
        renderSpaces();
    }

    async function loadVehicles() {
        state.vehicles = await api('/api/vehicles');
        renderVehicles();
        renderVehicleOptions();
        renderSpaceOptions();
    }

    async function loadReservations() {
        const params = new URLSearchParams();
        const status = $('reservationStatusFilter').value;
        if (status) {
            params.set('status', status);
        }
        const suffix = params.toString() ? `?${params.toString()}` : '';
        state.reservations = await api(`/api/reservations${suffix}`);
        renderReservations();
    }

    async function loadWaiting() {
        state.waiting = await api('/api/waiting-list');
        renderWaiting();
    }

    async function loadHistory() {
        const params = new URLSearchParams();
        params.set('limit', '100');
        const query = $('historySearch').value.trim();
        if (query) {
            params.set('q', query);
        }
        state.history = await api(`/api/history?${params.toString()}`);
        renderHistory();
    }

    async function loadClock() {
        state.clock = await api('/api/system/clock');
        renderClock();
        ensureDefaultWindow();
    }

    async function loadTravelTimes() {
        state.travel = await api('/api/lots/travel-times');
        renderTravelSummary();
    }

    async function loadAll(options) {
        const quiet = Boolean(options && options.quiet);
        try {
            const [dashboard, lots, spaces, vehicles, reservations, waiting, history, clock, travel] =
                await Promise.all([
                    api('/api/dashboard'),
                    api('/api/lots'),
                    (async () => {
                        const params = new URLSearchParams();
                        const lotId = $('spaceLotFilter').value;
                        const kind = $('spaceKindFilter').value;
                        const status = $('spaceStatusFilter').value;
                        const query = $('spaceSearch').value.trim();
                        if (lotId) {
                            params.set('lotId', lotId);
                        }
                        if (kind) {
                            params.set('kind', kind);
                        }
                        if (status) {
                            params.set('status', status);
                        }
                        if (query) {
                            params.set('q', query);
                        }
                        const suffix = params.toString() ? `?${params.toString()}` : '';
                        return api(`/api/spaces${suffix}`);
                    })(),
                    api('/api/vehicles'),
                    (async () => {
                        const params = new URLSearchParams();
                        const status = $('reservationStatusFilter').value;
                        if (status) {
                            params.set('status', status);
                        }
                        const suffix = params.toString() ? `?${params.toString()}` : '';
                        return api(`/api/reservations${suffix}`);
                    })(),
                    api('/api/waiting-list'),
                    (async () => {
                        const params = new URLSearchParams();
                        params.set('limit', '100');
                        const query = $('historySearch').value.trim();
                        if (query) {
                            params.set('q', query);
                        }
                        return api(`/api/history?${params.toString()}`);
                    })(),
                    api('/api/system/clock'),
                    api('/api/lots/travel-times')
                ]);

            state.dashboard = dashboard;
            state.lots = lots;
            state.spaces = spaces;
            state.vehicles = vehicles;
            state.reservations = reservations;
            state.waiting = waiting;
            state.history = history;
            state.clock = clock;
            state.travel = travel;

            renderAll();
            ensureDefaultWindow();
            if (!quiet) {
                toast('Dashboard refreshed from the backend.', 'info');
            }
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    /* ------------------------------------------------------------------ */
    /* Rendering                                                           */
    /* ------------------------------------------------------------------ */

    function renderAll() {
        renderDashboard();
        renderLots();
        renderSpaces();
        renderVehicles();
        renderVehicleOptions();
        renderSpaceOptions();
        renderReservations();
        renderWaiting();
        renderHistory();
        renderClock();
        renderTravelSummary();
    }

    function renderDashboard() {
        const dashboard = state.dashboard;
        if (!dashboard) {
            return;
        }
        $('statTotal').textContent = dashboard.totalSpaces;
        $('statLots').textContent = `${dashboard.totalLots} lot${dashboard.totalLots === 1 ? '' : 's'}`;
        $('statAvailable').textContent = dashboard.availableSpaces;
        $('statOccupied').textContent = dashboard.occupiedSpaces;
        $('statReserved').textContent = dashboard.reservedSpaces;
        $('statReservations').textContent = dashboard.activeReservations;
        $('statWaiting').textContent = dashboard.waitingRequests;
    }

    function renderClock() {
        const clock = state.clock;
        if (!clock) {
            return;
        }
        const simulated = new Date(Date.now() + Number(clock.offsetMinutes) * 60000);
        $('clockText').textContent = `Simulated ${timeFormatter.format(simulated)}`;
        const offset = Number(clock.offsetMinutes);
        let offsetLabel = `${offset} min`;
        if (offset > 0) {
            offsetLabel = `+${offsetLabel}`;
        }
        if (Math.abs(offset) >= 60) {
            const hours = offset / 60;
            offsetLabel = `${offset > 0 ? '+' : ''}${Number(hours.toFixed(2))} h`;
        }
        $('clockOffset').textContent = offset === 0 ? 'real time' : offsetLabel;
        $('clockChip').title = `Simulated time. Offset from real time: ${offset} minute(s).`;
    }

    function renderTravelSummary() {
        const travel = state.travel;
        if (!travel || !Array.isArray(travel.lots)) {
            return;
        }
        const parts = travel.lots.map((lot) => `${lot.lotCode} ${lot.minutes} min`);
        $('travelSummary').textContent = `From ${travel.source}: ${parts.join(' · ')}`;
    }

    function renderLots() {
        const container = $('lotsList');
        if (!state.lots.length) {
            container.innerHTML = '<div class="empty">No parking lots configured.</div>';
            return;
        }

        container.innerHTML = state.lots.map((lot) => {
            const occupied = lot.totalSpaces - lot.availableSpaces;
            const percent = lot.totalSpaces === 0 ? 0 : Math.round((occupied / lot.totalSpaces) * 100);
            const walk = lot.travelMinutes >= 9999 ? 'unreachable' : `${lot.travelMinutes} min from entrance`;
            return `
                <button type="button" class="lot-card" data-lot-id="${lot.id}">
                    <span class="lot-code">${escapeHtml(lot.code)}</span>
                    <h4>${escapeHtml(lot.name)}</h4>
                    <p class="muted">${escapeHtml(lot.address)}</p>
                    <div class="lot-meta">
                        <span>${escapeHtml(walk)}</span>
                        <span>${lot.availableSpaces}/${lot.totalSpaces} free</span>
                    </div>
                    <div class="progress" title="${percent}% in use">
                        <span style="width:${percent}%"></span>
                    </div>
                    <div class="lot-counts">
                        <span><b>${lot.availableSpaces}</b> available</span>
                        <span><b>${lot.reservedSpaces}</b> reserved</span>
                        <span><b>${lot.occupiedSpaces}</b> occupied</span>
                    </div>
                </button>`;
        }).join('');

        const filter = $('spaceLotFilter');
        const selected = filter.value;
        filter.innerHTML = '<option value="">All lots</option>' + state.lots.map((lot) =>
            `<option value="${lot.id}">${escapeHtml(lot.code)} — ${escapeHtml(lot.name)}</option>`
        ).join('');
        if (state.lots.some((lot) => String(lot.id) === selected)) {
            filter.value = selected;
        }
    }

    function renderSpaces() {
        const container = $('spaceGrid');
        if (!state.spaces.length) {
            container.innerHTML = '<div class="empty">No spaces match the selected filters.</div>';
            return;
        }

        const typeOrder = ['CAR', 'TWO_WHEELER', 'ACCESSIBLE'];
        const groups = new Map();
        state.spaces.forEach((space) => {
            const type = space.type || 'CAR';
            if (!groups.has(type)) {
                groups.set(type, []);
            }
            groups.get(type).push(space);
        });

        const types = [
            ...typeOrder.filter((type) => groups.has(type)),
            ...[...groups.keys()].filter((type) => !typeOrder.includes(type))
        ];

        container.innerHTML = types.map((type) => {
            const spaces = groups.get(type);
            const rows = spaces.map((space) => {
                const status = String(space.status || 'AVAILABLE').toLowerCase();
                const occupant = space.activeVehicleNumber
                    ? escapeHtml(space.activeVehicleNumber)
                    : (space.requiresPermit ? 'Permit required' : 'No permit required');
                return `
                    <tr class="space-row" data-space-id="${space.id}" tabindex="0">
                        <td><strong>${escapeHtml(space.code)}</strong></td>
                        <td>${escapeHtml(space.lotCode)}</td>
                        <td>Floor ${space.floorNumber}</td>
                        <td><span class="badge badge-${status}">${escapeHtml(humanize(space.status))}</span></td>
                        <td>${occupant}</td>
                        <td>${space.travelMinutes >= 9999 ? 'Unreachable' : `${space.travelMinutes} min`}</td>
                    </tr>`;
            }).join('');

            return `
                <section class="space-group space-group-${type.toLowerCase()}">
                    <div class="space-group-heading">
                        <h3>
                            <span class="space-icon" aria-hidden="true">${spaceIcon(type)}</span>
                            ${escapeHtml(typeLabel(type))} spaces
                        </h3>
                        <span class="pill">${spaces.length} space${spaces.length === 1 ? '' : 's'}</span>
                    </div>
                    <div class="table-wrap">
                        <table class="space-table">
                            <thead>
                            <tr>
                                <th>Space</th>
                                <th>Lot</th>
                                <th>Floor</th>
                                <th>Status</th>
                                <th>Vehicle / permit</th>
                                <th>Walk</th>
                            </tr>
                            </thead>
                            <tbody>${rows}</tbody>
                        </table>
                    </div>
                </section>`;
        }).join('');
    }

    function renderVehicles() {
        const body = $('vehicleTableBody');
        $('vehicleCount').textContent = `${state.vehicles.length} vehicle${state.vehicles.length === 1 ? '' : 's'}`;
        if (!state.vehicles.length) {
            body.innerHTML = '<tr><td colspan="5" class="empty">No vehicles yet.</td></tr>';
            return;
        }

        body.innerHTML = state.vehicles.map((vehicle) => `
            <tr>
                <td><strong>${escapeHtml(vehicle.vehicleNumber)}</strong></td>
                <td>${escapeHtml(typeLabel(vehicle.type))}</td>
                <td>${escapeHtml(vehicle.ownerName)}</td>
                <td>${vehicle.accessibilityPermit
                    ? '<span class="badge badge-permit">Permit</span>'
                    : '<span class="badge badge-muted">None</span>'}</td>
                <td>
                    <button type="button" class="btn btn-ghost btn-small"
                            data-permit-id="${vehicle.id}"
                            data-permit-value="${vehicle.accessibilityPermit ? 'false' : 'true'}">
                        ${vehicle.accessibilityPermit ? 'Remove permit' : 'Grant permit'}
                    </button>
                </td>
            </tr>`).join('');
    }

    function renderVehicleOptions() {
        const select = $('reservationVehicle');
        const selected = select.value;
        const options = state.vehicles.map((vehicle) =>
            `<option value="${vehicle.id}">${escapeHtml(vehicle.vehicleNumber)} · ${escapeHtml(typeLabel(vehicle.type))} · ${escapeHtml(vehicle.ownerName)}${vehicle.accessibilityPermit ? ' · permit' : ''}</option>`
        ).join('');
        select.innerHTML = '<option value="">Select a registered vehicle</option>' + options;
        if (state.vehicles.some((vehicle) => String(vehicle.id) === selected)) {
            select.value = selected;
        }
    }

    function renderSpaceOptions() {
        const select = $('reservationSpace');
        const selected = select.value;
        // Use the full lot snapshot (not the filtered grid) so the preferred
        // space list always contains every space in the system.
        const allSpaces = state.lots.flatMap((lot) => lot.spaces || []);
        const source = allSpaces.length ? allSpaces : state.spaces;
        const options = source
            .slice()
            .sort((a, b) => a.code.localeCompare(b.code))
            .map((space) => {
                const permit = space.requiresPermit ? ' · permit' : '';
                return `<option value="${space.id}">${escapeHtml(space.code)} · ${escapeHtml(space.lotCode)} · ${escapeHtml(typeLabel(space.type))}${permit}</option>`;
            }).join('');
        select.innerHTML = '<option value="">Smart allocation (recommended)</option>' + options;
        if (source.some((space) => String(space.id) === selected)) {
            select.value = selected;
        }
    }

    function renderReservations() {
        const body = $('reservationTableBody');
        if (!state.reservations.length) {
            body.innerHTML = '<tr><td colspan="7" class="empty">No reservations match the filter.</td></tr>';
            return;
        }

        body.innerHTML = state.reservations.map((reservation) => {
            const actions = [];
            if (reservation.status === 'RESERVED') {
                actions.push(`<button type="button" class="btn btn-secondary btn-small" data-reservation-action="check-in" data-reservation-id="${reservation.id}">Check in</button>`);
                actions.push(`<button type="button" class="btn btn-danger btn-small" data-reservation-action="cancel" data-reservation-id="${reservation.id}">Cancel</button>`);
            } else if (reservation.status === 'CHECKED_IN') {
                actions.push(`<button type="button" class="btn btn-primary btn-small" data-reservation-action="check-out" data-reservation-id="${reservation.id}">Check out</button>`);
            }
            return `
                <tr>
                    <td><strong>${escapeHtml(reservation.reservationCode)}</strong></td>
                    <td>
                        <strong>${escapeHtml(reservation.vehicleNumber)}</strong><br>
                        <span class="badge badge-muted">${escapeHtml(typeLabel(reservation.vehicleType))}</span>
                    </td>
                    <td>
                        <strong>${escapeHtml(reservation.spaceCode)}</strong><br>
                        <span class="muted">${escapeHtml(reservation.lotCode)}</span>
                    </td>
                    <td>
                        ${formatDateTime(reservation.startTime)}<br>
                        <span class="muted">to ${formatDateTime(reservation.endTime)}</span>
                    </td>
                    <td>${statusBadge(reservation.status)}</td>
                    <td>${formatFee(reservation.fee)}</td>
                    <td><div class="table-actions">${actions.length ? actions.join('') : '<span class="badge badge-muted">No action</span>'}</div></td>
                </tr>`;
        }).join('');
    }

    function renderWaiting() {
        const body = $('waitingTableBody');
        if (!state.waiting.length) {
            body.innerHTML = '<tr><td colspan="6" class="empty">The waiting list is empty.</td></tr>';
            return;
        }

        body.innerHTML = state.waiting.map((request) => {
            const priority = request.accessibilityPermit
                ? '<span class="badge badge-permit priority-high">Permit holder · P1</span>'
                : '<span class="badge badge-muted">Standard · P0</span>';
            return `
                <tr>
                    <td><strong>#${request.queuePosition}</strong></td>
                    <td>
                        <strong>${escapeHtml(request.vehicleNumber)}</strong><br>
                        <span class="badge badge-muted">${escapeHtml(typeLabel(request.vehicleType))}</span>
                    </td>
                    <td>${priority}</td>
                    <td>
                        ${formatDateTime(request.startTime)}<br>
                        <span class="muted">to ${formatDateTime(request.endTime)}</span>
                    </td>
                    <td>${formatDateTime(request.arrivalTime)}</td>
                    <td>
                        <button type="button" class="btn btn-danger btn-small"
                                data-waiting-cancel="${request.id}">Cancel</button>
                    </td>
                </tr>`;
        }).join('');
    }

    function renderHistory() {
        const body = $('historyTableBody');
        if (!state.history.length) {
            body.innerHTML = '<tr><td colspan="6" class="empty">No events match the search.</td></tr>';
            return;
        }

        body.innerHTML = state.history.map((event) => `
            <tr>
                <td>${formatDateTime(event.occurredAt)}</td>
                <td><span class="badge badge-${historyBadgeClass(event.eventType)}">${escapeHtml(humanize(event.eventType))}</span></td>
                <td>${escapeHtml(event.vehicleNumber || '—')}</td>
                <td>${event.spaceCode ? `${escapeHtml(event.lotCode || '')} · ${escapeHtml(event.spaceCode)}` : '—'}</td>
                <td>${escapeHtml(event.message || '')}</td>
                <td>${formatFee(event.fee)}</td>
            </tr>`).join('');
    }

    /* ------------------------------------------------------------------ */
    /* Defaults and modal                                                  */
    /* ------------------------------------------------------------------ */

    function ensureDefaultWindow() {
        const start = $('reservationStart');
        const end = $('reservationEnd');
        if (!start || !end || (start.value && end.value)) {
            return;
        }
        const offset = state.clock ? Number(state.clock.offsetMinutes) : 0;
        const simulatedNow = new Date(Date.now() + offset * 60000);
        simulatedNow.setSeconds(0, 0);
        if (!start.value) {
            start.value = toLocalInputValue(simulatedNow);
        }
        if (!end.value) {
            end.value = toLocalInputValue(new Date(simulatedNow.getTime() + 2 * 60 * 60 * 1000));
        }
    }

    async function openSpaceModal(spaceId) {
        const modal = $('spaceModal');
        try {
            const space = await api(`/api/spaces/${spaceId}`);
            $('spaceModalTitle').textContent = `${space.code} · ${space.lotCode}`;
            const rows = [
                ['Status', statusBadge(space.status)],
                ['Type', escapeHtml(typeLabel(space.type))],
                ['Lot', `${escapeHtml(space.lotCode)} — ${escapeHtml(space.lotName)}`],
                ['Floor', String(space.floorNumber)],
                ['Accessibility permit', space.requiresPermit ? 'Required' : 'Not required'],
                ['Walk from entrance', space.travelMinutes >= 9999 ? 'Unreachable' : `${space.travelMinutes} minute(s)`],
                ['Active vehicle', space.activeVehicleNumber ? escapeHtml(space.activeVehicleNumber) : '—'],
                ['Reservation window', space.reservationStart
                    ? `${formatDateTime(space.reservationStart)} → ${formatDateTime(space.reservationEnd)}`
                    : '—']
            ];
            let html = rows.map(([label, value]) =>
                `<div class="modal-row"><span>${label}</span><strong>${value}</strong></div>`
            ).join('');
            if (space.status === 'AVAILABLE') {
                html += `<button type="button" class="btn btn-primary" data-reserve-space="${space.id}">Use this space in the reservation form</button>`;
            }
            $('spaceModalBody').innerHTML = html;
            modal.hidden = false;
            document.body.style.overflow = 'hidden';
        } catch (error) {
            toast(error.message, 'error');
        }
    }

    function closeSpaceModal() {
        $('spaceModal').hidden = true;
        document.body.style.overflow = '';
    }

    /* ------------------------------------------------------------------ */
    /* Commands                                                            */
    /* ------------------------------------------------------------------ */

    async function submitVehicle(event) {
        event.preventDefault();
        const button = event.target.querySelector('button[type="submit"]');
        const number = $('vehicleNumber').value.trim();
        const owner = $('vehicleOwner').value.trim();
        const type = $('vehicleType').value;

        if (!number || !owner) {
            setFormMessage($('vehicleMessage'), 'Vehicle number and owner name are required.', 'error');
            return;
        }

        setFormMessage($('vehicleMessage'), '', '');
        await withBusy(button, async () => {
            try {
                const vehicle = await api('/api/vehicles', {
                    method: 'POST',
                    body: JSON.stringify({
                        vehicleNumber: number,
                        type,
                        ownerName: owner,
                        accessibilityPermit: $('vehiclePermit').checked
                    })
                });
                event.target.reset();
                setFormMessage($('vehicleMessage'),
                    `Registered ${vehicle.vehicleNumber} (${typeLabel(vehicle.type)}).`, 'success');
                toast(`Vehicle ${vehicle.vehicleNumber} registered.`, 'success');
                await loadAll({ quiet: true });
            } catch (error) {
                setFormMessage($('vehicleMessage'), error.message, 'error');
                toast(error.message, 'error');
            }
        });
    }

    async function togglePermit(button) {
        const id = button.getAttribute('data-permit-id');
        const value = button.getAttribute('data-permit-value') === 'true';
        await withBusy(button, async () => {
            try {
                const vehicle = await api(`/api/vehicles/${id}/permit`, {
                    method: 'PATCH',
                    body: JSON.stringify({ accessibilityPermit: value })
                });
                toast(`Permit ${value ? 'granted to' : 'removed from'} ${vehicle.vehicleNumber}.`, 'success');
                await loadAll({ quiet: true });
            } catch (error) {
                toast(error.message, 'error');
            }
        });
    }

    function readReservationForm() {
        const vehicleId = Number($('reservationVehicle').value);
        const start = $('reservationStart').value;
        const end = $('reservationEnd').value;
        const preferredSpaceId = $('reservationSpace').value
            ? Number($('reservationSpace').value)
            : null;
        if (!vehicleId) {
            throw new Error('Select a registered vehicle first.');
        }
        if (!start || !end) {
            throw new Error('Choose both a start time and an end time.');
        }
        return {
            vehicleId,
            startTime: toApiDateTime(start),
            endTime: toApiDateTime(end),
            preferredSpaceId
        };
    }

    async function submitReservation(event) {
        event.preventDefault();
        const button = event.target.querySelector('button[type="submit"]');
        let payload;
        try {
            payload = readReservationForm();
        } catch (error) {
            setFormMessage($('reservationMessage'), error.message, 'error');
            return;
        }

        setFormMessage($('reservationMessage'), '', '');
        await withBusy(button, async () => {
            try {
                const reservation = await api('/api/reservations', {
                    method: 'POST',
                    body: JSON.stringify(payload)
                });
                setFormMessage($('reservationMessage'),
                    `Reserved ${reservation.spaceCode} in ${reservation.lotCode} (${reservation.reservationCode}).`,
                    'success');
                toast(`Reservation ${reservation.reservationCode} created for ${reservation.spaceCode}.`, 'success');
                await loadAll({ quiet: true });
            } catch (error) {
                setFormMessage($('reservationMessage'), error.message, 'error');
                toast(error.message, 'error');
            }
        });
    }

    async function joinWaitingList() {
        const button = $('joinWaitingBtn');
        let payload;
        try {
            const form = readReservationForm();
            payload = {
                vehicleId: form.vehicleId,
                startTime: form.startTime,
                endTime: form.endTime
            };
        } catch (error) {
            setFormMessage($('reservationMessage'), error.message, 'error');
            return;
        }

        setFormMessage($('reservationMessage'), '', '');
        await withBusy(button, async () => {
            try {
                const request = await api('/api/waiting-list', {
                    method: 'POST',
                    body: JSON.stringify(payload)
                });
                setFormMessage($('reservationMessage'),
                    `Joined the waiting list at position #${request.queuePosition}.`, 'success');
                toast(`Waiting request #${request.queuePosition} created.`, 'success');
                await loadAll({ quiet: true });
            } catch (error) {
                setFormMessage($('reservationMessage'), error.message, 'error');
                toast(error.message, 'error');
            }
        });
    }

    async function reservationAction(button) {
        const id = button.getAttribute('data-reservation-id');
        const action = button.getAttribute('data-reservation-action');
        if (action === 'cancel' && !window.confirm('Cancel this reservation and release the space?')) {
            return;
        }
        await withBusy(button, async () => {
            try {
                const reservation = await api(`/api/reservations/${id}/${action}`, { method: 'POST' });
                const messages = {
                    'check-in': `Checked in to ${reservation.spaceCode}.`,
                    'check-out': `Checked out. Fee: ${formatFee(reservation.fee)}.`,
                    'cancel': 'Reservation cancelled.'
                };
                toast(messages[action] || 'Reservation updated.', 'success');
                await loadAll({ quiet: true });
            } catch (error) {
                toast(error.message, 'error');
            }
        });
    }

    async function cancelWaiting(button) {
        const id = button.getAttribute('data-waiting-cancel');
        if (!window.confirm('Remove this request from the waiting list?')) {
            return;
        }
        await withBusy(button, async () => {
            try {
                await api(`/api/waiting-list/${id}`, { method: 'DELETE' });
                toast('Waiting request cancelled.', 'success');
                await loadAll({ quiet: true });
            } catch (error) {
                toast(error.message, 'error');
            }
        });
    }

    async function runExpiry(button) {
        await withBusy(button, async () => {
            try {
                const result = await api('/api/reservations/expire', { method: 'POST' });
                const message = result.expiredCount === 0
                    ? 'No unclaimed reservations had passed the grace period.'
                    : `${result.expiredCount} reservation(s) expired; ${result.allocatedFromWaitingList} waiting request(s) allocated.`;
                toast(message, result.expiredCount === 0 ? 'info' : 'success');
                await loadAll({ quiet: true });
            } catch (error) {
                toast(error.message, 'error');
            }
        });
    }

    async function processWaiting(button) {
        await withBusy(button, async () => {
            try {
                const result = await api('/api/waiting-list/process', { method: 'POST' });
                toast(result.allocated === 0
                    ? 'No waiting request could be allocated yet.'
                    : `${result.allocated} waiting request(s) allocated.`, result.allocated === 0 ? 'info' : 'success');
                await loadAll({ quiet: true });
            } catch (error) {
                toast(error.message, 'error');
            }
        });
    }

    async function advanceClock(button) {
        const minutes = Number(button.getAttribute('data-advance'));
        await withBusy(button, async () => {
            try {
                await api('/api/system/clock/advance', {
                    method: 'POST',
                    body: JSON.stringify({ minutes })
                });
                toast(`Simulated clock advanced by ${minutes} minute(s).`, 'info');
                await loadAll({ quiet: true });
            } catch (error) {
                toast(error.message, 'error');
            }
        });
    }

    async function resetClock(button) {
        await withBusy(button, async () => {
            try {
                await api('/api/system/clock/reset', { method: 'POST' });
                $('reservationStart').value = '';
                $('reservationEnd').value = '';
                toast('Simulated clock reset to real time.', 'info');
                await loadAll({ quiet: true });
            } catch (error) {
                toast(error.message, 'error');
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* Wiring                                                              */
    /* ------------------------------------------------------------------ */

    function bindEvents() {
        $('refreshBtn').addEventListener('click', () => loadAll());
        $('expireBtn').addEventListener('click', (event) => runExpiry(event.currentTarget));
        $('expireBtn2').addEventListener('click', (event) => runExpiry(event.currentTarget));
        $('resetClockBtn').addEventListener('click', (event) => resetClock(event.currentTarget));
        $('processWaitingBtn').addEventListener('click', (event) => processWaiting(event.currentTarget));

        document.querySelectorAll('[data-advance]').forEach((button) => {
            button.addEventListener('click', () => advanceClock(button));
        });

        $('vehicleForm').addEventListener('submit', submitVehicle);
        $('reservationForm').addEventListener('submit', submitReservation);
        $('joinWaitingBtn').addEventListener('click', joinWaitingList);

        $('vehicleTableBody').addEventListener('click', (event) => {
            const button = event.target.closest('button[data-permit-id]');
            if (button) {
                togglePermit(button);
            }
        });

        $('reservationTableBody').addEventListener('click', (event) => {
            const button = event.target.closest('button[data-reservation-action]');
            if (button) {
                reservationAction(button);
            }
        });

        $('waitingTableBody').addEventListener('click', (event) => {
            const button = event.target.closest('button[data-waiting-cancel]');
            if (button) {
                cancelWaiting(button);
            }
        });

        $('lotsList').addEventListener('click', (event) => {
            const card = event.target.closest('button[data-lot-id]');
            if (!card) {
                return;
            }
            $('spaceLotFilter').value = card.getAttribute('data-lot-id');
            $('spaceStatusFilter').value = '';
            $('spaceKindFilter').value = '';
            $('spaceSearch').value = '';
            loadSpaces().catch((error) => toast(error.message, 'error'));
            document.getElementById('spaces').scrollIntoView({ behavior: 'smooth', block: 'start' });
        });

        $('spaceGrid').addEventListener('click', (event) => {
            const card = event.target.closest('[data-space-id]');
            if (card) {
                openSpaceModal(card.getAttribute('data-space-id'));
            }
        });

        $('spaceGrid').addEventListener('keydown', (event) => {
            if (event.key !== 'Enter' && event.key !== ' ') {
                return;
            }
            const row = event.target.closest('tr[data-space-id]');
            if (row) {
                event.preventDefault();
                openSpaceModal(row.getAttribute('data-space-id'));
            }
        });

        $('spaceModalBody').addEventListener('click', (event) => {
            const button = event.target.closest('button[data-reserve-space]');
            if (!button) {
                return;
            }
            $('reservationSpace').value = button.getAttribute('data-reserve-space');
            closeSpaceModal();
            document.getElementById('reservations').scrollIntoView({ behavior: 'smooth', block: 'start' });
            $('reservationVehicle').focus();
        });

        $('spaceModalClose').addEventListener('click', closeSpaceModal);
        $('spaceModal').addEventListener('click', (event) => {
            if (event.target === $('spaceModal')) {
                closeSpaceModal();
            }
        });
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape' && !$('spaceModal').hidden) {
                closeSpaceModal();
            }
        });

        const reloadSpaces = debounce(() => {
            loadSpaces().catch((error) => toast(error.message, 'error'));
        }, 250);

        $('spaceLotFilter').addEventListener('change', reloadSpaces);
        $('spaceKindFilter').addEventListener('change', reloadSpaces);
        $('spaceStatusFilter').addEventListener('change', reloadSpaces);
        $('spaceSearch').addEventListener('input', reloadSpaces);

        $('reservationStatusFilter').addEventListener('change', () => {
            loadReservations().catch((error) => toast(error.message, 'error'));
        });

        const reloadHistory = debounce(() => {
            loadHistory().catch((error) => toast(error.message, 'error'));
        }, 250);
        $('historySearch').addEventListener('input', reloadHistory);

        document.querySelectorAll('.section-nav a').forEach((link) => {
            link.addEventListener('click', () => {
                document.querySelectorAll('.section-nav a').forEach((item) => item.classList.remove('active'));
                link.classList.add('active');
            });
        });
    }

    function startClockTicker() {
        window.setInterval(() => {
            if (state.clock) {
                renderClock();
            }
        }, 1000);
    }

    function init() {
        bindEvents();
        ensureDefaultWindow();
        startClockTicker();
        loadAll({ quiet: true });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
