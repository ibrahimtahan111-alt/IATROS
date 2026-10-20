package com.iatros.backend.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.iatros.backend.audit.AuditActions;
import com.iatros.backend.audit.AuditService;
import com.iatros.backend.consultation.ConsultationRepository;
import com.iatros.backend.document.dto.DocumentResponse;
import com.iatros.backend.medicalrecord.MedicalRecord;
import com.iatros.backend.medicalrecord.MedicalRecordAccess;
import com.iatros.backend.medicalrecord.dto.AuthorRef;
import com.iatros.backend.patient.Patient;
import com.iatros.backend.patient.PatientService;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.error.ForbiddenOperationException;
import com.iatros.backend.shared.error.NotFoundException;
import com.iatros.backend.shared.error.ValidationException;
import com.iatros.backend.shared.id.UuidV7;
import com.iatros.backend.shared.web.PageResponse;
import com.iatros.backend.shared.web.SearchTerms;
import com.iatros.backend.user.AppUser;
import com.iatros.backend.user.AppUserRepository;
import com.iatros.backend.user.Role;

/**
 * Medical documents (api.md §21, decisions.md D-032, D-044). Access follows the medical record: owners and the
 * patient's doctors, never secretaries. Files are validated from their content, stored under generated keys, and
 * archived rather than deleted. File names stay out of the audit trail and logs, since they can be clinical.
 */
@Service
public class DocumentService {

	private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

	private final MedicalDocumentRepository documents;
	private final ConsultationRepository consultations;
	private final MedicalRecordAccess recordAccess;
	private final AppUserRepository users;
	private final DocumentStorage storage;
	private final DocumentSettings settings;
	private final AuditService audit;
	private final Clock clock;

	DocumentService(MedicalDocumentRepository documents, ConsultationRepository consultations,
			MedicalRecordAccess recordAccess, AppUserRepository users, DocumentStorage storage,
			DocumentSettings settings, AuditService audit, Clock clock) {
		this.documents = documents;
		this.consultations = consultations;
		this.recordAccess = recordAccess;
		this.users = users;
		this.storage = storage;
		this.settings = settings;
		this.audit = audit;
		this.clock = clock;
	}

	/** Current documents, newest first by default. */
	@Transactional(readOnly = true)
	public PageResponse<DocumentResponse> list(AuthenticatedUser caller, UUID patientId, UUID consultationId,
			String search, Pageable pageable) {
		MedicalRecord record = recordAccess.readable(caller, patientId);
		Page<MedicalDocument> found = documents.search(record.getId(), consultationId,
				SearchTerms.containsPattern(search), pageable);
		Map<UUID, AuthorRef> uploaders = authors(
				found.getContent().stream().map(MedicalDocument::getUploadedBy).collect(Collectors.toSet()));
		return PageResponse.from(found,
				document -> toResponse(caller, document, patientId, uploaders.get(document.getUploadedBy())));
	}

	@Transactional(readOnly = true)
	public DocumentResponse get(AuthenticatedUser caller, UUID documentId) {
		MedicalDocument document = requireDocument(caller, documentId);
		return toResponse(caller, document, patientOf(caller, document).getId());
	}

	@Transactional
	public DocumentResponse upload(AuthenticatedUser caller, UUID patientId, UUID consultationId,
			MultipartFile file) {
		MedicalRecord record = recordAccess.writable(caller, patientId);
		UUID consultation = consultationId == null ? null : requireConsultation(caller, record, consultationId);
		if (file == null) {
			throw ValidationException.of("file", "REQUIRED");
		}
		if (file.isEmpty()) {
			throw ValidationException.of("file", "FILE_EMPTY");
		}
		if (file.getSize() > settings.maxSize().toBytes()) {
			throw ValidationException.of("file", "FILE_TOO_LARGE");
		}
		String fileName = DocumentFileNames.clean(file.getOriginalFilename());
		String extension = DocumentFileNames.extension(fileName);
		if (!DocumentType.isAcceptedExtension(extension)) {
			throw ValidationException.of("file", "UNSUPPORTED_FILE_TYPE");
		}
		DocumentType type = DocumentType.detect(head(file))
			.orElseThrow(() -> ValidationException.of("file", "UNSUPPORTED_FILE_TYPE"));
		if (!type.acceptsExtension(extension)) {
			throw ValidationException.of("file", "FILE_TYPE_MISMATCH");
		}

		String key = UuidV7.generate().toString();
		deleteOnRollback(key);
		String checksum = store(key, file);
		MedicalDocument document = documents.saveAndFlush(new MedicalDocument(caller.clinicId(), record.getId(),
				consultation, fileName, type.contentType(), file.getSize(), checksum, key, caller.userId()));
		audit.record(caller.clinicId(), caller.userId(), AuditActions.DOCUMENT_UPLOADED, AuditActions.ENTITY_DOCUMENT,
				document.getId(), references(patientId, consultation));
		return toResponse(caller, document, patientId, author(caller.userId()));
	}

