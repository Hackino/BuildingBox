"""
secrets_core.py — pure-stdlib port of scripts/{init,restore,export}-secrets.sh.

No external deps (no jq/openssl/bash). The only external tool is `keytool`, which
ships with every JDK and is the same dependency the bash scripts use to generate a
fresh keystore. Everything else is Python stdlib so the tool runs identically on
macOS and Windows.

Layout (paths resolved relative to the mobile/ project root):
  scripts/secrets.config.README.md                          template (```json block)
  secrets/config.json                                       editable source of truth
  secrets/github-secrets.txt                                GitHub Actions bundle
  composeApp/google-services.json                           generated (Android)
  composeApp/buildingbox.keystore                           generated/decoded
  keystore.properties                                       generated
  composeApp/desktop-firebase.properties                    generated (desktop dev)
  composeApp/desktop-resources/common/desktop-firebase.properties   bundled into app

Each public function takes a `log` callable (str -> None) so the GUI can stream
progress into its terminal pane. They return True on success, False on a handled
error (after logging it); unexpected errors propagate.
"""

from __future__ import annotations

import base64
import json
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

PLACEHOLDER = "CHANGE_ME"


# --------------------------------------------------------------------------- #
# Paths
# --------------------------------------------------------------------------- #
def project_root() -> Path:
    """mobile/ — two levels up from this file (tools/setup-wizard/secrets_core.py)."""
    return Path(__file__).resolve().parents[2]


class Paths:
    def __init__(self, root: Path | None = None) -> None:
        self.root = root or project_root()
        self.readme = self.root / "scripts" / "secrets.config.README.md"
        self.config = self.root / "secrets" / "config.json"
        self.github = self.root / "secrets" / "github-secrets.txt"
        self.google_services = self.root / "composeApp" / "google-services.json"
        self.keystore = self.root / "composeApp" / "buildingbox.keystore"
        self.keystore_props = self.root / "keystore.properties"
        self.desktop_fb_dev = self.root / "composeApp" / "desktop-firebase.properties"
        self.desktop_fb_bundled = (
            self.root / "composeApp" / "desktop-resources" / "common" / "desktop-firebase.properties"
        )


# --------------------------------------------------------------------------- #
# Helpers
# --------------------------------------------------------------------------- #
def is_unset(value) -> bool:
    return value is None or value == "" or value == PLACEHOLDER


def _secure_write_text(path: Path, text: str) -> None:
    """Write text and chmod 600 (best-effort; chmod is a no-op on Windows)."""
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    try:
        os.chmod(path, 0o600)
    except OSError:
        pass


