package dev.zen.zerosum.ledger.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(LedgerException.class)
    ProblemDetail handleLedger(LedgerException ex, HttpServletRequest request) {
        return switch (ex) {
            case LedgerException.AccountNotFound e ->
                    // A GET for a missing account is a 404. A POST that merely references one in
                    // its body is a valid request about a missing thing, so 422.
                    "GET".equals(request.getMethod())
                            ? problem(404, "account-not-found", "Account not found", e)
                            : problem(422, "unknown-account", "Referenced account not found", e);
            case LedgerException.UnbalancedTransfer e ->
                    problem(422, "unbalanced-transfer", "Transfer is unbalanced", e);
            case LedgerException.CurrencyMismatch e ->
                    problem(422, "currency-mismatch", "Currency mismatch", e);
            case LedgerException.InvalidTransfer e ->
                    problem(422, "invalid-transfer", "Invalid transfer", e);
            case LedgerException.InvalidInput e ->
                    problem(422, "invalid-input", "Invalid input", e);
            case LedgerException.DuplicateAccount e ->
                    problem(409, "duplicate-account", "Account already exists", e);
            case LedgerException.IdempotencyConflict e ->
                    problem(409, "idempotency-key-reuse", "Idempotency key reused", e);
        };
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = ex.getBody();
        problem.setTitle("Validation failed");
        problem.setType(URI.create("urn:zerosum:problem:validation-failed"));
        List<Map<String, String>> errors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                e ->
                                        Map.of(
                                                "field", e.getField(),
                                                "message", String.valueOf(e.getDefaultMessage())))
                        .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal error");
        problem.setType(URI.create("urn:zerosum:problem:internal-error"));
        return problem;
    }

    private static ProblemDetail problem(int status, String slug, String title, Exception ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), ex.getMessage());
        problem.setTitle(title);
        problem.setType(URI.create("urn:zerosum:problem:" + slug));
        return problem;
    }
}
