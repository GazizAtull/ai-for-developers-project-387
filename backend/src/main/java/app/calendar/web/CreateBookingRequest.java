package app.calendar.web;

import java.time.Instant;

public record CreateBookingRequest(
    String eventTypeId, Instant start, String guestName, String guestEmail, String topic) {}
