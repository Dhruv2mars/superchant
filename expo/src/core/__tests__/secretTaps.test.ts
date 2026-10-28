import { describe, expect, test } from 'bun:test';
import { NO_TAPS, secretTap, SECRET_TAPS, TAP_GAP_MS, TapStreak } from '../secretTaps';

function tapAt(times: number[], start: TapStreak = NO_TAPS) {
  let streak = start;
  const remaining: number[] = [];
  for (const now of times) {
    const next = secretTap(streak, now);
    streak = next.streak;
    remaining.push(next.remaining);
  }
  return { streak, remaining };
}

describe('secretTap', () => {
  test('seven quick taps count down to zero', () => {
    const { remaining, streak } = tapAt([1000, 1200, 1400, 1600, 1800, 2000, 2200]);
    expect(remaining).toEqual([6, 5, 4, 3, 2, 1, 0]);
    expect(streak).toEqual(NO_TAPS);
  });

  test('a pause longer than the gap starts over', () => {
    const { remaining } = tapAt([1000, 1200, 1400, 1400 + TAP_GAP_MS + 1, 3000]);
    expect(remaining).toEqual([6, 5, 4, 6, 5]);
  });

  test('a pause of exactly the gap still counts', () => {
    const { remaining } = tapAt([1000, 1000 + TAP_GAP_MS]);
    expect(remaining).toEqual([6, 5]);
  });

  test('after unlocking, the next taps start a new streak', () => {
    const times = Array.from({ length: SECRET_TAPS + 2 }, (_, i) => 1000 + i * 100);
    expect(tapAt(times).remaining).toEqual([6, 5, 4, 3, 2, 1, 0, 6, 5]);
  });

  test('the first tap ever counts even at time zero', () => {
    expect(secretTap(NO_TAPS, 0).remaining).toBe(SECRET_TAPS - 1);
  });
});
