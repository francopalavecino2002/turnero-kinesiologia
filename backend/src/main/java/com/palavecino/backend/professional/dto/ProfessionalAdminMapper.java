package com.palavecino.backend.professional.dto;

import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.service.dto.ServiceMapper;

public final class ProfessionalAdminMapper {

    private ProfessionalAdminMapper() {
    }

    public static ProfessionalAdminResponse toAdminResponse(Professional professional) {
        return new ProfessionalAdminResponse(
                professional.getId(),
                professional.getFirstName(),
                professional.getLastName(),
                professional.getUser().getEmail(),
                professional.getUser().isActive(),
                professional.getServices().stream()
                        .map(ServiceMapper::toAdminResponse)
                        .toList()
        );
    }

    public static ProfessionalCreatedResponse toCreatedResponse(Professional professional, String temporaryPassword) {
        return new ProfessionalCreatedResponse(
                professional.getId(),
                professional.getFirstName(),
                professional.getLastName(),
                professional.getUser().getEmail(),
                professional.getUser().isActive(),
                temporaryPassword,
                professional.getServices().stream()
                        .map(ServiceMapper::toAdminResponse)
                        .toList()
        );
    }
}
