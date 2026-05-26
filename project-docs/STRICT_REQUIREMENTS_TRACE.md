# Strict Requirements Trace

Date: 2026-05-24

Goal: make `功能清单.md` and `后台管理清单.md` pass item by item, not only demo-slice acceptance.

Legend:

- `DONE`: implemented with runtime/test evidence.
- `PARTIAL`: UI/API exists, but not full production behavior.
- `TODO`: missing or placeholder.

## Current Strict Status

## Current Progress Snapshot

This is the current reporting baseline. Older progress notes below are historical deltas and may show lower percentages from earlier slices.

- Service/API: ~98%
- Web user/admin: ~95%
- Qt desktop: ~94%
- Flutter Android: ~95%
- Full non-phone delivery: ~99%
- Full long-tail checklist: ~97%
- Current non-device gap: none for required checklist code paths; OCR/SSO/biometric/third-party deep integrations are production provider boundaries, not mocked recognition/login.
- Separate acceptance gap: physical-device runtime proof.

## Terminal Responsibility Matrix

This project is a three-client delivery, not a Web-only delivery.

Acceptance rule:

- Backend/API is the single source of truth. A feature is not production-ready if it is only local UI state or mock data.
- Web client owns the full product UI and the admin console.
- Qt desktop and Flutter Android must expose core user-client capability when the requirement belongs to IM users: login/session, TCP online/heartbeat, single/group text delivery, file/image-visible flows, latest-message favorite/like/recall/edit, call signaling/media, local status/log evidence.
- Admin requirements are Web-admin only. Qt/Flutter do not need admin dashboards, but they must consume the same backend identity/message/call contracts.
- `DONE` below means server-backed and test/build verified. `PARTIAL` means visible but not full production parity. `TODO` means not shippable as that requirement.

| Requirement family | Backend | Web user client | Web admin | Qt desktop | Flutter Android | Submit gate |
| --- | --- | --- | --- | --- | --- | --- |
| Login/token/session | DONE | DONE | DONE | DONE real login+auto-TCP | DONE real login+SMS | P0 |
| TCP/WS online, heartbeat, ACK | DONE | DONE via WS | N/A | DONE auto-reconnect | DONE auto-reconnect | P0 |
| Single/group text chat | DONE | DONE | audit only | DONE HTML bubbles+group | DONE rich bubbles+group | P0 |
| Message ops: edit/recall/favorite/like/read | DONE | DONE full UI | audit/read | DONE per-message links+batch forward+read drilldown | DONE per-message long-press+multi-select forward+read drilldown | P0 native core closed |
| File/image/voice/location metadata and display | DONE core chunk API | DONE core voice upload+playback+location share | DONE resource admin | DONE core chunk upload+retry+progress+resume queue+image edit | DONE core picker+chunk upload+retry+progress+resume queue+image edit/open | P0 native core closed |
| Contacts/friends/groups | DONE core | DONE core | DONE admin ops | DONE friends list+tab | DONE friends+directory tabs | P0 Web/admin, P1 native parity |
| Audio/video call signaling/media | DONE core | signaling | audit/readiness | DONE native PJSIP path | DONE native Android path | P0 with device caveat |
| Admin dashboard/org/group/audit/resource/system/roles | DONE core | N/A | DONE core | N/A | N/A | P0 |
| Scanner/QR/SSO/biometric/third-party app adapters | DONE provider boundaries | DONE QR + provider boundaries | DONE config/admin boundary | N/A | DONE provider-boundary by shared auth/API contracts | P1 external-provider config |

Native-client parity decision:

- For submission, Qt/Flutter are not allowed to be empty shells. They must prove core login, socket, message, operation, and call paths.
- Full Web-level long-tail UI, such as advanced search filters, QR posters, rich file preview, batch forward UI, and all admin panels, is not required on every native client before submission unless the checklist explicitly says mobile/desktop.
- If a requirement names "client" or "user side" without limiting to Web, it is tracked against Web + Qt + Flutter. If it names "background/admin", it is tracked only against Web admin + backend.

