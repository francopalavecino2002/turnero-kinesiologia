package com.palavecino.backend.appointment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Contact data for a walk-in/phone patient with no account, submitted as part of a
 * {@link StaffBookAppointmentRequest}. Email is optional - a guest with no email simply won't
 * receive a confirmation mail.
 */
public record GuestPatientRequest(
        @NotBlank String name,
        @NotBlank String phone,
        @Email String email) {
}
