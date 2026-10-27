#!/bin/sh
# Only the update hosts may be reached: INTERNET (and ACCESS_NETWORK_STATE, for expo-updates) are allowed, but the
# release build must trust TLS for the update hosts only, allow no cleartext, and request nothing beyond the Kotlin
# app's permissions. Run from expo/android after :app:assembleRelease.
set -eu
manifest=$(ls app/build/intermediates/merged_manifest/release/*/AndroidManifest.xml | head -1)
test -f "$manifest" || { echo "merged release manifest not found"; exit 1; }
allowed="android.permission.INTERNET android.permission.ACCESS_NETWORK_STATE android.permission.FOREGROUND_SERVICE android.permission.FOREGROUND_SERVICE_SPECIAL_USE android.permission.POST_NOTIFICATIONS android.permission.RECEIVE_BOOT_COMPLETED android.permission.VIBRATE com.superchant.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
for p in $(grep -o 'uses-permission[^>]*android:name="[^"]*"' "$manifest" | sed 's/.*android:name="//; s/"$//'); do
  case " $allowed " in *" $p "*) ;; *) echo "Unexpected permission $p in the release manifest"; exit 1 ;; esac
done
grep -q 'android:networkSecurityConfig="@xml/network_security_config"' "$manifest" \
  || { echo "release manifest has no network security config"; exit 1; }
if grep -q 'usesCleartextTraffic="true"' "$manifest"; then echo "cleartext traffic enabled"; exit 1; fi
nsc=app/src/release/res/xml/network_security_config.xml
hosts=$(grep -o '<domain[^>]*>[^<]*</domain>' "$nsc" | sed 's/<[^>]*>//g' | sort | tr '\n' ' ')
test "$hosts" = "assets.eascdn.net u.expo.dev " || { echo "trusted hosts are '$hosts'"; exit 1; }
grep -q '<base-config cleartextTrafficPermitted="false">' "$nsc" && grep -A1 '<base-config' "$nsc" | grep -q '<trust-anchors />' \
  || { echo "base config must trust no certificate authority"; exit 1; }
echo "network guard: ok"
