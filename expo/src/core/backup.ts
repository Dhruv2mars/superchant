/**
 * Backup files (data/Backup.kt): a human-readable copy of settings, goals and history. The format is
 * shared with the Kotlin app, so a file exported on either platform imports on the other.
 */
import {
  contentOrNull,
  DecodeError,
  isArray,
  isObject,
  KJson,
  kArray,
  kBool,
  KJsonSyntaxError,
  KNull,
  kNumber,
  kObject,
  kString,
  parseKJson,
  prettyKJson,
  stringifyKJson,
} from './kjson';
import { nameUUIDFromString } from './md5';
import {
  decodeBoolean,
  decodeGoal,
  decodeInt,
  decodeSessionRecord,
  decodeString,
  encodeGoal,
  encodeSessionRecord,
  Goal,
  isGoalValid,
  SessionRecord,
} from './model';
import { withUniqueGoalIds, withUniqueRecordIds } from './storedList';

export const BACKUP_APP = 'SuperChant';
export const BACKUP_FORMAT = 1;

/** Settings worth carrying to a new phone. Null leaves the current value unchanged on import. */
export interface BackupSettings {
  themeMode: string | null;
  dailyTarget: number | null;
  countVibration: boolean | null;
  countIntensity: number | null;
  roundVibration: boolean | null;
  roundIntensity: number | null;
  milestonePulse: boolean | null;
  beadClick: boolean | null;
  reminderEnabled: boolean | null;
  reminderMinuteOfDay: number | null;
}

export const emptyBackupSettings: BackupSettings = {
  themeMode: null,
  dailyTarget: null,
  countVibration: null,
  countIntensity: null,
  roundVibration: null,
  roundIntensity: null,
  milestonePulse: null,
  beadClick: null,
  reminderEnabled: null,
  reminderMinuteOfDay: null,
};

export interface Backup {
  app: string;
  format: number;
  exportedAt: number;
  settings: BackupSettings;
  goals: Goal[];
  history: SessionRecord[];
}

export class BackupFormatException extends Error {}

export interface ImportResult {
  sessionsAdded: number;
  goalsAdded: number;
  goalsUpdated: number;
}

const SETTING_KEYS: { key: keyof BackupSettings; type: 'string' | 'int' | 'bool' }[] = [
  { key: 'themeMode', type: 'string' },
  { key: 'dailyTarget', type: 'int' },
  { key: 'countVibration', type: 'bool' },
  { key: 'countIntensity', type: 'int' },
  { key: 'roundVibration', type: 'bool' },
  { key: 'roundIntensity', type: 'int' },
  { key: 'milestonePulse', type: 'bool' },
  { key: 'beadClick', type: 'bool' },
  { key: 'reminderEnabled', type: 'bool' },
  { key: 'reminderMinuteOfDay', type: 'int' },
];

function encodeSettings(s: BackupSettings): KJson {
  return kObject(
    SETTING_KEYS.map(({ key, type }) => {
      const v = s[key];
      if (v == null) return [key, KNull];
      return [key, type === 'string' ? kString(v as string) : type === 'int' ? kNumber(v as number) : kBool(v as boolean)];
    }),
  );
}

function decodeSettings(e: KJson): BackupSettings {
  if (!isObject(e)) throw new DecodeError('settings');
  const out: BackupSettings = { ...emptyBackupSettings };
  for (const { key, type } of SETTING_KEYS) {
    const v = e.entries.get(key);
    if (v == null || v.kind === 'null') continue;
    (out as unknown as Record<string, unknown>)[key] =
      type === 'string' ? decodeString(v) : type === 'int' ? decodeInt(v) : decodeBoolean(v);
  }
  return out;
}

export function encodeBackup(b: Backup): string {
  return prettyKJson(
    kObject([
      ['app', kString(b.app)],
      ['format', kNumber(b.format)],
      ['exportedAt', kNumber(b.exportedAt)],
      ['settings', encodeSettings(b.settings)],
      ['goals', kArray(b.goals.map(encodeGoal))],
      ['history', kArray(b.history.map(encodeSessionRecord))],
    ]),
  );
}

/** An entry without an ID gets one derived from its content, so re-importing the file updates it. */
function withContentId(e: KJson): KJson {
  if (!isObject(e)) return e;
  const id = e.entries.get('id');
  if (id != null && id.kind !== 'null') return e;
  const entries = new Map(e.entries);
  entries.set('id', kString(nameUUIDFromString(stringifyKJson(e))));
  return { kind: 'object', entries };
}

