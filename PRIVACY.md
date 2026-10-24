# Privacy audit — release APK

Audited `app/build/outputs/apk/release/app-release.apk` (the merged manifest and dex,
not the source tree).

## Permissions

| Permission | Why |
|---|---|
| `android.permission.INTERNET` | WebSocket to the Hermes gateway. |
| `android.permission.WAKE_LOCK` | Keep the CPU awake while a task runs (released on completion / 15-min cap). |
| `android.permission.FOREGROUND_SERVICE` | Post the "working…" notification so the process isn't killed mid-task. |
| `android.permission.FOREGROUND_SERVICE_DATA_SYNC` | Android 14 type declaration for that service. |

No location, camera, microphone, storage, contacts, or SMS permissions.

## Third-party SDKs

None. The dex string scan (firebase / crashlytics / appsflyer / adjust / amplitude /
mixpanel / segment / gms analytics / sentry) returned only benign Kotlin stdlib symbols
(`AdjustedTimeMark`, `AdjustableOrientation`, `containsEntry`, …) — no analytics or
tracking SDK is linked.

## Components reachable from other apps

- `MainActivity` — `exported=true` (the launcher).
- `HermesService` — `exported=false`.

No exported receivers or content providers.

## Backup & transport

- `allowBackup="false"` — no cloud backup / device-to-device transfer of app data.
- Cleartext HTTP disabled by default (`networkSecurityConfig`), permitted only to the
  home LAN gateway (`172.16.1.202`) and the emulator host alias for debugging.

## Logging & secrets

- No `Log.*` statements in release sources.
- No API keys, tokens, or passwords in the repo; the signing keystore is gitignored.
