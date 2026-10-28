/**
 * Android's build-number easter egg: 7 taps in a row unlock something hidden. A pause longer than `TAP_GAP_MS`
 * starts the count again, so stray taps spread over a visit never add up.
 */
export const SECRET_TAPS = 7;
export const TAP_GAP_MS = 1500;
/** Like Android, the countdown is shown only for the last few taps. */
export const SHOW_COUNTDOWN_FROM = 4;

export type TapStreak = { taps: number; lastAt: number };

export const NO_TAPS: TapStreak = { taps: 0, lastAt: 0 };

/** Adds a tap at `now`; `remaining` is 0 on the tap that completes the streak, which then starts over. */
export function secretTap(streak: TapStreak, now: number): { streak: TapStreak; remaining: number } {
  const taps = (streak.taps > 0 && now - streak.lastAt <= TAP_GAP_MS ? streak.taps : 0) + 1;
  const remaining = SECRET_TAPS - taps;
  return { streak: remaining === 0 ? NO_TAPS : { taps, lastAt: now }, remaining };
}
