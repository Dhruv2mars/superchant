// Ported from PracticeStatsTest.kt and HistoryBucketsTest.kt. Local-time functions run under the TZ
// the test script sets (UTC, and again under America/New_York and Asia/Kolkata).
import { describe, expect, test } from 'bun:test';
import { MantraTally, SessionRecord } from '../model';
import {
  addDays,
  bucketSessions,
  dailyTotals,
  heatLevel,
  isPracticeDay,
  mantraTotals,
  millisUntilNextMidnight,
  streak,
} from '../stats';

const today = '2026-10-18';
const at = (day: string, hour = 6) => {
  const [y, m, d] = day.split('-').map(Number);
  return new Date(y, m - 1, d, hour).getTime();
};
function session(day: string, rounds: number, opts: { counts?: number; hour?: number; mantras?: MantraTally[] } = {}): SessionRecord {
  const start = at(day, opts.hour ?? 6);
  return {
    id: `${day}-${opts.hour ?? 6}-${rounds}`,
    startTime: start,
    endTime: start + 600_000,
    totalCounts: opts.counts ?? rounds * 108,
    totalRounds: rounds,
    goalId: null,
    goalName: null,
    mantras: opts.mantras ?? [],
  };
}

describe('PracticeStats', () => {
  test('daily totals group by local start day', () => {
    const daily = dailyTotals([session(today, 2), session(today, 1, { hour: 23 }), session(addDays(today, -1), 4)]);
    expect(daily.get(today)).toEqual({ counts: 324, rounds: 3, millis: 1_200_000, sessions: 2 });
    expect(daily.get(addDays(today, -1))?.rounds).toBe(4);
  });

  test('streak stays alive until today is done', () => {
    const daily = dailyTotals([1, 2, 3].map((n) => session(addDays(today, -n), 1)));
    const s = streak(daily, today, 0);
    expect(s.current).toBe(3);
    expect(s.todayDone).toBe(false);
    const withToday = dailyTotals([0, 1, 2, 3].map((n) => session(addDays(today, -n), 1)));
    expect(streak(withToday, today, 0)).toEqual({ current: 4, best: 4, todayDone: true });
  });

  test('a missed day breaks the streak but best is kept', () => {
    const days = [0, 1, 4, 5, 6, 7, 8].map((n) => session(addDays(today, -n), 1));
    const s = streak(dailyTotals(days), today, 0);
    expect(s.current).toBe(2);
    expect(s.best).toBe(5);
    expect(streak(new Map(), today, 0).current).toBe(0);
  });

  test('streaks cross DST changes and month ends', () => {
    // 2026-03-08 and 2026-11-01 are DST changes in New York; 23- and 25-hour days still count once
    const spring = ['2026-03-06', '2026-03-07', '2026-03-08', '2026-03-09'].map((d) => session(d, 1, { hour: 0 }));
    expect(streak(dailyTotals(spring), '2026-03-09', 0).current).toBe(4);
    const fall = ['2026-10-30', '2026-10-31', '2026-11-01', '2026-11-02'].map((d) => session(d, 1, { hour: 23 }));
    expect(streak(dailyTotals(fall), '2026-11-02', 0)).toEqual({ current: 4, best: 4, todayDone: true });
  });

  test('daily target decides what counts as practice', () => {
    const daily = dailyTotals([session(today, 2), session(addDays(today, -1), 16)]);
    expect(streak(daily, today, 0).current).toBe(2);
    const withTarget = streak(daily, today, 16);
    expect(withTarget.current).toBe(1);
    expect(withTarget.todayDone).toBe(false);
    expect(isPracticeDay({ counts: 5, rounds: 0, millis: 0, sessions: 0 }, 0)).toBe(true);
    expect(isPracticeDay({ counts: 5, rounds: 0, millis: 0, sessions: 0 }, 1)).toBe(false);
  });

  test('mantra totals merge by name and sort by counts', () => {
    const sessions = [
      session(today, 3, { mantras: [{ name: 'Om Namah Shivaya', rounds: 2, counts: 216 }, { name: 'Gayatri', rounds: 1, counts: 120 }] }),
      session(today, 1, { mantras: [{ name: ' om namah shivaya ', rounds: 1, counts: 108 }] }),
      session(today, 1),
    ];
    expect(mantraTotals(sessions)).toEqual([
      { name: 'Om Namah Shivaya', rounds: 3, counts: 324 },
      { name: 'Gayatri', rounds: 1, counts: 120 },
    ]);
  });

  test('heat levels step up to the reference', () => {
    const d = (counts: number, rounds: number) => ({ counts, rounds, millis: 0, sessions: 1 });
    expect(heatLevel(undefined, 16)).toBe(0);
    expect(heatLevel(d(0, 0), 16)).toBe(0);
    expect(heatLevel(d(10, 0), 16)).toBe(1);
    expect(heatLevel(d(1, 6), 16)).toBe(2);
    expect(heatLevel(d(1, 16), 16)).toBe(4);
    expect(heatLevel(d(1, 40), 16)).toBe(4);
  });
});

describe('HistoryBuckets', () => {
  const rec = (id: string, start: number): SessionRecord => ({ ...session(today, 0), id, startTime: start });
  const SUNDAY = 1;
  const MONDAY = 2;

  test('buckets use the first day of the week', () => {
    const now = at('2025-10-15', 10); // Wednesday
    const sessions = [
      rec('today', at('2025-10-15', 0)),
      rec('mon', at('2025-10-13', 12)),
      rec('sun', at('2025-10-12', 12)),
      rec('oct1', at('2025-10-01', 12)),
      rec('sep', at('2025-09-30', 12)),
    ];
    const us = bucketSessions(sessions, now, SUNDAY);
    expect(us.today.map((s) => s.id)).toEqual(['today']);
    expect(us.thisWeek.map((s) => s.id)).toEqual(['mon', 'sun']);
    expect(us.thisMonth.map((s) => s.id)).toEqual(['oct1']);
    expect(us.older.map((s) => s.id)).toEqual(['sep']);
    const de = bucketSessions(sessions, now, MONDAY);
    expect(de.thisWeek.map((s) => s.id)).toEqual(['mon']);
    expect(de.thisMonth.map((s) => s.id)).toEqual(['sun', 'oct1']);
  });

  test('a week spanning months counts as this week', () => {
    const b = bucketSessions([rec('sep29', at('2025-09-29', 12)), rec('sep27', at('2025-09-27', 12))], at('2025-10-02', 12), SUNDAY);
    expect(b.thisWeek.map((s) => s.id)).toEqual(['sep29']);
    expect(b.older.map((s) => s.id)).toEqual(['sep27']);
  });

  test('today moves to this week after midnight', () => {
    const late = [rec('late', at('2025-10-15', 23))];
    expect(bucketSessions(late, at('2025-10-15', 23) + 60_000, SUNDAY).today.length).toBe(1);
    expect(bucketSessions(late, at('2025-10-16', 0) + 60_000, SUNDAY).thisWeek.length).toBe(1);
  });

  test('millis until next midnight is exact', () => {
    expect(millisUntilNextMidnight(at('2025-10-16', 0) - 60_000)).toBe(60_000);
    expect(millisUntilNextMidnight(at('2025-10-16', 0))).toBe(24 * 3_600_000);
  });
});
