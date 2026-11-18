package app.calendar.domain;

import java.time.Instant;

public record Slot(Instant start, Instant end) {}
