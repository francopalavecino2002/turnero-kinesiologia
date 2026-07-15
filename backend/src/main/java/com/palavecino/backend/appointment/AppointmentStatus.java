package com.palavecino.backend.appointment;

import java.util.Set;

/**
 * Explicit state machine instead of scattered ifs across the service: the set of valid
 * transitions lives in exactly one place, is exhaustive (the compiler enforces a branch per
 * enum constant), and is trivially unit-testable independent of any HTTP/persistence concerns.
 * Scattered conditionals in the service would let a valid transition rule silently diverge
 * between bookAppointment, cancel, confirm, etc. - here there is only one source of truth.
 */
public enum AppointmentStatus {
    BOOKED,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    NO_SHOW;

    private static final Set<AppointmentStatus> BOOKED_TARGETS =
            Set.of(CONFIRMED, CANCELLED, COMPLETED, NO_SHOW);
    private static final Set<AppointmentStatus> CONFIRMED_TARGETS =
            Set.of(CANCELLED, COMPLETED, NO_SHOW);

    public boolean canTransitionTo(AppointmentStatus target) {
        return switch (this) {
            case BOOKED -> BOOKED_TARGETS.contains(target);
            case CONFIRMED -> CONFIRMED_TARGETS.contains(target);
            case CANCELLED, COMPLETED, NO_SHOW -> false;
        };
    }
}
