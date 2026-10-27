/** String lookup with Android-style placeholders and English plural rules (the app ships in English). */
import { platformStrings } from './platformStrings';
import { plurals, strings } from './strings';

export type StringKey = keyof typeof strings;
export type PluralKey = keyof typeof plurals;

const grouped = new Intl.NumberFormat();

/** Android format placeholders: %s, %d, positional %1$s, and the grouping flag %,d (1,234). */
export function format(template: string, args: (string | number)[]): string {
  let next = 0;
  return template.replace(/%(?:(\d+)\$)?(,?)([sd])/g, (_, pos: string | undefined, group: string) => {
    const value = pos ? args[Number(pos) - 1] : args[next++];
    if (group && typeof value === 'number') return grouped.format(value);
    return String(value ?? '');
  });
}

export function t(key: StringKey, ...args: (string | number)[]): string {
  return format(platformStrings[key] ?? strings[key], args);
}

/** Quantity string: `count` picks the form (one/other); `args` fill the placeholders (default: count). */
export function tp(key: PluralKey, count: number, ...args: (string | number)[]): string {
  const forms = plurals[key] as Record<string, string>;
  const form = (count === 1 ? forms.one : undefined) ?? forms.other ?? forms.one;
  return format(form, args.length ? args : [count]);
}
