# BuildingBox — Implementation Plan (Compose Multiplatform + Firebase)

Mobile/desktop implementation of the prototype in [`../design`](../design). Android is the
primary runtime; iOS source is created but not run; **Windows desktop** is supported via a
responsive Compose UI.

## Decisions (locked)
- **App name:** BuildingBox
- **Application ID / bundle:** `com.buildingbox.app`
- **Architecture:** Clean Architecture, feature-based, **single module** (`composeApp`).
- **Stack:** Kotlin Multiplatform + Compose Multiplatform 1.11, **Koin** (DI),
  **Navigation-Compose (KMP)**, Coroutines/Flow, kotlinx.serialization.
- **Firebase access:** GitLive Firebase SDK on **Android/iOS**; **Ktor + Firebase REST** on
  **Desktop (JVM)** (no official client SDK on desktop). Crashlytics native on Android/iOS,
  file-logger no-op on desktop.
- **RTDB security:** **role-based** (admin writes, viewers read-only).
- **Money:** stored as integers — `usdCents: Long`, `lbp: Long`. No currency conversion ever.

## Firebase — what to create and hand over
1. Firebase project → **Project ID**.
2. Android app `com.buildingbox.app` → **`google-services.json`** → `composeApp/google-services.json`.
3. iOS app `com.buildingbox.app` → **`GoogleService-Info.plist`** (folders only).
4. **Authentication → Email/Password** enabled. Create the admin account in the console
   (no in-app sign-up). Hand over the **admin email** (keep the password).
5. **Realtime Database** created (pick a region) → deploy [`../config/firebase/database.rules.json`](../config/firebase/database.rules.json).
   Hand over the **database URL**. Full guide: [`../config/firebase/FIREBASE_SETUP.md`](../config/firebase/FIREBASE_SETUP.md).
6. **Crashlytics** enabled.
7. **Web API key** (Project settings → General) — needed by the Desktop build for REST auth/RTDB.
8. *(Recommended)* **App Check** (Play Integrity / DeviceCheck). I'll provide a debug token.
9. **Never** ship a service-account / Admin SDK key in the client.

After first login the app self-creates `/users/$uid` as **viewer**; promote your own node to
**admin** once in the console.

## Realtime Database schema (sharded + aggregated)
```
users/$uid            { role, displayName, createdAt }
building              { name, address, currentMonth }
apartments/$aptId     { name, ownerName, floor, feeUsdCents, feeLbp, phone, active, createdAt }
dues/$month/$aptId/$dueId   { title, usdCents, lbp, paid, paidOn, base }
expenses/$month/$expenseId  { date, label, category, usdCents, lbp }
summaries/$month      { collectedUsdCents, collectedLbp, expectedUsdCents, expectedLbp,
                        spentUsdCents, spentLbp, paidUnits }   // precomputed
state/balance         { usdCents, lbp }                        // running box balance
```
**Efficiency:** month-sharding keeps every read tiny; dashboard reads only `state/balance` +
`summaries/$month`; writes are **atomic multi-path `updateChildren` using `ServerValue.increment`**
to keep aggregates exact without read-modify-write. Offline persistence on; listeners scoped
per screen; `.indexOn` for filtered queries.

## Module layout (single `composeApp`)
```
composeApp/src/
  commonMain/kotlin/com/buildingbox/app/
    App.kt                       root composable, responsive scaffold, nav host
    di/                          Koin modules
    core/{designsystem,money,datetime,result,firebase}
    feature/{auth,dashboard,payments,calendar,units,reports}/{domain,data,presentation}
  commonMain/composeResources/   fonts (Sora, Inter, JetBrains Mono), icons
  androidMain/                   MainActivity, Application, Firebase+Crashlytics, App Check
  iosMain/                       iOS entry + GitLive actuals (not run)
  desktopMain/                   Desktop window, Ktor-REST Firebase actuals
```
Each feature: `domain` (models, repo interfaces, use cases — pure) → `data` (repo impls,
RTDB/Auth mappers) → `presentation` (ViewModel + StateFlow + Compose screens).

## Responsive UI
Material3 `WindowSizeClass`: **compact** → bottom nav + single pane + bottom sheets (phone);
**medium/expanded** → navigation rail + two-pane (list ▸ detail) + side dialogs (desktop/tablet).

## Roles
`admin` → all add/edit/sheets. `viewer` → identical screens, write affordances hidden/disabled.
Enforced both in UI (Koin-provided `Session.role`) and in RTDB rules (defense in depth).

## Roadmap (feature-by-feature vertical slices)
0. **Foundation** — module, DI, theme/tokens, design-system core, money, gateways, app shell, rules. ← current
1. **Auth** — login, session, role bootstrap, route guard.
2. **Units** — list (by floor) + detail + add/edit; establishes CRUD + atomic writes.
3. **Payments + Dues** — month view, multi-month filter, by-day; dues edit/add in unit detail;
   fan-out aggregate writes.
4. **Calendar** — month/day ledger from dues+expenses; add expense.
5. **Dashboard** — balance + summaries + recent activity + collection ring.
6. **Reports** — compute + share/copy/export.
7. **Hardening** — Crashlytics, App Check, offline, desktop polish, states.

## Notes
- Versions in `gradle/libs.versions.toml` target CMP 1.11 / current KMP; open in Android Studio
  (latest) to sync — bump the catalog if resolution complains.
- Desktop has no Crashlytics/App Check; it authenticates via REST and is effectively read-mostly.
