package com.iatros.backend.medicalrecord;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.iatros.backend.medicalrecord.dto.AllergyRequest;
import com.iatros.backend.medicalrecord.dto.AllergyResponse;
import com.iatros.backend.medicalrecord.dto.ConditionRequest;
import com.iatros.backend.medicalrecord.dto.ConditionResponse;
import com.iatros.backend.medicalrecord.dto.HistoryEntryRequest;
import com.iatros.backend.medicalrecord.dto.HistoryEntryResponse;
import com.iatros.backend.medicalrecord.dto.MedicalRecordResponse;
import com.iatros.backend.medicalrecord.dto.MedicationRequest;
import com.iatros.backend.medicalrecord.dto.MedicationResponse;
import com.iatros.backend.medicalrecord.dto.NoteRequest;
import com.iatros.backend.medicalrecord.dto.NoteResponse;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.web.PageRequests;
import com.iatros.backend.shared.web.PageResponse;
import com.iatros.backend.shared.web.SortWhitelist;

import jakarta.validation.Valid;

/**
 * Medical record API (api.md §12–15). Clinical access (owners, linked doctors; never secretaries) is enforced by
 * {@link MedicalRecordAccess} in the services.
 */
@RestController
@RequestMapping("/api/v1/patients/{patientId}/medical-record")
class MedicalRecordController {

	static final SortWhitelist NOTE_SORTS = SortWhitelist.of(Sort.by(Sort.Direction.DESC, "createdAt"),
			Map.of("createdAt", "createdAt"));

	private final MedicalRecordService recordService;
	private final MedicalNoteService noteService;

	MedicalRecordController(MedicalRecordService recordService, MedicalNoteService noteService) {
		this.recordService = recordService;
		this.noteService = noteService;
	}

	@GetMapping
	MedicalRecordResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID patientId) {
		return recordService.get(user, patientId);
	}

	@PostMapping("/allergies")
	@ResponseStatus(HttpStatus.CREATED)
	AllergyResponse addAllergy(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID patientId,
			@Valid @RequestBody AllergyRequest request) {
		return recordService.addAllergy(user, patientId, request);
	}

	@PutMapping("/allergies/{allergyId}")
	AllergyResponse updateAllergy(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID patientId,
			@PathVariable UUID allergyId, @Valid @RequestBody AllergyRequest request) {
		return recordService.updateAllergy(user, patientId, allergyId, request);
	}

	@DeleteMapping("/allergies/{allergyId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeAllergy(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID patientId,
			@PathVariable UUID allergyId) {
		recordService.removeAllergy(user, patientId, allergyId);
	}

	@PostMapping("/conditions")
	@ResponseStatus(HttpStatus.CREATED)
	ConditionResponse addCondition(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID patientId,
			@Valid @RequestBody ConditionRequest request) {
		return recordService.addCondition(user, patientId, request);
	}

	@PutMapping("/conditions/{conditionId}")
	ConditionResponse updateCondition(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID patientId,
			@PathVariable UUID conditionId, @Valid @RequestBody ConditionRequest request) {
		return recordService.updateCondition(user, patientId, conditionId, request);
	}

	@DeleteMapping("/conditions/{conditionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeCondition(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID patientId,
			@PathVariable UUID conditionId) {
		recordService.removeCondition(user, patientId, conditionId);
	}

	@PostMapping("/medications")
	@ResponseStatus(HttpStatus.CREATED)
	MedicationResponse addMedication(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID patientId,
			@Valid @RequestBody MedicationRequest request) {
		return recordService.addMedication(user, patientId, request);
	}

	@PutMapping("/medications/{medicationId}")
	MedicationResponse updateMedication(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID patientId, @PathVariable UUID medicationId,
			@Valid @RequestBody MedicationRequest request) {
		return recordService.updateMedication(user, patientId, medicationId, request);
	}

	@DeleteMapping("/medications/{medicationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeMedication(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID patientId,
			@PathVariable UUID medicationId) {
		recordService.removeMedication(user, patientId, medicationId);
	}

	@PostMapping("/history")
	@ResponseStatus(HttpStatus.CREATED)
	HistoryEntryResponse addHistoryEntry(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID patientId, @Valid @RequestBody HistoryEntryRequest request) {
		return recordService.addHistoryEntry(user, patientId, request);
	}

	@PutMapping("/history/{entryId}")
	HistoryEntryResponse updateHistoryEntry(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID patientId, @PathVariable UUID entryId,
			@Valid @RequestBody HistoryEntryRequest request) {
		return recordService.updateHistoryEntry(user, patientId, entryId, request);
	}

	@DeleteMapping("/history/{entryId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeHistoryEntry(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID patientId,
			@PathVariable UUID entryId) {
		recordService.removeHistoryEntry(user, patientId, entryId);
	}

	@GetMapping("/notes")
	PageResponse<NoteResponse> listNotes(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID patientId, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) List<String> sort) {
		return noteService.list(user, patientId, PageRequests.of(page, size, sort, NOTE_SORTS));
	}

	@PostMapping("/notes")
	@ResponseStatus(HttpStatus.CREATED)
	NoteResponse addNote(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID patientId,
			@Valid @RequestBody NoteRequest request) {
		return noteService.add(user, patientId, request);
	}

	@PutMapping("/notes/{noteId}")
	NoteResponse updateNote(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID patientId,
			@PathVariable UUID noteId, @Valid @RequestBody NoteRequest request) {
		return noteService.update(user, patientId, noteId, request);
	}

	@DeleteMapping("/notes/{noteId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeNote(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID patientId,
			@PathVariable UUID noteId) {
		noteService.remove(user, patientId, noteId);
	}

}
