package com.iatros.backend.schedule.dto;

import java.util.List;
import java.util.UUID;

/** A doctor's weekly schedule (api.md §20). {@code timeZone} is the clinic's, in which all times are expressed. */
public record WeeklyScheduleResponse(UUID doctorId, String timeZone, long version, List<SchedulePeriodDto> periods) {
}
