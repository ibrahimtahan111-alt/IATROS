package com.iatros.backend.kpi.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.iatros.backend.appointment.AppointmentStatus;
import com.iatros.backend.appointment.dto.NameRef;

/**
 * KPIs of a period and of the equally long period before it (api.md §23, decisions.md D-031, D-039). Rates are
 * fractions between 0 and 1, or null when there is nothing to divide by.
 *
 * @param doctor the doctor the KPIs belong to; null for clinic KPIs
 * @param doctors per-doctor figures of the current period; clinic KPIs only, empty otherwise
 */
public record KpiResponse(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo, NameRef doctor,
		Figures current, Figures previous, TrendBucket trendBucket, List<TrendPoint> trend,
		List<DoctorFigures> doctors) {

	public enum TrendBucket {
		DAY, MONTH
	}

	/**
	 * @param activePatients distinct patients with a COMPLETED appointment in the 12 months up to the period's end
	 */
	public record Figures(long appointments, Map<AppointmentStatus, Long> appointmentsByStatus, Double completionRate,
			Double noShowRate, Double cancellationRate, long newPatients, long activePatients, long consultations) {
	}

	/** @param date the first day of the bucket */
	public record TrendPoint(LocalDate date, long appointments, long completed, long cancelled, long noShows) {
	}

	public record DoctorFigures(NameRef doctor, long appointments, long completed, long cancelled, long noShows,
			Double completionRate, long consultations) {
	}

}