def _secure_write_bytes(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    try:
        os.chmod(path, 0o600)
    except OSError:
        pass


def read_template(paths: Paths) -> dict:
    """Extract the first ```json fenced block from the README and drop the _doc key."""
    if not paths.readme.exists():
        raise FileNotFoundError(f"README not found: {paths.readme}")
    text = paths.readme.read_text(encoding="utf-8")
    match = re.search(r"```json\s*\n(.*?)\n```", text, re.DOTALL)
    if not match:
        raise ValueError(f"No ```json template block found in {paths.readme}")
    template = json.loads(match.group(1))
    template.pop("_doc", None)
    return template


def load_config(paths: Paths) -> dict:
    """Load config.json (must be valid JSON). Returns {} if it doesn't exist."""
    if not paths.config.exists():
        return {}
    return json.loads(paths.config.read_text(encoding="utf-8"))


def _deep_merge(base: dict, override: dict) -> dict:
    """jq's `*` operator: recursively merge; `override` wins on scalar collisions."""
    result = dict(base)
    for key, val in override.items():
        if key in result and isinstance(result[key], dict) and isinstance(val, dict):
            result[key] = _deep_merge(result[key], val)
        else:
            result[key] = val
    return result


def _properties_get(path: Path, key: str) -> str:
    """Read a key=value line from a java .properties file (first match), trimmed."""
    if not path.exists():
        return ""
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith(key + "="):
            return line[len(key) + 1:].strip()
    return ""


# --------------------------------------------------------------------------- #
# Keystore generation (transient — produces only base64, keystore is deleted)
# --------------------------------------------------------------------------- #
def generate_keystore_base64(
    store_pw: str,
    key_alias: str,
    key_pw: str,
    dname: str,
    log,
) -> str | None:
    """Generate a keystore with keytool in a temp dir, return its single-line
    base64, then delete the temp file. The keystore itself never persists — the
    caller keeps only the base64 (which restore can decode later).

    Returns the base64 string on success, or None on failure (after logging)."""
    if shutil.which("keytool") is None:
        log("ERROR: 'keytool' not found. Install a JDK and ensure it's on PATH.")
        return None
    for name, value in (("store password", store_pw), ("key alias", key_alias), ("key password", key_pw)):
        if is_unset(value):
            log(f"ERROR: {name} is required to generate a keystore.")
            return None

    tmpdir = tempfile.mkdtemp(prefix="bb-keystore-")
    tmp_ks = Path(tmpdir) / "buildingbox.keystore"
    try:
        cmd = [
            "keytool", "-genkeypair", "-v",
            "-keystore", str(tmp_ks),
            "-alias", key_alias,
            "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
            "-storepass", store_pw, "-keypass", key_pw,
            "-dname", dname,
        ]
        log("Generating keystore with keytool…")
        proc = subprocess.run(cmd, capture_output=True, text=True)
        if proc.returncode != 0 or not tmp_ks.exists():
            log("ERROR: keytool failed:")
            log((proc.stderr or proc.stdout).strip())
            return None
        b64 = base64.b64encode(tmp_ks.read_bytes()).decode("ascii")
        log(f"✓ Keystore generated and base64-encoded ({len(b64)} chars). Temp file deleted.")
        return b64
    finally:
        # Always wipe the temp keystore + dir — only the base64 survives.
        shutil.rmtree(tmpdir, ignore_errors=True)


# --------------------------------------------------------------------------- #
# init  (port of init-secrets.sh)
# --------------------------------------------------------------------------- #
def init_config(paths: Paths, log) -> bool:
    """Create config.json from the template, or merge new template keys into an
    existing config (existing values always win — nothing filled in is lost)."""
    try:
        template = read_template(paths)
    except (FileNotFoundError, ValueError, json.JSONDecodeError) as e:
        log(f"ERROR: {e}")
        return False

    if not paths.config.exists():
        _secure_write_text(paths.config, json.dumps(template, indent=2) + "\n")
        log(f"Created {paths.config}  (gitignored).")
        log("Fill in every CHANGE_ME, then click Update & Restore.")
        return True

    try:
        existing = load_config(paths)
    except json.JSONDecodeError as e:
        log(f"ERROR: existing {paths.config} is not valid JSON ({e}). Fix or delete it.")
        return False

    merged = _deep_merge(template, existing)
    # Preserve a fully-filled googleServicesJson verbatim (don't splice template skeleton in).
    gsj = existing.get("googleServicesJson")
    if gsj is not None and PLACEHOLDER not in json.dumps(gsj):
        merged["googleServicesJson"] = gsj

    added = [k for k in template if k not in existing]
    _secure_write_text(paths.config, json.dumps(merged, indent=2) + "\n")
    if added:
        log(f"Updated {paths.config} — added missing key(s): {', '.join(added)}")
        log("Existing values kept. Fill any new CHANGE_ME, then Update & Restore.")
    else:
        log(f"Up to date: {paths.config} already has every template key.")
    return True


# --------------------------------------------------------------------------- #
# restore  (port of restore-secrets.sh)
# --------------------------------------------------------------------------- #
def restore_secrets(paths: Paths, log) -> bool:
    """Generate the real (gitignored) secret files from config.json."""
    if not paths.config.exists():
        log(f"ERROR: {paths.config} not found. Click Init first.")
        return False
    try:
        cfg = load_config(paths)
    except json.JSONDecodeError as e:
        log(f"ERROR: invalid JSON in {paths.config} ({e}).")
        return False

    ks_b64 = cfg.get("keystoreBase64", "")
    store_pw = cfg.get("keystoreStorePassword", "")
    key_alias = cfg.get("keystoreKeyAlias", "")
    key_pw = cfg.get("keystoreKeyPassword", "")
    df = cfg.get("desktopFirebase", {}) or {}
    df_api, df_url, df_proj = df.get("apiKey", ""), df.get("databaseUrl", ""), df.get("projectId", "")

    # --- google-services.json (required for Android) ---
    gsj = cfg.get("googleServicesJson") or {}
    gs_project = (gsj.get("project_info") or {}).get("project_id", "")
    if gs_project and gs_project != PLACEHOLDER:
        _secure_write_text(paths.google_services, json.dumps(gsj, indent=2) + "\n")
        log("✓ composeApp/google-services.json  (written from config)")
    elif paths.google_services.exists():
        log("• composeApp/google-services.json  (kept existing file)")
    else:
        log("ERROR: googleServicesJson is still the unfilled template and no")
        log("       composeApp/google-services.json exists. Paste the downloaded")
        log("       google-services.json contents into the config first.")
        return False

    # --- keystore passwords/alias (always required) ---
    for name, value in (
        ("keystoreStorePassword", store_pw),
        ("keystoreKeyAlias", key_alias),
        ("keystoreKeyPassword", key_pw),
    ):
        if is_unset(value):
            log(f"ERROR: missing {name} in config.")
            return False

    # --- keystore file: decode base64 / keep existing / generate fresh ---
    if not is_unset(ks_b64):
        try:
            raw = base64.b64decode("".join(ks_b64.split()))
        except Exception as e:  # noqa: BLE001 - surface a friendly message
            log(f"ERROR: keystoreBase64 did not decode ({e}).")
            return False
        if not raw:
            log("ERROR: keystoreBase64 decoded to empty.")
            return False
        _secure_write_bytes(paths.keystore, raw)
        log("✓ composeApp/buildingbox.keystore  (decoded from config)")
    elif paths.keystore.exists():
        log("• composeApp/buildingbox.keystore  (kept existing file)")
    else:
        if shutil.which("keytool") is None:
            log("ERROR: no keystore present, keystoreBase64 empty, and 'keytool'")
            log("       (from a JDK) was not found to create one. Install a JDK,")
            log("       or paste an existing keystore's base64 into the config.")
            return False
        paths.keystore.parent.mkdir(parents=True, exist_ok=True)
        cmd = [
            "keytool", "-genkeypair", "-v",
            "-keystore", str(paths.keystore),
            "-alias", key_alias,
            "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
            "-storepass", store_pw, "-keypass", key_pw,
            "-dname", "CN=BuildingBox, OU=Dev, O=BuildingBox, C=US",
        ]
        log("Generating a new keystore with keytool…")
        proc = subprocess.run(cmd, capture_output=True, text=True)
        if proc.returncode != 0:
            log("ERROR: keytool failed:")
            log(proc.stderr.strip() or proc.stdout.strip())
            return False
        try:
            os.chmod(paths.keystore, 0o600)
        except OSError:
            pass
        log("✓ composeApp/buildingbox.keystore  (NEW key generated — BACK THIS FILE UP!)")

    # --- keystore.properties ---
    _secure_write_text(
        paths.keystore_props,
        "storeFile=buildingbox.keystore\n"
        f"storePassword={store_pw}\n"
        f"keyAlias={key_alias}\n"
        f"keyPassword={key_pw}\n",
    )
    log("✓ keystore.properties")

    # --- desktop-firebase.properties (optional; both dev + bundled locations) ---
    if not is_unset(df_api) and not is_unset(df_url) and not is_unset(df_proj):
        body = f"firebase.apiKey={df_api}\nfirebase.databaseUrl={df_url}\nfirebase.projectId={df_proj}\n"
        for dest in (paths.desktop_fb_dev, paths.desktop_fb_bundled):
            _secure_write_text(dest, body)
        log("✓ composeApp/desktop-firebase.properties + desktop-resources/common/ (bundled)")
    else:
        log("• desktop-firebase.properties skipped (desktopFirebase not fully filled — Android doesn't need it)")

    log("")
    log("Done. Ready to build:  ./gradlew :composeApp:assembleRelease   (desktop: ./gradlew :composeApp:run)")
    return True


# --------------------------------------------------------------------------- #
# export  (port of export-secrets.sh)
# --------------------------------------------------------------------------- #
def export_secrets(paths: Paths, log) -> bool:
    """Fold local secret files back into config.json, and write github-secrets.txt."""
    # Seed from existing config or the template.
    if paths.config.exists():
        try:
            config = load_config(paths)
        except json.JSONDecodeError as e:
            log(f"ERROR: existing {paths.config} is not valid JSON ({e}).")
            return False
    else:
        try:
            config = read_template(paths)
        except (FileNotFoundError, ValueError, json.JSONDecodeError) as e:
            log(f"ERROR: {e}")
            return False

    df_src = paths.desktop_fb_bundled if paths.desktop_fb_bundled.exists() else paths.desktop_fb_dev
    found: list[str] = []

    # google-services.json -> .googleServicesJson
    if paths.google_services.exists():
        try:
            config["googleServicesJson"] = json.loads(paths.google_services.read_text(encoding="utf-8"))
            found.append("googleServicesJson")
        except json.JSONDecodeError:
            log(f"WARN: {paths.google_services} is not valid JSON; skipping.")

    # keystore -> .keystoreBase64 (single line)
    if paths.keystore.exists():
        config["keystoreBase64"] = base64.b64encode(paths.keystore.read_bytes()).decode("ascii")
        found.append("keystoreBase64")

    # keystore.properties -> password/alias fields
    if paths.keystore_props.exists():
        config["keystoreStorePassword"] = _properties_get(paths.keystore_props, "storePassword")
        config["keystoreKeyAlias"] = _properties_get(paths.keystore_props, "keyAlias")
        config["keystoreKeyPassword"] = _properties_get(paths.keystore_props, "keyPassword")
        found.append("keystore passwords/alias")

    # desktop-firebase.properties -> .desktopFirebase.*
    if df_src.exists():
        config.setdefault("desktopFirebase", {})
        config["desktopFirebase"]["apiKey"] = _properties_get(df_src, "firebase.apiKey")
        config["desktopFirebase"]["databaseUrl"] = _properties_get(df_src, "firebase.databaseUrl")
        config["desktopFirebase"]["projectId"] = _properties_get(df_src, "firebase.projectId")
        found.append("desktopFirebase")

    if not found:
        log("No local secret files found to export. Nothing written.")
        return False

    _secure_write_text(paths.config, json.dumps(config, indent=2) + "\n")
    log(f"✓ Wrote {paths.config}")

    # --- github-secrets.txt: NAME line / VALUE line ---
    lines = [
        "# BuildingBox - GitHub Actions secrets bundle",
        "# Generated by setup-wizard. CONTAINS REAL SECRETS - never commit or share.",
        "# Paste each NAME/VALUE into: repo Settings -> Secrets and variables -> Actions.",
        "",
    ]
    count = 0

    def emit(name: str, value: str) -> None:
        nonlocal count
        lines.extend([name, value, ""])
        count += 1

    if paths.google_services.exists():
        emit("GOOGLE_SERVICES_JSON_BASE64",
             base64.b64encode(paths.google_services.read_bytes()).decode("ascii"))
    if paths.keystore.exists():
        emit("KEYSTORE_BASE64", base64.b64encode(paths.keystore.read_bytes()).decode("ascii"))
    if paths.keystore_props.exists():
        emit("KEYSTORE_STORE_PASSWORD", _properties_get(paths.keystore_props, "storePassword"))
        emit("KEYSTORE_KEY_ALIAS", _properties_get(paths.keystore_props, "keyAlias"))
        emit("KEYSTORE_KEY_PASSWORD", _properties_get(paths.keystore_props, "keyPassword"))
    if df_src.exists():
        emit("DESKTOP_FIREBASE_PROPERTIES_BASE64",
             base64.b64encode(df_src.read_bytes()).decode("ascii"))

    _secure_write_text(paths.github, "\n".join(lines) + "\n")
    log(f"✓ Wrote {paths.github}  — {count} GitHub secret(s)")
    log(f"Exported from: {', '.join(found)}")
    return True