	/** Opens the file for viewing or download. Every access is audited. */
	@Transactional
	public DocumentContent content(AuthenticatedUser caller, UUID documentId, boolean download) {
		MedicalDocument document = requireDocument(caller, documentId);
		Patient patient = patientOf(caller, document);
		Map<String, Object> metadata = new HashMap<>(references(patient.getId(), document.getConsultationId()));
		metadata.put("download", download);
		audit.record(caller.clinicId(), caller.userId(), AuditActions.DOCUMENT_VIEWED, AuditActions.ENTITY_DOCUMENT,
				document.getId(), metadata);
		try {
			return new DocumentContent(document.getFileName(), document.getContentType(), document.getSizeBytes(),
					storage.open(document.getStorageKey()));
		}
		catch (IOException missing) {
			log.error("Stored file unreadable for document {}", document.getId());
			throw new UncheckedIOException(missing);
		}
	}

	/** Hides the document from every view; the file is kept (D-030). The uploader or the owner may archive. */
	@Transactional
	public void archive(AuthenticatedUser caller, UUID documentId) {
		MedicalDocument document = requireDocument(caller, documentId);
		Patient patient = patientOf(caller, document);
		PatientService.requireActive(patient);
		if (!canArchive(caller, document)) {
			throw new ForbiddenOperationException("FORBIDDEN", "Only the uploader or the owner can archive");
		}
		document.archive(Instant.now(clock), caller.userId());
		documents.flush();
		audit.record(caller.clinicId(), caller.userId(), AuditActions.DOCUMENT_ARCHIVED, AuditActions.ENTITY_DOCUMENT,
				document.getId(), references(patient.getId(), document.getConsultationId()));
	}

	private MedicalDocument requireDocument(AuthenticatedUser caller, UUID documentId) {
		return documents.findByIdAndClinicId(documentId, caller.clinicId())
			.filter(document -> !document.isArchived())
			.orElseThrow(() -> NotFoundException.of("DOCUMENT"));
	}

	/** The document's patient, if the caller has clinical access to it; otherwise the document is hidden. */
	private Patient patientOf(AuthenticatedUser caller, MedicalDocument document) {
		try {
			return recordAccess.patientOf(caller, document.getMedicalRecordId());
		}
		catch (NotFoundException hidden) {
			throw NotFoundException.of("DOCUMENT");
		}
	}

	private UUID requireConsultation(AuthenticatedUser caller, MedicalRecord record, UUID consultationId) {
		return consultations.findByIdAndClinicId(consultationId, caller.clinicId())
			.filter(consultation -> consultation.getMedicalRecordId().equals(record.getId()))
			.orElseThrow(() -> ValidationException.of("consultationId", "INVALID"))
			.getId();
	}

	private static byte[] head(MultipartFile file) {
		try (InputStream in = file.getInputStream()) {
			return in.readNBytes(DocumentType.SIGNATURE_LENGTH);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Stores the bytes and returns their SHA-256. */
	private String store(String key, MultipartFile file) {
		MessageDigest digest = sha256();
		try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
			storage.store(key, in);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	/** A failed transaction must not leave an orphaned file behind. */
	private void deleteOnRollback(String key) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					try {
						storage.delete(key);
					}
					catch (IOException e) {
						log.warn("Could not remove the file of a failed upload, key={}", key);
					}
				}
			}
		});
	}

	private boolean canArchive(AuthenticatedUser caller, MedicalDocument document) {
		return caller.hasRole(Role.OWNER) || document.getUploadedBy().equals(caller.userId());
	}

	private DocumentResponse toResponse(AuthenticatedUser caller, MedicalDocument document, UUID patientId) {
		return toResponse(caller, document, patientId, author(document.getUploadedBy()));
	}

	private DocumentResponse toResponse(AuthenticatedUser caller, MedicalDocument document, UUID patientId,
			AuthorRef uploader) {
		return new DocumentResponse(document.getId(), patientId, document.getConsultationId(),
				document.getFileName(), document.getContentType(), document.getSizeBytes(), uploader,
				document.getCreatedAt(), canArchive(caller, document));
	}

	private AuthorRef author(UUID userId) {
		return authors(List.of(userId)).get(userId);
	}

	private Map<UUID, AuthorRef> authors(Collection<UUID> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		return users.findAllById(userIds)
			.stream()
			.collect(Collectors.toMap(AppUser::getId,
					user -> new AuthorRef(user.getId(), user.getFirstName(), user.getLastName()),
					(first, second) -> first, HashMap::new));
	}

	private static Map<String, Object> references(UUID patientId, UUID consultationId) {
		return consultationId == null ? Map.of("patientId", patientId.toString())
				: Map.of("patientId", patientId.toString(), "consultationId", consultationId.toString());
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** An opened document file; the caller closes {@code content}. */
	public record DocumentContent(String fileName, String contentType, long sizeBytes, InputStream content) {
	}

}
