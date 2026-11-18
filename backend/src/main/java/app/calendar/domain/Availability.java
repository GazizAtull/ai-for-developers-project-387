package app.calendar.domain;

import java.util.List;

public record Availability(String timezone, List<SlotDay> days) {}
