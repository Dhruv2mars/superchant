/**
 * Settings, goals and history over a key-value store, with the rules of the Kotlin PreferencesRepository: the
 * same keys and value formats, stored lists that keep elements they can't read, duplicate IDs made unique the
 * way the screens show them, an unparseable list copied to a backup key before it is replaced, and backup
 * import that merges instead of replacing. iOS uses it over UserDefaults; Android keeps the Kotlin original.
 */
import { BackupSettings, Backup, emptyBackupSettings, ImportResult, mergeGoals, mergeHistory } from './backup';
import { decodeGoal, decodeSessionRecord, encodeGoal, encodeSessionRecord, Goal, SessionRecord } from './model';
import { DAILY_TARGET_MAX } from './stats';
import { decodeStoredList, encodeStoredList, upsert, withUniqueGoalIds, withUniqueRecordIds } from './storedList';

export interface KeyValue {
  get(key: string): string | null;
  set(key: string, value: string): void;
}

export type ThemeModeName = 'LIGHT' | 'DARK' | 'SYSTEM';

export interface StoredSettings {
  themeMode: ThemeModeName;
  roundVibration: boolean;
  countVibration: boolean;
  countIntensity: number;
  roundIntensity: number;
  milestonePulse: boolean;
  beadClick: boolean;
  dailyTarget: number;
  reminderEnabled: boolean;
  reminderMinuteOfDay: number;
  accessibilityConsent: boolean;
  touchHintSeen: boolean;
}

export const KEYS = {
  themeMode: 'theme_mode',
  roundVibration: 'vibration_enabled',
  countVibration: 'count_vibration_enabled',
  countIntensity: 'count_vibration_intensity',
  roundIntensity: 'round_vibration_intensity',
  milestonePulse: 'milestone_pulse',
  beadClick: 'bead_click',
  accessibilityConsent: 'accessibility_consent',
  dailyTarget: 'daily_target',
  reminderEnabled: 'reminder_enabled',
  reminderMinute: 'reminder_minute',
  reminderHandledThrough: 'reminder_handled_through',
  touchHintSeen: 'touch_hint_seen',
  history: 'session_history',
  goals: 'saved_goals',
} as const;

const THEMES: ThemeModeName[] = ['LIGHT', 'DARK', 'SYSTEM'];
const clamp = (v: number, lo: number, hi: number) => Math.min(Math.max(v, lo), hi);

function int(kv: KeyValue, key: string): number | null {
  const v = kv.get(key);
  if (v == null || !/^-?\d+$/.test(v)) return null;
  return Number(v);
}
function bool(kv: KeyValue, key: string): boolean | null {
  const v = kv.get(key);
  return v === 'true' ? true : v === 'false' ? false : null;
}

export class Repository {
  constructor(
    private readonly kv: KeyValue,
    private readonly clock: () => number = Date.now,
  ) {}

  settings(): StoredSettings {
    const kv = this.kv;
    const minute = int(kv, KEYS.reminderMinute);
    return {
      themeMode: THEMES[int(kv, KEYS.themeMode) ?? 2] ?? 'SYSTEM',
      roundVibration: bool(kv, KEYS.roundVibration) ?? true,
      countVibration: bool(kv, KEYS.countVibration) ?? true,
      countIntensity: int(kv, KEYS.countIntensity) ?? 80,
      roundIntensity: int(kv, KEYS.roundIntensity) ?? 255,
      milestonePulse: bool(kv, KEYS.milestonePulse) ?? true,
      beadClick: bool(kv, KEYS.beadClick) ?? false,
      dailyTarget: clamp(int(kv, KEYS.dailyTarget) ?? 0, 0, DAILY_TARGET_MAX),
      reminderEnabled: bool(kv, KEYS.reminderEnabled) ?? false,
      reminderMinuteOfDay: minute != null && minute >= 0 && minute < 24 * 60 ? minute : 6 * 60,
      accessibilityConsent: bool(kv, KEYS.accessibilityConsent) ?? false,
      touchHintSeen: bool(kv, KEYS.touchHintSeen) ?? false,
    };
  }

  setThemeMode(mode: ThemeModeName) {
    this.kv.set(KEYS.themeMode, String(THEMES.indexOf(mode)));
  }
  setBool(key: keyof typeof KEYS, value: boolean) {
    this.kv.set(KEYS[key], String(value));
  }
  setCountIntensity(v: number) {
    this.kv.set(KEYS.countIntensity, String(clamp(Math.round(v), 1, 255)));
  }
  setRoundIntensity(v: number) {
    this.kv.set(KEYS.roundIntensity, String(clamp(Math.round(v), 1, 255)));
  }
  setDailyTarget(rounds: number) {
    this.kv.set(KEYS.dailyTarget, String(clamp(rounds, 0, DAILY_TARGET_MAX)));
  }
  /** A changed reminder starts from now: one due earlier today isn't owed anymore. */
  setReminder(enabled: boolean, minuteOfDay: number) {
    this.kv.set(KEYS.reminderEnabled, String(enabled));
    this.kv.set(KEYS.reminderMinute, String(clamp(minuteOfDay, 0, 24 * 60 - 1)));
    this.kv.set(KEYS.reminderHandledThrough, String(this.clock()));
  }
  reminderHandledThrough(): number {
    return int(this.kv, KEYS.reminderHandledThrough) ?? 0;
  }

  history(): SessionRecord[] {
    return withUniqueRecordIds(decodeStoredList(decodeSessionRecord, this.kv.get(KEYS.history)).items);
  }
  goals(): Goal[] {
    return withUniqueGoalIds(decodeStoredList(decodeGoal, this.kv.get(KEYS.goals)).items);
  }

