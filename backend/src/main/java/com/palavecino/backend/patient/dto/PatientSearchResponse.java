package com.palavecino.backend.patient.dto;

/**
 * Result row for the staff patient search (autocomplete when manually booking an appointment).
 * {@code registered} distinguishes a patient with an account from a guest, so the UI can label
 * them differently.
 */
public record PatientSearchResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        boolean registered) {
}
