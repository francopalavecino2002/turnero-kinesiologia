package com.palavecino.backend.service.dto;

public record ServiceAdminResponse(Long id, String name, int durationMinutes, boolean active) {
}
