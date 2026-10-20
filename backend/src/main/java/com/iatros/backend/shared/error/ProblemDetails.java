package com.iatros.backend.shared.error;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Builds RFC 9457 problem responses with the IATROS extensions {@code code}, {@code fieldErrors}, {@code timestamp}. */
@Component
public class ProblemDetails {

	public static final String CODE = "code";
	public static final String FIELD_ERRORS = "fieldErrors";
	public static final String TIMESTAMP = "timestamp";

	private final Clock clock;

	public ProblemDetails(Clock clock) {
		this.clock = clock;
	}

	public ProblemDetail create(HttpStatusCode status, String code) {
		ProblemDetail problem = ProblemDetail.forStatus(status);
		decorate(problem, code);
		return problem;
	}

	public ProblemDetail create(HttpStatusCode status, String code, List<FieldErrorResponse> fieldErrors) {
		ProblemDetail problem = create(status, code);
		problem.setProperty(FIELD_ERRORS, fieldErrors);
		return problem;
	}

	/** Adds the IATROS extensions and removes the developer detail, which may echo internal or user input. */
	void decorate(ProblemDetail problem, String code) {
		problem.setDetail(null);
		problem.setProperty(CODE, code);
		problem.setProperty(TIMESTAMP, Instant.now(clock));
	}

}
