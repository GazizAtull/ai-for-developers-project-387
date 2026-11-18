package app.calendar.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/** Бронь. Темы нет — поля topic в JSON нет (контракт: topic?: string). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Booking(
    UUID id,
    String eventTypeId,
    String eventTypeTitle,
    Instant start,
    Instant end,
    String guestName,
    String guestEmail,
    String topic) {}
