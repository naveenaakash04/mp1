package com.smartparking.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Application clock with a controllable offset.
 *
 * <p>All business logic calls {@link #now()} instead of
 * {@code LocalDateTime.now()}. For a demonstration we can advance the clock
 * by 15 minutes to make reservations expire without waiting in real time.</p>
 */
@Service
public class ClockService {

    private final AtomicLong offsetMinutes = new AtomicLong(0);

    public LocalDateTime now() {
        return LocalDateTime.now().plusMinutes(offsetMinutes.get());
    }

    public LocalDateTime realNow() {
        return LocalDateTime.now();
    }

    public void advance(long minutes) {
        if (minutes <= 0) {
            throw new IllegalArgumentException("Minutes to advance must be positive");
        }
        offsetMinutes.addAndGet(minutes);
    }

    public void reset() {
        offsetMinutes.set(0);
    }

    public long getOffsetMinutes() {
        return offsetMinutes.get();
    }
}
