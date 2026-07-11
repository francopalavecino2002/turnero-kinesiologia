package com.palavecino.backend.appointment.dto;

import com.palavecino.backend.appointment.Appointment;

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
}
