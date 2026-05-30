#!/usr/bin/env bash
#
# export-secrets.sh
# -----------------
# The INVERSE of restore-secrets.sh: read the real (gitignored) secret files that
# already exist locally and fold their values back into secrets/config.json — the
# single editable source of truth (see scripts/secrets.config.README.md).
#
# Use this when you set secrets up by hand (or on another machine) and want a
# config.json you can carry elsewhere and feed to restore-secrets.sh.
#
# Reads (whatever exists locally):
#   composeApp/google-services.json        -> .googleServicesJson   (raw JSON object)
#   composeApp/buildingbox.keystore        -> .keystoreBase64       (single-line base64)
#   keystore.properties                    -> .keystoreStorePassword / .keystoreKeyAlias / .keystoreKeyPassword
#   composeApp/desktop-firebase.properties -> .desktopFirebase.apiKey / .databaseUrl / .projectId
#
# Writes:
#   secrets/config.json        (mode 600, gitignored). Created from the README template
#                              if absent; otherwise updated in place (existing values kept
#                              for any source file that isn't present).
#   secrets/github-secrets.txt (mode 600, gitignored). Each GitHub Actions secret NAME on
#                              a line, its VALUE on the next — copy/paste into the repo's
#                              Settings → Secrets and variables → Actions.
#
# WARNING: both outputs hold real secrets in cleartext. NEVER commit or share them.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

GS="composeApp/google-services.json"
KS="composeApp/buildingbox.keystore"
KP="keystore.properties"
# desktop-firebase.properties: restore-secrets.sh writes it to BOTH the bundled
# resources dir and the top-level dev copy. Prefer the bundled one, fall back to dev.
DF_BUNDLED="composeApp/desktop-resources/common/desktop-firebase.properties"
DF_DEV="composeApp/desktop-firebase.properties"
DF="$DF_DEV"; [ -f "$DF_BUNDLED" ] && DF="$DF_BUNDLED"
README="scripts/secrets.config.README.md"
OUT="secrets/config.json"
GH_OUT="secrets/github-secrets.txt"

command -v jq >/dev/null 2>&1 || {
  echo "Missing dependency: jq.  Install it (macOS: 'brew install jq')." >&2; exit 1; }

# --- start from existing config.json, or seed from the README template ---------
if [ -f "$OUT" ]; then
  jq empty "$OUT" 2>/dev/null || {
    echo "Existing $OUT is not valid JSON — fix or delete it first." >&2; exit 1; }
  config="$(cat "$OUT")"
elif [ -f "$README" ]; then
  block="$(awk '/```json/{f=1;next} /```/{if(f)exit} f' "$README")"
  [ -n "$block" ] || { echo "No \`\`\`json block found in $README" >&2; exit 1; }
  config="$(printf '%s\n' "$block" | jq 'del(._doc)')"
else
  echo "Neither $OUT nor $README found — nothing to seed config from." >&2
  exit 1
fi

# read a value from keystore.properties, strip CR/LF
prop() { grep -E "^$1=" "$KP" | head -n1 | cut -d= -f2- | tr -d '\r\n'; }
# read a key from a java .properties file
dprop() { grep -E "^$1=" "$DF" | head -n1 | cut -d= -f2- | tr -d '\r\n'; }

found=()

# --- google-services.json -> .googleServicesJson (raw object) ------------------
if [ -f "$GS" ]; then
  jq empty "$GS" 2>/dev/null || { echo "WARN: $GS is not valid JSON; skipping." >&2; }
  if jq empty "$GS" 2>/dev/null; then
    config="$(jq --slurpfile gs "$GS" '.googleServicesJson = $gs[0]' <<<"$config")"
    found+=("googleServicesJson")
  fi
fi

# --- keystore file -> .keystoreBase64 (single-line) ----------------------------
if [ -f "$KS" ]; then
  ks_b64="$(openssl base64 -A -in "$KS")"
  config="$(jq --arg v "$ks_b64" '.keystoreBase64 = $v' <<<"$config")"
  found+=("keystoreBase64")
fi

# --- keystore.properties -> password/alias fields ------------------------------
if [ -f "$KP" ]; then
  config="$(jq \
    --arg sp "$(prop storePassword)" \
    --arg ka "$(prop keyAlias)" \
    --arg kp "$(prop keyPassword)" \
    '.keystoreStorePassword = $sp | .keystoreKeyAlias = $ka | .keystoreKeyPassword = $kp' \
    <<<"$config")"
  found+=("keystore passwords/alias")
fi

# --- desktop-firebase.properties -> .desktopFirebase.* -------------------------
if [ -f "$DF" ]; then
  config="$(jq \
    --arg ak "$(dprop firebase.apiKey)" \
    --arg du "$(dprop firebase.databaseUrl)" \
    --arg pj "$(dprop firebase.projectId)" \
    '.desktopFirebase.apiKey = $ak | .desktopFirebase.databaseUrl = $du | .desktopFirebase.projectId = $pj' \
    <<<"$config")"
  found+=("desktopFirebase")
fi

if [ "${#found[@]}" -eq 0 ]; then
  echo "No local secret files found to export (looked for $GS, $KS, $KP, $DF)." >&2
  echo "Nothing written." >&2
  exit 1
fi

mkdir -p secrets
umask 177
printf '%s\n' "$config" > "$OUT"
chmod 600 "$OUT"

# --- ALSO emit the GitHub Actions secrets bundle, sourced from the same local files ---
# Format the CI workflow expects: NAME line, VALUE line. Only includes entries whose
# source file is present. base64 is single-line (openssl -A) so it pastes cleanly.
emit() { printf '%s\n%s\n\n' "$1" "$2"; }
gh_count=0
{
  echo "# BuildingBox - GitHub Actions secrets bundle"
  echo "# Generated by scripts/export-secrets.sh. CONTAINS REAL SECRETS - never commit or share."
  echo "# Paste each NAME/VALUE into: repo Settings -> Secrets and variables -> Actions."
  echo
  if [ -f "$GS" ]; then emit GOOGLE_SERVICES_JSON_BASE64 "$(openssl base64 -A -in "$GS")"; gh_count=$((gh_count+1)); fi
  if [ -f "$KS" ]; then emit KEYSTORE_BASE64             "$(openssl base64 -A -in "$KS")"; gh_count=$((gh_count+1)); fi
  if [ -f "$KP" ]; then
    emit KEYSTORE_STORE_PASSWORD "$(prop storePassword)"; gh_count=$((gh_count+1))
    emit KEYSTORE_KEY_ALIAS      "$(prop keyAlias)";      gh_count=$((gh_count+1))
    emit KEYSTORE_KEY_PASSWORD   "$(prop keyPassword)";   gh_count=$((gh_count+1))
  fi
  # Optional — CI does not need it to build, but include it if present for completeness.
  if [ -f "$DF" ]; then emit DESKTOP_FIREBASE_PROPERTIES_BASE64 "$(openssl base64 -A -in "$DF")"; gh_count=$((gh_count+1)); fi
} > "$GH_OUT"
chmod 600 "$GH_OUT"

echo "Wrote $ROOT/$OUT  (mode 600, gitignored)"
echo "Wrote $ROOT/$GH_OUT  (mode 600, gitignored) — $gh_count GitHub secret(s)"
echo "Exported from: ${found[*]}"
echo "  • config.json        → carry to another machine, run scripts/restore-secrets.sh"
echo "  • github-secrets.txt → copy each NAME/VALUE into GitHub Actions secrets"
