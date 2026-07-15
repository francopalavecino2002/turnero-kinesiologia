package com.palavecino.backend.service.dto;

import com.palavecino.backend.service.Service;

public final class ServiceMapper {

    private ServiceMapper() {
    }

    public static ServiceResponse toResponse(Service service) {
        return new ServiceResponse(service.getId(), service.getName(), service.getDurationMinutes());
    }
}
