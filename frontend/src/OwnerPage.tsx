import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, type Booking, type EventType } from './api';
import { formatDateTime, formatDuration, formatTime } from './format';

export default function OwnerPage() {
  const [types, setTypes] = useState<EventType[]>([]);
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [id, setId] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [duration, setDuration] = useState('30');
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      const [t, b] = await Promise.all([api.listEventTypes(), api.listBookings()]);
      setTypes(t);
      setBookings(b);
    } catch (e) {
      setError((e as Error).message);
    }
  }, []);

  useEffect(() => {
    let alive = true;
    Promise.all([api.listEventTypes(), api.listBookings()])
      .then(([t, b]) => {
        if (!alive) return;
        setTypes(t);
        setBookings(b);
      })
      .catch((e: Error) => alive && setError(e.message));
    return () => {
      alive = false;
    };
  }, []);

  async function create(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api.createEventType({ id, title, description, durationMinutes: Number(duration) });
      setId('');
      setTitle('');
      setDescription('');
      await reload();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  return (
    <>
      <section>
        <h2>Типы встреч</h2>
        <ul className="list">
          {types.map((t) => (
            <li key={t.id}>
              <strong>{t.title}</strong> <span className="muted">{formatDuration(t.durationMinutes)}</span>
              {t.description && <div>{t.description}</div>}
            </li>
          ))}
          {types.length === 0 && <li className="muted">Типов встреч пока нет.</li>}
        </ul>
        <form className="card form" onSubmit={(e) => void create(e)}>
          <h3>Новый тип</h3>
          <label>
            Id (латиница, цифры, дефис)
            <input value={id} onChange={(e) => setId(e.target.value)} pattern="[a-z0-9-]{1,50}" maxLength={50} required />
          </label>
          <label>
            Название
            <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={100} required />
          </label>
          <label>
            Описание
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} maxLength={500} />
          </label>
          <label>
            Длительность, мин (кратно 5)
            <input
              type="number"
              min={5}
              max={240}
              step={5}
              value={duration}
              onChange={(e) => setDuration(e.target.value)}
              required
            />
          </label>
          <button type="submit">Создать</button>
        </form>
      </section>

      <section>
        <h2>Предстоящие встречи</h2>
        <button className="link" onClick={() => void reload()}>
          Обновить
        </button>
        <ul className="list">
          {bookings.map((b) => (
            <li key={b.id}>
              <strong>{formatDateTime(b.start)}</strong> — {formatTime(b.end)} · {b.eventTypeTitle}
              <div>
                {b.guestName}, {b.guestEmail}
              </div>
              {b.topic && <div className="topic">Тема: {b.topic}</div>}
            </li>
          ))}
          {bookings.length === 0 && <li className="muted">Записей пока нет.</li>}
        </ul>
        <p className="muted">Время показано в вашем часовом поясе.</p>
      </section>
      {error && <p className="error">{error}</p>}
    </>
  );
}
