package app.calendar.domain;

import app.calendar.domain.CalendarException.Kind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Вся логика календаря. Данные живут в памяти процесса, перезапуск их сбрасывает.
 *
 * <p>Проверка пересечения и запись брони выполняются под одним замком, поэтому два одновременных
 * запроса на пересекающееся время не могут оба получить успех.
 */
public class CalendarService {

  private static final Pattern TYPE_ID = Pattern.compile("^[a-z0-9-]{1,50}$");
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  private final Object lock = new Object();
  private final Clock clock;
  private final ScheduleConfig config;
  private final Map<String, EventType> eventTypes = new LinkedHashMap<>();
  private final List<Booking> bookings = new ArrayList<>();

  public CalendarService(Clock clock, ScheduleConfig config) {
    this.clock = clock;
    this.config = config;
  }

  public List<EventType> listEventTypes() {
    synchronized (lock) {
      return List.copyOf(eventTypes.values());
    }
  }

  public EventType createEventType(
      String id, String title, String description, int durationMinutes) {
    if (id == null || !TYPE_ID.matcher(id).matches()) {
      throw invalid("id: латиница в нижнем регистре, цифры и дефис, до 50 символов");
    }
    String cleanTitle = title == null ? "" : title.strip();
    String cleanDescription = description == null ? "" : description.strip();
    if (cleanTitle.isEmpty() || cleanTitle.length() > 100) {
      throw invalid("Название обязательно, не длиннее 100 символов");
    }
    if (cleanDescription.length() > 500) {
      throw invalid("Описание не длиннее 500 символов");
    }
    if (durationMinutes < 5 || durationMinutes > 240 || durationMinutes % 5 != 0) {
      throw invalid("Длительность от 5 до 240 минут, кратна 5");
    }
    EventType type = new EventType(id, cleanTitle, cleanDescription, durationMinutes);
    synchronized (lock) {
      if (eventTypes.containsKey(id)) {
        throw new CalendarException(Kind.EVENT_TYPE_EXISTS, "Тип встречи с таким id уже есть");
      }
      eventTypes.put(type.id(), type);
    }
    return type;
  }

  public Availability availability(String eventTypeId) {
    synchronized (lock) {
      EventType type = requireType(eventTypeId);
      Instant now = clock.instant();
      LocalDate today = now.atZone(config.zone()).toLocalDate();
      List<SlotDay> days = new ArrayList<>();
      for (int i = 0; i < config.windowDays(); i++) {
        LocalDate date = today.plusDays(i);
        List<Slot> free = new ArrayList<>();
        for (Slot candidate : candidates(date, type, now)) {
          if (!overlapsExisting(candidate.start(), candidate.end())) {
            free.add(candidate);
          }
        }
        days.add(new SlotDay(date, List.copyOf(free)));
      }
      return new Availability(config.zone().getId(), List.copyOf(days));
    }
  }

  public Booking book(String eventTypeId, Instant start, String guestName, String guestEmail) {
    return book(eventTypeId, start, guestName, guestEmail, null);
  }

  public Booking book(
      String eventTypeId, Instant start, String guestName, String guestEmail, String topic) {
    String name = guestName == null ? "" : guestName.strip();
    String email = guestEmail == null ? "" : guestEmail.strip();
    String cleanTopic = topic == null ? "" : topic.strip();
    if (start == null) {
      throw invalid("Начало слота обязательно");
    }
    if (name.isEmpty() || name.length() > 100) {
      throw invalid("Имя обязательно, не длиннее 100 символов");
    }
    if (!EMAIL.matcher(email).matches()) {
      throw invalid("Некорректный email");
    }
    if (cleanTopic.codePointCount(0, cleanTopic.length()) > 300) {
      throw invalid("Тема разговора не длиннее 300 символов");
    }
    synchronized (lock) {
      EventType type = requireType(eventTypeId);
      Instant now = clock.instant();
      LocalDate date = start.atZone(config.zone()).toLocalDate();
      LocalDate today = now.atZone(config.zone()).toLocalDate();
      if (date.isBefore(today) || !date.isBefore(today.plusDays(config.windowDays()))) {
        throw new CalendarException(Kind.SLOT_UNAVAILABLE, "Время вне окна записи");
      }
      Slot slot =
          candidates(date, type, now).stream()
              .filter(c -> c.start().equals(start))
              .findFirst()
              .orElseThrow(
                  () ->
                      new CalendarException(
                          Kind.SLOT_UNAVAILABLE, "Такого слота нет в расписании владельца"));
      if (overlapsExisting(slot.start(), slot.end())) {
        throw new CalendarException(Kind.SLOT_TAKEN, "Это время уже занято");
      }
      Booking booking =
          new Booking(
              UUID.randomUUID(),
              type.id(),
              type.title(),
              slot.start(),
              slot.end(),
              name,
              email,
              cleanTopic);
      bookings.add(booking);
      return booking;
    }
  }

  /** Предстоящие встречи всех типов, ближайшие первыми. Завершённые не показываются. */
  public List<Booking> upcomingBookings() {
    synchronized (lock) {
      Instant now = clock.instant();
      return bookings.stream()
          .filter(b -> b.end().isAfter(now))
          .sorted(Comparator.comparing(Booking::start))
          .toList();
    }
  }

  /** Допустимые по расписанию старты на дату: сетка шага, часы работы, не в прошлом. */
  private List<Slot> candidates(LocalDate date, EventType type, Instant now) {
    List<Slot> result = new ArrayList<>();
    if (!config.workDays().contains(date.getDayOfWeek())) {
      return result;
    }
    Duration length = Duration.ofMinutes(type.durationMinutes());
    LocalTime time = config.open();
    while (!time.plus(length).isAfter(config.close()) && !time.plus(length).isBefore(time)) {
      ZonedDateTime startZoned = date.atTime(time).atZone(config.zone());
      Instant start = startZoned.toInstant();
      if (start.isAfter(now)) {
        result.add(new Slot(start, start.plus(length)));
      }
      LocalTime next = time.plusMinutes(config.stepMinutes());
      if (!next.isAfter(time)) {
        break;
      }
      time = next;
    }
    return result;
  }

  /** Интервалы полуоткрытые: встреча может начаться ровно в момент окончания другой. */
  private boolean overlapsExisting(Instant start, Instant end) {
    for (Booking b : bookings) {
      if (start.isBefore(b.end()) && b.start().isBefore(end)) {
        return true;
      }
    }
    return false;
  }

  private EventType requireType(String id) {
    EventType type = id == null ? null : eventTypes.get(id);
    if (type == null) {
      throw new CalendarException(Kind.EVENT_TYPE_NOT_FOUND, "Тип встречи не найден");
    }
    return type;
  }

  private static CalendarException invalid(String message) {
    return new CalendarException(Kind.VALIDATION_ERROR, message);
  }
}