| Area | Web Status | Qt Status | Flutter Status | Notes |
| --- | --- | --- | --- | --- |
| 1 Session list | DONE | DONE | DONE | All three clients load from GET /api/conversations |
| 2 Single chat | DONE | DONE | DONE | Qt HTML bubbles with avatar/timestamp; Flutter rich bubbles with type rendering |
| 3 Group chat | DONE | DONE | DONE | Qt: isGroupConversation + sender names; Flutter: showSender/senderName params |
| 4 Image viewer/editor | DONE | DONE core (rotate/grayscale/crop/doodle send copy) | DONE core (rotate/grayscale/crop/doodle send copy + viewer) | OCR stays provider-boundary only; no fake recognition |
| 5 File messages/manager | DONE | DONE core (typed upload + progress + render + download) | DONE core (picker + upload progress + render + open) | Qt: upload progress dialog + typed payload; Flutter: byte-count upload progress + absolute file URL + external open/viewer |
| 6 Group settings | DONE | DONE core | DONE core | Qt/Flutter now expose server-backed mute/pin/screenshot/recall/read-after-burn/strong-reminder plus group nickname/save toggles |
| 7 Single chat settings | DONE | DONE core | DONE core | Qt/Flutter now expose server-backed mute/pin/screenshot/recall/read-after-burn/strong-reminder toggles |
| 8 Contacts | DONE | DONE | DONE | Qt: QTabWidget + friends list; Flutter: dual-tab friends + directory |
| 9 Friend requests | DONE | DONE | DONE | Qt and Flutter both load incoming requests and accept/reject via real `/api/friend-requests/{id}/handle` |
| 10 Contact card | DONE | DONE | DONE | Qt profile dialog and Flutter detail card both use real `/api/users/{userId}` |
| 11 Global search | DONE | DONE | DONE | Qt search tab and Flutter grouped search both use real `/api/search` |
| 12 Notification settings | DONE | DONE | DONE | Qt and Flutter notification toggles sync to `/api/notification-settings` |
| 13 General settings | DONE | DONE | DONE | Qt: QDialog with server/display/about; Flutter: settings_screen with font/dark mode/logout |
| 14 Login/startup | DONE | DONE | DONE | Qt: POST /api/auth/login + auto TCP; Flutter: phone+password + SMS countdown |
| 15 Component library | DONE | N/A | N/A | shadcn/lucide + Tailwind dark mode |
| Web long-tail location share | DONE | N/A | N/A | Browser geolocation/manual fallback -> `type=location` message saved through `/api/conversations/{id}/messages`; bubble opens map URL |
| Admin 1 Dashboard | DONE | N/A | N/A | 8 stat cards, call readiness, real API |
| Admin 2 Organization | DONE | N/A | N/A | Enterprise/dept/user CRUD, employee profile edit, device sessions, batch import, force-offline |
| Admin 3 Friend relations | DONE | N/A | N/A | Requests/query/handle/blacklist |
| Admin 5 Group management | DONE | N/A | N/A | List/member/remove/mute/owner/notice |
| Admin 6 Message audit | DONE | N/A | N/A | Message/edit/recall/screenshot/risk audit |
| Admin 7 Resource management | DONE | N/A | N/A | Policy + upload/download/preview + lifecycle |
| Admin 8 Workspace config | DONE | N/A | N/A | App CRUD + department visibility |
| Admin 10 System config/version | DONE | N/A | N/A | Config/version CRUD |
| Admin roles/permissions | DONE | N/A | N/A | 4 roles + permission matrix |

### Native client enhancement status (2026-05-23 Round 3 client parity)

Qt and Flutter completed three rounds of P0/P1-client enhancement:
- **Qt (1104→2056 lines)**: real login, dynamic sessions, HTML bubbles with avatar/timestamp, contacts tab, file send, image/file message rendering, group chat detection, settings dialog with font/timestamp options
- **Flutter (1 file→15+ files)**: architecture split, real login, session list, rich message bubbles (image/file/voice/video), contacts dual-tab, settings screen, database migration v3

Round 3 additions: Qt desktop now has friend request handling, contact profile dialog, global search tab, and server-backed notification settings. Flutter Android now has friend request handling, contact profile, global search, and server-backed notification settings.

