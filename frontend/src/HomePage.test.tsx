import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { client } from './api/client.gen';
import App from './App';

function json(body: unknown) {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
}

describe('Главная страница', () => {
  beforeEach(() => {
    client.setConfig({ baseUrl: 'http://localhost' });
    vi.stubGlobal('fetch', vi.fn(async () => json([])));
  });

  it('открывается по умолчанию и рассказывает о сервисе', () => {
    render(<App />);
    expect(screen.getByRole('heading', { name: 'Запишитесь на звонок за минуту' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Записаться' })).toBeInTheDocument();
  });

  it('кнопка «Записаться» ведёт на страницу записи', async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole('button', { name: 'Записаться' }));
    expect(screen.queryByRole('heading', { name: 'Запишитесь на звонок за минуту' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Запись' })).toHaveClass('active');
  });
});
