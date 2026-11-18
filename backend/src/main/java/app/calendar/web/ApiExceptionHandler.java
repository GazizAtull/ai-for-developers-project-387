package app.calendar.web;

import app.calendar.domain.CalendarException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(CalendarException.class)
  ResponseEntity<ApiError> handleDomain(CalendarException e) {
    HttpStatus status =
        switch (e.kind()) {
          case VALIDATION_ERROR, SLOT_UNAVAILABLE -> HttpStatus.BAD_REQUEST;
          case EVENT_TYPE_NOT_FOUND -> HttpStatus.NOT_FOUND;
          case EVENT_TYPE_EXISTS, SLOT_TAKEN -> HttpStatus.CONFLICT;
        };
    return ResponseEntity.status(status).body(new ApiError(e.kind().name(), e.getMessage()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
    return ResponseEntity.badRequest()
        .body(new ApiError("VALIDATION_ERROR", "Тело запроса не удалось разобрать"));
  }
}
