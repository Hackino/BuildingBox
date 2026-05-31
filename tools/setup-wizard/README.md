# BuildingBox Setup Wizard

A small cross-platform (macOS + Windows) GUI for managing the project's local
secrets. It is a graphical front-end over the same logic as
[`scripts/`](../../scripts) — but reimplemented in pure Python stdlib so it needs
**no `bash`, `jq`, or `openssl`** (only a JDK's `keytool` to generate a fresh
keystore, exactly like the shell scripts).

## What it does

| Button | Action |
|--------|--------|
| **Init** | Create `secrets/config.json` from the committed template, or merge any new template keys into your existing config (your filled-in values are always kept). |
| **Reload** | Re-read `secrets/config.json` back into the form fields. |
| **Update & Restore** | Save the form values to `secrets/config.json`, then regenerate the real (gitignored) secret files the build needs. |
| **Export** | Fold existing local secret files back into `secrets/config.json` and write `secrets/github-secrets.txt` (the GitHub Actions bundle). |

Every field in `secrets/config.json` is editable in the form (keystore
passwords/alias, the desktop Firebase trio, and the full `google-services.json`
paste box). A **Terminal** pane streams exactly what each action does.

## Files it reads / writes (all under `mobile/`)

- reads template: `scripts/secrets.config.README.md`
- source of truth: `secrets/config.json` *(gitignored)*
- generates: `composeApp/google-services.json`, `composeApp/buildingbox.keystore`,
  `keystore.properties`, `composeApp/desktop-firebase.properties`,
  `composeApp/desktop-resources/common/desktop-firebase.properties`
- export bundle: `secrets/github-secrets.txt` *(gitignored)*

## Run it

**macOS**
```bash
cd mobile/tools/setup-wizard
./run.command        # or: python3 setup_wizard.py
```
(First time: `chmod +x run.command`, or right-click → Open to bypass Gatekeeper.)

**Windows**
```bat
cd mobile\tools\setup-wizard
run.bat              REM or: python setup_wizard.py
```

## Requirements

- **Python 3.8+ with Tkinter.** Tkinter ships with the official python.org
  installers on both OSes. (Linux: `sudo apt install python3-tk`.)
- **A JDK on `PATH`** — only needed when generating a brand-new keystore
  (`keytool`). Not needed if you paste an existing `keystoreBase64` or already
  have `composeApp/buildingbox.keystore`.

## Notes

- Secret files are written with `0600` permissions where the OS supports it.
- Nothing here is ever committed: `secrets/` and the generated files are
  gitignored. **Back up `composeApp/buildingbox.keystore`** once generated — a
  release signing key is irreplaceable.
- The logic lives in [`secrets_core.py`](secrets_core.py) (no Tkinter, so it can
  be imported and tested headlessly); the GUI is [`setup_wizard.py`](setup_wizard.py).
