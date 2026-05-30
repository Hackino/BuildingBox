#!/usr/bin/env bash
#
# restore-secrets.sh
# ------------------
# Recreate the local secret files from a bundle produced by export-secrets.sh.
# Run this on a fresh clone / new machine to get a buildable checkout.
#
# Reads:
#   secrets/github-secrets.txt   (default; or pass a path: ./restore-secrets.sh path/to/bundle.txt)
#
# Writes:
#   composeApp/google-services.json   (from GOOGLE_SERVICES_JSON_BASE64)
#   composeApp/buildingbox.keystore   (from KEYSTORE_BASE64)
#   keystore.properties               (from KEYSTORE_STORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD)
#
# All outputs are gitignored. Existing files are overwritten.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BUNDLE="${1:-secrets/github-secrets.txt}"
[ -f "$BUNDLE" ] || { echo "Bundle not found: $BUNDLE" >&2; exit 1; }

# --- parse: a known NAME line, then its VALUE on the next non-blank line ---
GOOGLE_SERVICES_JSON_BASE64=""
KEYSTORE_BASE64=""
KEYSTORE_STORE_PASSWORD=""
KEYSTORE_KEY_ALIAS=""
KEYSTORE_KEY_PASSWORD=""

current=""
while IFS= read -r line || [ -n "$line" ]; do
  line="${line%$'\r'}"                  # strip trailing CR
  case "$line" in
    ""|"#"*) continue ;;                # skip blanks and comments
  esac
  case "$line" in
    GOOGLE_SERVICES_JSON_BASE64|KEYSTORE_BASE64|KEYSTORE_STORE_PASSWORD|KEYSTORE_KEY_ALIAS|KEYSTORE_KEY_PASSWORD)
      current="$line" ;;
    *)
      if [ -n "$current" ]; then
        printf -v "$current" '%s' "$line"
        current=""
      fi ;;
  esac
done < "$BUNDLE"

# --- validate ---
: "${GOOGLE_SERVICES_JSON_BASE64:?Missing GOOGLE_SERVICES_JSON_BASE64 in bundle}"
: "${KEYSTORE_BASE64:?Missing KEYSTORE_BASE64 in bundle}"
: "${KEYSTORE_STORE_PASSWORD:?Missing KEYSTORE_STORE_PASSWORD in bundle}"
: "${KEYSTORE_KEY_ALIAS:?Missing KEYSTORE_KEY_ALIAS in bundle}"
: "${KEYSTORE_KEY_PASSWORD:?Missing KEYSTORE_KEY_PASSWORD in bundle}"

umask 177
mkdir -p composeApp

printf '%s' "$GOOGLE_SERVICES_JSON_BASE64" | openssl base64 -d -A > composeApp/google-services.json
printf '%s' "$KEYSTORE_BASE64"             | openssl base64 -d -A > composeApp/buildingbox.keystore

cat > keystore.properties <<EOF
storeFile=buildingbox.keystore
storePassword=$KEYSTORE_STORE_PASSWORD
keyAlias=$KEYSTORE_KEY_ALIAS
keyPassword=$KEYSTORE_KEY_PASSWORD
EOF

chmod 600 keystore.properties composeApp/buildingbox.keystore composeApp/google-services.json

echo "Restored (all gitignored):"
echo "  composeApp/google-services.json"
echo "  composeApp/buildingbox.keystore"
echo "  keystore.properties"
echo "Ready to build:  ./gradlew :composeApp:assembleRelease"
