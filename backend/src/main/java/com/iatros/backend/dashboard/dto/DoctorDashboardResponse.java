package com.iatros.backend.dashboard.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.iatros.backend.activity.dto.ActivityItemResponse;
import com.iatros.backend.appointment.dto.NameRef;

/**
 * A doctor's own day (api.md §22, ui-ux.md §8). Every figure comes from the doctor's own appointments and
 * authorized patients; nothing clinic-wide is included.
 *
 * @param upcomingAppointments SCHEDULED or CONFIRMED appointments in the next seven days after today
 * @param patients active patients the doctor is primary or additional doctor for
 */
public record DoctorDashboardResponse(String timeZone, LocalDate today, NameRef doctor, DaySummary summary,
		long upcomingAppointments, long patients, long primaryPatients, List<ScheduleItem> schedule,
		List<RecentPatient> recentPatients, List<ActivityItemResponse> recentActivity) {

	public record RecentPatient(NameRef patient, Instant lastVisitAt) {
	}

}
