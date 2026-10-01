package com.budgettracker.api;

import com.budgettracker.domain.error.ConflictException;
import com.budgettracker.domain.error.ForbiddenException;
import com.budgettracker.domain.error.InvalidInviteException;
import com.budgettracker.domain.error.NoHouseholdException;
import com.budgettracker.domain.error.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain errors to RFC 7807 problem responses. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage(), "NOT_FOUND");
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail forbidden(ForbiddenException e) {
        return problem(HttpStatus.FORBIDDEN, e.getMessage(), "FORBIDDEN");
    }

    @ExceptionHandler(NoHouseholdException.class)
    ProblemDetail noHousehold(NoHouseholdException e) {
        return problem(HttpStatus.FORBIDDEN, e.getMessage(), "NO_HOUSEHOLD");
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage(), "CONFLICT");
    }

    @ExceptionHandler(InvalidInviteException.class)
    ProblemDetail invalidInvite(InvalidInviteException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_INVITE");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        return problem(HttpStatus.BAD_REQUEST, "The request body is invalid.", "VALIDATION");
    }

    private static ProblemDetail problem(HttpStatus status, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
