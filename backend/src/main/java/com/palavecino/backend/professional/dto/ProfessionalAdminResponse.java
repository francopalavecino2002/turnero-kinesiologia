package com.palavecino.backend.professional.dto;

import com.palavecino.backend.service.dto.ServiceAdminResponse;
import java.util.List;

public record ProfessionalAdminResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        boolean active,
        List<ServiceAdminResponse> services
) {
}
