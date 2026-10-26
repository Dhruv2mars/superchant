// Ported from PreferencesRepositoryTest.kt (the parts that are about stored data rather than DataStore's queue),
// plus the iOS reminder plan.
import { describe, expect, test } from 'bun:test';
import { decodeBackup, emptyBackupSettings, encodeBackup } from '../backup';
import { stringifyKJson, kArray } from '../kjson';
import { encodeGoal, encodeSessionRecord, Goal, SessionRecord } from '../model';
import { planReminders } from '../reminders';
import { KEYS, KeyValue, Repository } from '../repository';
import { addDays, dayKey } from '../stats';

class MemoryKV implements KeyValue {
  map = new Map<string, string>();
  get = (k: string) => this.map.get(k) ?? null;
  set = (k: string, v: string) => void this.map.set(k, v);
}

const goal: Goal = { id: 'g', name: 'Morning', mantras: [{ id: 'm', name: 'Om', targetRounds: 1 }], createdAt: 1 };
const record: SessionRecord = { id: 's', startTime: 1, endTime: 2, totalCounts: 108, totalRounds: 1, goalId: null, goalName: null, mantras: [] };
const goalsJson = (...g: Goal[]) => stringifyKJson(kArray(g.map(encodeGoal)));
const historyJson = (...r: SessionRecord[]) => stringifyKJson(kArray(r.map(encodeSessionRecord)));

describe('Repository', () => {
  test('defaults match the Kotlin app', () => {
    expect(new Repository(new MemoryKV()).settings()).toEqual({
      themeMode: 'SYSTEM', roundVibration: true, countVibration: true, countIntensity: 80, roundIntensity: 255,
      milestonePulse: true, beadClick: false, dailyTarget: 0, reminderEnabled: false, reminderMinuteOfDay: 360,
      accessibilityConsent: false, touchHintSeen: false,
    });
  });

  test('edits and deletes hit the duplicate that was shown', () => {
    const kv = new MemoryKV();
    kv.set(KEYS.goals, goalsJson(goal, { ...goal, name: 'Evening' }));
    const repo = new Repository(kv);
    expect(repo.goals().map((g) => g.id)).toEqual(['g', 'g-2']);
    repo.saveGoal({ ...goal, id: 'g-2', name: 'Night' });
    expect(repo.goals().map((g) => g.name)).toEqual(['Morning', 'Night']);
    repo.deleteGoal('g-2');
    expect(repo.goals().map((g) => g.name)).toEqual(['Morning']);
  });

  test('importing an export into data with duplicate ids adds nothing', () => {
    const kv = new MemoryKV();
    kv.set(KEYS.goals, goalsJson(goal, { ...goal, name: 'Evening' }));
    kv.set(KEYS.history, historyJson(record, { ...record, startTime: 5 }));
    const repo = new Repository(kv);
    const backup = decodeBackup(encodeBackup(repo.exportBackup(0)));
    expect(repo.importBackup(backup)).toEqual({ sessionsAdded: 0, goalsAdded: 0, goalsUpdated: 2 });
    expect(repo.goals().map((g) => g.id)).toEqual(['g', 'g-2']);
    expect(repo.history().length).toBe(2);
  });

  test('unreadable stored lists are kept as a backup before being replaced', () => {
    const kv = new MemoryKV();
    kv.set(KEYS.goals, '{not json');
    kv.set(KEYS.history, 'also not json');
    let t = 100;
    const repo = new Repository(kv, () => t++);
    repo.importBackup({ app: 'SuperChant', format: 1, exportedAt: 0, settings: emptyBackupSettings, goals: [goal], history: [record] });
    repo.saveGoal({ ...goal, id: 'g2' });
    const backups = [...kv.map].filter(([k]) => k.includes('_backup_')).map(([, v]) => v);
    expect(new Set(backups)).toEqual(new Set(['{not json', 'also not json']));
    expect(repo.goals().map((g) => g.id)).toEqual(['g2', 'g']);
  });

  test('history keeps unreadable elements and never drops anything', () => {
    const kv = new MemoryKV();
    kv.set(KEYS.history, `[${historyJson(record).slice(1, -1)},{"id":"broken"}]`);
    const repo = new Repository(kv);
    repo.addSessionRecord({ ...record, id: 'new', startTime: 9 });
    expect(repo.history().map((r) => r.id)).toEqual(['new', 's']);
    expect(kv.get(KEYS.history)!.endsWith('{"id":"broken"}]')).toBe(true);
  });

  test('changing or importing the reminder marks it handled from now', () => {
    const kv = new MemoryKV();
    const repo = new Repository(kv, () => 1234);
    repo.setReminder(true, 7 * 60);
    expect(repo.settings().reminderEnabled).toBe(true);
    expect(repo.settings().reminderMinuteOfDay).toBe(420);
    expect(repo.reminderHandledThrough()).toBe(1234);
    kv.set(KEYS.reminderHandledThrough, '5');
    repo.importBackup({ app: 'SuperChant', format: 1, exportedAt: 0, settings: { ...emptyBackupSettings, reminderMinuteOfDay: 480 }, goals: [], history: [] });
    expect(repo.reminderHandledThrough()).toBe(1234);
  });

  test('imported settings out of range are ignored', () => {
    const repo = new Repository(new MemoryKV());
    repo.importBackup({
      app: 'SuperChant', format: 1, exportedAt: 0, goals: [], history: [],
      settings: { ...emptyBackupSettings, themeMode: 'DARK', dailyTarget: 500, countIntensity: 0, roundIntensity: 300, reminderMinuteOfDay: 1440, beadClick: true },
    });
    const s = repo.settings();
    expect([s.themeMode, s.dailyTarget, s.countIntensity, s.roundIntensity, s.reminderMinuteOfDay, s.beadClick]).toEqual(['DARK', 0, 80, 255, 360, true]);
  });
});

