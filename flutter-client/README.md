# Enterprise IM Flutter Client

Minimal Flutter client for the Java Netty TCP protocol and HTTP call signaling API.

## Scope

- Connect to the TCP gateway with `dart:io Socket`.
- Send `AUTH`, `PING`, and `TEXT` JSON-line frames.
- Display inbound frames from the server.
- Check `/api/calls/readiness`.
- Start audio/video calls through `POST /api/calls`.
- Answer, reject, hang up, and list call records through `/api/calls`.
- Use local SQLite through `sqflite` for messages, logs, and call records.
- Android side exposes `MethodChannel('enterprise_im/sip')`; it uses bundled
  PJSIP/pjsua2 arm64 runtime, then falls back to Android platform SIP.

## Build

Install Flutter, then generate platform folders and build:

```powershell
flutter create --platforms=android .
flutter pub get
flutter build apk
```

Local package command: `scripts/package-flutter.ps1`.

## Defaults

- Host: `127.0.0.1`
- HTTP port: `18080` for Docker Compose, or `8080` for direct local server.
- TCP port: `19090` for Docker Compose, or `9000` for direct local server.
- Token format: login returns a signed JWT. Legacy `demo-token-<userId>` is test-only and must be explicitly enabled on the server.
- Frame ending: newline `\n`

## PJSIP Native Runtime

For final device media, PJSIP/pjsua2 2.14 arm64 runtime is bundled under:

```text
android/app/src/main/jniLibs/<abi>/
```

Current bundled ABI folder: `arm64-v8a`. See `android/app/src/main/jniLibs/README.md`.

The generated pjsua2 Java binding is bundled at `android/app/libs/pjsua2.jar`.
Use `scripts/build-pjsip-android-wsl.ps1` to rebuild the runtime.