  /** Reads a stored list to change it without losing data (readListForUpdate in Kotlin). */
  private readForUpdate<T>(key: string, decode: (e: never) => T, unique: (items: T[]) => T[]) {
    const stored = this.kv.get(key);
    const list = decodeStoredList(decode as never, stored);
    if (list.corrupt && stored != null) this.kv.set(`${key}_backup_${this.clock()}`, stored);
    return { items: unique(list.items as T[]), unreadable: list.unreadable };
  }

  private updateHistory(transform: (items: SessionRecord[]) => SessionRecord[]) {
    const list = this.readForUpdate(KEYS.history, decodeSessionRecord, withUniqueRecordIds);
    this.kv.set(KEYS.history, encodeStoredList(encodeSessionRecord, transform(list.items), list.unreadable));
  }
  private updateGoals(transform: (items: Goal[]) => Goal[]) {
    const list = this.readForUpdate(KEYS.goals, decodeGoal, withUniqueGoalIds);
    this.kv.set(KEYS.goals, encodeStoredList(encodeGoal, transform(list.items), list.unreadable));
  }

  /** Adds the record at the top, replacing one with the same ID. History is never truncated. */
  addSessionRecord(record: SessionRecord) {
    this.updateHistory((items) => upsert(items, record));
  }
  clearHistory() {
    this.kv.set(KEYS.history, '[]');
  }
  /** Replaces the goal with the same ID, or adds it at the top. */
  saveGoal(goal: Goal) {
    this.updateGoals((goals) => (goals.some((g) => g.id === goal.id) ? goals.map((g) => (g.id === goal.id ? goal : g)) : [goal, ...goals]));
  }
  deleteGoal(goalId: string) {
    this.updateGoals((goals) => goals.filter((g) => g.id !== goalId));
  }

  /** Everything worth keeping; settings never changed by the user are left out. */
  exportBackup(now: number): Backup {
    const kv = this.kv;
    const theme = int(kv, KEYS.themeMode);
    const settings: BackupSettings = {
      ...emptyBackupSettings,
      themeMode: theme != null && theme >= 0 && theme <= 2 ? THEMES[theme] : null,
      dailyTarget: int(kv, KEYS.dailyTarget),
      countVibration: bool(kv, KEYS.countVibration),
      countIntensity: int(kv, KEYS.countIntensity),
      roundVibration: bool(kv, KEYS.roundVibration),
      roundIntensity: int(kv, KEYS.roundIntensity),
      milestonePulse: bool(kv, KEYS.milestonePulse),
      beadClick: bool(kv, KEYS.beadClick),
      reminderEnabled: bool(kv, KEYS.reminderEnabled),
      reminderMinuteOfDay: int(kv, KEYS.reminderMinute),
    };
    return { app: 'SuperChant', format: 1, exportedAt: now, settings, goals: this.goals(), history: this.history() };
  }

  /** Merges a backup: sessions and goals are added or updated by ID, valid settings applied, nothing deleted. */
  importBackup(backup: Backup): ImportResult {
    const history = this.readForUpdate(KEYS.history, decodeSessionRecord, withUniqueRecordIds);
    const historyIds = new Set(history.items.map((r) => r.id));
    const merged = mergeHistory(history.items, backup.history);
    const goals = this.readForUpdate(KEYS.goals, decodeGoal, withUniqueGoalIds);
    const goalIds = new Set(goals.items.map((g) => g.id));
    this.kv.set(KEYS.history, encodeStoredList(encodeSessionRecord, merged, history.unreadable));
    this.kv.set(KEYS.goals, encodeStoredList(encodeGoal, mergeGoals(goals.items, backup.goals), goals.unreadable));

    const s = backup.settings;
    if (s.themeMode != null && THEMES.includes(s.themeMode as ThemeModeName)) this.setThemeMode(s.themeMode as ThemeModeName);
    if (s.dailyTarget != null && s.dailyTarget >= 0 && s.dailyTarget <= DAILY_TARGET_MAX) this.kv.set(KEYS.dailyTarget, String(s.dailyTarget));
    if (s.countVibration != null) this.setBool('countVibration', s.countVibration);
    if (s.countIntensity != null && s.countIntensity >= 1 && s.countIntensity <= 255) this.kv.set(KEYS.countIntensity, String(s.countIntensity));
    if (s.roundVibration != null) this.setBool('roundVibration', s.roundVibration);
    if (s.roundIntensity != null && s.roundIntensity >= 1 && s.roundIntensity <= 255) this.kv.set(KEYS.roundIntensity, String(s.roundIntensity));
    if (s.milestonePulse != null) this.setBool('milestonePulse', s.milestonePulse);
    if (s.beadClick != null) this.setBool('beadClick', s.beadClick);
    if (s.reminderEnabled != null) this.setBool('reminderEnabled', s.reminderEnabled);
    if (s.reminderMinuteOfDay != null && s.reminderMinuteOfDay >= 0 && s.reminderMinuteOfDay < 24 * 60) {
      this.kv.set(KEYS.reminderMinute, String(s.reminderMinuteOfDay));
    }
    // Like a change in Settings, an imported reminder starts from now
    if (s.reminderEnabled != null || s.reminderMinuteOfDay != null) this.kv.set(KEYS.reminderHandledThrough, String(this.clock()));

    return {
      sessionsAdded: merged.filter((r) => !historyIds.has(r.id)).length,
      goalsAdded: backup.goals.filter((g) => !goalIds.has(g.id)).length,
      goalsUpdated: backup.goals.filter((g) => goalIds.has(g.id)).length,
    };
  }
}
