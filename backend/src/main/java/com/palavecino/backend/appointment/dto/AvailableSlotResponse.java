package com.palavecino.backend.appointment.dto;

import java.time.LocalDateTime;

public record AvailableSlotResponse(LocalDateTime startTime, LocalDateTime endTime) {
}
