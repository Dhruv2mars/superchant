import type { SettingsSnapshot } from 'superchant-native';
import type { ChantState, Goal, SessionRecord } from '@/core/model';

export type Settings = SettingsSnapshot;
export type ThemeMode = Settings['themeMode'];

export const defaultSettings: Settings = {
  themeMode: 'SYSTEM',
  roundVibration: true,
  countVibration: true,
  countIntensity: 80,
  roundIntensity: 255,
  milestonePulse: true,
  beadClick: false,
  dailyTarget: 0,
  reminderEnabled: false,
  reminderMinuteOfDay: 6 * 60,
  accessibilityConsent: false,
  touchHintSeen: false,
};

export interface ImportResult {
  sessionsAdded: number;
  goalsAdded: number;
  goalsUpdated: number;
}

export type BackupOutcome =
  | { kind: 'exported'; sessions: number; goals: number }
  | { kind: 'imported'; result: ImportResult }
  | { kind: 'cancelled' }
  | { kind: 'notValid' }
  | { kind: 'failed'; action: 'export' | 'import' };

/** Where the platform layer delivers stored data and session changes. */
export interface Sink {
  settings(s: Settings): void;
  goals(g: Goal[]): void;
  history(h: SessionRecord[]): void;
  session(s: ChantState): void;
}

/**
 * Everything the screens need from the platform. Android delegates to the Kotlin engine (which also owns
 * storage); iOS runs storage in TypeScript and the live session in Swift.
 */
export interface PlatformApi {
  /** Loads stored data into the sink and keeps it updated; restores a session left by a dead process. */
  init(sink: Sink): Promise<void>;

  getSession(): ChantState;
  start(goal: Goal | null, touchCounting: boolean): void;
  /** Stops and saves the session. */
  stop(): void;
  setPaused(paused: boolean): void;
  setTouchCounting(on: boolean): void;
  countFromScreen(): void;
  /** A session persisted by a dead process would be resumed (not ended) by restore(). */
  hasResumableSession(): Promise<boolean>;
  restore(): void;

  setThemeMode(mode: ThemeMode): void;
  setRoundVibration(on: boolean): void;
  setCountVibration(on: boolean): void;
  setCountIntensity(v: number): void;
  setRoundIntensity(v: number): void;
  setMilestonePulse(on: boolean): void;
  setBeadClick(on: boolean): void;
  setDailyTarget(rounds: number): void;
  setAccessibilityConsent(agreed: boolean): void;
  setTouchHintSeen(): void;
  /** Saves the reminder and re-arms it. Resolves false if notifications aren't allowed (it then stays off). */
  setReminder(enabled: boolean, minuteOfDay: number): Promise<boolean>;

  saveGoal(goal: Goal): void;
  deleteGoal(goalId: string): void;
  clearHistory(): void;

  exportBackup(): Promise<BackupOutcome>;
  importBackup(): Promise<BackupOutcome>;

  /** Notification permission for the session notification (Android) or reminders. */
  notificationsGranted(): Promise<boolean>;
  requestNotifications(): Promise<boolean>;

  /** Android-only capabilities; absent on iOS. */
  volume?: {
    isServiceEnabled(): boolean;
    openAccessibilitySettings(): void;
    needsRestrictedSettings(): boolean;
    openAppSettings(): void;
  };
  hasAmplitudeControl: boolean;

  /** A start asked for by a launcher shortcut, the tile, or an iOS quick action. */
  takeLaunchRequest(): { goalId: string | null } | null;
  onStartRequest(cb: (r: { goalId: string | null }) => void): () => void;
  reportShortcutUsed(goalId: string | null): void;
}
