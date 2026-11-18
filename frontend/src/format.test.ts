import { formatDay, formatDuration, formatTime } from './format';

describe('format', () => {
  it('formats plain date without timezone shift', () => {
    expect(formatDay('2026-11-19')).toContain('19');
  });

  it('formats time in the given timezone', () => {
    expect(formatTime('2026-11-19T05:00:00Z', 'Asia/Almaty')).toBe('10:00');
  });

  it('formats duration', () => {
    expect(formatDuration(30)).toBe('30 мин');
    expect(formatDuration(60)).toBe('1 ч');
    expect(formatDuration(90)).toBe('1 ч 30 мин');
  });
});
