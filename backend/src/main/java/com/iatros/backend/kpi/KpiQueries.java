package com.iatros.backend.kpi;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.iatros.backend.appointment.AppointmentStatus;

/**
 * Aggregate KPI queries over the authoritative tables (modules.md §14: no manually maintained counters). Every query
 * is limited to one clinic and, when {@code doctorId} is given, to that doctor's data. Instants bound the period as
 * [from, to).
 */
@Repository
class KpiQueries {

	/** A period's appointments grouped into one trend bucket (a clinic-local day or month). */
	record TrendRow(LocalDate bucket, long appointments, long completed, long cancelled, long noShows) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	KpiQueries(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	Map<UUID, AppointmentTally> appointmentsByDoctor(UUID clinicId, UUID doctorId, Instant from, Instant to,
			Instant now) {
		String sql = """
				select doctor_id, status, count(*) as total, count(*) filter (where start_at < :now) as past
				from appointment
				where clinic_id = :clinicId
				  and start_at >= :from
				  and start_at < :to
				""" + doctorFilter(doctorId) + """
				group by doctor_id, status
				""";
		Map<UUID, AppointmentTally> tallies = new HashMap<>();
		jdbc.query(sql, period(clinicId, doctorId, from, to).addValue("now", timestamp(now)), row -> {
			AppointmentTally tally = tallies.computeIfAbsent(row.getObject("doctor_id", UUID.class),
					id -> new AppointmentTally());
			AppointmentStatus status = AppointmentStatus.valueOf(row.getString("status"));
			long past = row.getLong("past");
			tally.count(status, row.getLong("total"));
			switch (status) {
				case COMPLETED -> tally.countPast(past, 0, 0);
				case NO_SHOW -> tally.countPast(0, past, 0);
				case CANCELLED -> tally.countPast(0, 0, past);
				default -> {
				}
			}
		});
		return tallies;
	}

	/** {@code unit} is {@code day} or {@code month}; buckets are clinic-local. */
	List<TrendRow> trend(UUID clinicId, UUID doctorId, Instant from, Instant to, ZoneId zone, String unit) {
		String sql = """
				select cast(date_trunc(cast(:unit as text), start_at at time zone cast(:zone as text)) as date) as bucket,
				       count(*) as total,
				       count(*) filter (where status = 'COMPLETED') as completed,
				       count(*) filter (where status = 'CANCELLED') as cancelled,
				       count(*) filter (where status = 'NO_SHOW') as no_shows
				from appointment
				where clinic_id = :clinicId
				  and start_at >= :from
				  and start_at < :to
				""" + doctorFilter(doctorId) + """
				group by bucket
				order by bucket
				""";
		return jdbc.query(sql, period(clinicId, doctorId, from, to).addValue("unit", unit).addValue("zone", zone.getId()),
				(row, index) -> new TrendRow(row.getObject("bucket", LocalDate.class), row.getLong("total"),
						row.getLong("completed"), row.getLong("cancelled"), row.getLong("no_shows")));
	}

	/** Patients created in the period; for a doctor, those they are primary doctor for. */
	long newPatients(UUID clinicId, UUID doctorId, Instant from, Instant to) {
		String sql = """
				select count(*) from patient p
				where p.clinic_id = :clinicId
				  and p.created_at >= :from
				  and p.created_at < :to
				""" + (doctorId == null ? "" : """
				  and exists (select 1 from patient_doctor pd
				              where pd.patient_id = p.id and pd.doctor_id = :doctorId
				                and pd.relationship_type = 'PRIMARY')
				""");
		return count(sql, period(clinicId, doctorId, from, to));
	}

	/** Distinct patients with at least one COMPLETED appointment in the period (with the doctor, if given). */
	long activePatients(UUID clinicId, UUID doctorId, Instant from, Instant to) {
		String sql = """
				select count(distinct patient_id) from appointment
				where clinic_id = :clinicId
				  and status = 'COMPLETED'
				  and start_at >= :from
				  and start_at < :to
				""" + doctorFilter(doctorId);
		return count(sql, period(clinicId, doctorId, from, to));
	}

	Map<UUID, Long> consultationsByDoctor(UUID clinicId, UUID doctorId, Instant from, Instant to) {
		String sql = """
				select doctor_id, count(*) as total from consultation
				where clinic_id = :clinicId
				  and consulted_at >= :from
				  and consulted_at < :to
				""" + doctorFilter(doctorId) + """
				group by doctor_id
				""";
		Map<UUID, Long> counts = new HashMap<>();
		jdbc.query(sql, period(clinicId, doctorId, from, to),
				row -> {
					counts.put(row.getObject("doctor_id", UUID.class), row.getLong("total"));
				});
		return counts;
	}

	private long count(String sql, MapSqlParameterSource parameters) {
		Long count = jdbc.queryForObject(sql, parameters, Long.class);
		return count == null ? 0 : count;
	}

	/** A constant SQL fragment; the doctor ID itself is always a bound parameter. */
	private static String doctorFilter(UUID doctorId) {
		return doctorId == null ? "" : "  and doctor_id = :doctorId\n";
	}

	private static MapSqlParameterSource period(UUID clinicId, UUID doctorId, Instant from, Instant to) {
		MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("clinicId", clinicId)
			.addValue("from", timestamp(from))
			.addValue("to", timestamp(to));
		return doctorId == null ? parameters : parameters.addValue("doctorId", doctorId);
	}

	private static OffsetDateTime timestamp(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

}
