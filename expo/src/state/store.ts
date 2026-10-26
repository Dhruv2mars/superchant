/**
 * App state: settings, goals and history (null while loading, so screens can tell "loading" from "empty")
 * and the coarse session. Components subscribe to slices with a selector, so a session change only
 * re-renders what reads the changed field. The count itself is never here: CounterView draws it natively.
 */
import { useSyncExternalStore } from 'react';
import { ChantState, Goal, idleState, SessionRecord } from '@/core/model';
import { platform } from '@/native/platform';
import { Settings, Sink } from '@/native/types';

export interface AppState {
  settings: Settings | null;
  goals: Goal[] | null;
  history: SessionRecord[] | null;
  session: ChantState;
  /** A start asked for outside Home (Goals, a shortcut, the tile); Home runs it after its permission checks. */
  pendingStart: { goal: Goal | null; goalId?: string | null } | null;
}

let state: AppState = { settings: null, goals: null, history: null, session: idleState, pendingStart: null };
const listeners = new Set<() => void>();

function set(patch: Partial<AppState>) {
  state = { ...state, ...patch };
  listeners.forEach((l) => l());
}

export const getState = () => state;

export function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function requestStart(request: AppState['pendingStart']) {
  set({ pendingStart: request });
}

const sink: Sink = {
  settings: (settings) => set({ settings }),
  goals: (goals) => set({ goals }),
  history: (history) => set({ history }),
  session: (session) => set({ session }),
};

let initialized: Promise<void> | null = null;
export function initStore(): Promise<void> {
  return (initialized ??= platform.init(sink));
}

/** A slice of the state; re-renders only when the selected value changes (Object.is). */
export function useAppState<T>(selector: (s: AppState) => T): T {
  return useSyncExternalStore(subscribe, () => selector(state));
}

export const useSettings = () => useAppState((s) => s.settings);
export const useGoals = () => useAppState((s) => s.goals);
export const useHistory = () => useAppState((s) => s.history);
export const useSession = <T>(selector: (s: ChantState) => T) => useAppState((s) => selector(s.session));
