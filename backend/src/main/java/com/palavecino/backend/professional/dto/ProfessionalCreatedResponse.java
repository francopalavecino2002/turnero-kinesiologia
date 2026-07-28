package com.palavecino.backend.professional.dto;

import com.palavecino.backend.service.dto.ServiceAdminResponse;
import java.util.List;

public record ProfessionalCreatedResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        boolean active,
        String temporaryPassword,
        List<ServiceAdminResponse> services
) {
}
