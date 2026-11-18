package app.calendar.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.calendar.domain.CalendarException.Kind;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CalendarServiceTest {

  private static final ZoneId ALMATY = ZoneId.of("Asia/Almaty");
  // Среда, 18 ноября 2026, 10:00 по Алматы
  private static final Instant NOW = Instant.parse("2026-11-18T05:00:00Z");

  private CalendarService service;
  private EventType short30;
  private EventType long60;

  @BeforeEach
  void setUp() {
    service =
        new CalendarService(Clock.fixed(NOW, ZoneOffset.UTC), ScheduleConfig.defaults());
    short30 = service.createEventType("intro", "Знакомство", "", 30);
    long60 = service.createEventType("consult", "Консультация", "Подробный разбор", 60);
  }

  private static Instant at(String date, String time) {
    return LocalDate.parse(date).atTime(java.time.LocalTime.parse(time)).atZone(ALMATY).toInstant();
  }

  @Test
  void windowIsFourteenDaysStartingToday() {
    Availability a = service.availability(short30.id());
    assertEquals("Asia/Almaty", a.timezone());
    assertEquals(14, a.days().size());
    assertEquals(LocalDate.parse("2026-11-18"), a.days().get(0).date());
    assertEquals(LocalDate.parse("2026-12-01"), a.days().get(13).date());
  }

  @Test
  void todayHidesPastSlots() {
    List<Slot> today = service.availability(short30.id()).days().get(0).slots();
    assertEquals(at("2026-11-18", "10:30"), today.get(0).start());
  }

  @Test
  void weekendsAreEmpty() {
    Availability a = service.availability(short30.id());
    assertTrue(a.days().get(3).slots().isEmpty()); // суббота 21.11
    assertTrue(a.days().get(4).slots().isEmpty()); // воскресенье 22.11
  }

  @Test
  void slotLengthMatchesEventTypeAndFitsWorkingHours() {
    List<Slot> day = service.availability(long60.id()).days().get(1).slots(); // чт 19.11
    assertEquals(at("2026-11-19", "09:00"), day.get(0).start());
    assertEquals(at("2026-11-19", "10:00"), day.get(0).end());
    assertEquals(at("2026-11-19", "17:00"), day.get(day.size() - 1).start());
    assertEquals(at("2026-11-19", "18:00"), day.get(day.size() - 1).end());
  }

  @Test
  void bookedSlotDisappearsAndPartiallyOverlappingSlotsOfOtherTypesToo() {
    service.book(long60.id(), at("2026-11-19", "10:00"), "Аня", "anya@example.com");
    List<Slot> thirty = service.availability(short30.id()).days().get(1).slots();
    List<Instant> starts = thirty.stream().map(Slot::start).toList();
    assertFalse(starts.contains(at("2026-11-19", "10:00")));
    assertFalse(starts.contains(at("2026-11-19", "10:30")));
    assertTrue(starts.contains(at("2026-11-19", "09:30"))); // кончается ровно в 10:00
    assertTrue(starts.contains(at("2026-11-19", "11:00"))); // начинается ровно в 11:00
  }

  @Test
  void overlapByAnotherTypeIsRejected() {
    service.book(long60.id(), at("2026-11-19", "10:00"), "Аня", "anya@example.com");
    CalendarException e =
        assertThrows(
            CalendarException.class,
            () -> service.book(short30.id(), at("2026-11-19", "10:30"), "Боб", "bob@example.com"));
    assertEquals(Kind.SLOT_TAKEN, e.kind());
    // 60-минутная с 09:30 пересекается частично с бронью 10:00-11:00
    e =
        assertThrows(
            CalendarException.class,
            () -> service.book(long60.id(), at("2026-11-19", "09:30"), "Боб", "bob@example.com"));
    assertEquals(Kind.SLOT_TAKEN, e.kind());
  }

  @Test
  void topicIsOptionalTrimmedAndKeepsInnerLineBreaks() {
    Booking withTopic =
        service.book(
            short30.id(),
            at("2026-11-19", "10:00"),
            "Аня",
            "anya@example.com",
            "  Обсудить проект\nи сроки  ");
    assertEquals("Обсудить проект\nи сроки", withTopic.topic());

    Booking withoutTopic =
        service.book(
            short30.id(), at("2026-11-19", "10:30"), "Боб", "bob@example.com", null);
    assertNull(withoutTopic.topic());

    Booking blankTopic =
        service.book(
            short30.id(), at("2026-11-19", "11:00"), "Вика", "vika@example.com", "   \n  ");
    assertNull(blankTopic.topic());
  }

  @Test
  void topicUpTo300IsAcceptedAndLongerIsRejected() {
    String max = "я".repeat(300);
    Booking booking =
        service.book(
            short30.id(), at("2026-11-19", "10:00"), "Аня", "anya@example.com", max);
    assertEquals(300, booking.topic().length());

    CalendarException e =
        assertThrows(
            CalendarException.class,
            () ->
                service.book(
                    short30.id(),
                    at("2026-11-19", "10:30"),
                    "Боб",
                    "bob@example.com",
                    "я".repeat(301)));
    assertEquals(Kind.VALIDATION_ERROR, e.kind());
  }

  @Test
  void topicLengthCountsCodePointsNotUtf16Units() {
    String max = "😀".repeat(300);
    Booking booking =
        service.book(
            short30.id(), at("2026-11-19", "10:00"), "Аня", "anya@example.com", max);
    assertEquals(300, booking.topic().codePointCount(0, booking.topic().length()));

    CalendarException e =
        assertThrows(
            CalendarException.class,
            () ->
                service.book(
                    short30.id(),
                    at("2026-11-19", "10:30"),
                    "Боб",
                    "bob@example.com",
                    "😀".repeat(301)));
    assertEquals(Kind.VALIDATION_ERROR, e.kind());
  }

  @Test
  void conflictWithTopicStillReturnsSlotTaken() {
    service.book(
        short30.id(), at("2026-11-19", "10:00"), "Аня", "anya@example.com", "Первая тема");
    CalendarException e =
        assertThrows(
            CalendarException.class,
            () ->
                service.book(
                    short30.id(),
                    at("2026-11-19", "10:00"),
                    "Боб",
                    "bob@example.com",
                    "Вторая тема"));
    assertEquals(Kind.SLOT_TAKEN, e.kind());
  }

  @Test
  void backToBackBookingsAreAllowed() {
    service.book(short30.id(), at("2026-11-19", "10:00"), "Аня", "anya@example.com");
    service.book(short30.id(), at("2026-11-19", "10:30"), "Боб", "bob@example.com");
    service.book(long60.id(), at("2026-11-19", "09:00"), "Вика", "vika@example.com");
    assertEquals(3, service.upcomingBookings().size());
  }

  @Test
  void rejectsOffGridPastWeekendAndOverflowingStarts() {
    assertUnavailable(short30, at("2026-11-19", "10:15")); // не по сетке
    assertUnavailable(short30, at("2026-11-18", "09:30")); // уже прошло
    assertUnavailable(short30, at("2026-11-21", "10:00")); // суббота
    assertUnavailable(short30, at("2026-11-19", "18:00")); // после закрытия
    assertUnavailable(long60, at("2026-11-19", "17:30")); // 17:30 + 60 мин выходит за 18:00
  }

  private void assertUnavailable(EventType type, Instant start) {
    CalendarException e =
        assertThrows(
            CalendarException.class,
            () -> service.book(type.id(), start, "Аня", "anya@example.com"));
    assertEquals(Kind.SLOT_UNAVAILABLE, e.kind());
  }

  @Test
  void rejectsDayBeyondWindow() {
    CalendarException e =
        assertThrows(
            CalendarException.class,
            () -> service.book(short30.id(), at("2026-12-02", "10:00"), "Аня", "anya@example.com"));
    assertEquals(Kind.SLOT_UNAVAILABLE, e.kind());
  }

  @Test
  void validatesInput() {
    assertEquals(
        Kind.VALIDATION_ERROR,
        assertThrows(CalendarException.class, () -> service.createEventType("blank", " ", "", 30)).kind());
    assertEquals(
        Kind.VALIDATION_ERROR,
        assertThrows(CalendarException.class, () -> service.createEventType("odd", "X", "", 7)).kind());
    assertEquals(
        Kind.VALIDATION_ERROR,
        assertThrows(
                CalendarException.class,
                () -> service.book(short30.id(), at("2026-11-19", "10:00"), "Аня", "not-an-email"))
            .kind());
    assertEquals(
        Kind.EVENT_TYPE_NOT_FOUND,
        assertThrows(
                CalendarException.class,
                () ->
                    service.book(
                        "missing",
                        at("2026-11-19", "10:00"),
                        "Аня",
                        "anya@example.com"))
            .kind());
  }

  @Test
  void eventTypeIdIsOwnersSlugAndUnique() {
    assertEquals("intro", short30.id());
    assertEquals(
        Kind.EVENT_TYPE_EXISTS,
        assertThrows(CalendarException.class, () -> service.createEventType("intro", "X", "", 30))
            .kind());
    assertEquals(
        Kind.VALIDATION_ERROR,
        assertThrows(
                CalendarException.class, () -> service.createEventType("Bad Id", "X", "", 30))
            .kind());
  }

  @Test
  void upcomingBookingsAreSortedAndHideFinishedOnes() {
    service.book(short30.id(), at("2026-11-20", "10:00"), "Поздняя", "b@example.com");
    service.book(short30.id(), at("2026-11-19", "10:00"), "Ранняя", "a@example.com");
    List<Booking> list = service.upcomingBookings();
    assertEquals("Ранняя", list.get(0).guestName());
    assertEquals("Знакомство", list.get(0).eventTypeTitle());

    CalendarService later =
        new CalendarService(
            Clock.fixed(Instant.parse("2026-11-19T05:20:00Z"), ZoneOffset.UTC),
            ScheduleConfig.defaults());
    EventType t = later.createEventType("t", "T", "", 30);
    later.book(t.id(), at("2026-11-19", "10:30"), "Аня", "anya@example.com");
    assertEquals(1, later.upcomingBookings().size());
  }

  @Test
  void concurrentBookingsOfOverlappingTimeProduceExactlyOneSuccess() throws Exception {
    int threads = 40;
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger ok = new AtomicInteger();
    AtomicInteger taken = new AtomicInteger();
    List<Thread> list = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      // половина просит 10:00 на 30 минут, половина 10:00 на 60: все пересекаются
      EventType type = i % 2 == 0 ? short30 : long60;
      int n = i;
      Thread t =
          new Thread(
              () -> {
                try {
                  start.await();
                  service.book(type.id(), at("2026-11-19", "10:00"), "G" + n, "g" + n + "@e.com");
                  ok.incrementAndGet();
                } catch (CalendarException e) {
                  if (e.kind() == Kind.SLOT_TAKEN) {
                    taken.incrementAndGet();
                  }
                } catch (InterruptedException ignored) {
                  Thread.currentThread().interrupt();
                }
              });
      list.add(t);
      t.start();
    }
    start.countDown();
    for (Thread t : list) {
      t.join();
    }
    assertEquals(1, ok.get());
    assertEquals(threads - 1, taken.get());
    assertEquals(1, service.upcomingBookings().size());
  }
}
