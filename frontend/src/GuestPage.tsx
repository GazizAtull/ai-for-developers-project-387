import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, ApiProblem, type Availability, type Booking, type EventType } from './api';
import { formatDateTime, formatDay, formatDuration, formatTime } from './format';

export default function GuestPage() {
  const [types, setTypes] = useState<EventType[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [type, setType] = useState<EventType | null>(null);
  const [availability, setAvailability] = useState<Availability | null>(null);
  const [date, setDate] = useState<string | null>(null);
  const [start, setStart] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [topic, setTopic] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [booked, setBooked] = useState<Booking | null>(null);

  useEffect(() => {
    api
      .listEventTypes()
      .then(setTypes)
      .catch((e: Error) => setLoadError(e.message));
  }, []);

  const loadAvailability = useCallback(async (t: EventType) => {
    const a = await api.getAvailability(t.id);
    setAvailability(a);
    return a;
  }, []);

  async function pickType(t: EventType) {
    setType(t);
    setStart(null);
    setError(null);
    setBooked(null);
    try {
      const a = await loadAvailability(t);
      setDate(a.days.find((d) => d.slots.length > 0)?.date ?? null);
    } catch (e) {
      setError((e as Error).message);
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!type || !start) return;
    setBusy(true);
    setError(null);
    try {
      setBooked(
        await api.createBooking({
          eventTypeId: type.id,
          start,
          guestName: name,
          guestEmail: email,
          topic: topic.trim(),
        }),
      );
    } catch (err) {
      setError((err as Error).message);
      if (err instanceof ApiProblem && (err.code === 'SLOT_TAKEN' || err.code === 'SLOT_UNAVAILABLE')) {
        setStart(null);
        await loadAvailability(type).catch(() => undefined);
      }
    } finally {
      setBusy(false);
    }
  }

  function reset() {
    setType(null);
    setAvailability(null);
    setDate(null);
    setStart(null);
    setBooked(null);
    setName('');
    setEmail('');
    setTopic('');
    setError(null);
  }

  if (loadError) return <p className="error">{loadError}</p>;
  if (!types) return <p>Загрузка…</p>;

  if (booked && availability) {
    return (
      <section className="card success">
        <h2>Вы записаны</h2>
        <p>
          {booked.eventTypeTitle}: {formatDateTime(booked.start, availability.timezone)} —{' '}
          {formatTime(booked.end, availability.timezone)} ({availability.timezone})
        </p>
        {booked.topic && <p className="topic">Тема: {booked.topic}</p>}
        <button onClick={reset}>Записаться ещё</button>
      </section>
    );
  }

  const day = availability?.days.find((d) => d.date === date);

  return (
    <>
      <section>
        <h2>Выберите тип встречи</h2>
        {types.length === 0 && <p className="muted">Владелец пока не создал ни одного типа встречи.</p>}
        <div className="grid">
          {types.map((t) => (
            <button
              key={t.id}
              className={type?.id === t.id ? 'card choice selected' : 'card choice'}
              onClick={() => void pickType(t)}
            >
              <strong>{t.title}</strong>
              <span className="muted">{formatDuration(t.durationMinutes)}</span>
              {t.description && <span>{t.description}</span>}
            </button>
          ))}
        </div>
      </section>

      {type && availability && (
        <section>
          <h2>Выберите день и время</h2>
          <p className="muted">Время по часовому поясу владельца: {availability.timezone}</p>
          <div className="days">
            {availability.days.map((d) => (
              <button
                key={d.date}
                disabled={d.slots.length === 0}
                className={d.date === date ? 'day selected' : 'day'}
                onClick={() => {
                  setDate(d.date);
                  setStart(null);
                }}
              >
                {formatDay(d.date)}
              </button>
            ))}
          </div>
          {day && day.slots.length > 0 && (
            <div className="slots">
              {day.slots.map((s) => (
                <button
                  key={s.start}
                  className={s.start === start ? 'slot selected' : 'slot'}
                  onClick={() => setStart(s.start)}
                >
                  {formatTime(s.start, availability.timezone)}
                </button>
              ))}
            </div>
          )}
          {!availability.days.some((d) => d.slots.length > 0) && (
            <p className="muted">В ближайшие 14 дней свободного времени нет.</p>
          )}
        </section>
      )}

      {type && availability && start && (
        <form className="card form" onSubmit={(e) => void submit(e)}>
          <h2>Ваши данные</h2>
          <p>
            {type.title}, {formatDateTime(start, availability.timezone)}
          </p>
          <label>
            Имя
            <input value={name} onChange={(e) => setName(e.target.value)} maxLength={100} required />
          </label>
          <label>
            Email
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </label>
          <label>
            Тема разговора
            <textarea
              value={topic}
              onChange={(e) => setTopic(e.target.value)}
              maxLength={300}
              rows={3}
            />
          </label>
          <button type="submit" disabled={busy}>
            {busy ? 'Записываем…' : 'Записаться'}
          </button>
        </form>
      )}

      {error && <p className="error">{error}</p>}
    </>
  );
}
