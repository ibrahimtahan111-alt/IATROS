package com.iatros.backend.kpi;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.access.AccessPolicy;
import com.iatros.backend.appointment.AppointmentDirectory;
import com.iatros.backend.appointment.AppointmentStatus;
import com.iatros.backend.appointment.dto.NameRef;
import com.iatros.backend.clinic.ClinicTime;
import com.iatros.backend.doctor.Doctor;
import com.iatros.backend.doctor.DoctorRepository;
import com.iatros.backend.kpi.dto.KpiResponse;
import com.iatros.backend.kpi.dto.KpiResponse.DoctorFigures;
import com.iatros.backend.kpi.dto.KpiResponse.Figures;
import com.iatros.backend.kpi.dto.KpiResponse.TrendBucket;
import com.iatros.backend.kpi.dto.KpiResponse.TrendPoint;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.ForbiddenOperationException;
import com.iatros.backend.shared.error.ValidationException;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.Role;
import com.iatros.backend.user.UserStatus;

/**
 * Clinic and doctor KPIs (api.md §23, decisions.md D-031, D-039). Clinic KPIs are owner-only; doctor KPIs are for
 * the doctor themselves or the owner. A doctor's figures are computed from that doctor's data alone, so no
 * clinic-wide total ever reaches a doctor (security.md §40).
 */
@Service
@Transactional(readOnly = true)
public class KpiService {

	static final int DEFAULT_PERIOD_DAYS = 30;
	static final int MAX_PERIOD_DAYS = 366;
	/** Longer periods are charted by month. */
	static final int MAX_DAILY_TREND_DAYS = 62;
	static final int ACTIVE_PATIENT_MONTHS = 12;

	private final KpiQueries queries;
	private final AccessPolicy access;
	private final ClinicTime clinicTime;
	private final DoctorRepository doctors;
	private final AppointmentDirectory directory;
	private final Clock clock;

	KpiService(KpiQueries queries, AccessPolicy access, ClinicTime clinicTime, DoctorRepository doctors,
			AppointmentDirectory directory, Clock clock) {
		this.queries = queries;
		this.access = access;
		this.clinicTime = clinicTime;
		this.doctors = doctors;
		this.directory = directory;
		this.clock = clock;
	}

	public KpiResponse clinic(AuthenticatedUser caller, LocalDate from, LocalDate to) {
		if (!caller.hasRole(Role.OWNER)) {
			throw new ForbiddenOperationException("FORBIDDEN", "Operation not permitted for this role");
		}
		Period period = period(caller.clinicId(), from, to);
		Map<UUID, AppointmentTally> tallies = queries.appointmentsByDoctor(caller.clinicId(), null,
				period.start(), period.end(), Instant.now(clock));
		Map<UUID, Long> consultations = queries.consultationsByDoctor(caller.clinicId(), null, period.start(),
				period.end());
		return response(caller.clinicId(), null, period, tallies, consultations,
				doctorFigures(caller.clinicId(), tallies, consultations));
	}

	public KpiResponse doctor(AuthenticatedUser caller, UUID doctorId, LocalDate from, LocalDate to) {
		Doctor doctor = access.requireDoctorManagement(caller, doctorId);
		Period period = period(caller.clinicId(), from, to);
		Map<UUID, AppointmentTally> tallies = queries.appointmentsByDoctor(caller.clinicId(), doctor.getId(),
				period.start(), period.end(), Instant.now(clock));
		Map<UUID, Long> consultations = queries.consultationsByDoctor(caller.clinicId(), doctor.getId(),
				period.start(), period.end());
		return response(caller.clinicId(), doctor, period, tallies, consultations, List.of());
	}

	private KpiResponse response(UUID clinicId, Doctor doctor, Period period, Map<UUID, AppointmentTally> tallies,
			Map<UUID, Long> consultations, List<DoctorFigures> doctorFigures) {
		UUID doctorId = doctor == null ? null : doctor.getId();
		Period previous = period.previous();
		Map<UUID, AppointmentTally> previousTallies = queries.appointmentsByDoctor(clinicId, doctorId,
				previous.start(), previous.end(), Instant.now(clock));
		Map<UUID, Long> previousConsultations = queries.consultationsByDoctor(clinicId, doctorId, previous.start(),
				previous.end());
		TrendBucket bucket = period.days() <= MAX_DAILY_TREND_DAYS ? TrendBucket.DAY : TrendBucket.MONTH;
		NameRef doctorRef = doctor == null ? null : directory.doctorRefs(List.of(doctor)).get(doctor.getId());
		return new KpiResponse(period.from(), period.to(), previous.from(), previous.to(), doctorRef,
				figures(clinicId, doctorId, period, tallies, consultations),
				figures(clinicId, doctorId, previous, previousTallies, previousConsultations), bucket,
				trend(clinicId, doctorId, period, bucket), doctorFigures);
	}

