#!/usr/bin/env bash
# Builds the signed release APK and prints what to publish on GitHub.
#   1. bump versionCode / versionName in app/build.gradle.kts
#   2. tools/release.sh
#   3. On GitHub: Releases -> Draft a new release -> tag v<versionName> -> attach the APK -> Publish
# Installed apps check "latest release", compare the tag with their versionName and offer the APK.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f keystore.properties ] || { echo "No keystore.properties - run tools/make-keystore.sh first."; exit 1; }
./gradlew --no-daemon clean assembleRelease testDebugUnitTest
APK=app/build/outputs/apk/release/app-release.apk
VER=$(grep -m1 'versionName' app/build.gradle.kts | sed 's/.*"\(.*\)".*/\1/')
OUT="MyKeyboard-$VER.apk"
cp "$APK" "$OUT"
"${ANDROID_HOME:-/opt/android-sdk}/build-tools/35.0.0/apksigner" verify --print-certs "$OUT" | head -3
sha256sum "$OUT"
echo "Publish tag v$VER on GitHub and attach $OUT"