function decodeEach<T>(element: KJson | undefined, decode: (e: KJson) => T | null): T[] {
  if (!isArray(element)) return [];
  const out: T[] = [];
  for (const item of element.items) {
    try {
      const v = decode(item);
      if (v != null) out.push(v);
    } catch (e) {
      if (!(e instanceof DecodeError)) throw e;
    }
  }
  return out;
}

function toLongOrNull(s: string | null): number | null {
  if (s == null || !/^[+-]?\d+$/.test(s)) return null;
  const v = BigInt(s);
  if (v < -9223372036854775808n || v > 9223372036854775807n) return null;
  return Number(v);
}

function toIntOrNull(s: string | null): number | null {
  const v = toLongOrNull(s);
  return v == null || v < -2147483648 || v > 2147483647 ? null : v;
}

/**
 * Reads a backup, skipping goals or sessions that don't decode (or goals the editor couldn't make)
 * instead of rejecting the file. Throws BackupFormatException for other files and newer formats.
 */
export function decodeBackup(text: string): Backup {
  let root: KJson;
  try {
    root = parseKJson(text);
  } catch (e) {
    if (!(e instanceof KJsonSyntaxError)) throw e;
    throw new BackupFormatException('Not a JSON object');
  }
  if (!isObject(root)) throw new BackupFormatException('Not a JSON object');
  const r = root.entries;
  const primContent = (k: string) => {
    const v = r.get(k);
    if (v != null && v.kind !== 'prim' && v.kind !== 'null') throw new BackupFormatException(`Bad ${k}`);
    return contentOrNull(v);
  };
  if (primContent('app') !== BACKUP_APP) throw new BackupFormatException('Not a SuperChant backup');
  const format = toIntOrNull(primContent('format'));
  if (format == null) throw new BackupFormatException('Missing format');
  if (format > BACKUP_FORMAT) throw new BackupFormatException(`Made by a newer version (format ${format})`);

  let settings = { ...emptyBackupSettings };
  const s = r.get('settings');
  if (s) {
    try {
      settings = decodeSettings(s);
    } catch (e) {
      if (!(e instanceof DecodeError)) throw e;
    }
  }
  let exportedAt = 0;
  const ex = r.get('exportedAt');
  if (ex != null && ex.kind !== 'prim' && ex.kind !== 'null') throw new BackupFormatException('Bad exportedAt');
  exportedAt = toLongOrNull(contentOrNull(ex)) ?? 0;

  return {
    app: BACKUP_APP,
    format,
    exportedAt,
    settings,
    goals: withUniqueGoalIds(
      decodeEach(r.get('goals'), (goal) => {
        let withIds = withContentId(goal);
        if (isObject(withIds)) {
          const mantras = withIds.entries.get('mantras');
          if (isArray(mantras)) {
            const entries = new Map(withIds.entries);
            entries.set('mantras', kArray(mantras.items.map(withContentId)));
            withIds = { kind: 'object', entries };
          }
        }
        const decoded = decodeGoal(withIds);
        return isGoalValid(decoded) ? decoded : null;
      }),
    ),
    history: withUniqueRecordIds(decodeEach(r.get('history'), (e) => decodeSessionRecord(withContentId(e)))),
  };
}

/** Sessions from both, newest first; on an ID clash the imported copy wins. Nothing is dropped. */
export function mergeHistory(existing: SessionRecord[], imported: SessionRecord[]): SessionRecord[] {
  const importedIds = new Set(imported.map((r) => r.id));
  // Stable sort, like Kotlin's sortedByDescending
  return [...imported, ...existing.filter((r) => !importedIds.has(r.id))]
    .map((r, i) => [r, i] as const)
    .sort((a, b) => b[0].startTime - a[0].startTime || a[1] - b[1])
    .map(([r]) => r);
}

/** Imported goals replace goals with the same ID in place; new ones go on top in file order. */
export function mergeGoals(existing: Goal[], imported: Goal[]): Goal[] {
  const byId = new Map(imported.map((g) => [g.id, g]));
  const existingIds = new Set(existing.map((g) => g.id));
  return [...imported.filter((g) => !existingIds.has(g.id)), ...existing.map((g) => byId.get(g.id) ?? g)];
}

export function suggestedFileName(date: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `superchant-backup-${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}.json`;
}
