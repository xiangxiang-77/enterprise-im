# Submission Scope

Date: 2026-05-20

## Submit-Ready Scope

- Java 8 compatible Spring Boot backend with HTTP API, Netty TCP gateway, DB migrations, Docker image, and 27 passing tests.
- React/Vite Web client and admin console with real login, message history, WebSocket text delivery, call signaling, call audit, and admin resource panels.
- Qt 5.9.3 VS2017 Windows package with WebEngine, SQLite, TCP/API call signaling, bundled PJSIP 2.14 `pjsua.exe`, and audio/video call screen.
- Flutter Android APK with TCP/API call signaling, local SQLite cache, bundled arm64 PJSIP/pjsua2 runtime, camera preview, and Android remote video surface bridge.
- Docker Compose runtime: MySQL 8.0, Redis, MinIO, coturn, Asterisk, PJSIP gateway, and im-server.
- Automated checks: backend tests, Web build, Docker live API, TURN allocation, video capability, native runtime bundle, and SIP PCMU/RTP media loop.
- Delivery artifacts: `dist/EnterpriseIM-Delivery-Package.zip`, `dist/EnterpriseIMQtClient-vs2017.zip`, `dist/enterprise-im-app-release.apk`, backend jar, source code, scripts, and docs.

## Explicit Non-Blocking Limits

- Physical-device acceptance is still required for Android microphone/speaker/camera behavior, Qt microphone/speaker/camera behavior, remote camera rendering quality, and external-network TURN traversal.
- Browser/Web still supports real text messaging and WebRTC call signaling, but production-grade Web media is not the primary PJSIP acceptance path; Qt and Flutter are the native-media clients.
- Demo auth remains intentionally simple for assessment: user tokens use `DEMO_TOKEN_PREFIX`, and admin password is set with `ADMIN_PASSWORD`. Replace with JWT/OIDC before production use.
- Scope rule for three ends: Web owns the complete user UI and admin console; Qt and Flutter must still prove core login, TCP session, text messaging, latest-message favorite/like/recall/edit, and call signaling/media. Admin dashboards are Web-admin only.
- File/image/message advanced product features are split strictly: server-backed Web/admin core flows are P0; full Qt/Flutter rich attachment parity, multi-select operations, QR/scanner flows, semantic search, and third-party integrations are P1 unless listed as a required native-core flow in `STRICT_REQUIREMENTS_TRACE.md`.

## Post-Submit Optimization Backlog

1. Real-device acceptance report with screenshots/logs for Qt and Android audio/video.
2. Replace demo token auth with JWT/OIDC, password hashing, refresh tokens, and role-scoped claims.
3. Complete full file flow: upload/download progress, preview, retry, type policy, storage lifecycle, and audit.
4. Complete advanced message operations parity: native multi-message selection, forward target picker, read/unread member drilldown, edit history display, recall audit display, and burn-after-reading UX.
5. Complete friend/group policy backend: friend applications, blacklist recovery, group ownership transfer, group announcement publishing, and admin intervention.
6. Add CI packaging pipeline and signed release artifacts.
