# Hermes Mobile

Native Android app that puts a [Hermes Agent](https://github.com/NousResearch/hermes-agent) on your phone.

It wraps the Hermes mobile web client in a WebView and connects over WebSocket to the
`hermes-mobile` gateway platform (default `:8787`), usually exposed through an ngrok
tunnel so the phone works from anywhere. The phone is a remote surface — the agent
("brain") runs on your PC; the app chats with it and can approve risky commands.

## Features

- Chat with your Hermes agent from the phone (streaming replies, approval buttons).
- **Keep-awake**: while a task is running the app holds a wake-lock and a foreground
  "working…" notification so the phone can't sleep or be killed mid-task; released on
  completion, connection loss, or a 15-minute cap.
- **Auto-reconnect** with capped backoff (3s → 30s) if the tunnel drops.
- Auto-connects on launch and remembers the token on-device. No secret is baked in.

## Layout

- `app/` — Android app (Kotlin). WebView shell + `HermesService` (foreground keep-awake)
  + `NativeBridge` (JS↔native) + pure decision logic (`ReconnectBackoff`, `KeepAlivePolicy`).
- `proxy/` — single-origin aiohttp proxy that serves the web client at `/` and forwards
  `/ws` to the gateway, with a client-side heartbeat so ngrok free-tier doesn't drop idle
  connections. Includes `test_proxy.py`.
- `e2e/` — `smoke.py`, an end-to-end round-trip test.

## Build

```bash
export JAVA_HOME=.../jdk-17
export ANDROID_HOME=.../Android/Sdk
gradle --no-daemon :app:assembleRelease
```

Release signing expects `keystore/keystore.properties` (gitignored — see Security).

## Test

```bash
gradle --no-daemon :app:testDebugUnitTest   # pure-logic unit tests
python3 proxy/test_proxy.py                 # proxy round-trip tests
python3 e2e/smoke.py [ws_url] [device_id]   # live gateway round-trip
```

The e2e test reads `MOBILE_TOKEN` from the environment or `~/.hermes/.env` and requires
a paired device id (default `phone-1`).

## Security

- The `MOBILE_TOKEN` is never stored in the repo or the APK; it's entered once on device.
- `allowBackup="false"`; cleartext HTTP is disabled except to the home LAN gateway.
- The signing keystore lives in `keystore/` (gitignored); losing it means a fresh install
  rather than an update.
- See `PRIVACY.md` for the release-audit findings.

## Deploying the proxy

The proxy runs on the same machine as the gateway:

```bash
python3 proxy/proxy.py   # serves :8080, forwards /ws to 127.0.0.1:8787
ngrok http 8080          # expose it; the phone connects to the wss:// URL
```
