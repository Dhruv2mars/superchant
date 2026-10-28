/**
 * Early updates: one store build, two update channels. Everyone gets `production` (published from `main`); a
 * tester's install can switch itself to `preview` (published from `dev`) from Settings, so the owner tests on
 * the Play install instead of a second app. expo-updates saves the header override on the device, so it lasts
 * across launches. Only keys declared in app.config.ts `updates.requestHeaders` may be overridden, and the
 * override replaces that whole map, so it repeats the blanked `Expo-Fatal-Error` header.
 */
import * as Updates from 'expo-updates';

export const EARLY_CHANNEL = 'preview';

/** The channel this launch asked for updates on, or null when updates are off (dev builds, no EAS project). */
export function currentChannel(): string | null {
  return Updates.isEnabled ? Updates.channel : null;
}

/** First 7 characters of the running OTA update, or null when running the bundle embedded in the build. */
export function runningUpdate(): string | null {
  return Updates.isEnabled && !Updates.isEmbeddedLaunch && Updates.updateId ? Updates.updateId.slice(0, 7) : null;
}

export type SwitchResult = 'reloading' | 'up-to-date' | 'offline';

/** Moves this install to the preview channel (or back to the build's own), then loads that channel's newest update. */
export async function setEarlyUpdates(on: boolean): Promise<SwitchResult> {
  Updates.setUpdateRequestHeadersOverride(on ? { 'expo-channel-name': EARLY_CHANNEL, 'Expo-Fatal-Error': '' } : null);
  try {
    const result = await Updates.fetchUpdateAsync();
    if (result.isNew || result.isRollBackToEmbedded) {
      await Updates.reloadAsync();
      return 'reloading';
    }
    return 'up-to-date';
  } catch {
    // Saved anyway; the next launch checks the new channel
    return 'offline';
  }
}
