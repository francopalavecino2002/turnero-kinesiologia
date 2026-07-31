package com.palavecino.backend.recurringblock.dto;

import com.palavecino.backend.availability.DayOfWeek;
import java.time.LocalTime;

public record RecurringBlockAdminResponse(
        Long id,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        String description,
        boolean active,
        Long serviceId,
        String serviceName,
        Long professionalId,
        String professionalName,
        int affectedAppointmentsCount
) {
}
