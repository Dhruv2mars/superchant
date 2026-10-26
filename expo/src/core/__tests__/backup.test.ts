// Ported from BackupTest.kt and StoredListTest.kt
import { describe, expect, test } from 'bun:test';
import {
  Backup,
  BackupFormatException,
  decodeBackup,
  emptyBackupSettings,
  encodeBackup,
  mergeGoals,
  mergeHistory,
} from '../backup';
import { parseKJson, stringifyKJson } from '../kjson';
import { nameUUIDFromString } from '../md5';
import { decodeGoal, decodeSessionRecord, encodeSessionRecord, Goal, SessionRecord } from '../model';
import { decodeStoredList, encodeStoredList, upsert, withUniqueGoalIds, withUniqueIds, withUniqueRecordIds } from '../storedList';

const goal: Goal = { id: 'g1', name: 'Morning', mantras: [{ id: 'm1', name: 'Om', targetRounds: 2 }], createdAt: 10 };
const record: SessionRecord = {
  id: 's1', startTime: 100, endTime: 700, totalCounts: 120, totalRounds: 2,
  goalId: 'g1', goalName: 'Morning', mantras: [{ name: 'Om', rounds: 2, counts: 120 }],
};
const backup: Backup = {
  app: 'SuperChant',
  format: 1,
  exportedAt: 1000,
  settings: { ...emptyBackupSettings, themeMode: 'DARK', dailyTarget: 4, beadClick: true },
  goals: [goal],
  history: [record, { ...record, id: 's2', startTime: 50, goalId: null, goalName: null, mantras: [] }],
};

describe('md5 / nameUUIDFromBytes', () => {
  test('matches java.util.UUID.nameUUIDFromBytes', () => {
    // MD5 test vectors with the version-3 and variant bits applied, as UUID.nameUUIDFromBytes does
    expect(nameUUIDFromString('')).toBe('d41d8cd9-8f00-3204-a980-0998ecf8427e');
    expect(nameUUIDFromString('The quick brown fox jumps over the lazy dog')).toBe('9e107d9d-372b-3682-abd8-1d3542a419d6');
  });
});

