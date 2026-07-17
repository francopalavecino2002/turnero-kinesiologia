package com.palavecino.backend.appointment.dto;

import com.palavecino.backend.appointment.AppointmentStatus;
import java.time.LocalDateTime;

/**
 * A privacy-aware view of an appointment for the clinic-wide agenda screen.
 * <p>
 * For the <em>owner</em> (the professional who owns the appointment) or an admin,
 * all fields are populated including patient data and the appointment {@code id}.
 * For <em>other</em> professionals the patient data is {@code null} and {@code id}
 * is {@code null} so the frontend cannot construct action URLs against them.
 * <p>
 * The backend enforces this split — patient data is never sent to a professional
 * who does not own the appointment, regardless of what the frontend does.
 */
public record AgendaEntryResponse(
        Long id,
        LocalDateTime dateTime,
        LocalDateTime endTime,
        String serviceName,
        long professionalId,
        String professionalFirstName,
        String professionalLastName,
        AppointmentStatus status,
        String patientFirstName,
        String patientLastName,
        boolean ownedByCurrentUser) {
}
