package app.calendar.domain;

/** Ошибка предметной области. Код совпадает с кодом ApiError из контракта. */
public class CalendarException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public enum Kind {
    VALIDATION_ERROR,
    EVENT_TYPE_EXISTS,
    EVENT_TYPE_NOT_FOUND,
    SLOT_UNAVAILABLE,
    SLOT_TAKEN
  }

  private final Kind kind;

  public CalendarException(Kind kind, String message) {
    super(message);
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }
}
