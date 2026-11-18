import "@typespec/http";
import "@typespec/openapi";

using TypeSpec.Http;
using TypeSpec.OpenAPI;

@service(#{ title: "Календарь звонков" })
@server("/", "Same origin")
namespace CallCalendar;

@doc("Тип встречи, который создаёт владелец календаря.")
model EventType {
  @doc("Идентификатор, который задаёт владелец: латиница в нижнем регистре, цифры и дефис.")
  @pattern("^[a-z0-9-]{1,50}$")
  id: string;

  @minLength(1) @maxLength(100) title: string;

  @maxLength(500) description: string;

  @doc("Длительность в минутах, кратна 5.")
  @minValue(5)
  @maxValue(240)
  durationMinutes: int32;
}

model CreateEventType {
  @pattern("^[a-z0-9-]{1,50}$") id: string;

  @minLength(1) @maxLength(100) title: string;

  @maxLength(500) description?: string;

  @minValue(5) @maxValue(240) durationMinutes: int32;
}

@doc("Свободный слот. Время в UTC, день и часы показываются в часовом поясе владельца.")
model Slot {
  start: utcDateTime;
  end: utcDateTime;
}

model SlotDay {
  @doc("Календарная дата в часовом поясе владельца.")
  date: plainDate;

  slots: Slot[];
}

model Availability {
  @doc("IANA-часовой пояс владельца, например Asia/Almaty.")
  timezone: string;

  @doc("Ровно 14 календарных дней начиная с сегодняшнего.")
  days: SlotDay[];
}

model CreateBooking {
  eventTypeId: string;

  @doc("Начало слота, один из стартов из ответа /availability.")
  start: utcDateTime;

  @minLength(1) @maxLength(100) guestName: string;

  @format("email") guestEmail: string;
}

model Booking {
  @format("uuid") id: string;
  eventTypeId: string;
  eventTypeTitle: string;
  start: utcDateTime;
  end: utcDateTime;
  guestName: string;
  guestEmail: string;
}

@doc("Код ошибки: VALIDATION_ERROR, EVENT_TYPE_EXISTS, EVENT_TYPE_NOT_FOUND, SLOT_UNAVAILABLE, SLOT_TAKEN.")
model ApiError {
  code: string;
  message: string;
}

@route("/api/event-types")
@tag("event-types")
interface EventTypes {
  @get @operationId("listEventTypes") list(): EventType[];

  @post @operationId("createEventType") create(@body body: CreateEventType): {
    @statusCode _: 201;
    @body body: EventType;
  } | {
    @statusCode _: 400;
    @body body: ApiError;
  } | {
    @statusCode _: 409;
    @body body: ApiError;
  };

  @route("{id}/availability")
  @get
  @operationId("getAvailability")
  availability(@path id: string): Availability | {
    @statusCode _: 404;
    @body body: ApiError;
  };
}

@route("/api/bookings")
@tag("bookings")
interface Bookings {
  @get @operationId("listBookings") list(): Booking[];

  @post @operationId("createBooking") create(@body body: CreateBooking): {
    @statusCode _: 201;
    @body body: Booking;
  } | {
    @statusCode _: 400;
    @body body: ApiError;
  } | {
    @statusCode _: 404;
    @body body: ApiError;
  } | {
    @statusCode _: 409;
    @body body: ApiError;
  };
}
