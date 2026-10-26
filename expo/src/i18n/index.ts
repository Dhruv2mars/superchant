/** String lookup with Android-style placeholders and English plural rules (the app ships in English). */
import { plurals, strings } from './strings';

export type StringKey = keyof typeof strings;
export type PluralKey = keyof typeof plurals;

function format(template: string, args: (string | number)[]): string {
  let next = 0;
  return template.replace(/%(?:(\d+)\$)?([sd])/g, (_, pos: string | undefined) => {
    const value = pos ? args[Number(pos) - 1] : args[next++];
    return String(value ?? '');
  });
}

export function t(key: StringKey, ...args: (string | number)[]): string {
  return format(strings[key], args);
}

/** Quantity string: `count` picks the form (one/other); `args` fill the placeholders (default: count). */
export function tp(key: PluralKey, count: number, ...args: (string | number)[]): string {
  const forms = plurals[key] as Record<string, string>;
  const form = (count === 1 ? forms.one : undefined) ?? forms.other ?? forms.one;
  return format(form, args.length ? args : [count]);
}
