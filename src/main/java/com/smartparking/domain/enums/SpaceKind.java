package com.smartparking.domain.enums;

/**
 * Physical kind of a parking space.
 *
 * <p>ACCESSIBLE spaces require an accessibility permit. CAR and TWO_WHEELER
 * spaces are normal spaces that only accept the matching vehicle type.</p>
 */
public enum SpaceKind {
    CAR,
    TWO_WHEELER,
    ACCESSIBLE
}
