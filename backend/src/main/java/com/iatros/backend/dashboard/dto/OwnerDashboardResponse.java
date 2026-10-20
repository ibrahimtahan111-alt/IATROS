package com.iatros.backend.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

import com.iatros.backend.activity.dto.ActivityItemResponse;
import com.iatros.backend.appointment.dto.NameRef;

/**
 * The owner's view of the clinic today (api.md §22, ui-ux.md §7). Period KPIs come from {@code /kpis/clinic}.
 *
 * @param activePatients patients whose record is ACTIVE (not archived)
 * @param activeDoctors doctors whose account is active
 * @param scheduleTruncated true when today has more appointments than the schedule list holds
 */
public record OwnerDashboardResponse(String timeZone, LocalDate today, DaySummary summary, long activePatients,
		long activeDoctors, List<DoctorDay> doctors, List<ScheduleItem> schedule, boolean scheduleTruncated,
		List<ActivityItemResponse> recentActivity) {

	/** One doctor's day: {@code inProgress} counts WAITING and IN_CONSULTATION. */
	public record DoctorDay(NameRef doctor, long appointments, long completed, long inProgress, long remaining,
			long noShows) {
	}

}
