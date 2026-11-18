import { client } from './api/client.gen';
import * as sdk from './api/sdk.gen';
import type { ApiError, Availability, Booking, CreateBooking, CreateEventType, EventType } from './api/types.gen';

client.setConfig({ baseUrl: '' });

export type { Availability, Booking, EventType };

export class ApiProblem extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

type Result<T> = { data?: T; error?: unknown; response?: Response };

function unwrap<T>(result: Result<T>): T {
  if (result.error !== undefined || result.data === undefined) {
    const err = result.error as Partial<ApiError> | undefined;
    throw new ApiProblem(
      result.response?.status ?? 0,
      err?.code ?? 'NETWORK_ERROR',
      err?.message ?? 'Не удалось связаться с сервером',
    );
  }
  return result.data;
}

export const api = {
  listEventTypes: async () => unwrap<EventType[]>(await sdk.listEventTypes()),
  createEventType: async (body: CreateEventType) =>
    unwrap<EventType>(await sdk.createEventType({ body })),
  getAvailability: async (id: string) =>
    unwrap<Availability>(await sdk.getAvailability({ path: { id } })),
  listBookings: async () => unwrap<Booking[]>(await sdk.listBookings()),
  createBooking: async (body: CreateBooking) =>
    unwrap<Booking>(await sdk.createBooking({ body })),
};
