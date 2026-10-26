/** Fail-safe JSON lists (data/StoredList.kt): unreadable elements are kept and written back unchanged. */
import { DecodeError, isArray, KJson, KJsonSyntaxError, parseKJson, serializeKJson } from './kjson';
import { Goal, SessionRecord } from './model';

export interface StoredList<T> {
  items: T[];
  unreadable: KJson[];
  /** The stored string was not a JSON array at all. */
  corrupt: boolean;
}

export function decodeStoredList<T>(decode: (e: KJson) => T, stored: string | null | undefined): StoredList<T> {
  if (stored == null) return { items: [], unreadable: [], corrupt: false };
  let root: KJson;
  try {
    root = parseKJson(stored);
  } catch (e) {
    if (!(e instanceof KJsonSyntaxError)) throw e;
    return { items: [], unreadable: [], corrupt: true };
  }
  if (!isArray(root)) return { items: [], unreadable: [], corrupt: true };
  const items: T[] = [];
  const unreadable: KJson[] = [];
  for (const element of root.items) {
    try {
      items.push(decode(element));
    } catch (e) {
      if (!(e instanceof DecodeError)) throw e;
      unreadable.push(element);
    }
  }
  return { items, unreadable, corrupt: false };
}

/** Encodes items followed by the preserved unreadable elements, as kotlinx's JsonArray serializer writes them. */
export function encodeStoredList<T>(encode: (v: T) => KJson, items: T[], unreadable: KJson[]): string {
  return '[' + [...items.map(encode), ...unreadable].map(serializeKJson).join(',') + ']';
}

/**
 * Gives every item a unique ID; later duplicates get "<id>-<n>" with the smallest free n from 2, so the
 * same data always gets the same IDs (v1.1.0 used colliding currentTimeMillis IDs).
 */
export function withUniqueIds<T>(items: T[], id: (t: T) => string, withId: (t: T, id: string) => T): T[] {
  const seen = new Set<string>();
  const nextSuffix = new Map<string, number>();
  return items.map((item) => {
    const original = id(item);
    if (!seen.has(original)) {
      seen.add(original);
      return item;
    }
    let suffix = nextSuffix.get(original) ?? 2;
    while (seen.has(`${original}-${suffix}`)) suffix++;
    seen.add(`${original}-${suffix}`);
    nextSuffix.set(original, suffix + 1);
    return withId(item, `${original}-${suffix}`);
  });
}

export function withUniqueGoalIds(goals: Goal[]): Goal[] {
  return withUniqueIds(goals, (g) => g.id, (g, id) => ({ ...g, id })).map((g) => ({
    ...g,
    mantras: withUniqueIds(g.mantras, (m) => m.id, (m, id) => ({ ...m, id })),
  }));
}

export function withUniqueRecordIds(records: SessionRecord[]): SessionRecord[] {
  return withUniqueIds(records, (r) => r.id, (r, id) => ({ ...r, id }));
}

/** Adds record at the top, replacing any record with the same ID. History is never truncated. */
export function upsert(records: SessionRecord[], record: SessionRecord): SessionRecord[] {
  return [record, ...records.filter((r) => r.id !== record.id)];
}