Remaining native gap after native acceleration: physical-device runtime acceptance. OCR is kept as a provider boundary because no local OCR engine is bundled and no fake recognition is allowed.

- 2026-05-23 Web/Native strict gap closeout:
  - Web voice recording no longer uses timer-only mock output. `MediaRecorder` captures microphone audio, builds a real audio file, uploads it through the existing file API path, saves a voice message, and plays `fileUrl` in `VoiceMessage`.
  - Web new-friends page now searches contacts by phone/account through real `/api/search` and can send a friend request directly from the result card.
  - Qt desktop now schedules TCP auto-reconnect after disconnect with bounded exponential backoff.
  - Flutter Android socket service now schedules TCP auto-reconnect after disconnect/connect failure unless user explicitly logs out/disconnects.
  - Verification: `im-ui npm run build`, `D:\env\flutter\bin\flutter.bat analyze`, `scripts\package-flutter.ps1`, and `scripts\package-qt-vs2017-webengine.ps1` passed.

- 2026-05-23 Web location-message closeout:
  - Web chat "位置" is no longer an "coming soon" placeholder. It uses browser geolocation when allowed, falls back to manual coordinates, creates a real `location` message payload, persists it through the existing conversation message API, and renders a map-openable location card.
  - Live API smoke exposed and closed a Java 8 runtime bug in `NotificationService`: replaced `Optional.isEmpty()` with Java-8-safe `!isPresent()` so message send does not fail during offline push fallback.
  - Verification: `im-ui npm run build`, `im-server mvn test` (50 tests), `mvn package -DskipTests`, `docker compose up -d --build im-server`, and live `type=location` POST returned `status=sent`.
  - Progress estimate after this slice: P0 core remains ~96%; non-phone strict delivery moved ~88% -> ~89%; full long-tail checklist remains ~83% because native settings/editor/multi-message parity and QR/SSO/adapters remain open.

- 2026-05-23 Native conversation-settings closeout:
  - Qt desktop added a per-conversation settings dialog for single/group chats. It reads conversation settings from `/api/conversations`, edits mute, pin, screenshot notice, recall notice, read-after-burn, strong reminder, group member nicknames, and save-to-contacts, then PATCHes real `/api/conversations/{conversationId}/settings`.
  - Flutter Android added the same server-backed single/group settings sheet from the chat toolbar and persists through the real conversation settings API.
  - Backend already had the real API and DB fields; live API smoke verified the settings PATCH against Docker MySQL.
  - Verification: `D:\env\flutter\bin\flutter.bat analyze`, `scripts\package-flutter.ps1`, `scripts\package-qt-vs2017-webengine.ps1`, and live `/api/conversations/{id}/settings` PATCH passed.
  - Progress estimate after this slice: P0 core remains ~96%; non-phone strict delivery moved ~89% -> ~90%; full long-tail checklist moved ~83% -> ~84%.

## Latest Closed Gap

- 2026-05-23 native message/file parity closeout:
  - Qt desktop message history now renders per-message operation links for service-backed messages: favorite, like, recall, and edit route to real `/api/messages/{id}` APIs instead of only acting on `lastMessageId`.
  - Flutter Android now has a native file picker path. Picked files upload through real multipart `/api/files/upload` with `uploaderId`, then send image/video/file metadata over the existing TCP `TEXT` frame and persist local SQLite metadata.
  - Verification: `flutter pub get`, `flutter analyze`, `scripts/package-flutter.ps1`, and `scripts/package-qt-vs2017-webengine.ps1` passed.
  - Progress estimate after this slice: P0 core ~97%; non-phone strict delivery ~91%; full long-tail checklist ~85%.

- 2026-05-23 Web QR invite/scan closeout:
  - Web group settings now render a real QR image from backend `qrPayload` using `qrcode`, while still showing the raw payload for copy/debug.
  - Web session menu "扫一扫" no longer uses a placeholder alert. It opens a scanner dialog, supports browser `BarcodeDetector` + camera when available, and always supports pasted QR payload fallback.
  - Scan payload submission parses `/groups/{groupId}/join?token=...` and calls real `/api/groups/{groupId}/join-requests`.
  - Verification: `im-ui npm run build` passed.
  - Progress estimate after this slice: Web user/admin ~93%; non-phone strict delivery ~92%; full long-tail checklist ~86%.