	private Figures figures(UUID clinicId, UUID doctorId, Period period, Map<UUID, AppointmentTally> tallies,
			Map<UUID, Long> consultations) {
		AppointmentTally total = new AppointmentTally();
		tallies.values().forEach(total::add);
		Instant activeSince = period.to()
			.plusDays(1)
			.minusMonths(ACTIVE_PATIENT_MONTHS)
			.atStartOfDay(period.zone())
			.toInstant();
		return new Figures(total.total(), total.byStatus(), total.completionRate(), total.noShowRate(),
				total.cancellationRate(), queries.newPatients(clinicId, doctorId, period.start(), period.end()),
				queries.activePatients(clinicId, doctorId, activeSince, period.end()),
				consultations.values().stream().mapToLong(Long::longValue).sum());
	}

	/** Every bucket of the period, including empty ones, so charts have no gaps. */
	private List<TrendPoint> trend(UUID clinicId, UUID doctorId, Period period, TrendBucket bucket) {
		boolean daily = bucket == TrendBucket.DAY;
		Map<LocalDate, KpiQueries.TrendRow> rows = queries
			.trend(clinicId, doctorId, period.start(), period.end(), period.zone(), daily ? "day" : "month")
			.stream()
			.collect(Collectors.toMap(KpiQueries.TrendRow::bucket, Function.identity()));
		List<TrendPoint> points = new ArrayList<>();
		LocalDate date = daily ? period.from() : period.from().withDayOfMonth(1);
		while (!date.isAfter(period.to())) {
			KpiQueries.TrendRow row = rows.get(date);
			points.add(row == null ? new TrendPoint(date, 0, 0, 0, 0)
					: new TrendPoint(date, row.appointments(), row.completed(), row.cancelled(), row.noShows()));
			date = daily ? date.plusDays(1) : date.plusMonths(1);
		}
		return points;
	}

	/** Active doctors, and deactivated doctors who still have figures in the period, by name. */
	private List<DoctorFigures> doctorFigures(UUID clinicId, Map<UUID, AppointmentTally> tallies,
			Map<UUID, Long> consultations) {
		List<Doctor> clinicDoctors = doctors.findAllByClinicId(clinicId);
		Map<UUID, AppUser> users = directory.usersById(clinicDoctors.stream().map(Doctor::getUserId).toList());
		return clinicDoctors.stream()
			.filter(doctor -> users.get(doctor.getUserId()).getStatus() != UserStatus.DEACTIVATED
					|| tallies.containsKey(doctor.getId()) || consultations.containsKey(doctor.getId()))
			.sorted(Comparator.comparing((Doctor doctor) -> users.get(doctor.getUserId()).getLastName(),
					String.CASE_INSENSITIVE_ORDER)
				.thenComparing(doctor -> users.get(doctor.getUserId()).getFirstName(), String.CASE_INSENSITIVE_ORDER))
			.map(doctor -> {
				AppUser user = users.get(doctor.getUserId());
				AppointmentTally tally = tallies.getOrDefault(doctor.getId(), new AppointmentTally());
				return new DoctorFigures(new NameRef(doctor.getId(), user.getFirstName(), user.getLastName()),
						tally.total(), tally.of(AppointmentStatus.COMPLETED), tally.of(AppointmentStatus.CANCELLED),
						tally.of(AppointmentStatus.NO_SHOW), tally.completionRate(),
						consultations.getOrDefault(doctor.getId(), 0L));
			})
			.toList();
	}

	/** Defaults to the last {@value #DEFAULT_PERIOD_DAYS} days up to today (clinic-local). */
	private Period period(UUID clinicId, LocalDate from, LocalDate to) {
		LocalDate end = to != null ? to : clinicTime.today(clinicId);
		LocalDate start = from != null ? from : end.minusDays(DEFAULT_PERIOD_DAYS - 1);
		if (end.isBefore(start)) {
			throw ValidationException.of("to", "END_BEFORE_START");
		}
		if (ChronoUnit.DAYS.between(start, end) >= MAX_PERIOD_DAYS) {
			throw ValidationException.of("to", "OUT_OF_RANGE");
		}
		return new Period(start, end, clinicTime.zone(clinicId));
	}

	/** Clinic-local dates, both inclusive. */
	private record Period(LocalDate from, LocalDate to, ZoneId zone) {

		long days() {
			return ChronoUnit.DAYS.between(from, to) + 1;
		}

		Instant start() {
			return from.atStartOfDay(zone).toInstant();
		}

		Instant end() {
			return to.plusDays(1).atStartOfDay(zone).toInstant();
		}

		Period previous() {
			LocalDate previousTo = from.minusDays(1);
			return new Period(previousTo.minusDays(days() - 1), previousTo, zone);
		}

	}

}
