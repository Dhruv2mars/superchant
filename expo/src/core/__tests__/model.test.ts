// Ported from the Android engine's ChantStateTest.kt, ChantCounterTest.kt and MilestoneTest.kt
import { describe, expect, test } from 'bun:test';
import { parseKJson, stringifyKJson } from '../kjson';
import {
  ChantState,
  chantState,
  CountDebouncer,
  CountFeedback,
  currentMantra,
  currentRoundCount,
  decodeChantState,
  encodeChantState,
  Goal,
  increment,
  isGoalComplete,
  mantraTallies,
  normalizedRounds,
  progress,
  recordDuration,
  restoreAction,
  STALE_AFTER_MS,
  startState,
  toRecord,
} from '../model';

const goal: Goal = {
  id: 'g',
  name: 'Morning',
  mantras: [
    { id: 'a', name: 'A', targetRounds: 1 },
    { id: 'b', name: 'B', targetRounds: 2 },
  ],
  createdAt: 0,
};

function press(state: ChantState, times: number): [ChantState, CountFeedback[]] {
  const feedback: CountFeedback[] = [];
  for (let i = 0; i < times; i++) {
    const r = increment(state);
    if (!r) break;
    state = { ...r.state, lastUpdatedAt: 1000 + i };
    feedback.push(r.feedback);
  }
  return [state, feedback];
}

const inc = (s: ChantState) => increment(s)!;
const incTimes = (s: ChantState, n: number) => {
  for (let i = 0; i < n; i++) s = inc(s).state;
  return s;
};
const active = chantState({ isActive: true });

describe('ChantState', () => {
  test('round count rolls over at 108', () => {
    expect(currentRoundCount(chantState({ count: 107 }))).toBe(107);
    expect(currentRoundCount(chantState({ count: 108 }))).toBe(0);
    expect(currentRoundCount(chantState({ count: 109 }))).toBe(1);
    expect(progress(chantState({ count: 216 }))).toBe(0);
    expect(progress(chantState({ count: 54 }))).toBe(0.5);
  });

  test('goal completes only after last mantra target', () => {
    expect(isGoalComplete(chantState({ activeGoal: goal, currentMantraIndex: 0, mantraRoundsCompleted: 1 }))).toBe(false);
    expect(isGoalComplete(chantState({ activeGoal: goal, currentMantraIndex: 1, mantraRoundsCompleted: 1 }))).toBe(false);
    expect(isGoalComplete(chantState({ activeGoal: goal, currentMantraIndex: 1, mantraRoundsCompleted: 2 }))).toBe(true);
    expect(currentMantra(chantState({ activeGoal: goal, currentMantraIndex: 1 }))?.name).toBe('B');
    expect(isGoalComplete(chantState())).toBe(false);
  });

  test('record keeps session id and goal', () => {
    const [state] = press(startState(goal, 100), 5);
    const record = toRecord(state, 900);
    expect(record.id).toBe(state.sessionId);
    expect(record.goalId).toBe('g');
    expect(record.goalName).toBe('Morning');
    expect(record.totalCounts).toBe(5);
    expect(recordDuration(record)).toBe(800);
    expect(recordDuration(toRecord(state, 50))).toBe(0);
  });

  test('start creates unique session ids', () => {
    expect(startState(null, 0).sessionId).not.toBe(startState(null, 0).sessionId);
  });

  test('restore resumes a recent session and finishes a stale one', () => {
    const [state] = press(startState(goal, 0), 10);
    const last = state.lastUpdatedAt;
    expect(restoreAction(state, last + 60_000)).toEqual({ type: 'resume', state });
    expect(restoreAction(state, last + STALE_AFTER_MS + 1)).toEqual({ type: 'finish', record: toRecord(state, last) });
    expect(restoreAction(startState(null, 0), STALE_AFTER_MS + 1)).toEqual({ type: 'discard' });
    expect(restoreAction(chantState(), 0)).toEqual({ type: 'discard' });
  });

  test('active session survives a JSON round trip', () => {
    const [state] = press(startState(goal, 0), 150);
    const restored = decodeChantState(parseKJson(stringifyKJson(encodeChantState(state))));
    expect(restored).toEqual(state);
    expect(restored.currentMantraIndex).toBe(1);
  });

  test('a session persisted by an older version still loads', () => {
    const stored = decodeChantState(
      parseKJson('{"count":30,"rounds":1,"isActive":true,"sessionStartTime":5,"roundSize":27,"sessionId":"s","lastUpdatedAt":9}'),
    );
    expect(currentRoundCount(stored)).toBe(30);
    expect(stored.touchCounting).toBe(false);
    expect(startState(null, 0, true).touchCounting).toBe(true);
  });

  test('older round sizes are normalized to 108 on restore', () => {
    const legacy = chantState({ isActive: true, count: 54, rounds: 1, activeGoal: goal, currentMantraIndex: 1 });
    const restored = normalizedRounds(legacy);
    expect(restored.rounds).toBe(0);
    expect(restored.currentMantraIndex).toBe(0);
    expect(restored.mantraRoundsCompleted).toBe(0);
    expect(mantraTallies(restored).reduce((s, t) => s + t.counts, 0)).toBe(restored.count);
    const far = normalizedRounds(chantState({ isActive: true, count: 250, activeGoal: goal }));
    expect([far.rounds, far.currentMantraIndex, far.mantraRoundsCompleted]).toEqual([2, 1, 1]);
  });

  test('sessions counted at 108 are unchanged by normalizing', () => {
    const [state] = press(chantState({ isActive: true, activeGoal: goal }), 250);
    expect(normalizedRounds(state)).toEqual(state);
    const [free] = press(chantState({ isActive: true }), 230);
    expect(normalizedRounds(free)).toEqual(free);
  });
});

