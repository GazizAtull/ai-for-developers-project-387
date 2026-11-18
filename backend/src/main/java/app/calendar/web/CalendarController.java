package app.calendar.web;

import app.calendar.domain.Availability;
import app.calendar.domain.Booking;
import app.calendar.domain.CalendarException;
import app.calendar.domain.CalendarException.Kind;
import app.calendar.domain.CalendarService;
import app.calendar.domain.EventType;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Реализует контракт из contract/main.tsp (см. openapi.yaml). */
@RestController
@RequestMapping("/api")
public class CalendarController {

  private final CalendarService service;

  public CalendarController(CalendarService service) {
    this.service = service;
  }

  @GetMapping("/event-types")
  public List<EventType> listEventTypes() {
    return service.listEventTypes();
  }

  @PostMapping("/event-types")
  @ResponseStatus(HttpStatus.CREATED)
  public EventType createEventType(@RequestBody CreateEventTypeRequest body) {
    if (body.durationMinutes() == null) {
      throw new CalendarException(Kind.VALIDATION_ERROR, "Длительность обязательна");
    }
    return service.createEventType(body.id(), body.title(), body.description(), body.durationMinutes());
  }

  @GetMapping("/event-types/{id}/availability")
  public Availability availability(@PathVariable String id) {
    return service.availability(id);
  }

  @GetMapping("/bookings")
  public List<Booking> listBookings() {
    return service.upcomingBookings();
  }

  @PostMapping("/bookings")
  @ResponseStatus(HttpStatus.CREATED)
  public Booking createBooking(@RequestBody CreateBookingRequest body) {
    return service.book(
        body.eventTypeId(), body.start(), body.guestName(), body.guestEmail(), body.topic());
  }
}
