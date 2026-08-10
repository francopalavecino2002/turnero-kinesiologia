package com.palavecino.backend.appointment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Manual booking made by staff (PROFESSIONAL or ADMIN) on behalf of a patient, e.g. someone who
 * called or walked in. Exactly one of {@code patientId} (an existing, registered patient) or
 * {@code guestPatient} (a new walk-in with no account) must be provided - enforced in
 * AppointmentService, not here, since it's a cross-field rule.
 */
public record StaffBookAppointmentRequest(
        @NotNull Long professionalId,
        @NotNull Long serviceId,
        @NotNull @Future LocalDateTime dateTime,
        Long patientId,
        @Valid GuestPatientRequest guestPatient) {
}
