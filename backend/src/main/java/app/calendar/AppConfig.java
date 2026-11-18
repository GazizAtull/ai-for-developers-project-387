package app.calendar;

import app.calendar.domain.CalendarService;
import app.calendar.domain.ScheduleConfig;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  ScheduleConfig scheduleConfig() {
    return ScheduleConfig.defaults();
  }

  @Bean
  CalendarService calendarService(Clock clock, ScheduleConfig config) {
    return new CalendarService(clock, config);
  }
}
