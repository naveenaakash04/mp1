package com.smartparking.event;

/**
 * Published when a vehicle's accessibility permit changes. The waiting
 * queue is re-ranked, then it is reconsidered for allocation.
 */
public record PriorityChangedEvent(Long vehicleId) {
}
