package com.palavecino.backend.appointment.dto;

import java.util.List;

/**
 * Day-level summary for a month calendar view. Each entry represents one day
 * that has at least one active (non-cancelled) appointment. Days with only
 * cancelled appointments are omitted entirely — the frontend treats absent
 * days as empty.
 *
 * @param days ordered list of non-empty days in the requested month
 */
public record MonthSummaryResponse(List<DaySummary> days) {

    /**
     * @param day   day of month (1-31)
     * @param count number of active appointments on that day
     */
    public record DaySummary(int day, int count) {
    }
}
