package com.iatros.backend.clinic;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.iatros.backend.shared.i18n.Language;
import com.iatros.backend.shared.persistence.AuditedEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Operational settings of a clinic. Shares its primary key with the clinic. */
@Entity
@Table(name = "clinic_settings")
@AttributeOverride(name = "id", column = @Column(name = "clinic_id"))
public class ClinicSettings extends AuditedEntity {

	public static final int DEFAULT_APPOINTMENT_MINUTES = 30;

	@Column(name = "time_zone", nullable = false, length = 64)
	private String timeZone;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "default_appointment_minutes", nullable = false)
	private int defaultAppointmentMinutes = DEFAULT_APPOINTMENT_MINUTES;

	@Column(name = "default_language", nullable = false, length = 5)
	private Language defaultLanguage = Language.EN;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "week_start_day", nullable = false)
	private int weekStartDay = DayOfWeek.MONDAY.getValue();

	protected ClinicSettings() {
	}

	public ClinicSettings(UUID clinicId, ZoneId timeZone) {
		super(clinicId);
		this.timeZone = timeZone.getId();
	}

	public UUID getClinicId() {
		return getId();
	}

	public ZoneId getTimeZone() {
		return ZoneId.of(timeZone);
	}

	public int getDefaultAppointmentMinutes() {
		return defaultAppointmentMinutes;
	}

	public Language getDefaultLanguage() {
		return defaultLanguage;
	}

	public DayOfWeek getWeekStartDay() {
		return DayOfWeek.of(weekStartDay);
	}

	public void update(ZoneId timeZone, int defaultAppointmentMinutes, Language defaultLanguage, DayOfWeek weekStartDay) {
		this.timeZone = timeZone.getId();
		this.defaultAppointmentMinutes = defaultAppointmentMinutes;
		this.defaultLanguage = defaultLanguage;
		this.weekStartDay = weekStartDay.getValue();
	}

}
