import { NativeModule, requireNativeModule, requireNativeView } from 'expo';
import type { ViewProps } from 'react-native';

export interface SettingsSnapshot {
  themeMode: 'LIGHT' | 'DARK' | 'SYSTEM';
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

type Events = {
  /** A coarse session change (start, stop, pause, touch counting, round, goal progress), never one per count. */
  onSession(event: { state: string }): void;
  /** Android: stored settings changed. */
  onSettings(event: SettingsSnapshot): void;
  /** Android: stored history changed (JSON array of SessionRecord). */
  onHistory(event: { json: string }): void;
  /** Android: stored goals changed (JSON array of Goal). */
  onGoals(event: { json: string }): void;
  /** Android: a launcher shortcut or the tile asked to start counting while the app was open. */
  onStartRequest(event: { goalId: string | null }): void;
};

/** Functions both platforms implement. */
interface SessionApi {
  /** The session right now, as JSON (ChantState). */
  getSession(): string;
  start(goalJson: string | null, touchCounting: boolean): void;
  setPaused(paused: boolean): void;
  setTouchCounting(on: boolean): void;
  countFromScreen(): void;
}

declare class SuperchantNativeModule extends NativeModule<Events> implements SessionApi {
  getSession(): string;
  start(goalJson: string | null, touchCounting: boolean): void;
  setPaused(paused: boolean): void;
  setTouchCounting(on: boolean): void;
  countFromScreen(): void;

  // --- Android (the Kotlin engine owns storage, history and the foreground service) ---
  stop(): void;
  restore(): void;
  hasResumableSession(): Promise<boolean>;
  takeLaunchRequest(): { goalId: string | null } | null;
  reportShortcutUsed(goalId: string | null): void;
  getSettings(): Promise<SettingsSnapshot>;
  getHistory(): Promise<string>;
  getGoals(): Promise<string>;
  setThemeMode(mode: SettingsSnapshot['themeMode']): void;
  setRoundVibration(on: boolean): void;
  setCountVibration(on: boolean): void;
  setCountIntensity(value: number): void;
  setRoundIntensity(value: number): void;
  setMilestonePulse(on: boolean): void;
  setBeadClick(on: boolean): void;
  setDailyTarget(rounds: number): void;
  setAccessibilityConsent(agreed: boolean): void;
  setTouchHintSeen(): void;
  setReminder(enabled: boolean, minuteOfDay: number): Promise<void>;
  saveGoal(goalJson: string): void;
  deleteGoal(goalId: string): void;
  clearHistory(): void;
  flushWrites(): Promise<void>;
  exportBackup(now: number): Promise<string>;
  importBackup(text: string): Promise<{ sessionsAdded: number; goalsAdded: number; goalsUpdated: number }>;
  saveBackupFile(fileName: string, text: string): Promise<boolean | null>;
  openBackupFile(): Promise<string | null>;
  isAccessibilityServiceEnabled(): boolean;
  openAccessibilitySettings(): void;
  needsRestrictedSettings(): boolean;
  openAppSettings(): void;
  notificationsGranted(): boolean;
  hasAmplitudeControl(): boolean;

  // --- iOS (TypeScript owns storage; Swift owns the live session, haptics and the Live Activity) ---
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
  /** Ends the session and returns its final state; the stored copy stays until clearActiveSession. */
  finish(): Promise<string>;
  /** The session persisted by a previous process, as JSON, if any. */
  loadActiveSession(): string | null;
  clearActiveSession(sessionId: string): void;
  /** Resumes a persisted session (JSON) in this process. */
  resume(stateJson: string): void;
  /** Counting feel the Swift engine applies to each count. */
  configureFeedback(feedback: { countVibration: boolean; roundVibration: boolean; milestonePulse: boolean; beadClick: boolean; countIntensity: number; roundIntensity: number }): void;
  requestNotificationPermission(): Promise<boolean>;
  notificationStatus(): Promise<'granted' | 'denied' | 'undetermined'>;
  scheduleReminders(reminders: { id: string; epochMs: number; title: string; body: string }[]): Promise<void>;
  cancelReminders(): Promise<void>;
  exportFile(fileName: string, text: string): Promise<boolean>;
  importFile(): Promise<string | null>;
}

export default requireNativeModule<SuperchantNativeModule>('SuperchantNative');

export interface CounterViewProps extends ViewProps {
  dark: boolean;
  /** Android: volume buttons can count (service enabled and disclosure agreed). */
  volumeReady: boolean;
}

/** The mala ring and tap-to-count pad, drawn natively from the live session (no JS per count). */
export const CounterView = requireNativeView<CounterViewProps>('SuperchantNative', 'CounterView');
