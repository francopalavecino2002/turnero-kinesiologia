package com.palavecino.backend.appointment.dto;

import com.palavecino.backend.appointment.AppointmentStatus;
import java.time.LocalDateTime;

public record AppointmentResponse(
        Long id,
        LocalDateTime dateTime,
        AppointmentStatus status,
        String serviceName,
        int serviceDurationMinutes,
        String professionalFirstName,
        String professionalLastName,
        String patientFirstName,
        String patientLastName) {
}
