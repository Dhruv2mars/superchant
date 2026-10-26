import { describe, expect, test } from 'bun:test';
import { format, t, tp } from '..';
import { plurals, strings } from '../strings';

describe('string formatting', () => {
  test('Android placeholders', () => {
    expect(format('%d of %d', [3, 4])).toBe('3 of 4');
    expect(format('%2$d / %1$d', [3, 4])).toBe('4 / 3');
    expect(format('%,d counts', [12345])).toMatch(/^12.345 counts$/);
    expect(tp('rounds_count', 1)).toBe('1 round');
    expect(tp('rounds_count', 3)).toBe('3 rounds');
  });

  test('every string and plural formats without a leftover placeholder', () => {
    const args = [1234, 5, 'x'];
    for (const key of Object.keys(strings) as (keyof typeof strings)[]) {
      expect(t(key, ...args)).not.toMatch(/%[\d$,]*[sd]/);
    }
    for (const key of Object.keys(plurals) as (keyof typeof plurals)[]) {
      expect(tp(key, 2, ...args)).not.toMatch(/%[\d$,]*[sd]/);
    }
  });
});