describe('ChantCounter', () => {
  test('increment counts and rolls over at 108', () => {
    const first = inc(active);
    expect(first.state.count).toBe(1);
    expect(first.feedback).toBe('COUNT');
    const round = inc(incTimes(active, 107));
    expect(round.state.count).toBe(108);
    expect(round.state.rounds).toBe(1);
    expect(currentRoundCount(round.state)).toBe(0);
    expect(round.feedback).toBe('ROUND');
  });

  test('ignored when inactive, paused or goal complete', () => {
    expect(increment(chantState())).toBeNull();
    expect(increment({ ...active, isPaused: true })).toBeNull();
    expect(increment({ ...active, activeGoal: goal, currentMantraIndex: 1, mantraRoundsCompleted: 2 })).toBeNull();
  });

  test('goal advances through mantras and completes', () => {
    const start = { ...active, activeGoal: goal };
    const section = inc(incTimes(start, 107));
    expect(section.feedback).toBe('SECTION');
    expect(section.state.currentMantraIndex).toBe(1);
    expect(section.state.mantraRoundsCompleted).toBe(0);
    const midB = incTimes(section.state, 108);
    expect(midB.mantraRoundsCompleted).toBe(1);
    expect(isGoalComplete(midB)).toBe(false);
    const done = inc(incTimes(midB, 107));
    expect(done.feedback).toBe('GOAL');
    expect(isGoalComplete(done.state)).toBe(true);
    expect(done.state.count).toBe(324);
    expect(increment(done.state)).toBeNull();
  });

  test('debouncer drops fast repeats', () => {
    const d = new CountDebouncer(150);
    expect(d.tryAccept(1000)).toBe(true);
    expect(d.tryAccept(1040)).toBe(false);
    expect(d.tryAccept(1149)).toBe(false);
    expect(d.tryAccept(1150)).toBe(true);
    expect(d.tryAccept(2000)).toBe(true);
  });

  test('mala quarters on 108 and repeat each round', () => {
    const [, feedback] = press(active, 216);
    const milestones = feedback.flatMap((f, i) => (f === 'MILESTONE' ? [i + 1] : []));
    expect(milestones).toEqual([27, 54, 81, 135, 162, 189]);
    expect(feedback[107]).toBe('ROUND');
  });

  test('records carry a per-mantra breakdown that adds up', () => {
    const g: Goal = { ...goal, mantras: [{ id: '1', name: 'A', targetRounds: 1 }, { id: '2', name: 'B', targetRounds: 2 }] };
    const state = incTimes(startState(g, 0), 250);
    const record = toRecord(state, 1);
    expect(record.mantras).toEqual([
      { name: 'A', rounds: 1, counts: 108 },
      { name: 'B', rounds: 1, counts: 142 },
    ]);
    expect(toRecord({ ...startState(null, 0), count: 5 }, 1).mantras).toEqual([]);
  });
});
