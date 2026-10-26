/**
 * JSON between JS and the native engines. The engines write complete objects (kotlinx `encodeDefaults`, or
 * Swift Codable with every field), so the fast JSON.parse is enough here; stored data from elsewhere goes
 * through the kotlinx-compatible decoders in core/.
 */
import { stringifyKJson } from '@/core/kjson';
import { ChantState, chantState, encodeChantState, encodeGoal, Goal, SessionRecord } from '@/core/model';

export function parseSession(json: string): ChantState {
  return chantState(JSON.parse(json) as Partial<ChantState>);
}

export const parseHistory = (json: string) => JSON.parse(json) as SessionRecord[];
export const parseGoals = (json: string) => JSON.parse(json) as Goal[];

export const encodeBackupGoal = (goal: Goal) => stringifyKJson(encodeGoal(goal));
export const encodeSession = (state: ChantState) => stringifyKJson(encodeChantState(state));
