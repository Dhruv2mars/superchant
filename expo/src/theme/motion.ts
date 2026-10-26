/**
 * Motion tokens (ui/theme/Motion.kt): UI motion stays under 300 ms and starts fast. Things that appear or
 * answer a touch use EaseOutStrong; nothing uses ease-in. Reduced motion (the system setting) is honoured by
 * Reanimated's ReduceMotion.System default.
 */
import { Easing } from 'react-native-reanimated';

export const EaseOutStrong = Easing.bezier(0.23, 1, 0.32, 1);

export const durations = {
  /** Press-in shrink. */
  pressIn: 100,
  /** Settle back after a press. */
  pressOut: 160,
  /** Toggle fill in / out. */
  toggleOn: 180,
  toggleOff: 120,
  /** Small enter/exit fades. */
  fade: 200,
};

export const PRESS_SCALE = 0.95;
