package com.smartparking.service;

import com.smartparking.dto.ReservationResponse;
import com.smartparking.event.PriorityChangedEvent;
import com.smartparking.event.ReservationReleasedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/**
 * Reconsiders waiting requests after a space is released (cancel, checkout or
 * expiry) and after a priority change.
 *
 * <p>The listener runs AFTER_COMMIT, so it only sees reservations that were
 * actually committed. Each allocation attempt is a separate transaction.</p>
 */
@Component
public class WaitingListProcessor {

    private static final Logger log = LoggerFactory.getLogger(WaitingListProcessor.class);

    private final WaitingListService waitingListService;
    private final ReservationService reservationService;

    public WaitingListProcessor(WaitingListService waitingListService,
                                ReservationService reservationService) {
        this.waitingListService = waitingListService;
        this.reservationService = reservationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationReleased(ReservationReleasedEvent event) {
        int allocated = processWaitingList();
        if (allocated > 0) {
            log.info("Waiting list allocated {} reservation(s) after reservation {} was released",
                    allocated, event.reservationId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPriorityChanged(PriorityChangedEvent event) {
        processWaitingList();
    }

    /**
     * Tries queued requests in priority order. A request that still cannot be
     * allocated stays in the queue; later requests are still considered.
     */
    public int processWaitingList() {
        int allocated = 0;
        for (Long waitingId : waitingListService.orderedIds()) {
            Optional<ReservationResponse> result =
                    reservationService.tryAllocateWaitingRequest(waitingId);
            if (result.isPresent()) {
                allocated++;
            }
        }
        return allocated;
    }
}
