package com.visiondigitallab.visionone.common;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** RFC 9457 problem+json for every error the API returns. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String BASE = "https://visionone.visiondigitallab.com/problems/";

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail onNotFound(NotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create(BASE + "not-found"));
        problem.setTitle("Not found");
        return problem;
    }

    /**
     * A URL that maps to nothing.
     *
     * Without this the catch-all below turns a typo into a 500 and logs a stack trace, so an
     * unmapped path reads as a server fault in the logs and tells the caller nothing useful.
     * Spring raises this for any request the dispatcher cannot match once static resource
     * handling has had its turn.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail onNoHandler(NoResourceFoundException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No endpoint for this path");
        problem.setType(URI.create(BASE + "not-found"));
        problem.setTitle("Not found");
        return problem;
    }

    @ExceptionHandler(AccessDeniedInOrganizationException.class)
    public ProblemDetail onOrganizationAccessDenied(AccessDeniedInOrganizationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setType(URI.create(BASE + "organization-access-denied"));
        problem.setTitle("Access denied");
        return problem;
    }

    /**
     * Method security throws this, and without an explicit handler the catch-all below turns a
     * denial into a 500. The detail is deliberately generic: a caller learns that it was refused,
     * not which rule refused it.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail onAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
        problem.setType(URI.create(BASE + "access-denied"));
        problem.setTitle("Access denied");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidationFailure(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "One or more fields are invalid");
        problem.setType(URI.create(BASE + "validation-failed"));
        problem.setTitle("Validation failed");
        List<Violation> violations = new ArrayList<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> violations.add(new Violation(error.getField(), error.getDefaultMessage())));
        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail onConflict(ConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create(BASE + "conflict"));
        problem.setTitle("Conflict");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create(BASE + "bad-request"));
        problem.setTitle("Bad request");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setType(URI.create(BASE + "internal-error"));
        problem.setTitle("Internal error");
        return problem;
    }

    /** One invalid field. */
    public record Violation(String field, String message) {}
}
