package com.iatros.backend.document;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.iatros.backend.document.DocumentService.DocumentContent;
import com.iatros.backend.document.dto.DocumentResponse;
import com.iatros.backend.security.AuthenticatedUser;
import com.iatros.backend.shared.web.PageRequests;
import com.iatros.backend.shared.web.PageResponse;
import com.iatros.backend.shared.web.SortWhitelist;

/** Documents API (api.md §21). Clinical access and archiving rights are enforced in {@link DocumentService}. */
@RestController
@RequestMapping("/api/v1")
class DocumentController {

	static final SortWhitelist SORTS = SortWhitelist.of(Sort.by(Sort.Direction.DESC, "createdAt"),
			Map.of("uploadedAt", "createdAt", "fileName", "fileName"));

	private final DocumentService service;

	DocumentController(DocumentService service) {
		this.service = service;
	}

	@GetMapping("/patients/{patientId}/documents")
	PageResponse<DocumentResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID patientId,
			@RequestParam(required = false) UUID consultationId, @RequestParam(required = false) String search,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) List<String> sort) {
		return service.list(user, patientId, consultationId, search, PageRequests.of(page, size, sort, SORTS));
	}

	@PostMapping(path = "/patients/{patientId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	DocumentResponse upload(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID patientId,
			@RequestPart(name = "file", required = false) MultipartFile file,
			@RequestParam(required = false) UUID consultationId) {
		return service.upload(user, patientId, consultationId, file);
	}

	@GetMapping("/documents/{documentId}")
	DocumentResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID documentId) {
		return service.get(user, documentId);
	}

	/**
	 * The file itself, shown in the browser or, with {@code download=true}, saved. The content type is the one
	 * verified at upload, and responses are never cached (Spring Security's default headers).
	 */
	@GetMapping("/documents/{documentId}/content")
	ResponseEntity<InputStreamResource> content(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID documentId, @RequestParam(defaultValue = "false") boolean download) {
		DocumentContent file = service.content(user, documentId, download);
		ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
			.filename(file.fileName(), StandardCharsets.UTF_8)
			.build();
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(file.contentType()))
			.contentLength(file.sizeBytes())
			.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
			.body(new InputStreamResource(file.content()));
	}

	@DeleteMapping("/documents/{documentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void archive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID documentId) {
		service.archive(user, documentId);
	}

}
