package com.palavecino.backend.recurringblock.dto;

import com.palavecino.backend.availability.DayOfWeek;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record RecurringBlockUpdateRequest(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotBlank @Size(max = 255) String description,
        Long serviceId,
        Long professionalId
) {
}
