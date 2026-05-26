# Submission Status

Date: 2026-05-22

## Ready To Submit

- JDK 1.8 backend source and runnable jar.
- Web client and admin console source.
- Qt 5.9.3 Windows client package.
- VS2017 + WebEngine Qt packaging script.
- Flutter Android release APK.
- Docker Compose config.
- MySQL 8.0, Derby profile, Redis, MinIO, coturn, Asterisk, bundled PJSIP gateway.
- PJSIP gateway contract and backend integration boundary.
- Delivery package and project docs.
- Scope boundary document: `project-docs/SUBMISSION_SCOPE.md`.

## Delivery Files

- `dist/EnterpriseIM-Delivery-Package.zip`
- `dist/EnterpriseIMQtClient-vs2017.zip`
- `dist/enterprise-im-app-release.apk`
- `im-server/target/im-server-0.1.0-SNAPSHOT.jar`
- `scripts/package-qt-vs2017-webengine.ps1`
- `project-docs/PJSIP_GATEWAY_CONTRACT.md`
- `pjsip-gateway`

## Verification Run

Command:

```powershell
.\scripts\verify.ps1 -SkipNativeBuild
```

Passed:

- Backend tests: 27 tests passed.
- Web build: passed.
- Docker compose config: passed.
- Docker server image check: passed.
- Docker compose runtime: passed.
- Docker live API checks: passed.
- Delivery artifacts: passed.

Runtime checks:

- Docker Desktop is running.
- Compose services are running: MySQL 8.0, Redis, MinIO, coturn, Asterisk, pjsip-gateway, im-server.
- Container backend health passed at `http://127.0.0.1:18080/actuator/health`.
- `GET /api/calls/readiness` returns `ready=true`.
- PJSIP gateway is reachable in Docker through `PJSIP_SIGNAL_URL=http://pjsip-gateway:7070`.
- PJSIP gateway health reports `engine=pjsua-adapter` with bundled `/opt/pjsip/bin/pjsua`.
- PJSIP version is aligned to 2.14.
- Flutter APK includes Android arm64 PJSIP runtime: `libpjsua2.so`, `libc++_shared.so`, and `pjsua2.jar`.
- Qt PJSIP runtime folder includes Windows `pjsua.exe` 2.14 and required MinGW runtime DLLs.
- Qt 5.9.3 MSVC2017_64 WebEngine kit is installed at `D:\Qt\5.9.3\msvc2017_64`.
- Latest VS2017 Qt WebEngine desktop package rebuild passed on 2026-05-19.
- `scripts/verify-sip-media-loop.ps1` passed on 2026-05-19 with evidence under `build/sip-media-loop`.
- `scripts/verify-sip-audio-content.ps1` verifies audio content, not only channel state: Qt-side recorded audio must contain the Flutter-side 880 Hz voice fingerprint, and Flutter-side recorded audio must contain the Qt-side 440 Hz voice fingerprint. Evidence is under `build/sip-audio-content`.
- `scripts/verify-turn-allocation.ps1` passed on 2026-05-19 and confirmed authenticated coturn relay allocation.
- `scripts/verify-video-capability.ps1` fully passed on 2026-05-19 after rebuilding the bundled Windows `pjsua.exe` with video option support.
- `scripts/verify-final-media-preflight.ps1` is the final automated audio/video gate: native runtime bundle, video capability, TURN allocation, SIP media loop, and audio content proof.
- Flutter Android pjsua2 bridge now requests `videoCount=1` for video calls and asks for camera permission when needed.
- Flutter Android now opens a dedicated call page, blocks self-answer on outgoing calls, shows local camera preview PiP, and registers an Android `SurfaceView` bridge for incoming PJSIP remote video.
- Qt desktop now starts `pjsua` with `--auto-conf`, uses `--null-video` only for audio calls, uses explicit `--video` for video calls, and bundled Windows `pjsua.exe --help` exposes `--video`, `--vcapture-dev`, and `--vrender-dev`.
- Qt desktop now opens a full audio/video call screen with answer/reject/hangup controls and local camera preview when QtMultimedia is available.

## Known Limits

- Compose-side native PJSIP hard dependency is resolved with PJSIP/pjsua 2.14.
- Qt Windows PJSIP runtime is bundled under `qt-client/third_party/pjsip/windows`; the current `pjsua.exe` exposes video options.
- VS2017 Build Tools are now available at `D:\BuildTools\VS2017`; the Qt 5.9.3 MSVC2017 WebEngine package rebuild passes.
- Flutter arm64 real-media runtime is bundled and the Android bridge now uses pjsua2 before Android SIP fallback.
- Automated local media scope is closed when `scripts/verify-final-media-preflight.ps1` passes.
- Remaining acceptance is real-device only: a human or audio capture must confirm physical Qt/Android microphone and speaker audio in both directions, a human or screenshot capture must confirm each endpoint sees the other endpoint's camera image, and external-network TURN traversal must be exercised.

## Submission Note

Core delivery is ready for demo: JDK 1.8 server, Web/admin, Qt exe, Flutter apk, Docker Compose, PJSIP 2.14 gateway, coturn, Asterisk, and docs. Local Docker runtime, backend tests, Web build, live Docker API checks, TURN allocation, video capability report, content-level audio proof, final media preflight, and delivery artifact checks passed. Flutter APK bundles arm64 pjsua2 native runtime, opens a full call page, shows local camera preview, and bridges remote PJSIP video to Android `SurfaceView`. Qt uses PJSIP 2.14 Windows runtime with explicit `--video` and `--auto-conf`, plus a full call screen and local camera preview; VS2017 package rebuild passes. Remaining product caveat is real-device only: rendered remote camera quality, physical bidirectional voice heard through the devices, and external-network TURN traversal. Full PRD extras and production IAM are tracked as post-submit optimization in `project-docs/SUBMISSION_SCOPE.md`.
