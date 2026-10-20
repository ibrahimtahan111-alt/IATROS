package com.iatros.backend.dashboard;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Names of severe allergies and active conditions per patient, for the doctor dashboard. Callers pass only patients
 * the doctor is authorized for.
 */
@Repository
class ClinicalAlertQueries {

	private record Label(UUID patientId, String name) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	ClinicalAlertQueries(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	Map<UUID, List<String>> severeAllergies(UUID clinicId, Collection<UUID> patientIds) {
		return labels("""
				select mr.patient_id, a.allergen as name
				from allergy a
				join medical_record mr on mr.id = a.medical_record_id
				where mr.clinic_id = :clinicId
				  and mr.patient_id in (:patientIds)
				  and a.deleted_at is null
				  and a.severity = 'SEVERE'
				order by a.allergen
				""", clinicId, patientIds);
	}

	Map<UUID, List<String>> activeConditions(UUID clinicId, Collection<UUID> patientIds) {
		return labels("""
				select mr.patient_id, c.name
				from medical_condition c
				join medical_record mr on mr.id = c.medical_record_id
				where mr.clinic_id = :clinicId
				  and mr.patient_id in (:patientIds)
				  and c.deleted_at is null
				  and c.status = 'ACTIVE'
				order by c.name
				""", clinicId, patientIds);
	}

	private Map<UUID, List<String>> labels(String sql, UUID clinicId, Collection<UUID> patientIds) {
		if (patientIds.isEmpty()) {
			return Map.of();
		}
		MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("clinicId", clinicId)
			.addValue("patientIds", patientIds);
		return jdbc
			.query(sql, parameters,
					(row, index) -> new Label(row.getObject("patient_id", UUID.class), row.getString("name")))
			.stream()
			.collect(Collectors.groupingBy(Label::patientId,
					Collectors.mapping(Label::name, Collectors.toList())));
	}

}
