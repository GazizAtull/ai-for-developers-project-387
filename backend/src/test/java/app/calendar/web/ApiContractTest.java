package app.calendar.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

/**
 * API-тесты прямыми HTTP-запросами к поднятому серверу. Проверяют коды и тела ответов из
 * contract/main.tsp (openapi.yaml).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApiContractTest {

  @Autowired TestRestTemplate http;
  @Autowired ObjectMapper json;

  private JsonNode body(ResponseEntity<String> r) throws Exception {
    return json.readTree(r.getBody());
  }

  private void assertNoTopic(JsonNode booking) {
    JsonNode topic = booking.get("topic");
    assertThat(topic == null || topic.isNull()).isTrue();
  }

  private ResponseEntity<String> createType(String id, String title, int minutes) {
    return http.postForEntity(
        "/api/event-types",
        Map.of("id", id, "title", title, "description", "", "durationMinutes", minutes),
        String.class);
  }

  private ResponseEntity<String> book(String typeId, String start, String name) {
    return book(typeId, start, name, null);
  }

  private ResponseEntity<String> book(String typeId, String start, String name, String topic) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("eventTypeId", typeId);
    body.put("start", start);
    body.put("guestName", name);
    body.put("guestEmail", "g@example.com");
    if (topic != null) {
      body.put("topic", topic);
    }
    return http.postForEntity("/api/bookings", body, String.class);
  }

  /** Первый день окна, где у типа свободен старт 10:00 по Алматы (05:00Z). */
  private String tenOClock(String typeId) throws Exception {
    JsonNode days =
        body(http.getForEntity("/api/event-types/" + typeId + "/availability", String.class))
            .get("days");
    for (JsonNode day : days) {
      for (JsonNode slot : day.get("slots")) {
        if (slot.get("start").asText().endsWith("T05:00:00Z")) {
          return slot.get("start").asText();
        }
      }
    }
    throw new AssertionError("в окне нет слота 10:00");
  }

  @Test
  void eventTypesCreateListAndErrors() throws Exception {
    ResponseEntity<String> created = createType("intro", "Знакомство", 30);
    assertThat(created.getStatusCode().value()).isEqualTo(201);
    assertThat(body(created).get("id").asText()).isEqualTo("intro");
    assertThat(body(created).get("durationMinutes").asInt()).isEqualTo(30);

    ResponseEntity<String> list = http.getForEntity("/api/event-types", String.class);
    assertThat(list.getStatusCode().value()).isEqualTo(200);
    assertThat(body(list).get(0).get("title").asText()).isEqualTo("Знакомство");

    ResponseEntity<String> dup = createType("intro", "Ещё", 30);
    assertThat(dup.getStatusCode().value()).isEqualTo(409);
    assertThat(body(dup).get("code").asText()).isEqualTo("EVENT_TYPE_EXISTS");

    for (ResponseEntity<String> bad :
        List.of(createType("Bad Id", "X", 30), createType("x", "", 30), createType("y", "Y", 7))) {
      assertThat(bad.getStatusCode().value()).isEqualTo(400);
      assertThat(body(bad).get("code").asText()).isEqualTo("VALIDATION_ERROR");
    }
  }

  @Test
  void availabilityCoversFourteenDaysInOwnerZone() throws Exception {
    createType("intro", "Знакомство", 30);
    ResponseEntity<String> r =
        http.getForEntity("/api/event-types/intro/availability", String.class);
    assertThat(r.getStatusCode().value()).isEqualTo(200);
    assertThat(body(r).get("timezone").asText()).isEqualTo("Asia/Almaty");
    assertThat(body(r).get("days")).hasSize(14);

    ResponseEntity<String> missing =
        http.getForEntity("/api/event-types/missing/availability", String.class);
    assertThat(missing.getStatusCode().value()).isEqualTo(404);
    assertThat(body(missing).get("code").asText()).isEqualTo("EVENT_TYPE_NOT_FOUND");
  }

  @Test
  void bookingConflictsAcrossTypesIncludingPartialOverlap() throws Exception {
    createType("consult", "Консультация", 60);
    createType("intro", "Знакомство", 30);
    String ten = tenOClock("consult");
    String tenThirty = ten.replace("T05:00:00Z", "T05:30:00Z");
    String eleven = ten.replace("T05:00:00Z", "T06:00:00Z");

    ResponseEntity<String> first = book("consult", ten, "Аня");
    assertThat(first.getStatusCode().value()).isEqualTo(201);
    assertThat(body(first).get("eventTypeTitle").asText()).isEqualTo("Консультация");
    assertThat(body(first).get("end").asText()).isEqualTo(eleven);

    // 30 минут в 10:30 частично накладываются на 60 минут в 10:00 другого типа
    ResponseEntity<String> overlap = book("intro", tenThirty, "Боб");
    assertThat(overlap.getStatusCode().value()).isEqualTo(409);
    assertThat(body(overlap).get("code").asText()).isEqualTo("SLOT_TAKEN");
    assertThat(body(overlap).get("message").asText()).isNotBlank();

    // занятое время не предлагается как свободное
    String introDays =
        body(http.getForEntity("/api/event-types/intro/availability", String.class))
            .get("days")
            .toString();
    assertThat(introDays).doesNotContain(tenThirty).doesNotContain("\"start\":\"" + ten + "\"");

    // примыкание разрешено
    assertThat(book("intro", eleven, "Вика").getStatusCode().value()).isEqualTo(201);

    ResponseEntity<String> list = http.getForEntity("/api/bookings", String.class);
    assertThat(list.getStatusCode().value()).isEqualTo(200);
    assertThat(body(list)).hasSize(2);
    assertThat(body(list).get(0).get("guestName").asText()).isEqualTo("Аня");
  }

  @Test
  void bookingWithTopicIsReturnedAndListed() throws Exception {
    createType("intro", "Знакомство", 30);
    String ten = tenOClock("intro");

    ResponseEntity<String> created = book("intro", ten, "Аня", "  Обсудить проект\nи сроки  ");
    assertThat(created.getStatusCode().value()).isEqualTo(201);
    assertThat(body(created).get("topic").asText()).isEqualTo("Обсудить проект\nи сроки");

    ResponseEntity<String> list = http.getForEntity("/api/bookings", String.class);
    assertThat(list.getStatusCode().value()).isEqualTo(200);
    assertThat(body(list).get(0).get("topic").asText()).isEqualTo("Обсудить проект\nи сроки");
  }

  @Test
  void bookingWithoutTopicWorksAsBefore() throws Exception {
    createType("intro", "Знакомство", 30);
    String ten = tenOClock("intro");

    ResponseEntity<String> created = book("intro", ten, "Аня");
    assertThat(created.getStatusCode().value()).isEqualTo(201);
    assertNoTopic(body(created));

    ResponseEntity<String> list = http.getForEntity("/api/bookings", String.class);
    assertThat(body(list)).hasSize(1);
    assertThat(body(list).get(0).get("guestName").asText()).isEqualTo("Аня");
    assertNoTopic(body(list).get(0));
  }

  @Test
  void bookingWithBlankTopicIsReturnedWithoutTopic() throws Exception {
    createType("intro", "Знакомство", 30);
    String ten = tenOClock("intro");

    ResponseEntity<String> created = book("intro", ten, "Аня", "   \n  ");
    assertThat(created.getStatusCode().value()).isEqualTo(201);
    assertNoTopic(body(created));

    ResponseEntity<String> list = http.getForEntity("/api/bookings", String.class);
    assertThat(body(list)).hasSize(1);
    assertNoTopic(body(list).get(0));
  }

  @Test
  void topicLongerThan300IsRejected() throws Exception {
    createType("intro", "Знакомство", 30);
    String ten = tenOClock("intro");

    ResponseEntity<String> tooLong = book("intro", ten, "Аня", "я".repeat(301));
    assertThat(tooLong.getStatusCode().value()).isEqualTo(400);
    assertThat(body(tooLong).get("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(body(tooLong).get("message").asText()).contains("300");

    assertThat(book("intro", ten, "Аня", "я".repeat(300)).getStatusCode().value()).isEqualTo(201);
  }

  @Test
  void conflictWithTopicReturnsSlotTaken() throws Exception {
    createType("intro", "Знакомство", 30);
    String ten = tenOClock("intro");

    assertThat(book("intro", ten, "Аня", "Первая тема").getStatusCode().value()).isEqualTo(201);

    ResponseEntity<String> second = book("intro", ten, "Боб", "Вторая тема");
    assertThat(second.getStatusCode().value()).isEqualTo(409);
    assertThat(body(second).get("code").asText()).isEqualTo("SLOT_TAKEN");
  }

  @Test
  void bookingDataErrors() throws Exception {
    createType("intro", "Знакомство", 30);
    String ten = tenOClock("intro");
    ResponseEntity<String> badEmail =
        http.postForEntity(
            "/api/bookings",
            Map.of("eventTypeId", "intro", "start", ten, "guestName", "А", "guestEmail", "nope"),
            String.class);
    assertThat(badEmail.getStatusCode().value()).isEqualTo(400);
    assertThat(body(badEmail).get("code").asText()).isEqualTo("VALIDATION_ERROR");

    ResponseEntity<String> offGrid = book("intro", ten.replace("T05:00:00Z", "T05:10:00Z"), "А");
    assertThat(offGrid.getStatusCode().value()).isEqualTo(400);
    assertThat(body(offGrid).get("code").asText()).isEqualTo("SLOT_UNAVAILABLE");

    ResponseEntity<String> missing = book("missing", ten, "А");
    assertThat(missing.getStatusCode().value()).isEqualTo(404);
    assertThat(body(missing).get("code").asText()).isEqualTo("EVENT_TYPE_NOT_FOUND");
  }

  @Test
  void concurrentOverlappingRequestsYieldOneSuccess() throws Exception {
    createType("consult", "Консультация", 60);
    createType("intro", "Знакомство", 30);
    String ten = tenOClock("consult");
    String tenThirty = ten.replace("T05:00:00Z", "T05:30:00Z");
    ExecutorService pool = Executors.newFixedThreadPool(8);
    List<Callable<HttpStatusCode>> tasks = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      boolean even = i % 2 == 0;
      String name = "g" + i;
      tasks.add(
          () ->
              (even ? book("consult", ten, name) : book("intro", tenThirty, name))
                  .getStatusCode());
    }
    int created = 0;
    int conflicts = 0;
    for (Future<HttpStatusCode> f : pool.invokeAll(tasks)) {
      int code = f.get().value();
      if (code == 201) {
        created++;
      } else if (code == 409) {
        conflicts++;
      }
    }
    pool.shutdown();
    assertThat(created).isEqualTo(1);
    assertThat(conflicts).isEqualTo(7);
  }
}
