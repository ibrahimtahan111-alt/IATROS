package com.iatros.backend.shared.error;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converts every error into an RFC 9457 problem response with a machine-readable {@code code} (decisions.md D-014).
 * Responses never contain stack traces, exception messages, SQL, or echoed input values.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	static final String VALIDATION_ERROR = "VALIDATION_ERROR";

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final ProblemDetails problems;
	private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

	public GlobalExceptionHandler(ProblemDetails problems) {
		this.problems = problems;
	}

	@ExceptionHandler(ValidationException.class)
	ResponseEntity<Object> handleValidation(ValidationException ex) {
		return validationFailed(ex.getFieldErrors());
	}

	@ExceptionHandler(TooManyRequestsException.class)
	ResponseEntity<ProblemDetail> handleTooManyRequests(TooManyRequestsException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
			.header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()))
			.body(problems.create(HttpStatus.TOO_MANY_REQUESTS, ex.getCode()));
	}

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
		HttpStatus status = switch (ex) {
			case AuthenticationFailedException ignored -> HttpStatus.UNAUTHORIZED;
			case NotFoundException ignored -> HttpStatus.NOT_FOUND;
			case ConflictException ignored -> HttpStatus.CONFLICT;
			case BusinessRuleException ignored -> HttpStatus.UNPROCESSABLE_CONTENT;
			case ForbiddenOperationException ignored -> HttpStatus.FORBIDDEN;
			case BadRequestException ignored -> HttpStatus.BAD_REQUEST;
			default -> HttpStatus.BAD_REQUEST;
		};
		return respond(status, ex.getCode());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		String constraint = DatabaseConstraintCodes.constraintName(ex).orElse(null);
		return DatabaseConstraintCodes.conflictCodeFor(constraint)
			.map(code -> respond(HttpStatus.CONFLICT, code))
			.orElseGet(() -> {
				// The exception message can contain row values (e.g. emails); log the constraint name only.
				log.warn("Unmapped data integrity violation, constraint={}", constraint);
				return respond(HttpStatus.CONFLICT, "DATA_CONFLICT");
			});
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
		return respond(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
		return respond(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
	}

	@ExceptionHandler(CsrfException.class)
	ResponseEntity<ProblemDetail> handleCsrf(CsrfException ex) {
		return respond(HttpStatus.FORBIDDEN, "CSRF_TOKEN_INVALID");
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || trustResolver.isAnonymous(authentication)) {
			return respond(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
		}
		return respond(HttpStatus.FORBIDDEN, "FORBIDDEN");
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldErrorResponse> fieldErrors = new ArrayList<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			fieldErrors.add(new FieldErrorResponse(error.getField(), ValidationCodes.forConstraint(error.getCode())));
		}
		ex.getBindingResult().getGlobalErrors()
			.forEach(error -> fieldErrors.add(new FieldErrorResponse(null, ValidationCodes.forConstraint(error.getCode()))));
		return validationFailed(fieldErrors);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldErrorResponse> fieldErrors = new ArrayList<>();
		ex.getParameterValidationResults().forEach(result -> {
			String field = result.getMethodParameter().getParameterName();
			for (MessageSourceResolvable error : result.getResolvableErrors()) {
				fieldErrors.add(new FieldErrorResponse(field, ValidationCodes.forConstraint(constraintName(error))));
			}
		});
		return validationFailed(fieldErrors);
	}

	@Override
	protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
			.body(problems.create(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE"));
	}

	/** Ensures framework-generated problems (malformed JSON, 405, 415, ...) also carry a code and no detail. */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem
				&& (problem.getProperties() == null || !problem.getProperties().containsKey(ProblemDetails.CODE))) {
			problems.decorate(problem, codeForFrameworkStatus(statusCode));
		}
		return response;
	}

	private ResponseEntity<Object> validationFailed(List<FieldErrorResponse> fieldErrors) {
		ProblemDetail problem = problems.create(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, fieldErrors);
		problem.setTitle("Validation failed");
		return ResponseEntity.badRequest().body(problem);
	}

	private ResponseEntity<ProblemDetail> respond(HttpStatus status, String code) {
		return ResponseEntity.status(status).body(problems.create(status, code));
	}

	/** The last resolvable code is the bare constraint name, e.g. "Min" in ["Min.page", "Min"]. */
	private static String constraintName(MessageSourceResolvable error) {
		String[] codes = error.getCodes();
		return codes == null || codes.length == 0 ? null : codes[codes.length - 1];
	}

	private static String codeForFrameworkStatus(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> "MALFORMED_REQUEST";
			case 401 -> "UNAUTHENTICATED";
			case 403 -> "FORBIDDEN";
			case 404 -> "NOT_FOUND";
			case 405 -> "METHOD_NOT_ALLOWED";
			case 406 -> "NOT_ACCEPTABLE";
			case 413 -> "PAYLOAD_TOO_LARGE";
			case 415 -> "UNSUPPORTED_MEDIA_TYPE";
			default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_ERROR";
		};
	}

}
