// Android project settings the Kotlin app had (app/build.gradle.kts, AndroidManifest.xml, res/), applied to the
// generated android/ project: the "SuperChant Dev" debug package next to the Play app, launcher shortcuts, backup
// rules, release signing from the untracked local.properties, the exact launcher icon vectors, and a release-only
// network lockdown that lets TLS reach the update servers and nothing else.
const { withAppBuildGradle, withAndroidManifest, withDangerousMod, AndroidConfig } = require('expo/config-plugins');
const fs = require('fs');
const path = require('path');

/** Hosts expo-updates talks to: the manifest server and the asset CDN. */
const UPDATE_HOSTS = ['u.expo.dev', 'assets.eascdn.net'];

const shortcutsXml = (pkg) => `<?xml version="1.0" encoding="utf-8"?>
<!-- Long-press launcher shortcut; goal shortcuts are added at runtime (AppShortcuts). -->
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
    <shortcut
        android:enabled="true"
        android:icon="@drawable/ic_shortcut_start"
        android:shortcutId="start_counting"
        android:shortcutLongLabel="@string/shortcut_start_long"
        android:shortcutShortLabel="@string/shortcut_start_short">
        <intent
            android:action="com.superchant.action.START_COUNTING"
            android:targetClass="com.superchant.MainActivity"
            android:targetPackage="${pkg}" />
    </shortcut>
</shortcuts>
`;

const backupInclude = `        <include domain="sharedpref" path="." />
        <include domain="file" path="datastore/" />`;

const FILES = {
  'src/main/res/xml/shortcuts.xml': shortcutsXml('com.superchant'),
  'src/debug/res/xml/shortcuts.xml': shortcutsXml('com.superchant.dev'),
  'src/debug/res/values/strings_dev.xml': `<?xml version="1.0" encoding="utf-8"?>
<!-- Debug builds install as com.superchant.dev next to the Play Store app; label them so they're easy to tell apart. -->
<resources>
    <string name="app_name">SuperChant Dev</string>
    <string name="accessibility_service_label">SuperChant Dev Volume Counter</string>
</resources>
`,
  'src/main/res/xml/backup_rules.xml': `<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
${backupInclude.replace(/^ {4}/gm, '')}
</full-backup-content>
`,
  'src/main/res/xml/data_extraction_rules.xml': `<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
${backupInclude}
    </cloud-backup>
    <device-transfer>
${backupInclude}
    </device-transfer>
</data-extraction-rules>
`,
  // Release only: no certificate authority is trusted except for the update hosts, so HTTPS to any other
  // host fails, and cleartext is off everywhere. Debug builds keep the defaults to reach Metro.
  'src/release/res/xml/network_security_config.xml': `<?xml version="1.0" encoding="utf-8"?>
<!-- SuperChant goes online only to check for and download updates (EAS Update). -->
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors />
    </base-config>
    <domain-config cleartextTrafficPermitted="false">
${UPDATE_HOSTS.map((h) => `        <domain includeSubdomains="false">${h}</domain>`).join('\n')}
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </domain-config>
</network-security-config>
`,
  'src/release/AndroidManifest.xml': `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:networkSecurityConfig="@xml/network_security_config" />
</manifest>
`,
};

// The Kotlin app's adaptive launcher icon vectors (copied from app/src/main/res), so the icon is identical.
const ICON_DIR = path.join(__dirname, '..', 'assets', 'android');
const ICON_FILES = [
  'drawable/ic_launcher_background.xml',
  'drawable/ic_launcher_foreground.xml',
  'drawable/ic_launcher_monochrome.xml',
  'mipmap-anydpi-v26/ic_launcher.xml',
  'mipmap-anydpi-v26/ic_launcher_round.xml',
];

function withFiles(config) {
  return withDangerousMod(config, [
    'android',
    (cfg) => {
      const appDir = path.join(cfg.modRequest.platformProjectRoot, 'app');
      for (const [rel, text] of Object.entries(FILES)) {
        const file = path.join(appDir, rel);
        fs.mkdirSync(path.dirname(file), { recursive: true });
        fs.writeFileSync(file, text);
      }
      for (const rel of ICON_FILES) {
        const src = path.join(ICON_DIR, rel);
        const dest = path.join(appDir, 'src/main/res', rel);
        fs.mkdirSync(path.dirname(dest), { recursive: true });
        fs.copyFileSync(src, dest);
      }
      // Expo also writes webp foregrounds for the adaptive icon; the vector one above replaces them
      for (const dir of fs.readdirSync(path.join(appDir, 'src/main/res'))) {
        if (!dir.startsWith('mipmap-')) continue;
        for (const f of ['ic_launcher_foreground.webp', 'ic_launcher_background.webp', 'ic_launcher_monochrome.webp']) {
          fs.rmSync(path.join(appDir, 'src/main/res', dir, f), { force: true });
        }
      }
      return cfg;
    },
  ]);
}

function withManifest(config) {
  return withAndroidManifest(config, (cfg) => {
    const app = AndroidConfig.Manifest.getMainApplicationOrThrow(cfg.modResults);
    app.$['android:dataExtractionRules'] = '@xml/data_extraction_rules';
    app.$['android:fullBackupContent'] = '@xml/backup_rules';
    app.$['android:icon'] = '@mipmap/ic_launcher';
    app.$['android:roundIcon'] = '@mipmap/ic_launcher_round';
    const activity = AndroidConfig.Manifest.getMainActivityOrThrow(cfg.modResults);
    activity['meta-data'] = (activity['meta-data'] ?? []).filter((m) => m.$['android:name'] !== 'android.app.shortcuts');
    activity['meta-data'].push({ $: { 'android:name': 'android.app.shortcuts', 'android:resource': '@xml/shortcuts' } });
    return cfg;
  });
}

const SIGNING = `
// SuperChant: release signing keys live in the untracked repo-root local.properties (RELEASE_*); without them
// the release build is signed with the debug key so it still installs locally.
def superchantProps = new Properties()
def superchantPropsFile = rootProject.file("../../local.properties")
if (superchantPropsFile.exists()) { superchantPropsFile.withInputStream { superchantProps.load(it) } }
`;

function withGradle(config) {
  return withAppBuildGradle(config, (cfg) => {
    let s = cfg.modResults.contents;
    if (!s.includes('superchantProps')) {
      s = s.replace(/\nandroid \{/, `${SIGNING}\nandroid {`);
      s = s.replace(
        /signingConfigs \{/,
        `signingConfigs {
        if (superchantProps.getProperty("RELEASE_STORE_FILE")) {
            release {
                storeFile file(superchantProps.getProperty("RELEASE_STORE_FILE"))
                storePassword superchantProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias superchantProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword superchantProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }`,
      );
      // Release: the upload key when configured, else the debug key
      s = s.replace(
        /(release \{[^{}]*?)signingConfig signingConfigs\.debug/,
        '$1signingConfig signingConfigs.findByName("release") ?: signingConfigs.debug',
      );
      // "SuperChant Dev" installs next to the Play Store app, so testing never touches real practice data
      s = s.replace(/buildTypes \{\s*debug \{/, 'buildTypes {\n        debug {\n            applicationIdSuffix ".dev"\n            versionNameSuffix "-dev"');
    }
    cfg.modResults.contents = s;
    return cfg;
  });
}

module.exports = (config) => withGradle(withManifest(withFiles(config)));
