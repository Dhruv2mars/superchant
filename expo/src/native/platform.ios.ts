/**
 * iOS: settings, goals and history live in TypeScript (core/repository.ts over UserDefaults); the live
 * session, haptics and the Live Activity live in Swift (modules/superchant-native/ios). The order of
 * operations follows the Kotlin engine: a finished session is written to history before its stored copy is
 * cleared, and a session left by a dead process is resumed, or saved if idle for 3+ hours.
 */
import { AppState } from 'react-native';
import Native from 'superchant-native';
import { BackupFormatException, decodeBackup, encodeBackup, suggestedFileName } from '@/core/backup';
import { Goal, restoreAction, normalizedRounds, toRecord } from '@/core/model';
import { planReminders } from '@/core/reminders';
import { KeyValue, Repository } from '@/core/repository';
import { t, tp } from '@/i18n';
import { encodeBackupGoal, encodeSession, parseSession } from './json';
import { BackupOutcome, PlatformApi, Settings, Sink } from './types';

const kv: KeyValue = { get: (k) => Native.getItem(k), set: (k, v) => Native.setItem(k, v) };
const repo = new Repository(kv);
let sink: Sink | null = null;

function publishSettings() {
  const s: Settings = repo.settings();
  Native.configureFeedback(s);
  sink?.settings(s);
  return s;
}
const publishHistory = () => sink?.history(repo.history());
const publishGoals = () => sink?.goals(repo.goals());

/** Re-plans the week's reminders from the stored settings and history. */
async function syncReminders() {
  const s = repo.settings();
  if (!s.reminderEnabled || (await Native.notificationStatus()) !== 'granted') return Native.cancelReminders();
  const plan = planReminders({
    now: Date.now(),
    minuteOfDay: s.reminderMinuteOfDay,
    history: repo.history(),
    dailyTarget: s.dailyTarget,
    sessionActive: parseSession(Native.getSession()).isActive,
    texts: {
      title: t('reminder_title'),
      streak: (d) => tp('reminder_text_streak', d),
      target: (r, target) => tp('reminder_text_target', target, r, target),
      plain: t('reminder_text'),
    },
  });
  return Native.scheduleReminders(plan);
}

/** Saves a finished session to history (if anything was counted), then clears its stored copy. */
function saveFinished(stateJson: string, endTime?: number) {
  const s = parseSession(stateJson);
  if (!s.isActive) return;
  if (s.count > 0) repo.addSessionRecord(toRecord(s, endTime ?? Date.now()));
  Native.clearActiveSession(s.sessionId);
  publishHistory();
}

/** A session persisted by a process that died: resume it, save it (idle 3+ hours), or drop an empty one. */
function restoreStored(resume: boolean): boolean {
  if (parseSession(Native.getSession()).isActive) return true;
  const stored = Native.loadActiveSession();
  if (!stored) return false;
  const state = normalizedRounds(parseSession(stored));
  const action = restoreAction(state, Date.now());
  if (action.type === 'resume') {
    if (resume) Native.resume(encodeSession(state));
    return true;
  }
  if (action.type === 'finish') {
    repo.addSessionRecord(action.record);
    publishHistory();
  }
  Native.clearActiveSession(state.sessionId);
  return false;
}