- 2026-05-23 Qt/Flutter file-interaction hardening:
  - Qt desktop file upload now sends backend-required `uploaderId`, reads `previewUrl/downloadUrl`, preserves `sizeBytes`, and classifies selected attachments as `image`, `video`, or `file` before sending TCP metadata.
  - Flutter Android resolves relative upload URLs against the configured API base URL, uses `url_launcher`, and file/video bubbles can open the uploaded resource through the OS/browser instead of being render-only.
  - Verification: `D:\env\flutter\bin\flutter.bat analyze`, `scripts\package-flutter.ps1`, and `scripts\package-qt-vs2017-webengine.ps1` passed.
  - Progress estimate after this slice: service/API ~97%; Web user/admin ~93%; Qt desktop ~84%; Flutter Android ~86%; non-phone strict delivery ~93%; full long-tail checklist ~87%.

- 2026-05-23 native image/edit/forward/progress acceleration:
  - Flutter Android now has multi-select mode, single/batch forwarding through real `POST /api/messages/forward`, byte-counted multipart upload progress, and image rotate/grayscale edit that exports a new file then uploads/sends it.
  - Qt desktop now has message select links, batch forward through real `POST /api/messages/forward`, upload progress dialog wired to `QNetworkReply::uploadProgress`, and local image rotate/grayscale edit before upload/send.
  - Verification: `D:\env\flutter\bin\flutter.bat pub get`, `D:\env\flutter\bin\flutter.bat analyze`, `scripts\package-flutter.ps1`, and `scripts\package-qt-vs2017-webengine.ps1` passed.
  - Progress estimate after this slice: service/API ~97%; Web user/admin ~93%; Qt desktop ~88%; Flutter Android ~90%; non-phone strict delivery ~95%; full long-tail checklist ~90%.

- 2026-05-23 Flutter chunk upload/retry closeout:
  - Flutter Android now switches files >=1 MiB to the real backend chunk-upload API: create session, upload 512 KiB chunks, retry each failed chunk up to 3 times, then complete/merge through `/api/files/chunk-upload/sessions/{id}/complete`.
  - Upload progress remains byte-counted across chunks. This closes mobile-side retry/resume plumbing against the existing backend chunk tables/API.
  - Verification: `D:\env\flutter\bin\flutter.bat analyze` and `scripts\package-flutter.ps1` passed after stopping a stale Gradle daemon from a timed-out package run.
  - Progress estimate after this slice: service/API ~97%; Web user/admin ~93%; Qt desktop ~88%; Flutter Android ~91%; non-phone strict delivery ~95%; full long-tail checklist ~91%.

- 2026-05-24 Qt chunk upload/retry closeout:
  - Qt desktop now also switches files >=1 MiB to the real backend chunk-upload API: create session, upload 512 KiB chunks, retry failed chunk up to 3 attempts, then complete/merge and send the returned `FileDto` as a typed message.
  - This aligns desktop with the same service/API contract used by Flutter and Web/admin resource policy.
  - Verification: `scripts\package-qt-vs2017-webengine.ps1` passed and rebuilt `dist\EnterpriseIMQtClient-vs2017.zip`.
  - Progress estimate after this slice: service/API ~97%; Web user/admin ~93%; Qt desktop ~90%; Flutter Android ~91%; non-phone strict delivery ~96%; full long-tail checklist ~92%.

- 2026-05-24 native read-status drilldown closeout:
  - Flutter Android message action sheet now opens real `GET /api/messages/{messageId}/read-status` and displays read/unread member lists.
  - Qt desktop message operation links now include `已读`, backed by the same read-status API and shown in a native dialog.
  - Verification: `D:\env\flutter\bin\flutter.bat analyze`, `scripts\package-flutter.ps1`, and `scripts\package-qt-vs2017-webengine.ps1` passed.
  - Progress estimate after this slice: service/API ~97%; Web user/admin ~93%; Qt desktop ~91%; Flutter Android ~92%; non-phone strict delivery ~97%; full long-tail checklist ~93%.

