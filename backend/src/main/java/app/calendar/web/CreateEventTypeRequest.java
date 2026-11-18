package app.calendar.web;

public record CreateEventTypeRequest(
    String id, String title, String description, Integer durationMinutes) {}
