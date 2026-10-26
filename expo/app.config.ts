import type { ConfigContext, ExpoConfig } from 'expo/config';

// Date-based versions, the same scheme as the Kotlin app (app/build.gradle.kts): versionName 2.YYYY.DDD
// (DDD = day of year in IST) and versionCode YYDDDNN, so a build of this app is a normal Play update.
// RELEASE_DATE=YYYY-MM-DD and BUILD_NUMBER=NN pin them.
function istToday(): string {
  return new Date(Date.now() + 5.5 * 3600_000).toISOString().slice(0, 10);
}
const releaseDate = process.env.RELEASE_DATE ?? istToday();
const buildNumber = Number(process.env.BUILD_NUMBER ?? 0);
if (!Number.isInteger(buildNumber) || buildNumber < 0 || buildNumber > 99) throw new Error('BUILD_NUMBER must be 0-99');
const [year, month, day] = releaseDate.split('-').map(Number);
const dayOfYear = Math.round((Date.UTC(year, month - 1, day) - Date.UTC(year, 0, 0)) / 86_400_000);
const versionName = `2.${year}.${String(dayOfYear).padStart(3, '0')}`;
const versionCode = (year % 100) * 100_000 + dayOfYear * 100 + buildNumber;

// EAS project (expo.dev). Updates are the app's only network use; see AGENTS.md.
const easProjectId = process.env.EAS_PROJECT_ID ?? '';

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: 'SuperChant',
  slug: 'superchant',
  owner: process.env.EAS_OWNER,
  scheme: 'superchant',
  version: versionName,
  orientation: 'default',
  icon: './assets/icon.png',
  userInterfaceStyle: 'automatic',
  backgroundColor: '#FFFBF8',
  runtimeVersion: { policy: 'fingerprint' },
  updates: {
    enabled: easProjectId !== '',
    url: easProjectId ? `https://u.expo.dev/${easProjectId}` : undefined,
    checkAutomatically: 'ON_LOAD',
    fallbackToCacheTimeout: 0,
    requestHeaders: {
      'expo-channel-name': process.env.EAS_UPDATE_CHANNEL ?? 'production',
      // expo-updates would send the text of the previous launch's fatal error to the update server; SuperChant
      // sends no crash reports, so this header (applied after the built-in one) blanks it.
      'Expo-Fatal-Error': '',
    },
  },
  extra: easProjectId ? { eas: { projectId: easProjectId } } : {},
  ios: {
    bundleIdentifier: 'com.superchant',
    buildNumber: String(versionCode),
    supportsTablet: true,
    infoPlist: {
      ITSAppUsesNonExemptEncryption: false,
      NSSupportsLiveActivities: true,
    },
  },
  android: {
    package: 'com.superchant',
    versionCode,
    icon: './assets/icon-android.png',
    predictiveBackGestureEnabled: true,
    allowBackup: true,
    blockedPermissions: [
      'android.permission.SYSTEM_ALERT_WINDOW',
      'android.permission.READ_EXTERNAL_STORAGE',
      'android.permission.WRITE_EXTERNAL_STORAGE',
    ],
  },
  plugins: [
    'expo-router',
    'expo-localization',
    'expo-sharing',
    [
      'expo-splash-screen',
      {
        image: './assets/splash-icon.png',
        imageWidth: 120,
        backgroundColor: '#FFFBF8',
        dark: { image: './assets/splash-icon.png', backgroundColor: '#16130F' },
      },
    ],
    [
      'expo-build-properties',
      {
        android: {
          minSdkVersion: 26,
          enableMinifyInReleaseBuilds: true,
          enableShrinkResourcesInReleaseBuilds: true,
        },
        ios: { deploymentTarget: '17.0' },
      },
    ],
    './plugins/withSuperChantAndroid',
  ],
  experiments: { typedRoutes: true },
});
