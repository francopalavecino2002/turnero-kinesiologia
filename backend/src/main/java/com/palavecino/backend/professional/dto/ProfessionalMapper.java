package com.palavecino.backend.professional.dto;

import com.palavecino.backend.professional.Professional;

public final class ProfessionalMapper {

    private ProfessionalMapper() {
    }

    public static ProfessionalResponse toResponse(Professional professional) {
        return new ProfessionalResponse(professional.getId(), professional.getFirstName(), professional.getLastName());
    }
}
