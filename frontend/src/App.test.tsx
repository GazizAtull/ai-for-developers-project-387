import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { client } from './api/client.gen';
import App from './App';

const type = { id: 'intro', title: 'Знакомство', description: 'Короткий звонок', durationMinutes: 30 };
const availability = {
  timezone: 'Asia/Almaty',
  days: [
    { date: '2026-11-19', slots: [{ start: '2026-11-19T05:00:00Z', end: '2026-11-19T05:30:00Z' }] },
    { date: '2026-11-20', slots: [] },
  ],
};
const booking = {
  id: '22222222-2222-2222-2222-222222222222',
  eventTypeId: type.id,
  eventTypeTitle: type.title,
  start: '2026-11-19T05:00:00Z',
  end: '2026-11-19T05:30:00Z',
  guestName: 'Аня',
  guestEmail: 'anya@example.com',
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('App', () => {
  beforeEach(() => {
    client.setConfig({ baseUrl: 'http://localhost' });
  });

  it('guest picks a type, a slot and books it', async () => {
    const calls: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: Request | string) => {
        const req = input instanceof Request ? input : new Request(input);
        const path = new URL(req.url).pathname;
        calls.push(`${req.method} ${path}`);
        if (path === '/api/event-types') return json([type]);
        if (path.endsWith('/availability')) return json(availability);
        if (path === '/api/bookings' && req.method === 'POST') return json(booking, 201);
        return json({ code: 'X', message: 'unexpected' }, 500);
      }),
    );
    const user = userEvent.setup();
    render(<App initialView="guest" />);

    await user.click(await screen.findByText('Знакомство'));
    await user.click(await screen.findByRole('button', { name: '10:00' }));
    await user.type(screen.getByLabelText('Имя'), 'Аня');
    await user.type(screen.getByLabelText('Email'), 'anya@example.com');
    await user.click(screen.getByRole('button', { name: 'Записаться' }));

    await waitFor(() => expect(screen.getByText('Вы записаны')).toBeInTheDocument());
    expect(calls).toContain('POST /api/bookings');
  });

  it('shows the server message when the slot is already taken', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: Request | string) => {
        const req = input instanceof Request ? input : new Request(input);
        const path = new URL(req.url).pathname;
        if (path === '/api/event-types') return json([type]);
        if (path.endsWith('/availability')) return json(availability);
        return json({ code: 'SLOT_TAKEN', message: 'Это время уже занято' }, 409);
      }),
    );
    const user = userEvent.setup();
    render(<App initialView="guest" />);

    await user.click(await screen.findByText('Знакомство'));
    await user.click(await screen.findByRole('button', { name: '10:00' }));
    await user.type(screen.getByLabelText('Имя'), 'Боб');
    await user.type(screen.getByLabelText('Email'), 'bob@example.com');
    await user.click(screen.getByRole('button', { name: 'Записаться' }));

    expect(await screen.findByText('Это время уже занято')).toBeInTheDocument();
  });
});
