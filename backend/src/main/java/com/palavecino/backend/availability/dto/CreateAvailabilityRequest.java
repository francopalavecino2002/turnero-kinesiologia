package com.palavecino.backend.availability.dto;

import com.palavecino.backend.availability.DayOfWeek;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record CreateAvailabilityRequest(
        @NotNull Long professionalId,
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        Long serviceId
) {
}
