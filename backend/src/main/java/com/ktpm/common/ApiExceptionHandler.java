package com.ktpm.common;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  public record ErrorBody(
      Instant timestamp, int status, String error, String message, String path) {}

  public static ErrorBody body(HttpStatus s, String m, String path) {
    return new ErrorBody(Instant.now(), s.value(), s.name(), m, path);
  }

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ErrorBody> business(BusinessException e, HttpServletRequest r) {
    if (r.getRequestURI().endsWith("/bids")) {
      log.warn("Bid operation rejected at {}: {}", r.getRequestURI(), e.getMessage());
    }
    return ResponseEntity.status(e.status.value())
        .body(body(HttpStatus.valueOf(e.status.value()), e.getMessage(), r.getRequestURI()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(x -> x.getField() + ": " + x.getDefaultMessage())
            .sorted()
            .reduce((a, b) -> a + "; " + b)
            .orElse("Invalid request");
    return ResponseEntity.badRequest()
        .body(body(HttpStatus.BAD_REQUEST, message, r.getRequestURI()));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ErrorBody> malformed(Exception e, HttpServletRequest r) {
    return ResponseEntity.badRequest()
        .body(body(HttpStatus.BAD_REQUEST, "Invalid request format", r.getRequestURI()));
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    PessimisticLockingFailureException.class
  })
  ResponseEntity<ErrorBody> database(Exception e, HttpServletRequest r) {
    log.warn("Database conflict at {}", r.getRequestURI());
    return ResponseEntity.status(409)
        .body(
            body(
                HttpStatus.CONFLICT,
                "Conflicting data or concurrent operation; retry with fresh state",
                r.getRequestURI()));
  }

  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  ResponseEntity<ErrorBody> forbidden(Exception e, HttpServletRequest r) {
    return ResponseEntity.status(403)
        .body(body(HttpStatus.FORBIDDEN, "Access denied", r.getRequestURI()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorBody> unexpected(Exception e, HttpServletRequest r) {
    if (e instanceof org.springframework.web.ErrorResponse frameworkError) {
      HttpStatus status = HttpStatus.resolve(frameworkError.getStatusCode().value());
      if (status != null && status.is4xxClientError()) {
        return ResponseEntity.status(status)
            .body(body(status, frameworkError.getBody().getDetail(), r.getRequestURI()));
      }
    }
    log.error("Unexpected error at {}", r.getRequestURI(), e);
    return ResponseEntity.internalServerError()
        .body(body(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", r.getRequestURI()));
  }
}
