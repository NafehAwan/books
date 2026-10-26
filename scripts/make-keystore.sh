#!/usr/bin/env bash
# Creates your private release signing key (release.jks) and keystore.properties in the
# project root. Both are in .gitignore: NEVER commit them, and back them up somewhere safe.
# If you lose this key you cannot update the app on Google Play (unless you use Play App Signing,
# which is the default for new apps; then this is your "upload key").
#
#   scripts/make-keystore.sh            (asks for the passwords)
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v keytool >/dev/null; then
  echo "keytool not found. Install a JDK 17+ (Android Studio includes one) and try again." >&2
  exit 1
fi
if [ -e release.jks ]; then
  echo "release.jks already exists; refusing to overwrite it." >&2
  exit 1
fi

ALIAS=${KEY_ALIAS:-tinymotors}
read -r -s -p "Choose a keystore password (6+ characters): " STOREPASS; echo
read -r -s -p "Type it again: " STOREPASS2; echo
[ "$STOREPASS" = "$STOREPASS2" ] || { echo "Passwords do not match." >&2; exit 1; }
[ ${#STOREPASS} -ge 6 ] || { echo "Password too short." >&2; exit 1; }
read -r -p "Your name or studio name (for the certificate): " CN

keytool -genkeypair -v \
  -keystore release.jks -storetype PKCS12 \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$STOREPASS" -keypass "$STOREPASS" \
  -dname "CN=${CN:-Tiny Motors}, O=${CN:-Tiny Motors}"

umask 077
cat > keystore.properties <<PROPS
storeFile=release.jks
storePassword=$STOREPASS
keyAlias=$ALIAS
keyPassword=$STOREPASS
PROPS

echo
echo "Done: release.jks and keystore.properties created (not tracked by git)."
echo "Build signed files with:  ./gradlew bundleRelease assembleRelease"
