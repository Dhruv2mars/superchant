/**
 * Which daily reminders to schedule on iOS. Android's alarm runs code when it fires (Reminder.kt) and skips the
 * reminder if today's practice is done or a session is running; iOS local notifications can't, so the app plans
 * the next week of them whenever practice, the setting or the session changes, leaving out days already done.
 */
import { SessionRecord } from './model';
import { addDays, dailyTotals, dayKey, DayKey, isPracticeDay, streak } from './stats';

export interface PlannedReminder {
  id: DayKey;
  epochMs: number;
  title: string;
  body: string;
}

export interface ReminderTexts {
  title: string;
  /** "Keep your N-day streak going." */
  streak: (days: number) => string;
  /** "N of M rounds so far today." */
  target: (rounds: number, target: number) => string;
  plain: string;
}

/** The local time `minuteOfDay` on `day`; in a DST gap the Date constructor moves it later, as Java's ZonedDateTime does. */
export function triggerAt(day: DayKey, minuteOfDay: number): number {
  const [y, m, d] = day.split('-').map(Number);
  return new Date(y, m - 1, d, Math.floor(minuteOfDay / 60), minuteOfDay % 60).getTime();
}

export function planReminders(opts: {
  now: number;
  minuteOfDay: number;
  history: SessionRecord[];
  dailyTarget: number;
  sessionActive: boolean;
  texts: ReminderTexts;
  days?: number;
}): PlannedReminder[] {
  const { now, minuteOfDay, history, dailyTarget, sessionActive, texts, days = 7 } = opts;
  const daily = dailyTotals(history);
  const today = dayKey(new Date(now));
  const todayDone = isPracticeDay(daily.get(today), dailyTarget);
  const out: PlannedReminder[] = [];
  for (let i = 0; i <= days; i++) {
    const day = addDays(today, i);
    const at = triggerAt(day, minuteOfDay);
    if (at <= now) continue;
    // Today's isn't wanted once today's practice is done, or while a session is running
    if (i === 0 && (todayDone || sessionActive)) continue;
    // The streak as it will stand that morning if nothing more is chanted before then
    const current = i <= 1 ? streak(daily, day, dailyTarget).current : 0;
    const roundsToday = i === 0 ? (daily.get(today)?.rounds ?? 0) : 0;
    const body = current > 0 ? texts.streak(current) : dailyTarget > 0 ? texts.target(roundsToday, dailyTarget) : texts.plain;
    out.push({ id: day, epochMs: at, title: texts.title, body });
  }
  return out;
}
