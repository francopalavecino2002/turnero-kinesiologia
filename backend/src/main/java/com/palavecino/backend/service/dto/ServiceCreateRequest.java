package com.palavecino.backend.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ServiceCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @Min(5) @Max(480) int durationMinutes
) {
}