describe('BackupCodec', () => {
  test('round trip keeps everything', () => {
    const text = encodeBackup(backup);
    expect(decodeBackup(text)).toEqual(backup);
    expect(text).toContain('"id": "s1"');
  });

  test('legacy numeric ids import as strings', () => {
    const decoded = decodeBackup(`{"app":"SuperChant","format":1,
      "goals":[{"id":1690000000000,"name":"Old","mantras":[{"id":1690000000001,"name":"A","targetRounds":1}]}],
      "history":[{"id":1700000000000,"startTime":1,"endTime":2,"totalCounts":108,"totalRounds":1,"goalId":1690000000000}]}`);
    expect(decoded.goals[0].id).toBe('1690000000000');
    expect(decoded.history[0].id).toBe('1700000000000');
    expect(decoded.history[0].goalId).toBe('1690000000000');
  });

  test('exports with removed fields still import', () => {
    const decoded = decodeBackup(`{"app":"SuperChant","format":1,"exportedAt":1000,
      "settings":{"themeMode":"DARK","inputMode":"BOTH","roundSize":54,"dailyTarget":4,"pocketGuard":false,"beadClick":true},
      "goals":[{"id":"g1","name":"Morning","mantras":[{"id":"m1","name":"Om","targetRounds":2}],"createdAt":10,"roundSize":54}],
      "history":[{"id":"s1","startTime":100,"endTime":700,"totalCounts":120,"totalRounds":2,"goalId":"g1","goalName":"Morning",
                  "roundSize":54,"mantras":[{"name":"Om","rounds":2,"counts":120}]}]}`);
    expect(decoded.settings).toEqual({ ...emptyBackupSettings, themeMode: 'DARK', dailyTarget: 4, beadClick: true });
    expect(decoded.goals).toEqual([goal]);
    expect(decoded.history).toEqual([record]);
  });

  test('bad entries are skipped, not fatal', () => {
    const decoded = decodeBackup(`{"app":"SuperChant","format":1,"settings":"nonsense",
      "history":[{"id":"ok","startTime":1,"endTime":2,"totalCounts":1,"totalRounds":0},{"id":"bad"}],
      "goals":"not a list"}`);
    expect(decoded.history.map((r) => r.id)).toEqual(['ok']);
    expect(decoded.goals).toEqual([]);
    expect(decoded.settings).toEqual(emptyBackupSettings);
  });

  test('goals the editor could not make are skipped', () => {
    const text = `{"app":"SuperChant","format":1,"goals":[
      {"id":"ok","name":"Fine","mantras":[{"id":"m","name":"Om","targetRounds":999}]},
      {"id":"blank","name":"  ","mantras":[{"id":"m","name":"Om","targetRounds":1}]},
      {"id":"none","name":"No mantras","mantras":[]},
      {"id":"zero","name":"Zero","mantras":[{"id":"m","name":"Om","targetRounds":0}]},
      {"id":"huge","name":"Huge","mantras":[{"id":"m","name":"Om","targetRounds":1000}]},
      {"id":"unnamed","name":"Unnamed mantra","mantras":[{"id":"m","name":" ","targetRounds":1}]}]}`;
    expect(decodeBackup(text).goals.map((g) => g.id)).toEqual(['ok']);
  });

  test('entries without ids get stable ids so re-importing adds nothing', () => {
    const text = `{"app":"SuperChant","format":1,
      "goals":[{"name":"By hand","mantras":[{"name":"Om","targetRounds":1},{"id":null,"name":"Ram","targetRounds":2}]}],
      "history":[{"startTime":1,"endTime":2,"totalCounts":108,"totalRounds":1},
                 {"id":null,"startTime":3,"endTime":4,"totalCounts":5,"totalRounds":0},
                 {"startTime":1,"endTime":2,"totalCounts":108,"totalRounds":1}]}`;
    const first = decodeBackup(text);
    const second = decodeBackup(text);
    expect(new Set(first.history.map((r) => r.id)).size).toBe(3);
    expect(first.history).toEqual(second.history);
    expect(first.goals.map((g) => g.id)).toEqual(second.goals.map((g) => g.id));
    expect(first.goals[0].mantras).toEqual(second.goals[0].mantras);
    expect(mergeHistory(mergeHistory([], first.history), second.history).length).toBe(3);
    expect(mergeGoals(mergeGoals([], first.goals), second.goals).length).toBe(1);
  });

  test('rejects other files and newer formats', () => {
    for (const text of ['not json', '[]', '{"app":"Other","format":1}', '{"app":"SuperChant","format":99}']) {
      expect(() => decodeBackup(text)).toThrow(BackupFormatException);
    }
  });

  test('duplicate ids in a file are made unique', () => {
    const text = encodeBackup({ ...backup, history: [record, record] });
    expect(new Set(decodeBackup(text).history.map((r) => r.id)).size).toBe(2);
  });

  test('re-importing a file with duplicate ids adds nothing new', () => {
    const text = encodeBackup({ ...backup, history: [record, { ...record, startTime: 5 }] });
    const first = mergeHistory([], decodeBackup(text).history);
    const second = mergeHistory(first, decodeBackup(text).history);
    expect(first.length).toBe(2);
    expect(second).toEqual(first);
  });

  test('merge adds, updates and is idempotent', () => {
    const existing = [{ ...record, totalCounts: 5 }, { ...record, id: 'local', startTime: 900 }];
    const merged = mergeHistory(existing, backup.history);
    expect(merged.map((r) => r.id)).toEqual(['local', 's1', 's2']);
    expect(merged.find((r) => r.id === 's1')!.totalCounts).toBe(120);
    expect(mergeHistory(merged, backup.history)).toEqual(merged);
    const goals = mergeGoals([{ ...goal, name: 'Renamed' }, { ...goal, id: 'g2' }], [goal, { ...goal, id: 'g3' }]);
    expect(goals.map((g) => g.id)).toEqual(['g3', 'g1', 'g2']);
    expect(goals[1].name).toBe('Morning');
    expect(mergeGoals(goals, [goal, { ...goal, id: 'g3' }])).toEqual(goals);
  });

  test('restore into an empty app gives back the same data', () => {
    const restored = decodeBackup(encodeBackup(backup));
    expect(mergeHistory([], restored.history)).toEqual(backup.history);
    expect(mergeGoals([], restored.goals)).toEqual(backup.goals);
  });
});

