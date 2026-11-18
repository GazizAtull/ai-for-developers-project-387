package app.calendar.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

/** Правила расписания владельца. Зафиксированы в SPEC.md. */
public record ScheduleConfig(
    ZoneId zone,
    LocalTime open,
    LocalTime close,
    Set<DayOfWeek> workDays,
    int stepMinutes,
    int windowDays) {

  public static ScheduleConfig defaults() {
    return new ScheduleConfig(
        ZoneId.of("Asia/Almaty"),
        LocalTime.of(9, 0),
        LocalTime.of(18, 0),
        Set.of(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY),
        30,
        14);
  }
}
