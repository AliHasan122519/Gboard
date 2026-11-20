#!/usr/bin/env bash
# Publishes the current build as an in-app update: copies the APK to update/, writes update/latest.json
# (version, SHA-256, notes) and pushes both. Installed apps pick it up the next time they are opened.
#   1. bump versionCode / versionName in app/build.gradle.kts
#   2. tools/publish-update.sh "- what changed"
# The APK must be signed with the same key as the installed apps (see README).
set -euo pipefail
cd "$(dirname "$0")/.."
NOTES="${1:-}"
./gradlew --no-daemon -q assembleDebug
NAME=$(grep -m1 'versionName' app/build.gradle.kts | sed 's/.*"\(.*\)".*/\1/')
CODE=$(grep -m1 'versionCode' app/build.gradle.kts | sed 's/[^0-9]//g')
mkdir -p update
APK="update/MyKeyboard-$CODE.apk"
cp app/build/outputs/apk/debug/app-debug.apk "$APK"
SHA=$(sha256sum "$APK" | cut -d' ' -f1); SIZE=$(stat -c %s "$APK")
BRANCH=$(git rev-parse --abbrev-ref HEAD)
URL="https://raw.githubusercontent.com/AliHasan122519/Gboard/$BRANCH/$APK"
python3 - "$NAME" "$CODE" "$URL" "$SHA" "$SIZE" "$NOTES" <<'PY'
import json,sys
n,c,u,s,z,notes=sys.argv[1:7]
json.dump({"versionName":n,"versionCode":int(c),"apkUrl":u,"sha256":s,"size":int(z),"notes":notes},open("update/latest.json","w"),ensure_ascii=False,indent=1)
PY
git add update && git commit -m "Publish update $NAME ($CODE)" && git push origin "$BRANCH"
echo "Published $NAME: $URL"
