package com.palavecino.backend.professional.dto;

import com.palavecino.backend.service.dto.ServiceResponse;
import java.util.List;

public record ProfessionalResponse(Long id, String firstName, String lastName, List<ServiceResponse> services) {
}
