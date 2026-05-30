# BuildingBox — Local Secrets Setup

This is the **source of truth** for the secrets your local build needs. The setup
script reads the JSON template at the bottom of this file and creates your private,
editable copy. You never edit this README to add secrets — you edit the copy.

## How to use

```bash
cd mobile
scripts/init-secrets.sh      # creates secrets/config.json from the template below
#   ...open secrets/config.json and replace every CHANGE_ME (see the table)...
scripts/restore-secrets.sh   # generates the real (gitignored) files the app needs
```

- `secrets/config.json` is **gitignored** — it holds real secrets. Never commit it.
- This README + its template **are** committed (no secrets in here).

## What you fill in — step by step

### 1. `googleServicesJson`  (required for Android)

1. Go to <https://console.firebase.google.com> and open your **BuildingBox** project.
2. Click the **gear icon** (top-left) → **Project settings**.
3. Scroll to **Your apps** → select the **Android** app (package `com.buildingbox.app`).
   - No Android app yet? Click **Add app → Android**, package name `com.buildingbox.app`, register.
4. Click **Download `google-services.json`**.
5. Open the downloaded file in a text editor, **copy its entire contents**, and paste it
   as the value of `googleServicesJson` in `secrets/config.json` (replace the whole object).
   Paste the **raw JSON** — no base64.

### 2. Keystore  (required — signs the release APK)

**First time, you invent these values — there is nothing to download:**

1. `keystoreStorePassword` → type any password you want (e.g. a strong passphrase).
2. `keystoreKeyPassword` → type any password (commonly the same as the store password).
3. `keystoreKeyAlias` → any name; the default `buildingbox` is fine.
4. Leave `keystoreBase64` as `CHANGE_ME`.

`restore-secrets.sh` then **creates `composeApp/buildingbox.keystore` for you** from those values.

> **Reusing an existing key on another machine** (so both machines sign identically):
> on the machine that has the keystore, run
> `openssl base64 -A -in composeApp/buildingbox.keystore`
> and paste that one-line string into `keystoreBase64` (keep the same passwords/alias).

> ⚠️ **Back up `composeApp/buildingbox.keystore` after it's generated.** It is
> *irreplaceable* — lose it and you can never publish an update to an app already on
> the Play Store under that signing key.

### 3. `desktopFirebase`  (optional — Desktop build only)

The Desktop app talks to Firebase over REST, so it needs these three values. Leave all
three as `CHANGE_ME` to skip (the **Android build does not need them**).

1. Firebase Console → **gear icon → Project settings → General** tab.
2. `apiKey` → copy the **Web API Key** shown on that page.
3. `projectId` → copy the **Project ID** shown on that page.
4. `databaseUrl` → Firebase Console → **Build → Realtime Database**; the URL is shown at
   the top of the data view, e.g.
   `https://<project-id>-default-rtdb.<region>.firebasedatabase.app`.

## What `restore-secrets.sh` produces

All gitignored, written from `secrets/config.json`:

- `composeApp/google-services.json` — from the `googleServicesJson` object
- `composeApp/buildingbox.keystore` — decoded from `keystoreBase64`, or generated fresh
- `keystore.properties` — from the keystore password/alias fields
- `composeApp/desktop-firebase.properties` — from `desktopFirebase.*` (only if filled in)

## Config template

`init-secrets.sh` copies the JSON block below into `secrets/config.json`.

```json
{
  "_doc": "Fill in every CHANGE_ME. See scripts/secrets.config.README.md for how to get each value. This file is gitignored — never commit it.",
  "googleServicesJson": {
    "project_info": {
      "project_number": "CHANGE_ME",
      "project_id": "CHANGE_ME",
      "firebase_url": "CHANGE_ME",
      "storage_bucket": "CHANGE_ME"
    },
    "client": [
      {
        "client_info": {
          "mobilesdk_app_id": "CHANGE_ME",
          "android_client_info": { "package_name": "com.buildingbox.app" }
        },
        "api_key": [{ "current_key": "CHANGE_ME" }]
      }
    ],
    "configuration_version": "1"
  },
  "keystoreBase64": "CHANGE_ME",
  "keystoreStorePassword": "CHANGE_ME",
  "keystoreKeyAlias": "buildingbox",
  "keystoreKeyPassword": "CHANGE_ME",
  "desktopFirebase": {
    "apiKey": "CHANGE_ME",
    "databaseUrl": "CHANGE_ME",
    "projectId": "CHANGE_ME"
  }
}
```
