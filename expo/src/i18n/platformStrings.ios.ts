import type { strings } from './strings';

/** iPhone wording where the Android text talks about hardware iOS doesn't use for counting. */
export const platformStrings: Partial<Record<keyof typeof strings, string>> = {
  bead_click_desc: 'A quiet click on each count. Silent when your iPhone is on silent.',
  settings_vibrate_count_desc: 'A light tap for every count',
};
