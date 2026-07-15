package com.palavecino.backend.appointment.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Deliberately has no "patientId" field. The patient is always resolved from the authenticated
 * principal in AppointmentService.bookAppointment - a patient can only ever book for themselves.
 * An admin-on-behalf-of-a-patient booking flow is a separate, not-yet-implemented endpoint.
 */
public record CreateAppointmentRequest(
        @NotNull Long professionalId,
        @NotNull Long serviceId,
        @NotNull @Future LocalDateTime dateTime) {
}
