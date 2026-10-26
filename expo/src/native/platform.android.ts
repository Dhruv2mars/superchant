/**
 * Android: the Kotlin engine (modules/superchant-native) owns storage, the foreground service, volume keys,
 * the tile, shortcuts and reminders, exactly as in the Kotlin app. This file only forwards calls and turns
 * its events into store updates.
 */
import { PermissionsAndroid } from 'react-native';
import Native from 'superchant-native';
import { suggestedFileName } from '@/core/backup';
import { encodeBackupGoal, parseGoals, parseHistory, parseSession } from './json';
import { BackupOutcome, PlatformApi, Sink } from './types';

const errorCode = (e: unknown) => (e as { code?: string })?.code;

async function requestNotifications(): Promise<boolean> {
  if (Native.notificationsGranted()) return true;
  const result = await PermissionsAndroid.request('android.permission.POST_NOTIFICATIONS');
  return result === PermissionsAndroid.RESULTS.GRANTED;
}

export const platform: PlatformApi = {
  async init(sink: Sink) {
    Native.addListener('onSession', (e) => sink.session(parseSession(e.state)));
    Native.addListener('onSettings', (e) => sink.settings(e));
    Native.addListener('onHistory', (e) => sink.history(parseHistory(e.json)));
    Native.addListener('onGoals', (e) => sink.goals(parseGoals(e.json)));
    sink.session(parseSession(Native.getSession()));
    const [settings, history, goals] = await Promise.all([Native.getSettings(), Native.getHistory(), Native.getGoals()]);
    sink.settings(settings);
    sink.history(parseHistory(history));
    sink.goals(parseGoals(goals));
  },

  getSession: () => parseSession(Native.getSession()),
  start: (goal, touchCounting) => Native.start(goal ? encodeBackupGoal(goal) : null, touchCounting),
  stop: () => Native.stop(),
  setPaused: (paused) => Native.setPaused(paused),
  setTouchCounting: (on) => Native.setTouchCounting(on),
  countFromScreen: () => Native.countFromScreen(),
  hasResumableSession: () => Native.hasResumableSession(),
  restore: () => Native.restore(),

  setThemeMode: (mode) => Native.setThemeMode(mode),
  setRoundVibration: (on) => Native.setRoundVibration(on),
  setCountVibration: (on) => Native.setCountVibration(on),
  setCountIntensity: (v) => Native.setCountIntensity(v),
  setRoundIntensity: (v) => Native.setRoundIntensity(v),
  setMilestonePulse: (on) => Native.setMilestonePulse(on),
  setBeadClick: (on) => Native.setBeadClick(on),
  setDailyTarget: (rounds) => Native.setDailyTarget(rounds),
  setAccessibilityConsent: (agreed) => Native.setAccessibilityConsent(agreed),
  setTouchHintSeen: () => Native.setTouchHintSeen(),
  async setReminder(enabled, minuteOfDay) {
    // Turning it on asks for the notification permission where needed; without it the reminder stays off
    if (enabled && !(await requestNotifications())) return false;
    await Native.setReminder(enabled, minuteOfDay);
    return true;
  },

  saveGoal: (goal) => Native.saveGoal(encodeBackupGoal(goal)),
  deleteGoal: (id) => Native.deleteGoal(id),
  clearHistory: () => Native.clearHistory(),

  async exportBackup(): Promise<BackupOutcome> {
    try {
      // Off the JS thread in Kotlin: years of history take a while to encode
      const text = await Native.exportBackup(Date.now());
      const saved = await Native.saveBackupFile(suggestedFileName(new Date()), text);
      if (!saved) return { kind: 'cancelled' };
      const counts = JSON.parse(text) as { goals: unknown[]; history: unknown[] };
      return { kind: 'exported', sessions: counts.history.length, goals: counts.goals.length };
    } catch {
      return { kind: 'failed', action: 'export' };
    }
  },

  async importBackup(): Promise<BackupOutcome> {
    try {
      const text = await Native.openBackupFile();
      if (text == null) return { kind: 'cancelled' };
      return { kind: 'imported', result: await Native.importBackup(text) };
    } catch (e) {
      return errorCode(e) === 'ERR_BACKUP_FORMAT' ? { kind: 'notValid' } : { kind: 'failed', action: 'import' };
    }
  },

  notificationsGranted: async () => Native.notificationsGranted(),
  requestNotifications,

  volume: {
    isServiceEnabled: () => Native.isAccessibilityServiceEnabled(),
    openAccessibilitySettings: () => Native.openAccessibilitySettings(),
    needsRestrictedSettings: () => Native.needsRestrictedSettings(),
    openAppSettings: () => Native.openAppSettings(),
  },
  get hasAmplitudeControl() {
    return Native.hasAmplitudeControl();
  },

  takeLaunchRequest: () => Native.takeLaunchRequest(),
  onStartRequest(cb) {
    const sub = Native.addListener('onStartRequest', cb);
    return () => sub.remove();
  },
  reportShortcutUsed: (goalId) => Native.reportShortcutUsed(goalId),
};
