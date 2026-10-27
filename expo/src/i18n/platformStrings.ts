import type { strings } from './strings';

/** Per-platform wording; Metro picks platformStrings.ios.ts on iOS. Android uses the base strings. */
export const platformStrings: Partial<Record<keyof typeof strings, string>> = {};
