package app.calendar.domain;

import java.time.Instant;
import java.util.UUID;

public record Booking(
    UUID id,
    String eventTypeId,
    String eventTypeTitle,
    Instant start,
    Instant end,
    String guestName,
    String guestEmail,
    String topic) {}
