package com.palavecino.backend.appointment.dto;

import com.palavecino.backend.appointment.Appointment;
import java.time.LocalDateTime;

public final class AppointmentMapper {

    private AppointmentMapper() {
    }

    public static AppointmentResponse toResponse(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getDateTime(),
                appointment.getStatus(),
                appointment.getService().getName(),
                appointment.getService().getDurationMinutes(),
                appointment.getProfessional().getFirstName(),
                appointment.getProfessional().getLastName(),
                appointment.getPatient().getFirstName(),
                appointment.getPatient().getLastName());
    }

    public static AgendaEntryResponse toFullAgendaEntry(Appointment appointment) {
        return new AgendaEntryResponse(
                appointment.getId(),
                appointment.getDateTime(),
                appointment.getDateTime().plusMinutes(appointment.getService().getDurationMinutes()),
                appointment.getService().getName(),
                appointment.getProfessional().getId(),
                appointment.getProfessional().getFirstName(),
                appointment.getProfessional().getLastName(),
                appointment.getStatus(),
                appointment.getPatient().getFirstName(),
                appointment.getPatient().getLastName(),
                true);
    }

    public static AgendaEntryResponse toReducedAgendaEntry(Appointment appointment) {
        return new AgendaEntryResponse(
                null,
                appointment.getDateTime(),
                appointment.getDateTime().plusMinutes(appointment.getService().getDurationMinutes()),
                appointment.getService().getName(),
                appointment.getProfessional().getId(),
                appointment.getProfessional().getFirstName(),
                appointment.getProfessional().getLastName(),
                appointment.getStatus(),
                null,
                null,
                false);
    }
}
