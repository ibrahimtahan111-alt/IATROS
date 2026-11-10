package com.iatros.backend.medicalrecord;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.medicalrecord.dto.AuthorRef;
import com.iatros.backend.medicalrecord.dto.NoteRequest;
import com.iatros.backend.medicalrecord.dto.NoteResponse;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.ForbiddenOperationException;
import com.iatros.backend.shared.error.NotFoundException;
import com.iatros.backend.shared.text.Text;
import com.iatros.backend.shared.web.PageResponse;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;

/**
 * General medical notes (decisions.md D-037): newest first, written by any clinician with access, and edited or
 * removed only by their author, since a note is a signed statement.
 */
@Service
public class MedicalNoteService {

	private final MedicalRecordAccess recordAccess;
	private final MedicalNoteRepository notes;
	private final AppUserRepository users;
	private final ClinicalEntryWriter writer;

	MedicalNoteService(MedicalRecordAccess recordAccess, MedicalNoteRepository notes, AppUserRepository users,
			ClinicalEntryWriter writer) {
		this.recordAccess = recordAccess;
		this.notes = notes;
		this.users = users;
		this.writer = writer;
	}

	@Transactional(readOnly = true)
	public PageResponse<NoteResponse> list(AuthenticatedUser caller, UUID patientId, Pageable pageable) {
		MedicalRecord record = recordAccess.readable(caller, patientId);
		Page<MedicalNote> found = notes.findAllByMedicalRecordId(record.getId(), pageable);
		Map<UUID, AppUser> authors = users.findAllById(found.getContent().stream().map(MedicalNote::getCreatedBy).toList())
			.stream()
			.collect(Collectors.toMap(AppUser::getId, Function.identity()));
		return PageResponse.from(found, note -> toResponse(caller, note, authors.get(note.getCreatedBy())));
	}

	@Transactional
	public NoteResponse add(AuthenticatedUser caller, UUID patientId, NoteRequest request) {
		MedicalRecord record = recordAccess.writable(caller, patientId);
		MedicalNote note = writer.create(caller, record,
				new MedicalNote(record.getClinicId(), record.getId(), Text.trim(request.content()), caller.userId()), notes,
				AuditActions.ENTITY_MEDICAL_NOTE);
		return toResponse(caller, note, author(note));
	}

	@Transactional
	public NoteResponse update(AuthenticatedUser caller, UUID patientId, UUID noteId, NoteRequest request) {
		MedicalRecord record = recordAccess.writable(caller, patientId);
		MedicalNote note = requireOwnNote(caller, record, noteId);
		writer.update(caller, record, note, request.version(),
				found -> found.update(Text.trim(request.content()), caller.userId()), notes,
				AuditActions.ENTITY_MEDICAL_NOTE);
		return toResponse(caller, note, author(note));
	}

	@Transactional
	public void remove(AuthenticatedUser caller, UUID patientId, UUID noteId) {
		MedicalRecord record = recordAccess.writable(caller, patientId);
		writer.delete(caller, record, requireOwnNote(caller, record, noteId), notes, AuditActions.ENTITY_MEDICAL_NOTE);
	}

	private MedicalNote requireOwnNote(AuthenticatedUser caller, MedicalRecord record, UUID noteId) {
		MedicalNote note = notes.findByIdAndMedicalRecordId(noteId, record.getId())
			.orElseThrow(() -> NotFoundException.of("MEDICAL_NOTE"));
		if (!note.getCreatedBy().equals(caller.userId())) {
			throw new ForbiddenOperationException("FORBIDDEN", "Only the author can change a note");
		}
		return note;
	}

	private AppUser author(MedicalNote note) {
		return users.findById(note.getCreatedBy()).orElse(null);
	}

	private static NoteResponse toResponse(AuthenticatedUser caller, MedicalNote note, AppUser author) {
		AuthorRef authorRef = author == null ? null
				: new AuthorRef(author.getId(), author.getFirstName(), author.getLastName());
		return new NoteResponse(note.getId(), note.getContent(), authorRef, note.getCreatedAt(), note.getUpdatedAt(),
				note.getUpdatedBy() != null, note.getCreatedBy().equals(caller.userId()), note.getVersion());
	}

}
