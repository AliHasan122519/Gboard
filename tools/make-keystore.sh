#!/usr/bin/env bash
# Creates the release signing key ONCE. Every future update must be signed with this same key,
# otherwise Android refuses to install it over the existing app. Back the file up somewhere safe
# and never commit it (keystore.properties and *.jks are git-ignored).
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -f keystore.properties ]; then echo "keystore.properties already exists - refusing to overwrite."; exit 1; fi
PASS=$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)
keytool -genkeypair -v -keystore release.jks -alias mykeyboard -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=My Keyboard, O=My Keyboard, C=SA"
cat > keystore.properties <<PROPS
storeFile=release.jks
storePassword=$PASS
keyAlias=mykeyboard
keyPassword=$PASS
PROPS
chmod 600 release.jks keystore.properties
echo "Created release.jks and keystore.properties. BACK THEM UP (losing them means users must uninstall to update)."
