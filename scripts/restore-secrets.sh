#!/usr/bin/env bash
#
# restore-secrets.sh
# ------------------
# Generate the local (gitignored) secret files the app needs to build,
# from your editable JSON config.
#
# Reads:
#   secrets/config.json   (default; create it with scripts/init-secrets.sh, then edit)
#   or pass a path:        ./restore-secrets.sh path/to/config.json
#
# Writes (all gitignored, overwritten):
#   composeApp/google-services.json                          (from the googleServicesJson object)
#   composeApp/buildingbox.keystore                          (decoded from keystoreBase64, OR generated fresh)
#   keystore.properties                                      (from keystoreStorePassword / keyAlias / keyPassword)
#   composeApp/desktop-firebase.properties                   (desktop dev run from project root)
#   composeApp/desktop-resources/common/desktop-firebase.properties
#                                                            (BUNDLED into the packaged desktop app so
#                                                             it's found on any device, any working dir)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

CONFIG="${1:-secrets/config.json}"

if [ ! -f "$CONFIG" ]; then
  echo "Config not found: $CONFIG" >&2
  echo "Create it first:  scripts/init-secrets.sh   (then edit it and re-run)." >&2
  exit 1
fi

command -v jq >/dev/null 2>&1 || {
  echo "Missing dependency: jq.  Install it (macOS: 'brew install jq')." >&2; exit 1; }

# Fail early if the file isn't valid JSON (common after hand-editing).
jq empty "$CONFIG" 2>/dev/null || {
  echo "Invalid JSON in $CONFIG — check for a trailing comma or unquoted value." >&2; exit 1; }

# Read a scalar value; empty string if absent/null.
val() { jq -r "$1 // \"\"" "$CONFIG"; }
# True when a value is missing or still the CHANGE_ME placeholder.
is_unset() { [ -z "$1" ] || [ "$1" = "CHANGE_ME" ]; }

KS_B64="$(val '.keystoreBase64')"
STORE_PW="$(val '.keystoreStorePassword')"
KEY_ALIAS="$(val '.keystoreKeyAlias')"
KEY_PW="$(val '.keystoreKeyPassword')"
DF_API="$(val '.desktopFirebase.apiKey')"
DF_URL="$(val '.desktopFirebase.databaseUrl')"
DF_PROJ="$(val '.desktopFirebase.projectId')"

umask 177
mkdir -p composeApp

# --- google-services.json (REQUIRED for Android) -----------------------------
# Pulled straight from the nested object. Guard against the unfilled skeleton.
GS_PROJECT_ID="$(jq -r '.googleServicesJson.project_info.project_id // ""' "$CONFIG")"
if [ -n "$GS_PROJECT_ID" ] && [ "$GS_PROJECT_ID" != "CHANGE_ME" ]; then
  jq '.googleServicesJson' "$CONFIG" > composeApp/google-services.json
  chmod 600 composeApp/google-services.json
  GS_STATUS="composeApp/google-services.json  (written from config)"
elif [ -f composeApp/google-services.json ]; then
  GS_STATUS="composeApp/google-services.json  (kept existing file)"
else
  echo "googleServicesJson in $CONFIG is still the unfilled template, and no" >&2
  echo "composeApp/google-services.json exists." >&2
  echo "Download google-services.json from Firebase Console and paste its full" >&2
  echo "contents into the googleServicesJson object in $CONFIG." >&2
  exit 1
fi

# --- keystore passwords/alias (always required) ------------------------------
for pair in "keystoreStorePassword:$STORE_PW" "keystoreKeyAlias:$KEY_ALIAS" "keystoreKeyPassword:$KEY_PW"; do
  name="${pair%%:*}"; value="${pair#*:}"
  if is_unset "$value"; then echo "Missing $name in $CONFIG" >&2; exit 1; fi
done

# --- keystore file: reuse base64, keep existing, or generate a fresh one ------
if ! is_unset "$KS_B64"; then
  printf '%s' "$KS_B64" | tr -d '[:space:]' | openssl base64 -d -A > composeApp/buildingbox.keystore
  [ -s composeApp/buildingbox.keystore ] || {
    echo "keystoreBase64 did not decode. Re-encode: openssl base64 -A -in buildingbox.keystore" >&2; exit 1; }
  KS_STATUS="composeApp/buildingbox.keystore  (decoded from config)"
elif [ -f composeApp/buildingbox.keystore ]; then
  KS_STATUS="composeApp/buildingbox.keystore  (kept existing file)"
else
  command -v keytool >/dev/null 2>&1 || {
    echo "No keystore present and keystoreBase64 is empty, but 'keytool' (from a JDK) was not found to create one." >&2
    echo "Install a JDK, or paste an existing keystore's base64 into keystoreBase64." >&2
    exit 1; }
  keytool -genkeypair -v \
    -keystore composeApp/buildingbox.keystore \
    -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STORE_PW" -keypass "$KEY_PW" \
    -dname "CN=BuildingBox, OU=Dev, O=BuildingBox, C=US" >/dev/null
  KS_STATUS="composeApp/buildingbox.keystore  (NEW key generated — back this file up!)"
fi
chmod 600 composeApp/buildingbox.keystore

# --- keystore.properties -----------------------------------------------------
cat > keystore.properties <<EOF
storeFile=buildingbox.keystore
storePassword=$STORE_PW
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PW
EOF
chmod 600 keystore.properties

# --- desktop-firebase.properties (OPTIONAL) ----------------------------------
# Written to TWO places:
#   1. composeApp/                       — for `./gradlew :composeApp:run` from the project root.
#   2. composeApp/desktop-resources/common/ — bundled (appResourcesRootDir) into the packaged
#      app so loadConfig() finds it via compose.application.resources.dir on ANY device.
DF_STATUS=""
if ! is_unset "$DF_API" && ! is_unset "$DF_URL" && ! is_unset "$DF_PROJ"; then
  # `umask 177` (set above for secret files) also strips the execute bit from any
  # dirs mkdir creates, leaving them non-traversable. Create the tree, then restore
  # 700 so files can be written into it.
  mkdir -p composeApp/desktop-resources/common
  chmod 700 composeApp/desktop-resources composeApp/desktop-resources/common
  for dest in composeApp/desktop-firebase.properties composeApp/desktop-resources/common/desktop-firebase.properties; do
    # Clear any prior copy first: overwriting can fail if an earlier run left the
    # file read-only/locked, but removing only needs write perm on the directory.
    rm -f "$dest" 2>/dev/null || true
    cat > "$dest" <<EOF
firebase.apiKey=$DF_API
firebase.databaseUrl=$DF_URL
firebase.projectId=$DF_PROJ
EOF
    chmod 600 "$dest"
  done
  DF_STATUS="composeApp/desktop-firebase.properties + desktop-resources/common/ (bundled)"
fi

echo "Generated (all gitignored):"
echo "  $GS_STATUS"
echo "  $KS_STATUS"
echo "  keystore.properties"
[ -n "$DF_STATUS" ] && echo "  $DF_STATUS"
echo "Ready to build:  ./gradlew :composeApp:assembleRelease   (desktop: ./gradlew :composeApp:run)"
