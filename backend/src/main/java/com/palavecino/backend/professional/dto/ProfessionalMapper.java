package com.palavecino.backend.professional.dto;

import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.service.dto.ServiceMapper;
import com.palavecino.backend.service.dto.ServiceResponse;
import java.util.Comparator;
import java.util.List;

public final class ProfessionalMapper {

    private ProfessionalMapper() {
    }

    // Requires professional.getServices() to already be initialized (JOIN FETCH-ed) - open-in-view
    // is disabled, so touching the lazy collection here without a fetch join would throw
    // LazyInitializationException.
    public static ProfessionalResponse toResponse(Professional professional) {
        List<ServiceResponse> services = professional.getServices().stream()
                .map(ServiceMapper::toResponse)
                .sorted(Comparator.comparing(ServiceResponse::name))
                .toList();
        return new ProfessionalResponse(professional.getId(), professional.getFirstName(),
                professional.getLastName(), services);
    }
}
