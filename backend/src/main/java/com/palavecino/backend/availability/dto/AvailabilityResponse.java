package com.palavecino.backend.availability.dto;

import com.palavecino.backend.availability.DayOfWeek;
import java.time.LocalTime;

public record AvailabilityResponse(
        Long id,
        Long professionalId,
        String professionalName,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Long serviceId,
        String serviceName
) {
}