export const platform: PlatformApi = {
  async init(s) {
    sink = s;
    Native.addListener('onSession', (e) => {
      const state = parseSession(e.state);
      s.session(state);
      // A session starting or ending changes whether today's reminder is wanted
      syncReminders();
    });
    publishSettings();
    publishGoals();
    publishHistory();
    restoreStored(true);
    s.session(parseSession(Native.getSession()));
    syncReminders();
    // Back from the background: a new day, or a Live Activity tap that paused the session
    AppState.addEventListener('change', (st) => {
      if (st === 'active') {
        s.session(parseSession(Native.getSession()));
        syncReminders();
      }
    });
  },

  getSession: () => parseSession(Native.getSession()),

  start(goal: Goal | null) {
    // Starting over a running session saves it first (ChantingService.startSession)
    const current = parseSession(Native.getSession());
    if (current.isActive) {
      Native.finish().then((json) => {
        saveFinished(json);
        Native.start(goal ? encodeBackupGoal(goal) : null, true);
      });
      return;
    }
    // A stored session not resumed yet is saved to history rather than overwritten
    const stored = Native.loadActiveSession();
    if (stored) saveFinished(stored, Math.max(parseSession(stored).lastUpdatedAt, parseSession(stored).sessionStartTime));
    Native.start(goal ? encodeBackupGoal(goal) : null, true);
  },

  stop() {
    Native.finish().then((json) => {
      saveFinished(json);
      syncReminders();
    });
  },
  setPaused: (paused) => Native.setPaused(paused),
  setTouchCounting: (on) => Native.setTouchCounting(on),
  countFromScreen: () => Native.countFromScreen(),
  hasResumableSession: async () => restoreStored(false),
  restore: () => void restoreStored(true),

  setThemeMode: (mode) => (repo.setThemeMode(mode), publishSettings()),
  setRoundVibration: (on) => (repo.setBool('roundVibration', on), publishSettings()),
  setCountVibration: (on) => (repo.setBool('countVibration', on), publishSettings()),
  setCountIntensity: (v) => (repo.setCountIntensity(v), publishSettings()),
  setRoundIntensity: (v) => (repo.setRoundIntensity(v), publishSettings()),
  setMilestonePulse: (on) => (repo.setBool('milestonePulse', on), publishSettings()),
  setBeadClick: (on) => (repo.setBool('beadClick', on), publishSettings()),
  setDailyTarget: (rounds) => {
    repo.setDailyTarget(rounds);
    publishSettings();
    syncReminders();
  },
  setAccessibilityConsent: () => {},
  setTouchHintSeen: () => (repo.setBool('touchHintSeen', true), publishSettings()),
  async setReminder(enabled, minuteOfDay) {
    if (enabled && (await Native.notificationStatus()) !== 'granted' && !(await Native.requestNotificationPermission())) return false;
    repo.setReminder(enabled, minuteOfDay);
    publishSettings();
    await syncReminders();
    return true;
  },

  saveGoal: (goal) => (repo.saveGoal(goal), publishGoals()),
  deleteGoal: (id) => (repo.deleteGoal(id), publishGoals()),
  clearHistory: () => {
    repo.clearHistory();
    publishHistory();
    syncReminders();
  },

  async exportBackup(): Promise<BackupOutcome> {
    try {
      const backup = repo.exportBackup(Date.now());
      const saved = await Native.exportFile(suggestedFileName(new Date()), encodeBackup(backup));
      return saved ? { kind: 'exported', sessions: backup.history.length, goals: backup.goals.length } : { kind: 'cancelled' };
    } catch {
      return { kind: 'failed', action: 'export' };
    }
  },

  async importBackup(): Promise<BackupOutcome> {
    try {
      const text = await Native.importFile();
      if (text == null) return { kind: 'cancelled' };
      const backup = decodeBackup(text);
      const result = repo.importBackup(backup);
      // A backup may turn the reminder on; without permission it stays off until turned on in Settings
      if (backup.settings.reminderEnabled === true && (await Native.notificationStatus()) !== 'granted') {
        repo.setReminder(false, backup.settings.reminderMinuteOfDay ?? 6 * 60);
      }
      publishSettings();
      publishGoals();
      publishHistory();
      await syncReminders();
      return { kind: 'imported', result };
    } catch (e) {
      const code = (e as { code?: string })?.code;
      return e instanceof BackupFormatException || code === 'ERR_BACKUP_FORMAT' ? { kind: 'notValid' } : { kind: 'failed', action: 'import' };
    }
  },

  notificationsGranted: async () => (await Native.notificationStatus()) === 'granted',
  requestNotifications: () => Native.requestNotificationPermission(),

  volume: undefined,
  hasAmplitudeControl: Native.hasAmplitudeControl(),

  takeLaunchRequest: () => null,
  onStartRequest: () => () => {},
  reportShortcutUsed: () => {},
};
