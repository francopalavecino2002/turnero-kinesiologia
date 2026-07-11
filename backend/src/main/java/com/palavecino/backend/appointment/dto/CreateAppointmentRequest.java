package com.palavecino.backend.appointment.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CreateAppointmentRequest(
        @NotNull Long patientId,
        @NotNull Long professionalId,
        @NotNull Long serviceId,
        @NotNull @Future LocalDateTime dateTime) {
}
