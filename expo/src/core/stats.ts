/**
 * Insights derived from session history (data/PracticeStats.kt, data/HistoryBuckets.kt). Pure; "local"
 * means the device time zone, through Date's local getters.
 */
import { recordDuration, SessionRecord } from './model';

export const DAILY_TARGET_MAX = 200;

/** A local calendar day as "YYYY-MM-DD" (sorts chronologically). */
export type DayKey = string;

const pad = (n: number, w = 2) => String(n).padStart(w, '0');

export function dayKey(d: Date): DayKey {
  return `${pad(d.getFullYear(), 4)}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export const dayKeyOf = (epochMs: number) => dayKey(new Date(epochMs));

/** The day `days` after `key` (negative for before), by calendar, so DST never skips or repeats a day. */
export function addDays(key: DayKey, days: number): DayKey {
  const [y, m, d] = key.split('-').map(Number);
  return dayKey(new Date(y, m - 1, d + days, 12));
}

export interface DayTotal {
  counts: number;
  rounds: number;
  millis: number;
  sessions: number;
}

export const emptyDay: DayTotal = { counts: 0, rounds: 0, millis: 0, sessions: 0 };

export interface Streak {
  current: number;
  best: number;
  todayDone: boolean;
}

export interface MantraTotal {
  name: string;
  rounds: number;
  counts: number;
}

/** Totals per local day; a session belongs to the day it started. */
export function dailyTotals(sessions: SessionRecord[]): Map<DayKey, DayTotal> {
  const out = new Map<DayKey, DayTotal>();
  for (const s of sessions) {
    const key = dayKeyOf(s.startTime);
    const t = out.get(key) ?? { ...emptyDay };
    t.counts += s.totalCounts;
    t.rounds += s.totalRounds;
    t.millis += recordDuration(s);
    t.sessions += 1;
    out.set(key, t);
  }
  return out;
}

/** A day counts towards the streak once it meets the daily target (rounds), or has any counts without one. */
export function isPracticeDay(total: DayTotal | undefined, dailyTarget: number): boolean {
  if (!total) return false;
  return dailyTarget > 0 ? total.rounds >= dailyTarget : total.counts > 0;
}

/** Consecutive practice days: current ends today, or yesterday while today isn't done yet. */
export function streak(daily: Map<DayKey, DayTotal>, today: DayKey, dailyTarget: number): Streak {
  const practiced = (day: DayKey) => isPracticeDay(daily.get(day), dailyTarget);
  const todayDone = practiced(today);
  let current = 0;
  let day = todayDone ? today : addDays(today, -1);
  while (practiced(day)) {
    current++;
    day = addDays(day, -1);
  }
  let best = 0;
  let run = 0;
  let previous: DayKey | null = null;
  for (const practiceDay of [...daily.keys()].filter(practiced).sort()) {
    run = previous != null && addDays(previous, 1) === practiceDay ? run + 1 : 1;
    best = Math.max(best, run);
    previous = practiceDay;
  }
  return { current, best: Math.max(best, current), todayDone };
}

/** Lifetime per-mantra totals, most chanted first. Names match ignoring case and surrounding spaces. */
export function mantraTotals(sessions: SessionRecord[]): MantraTotal[] {
  const groups = new Map<string, MantraTotal>();
  for (const s of sessions) {
    for (const t of s.mantras) {
      if (t.name.trim() === '') continue;
      const key = t.name.trim().toLowerCase();
      const g = groups.get(key);
      if (g) {
        g.rounds += t.rounds;
        g.counts += t.counts;
      } else {
        groups.set(key, { name: t.name.trim(), rounds: t.rounds, counts: t.counts });
      }
    }
  }
  return [...groups.values()].sort((a, b) => {
    if (b.counts !== a.counts) return b.counts - a.counts;
    const x = a.name.toLowerCase();
    const y = b.name.toLowerCase();
    return x < y ? -1 : x > y ? 1 : 0;
  });
}

/**
 * Heatmap shade 0..4: 0 for no practice, then four steps up to `reference` rounds (the daily target, or
 * the busiest day shown). A day with counts but no full round still shows the lightest shade.
 */
export function heatLevel(total: DayTotal | undefined, reference: number): number {
  if (!total || total.counts <= 0) return 0;
  const ratio = reference > 0 ? total.rounds / reference : 1;
  const clamped = Math.min(Math.max(ratio, 0), 1);
  return Math.min(Math.max(1 + Math.trunc(clamped * 3), 1), 4);
}

export interface HistoryBuckets {
  today: SessionRecord[];
  thisWeek: SessionRecord[];
  thisMonth: SessionRecord[];
  older: SessionRecord[];
}

/**
 * Groups sessions (kept in order) relative to now: today, this week (from the locale's first day of the
 * week; 1 = Sunday ... 7 = Saturday, as java.util.Calendar), this month, older.
 */
export function bucketSessions(sessions: SessionRecord[], now: number, firstDayOfWeek: number): HistoryBuckets {
  const n = new Date(now);
  const startOfToday = new Date(n.getFullYear(), n.getMonth(), n.getDate()).getTime();
  // Calendar.set(DAY_OF_WEEK, first) moves within the week that starts on `first`
  const dow = n.getDay() + 1;
  const back = (dow - firstDayOfWeek + 7) % 7;
  const startOfWeek = new Date(n.getFullYear(), n.getMonth(), n.getDate() - back).getTime();
  const startOfMonth = new Date(n.getFullYear(), n.getMonth(), 1).getTime();
  const out: HistoryBuckets = { today: [], thisWeek: [], thisMonth: [], older: [] };
  for (const s of sessions) {
    if (s.startTime >= startOfToday) out.today.push(s);
    else if (s.startTime >= startOfWeek) out.thisWeek.push(s);
    else if (s.startTime >= startOfMonth) out.thisMonth.push(s);
    else out.older.push(s);
  }
  return out;
}

/** Milliseconds from now until the next local midnight. */
export function millisUntilNextMidnight(now: number): number {
  const n = new Date(now);
  return new Date(n.getFullYear(), n.getMonth(), n.getDate() + 1).getTime() - now;
}