describe('StoredList', () => {
  const legacyHistory = `[
    {"id":1700000000000,"startTime":1,"endTime":2,"totalCounts":108,"totalRounds":1,"goalId":1690000000000,"goalName":"Morning"},
    {"id":1700000000000,"startTime":3,"endTime":4,"totalCounts":5,"totalRounds":0,"goalId":null,"goalName":null}
  ]`;

  test('legacy numeric ids decode as strings', () => {
    const list = decodeStoredList(decodeSessionRecord, legacyHistory);
    expect(list.items.length).toBe(2);
    expect(list.unreadable).toEqual([]);
    expect(list.items[0].id).toBe('1700000000000');
    expect(list.items[0].goalId).toBe('1690000000000');
    const goals = decodeStoredList(decodeGoal, '[{"id":5,"name":"G","mantras":[{"id":7,"name":"M","targetRounds":3}],"createdAt":9}]').items;
    expect(goals[0].id).toBe('5');
    expect(goals[0].mantras[0].id).toBe('7');
  });

  test('duplicate ids are made unique', () => {
    const records = withUniqueRecordIds(decodeStoredList(decodeSessionRecord, legacyHistory).items);
    expect(records[0].id).toBe('1700000000000');
    expect(new Set(records.map((r) => r.id)).size).toBe(2);
    const mantra = { id: 'm', name: 'M', targetRounds: 1 };
    const goals = withUniqueGoalIds([
      { id: 'g', name: 'A', mantras: [mantra, mantra], createdAt: 0 },
      { id: 'g', name: 'B', mantras: [], createdAt: 0 },
    ]);
    expect(new Set(goals.map((g) => g.id)).size).toBe(2);
    expect(new Set(goals[0].mantras.map((m) => m.id)).size).toBe(2);
  });

  test('replacement ids match the reference rule and stay fast for many copies', () => {
    const reference = (ids: string[]) => {
      const seen = new Set<string>();
      return ids.map((original) => {
        let id = original;
        for (let n = 2; seen.has(id); n++) id = `${original}-${n}`;
        seen.add(id);
        return id;
      });
    };
    const unique = (ids: string[]) => withUniqueIds(ids, (s) => s, (_, id) => id);
    expect(unique(['g', 'g', 'g-2', 'g', 'g', 'g-2'])).toEqual(['g', 'g-2', 'g-2-2', 'g-3', 'g-4', 'g-2-3']);
    let seed = 7;
    const rand = (n: number) => ((seed = (seed * 1103515245 + 12345) % 2 ** 31), seed % n);
    const alphabet = ['a', 'a-2', 'a-3', 'a-2-2', 'b', 'b-2', 'a-2-3'];
    for (let i = 0; i < 500; i++) {
      const ids = Array.from({ length: 1 + rand(39) }, () => alphabet[rand(alphabet.length)]);
      expect(unique(ids)).toEqual(reference(ids));
    }
    const start = performance.now();
    const ids = unique(Array(10_000).fill('1700000000000'));
    expect(new Set(ids).size).toBe(10_000);
    expect(performance.now() - start).toBeLessThan(300);
  });

  test('undecodable elements are preserved on write', () => {
    const list = decodeStoredList(decodeSessionRecord, '[{"id":"a","startTime":1,"endTime":2,"totalCounts":1,"totalRounds":0},{"id":"broken"}]');
    expect(list.items.map((r) => r.id)).toEqual(['a']);
    expect(list.unreadable.length).toBe(1);
    const b: SessionRecord = { id: 'b', startTime: 5, endTime: 6, totalCounts: 2, totalRounds: 0, goalId: null, goalName: null, mantras: [] };
    const written = encodeStoredList(encodeSessionRecord, upsert(list.items, b), list.unreadable);
    const elements = parseKJson(written);
    expect(elements.kind === 'array' && elements.items.length).toBe(3);
    expect(elements.kind === 'array' && stringifyKJson(elements.items[2])).toBe('{"id":"broken"}');
  });

  test('non-array data is flagged corrupt', () => {
    const list = decodeStoredList(decodeSessionRecord, 'not json');
    expect(list.corrupt).toBe(true);
    expect(list.items).toEqual([]);
    expect(decodeStoredList(decodeSessionRecord, null).corrupt).toBe(false);
  });

  test('upsert replaces the same session instead of duplicating', () => {
    const first: SessionRecord = { id: 's', startTime: 0, endTime: 10, totalCounts: 5, totalRounds: 0, goalId: null, goalName: null, mantras: [] };
    const other = { ...first, id: 'o', endTime: 1, totalCounts: 1 };
    const history = upsert([first, other], { ...first, endTime: 20, totalCounts: 9 });
    expect(history.map((r) => r.id)).toEqual(['s', 'o']);
    expect(history[0].totalCounts).toBe(9);
  });

  test('ten years of history stays small and fast to decode', () => {
    const records: SessionRecord[] = Array.from({ length: 11_000 }, (_, i) => ({
      id: crypto.randomUUID(),
      startTime: 1_700_000_000_000 + i * 28_800_000,
      endTime: 1_700_000_000_000 + i * 28_800_000 + 900_000,
      totalCounts: 1080,
      totalRounds: 10,
      goalId: i % 2 === 0 ? crypto.randomUUID() : null,
      goalName: i % 2 === 0 ? 'Morning practice' : null,
      mantras: [],
    }));
    const encoded = encodeStoredList(encodeSessionRecord, records, []);
    const start = performance.now();
    const decoded = decodeStoredList(decodeSessionRecord, encoded).items;
    const ms = performance.now() - start;
    console.log(`history: ${Math.round(encoded.length / 1024)} KiB for ${records.length} sessions, decode ${ms.toFixed(0)} ms`);
    expect(decoded).toEqual(records);
    expect(encoded.length / records.length).toBeLessThan(220);
  });
});