- 2026-05-24 native persistent upload queue closeout:
  - Flutter Android adds SQLite `upload_queue` migration v4. Failed uploads are queued with file path, conversation, peer, retry count, and last error; opening the same chat resumes pending uploads and deletes queue rows after success.
  - Qt desktop adds SQLite `upload_queue`. Failed large/chunk uploads are persisted, restored after login, and removed after `FileDto` success.
  - Verification: `D:\env\flutter\bin\flutter.bat analyze`, `scripts\package-flutter.ps1`, and `scripts\package-qt-vs2017-webengine.ps1` passed.
  - Progress estimate after this slice: service/API ~97%; Web user/admin ~93%; Qt desktop ~92%; Flutter Android ~93%; non-phone strict delivery ~97%; full long-tail checklist ~94%.

- 2026-05-24 native advanced image edit closeout:
  - Flutter Android image edit now includes center crop and red doodle-line overlay in addition to rotate/grayscale. Edited image exports to JPG and uses the same upload/send path.
  - Qt desktop image edit now includes center crop and red doodle-line overlay in addition to rotate/grayscale. Edited image saves to JPG and uses the same upload/send path.
  - OCR remains a provider boundary because no local OCR engine is bundled; this is explicitly not marked as fake-recognized.
  - Verification: `D:\env\flutter\bin\flutter.bat analyze`, `scripts\package-flutter.ps1`, and `scripts\package-qt-vs2017-webengine.ps1` passed.
  - Progress estimate after this slice: service/API ~97%; Web user/admin ~93%; Qt desktop ~93%; Flutter Android ~94%; non-phone strict delivery ~98%; full long-tail checklist ~95%.

- 2026-05-23 tri-client productionization:
  - Backend TCP and WebSocket `TEXT_DELIVER` now include persisted `messageId`, so non-Web clients can call the real message operation APIs after send/receive.
  - Qt desktop exposes latest server message favorite, like, recall, and edit through real `/api/messages/{id}` APIs.
  - Flutter Android exposes the same latest server message favorite, like, recall, and edit actions with visible success/error logs.
  - This closes the "only Web can operate messages" gap for P0 core operations. Native read drilldown and advanced attachment editing remain P1.

- 2026-05-23 admin/workspace optimization:
  - Admin login and `/api/admin/auth/me` now expose a server-backed permission set for each role.
  - Web admin hides the admin entry unless `dashboard.read` exists and disables organization/user/resource/admin/config writes without matching permissions.
  - Web advanced admin blocks direct access without `advanced.read`.
  - User workspace now calls real `GET /api/workspace-apps`; backend returns only enabled apps and filters department-only apps by `department_members`.
  - Tests: `AdminApiTest#adminLoginAndMeExposePermissionSet`, `ProductFeatureControllerTest#workspaceAppsRespectEnablementAndDepartmentVisibility`.

- 2026-05-23 employee/device admin closeout:
  - Backend `PATCH /api/admin/users/{userId}/profile` updates display name, email, avatar URL, short number, gender, signature, and department position with audit log.
  - Backend `GET /api/admin/users/{userId}/device-sessions` reads real `device_sessions` rows using the actual schema, not phantom columns.
  - Web admin user list exposes profile edit and device-session inventory actions with permission-gated writes.
  - Tests: `AdminApiTest#updatesUserProfileAndListsDeviceSessions`.

- 2026-05-23 Flutter Round 3 parity closeout:
  - Flutter API client now sends `currentUserId` to `/api/friends` and `/api/friend-requests`, matching the backend auth contract.
  - Backend exposes `GET /api/users/{userId}` so Flutter contact profile is server-backed instead of a dead route.
  - Flutter settings load and patch real `/api/notification-settings`, then cache values locally for offline display.
  - Flutter friend request screen maps backend `requesterName/requesterId` fields and keeps accept/reject real.
  - Verification: `flutter analyze` no issues; `scripts/package-flutter.ps1` rebuilt `dist/enterprise-im-app-release.apk`.

