#!/bin/sh
# Builds the Play bundle on this Mac and uploads it to Play internal testing with EAS Submit. Needed only when
# the runtime fingerprint changed (native code, config plugins, native dependencies); JavaScript-only changes
# ship as updates from CI. A clean local build is ~9 min against ~24 min on EAS Build's free tier.
#
# Needs the upload key in the repo-root local.properties (RELEASE_*) and the Play service account JSON
# (PLAY_SERVICE_ACCOUNT_KEY, default ~/.superchant-keys/play-service-account.json), plus uv for the version lookup.
set -eu
cd "$(dirname "$0")/.."
key=${PLAY_SERVICE_ACCOUNT_KEY:-$HOME/.superchant-keys/play-service-account.json}
test -f "$key" || { echo "Play service account key not found: $key"; exit 1; }
grep -q '^RELEASE_STORE_FILE=' ../local.properties 2>/dev/null || { echo "no RELEASE_* signing keys in ../local.properties"; exit 1; }

# versionCode YYDDDNN (IST day): NN is one above the highest code Play has for the day
export RELEASE_DATE
RELEASE_DATE=$(TZ=Asia/Kolkata date +%F)
day=$(TZ=Asia/Kolkata date +%y%j)
BUILD_NUMBER=$(PLAY_SERVICE_ACCOUNT_JSON=$(cat "$key") uv run --quiet --no-project --with google-auth --with requests \
  python3 ../scripts/play_version.py com.superchant "$day")
export BUILD_NUMBER
echo "Building versionCode $day$(printf %02d "$BUILD_NUMBER")"

bunx expo prebuild --platform android --clean
echo "sdk.dir=${ANDROID_HOME:-$HOME/Library/Android/sdk}" > android/local.properties
(cd android && ./gradlew :app:bundleRelease && ../scripts/network-guard.sh)

# eas.json points at ./play-service-account.json; link it only for the upload
ln -sf "$key" play-service-account.json
trap 'rm -f play-service-account.json' EXIT
bunx eas-cli@26 submit --platform android --profile production --non-interactive \
  --path android/app/build/outputs/bundle/release/app-release.aab
