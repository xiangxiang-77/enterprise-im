# Delivery Checklist

## Required Artifacts

- [x] Java backend source: `im-server`
- [x] Web/admin source: `im-ui`
- [x] Qt client source: `qt-client`
- [x] Flutter client source: `flutter-client`
- [x] Dockerfile and Compose config
- [x] Project skills/docs: `project-docs`
- [x] Server Docker image: `enterprise-im-server:0.1.0`
- [x] Windows exe from Qt 5.9.3: `dist/EnterpriseIMQtClient/EnterpriseIMQtClient.exe`
- [x] VS2017 WebEngine packaging script: `scripts/package-qt-vs2017-webengine.ps1`
- [x] Android apk from Flutter: `dist/enterprise-im-app-release.apk`
- [x] PJSIP gateway service source: `pjsip-gateway`

## Verification Commands

```powershell
.\scripts\verify.ps1
```

Manual focused checks:

```powershell
cd im-server
mvn test

cd ..\im-ui
npm run build

cd ..
docker compose config
docker compose up -d
docker build -t enterprise-im-server:0.1.0 im-server

.\scripts\run-pjsip-gateway.ps1

.\scripts\package-qt.ps1
.\scripts\package-qt-vs2017-webengine.ps1 -QtRoot D:\Qt\5.9.3\msvc2017
.\scripts\package-flutter.ps1
.\scripts\package-delivery.ps1
.\scripts\verify-native-media-runtime.ps1
.\scripts\verify-native-media-runtime.ps1 -RequireQt -RequireFlutter
.\scripts\verify-video-capability.ps1
.\scripts\verify-sip-audio-content.ps1
.\scripts\verify-final-media-preflight.ps1
```

## Current Limits

- Bundled `pjsip-gateway` runs the PJSIP REST boundary and makes backend `media_ready` reachable.
- `pjsip-gateway` now has native adapter hooks for `pjsua-adapter` and `external-pjsip-command`.
- The Compose gateway image now builds and bundles PJSIP/pjsua 2.14, so the previous missing native binary hard dependency is resolved.
- SIP registrar/media topology is now reachable in Docker: Asterisk handles SIP, coturn is online, and `scripts/verify-sip-media-loop.ps1` verifies confirmed PCMU/RTP media between two registered PJSIP 2.14 clients.
- Content-level audio proof is now automated by `scripts/verify-sip-audio-content.ps1`: the Qt-side endpoint plays a 440 Hz voice fingerprint, the Flutter-side endpoint plays an 880 Hz voice fingerprint, both sides record the call, and the script checks that each recording contains the other side's fingerprint. Evidence is saved under `build/sip-audio-content`.
- Client media runtimes are present:
  - Qt: Windows PJSIP 2.14 `pjsua.exe` and DLLs are under `qt-client/third_party/pjsip/windows`, bundled in the VS2017 package, and `pjsua.exe --help` exposes video options.
  - Flutter arm64 is bundled: `libpjsua2.so`, `libc++_shared.so`, and `pjsua2.jar`.
- Run `scripts/verify-final-media-preflight.ps1`; it chains native runtime bundle checks, explicit video capability checks, TURN allocation, the Docker SIP PCMU/RTP loop, and the content-level audio proof.
- After that preflight passes, remaining media acceptance requires real devices only: hearing physical Qt/Android microphone/speaker audio in both directions, seeing the remote camera picture on both endpoints, and cross-network TURN behavior.
- Qt and Flutter clients now use product-like IM layouts. Audio/video calls open a dedicated call screen; Flutter blocks self-answer on outgoing calls.
- Flutter video now has local camera preview PiP and an Android PJSIP `SurfaceView` bridge for remote video. Final remote-camera rendering quality still needs a real Android phone.
- Deep chat features such as image/file picker and push notification remain limited.

## Smoke Scope

- Backend tests cover auth, admin, DB schema, message history, TCP, WebSocket, and call signaling.
- Call readiness can be checked at `GET /api/calls/readiness`; it verifies TURN/PJSIP configuration without leaking the TURN password.
- Authenticated SIP/TURN media config can be checked at `GET /api/calls/media-config?userId=&calleeId=`.
- Call signaling now emits realtime `CALL_INVITE` and `CALL_UPDATE` frames to online WebSocket/TCP clients, with integration coverage for both transports.
- Call lifecycle has guarded transitions: `ringing -> answered/rejected/ended`, `answered -> ended`, terminal states reject repeated or conflicting actions.
- Call transition APIs now require `actorId`: only callee can answer/reject, and only call participants can hang up.
- Call write APIs now require a matching user bearer token: token user must equal `callerId` or `actorId`.
- TURN/PJSIP call config and user call history now require a user bearer token; readiness remains password-safe.
- Admin call connectivity probe now tests TURN and PJSIP host/port reachability, separating configured from reachable.
- Call connectivity probe timeout is configurable with `REALTIME_PROBE_TIMEOUT_MS`, and each probe reports `durationMs`.
- Admin API and `/admin` now expose call audit records with `userId`, `status`, and `mediaType` filters.
- Admin overview now includes total, ringing, answered, and missed call counters.
- Qt/Flutter clients parse incoming `CALL_INVITE` and `CALL_UPDATE` frames and bind the active call id for answer/reject/hangup.
- Admin web shows the same call readiness result in `/admin` after login.
- Web build covers TypeScript and production bundle.
- Web call dialog now loads authenticated recent call history from `/api/calls?userId=`.
- `scripts/verify.ps1` includes Docker live API checks for admin call connectivity, authenticated call config, and rejected unauthenticated config access.
- Docker runtime is verified with Compose: backend `18080`, TCP `19090`, MinIO `9101/9102`, MySQL 8.0, Redis, coturn, Asterisk, and pjsip-gateway.
- Docker Compose now includes an Asterisk PJSIP registrar on SIP `5060` and RTP `10000-10020/udp` for client media registration.
- PJSIP gateway local smoke test covers `/health`, create, answer, hangup, and session listing.
- Docker PJSIP gateway smoke now reports `engine=pjsua-adapter` and `/opt/pjsip/bin/pjsua` inside the container.
- Qt and Flutter have dedicated packaging scripts and local delivery artifacts have been generated.
- Qt client covers TCP protocol plus desktop audio/video signaling entry points: readiness, start audio/video, answer, reject, hang up, and call history through `/api/calls`.
- Qt client now has a `SipMediaClient` adapter that consumes `/api/calls/media-config` and starts a local `pjsua` process when `PJSUA_BIN` or `pjsua` is available. Audio calls use `--null-video`; video calls use explicit `--video`; both use `--auto-conf`.
- Flutter client covers TCP protocol plus mobile audio/video signaling entry points: readiness, start audio/video, answer, reject, hang up, and call history through `/api/calls`.
- Flutter client now has an Android native SIP bridge through `MethodChannel("enterprise_im/sip")`, consumes `/api/calls/media-config?platform=android`, requests microphone/camera/audio permissions, bundles PJSIP/pjsua2 arm64 runtime, starts native pjsua2 audio calls, and falls back to Android SIP only when pjsua2 is unavailable.
- Flutter Android registers `enterprise_im/pjsip_video_view`, attaches incoming PJSIP `VideoWindow` to an Android `SurfaceView`, and shows local camera preview through the Flutter `camera` plugin.