describe('iOS reminder plan', () => {
  const texts = { title: 'Time to chant', streak: (d: number) => `streak ${d}`, target: (r: number, t: number) => `${r}/${t}`, plain: 'plain' };
  const at = (day: string, h: number, m = 0) => {
    const [y, mo, d] = day.split('-').map(Number);
    return new Date(y, mo - 1, d, h, m).getTime();
  };
  const today = '2026-10-18';
  const rec = (day: string, rounds: number): SessionRecord => ({ ...record, id: day, startTime: at(day, 5), endTime: at(day, 5) + 1, totalRounds: rounds, totalCounts: rounds * 108 });

  test('later today, then the next week', () => {
    const plan = planReminders({ now: at(today, 5), minuteOfDay: 360, history: [], dailyTarget: 0, sessionActive: false, texts });
    expect(plan.map((p) => p.id)).toEqual([0, 1, 2, 3, 4, 5, 6, 7].map((i) => addDays(today, i)));
    expect(plan[0].epochMs).toBe(at(today, 6));
    expect(plan[0].body).toBe('plain');
  });

  test('skips today once practiced or while a session runs; streak text as it will stand', () => {
    const history = [rec(addDays(today, -1), 1), rec(today, 1)];
    const done = planReminders({ now: at(today, 5, 30), minuteOfDay: 360, history, dailyTarget: 0, sessionActive: false, texts });
    expect(done[0].id).toBe(addDays(today, 1));
    expect(done[0].body).toBe('streak 2');
    expect(done[1].body).toBe('plain');
    const running = planReminders({ now: at(today, 5), minuteOfDay: 360, history: [rec(addDays(today, -1), 1)], dailyTarget: 0, sessionActive: true, texts });
    expect(running[0].id).toBe(addDays(today, 1));
    const target = planReminders({ now: at(today, 5), minuteOfDay: 360, history: [], dailyTarget: 4, sessionActive: false, texts });
    expect(target[0].body).toBe('0/4');
  });

  test('a reminder time already past today starts tomorrow', () => {
    const plan = planReminders({ now: at(today, 6), minuteOfDay: 360, history: [], dailyTarget: 0, sessionActive: false, texts });
    expect(plan[0].id).toBe(addDays(today, 1));
    expect(dayKey(new Date(plan[0].epochMs))).toBe(addDays(today, 1));
  });
});
