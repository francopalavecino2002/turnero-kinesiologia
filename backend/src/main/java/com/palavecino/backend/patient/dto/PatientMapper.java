package com.palavecino.backend.patient.dto;

import com.palavecino.backend.patient.Patient;

public final class PatientMapper {

    private PatientMapper() {
    }

    public static PatientSearchResponse toSearchResponse(Patient patient) {
        return new PatientSearchResponse(
                patient.getId(),
                patient.getFirstName() + " " + patient.getLastName(),
                patient.getEmail(),
                patient.getPhone(),
                !patient.isGuest());
    }
}