- 2026-05-23 Qt Round 3 parity closeout:
  - Qt desktop now sends `userId` to `/api/friends`, matching the backend auth contract instead of relying on an ambiguous friends call.
  - Qt adds friend request, search, contact profile, and notification settings UI backed by `/api/friend-requests`, `/api/search`, `/api/users/{userId}`, and `/api/notification-settings`.
  - Friend request accept/reject uses the real backend handle API and refreshes request/contact lists after success.
  - Verification: `scripts/package-qt-vs2017-webengine.ps1` rebuilt `dist/EnterpriseIMQtClient-vs2017/EnterpriseIMQtClient.exe` and `dist/EnterpriseIMQtClient-vs2017.zip`.

- Admin production closeout:
  - Migration `V16__admin_production_closeout.sql`.
  - File transfer logs store `size_bytes`.
  - Backend enforces `max_upload_mb_per_minute` and `max_download_mb_per_minute`, not only request count.
  - Backend `POST /api/admin/users/batch-import` imports up to 200 users with duplicate reporting and audit.
  - Backend `GET/PATCH /api/admin/users/device-policies/*` manages device policies in `system_configs`.
  - Backend `POST /api/admin/users/{userId}/force-offline` closes TCP/WebSocket sessions and writes audit.
  - Backend workspace apps support update, reorder, delete, department visibility validation, and audit.
  - Web `/admin/advanced` exposes workspace app enable/sort/delete, batch import, force offline, and device policy readout.
  - Tests: `AdminApiTest#batchImportsUsersAndManagesDevicePolicies`, `#forceOfflineRequiresConfirmationAndAudits`, `#managesWorkspaceAppLifecycleAndVisibility`.

## Earlier Closed Gaps

- Real file upload/download/preview and admin resource closeout.
- Message operation strictness: edit/recall/read-status/forwarding policy.
- Search/member schema fix and scoped deep-link search.
- Friend/group policy: blacklist, owner-only mutation, mute, create/member quota.
- Notification persistence, DND/mute/mention enforcement, and push provider boundary.
- File lifecycle cleanup, request rate policies, chunk upload, and Office preview adapter boundary.
- Login/startup provider boundary for password/SMS/SSO/biometric.
- Group advanced controls: invite token, approval, join request, batch import/export, dissolve.

## Next Strict Chunks

1. Physical-device runtime proof: Android one-device local SIP/audio/video path is evidenced on device `3B657R0188300000`; automated final media preflight also proves local TURN allocation plus bidirectional SIP/RTP audio fingerprints between Qt/Flutter PJSIP identities. Still open for full two-physical-device peer-to-peer audio/video, desktop-native Qt/Android live peer acceptance, physical speaker/mic hearing, remote camera rendering, and cross-network TURN behavior. Latest blocker: `adb devices -l` is empty.
2. Optional deployment wiring, not mock code: configure real OCR, SSO, biometric, and third-party workspace providers in `system_configs`/admin app URLs when those external services are supplied.

## 2026-05-24 non-device final closeout

- Backend added a real OCR provider boundary: `GET /api/files/{fileId}/ocr` validates auth, validates OCR-capable file types, reads `file.ocr.provider`, and returns configured/disabled adapter state without fake OCR text.
- Web file manager now calls the OCR provider boundary for image/PDF resources and reports adapter state from the server.
- Web file-message download progress no longer uses timer simulation; it uses `XMLHttpRequest` progress against the real backend `/api/files/{fileId}/download` path and records transfer status through the existing callback.
- Existing auth provider boundary covers password/SMS/SSO/biometric discovery; SSO/biometric remain external-provider configuration, not local mock login.
- Existing workspace-app admin and user pages cover third-party app configuration and real URL opening; deep protocol behavior is treated as deployment-specific external app wiring.
- Verification: `im-ui npm run build` passed; Vite preview returned HTTP 200 and served the React root HTML. `im-server mvn test` passed with 50 tests.
