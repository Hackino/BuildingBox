#!/usr/bin/env bash
#
# init-secrets.sh
# ---------------
# Create your local, editable secrets config from the committed README template.
# Run this once on a fresh clone, then fill in the values the README points you to.
#
# Re-running is safe and additive: if secrets/config.json already exists, this
# MERGES the template into it — your existing values are kept, and only keys the
# template has that your config is missing are added. Nothing you filled in is lost.
#
# Reads:
#   scripts/secrets.config.README.md   (the ```json block inside it is the template)
#
# Writes / updates:
#   secrets/config.json                (gitignored; YOUR copy to edit)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

README="scripts/secrets.config.README.md"
OUT="secrets/config.json"

[ -f "$README" ] || { echo "README not found: $README" >&2; exit 1; }

command -v jq >/dev/null 2>&1 || {
  echo "Missing dependency: jq.  Install it (macOS: 'brew install jq')." >&2; exit 1; }

# Extract the first ```json fenced block from the README, drop the _doc helper key.
JSON="$(awk '
  /^```json/ { capture=1; next }
  /^```/     { if (capture) exit }
  capture    { print }
' "$README")"

[ -n "$JSON" ] || { echo "No \`\`\`json template block found in $README" >&2; exit 1; }
printf '%s\n' "$JSON" | jq empty 2>/dev/null || {
  echo "Template JSON in $README is invalid." >&2; exit 1; }
TEMPLATE="$(printf '%s\n' "$JSON" | jq 'del(._doc)')"

mkdir -p secrets
umask 177

if [ -f "$OUT" ]; then
  jq empty "$OUT" 2>/dev/null || {
    echo "Existing $OUT is not valid JSON — fix or delete it, then re-run." >&2; exit 1; }

  # Deep-merge: template is the base, existing config wins on every key it defines.
  #   $tpl * $cfg  → adds keys present only in the template, keeps every existing value.
  # Recursion would also splice template skeleton keys into a fully-filled
  # googleServicesJson, so preserve a filled googleServicesJson object verbatim.
  merged="$(jq -n \
    --argjson tpl "$TEMPLATE" \
    --slurpfile cfgArr "$OUT" '
      ($cfgArr[0]) as $cfg
      | ($tpl * $cfg)
      | if ($cfg.googleServicesJson? != null)
           and (($cfg.googleServicesJson | tostring) | test("CHANGE_ME") | not)
        then .googleServicesJson = $cfg.googleServicesJson
        else . end
    ')"

  # Report which top-level keys were newly added (present in template, absent before).
  added="$(jq -n --argjson tpl "$TEMPLATE" --slurpfile cfgArr "$OUT" '
    ($cfgArr[0]) as $cfg
    | [ ($tpl | keys[]) | select($cfg[.] == null) ] | join(", ")
  ')"

  printf '%s\n' "$merged" > "$OUT"
  chmod 600 "$OUT"

  if [ -n "$added" ]; then
    echo "Updated $OUT — added missing key(s): $added"
    echo "Existing values were kept. Fill in any new CHANGE_ME, then run scripts/restore-secrets.sh"
  else
    echo "Up to date: $OUT already has every template key. Nothing added."
  fi
  exit 0
fi

# Fresh file.
printf '%s\n' "$TEMPLATE" > "$OUT"
chmod 600 "$OUT"

echo "Created $ROOT/$OUT  (gitignored)."
echo
echo "Next steps:"
echo "  1. Open $OUT and replace every CHANGE_ME."
echo "     See $README for exactly where to get each value."
echo "     (Keystore: just choose any passwords/alias — it'll be created for you.)"
echo "  2. Run:  scripts/restore-secrets.sh"
