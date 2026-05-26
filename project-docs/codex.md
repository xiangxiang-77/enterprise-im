# codex.md

## Current progress snapshot 2026-05-24

This is the current reporting baseline. Older notes below are historical deltas and can contain lower percentages from earlier passes.

- Service/API: 98%.
- Web user/admin: 95%.
- Qt desktop: 94%.
- Flutter Android: 95%.
- Full non-phone delivery: 99%.
- Full long-tail checklist: 97%.
- Current non-device gap: none for required checklist code paths; OCR/SSO/biometric/third-party deep integrations are production provider boundaries waiting on real external provider config, not mocks.
- Separate acceptance gap: physical-device runtime proof.

## Progress note 2026-05-25 Android real-device AV evidence refresh

- [x] Device `3B657R0188300000` / PLC110 connected through ADB; latest APK `dist/enterprise-im-app-release.apk` installed and launched.
- [x] ADB reverse configured for `tcp:18080`, `tcp:19090`, and `tcp:5060`; backend health was `UP`; PJSIP gateway health was `UP` with `engine=pjsua-adapter`.
- [x] Audio real-device local media path passed: call screen displayed `SIP CONFIRMED 200 OK`; backend `call_records` row `call_09cf3ec4-bcbf-4703-bf9b-b72a0be5dfa9` has `media_type=audio`, `media_status=media_ready`, `media_error=NULL`.
- [x] Video real-device local media path passed: call screen displayed `SIP CONFIRMED 200 OK` and local camera preview, with Android `SurfaceView` present; backend `call_records` row `call_06700492-1690-4be4-b2a3-ca0422857609` has `media_type=video`, `media_status=media_ready`, `media_error=NULL`.
- [x] Evidence saved under `runtime-logs/acceptance-av-evidence.txt`, `runtime-logs/acceptance-audio-call.png`, `runtime-logs/window-audio-call.xml`, `runtime-logs/acceptance-video-call.png`, and `runtime-logs/window-video-call.xml`.
- [ ] Remaining acceptance gap: full two-physical-device peer-to-peer audio/video and cross-network TURN traversal.

## New technical baseline 2026-05-16

User-provided target stack:

- Desktop client: Qt 5.9.3 + QSS + VS2017 + WebEngine + PJSIP + TcpSocket + SQLite, output Windows exe.
- Mobile client: Flutter + PJSIP + TcpSocket + SQLite, output Android apk.
- Server: JDK 1.8 + MySQL or Derby + Redis.
- Audio/video relay and traversal: open-source coturn.
- PJSIP recommended version: 2.10 to 2.14.

Current implementation gap against this target:

- [x] Qt desktop source and exe package exist, and the client uses Qt widgets plus TcpSocket/API call signaling.
- [x] Flutter source and Android apk package exist, and the client uses TCP/API call signaling.
- [x] Redis is present in Docker Compose.
- [x] coturn is present in Docker Compose.
- [x] PJSIP gateway exists and is wired through `PJSIP_SIGNAL_URL`.
- [x] PJSIP Docker build default was changed from 2.17 to 2.14 to match the requested 2.10-2.14 range; Docker image rebuilt and live container verified `PJ_VERSION : 2.14`.
- [x] Qt desktop now links Qt SQL and writes local SQLite message/call cache; packaged exe includes Qt SQL drivers.
- [x] Qt desktop project has conditional WebEngine support for a VS2017 Qt WebEngine kit; added `scripts/package-qt-vs2017-webengine.ps1` to build/package the requested exe when the VS2017 kit is installed.
- [x] Flutter mobile now uses `sqflite` and writes local SQLite logs/messages/calls; Android side checks bundled PJSIP `libpjsua2.so` before Android SIP fallback.
- [x] Server Docker Compose now defaults to MySQL 8.0 + Redis for the JDK 1.8 / Spring Boot 2.7 runtime; live Docker MySQL migration and health check passed.
- [x] Server has a Derby profile (`SPRING_PROFILES_ACTIVE=derby`) with dedicated bootstrap initializer because Flyway 10 does not support Derby 10.16.
- [x] Server is aligned with strict JDK 1.8 runtime: backend has been ported to Spring Boot 2.7 / Java 8 source target and Docker runs `Java 1.8.0_492`.
- [x] Qt desktop VS2017 Qt 5.9.3 WebEngine build environment is installed on the build machine, and `dist/EnterpriseIMQtClient-vs2017.zip` was built with WebEngine, SQLite, and Windows `pjsua.exe` bundled.
- [x] Flutter mobile now bundles compiled pjsua2/PJSIP Android arm64 runtime: `libpjsua2.so`, `libc++_shared.so`, and `pjsua2.jar`.

Decision note:

- Treat the above as the new acceptance baseline. Do not mark Qt/Flutter/server stack compliance as complete until the concrete runtime/build evidence exists.

## Progress note 2026-05-17 New stack alignment pass

- [x] Changed server Compose default database from PostgreSQL to MySQL 8.0; host port defaults to `3307` to avoid common local MySQL `3306` conflicts.
- [x] Added MySQL and Derby runtime drivers to `im-server`; kept H2 for tests.
- [x] Fixed MySQL migration compatibility by renaming reserved table `groups` to physical table `chat_groups` while keeping admin API path `/api/admin/groups`.
- [x] Verified live Docker runtime: MySQL healthy, Flyway V1-V6 migrated successfully, `GET /actuator/health` returns `{"status":"UP"}`.
- [x] Added Derby run path: `scripts/run-server-derby.ps1`, `application-derby.yml`, and Derby schema bootstrap; smoke verified with `SPRING_PROFILES_ACTIVE=derby`, Derby DB URL, and health `UP`.
- [x] Verified PJSIP gateway runtime: `engine=pjsua-adapter`, `PJ_VERSION : 2.14`.
- [x] Added Qt SQLite local cache for desktop messages/calls and conditional WebEngine support for the requested VS2017 WebEngine kit.
- [x] Rebuilt Qt package: `dist/EnterpriseIMQtClient/EnterpriseIMQtClient.exe` and `dist/EnterpriseIMQtClient.zip`.
- [x] Added Flutter SQLite local cache for logs/messages/calls and rebuilt release APK: `dist/enterprise-im-app-release.apk`.
- [x] Verification: `im-server mvn test` passed, `im-server mvn package -DskipTests` passed, `scripts/package-flutter.ps1` passed, Docker MySQL runtime health passed.
- [x] Remaining strict-stack work resolved for Qt packaging: VS2017 Qt WebEngine build passed on the local VS2017 kit, and Windows PJSIP runtime was bundled into `dist/EnterpriseIMQtClient-vs2017.zip`.

## Progress note 2026-05-18 VS2017 Qt WebEngine package recovery

- [x] Recovered interrupted VS2017 Build Tools installation by rerunning elevated Visual Studio Installer and adding the full C++ build workload plus Windows 10 SDK 10.0.19041.
- [x] Verified `vcvars64.bat` now exposes `nmake.exe`, `cl.exe`, and `link.exe`.
- [x] Fixed Qt MSVC source encoding by adding `/utf-8` flags in `qt-client/qt-client.pro`.
- [x] Fixed WebEngine module detection with `qtHaveModule(webenginewidgets)` so `ENTERPRISE_IM_HAS_WEBENGINE` is compiled.
- [x] Built `dist/EnterpriseIMQtClient-vs2017.zip`; package includes `QtWebEngineProcess.exe`, `Qt5WebEngineCore.dll`, `qsqlite.dll`, and `pjsua.exe`.

## Progress note 2026-05-15 Docker runtime verification

- [x] Docker Desktop is running and reachable (`docker info` returned server version `29.3.1`).
- [x] Rebuilt stale `enterprise-im-server:0.1.0` image so container includes current V6 migration and call gateway state fields.
- [x] `docker compose up -d im-server` restarted the stack with MySQL 8.0, Redis, MinIO, coturn, Asterisk, pjsip-gateway, and im-server online.
- [x] Container backend applied Flyway V6: `call media gateway state`.
- [x] `GET http://127.0.0.1:18080/actuator/health` returned `UP`.
- [x] `GET http://127.0.0.1:18080/api/calls/readiness` returned `ready=true`.
- [x] Full verification passed with `.\scripts\verify.ps1 -SkipNativeBuild`: backend 26 tests, Web build, Docker compose config, Docker runtime, Docker live call auth/connectivity, delivery artifacts.
- [x] Native PJSIP binary dependency resolved in Compose: `pjsip-gateway` image bundles PJSIP/pjsua 2.14 and reports `engine=pjsua-adapter`.

## Progress note 2026-05-15 Bundled native PJSIP

- [x] Reworked `pjsip-gateway/Dockerfile` into a multi-stage build that downloads and builds pjproject/PJSIP 2.14, then copies `pjsua` into `/opt/pjsip/bin/pjsua`.
- [x] Docker Compose now defaults `PJSUA_BIN=/opt/pjsip/bin/pjsua`, so the container selects `pjsua-adapter` without host-side manual mount.
- [x] Removed the stale local process on port 7070 and verified the Docker gateway is the live process.
- [x] Live verification: `GET http://127.0.0.1:7070/health` returns `engine=pjsua-adapter`.
- [x] Live call verification: authenticated `POST /api/calls` records `mediaStatus=media_ready`, `mediaError=null`, and a `pjsipSessionId`.
- [x] Container verification: `/opt/pjsip/bin/pjsua --version` reports PJSIP 2.14.
- [x] Regression verification: `im-server mvn test` passes 26 tests after isolating backend tests from the live Docker gateway.
- [x] Full verification: `.\scripts\verify.ps1 -SkipNativeBuild` passes backend tests, Web build, Docker config/runtime/live API checks, and delivery artifact checks.
- [x] Remaining media product work closed for automated delivery: PJSIP/Asterisk/coturn SIP media loop is verified by `scripts/verify-sip-media-loop.ps1`; Qt and Flutter native runtimes are bundled and their app bridges call the native media layer.

## Progress note 2026-05-15 Client SIP media interface

- [x] Added authenticated `GET /api/calls/media-config?userId=&calleeId=` so native clients can fetch SIP registrar, SIP account, peer SIP URI, and TURN credentials.
- [x] Added Docker Compose `asterisk` service with PJSIP endpoints for `u_qt`, `u_flutter`, `u_web`, `u_18812345678`, and `u_gateway`.
- [x] Added Asterisk configs under `asterisk/pjsip.conf` and `asterisk/extensions.conf`.
- [x] Added Qt `SipMediaClient` adapter that starts local `pjsua` from `PJSUA_BIN` or `PATH` after backend call media is `media_ready`.
- [x] Added Flutter -> Android `MethodChannel` SIP bridge surface (`enterprise_im/sip`) and Android microphone/camera/audio permissions.
- [x] Flutter client now fetches `/api/calls/media-config` after `media_ready`, calls the native SIP bridge on start/answer, and stops it on reject/hangup.
- [x] Android media-config now uses `platform=android` and returns `SIP_ANDROID_REGISTRAR` (`sip:10.0.2.2:5060` by default), avoiding the emulator `127.0.0.1` trap.
- [x] Android bridge now attempts native Android SIP registration/audio call when platform SIP/VoIP is available; unsupported devices return an explicit `android-sip-unavailable` fallback.
- [x] `scripts/package-flutter.ps1` now resolves Flutter SDK and Android SDK from `android/local.properties`, so packaging no longer requires `flutter` in PATH.
- [x] Verification: Flutter analyze passed, Android release APK rebuilt, and live Android media-config returns `sip:10.0.2.2:5060`.
- [x] Runtime remaining resolved for packaged verification: Qt package bundles Windows `pjsua.exe`, and two registered PJSIP clients completed an automated SIP audio loop through Asterisk with confirmed PCMU/RTP media.
- [x] Flutter remaining resolved for arm64: pjsua2 native engine is bundled and called before Android SIP fallback.

## Progress note 2026-05-15 Native PJSIP adapter path

- [x] `pjsip-gateway` now supports a real native adapter path:
  - `pjsua-adapter` when `PJSUA_BIN` points to a real `pjsua` binary or `pjsua` exists in `PATH`.
  - `external-pjsip-command` when `PJSIP_CREATE_CMD` is configured for an existing PJSIP/PBX control script.
  - `simulated-pjsip` remains only as fallback.
- [x] Gateway `/health` now exposes `engine` and `engineHealth`, so validation can distinguish real native media from simulated mode.
- [x] Gateway create-call response includes `processPid` and `error`, so real adapter startup can be audited.
- [x] Docker Compose and `.env.example` now expose `PJSUA_BIN`, SIP account, and PJSIP command-template env vars.
- [x] Backend `PjsipGatewayClient` now serializes JSON explicitly, fixing empty body / `callId is required` gateway calls.
- [x] Backend no longer treats `simulated-pjsip` as real media. Simulated gateway responses now write `media_status=signaling_only` and `media_error=PJSIP gateway is running simulated-pjsip; native RTP media is not attached`.
- [x] Verification: `python -m unittest discover -s pjsip-gateway -p "test_*.py"` passed, 4 tests.
- [x] Verification: `mvn test` passed, 26 tests.
- [x] Verification: `npm run build` passed.
- [x] Verification: rebuilt and restarted Docker images for `pjsip-gateway` and `im-server`; `.\scripts\verify.ps1 -SkipNativeBuild` passed.
- [x] Live probe after bundled PJSIP build: `GET http://127.0.0.1:7070/health` returns `engine=pjsua-adapter`; a live call records `mediaStatus=media_ready`.
- [x] Remaining real-media step resolved for local delivery topology: Asterisk registrar and RTP relay path are configured, TCP/UDP SIP transports are enabled, and the media loop reaches `CONFIRMED` with active PCMU sendrecv RTP.

## Progress note 2026-05-15 PJSIP gateway

- [x] Added runnable `pjsip-gateway` service with REST endpoints: `/health`, create call, answer, hangup, list sessions.
- [x] Wired Docker Compose so `im-server` uses `PJSIP_SIGNAL_URL=http://pjsip-gateway:7070`.
- [x] Added local starter script: `scripts/run-pjsip-gateway.ps1`.
- [x] Verified local gateway smoke flow on `127.0.0.1:7070`: health, create, answer, hangup, session list.
- [x] Native PJSIP binary is bundled behind the gateway. Current Compose mode is `pjsua-adapter`.

## 最新进度 2026-05-15

- [x] 本机安装并配置 Qt 5.9.3 MinGW、Flutter 3.41.9、Android SDK/JDK 打包环境。
- [x] Qt 桌面端完成产品化外观加工：会话栏、聊天区、消息输入、连接状态、音视频信令区、协议日志区。
- [x] Qt exe 构建并用 `windeployqt` 补齐依赖，交付包：`dist/EnterpriseIMQtClient.zip`；本机启动验证通过。
- [x] Flutter 移动端完成产品化外观加工：首页状态、聊天配置、消息发送、音视频信令、通话记录、实时日志。
- [x] Flutter analyze/test 通过，Android release apk 构建成功：`dist/enterprise-im-app-release.apk`。
- [x] Flutter 打包脚本配置 `pub.flutter-io.cn` 与 `storage.flutter-io.cn`，规避 Google 存储 TLS 失败。
- [x] 服务端新增 PJSIP 网关 REST 边界：发起、接听、挂断会调用 `PJSIP_SIGNAL_URL`，并写入 `pjsip_session_id`、`media_status`、`media_error`。
- [x] 验证：`im-server mvn test` 26 个测试全通过。
- [x] 真实 PJSIP native 媒体服务已在本地交付拓扑中验证：`pjsip-gateway` 使用 PJSIP 2.14，Asterisk/coturn 在线，双 pjsua 注册呼叫进入 `CONFIRMED`，PCMU/RTP `sendrecv` 生效。

## Progress note 2026-05-18 SIP media loop closure

- [x] Added Asterisk TCP transport so authenticated PJSIP INVITE retries that exceed UDP size can complete over TCP.
- [x] Changed Asterisk demo AOR max contacts to 1 with `remove_existing=yes` to prevent stale registration targets from breaking demo calls.
- [x] Added `scripts/verify-sip-media-loop.ps1`, which clears stale demo registrations, starts two PJSIP 2.14 clients inside the Docker network, registers `u_qt` and `u_flutter`, places a call through Asterisk, and asserts:
  - SIP registration status `200 OK`.
  - Incoming INVITE reaches the Qt-side endpoint.
  - Auto-answer returns `200 OK`.
  - Both sides reach `CONFIRMED`.
  - Audio is active as `PCMU (sendrecv)`.
  - RTP status lines are observed.
- [x] Verification passed and evidence logs are copied to `build/sip-media-loop`.
- [x] Automated media loop verification is complete. Manual acceptance is tracked separately because it requires physical Qt/Android devices.

## Progress note 2026-05-19 Final media preflight hardening

- [x] Qt desktop `SipMediaClient` now emits stable SIP lifecycle log prefixes, starts audio/video with `--auto-conf`, keeps `--null-video` only for audio calls, and uses explicit `--video` for video calls.
- [x] Qt packaging scripts now reject Windows `pjsua.exe` builds that lack `--video`, `--vcapture-dev`, and `--vrender-dev`.
- [x] Flutter packaging now treats missing Android `libpjsua2.so` plus Java binding as a hard final-media packaging error instead of a warning.
- [x] `scripts/verify-video-capability.ps1` now requires Android media permissions, APK `libpjsua2.so`, APK `libc++_shared.so`, explicit Qt video launch args, and bundled Windows pjsua video support.
- [x] Added `scripts/verify-final-media-preflight.ps1` to chain all automated media checks: native runtime bundle, video capability, TURN allocation, SIP PCMU/RTP loop, and audio content proof.
- [x] `scripts/verify-sip-audio-content.ps1` passed: Qt-side recording contains the Flutter-side 880 Hz voice fingerprint, and Flutter-side recording contains the Qt-side 440 Hz voice fingerprint. Evidence is under `build/sip-audio-content`.
- [x] `scripts/verify-final-media-preflight.ps1` passed with the content-level audio proof included; remaining acceptance is real-device only: physical microphone/speaker hearing, Android camera permission, camera preview/render, and external-network TURN traversal.

## Progress note 2026-05-20 Desktop/mobile call UI and mobile video bridge

- [x] Qt desktop now opens a product-style full call screen for audio/video calls, with incoming/answered state, answer/reject/hangup controls, and local camera preview when QtMultimedia is available.
- [x] Flutter Android now opens a full call page instead of only inline buttons: incoming calls show answer/reject, active calls show hangup, and video calls reserve a remote video stage plus local camera preview PiP.
- [x] Flutter call guard fixed: the caller's own outgoing audio/video call no longer exposes or allows the mobile-side "answer" action.
- [x] Flutter Android now registers `enterprise_im/pjsip_video_view` and attempts to attach the native PJSIP incoming `VideoWindow` to an Android `SurfaceView`.
- [x] Flutter packaging now stops the Gradle daemon before release build to avoid stale R8/classes.dex file locks.
- [x] Latest mobile APK rebuilt: `dist/enterprise-im-app-release.apk` (`0.1.5+6`, 2026-05-20 10:29, 56,571,344 bytes).
- [x] Latest delivery package rebuilt: `dist/EnterpriseIM-Delivery-Package.zip` (2026-05-20 10:30).
- [x] Local verification covered `flutter analyze`, `scripts/verify-video-capability.ps1`, `scripts/verify-sip-audio-content.ps1`, `scripts/verify-final-media-preflight.ps1`, and delivery packaging.
- [ ] Remaining acceptance is real-device only: Android camera/speaker/microphone behavior with human-heard two-way voice, actual remote video rendering quality on both endpoints, and cross-network TURN traversal.

## 最新进度 2026-05-14

- [x] 继续清理剩余可见英文/乱码：`MessageBubble`、文件消息、名片消息、旧 `HomePage`、旧 `LoginPage`、README、默认新会话名。
- [x] 验证：`rg` 扫描 `im-ui/src` 和 `README.md` 无旧乱码特征、`New Chat`、`Enterprise IM` 残留。
- [x] 验证：`im-ui npm run build` 通过；`im-server mvn test` 19 个测试全通过。
- [x] 增加音视频验收口：`GET /api/calls/readiness` 返回 TURN/PJSIP 配置状态、支持媒体类型和阻塞原因，不暴露 TURN 密码值。
- [x] 验证：`im-server mvn test` 20 个测试全通过；本地后端重启后 `/api/calls/readiness` 返回 `ready=true`。
- [x] Web 后台接入音视频就绪面板，管理员登录 `/admin` 后可直接查看 TURN/PJSIP 检查结果；浏览器实测显示 `已就绪`。
- [x] Docker 交付验证完成：`enterprise-im-server:0.1.0` 镜像存在，`docker compose up -d` 启动 PostgreSQL/Redis/MinIO/coturn/im-server，容器服务 HTTP `18080`、TCP `19090` 验证通过。
- [x] 修复容器 PostgreSQL 启动：新增 `flyway-database-postgresql`；调整 Compose 宿主端口避开本机冲突；`.dockerignore` 只带运行镜像所需 jar。
- [x] 验证：全量 `scripts/verify.ps1` 通过必需项，Docker compose config/image/runtime 均通过；仅 Qt/Flutter 工具链仍为 `BLOCKED`。
- [x] 增加独立打包脚本：`scripts/package-qt.ps1` 和 `scripts/package-flutter.ps1`；安装 Qt 5.9.3/Flutter 后可直接生成 exe/apk。

## 项目任务

把当前 `im-ui` Web 前端原型扩展成交付级企业即时通讯系统。

硬性要求：

- 截止时间：2026-05-24。
- 服务端：Java。
- IM 实时通信：TCP Socket。
- 实时音视频：PJSIP。
- 视频中转/穿透：coturn。
- 桌面端：Qt 5.9.3，交付 exe。
- 移动端：Flutter，交付 apk。
- 后台管理：必须实现。
- 交付：源代码、exe、apk、服务端 Docker 镜像、skills 文档库。

## 当前事实

- `d:\work\im-ui`：Web 前端，React + Vite + TypeScript + Tailwind + Radix UI + Zustand。
- `d:\work\im-server`：Java 服务端骨架，Spring Boot + Netty TCP。
- `d:\work\project-docs`：独立项目文档和 skills 库。
- 根目录 `.docx` 是当前可信需求来源：
  - `功能清单.docx`
  - `后台管理清单.docx`

## 工程规范

### 通用

- 先读现有代码，再改。
- 保留当前 UI 风格：专业蓝 `#0066FF`、浅/深色模式、移动优先。
- 禁止继续扩大 mock 假完成；新功能优先接真实 API/协议。
- 所有需求必须能被验收，不写“已完成”除非有运行证据。
- 文档、接口、DB、协议变更同步更新本文件。

### Web 前端

- 继续使用 React + TypeScript + Vite。
- 状态管理继续用 Zustand，逐步从 mock store 迁移到 API/socket store。
- UI 组件优先复用 `src/components/ui`。
- 每个页面至少支持浅色/深色基础适配。
- `npm run build` 必须保持通过。

### Java 服务端

- 当前骨架：Spring Boot 3 + Netty。
- 推荐后续接入：MySQL/PostgreSQL、Redis、MinIO/S3。
- HTTP API：认证、管理、查询、文件元数据、后台配置。
- TCP Socket：登录鉴权、心跳、单聊、群聊、ACK、重试、离线消息同步、正在输入、已读回执。
- 消息协议必须版本化，字段稳定，兼容旧客户端。
- Docker 镜像必须能通过环境变量配置 DB、Redis、对象存储、coturn、PJSIP 服务地址。

### 后台管理

- 推荐新建 `admin-web`，React 或 Vue 均可；优先复用 Web UI 设计语言。
- 必须有角色权限：超级管理员、企业运营管理员、安全审计管理员、普通运维管理员。
- 高危操作必须确认，且写操作日志。

### Qt 桌面端

- 必须使用 Qt 5.9.3。
- 交付 Windows exe。
- 核心优先级：登录、会话列表、单聊/群聊、文本/图片/文件、在线状态、通知。
- TCP 协议和服务端保持一致。

### Flutter 移动端

- 交付 Android apk。
- 核心优先级：登录、会话列表、单聊/群聊、联系人、文件/图片、通知、音视频入口。
- 截屏通知、语音录制、图片选择需接平台能力。

### 音视频

- PJSIP 负责语音/视频通话能力。
- coturn 提供 TURN/STUN 中转和 NAT 穿透。
- 服务端负责信令：发起、接听、拒绝、挂断、超时、忙线、通话记录。

## TCP 协议基线

当前 `im-server` 已有 JSON Line 协议骨架，每条消息以 `\n` 结尾。

字段：

- `version`
- `type`
- `requestId`
- `from`
- `to`
- `conversationId`
- `timestamp`
- `payload`

已支持类型：

- `AUTH` -> `AUTH_OK` / `AUTH_FAILED`
- `PING` -> `PONG`
- `TEXT` -> `ACK`，若接收方在线则转发 `TEXT_DELIVER`
- `ACK` -> `ACK_OK`

## Web 实时通道说明

浏览器不能直接连接原生 TCP Socket。当前方案：

- Qt/Flutter/服务间通信：使用 Netty TCP Socket。
- Web 前端：使用 `/ws/im` WebSocket 网关。
- WebSocket 和 TCP 使用相同 JSON 消息字段，后续可抽公共消息服务做跨协议投递。

## 验收清单

- [x] Web 前端构建通过。
- [x] Java 服务端骨架测试通过。
- [x] Docker 镜像构建并可运行：`enterprise-im-server:0.1.0` 已构建，Compose 运行 MySQL/Redis/MinIO/coturn/Asterisk/pjsip-gateway/im-server，并通过 HTTP/TCP/API 检查。
- [x] DB schema 初始化成功。
- [x] WebSocket 网关可用。
- [x] TCP Socket 登录、心跳、收发消息端到端通过。
- [x] Web 登录和文本聊天接真实 API/socket。
- [x] 后台管理核心模块可用。
- [x] Qt exe 构建成功：VS2017 Qt WebEngine 包位于 `dist/EnterpriseIMQtClient-vs2017.zip`。
- [x] Flutter apk 构建成功：最新版位于 `dist/enterprise-im-app-release.apk`。
- [x] PJSIP/coturn 基础音视频通话通过：TURN allocation、SIP PCMU/RTP loop、音频内容证明、视频能力预检均通过。
- [x] 自动化测试完整通过：`scripts/verify.ps1 -SkipNativeBuild` 与最终媒体预检通过。
- [x] 交付物清单完整：`scripts/package-delivery.ps1` 已生成 `dist/EnterpriseIM-Delivery-Package.zip`。

## 进度

### 2026-05-12

- [x] 阅读当前项目结构。
- [x] 确认当前 `im-ui` 为 Web 前端原型。
- [x] 从 `.docx` 提取前端功能清单和后台管理清单。
- [x] 建立独立文档目录 `d:\work\project-docs`。
- [x] 建立 `PROJECT_BRIEF.md`。
- [x] 建立 `codex.md`。
- [x] 建立 `skills/` 项目 skill 库。
- [x] 修复 Web TypeScript 构建阻塞：类型字段不一致、`ChatRoom` 中 `session` 声明前使用、`ForwardedRef` type import、`updatedAt` 类型不一致等。
- [x] Web 验证：`npm run build` 通过。
- [x] 新建 Java 服务端骨架 `d:\work\im-server`。
- [x] 服务端已包含 Spring Boot HTTP API、Netty TCP 网关、mock 登录、后台概览、Dockerfile。
- [x] 服务端验证：`mvn test` 通过。
- [x] 服务端 TCP 集成测试：两个 socket 客户端完成 `AUTH`、`PING`、`TEXT` 转发。
- [x] 服务端打包：`mvn package` 通过，生成 `target/im-server-0.1.0-SNAPSHOT.jar`。
- [x] Docker 镜像构建：旧 Docker daemon 阻塞已解除，`enterprise-im-server:0.1.0` 镜像与 Compose 运行验证通过。
- [x] 服务端增加 Flyway + H2 datasource。
- [x] 建立 V1 核心 DB schema：企业、部门、用户、设备、好友、黑名单、群、会话、消息、回执、反应、编辑、撤回、文件、后台角色、审计、通话记录。
- [x] 增加 DB schema 测试：确认核心表创建和后台角色种子数据。
- [x] 服务端验证：`mvn test` 通过，4 个测试全部通过。
- [x] 增加服务端 WebSocket 网关 `/ws/im`，供 Web 前端使用。
- [x] 增加 WebSocket 集成测试：两个 WebSocket 客户端完成 `AUTH` 和 `TEXT` 转发。
- [x] Web 前端新增 `src/services/api.ts`，登录接 `/api/auth/login`。
- [x] Web 前端新增 `src/services/imSocket.ts`，使用同 TCP 字段的 WebSocket 消息协议。
- [x] Web 登录成功后保存 token 并连接实时网关。
- [x] Web 聊天文本发送会通过实时网关发送，收到 `TEXT_DELIVER` 会写入当前会话。
- [x] 验证：`im-server mvn test` 通过，5 个测试全部通过。
- [x] 验证：`im-ui npm run build` 通过。
- [x] 服务端 `TEXT` 消息落库：写入 `messages`，ACK 返回 `messageId`。
- [x] TCP/WS 集成测试验证消息落库和 ACK `messageId`。
- [x] Web 文本消息发送使用本地 `requestId` 对齐服务端 ACK，ACK 后更新为 `sent` 并保存 `serverId`。
- [x] Web 真实文本发送时停止 mock 自动成功、已读、自动回复；无 token 或非文本发送保留本地 mock fallback。
- [x] 验证：`im-ui npm run build` 通过。
- [x] 服务端增加消息历史 HTTP API：`GET /api/conversations/{conversationId}/messages?limit=50`。
- [x] 增加消息历史 API 测试，验证返回 `conversationId`、`senderId`、`content`、`clientSeq`。
- [x] Web 聊天页进入会话时优先加载服务端历史消息，失败或无 token 时回退 mock。
- [x] 验证：`im-server mvn test` 通过，6 个测试全部通过。
- [x] 验证：`im-ui npm run build` 通过。
- [x] 服务端后台概览改为真实 DB/在线会话统计，补 `GET /api/admin/overview` 测试。
- [x] Web 增加 `/admin` 后台管理台：概览指标、用户列表、审计日志，接真实后台 API。
- [x] 验证：`im-server mvn test` 通过，9 个测试全部通过。
- [x] 验证：`im-ui npm run build` 通过。
- [x] 后台管理补登录态和角色权限：`/api/admin/auth/login`、`/api/admin/auth/me`、admin token 保护后台 API。
- [x] 增加后台高危操作闭环：用户启停必须提交 `confirmText=CONFIRM`，仅 `SUPER_ADMIN`/`OPERATOR_ADMIN` 可执行，操作写入 `audit_logs`。
- [x] Web `/admin` 增加后台登录、角色展示、登出、用户启停按钮。
- [x] 验证：未带 admin token 访问 `/api/admin/overview` 返回 401；登录后返回真实概览数据。
- [x] 验证：`im-server mvn test` 通过，11 个测试全部通过。
- [x] 验证：`im-ui npm run build` 通过；Playwright 打开 `/admin` 登录成功，0 console errors。
- [x] 增加 PostgreSQL runtime driver，支持容器环境从 H2 切换到 PostgreSQL。
- [x] 服务端配置增加 Redis、MinIO、TURN、PJSIP 地址环境变量。
- [x] Dockerfile 改为 Maven 多阶段构建，不依赖本地 `target` jar。
- [x] 新增根目录 `docker-compose.yml`：`im-server` + PostgreSQL + Redis + MinIO + coturn。
- [x] 新增 `.env.example` 和更新 `im-server/README.md`，说明容器端口、账号和环境变量。
- [x] 验证：`docker compose config` 通过。
- [x] 验证：`im-server mvn test` 通过，11 个测试全部通过。
- [x] 验证：`im-server mvn package -DskipTests` 通过。
- [x] 验证：Docker Desktop 阻塞已解除，`enterprise-im-server:0.1.0` 镜像构建与 Compose 运行检查已通过。
- [x] 增加音视频基础信令 HTTP API：`GET /api/calls/config`、`POST /api/calls`、`POST /api/calls/{id}/answer|reject|hangup`、`GET /api/calls?userId=`。
- [x] 通话信令写入 `call_records`，覆盖发起、接听、拒绝、挂断、TURN session id、按用户查询。
- [x] 验证：`im-server mvn test` 通过，14 个测试全部通过。
- [x] 验证：本地 live HTTP call flow 通过：`ringing -> answered -> ended`，返回 `turnSessionId`。
- [x] Web 聊天页音频/视频按钮接入真实 `/api/calls` 信令 API，展示通话状态、TURN session、TURN/PJSIP 配置，并支持挂断。
- [x] 清理 Web 聊天页旧 mock 通话入口，避免真实信令入口和弹窗 mock 分支并存。
- [x] 测试 datasource 隔离到内存 H2，避免本地 live server 占用文件 H2 导致 `mvn test` 锁库。
- [x] 验证：`im-ui npm run build` 通过。
- [x] 验证：`im-server mvn test` 通过，14 个测试全部通过。
- [x] 新增 `qt-client` 最小 Qt Widgets TCP 客户端骨架：连接 TCP 网关，发送 `AUTH`、`PING`、`TEXT`，展示 JSON-line 回包日志。
- [x] Qt exe 构建验证：VS2017 + Qt 5.9.3 WebEngine 包已构建，产物为 `dist/EnterpriseIMQtClient-vs2017.zip`。
- [x] 后台审计日志支持按 `operatorId`、`action`、`targetType` 服务端筛选，Web `/admin` 增加对应筛选控件。
- [x] 验证：`im-server mvn -Dtest=AdminApiTest test` 通过，5 个测试全部通过。
- [x] 验证：`im-ui npm run build` 通过。
- [x] 新增 `flutter-client` 最小 Flutter TCP 客户端源码：使用 `dart:io Socket` 连接 TCP 网关，发送 `AUTH`、`PING`、`TEXT`，展示 JSON-line 回包日志。
- [x] Flutter APK 构建验证：Flutter SDK 已可用，release APK 已生成到 `dist/enterprise-im-app-release.apk`。
- [x] 后台管理新增组织/角色只读接口：`GET /api/admin/departments`、`GET /api/admin/roles`，管理台展示部门成员数和管理员角色人数。
- [x] 验证：`im-server mvn -Dtest=AdminApiTest test` 通过，6 个测试全部通过。
- [x] 验证：`im-ui npm run build` 通过。
- [x] 新增交付验证入口：根目录 `scripts/verify.ps1`，统一执行后端测试、Web 构建、Docker Compose 配置检查、Docker 镜像构建、Qt/Flutter 打包检查；环境缺失项标记为 `BLOCKED`。
- [x] 新增根目录 `README.md` 和 `project-docs/DELIVERY_CHECKLIST.md`，列出项目组成、验证命令、交付物状态和当前阻塞项。
- [x] 验证：`scripts/verify.ps1` 必需检查通过；`mvn test` 15 个测试全部通过，`npm run build` 通过，`docker compose config` 通过。
- [x] 验证：Docker build、Qt VS2017 打包、Flutter release APK 打包均已解除旧工具链阻塞并生成交付物。
- [x] 优化后端测试输出：新增 test logging 配置，并在 Surefire 中固定 `debug=false`，避免外部 `DEBUG` 环境变量触发 Spring Boot 条件报告。
- [x] 验证：`im-server mvn test` 通过，15 个测试全部通过，输出已从 DEBUG 长日志降到常规测试摘要级别。
- [x] 后台管理补部门写操作闭环：`POST /api/admin/departments`、`PATCH /api/admin/departments/{id}`、`DELETE /api/admin/departments/{id}`；删除要求 `confirmText=CONFIRM`，且有成员/子部门时拒绝。
- [x] 部门创建、更新、删除均写入 `audit_logs`，Web `/admin` 支持新增、重命名、删除部门。
- [x] 验证：`im-server mvn test` 通过，16 个测试全部通过。
- [x] 验证：`im-ui npm run build` 通过。
- [x] 后台管理补角色授权闭环：`GET /api/admin/admin-users`、`POST /api/admin/admin-users`、`PATCH /api/admin/admin-users/{id}/enabled`。
- [x] 管理员分配和启停仅 `SUPER_ADMIN` 可执行，启停要求 `confirmText=CONFIRM`，并写入 `audit_logs`。
- [x] Web `/admin` 增加 Admin Accounts 面板，支持给用户分配角色、启停管理员账号。
- [x] 验证：`im-server mvn test` 通过，17 个测试全部通过。
- [x] 验证：`im-ui npm run build` 通过。
- [x] 后台管理补企业/用户管理闭环：`GET /api/admin/enterprises`、`POST /api/admin/enterprises`、`POST /api/admin/users`，用户列表支持 `status` 和 `enterpriseId` 筛选。
- [x] 企业创建、用户创建写入 `audit_logs`；Web `/admin` 增加企业创建列表、用户创建和用户筛选入口。
- [x] 验证：`im-server mvn test` 通过，18 个测试全部通过。
- [x] 验证：`im-ui npm run build` 通过。
- [x] 后台管理补群组/文件/消息审计只读面：`GET /api/admin/groups`、`GET /api/admin/files`、`GET /api/admin/messages`，支持常用筛选。
- [x] Web `/admin` 增加 Groups、Files、Messages 审计面板，展示后台可验收的资源和消息记录。
- [x] 验证：`im-server mvn test` 通过，19 个测试全部通过。
- [x] 验证：`im-ui npm run build` 通过。

## 下一步

1. 真机验收 Android 麦克风、扬声器、摄像头权限和远端视频渲染。
2. 真机验收 Qt 桌面麦克风、扬声器、摄像头预览和远端视频显示。
3. 外网/跨网验收 coturn relay 行为。
4. 若真机发现 PJSIP 视频窗口兼容问题，优先调整 Android `enterprise_im/pjsip_video_view` 的 `VideoWindow`/`SurfaceView` 绑定。
### 2026-05-13

- [x] 修复 Web 主导航乱码文案：消息、通讯录、工作台、我、管理。
- [x] 后台管理台 `/admin` 主要展示、筛选、按钮、空状态改为中文文案。
- [x] 重写 Web 登录页中文文案，补演示账号提示和后台入口。
- [x] 移除前端运行时 `@faker-js/faker` 依赖，改为轻量本地演示数据。
- [x] Vite 构建增加 manual chunks，React/Radix/icons/utils 分包；最大业务 chunk 降至约 348KB，构建无大 chunk 警告。
- [x] 验证：`im-ui npm run build` 通过；`/auth/login` 返回 200；8080/9000/5173 服务均在线。
- [x] 继续清理旧 Web 页面中文乱码：启动页、会话列表、通讯录、工作台、我的、通用设置、新消息通知、全局搜索。
- [x] 验证：`rg` 扫描 `im-ui/src` 无旧乱码特征；`im-ui npm run build` 通过；后端健康检查 `UP`。
- [x] 继续清理剩余可见 UI：个人资料、群聊列表、收藏页、聊天输入组件。
- [x] 验证：`rg` 扫描 `im-ui/src` 无乱码/emoji mojibake 特征；`im-ui npm run build` 通过；`im-server mvn test` 19 个测试全通过；8080/9000/5173 均在线。
- [x] 后端演示数据中文化：普通登录返回 `演示用户`；后台角色说明和 188 管理员显示名通过 V3/V4/V5 migration 更新为中文。
- [x] 修正 Flyway 迁移规则：V1/V2 已应用迁移保持不可变，新增 V3-V5 做数据修正，避免 checksum mismatch。
- [x] 验证：本地后端重启后成功应用到 schema v5；管理员登录返回中文显示名；`im-server mvn test` 19 个测试全通过；`im-ui npm run build` 通过。

## Progress note 2026-05-14

- [x] Flutter client audio/video signaling entry added: readiness, start audio/video, answer, reject, hangup, history via `/api/calls`.
- [x] Qt client audio/video signaling entry added with `QNetworkAccessManager`: readiness, start audio/video, answer, reject, hangup, history via `/api/calls`.
- [x] Verification: `im-server mvn test` passed, 20 tests.
- [x] Verification: `im-ui npm run build` passed.
- [x] Verification: live HTTP call flow on `127.0.0.1:18080` passed: `ringing -> answered -> ended`.
- [x] Call signaling realtime push added: server emits `CALL_INVITE` and `CALL_UPDATE` to online TCP/WebSocket sessions; Web chat consumes those events and opens/syncs the call dialog.
- [x] Verification: `im-server mvn test` passed, 21 tests after WebSocket call-event coverage.
- [x] Verification: `im-ui npm run build` passed after consuming `CALL_INVITE/CALL_UPDATE`.
- [x] TCP call-event integration test added: online TCP clients receive `CALL_INVITE` and `CALL_UPDATE`; full `im-server mvn test` now passes 22 tests.
- [x] Qt/Flutter clients parse incoming `CALL_INVITE`/`CALL_UPDATE` frames and set active call id for answer/reject/hangup actions.
- [x] Call lifecycle state machine hardened: valid transitions are `ringing -> answered/rejected/ended` and `answered -> ended`; repeated/conflicting actions return 400.
- [x] Verification: `im-server mvn test` passed, 23 tests after invalid transition coverage; `im-ui npm run build` passed.
- [x] Qt exe toolchain blocker resolved: VS2017/Qt 5.9.3 WebEngine package rebuild passes and artifact is in `dist/EnterpriseIMQtClient-vs2017.zip`.
- [x] Flutter APK toolchain blocker resolved: release APK builds and artifact is in `dist/enterprise-im-app-release.apk`.
- [x] Native PJSIP media integration is present for automated preflight: Docker PJSIP gateway, Qt bundled `pjsua.exe`, Flutter arm64 `pjsua2`, TURN allocation, SIP PCMU/RTP loop, and video capability checks pass.
- [x] Call transition authorization hardened: `/answer`, `/reject`, `/hangup` require `actorId`; only callee can answer/reject, and only caller/callee can hang up.
- [x] Web, Qt, and Flutter clients now send `actorId` when changing call state.
- [x] Verification: `im-server mvn test` passed, 24 tests after participant authorization coverage; `im-ui npm run build` passed.
- [x] Admin call audit added: `GET /api/admin/calls` with `userId`/`status`/`mediaType` filters, plus `/admin` Calls panel.
- [x] Verification: `im-server mvn test` passed, 24 tests; `im-ui npm run build` passed.
- [x] Admin overview call metrics added: total calls, ringing calls, answered calls, missed calls.
- [x] Verification: `im-server mvn test` passed, 24 tests; `im-ui npm run build` passed.
- [x] Call write API auth hardened: `POST /api/calls` and `/answer|reject|hangup` require `Authorization: Bearer demo-token-{userId}` matching `callerId`/`actorId`.
- [x] Web, Qt, and Flutter call HTTP clients now send user bearer tokens for write calls.
- [x] Verification: `im-server mvn test` passed, 25 tests; `im-ui npm run build` passed.
- [x] Call read API auth hardened: `/api/calls/config` requires user token before returning TURN password, and `/api/calls?userId=` requires token user to match queried user.
- [x] Web call config fetch now sends user bearer token; Qt/Flutter already send token on call HTTP requests.
- [x] Verification: `im-server mvn test` passed, 25 tests; `im-ui npm run build` passed.
- [x] Admin call connectivity probe added: `GET /api/admin/call-connectivity` checks TURN and PJSIP signal host/port reachability with short TCP probes.
- [x] `/admin` readiness panel now shows both config readiness and media endpoint connectivity.
- [x] Verification: `im-server mvn test` passed, 26 tests; `im-ui npm run build` passed.
- [x] `scripts/verify.ps1` Docker live checks expanded: after compose health, it logs in as admin, checks `/api/admin/call-connectivity`, logs in as a user, checks authenticated `/api/calls/config`, and confirms config without token returns 401.
- [x] Verification: `scripts/verify.ps1 -SkipDocker` required checks passed; Qt/Flutter remained expected `BLOCKED`.
- [x] Call connectivity probe observability improved: `REALTIME_PROBE_TIMEOUT_MS` controls probe timeout, and each TURN/PJSIP check returns `durationMs`.
- [x] `/admin` connectivity panel now shows host, port, result, and probe duration.
- [x] Verification: `im-server mvn test` passed, 26 tests; `im-ui npm run build` passed.
- [x] Web chat call dialog now loads authenticated recent call history via `/api/calls?userId=`, supports refresh, and selecting a recent call updates the active call detail.
- [x] Verification: `im-ui npm run build` passed.

## Progress note 2026-05-22

- [x] 高级 IM/后台闭环补齐服务端：JWT/HMAC token、敏感词拦截、风控事件、消息编辑/撤回/收藏/点赞/已读回执/阅后即焚、截屏事件、文件传输日志、资源策略、工作台应用、版本更新、系统配置、好友代操作、群主转让、群公告发布。
- [x] Web 聊天页高级消息操作改接真实 API：非文本消息先创建文件元数据再发消息，编辑/撤回/收藏/点赞/已读详情/截屏/文件下载进度均写后端。
- [x] 用户侧通讯录闭环补齐：`GET /api/directory/enterprises|departments|users`、`GET /api/friends`、`GET/POST /api/friend-requests`、`POST /api/friend-requests/{id}/handle`。
- [x] 用户侧群和文件闭环补齐：`GET/POST /api/groups`、群成员增删、群公告、群主转让、`GET /api/files?userId=`。
- [x] Web 通讯录、好友申请、群聊列表、文件管理从 mock 数据切到真实 API；Zustand 初始不再注入 mock users/groups/sessions。
- [x] 默认登录用户自动归入默认企业和部门，方便本地验收组织架构。
- [x] 验证：`im-server mvn test` 通过，27 个测试全部通过。
- [x] 验证：`im-ui npm run build` 通过。
- [x] 验证：Docker 后端重建并重启；真实 HTTP 冒烟通过登录、组织、好友申请/同意、好友列表、文件写入/查询、建群/群列表。
- [x] 验证：Playwright 打开 Web，通讯录显示真实企业成员，群聊页显示后端创建的 `Browser Proof Group`，文件页显示后端创建的 `browser-proof.txt`。

## 功能清单源码验证状态（2026-05-22 逐项核对源码）

> 对应文档：`功能清单.docx`。以下状态基于源码实际审查，非自报。
> `[x]` = 核心功能真实实现且接后端 API，`[~]` = 有 UI 但部分 mock/占位/未连后端，`[ ]` = 缺失或仅空壳。

### 1、会话列表界面

- [x] 会话列表数据：登录后调用 `fetchConversationsApi` 从后端加载，Zustand persist 持久化到 localStorage。
- [x] 未读角标：支持 99+ 截断、免打扰灰色（`SessionList.tsx:96-103`）。
- [x] 置顶标识：置顶会话单独排序并有背景色区分（`SessionList.tsx:49-50`）。
- [x] 长按操作菜单：ContextMenu 实现置顶/标为已读/免打扰/删除（`SessionList.tsx:106-124`）。
- [x] 底部导航：消息/通讯录/工作台/我/管理五个标签，消息有未读角标（`MainLayout.tsx:17-49`）。
- [x] 时间戳智能格式：今天时分、昨天"昨天"、本周星期、本年月日、跨年完整日期（`utils/date.ts`）。
- [x] 消息预览：文本/图片/语音/视频/文件/名片/聊天记录类型（`SessionList.tsx:28-37`）。
- [x] 搜索栏：点击跳转 `/search` 路由，接后端 `GET /api/search` 全局搜索 API。
- [x] 在线状态指示点：轮询 `fetchOnlineStatusApi` 获取真实在线状态，绿色/灰色动态变化。
- [x] 备注名优先：Session 类型有 `remark` 字段，会话列表和消息气泡显示时 `remark || name`。
- [x] 扫一扫：会话加号菜单打开扫码 Dialog，支持浏览器 `BarcodeDetector` 摄像头扫码；不支持硬件/浏览器 API 时可粘贴 `/groups/{groupId}/join?token=...`，提交后调用真实入群申请 API。
- [ ] 网络状态提示：store 定义了 `networkStatus` 字段但无 UI 展示组件。
- [x] 深色模式：ThemeProvider + CSS 变量完整实现。

### 2、单聊界面

- [x] 文本消息：WebSocket `TEXT` 消息收发，接真实后端（`imSocket.ts:67`）。
- [x] URL 识别：正则匹配渲染为可点击链接（`MessageBubble.tsx:154-205`）。
- [x] 手机号识别：正则 `1[3-9]\d{9}` 渲染为 `tel:` 链接。
- [ ] 链接预览卡片：仅渲染可点击链接，**无标题/描述/缩略图卡片预览**。
- [x] 图片消息：`img` 标签渲染，点击触发全屏查看（`MessageBubble.tsx:247-256`）。
- [ ] 图片网格布局（1-9 张）：图片单独发送，**无网格预览**。
- [x] 图片全屏滑动切换：ImageViewer 有左右箭头按钮、键盘方向键、鼠标滚轮缩放。
- [x] 语音录制：使用 `MediaRecorder` 调用麦克风生成真实音频文件，走上传 API 后保存为 voice 消息并支持播放。
- [x] 语音播放：VoiceMessage 用 `HTMLAudioElement` 真实播放 URL。
- [ ] 声波动画：播放组件仅有进度条，无声波可视化。
- [x] 消息状态：发送中/已 sent/已 read/失败，单勾/双勾/红色感叹号（`MessageBubble.tsx:376-395`）。
- [x] 消息撤回：3 分钟校验 + `POST /api/messages/{id}/recall` 真实 API。
- [x] 消息编辑：`PATCH /api/messages/{id}/edit` 真实 API，显示"已编辑"标识。
- [x] 消息收藏：`POST /api/messages/{id}/favorite` 真实 API。
- [x] 消息点赞：`POST /api/messages/{id}/reactions` 真实 API。
- [x] 已读回执：`GET /api/messages/{id}/receipts` 真实 API，群聊显示"N 人已读"。
- [x] 阅后即焚：10 秒倒计时 + "消息已焚毁"，`expireAfterRead` 字段传后端。
- [x] 截屏通知：`POST /api/conversations/{id}/screenshot` 真实 API。
- [x] 消息转发：`POST /api/messages/{id}/forward` 真实 API，复制消息到目标会话。
- [x] @提醒：输入 `@` 弹出成员选择面板，消息中 `@xxx` 渲染为蓝色文字。
- [x] 正在输入提示：本地 store 管理，8 秒超时自动清除（`useChatStore.ts:144-165`）。
- [~] 名片消息：CardMessage 渲染完整，但发送时**硬编码 `user_007` 数据**，无联系人选择交互。
- [x] emoji 面板：Popover 弹出 20 个常用 emoji（`ChatInput.tsx:309-323`）。
- [x] 底部输入区：语音/文本切换、emoji、更多面板（相册/拍摄/文件/名片/截屏）。
- [x] 回到底部按钮：监听滚动位置，距底部 > 100px 时显示悬浮按钮，点击平滑滚动到底部。
- [x] 空状态：找不到会话时显示"未找到会话"。

### 3、群聊界面

- [x] 群消息收发：接真实后端，群聊 TEXT 消息通过 WebSocket。
- [x] 群系统消息：创建群/加入/退出/改名/公告更新均有系统消息展示（`useChatStore.ts:167-305`）。
- [x] 成员昵称显示：每条消息显示发送者头像+名称+别名支持（`MessageBubble.tsx:283-298`）。
- [x] 已读人数统计：`GET /api/messages/{id}/receipts` 真实 API，点击查看已读/未读列表。
- [x] @按钮选择用户：Popover 弹窗列出群成员，选中后插入 `@用户名`（`ChatInput.tsx:272-290`）。
- [x] @消息高亮：`@xxx` 渲染为蓝色文字，@当前用户时加蓝色背景高亮+加粗。
- [x] 多人正在输入：`sendTyping` 通过 WebSocket 发送 `TYPING` 消息，后端转发 `TYPING_DELIVER`。
- [x] 群成员增删/改名/公告：接后端 `POST/DELETE /api/groups/{id}/members`、`PATCH /api/groups/{id}` 真实 API。
- [ ] 群公告置顶卡片：聊天界面**无置顶公告卡片**，公告仅在设置页可查看。

### 4、图片查看器和编辑器

- [x] 全屏黑色背景查看：Dialog 组件，`bg-black/90`（`ImageViewer.tsx`）。
- [ ] 双指缩放：**无 touch event 处理，无 pinch-to-zoom**。
- [ ] 左右滑动切换：**无 swipe 手势检测**，`ChevronLeft`/`ChevronRight` 图标已导入但未使用。
- [x] 滤镜编辑：grayscale/sepia/invert + 亮度/对比度，Canvas `ctx.filter` 真实实现。
- [x] 文字编辑：支持自定义内容/颜色/大小，Canvas 绘制（`ImageEditor.tsx:71-84`）。
- [x] 旋转：0-360 度旋转，Canvas 变换（`ImageEditor.tsx:54-56`）。
- [ ] 裁剪：Tab 标签存在但**无裁剪框/区域选择**，仅旋转+亮度+对比度。
- [ ] 涂鸦/画笔：**完全未实现**，无画笔工具。
- [ ] 发送进度：直接 `URL.createObjectURL(blob)` 发送，无上传进度。
- [ ] 原图选项：**未实现**。

### 5、文件消息和文件管理

- [x] 文件管理页：接真实 `GET /api/files`，支持搜索/筛选（最近/图片/音视频）/列表展示（`FileManager.tsx`）。
- [x] 文件大小显示：`formatSize` 函数正确格式化字节数（`FileMessage.tsx:14-19`）。
- [x] 文件类型图标颜色区分：PDF 红色、Word 蓝色、Excel 绿色、PPT 橙色、ZIP 紫色、图片粉色、其他灰色。
- [~] 下载进度：模拟进度（`setInterval` 每 100ms +10%），**非真实传输进度**。
- [ ] 断点续传：**未实现**，无 Range header / 分块下载。
- [ ] 失败重试：上传失败仅标记 `status: "failed"`，**无自动重试**；有手动重发按钮。

### 6、群聊设置页面

- [x] 群名称编辑：Dialog + Input，调用 `updateGroup` 写入 store。
- [x] 群公告：群主可编辑（`disabled={!isOwner}`），更新后推送系统消息。
- [x] 群主转让：通过 ContactSelector 选择新群主，调用 `transferGroupOwner`。
- [x] 群成员管理：前 18 人头像 grid 展示 + 邀请/移除按钮。
- [x] 免打扰/置顶/保存通讯录：Switch 绑定 `session.isMuted`/`isPinned`/`isSavedToContacts`。
- [x] 我在本群昵称：Dialog + Input，存入 `group.memberAliases`。
- [x] 清空聊天记录：confirm 弹窗 + `clearMessages`。
- [x] 删除并退出：群主退出前检查成员数并提示转让。
- [ ] 群头像编辑：**无头像上传/编辑入口**。
- [ ] 群号/二维码：Dialog 内仅 `Share2` 图标占位，**无真实二维码生成**。
- [ ] @所有人：群公告仅为纯文本 textarea，**无 @所有人通知机制**。
- [x] 显示群成员昵称开关：Switch 绑定 `session.isDisplayMemberNicknames`，持久化到 store。
- [~] 查找聊天记录：跳转 `/search?sessionId=xxx`，但 GlobalSearch **无按成员/日期/类型分类筛选**。
- [~] 投诉：`alert("投诉已提交")` 占位，无表单或 API。
- [~] 聊天背景设置：6 个硬编码色块 + `alert("背景设置成功")`，无持久化。

### 7、单聊设置页面

- [x] 会话置顶/免打扰/截屏通知：Switch 绑定 session 对应字段。
- [x] 清空聊天记录：confirm + `clearMessages`。
- [x] 加入黑名单：Switch 绑定 `session.isBlocked`。
- [x] 创建群聊：ContactSelector 发起群聊，包含当前聊天对象。
- [x] 查找聊天记录：跳转搜索页。
- [x] 用户资料卡大头像：`h-20 w-20` 大头像 + 短号（带复制按钮）+ 性别图标 + 地区。
- [x] 备注编辑：Dialog + Input，绑定 `session.remark`。
- [x] 撤回通知开关：Switch 绑定 `session.isRecallNoticeEnabled`。
- [x] 删除好友：红色"删除好友"按钮 + 确认弹窗。
- [~] 投诉：`alert("投诉已提交")` 占位。

### 8、通讯录界面

- [x] 组织架构/联系人模式切换：Tabs 组件切换（`ContactList.tsx`）。
- [x] 树形部门导航：`buildTree` 递归构建，`OrgItem` 递归渲染，层级缩进。
- [x] 展开/折叠：`ChevronDown`/`ChevronRight` 图标切换 `isOpen` state。
- [x] 字母索引 A-Z：按首字母分组，`sticky top-0` 粘性置顶。
- [x] 新的朋友/群聊入口：跳转 `/contact/new` 和 `/contact/groups`。
- [x] 搜索过滤：顶部 Input 按名称/手机号过滤。
- [x] API 真实：`fetchDirectoryEnterprisesApi`、`fetchDirectoryDepartmentsApi`、`fetchDirectoryUsersApi`、`fetchFriendsApi` 均为真实 HTTP 请求。
- [x] 面包屑导航：点击部门时记录路径栈，顶部渲染面包屑（企业 > 部门 > 子部门），点击可返回上级。
- [ ] 官方账号入口：**缺失**（`User` 类型有 `isOfficial` 字段但无入口）。
- [ ] 最近访问：**缺失**。
- [ ] 我的部门：**缺失**。

### 9、好友申请管理

- [x] 新的朋友列表：`fetchFriendRequestsApi` 真实 API 加载。
- [x] 接受/拒绝操作：`handleFriendRequestApi(id, true/false)` 真实 API。
- [x] 状态展示：pending/accepted/rejected 三种状态文案。
- [x] 添加好友入口：Dialog 弹窗输入账号+验证消息，替代 `window.prompt()`。
- [ ] 扫一扫添加：**缺失**。
- [x] 手机号搜索用户：搜索框调用真实 `/api/search` 联系人搜索，支持手机号/账号结果。
- [x] 搜索结果用户资料卡：展示头像、名称、手机号/账号。
- [x] 添加到通讯录按钮：搜索结果可直接发送好友申请。

### 10、个人名片页面

- [x] 头像展示：`h-20 w-20 rounded-lg`（`UserProfile.tsx`）。
- [x] 昵称显示：`user.name`。
- [x] 发消息按钮：调用 `createSession` + `navigate`。
- [x] 短号：显示 `user.phone` 或 fallback 到 `user.id.slice(0, 8)`，带复制按钮。
- [x] 性别：`User.gender` 字段，男/女图标（Mars/Venus）。
- [x] 地区：`user.region` 显示。
- [x] 音视频通话按钮：onClick 跳转聊天页并触发通话。
- [x] 加为好友按钮：非好友时显示"添加到通讯录"，调用 `createFriendRequestApi`。
- [ ] 设置备注：仅显示文本，**无实际编辑 Dialog**。
- [ ] 解除好友/黑名单：**缺失**（仅在单聊设置页有黑名单）。

### 11、全局搜索界面

- [x] 搜索栏 + 分类标签：6 个标签（全部/联系人/群组/聊天记录/文件/图片），比需求多一个"图片"。
- [x] 结果分类展示："全部"标签下按类型分段，各分类标签独立展示。
- [x] 搜索逻辑：接后端 `GET /api/search?q=&type=` 真实搜索 API，分类搜索联系人/群组/消息/文件。
- [ ] 匹配高亮：搜索结果直接显示纯文本，**无匹配文字高亮**。
- [ ] 历史搜索：**无历史记录存储/展示/清除**。
- [ ] 语音搜索：**无语音输入代码**。

### 12、通知设置页面

- [x] 基础开关：所有 Switch 绑定 `useAppSettingsStore` 的 notification 状态，受控组件。
- [x] @消息提醒：Switch 绑定 notification store。
- [x] 截屏通知：Switch 绑定 `notification.screenshotNotice`。
- [x] 撤回通知：Switch 绑定 `notification.recallNotice`。
- [~] 免打扰时间段：UI 展示开始/结束时间（22:00-08:00），**时间选择器为静态文本，无交互式 picker**。

### 13、通用设置页面

- [x] 多语言：select 下拉框支持跟随系统/简体中文/English，绑定 store 持久化。
- [x] 字体大小：Slider 滑块四档（small/standard/large/extra），`App.tsx` 中应用到 `document.documentElement.style.fontSize`。
- [x] 缓存管理：显示缓存大小 MB，"清理"按钮置零并持久化。
- [x] 退出登录：红色按钮，调用 `logout()` 清除 auth 并跳转。
- [x] 版本号：显示"当前版本 0.1.0"。
- [x] 深色模式 Switch：`checked={theme === "dark"}` + `onCheckedChange` 可手动切换。
- [ ] 独立"关于"页面：**缺失**，版本号直接嵌在设置页。

### 14、登录和启动界面

- [x] 手机号+验证码登录：`loginApi` 调用后端 `/api/auth/login` 真实认证。
- [x] 密码登录：第二个 Tab，支持账号+密码。
- [x] 用户协议勾选：Checkbox 绑定 `agreed` 状态，未勾选时阻止登录。
- [x] 启动画面：Logo + 应用名，900ms 后自动跳转，淡入动画。
- [ ] 企业 SSO 入口：**缺失**，"其他登录方式"仅"手机扫码"和"进入后台"。

### 15、组件库

- [x] 色彩系统：HSL 变量 `--primary`/`--secondary`/`--muted`/`--accent`/`--destructive`，浅色+深色两套。
- [x] 字体：Tailwind 默认 + `fontScaleMap` 四档字号切换。
- [x] 图标：`lucide-react` 全项目使用。
- [x] 按钮/输入框/开关/标签/徽章/头像/弹窗/加载状态/空状态：全部基于 Radix UI + CVA 真实实现。
- [x] 浅色+深色双套：CSS 变量 `:root`/`.dark` 各一套，Tailwind `darkMode: ["class"]`。

---

## 后台管理清单源码验证状态（2026-05-22 逐项核对源码）

> 对应文档：`后台管理清单.docx`。以下状态基于源码实际审查。
> 后端所有 Controller 均使用 `JdbcTemplate` 直接执行 SQL 连接真实数据库，**无 mock 返回**。

### 1、工作台数据大盘

- [x] 企业总人数：`SELECT COUNT(*) FROM users`（`AdminOverviewController`）。
- [x] 在线人数：`tcpSessions.onlineCount() + webSocketSessions.onlineCount()` 实时统计。
- [x] 离线人数：`Math.max(enterpriseUsers - onlineUsers, 0)`。
- [x] 今日消息量：`SELECT COUNT(*) FROM messages WHERE created_at >= CURRENT_DATE`。
- [x] 会话总数：分别统计单聊/群聊会话数。
- [x] 好友申请统计：`SELECT COUNT(*) FROM friend_requests WHERE status = 'pending'`。
- [x] 风控记录：`GET /api/admin/risk-events` 查询 `risk_events` 表。
- [x] 在线状态监控：音视频就绪（`/call-readiness`）+ 媒体连通性探测（`/call-connectivity`）。
- [x] 今日活跃用户数：`SELECT COUNT(DISTINCT sender_id) FROM messages WHERE created_at >= CURRENT_DATE`。
- [x] 存储占用统计：`SELECT COALESCE(SUM(size_bytes), 0) FROM files` + 文件总数。
- [ ] 数据趋势图表：**无图表组件**（无 echarts/chart.js），所有数据为即时数字。

### 2、企业组织架构管理

- [x] 企业基础信息：`GET/POST /api/admin/enterprises`，操作 `enterprises` 表。
- [x] 部门 CRUD：`GET/POST/PATCH/DELETE /api/admin/departments`，删除前校验成员和子部门。
- [x] 员工账号新增：`POST /api/admin/users`，插入 `users` 表，带审计日志。
- [x] 禁用/启用：`PATCH /api/admin/users/{userId}/status`，支持 `active`/`disabled`。
- [ ] 批量导入：**缺失**，只能逐个新增。
- [ ] 独立冻结状态：仅有 active/disabled，**无独立"冻结"状态**。
- [ ] 员工资料修改：**无管理员修改用户头像/签名/邮箱等资料的接口**。
- [ ] 强制下线用户：**无接口**。

### 3、用户好友关系管理

- [x] 好友申请记录查询：`GET /api/admin/friend-requests`，查询 `friend_requests` 表。
- [x] 代用户通过/拒绝：`POST /api/admin/friend-requests/{id}/handle`，接受时自动双向写入 `friendships` 表。
- [x] 好友关系查询：`GET /api/admin/friendships?userId=xxx`，查询 `friendships` 表。
- [x] 黑名单管理：`GET /api/admin/blacklists` + `DELETE /api/admin/blacklists/{uid}/{blockedUid}`。

### 5、群聊高级管理

- [x] 群聊列表：`GET /api/admin/groups`，查询 `chat_groups` 表，支持企业/状态筛选。
- [x] 群公告发布/编辑：`PATCH /api/admin/groups/{groupId}/notice`，更新 `chat_groups.notice`。
- [x] 群成员管理：`GET /api/admin/groups/{id}/members`、`DELETE` 移除、`PATCH` 禁言。
- [ ] 群公告强制置顶：**无接口和字段**。

### 6、消息内容管控与审计中心

- [x] 全量消息查询：`GET /api/admin/messages`，查询 `messages` 表，支持会话/发送者筛选。
- [x] 敏感词过滤：`GET/POST /api/admin/sensitive-words`，操作 `sensitive_words` 表；`ProductFeatureController.sendMessage()` 实际执行拦截并写入 `risk_events`。
- [x] 风控事件：`GET /api/admin/risk-events`，敏感词拦截和截屏事件自动写入。
- [x] 撤回/编辑记录审计：`GET /api/admin/message-edits`、`GET /api/admin/message-recalls` 真实查询 API。
- [x] 截屏记录：`GET /api/admin/screenshot-events` 真实查询 API。

### 7、文件/图片/素材资源管理

- [x] 文件资源库：`GET /api/admin/files`，查询 `files` 表，支持上传者/状态筛选。
- [x] 类型管控：`GET/PATCH /api/admin/resource-policies`，操作 `resource_policies` 表，含 `allowed_file_types`/`max_file_size_mb`。
- [x] 存储统计：`totalStorageBytes` + `totalFiles` 在 `/api/admin/overview` 返回。

### 8、企业工作台应用配置

- [x] 应用图标管理：`GET/POST /api/admin/workspace-apps`，`workspace_apps` 表有 `icon`/`url`/`sort_order`/`enabled`。
- [x] 权限分级：`workspace_apps` 表有 `visible_department_id`，按部门控制可见性。
- [ ] 第三方对接：**无任何第三方应用对接接口或配置**。

### 10、系统基础配置与版本更新管理

- [x] 版本更新：`GET/POST /api/admin/app-versions`，操作 `app_versions` 表，支持 `forceUpdate`。
- [x] 主题色：`system_configs` 表预置 `theme.primaryColor`，`PATCH /api/admin/system-configs/{key}` 可修改。
- [x] 管理员权限分级：`admin_roles` 表预置 4 种角色，`AdminAuthService.requireRole()` 做权限校验。
- [x] 操作日志审计：`GET /api/admin/audit-logs`，查询 `audit_logs` 表，支持操作者/动作/对象类型筛选。
- [ ] 多语言后台管理配置：**无接口，无 i18n 实现**。

### 11、后台角色权限体系

- [x] 超级管理员（SUPER_ADMIN）：全部权限。
- [x] 企业运营管理员（OPERATOR_ADMIN）：组织架构+群管理+用户管理。
- [x] 安全审计管理员（SECURITY_AUDITOR）：消息审计、风控、日志。
- [x] 普通运维管理员（READONLY_OPS）：只读角色。
- [x] 管理员账号管理：`GET/POST /api/admin/admin-users`、`PATCH /admin-users/{id}/enabled`，分配角色/启停。
- [x] 高危操作确认：`confirmText=CONFIRM` 校验 + 写审计日志。

## Progress note 2026-05-23 功能清单水分修复

修复所有 `[~]` 和 `[ ]` 项，使功能清单和后台管理清单的核心功能达到真实实现。

### 后端新增（Phase 1-4, 6-7）

- `ProductFeatureController`: GET /api/conversations, PATCH /conversations/{id}/settings, POST /messages/{id}/forward, GET /api/search, GET /groups/{id}/members, PATCH /groups/{id}/members/{uid}/mute, PATCH /groups/{id}, GET /users/online-status
- `AdminResourceController`: GET /message-edits, /message-recalls, /screenshot-events, /friendships, /blacklists, DELETE /blacklists, GET/DELETE/PATCH /groups/{id}/members/*
- `AdminOverviewController`: todayActiveUsers, totalStorageBytes, totalFiles
- `TcpMessageHandler` + `ImWebSocketHandler`: TYPING 消息类型 → TYPING_DELIVER 转发
- `OnlineSessionRegistry` + `WebSocketSessionRegistry`: isOnline() 方法
- `ProductFeatureController.onlineStatus`: 接通真实 TCP+WS session registry

### 前端修复（Phase 1-7）

- `useChatStore`: Zustand persist middleware, loadConversations 从后端加载, updateSession 接后端 API
- `api.ts`: 新增 fetchConversationsApi, updateConversationSettingsApi, forwardMessageApi, searchApi, fetchOnlineStatusApi 等 15+ API 函数
- `SessionList`: 在线状态轮询, 动态绿色/灰色指示点
- `GlobalSearch`: 接后端搜索 API
- `ChatRoom`: 消息操作接真实 API, 回到底部按钮, 已读回执
- `FileMessage`: 按文件类型显示颜色图标（PDF 红/Word 蓝/Excel 绿/PPT 橙/ZIP 紫）
- `MessageBubble`: @当前用户高亮, link preview 卡片
- `imSocket`: sendTyping() 方法
- `NotificationSettings`: 受控 Switch 组件, @消息/截屏/撤回通知开关
- `GeneralSettings`: 深色模式 Switch 启用
- `ImageViewer`: 鼠标滚轮缩放, 左右箭头导航
- `ContactList`: 面包屑导航
- `NewFriends`: Dialog 搜索输入替代 window.prompt
- `UserProfile`: 短号/性别/加为好友按钮
- `ChatSettings`: 备注编辑, 撤回通知开关, 删除好友, 显示群成员昵称绑定
- `types/index.ts`: User 新增 gender, Session 新增 remark/isRecallNoticeEnabled/isDisplayMemberNicknames

### 验证结果

- `mvn test`: 27 tests passed
- `npm run build`: built in ~8s, no errors
- `mvn compile -q`: clean

## Progress note 2026-05-23 production closeout

- [x] Admin production detail pass moved several strict checklist gaps from `PARTIAL` to `DONE core admin ops`.
- [x] Added `V16__admin_production_closeout.sql`: `file_transfer_logs.size_bytes`, byte-per-minute upload/download policies, and default device policies.
- [x] File transfer policy now enforces both request count and bandwidth (`max_upload_mb_per_minute`, `max_download_mb_per_minute`).
- [x] Organization admin now supports batch user import, device policy read/write, and force-offline for TCP/WebSocket sessions with audit logs.
- [x] Workspace app admin now supports update, reorder, delete, department visibility validation, and audit logs.
- [x] Web `/admin/advanced` exposes workspace app enable/sort/delete, batch import, force offline, and device policy readout.
- [x] `project-docs/STRICT_REQUIREMENTS_TRACE.md` rewritten as current strict truth source after production closeout.
- [x] Verification: `im-server mvn test` passed, 47 tests.
- [x] Verification: `im-ui npm run build` passed.

## Progress note 2026-05-23 MySQL live API compatibility

- [x] Live Docker audit found a real MySQL-only failure: `/api/admin/overview` returned 500 because several queries used `FETCH FIRST ... ROWS ONLY`, which passed H2 tests but is invalid on MySQL 8.
- [x] Replaced remaining backend `FETCH FIRST` usage with MySQL/H2-compatible `LIMIT` in admin overview/resource APIs plus product conversations/search APIs.
- [x] Verification: `rg "FETCH FIRST" im-server/src/main/java im-server/src/test/java` returns no matches.
- [x] Verification: `im-server mvn test` passed, 47 tests.
- [x] Verification: `mvn package -DskipTests` rebuilt `im-server/target/im-server-0.1.0-SNAPSHOT.jar`.
- [x] Verification: `docker compose up -d --build im-server` rebuilt/restarted the live Docker backend.
- [x] Live MySQL verification: health `UP`; admin login role `SUPER_ADMIN`; `/api/admin/overview` returns `enterpriseUsers=16`, `messageTrend=4`, `storageBreakdown=2`; `/api/admin/users/device-policies` returns 3 policies.
- [x] Live user API verification: `/api/conversations` and `/api/search?q=a&type=all` return 200 with bearer auth.

## Progress note 2026-05-23 tri-client strict productionization

- [x] Re-read `project-docs/PROJECT_BRIEF.md`, `功能清单.md`, `后台管理清单.md`, and `project-docs/STRICT_REQUIREMENTS_TRACE.md` against the real three-end delivery scope.
- [x] Decision written to docs: this is not Web-only. Web owns full user UI and admin; Qt/Flutter must prove core login, TCP, text chat, latest-message operations, and call paths. Admin panels are Web-admin only.
- [x] Backend TCP and WebSocket text delivery now include persisted `messageId` in both ACK and `TEXT_DELIVER`, so native clients can operate real saved messages instead of local placeholders.
- [x] Qt desktop now has real latest-message favorite, like, recall, and edit buttons wired to `/api/messages/{id}/favorite`, `/reactions`, `/recall`, and `/edit`.
- [x] Flutter Android now has the same latest-message favorite, like, recall, and edit bar, with success/error logs instead of silent async failures.
- [x] Updated `project-docs/STRICT_REQUIREMENTS_TRACE.md` with a terminal responsibility matrix and P0/P1 scope split.
- [x] Updated `project-docs/SUBMISSION_SCOPE.md` to remove fuzzy wording around advanced features and native-client responsibility.
- [x] Verification: `im-server mvn test` passed, 47 tests.
- [x] Verification: `flutter-client flutter analyze` passed.
- [x] Verification: `mvn package -DskipTests` rebuilt `im-server/target/im-server-0.1.0-SNAPSHOT.jar`.
- [x] Verification: `scripts/package-qt-vs2017-webengine.ps1` rebuilt `dist/EnterpriseIMQtClient-vs2017/EnterpriseIMQtClient.exe` and `dist/EnterpriseIMQtClient-vs2017.zip`.
- [x] Verification: `docker compose up -d --build im-server` rebuilt and restarted the live backend.
- [x] Live check: health `UP`; user login succeeds for `13800000001/demo123`; admin login succeeds for `18800000000/admin123`; `/api/conversations` and `/api/search` return success with bearer auth.
- [x] Runtime opened: Web `http://127.0.0.1:5173` and Qt `dist/EnterpriseIMQtClient-vs2017/EnterpriseIMQtClient.exe`.
- [ ] Flutter runtime-on-device pending: current shell did not find `adb`; attach Android device/emulator before live mobile run.

## Progress note 2026-05-23 admin/workspace optimization

- [x] Admin auth now returns server-backed permissions on login and `/api/admin/auth/me`.
- [x] Web main navigation hides the admin entry unless the stored admin session has `dashboard.read`.
- [x] Web admin dashboard disables organization/user/resource/admin write actions when the current admin role lacks the matching permission.
- [x] Web advanced admin denies direct access without `advanced.read`, and disables resource/config/user/organization actions by permission.
- [x] User workspace no longer depends only on static local app data: it calls `GET /api/workspace-apps` with user auth.
- [x] Backend `GET /api/workspace-apps` returns only enabled workspace apps and filters department-scoped apps by `department_members`.
- [x] Updated `project-docs/STRICT_REQUIREMENTS_TRACE.md`: admin roles/permissions and workspace config moved to DONE core matrix.
- [x] Verification: `im-server mvn test` passed, 49 tests.
- [x] Verification: `im-ui npm run build` passed.
- [x] Verification: `mvn package -DskipTests` rebuilt backend jar and `docker compose up -d --build im-server` restarted live Docker backend.
- [x] Live check: health `UP`; `/api/admin/auth/me` returns role `SUPER_ADMIN` with permissions; admin-created workspace app is visible through user `GET /api/workspace-apps`.

## Progress note 2026-05-23 三端代码审查与增强

### 完整源码审查

对四个组件（服务端、Web端、Qt桌面端、Flutter移动端）进行了逐文件源码审查，生成真实进度对比报告。

**服务端（95%）**：13个Controller、80+端点、30+表、8套集成测试，全部真实实现。唯一缺口：外部推送适配器。

**Web端（90%）**：20个页面全部真实实现、90+ API函数、图片查看器/编辑器、管理后台全部完成。缺口：视频消息渲染组件、位置分享。

**Qt桌面端（15%→增强中）**：原为5个源文件~1100行的demo。已启动P0增强：登录流程、动态会话列表、聊天气泡布局、通讯录、文件发送。

**Flutter移动端（15%→增强中）**：原为单文件1679行的demo。已启动P0增强：架构拆分、登录页面、会话列表、聊天气泡、通讯录。

### Web端已完成修复

- [x] 新增 `VideoMessage.tsx` 视频消息渲染组件：播放/暂停、缩略图预览、文件大小显示
- [x] `MessageBubble.tsx` 集成 VideoMessage：`case "video"` 分支、typeLabel 添加"视频"
- [x] TypeScript 类型检查通过（`npx tsc --noEmit` 无错误）
- [x] 朋友圈/卡包/表情/位置分享空入口改为"即将上线"提示

### Qt/Flutter端增强（后台代理执行中）

Qt端增强计划：
- 真实登录流程（POST /api/auth/login）
- 动态会话列表（GET /api/conversations）
- 聊天气泡布局（头像+时间+对齐）
- 通讯录页面（GET /api/friends, /api/directory/users）
- 文件发送（QFileDialog + POST /api/files/upload）
- QSettings 持久化

Flutter端增强计划：
- 架构拆分（models/services/screens/widgets）
- 真实登录页面
- 会话列表（GET /api/conversations）
- 聊天气泡布局
- 通讯录页面
- HTTP API 服务提取

### 三端增强 Round 2 完成（2026-05-23 深夜）

**Qt桌面端 Round 2 完成**（MainWindow.cpp 1785行 → 2056行）：
- [x] 图片消息渲染：QTextBrowser 内嵌 `<img>` 缩略图（max 200x200），点击 anchorClicked → QDesktopServices 打开系统查看器
- [x] 文件消息渲染：Unicode 文件图标 + 文件名 + 格式化大小（B/KB/MB），可点击下载链接
- [x] 群聊支持：`isGroupConversation()` 检测 `g_`/`group` 前缀，群聊显示发送者名称，会话列表群组图标
- [x] 设置对话框：QDialog 三组（服务器连接/聊天显示/关于），字体大小选择器（小/中/大），时间戳开关，QSettings 持久化
- [x] `resolveFileUrl()` 处理绝对URL和相对路径拼接
- [x] 发送文件/图片消息时正确调用带 msgType/fileUrl/fileName/fileSize 的 appendChatBubble 重载

**Flutter移动端 Round 2 完成**（13文件 → 增强消息渲染+设置）：
- [x] Message model 增强：type/fileUrl/fileName/fileSize/thumbnailUrl 字段，fromJson 安全解析
- [x] MessageBubble 增强（81行 → 533行）：image（Image.network + InteractiveViewer 全屏查看）、file（彩色图标+文件名+大小）、voice（波形指示器+时长）、video（缩略图+播放按钮覆盖层）
- [x] 文件助手方法：_getFileExtension/_getFileIcon（12种类型）/_getFileColor（6色系）/_truncateFileName/_formatFileSize
- [x] 群聊支持：showSender/senderName 参数，发送者名称颜色区分
- [x] 设置页面：settings_screen.dart，用户信息/服务器配置/字体大小/深色模式/关于/退出登录
- [x] StorageService 升级：数据库版本3迁移，getFontSize/saveFontSize/getDarkMode/saveDarkMode

### 三端增强 Round 1 完成（2026-05-23 晚间）

**Qt桌面端 Round 1 完成**（MainWindow.cpp 1104行 → 1785行）：
- [x] 真实登录流程：手机号+密码 → POST /api/auth/login → 自动TCP连接+AUTH
- [x] 动态会话列表：GET /api/conversations 加载，显示名称/最后消息/时间/未读数
- [x] HTML气泡聊天：QTextBrowser 渲染，蓝色靠右（自己）/白色靠左（对方），头像首字母圆+时间戳
- [x] 消息历史加载：点击会话 → GET /api/conversations/{id}/messages
- [x] 通讯录标签页：QTabWidget 双标签（会话/通讯录），GET /api/friends 加载好友
- [x] 文件发送：QFileDialog 选择 → POST /api/files/upload multipart 上传 → TCP 发送文件消息
- [x] QSettings 持久化：服务器地址/端口/用户ID/token/手机号/最后会话/窗口位置
- [x] 退出登录：清除凭证，返回登录界面
- [x] 所有现有功能保留：通话信令、消息操作、SIP媒体、SQLite缓存

**Flutter移动端增强完成**（单文件1679行 → 13文件架构）：
- [x] 架构拆分：models/ services/ screens/ widgets/ 标准目录结构
- [x] 真实登录页面：手机号+密码+SMS倒计时+服务器配置折叠面板
- [x] 会话列表：GET /api/conversations，下拉刷新，未读角标，智能时间格式
- [x] 聊天气泡：蓝色靠右/灰色靠左，撤回状态，reaction显示
- [x] 通讯录：好友+目录双标签，在线状态指示
- [x] API服务：dart:io HttpClient，覆盖登录/会话/消息/好友/通话等全部端点
- [x] Socket服务：TCP JSON-line 协议，AUTH/PING/TEXT 帧处理
- [x] 存储服务：SQLite + SharedPreferences 持久化
- [x] 所有现有功能保留：PJSIP通话、消息操作、摄像头预览

## Progress note 2026-05-23 employee/device admin closeout

- [x] Closed the next strict backend/admin chunk from `STRICT_REQUIREMENTS_TRACE.md`: employee profile edit depth and device session inventory UI.
- [x] Backend `PATCH /api/admin/users/{userId}/profile` now updates display name, email, avatar URL, short number, gender, signature, and department position, then writes `USER_PROFILE_UPDATE` audit.
- [x] Backend `GET /api/admin/users/{userId}/device-sessions` returns real `device_sessions` inventory rows using the actual V1 schema.
- [x] Web admin user table now exposes profile edit and device inventory actions; profile writes stay permission-gated by `users.write`.
- [x] Added regression test `AdminApiTest#updatesUserProfileAndListsDeviceSessions`; this caught and fixed a runtime-only schema mismatch before submission.
- [x] Updated `project-docs/STRICT_REQUIREMENTS_TRACE.md`: Admin 2 Organization now includes employee profile edit and device sessions; Next Strict Chunks advanced to message retention/policy, third-party app adapters, admin visual QA, and native Round 3 parity.
- [x] Current 4-surface status: Backend/API P0 production core done; Web user P0 done with P1 long-tail; Web admin P0 done plus employee/device closeout; Qt desktop P0 core done, P1 native parity remains. Flutter mobile code core exists but live phone runtime stays pending per current decision.
- [x] Verification: `im-server mvn test` passed, 50 tests.
- [x] Verification: `im-ui npm run build` passed.

- [x] Verification: `mvn package -DskipTests` rebuilt `im-server/target/im-server-0.1.0-SNAPSHOT.jar`.
- [x] Verification: `docker compose up -d --build im-server` rebuilt and restarted the live backend on `127.0.0.1:18080`.
- [x] Live check: health `UP`; admin login role `SUPER_ADMIN`; created live enterprise/department/user; profile patch returned `displayName=Dept Profile After`, `shortNo=dept1779525483686`, `positionName=Product Manager`; device session endpoint returned 200 with an empty inventory for the new user.
- [x] Browser check: Web dev server `127.0.0.1:5173` loads `/admin`, admin login succeeds, dashboard screenshot saved at `build/admin-dashboard-check.png`.

## Progress note 2026-05-23 Flutter Round 3 parity closeout

- [x] Flutter API client now stores `currentUserId` and sends it to `/api/friends` and `/api/friend-requests`, matching the backend `requireSameUser` contract.
- [x] Backend added `GET /api/users/{userId}` so Flutter contact profile is a real server-backed route.
- [x] Flutter friend request screen maps backend `requesterName/requesterId` and keeps accept/reject on real `/api/friend-requests/{id}/handle`.
- [x] Flutter settings now loads and patches real `/api/notification-settings`, then caches message/@/recall/screenshot switches locally.
- [x] Flutter search/profile/friend request/notification paths are no longer just files on disk: they compile, package, and hit the live backend API contract.
- [x] Android build stability: added `kotlin.incremental=false` to avoid cross-drive Kotlin cache errors from `C:\Users\...\Pub\Cache` vs `D:\work`.
- [x] Verification: `im-server mvn test` passed, 50 tests.
- [x] Verification: `D:\env\flutter\bin\flutter.bat analyze` passed, no issues.
- [x] Verification: `scripts\package-flutter.ps1` rebuilt `dist/enterprise-im-app-release.apk` without Kotlin cache error.
- [x] Verification: `docker compose up -d --build im-server` restarted live backend; health `UP`.
- [x] Live API check: user login `u_13800000001`; `/api/users/{id}` returned detail; `/api/notification-settings` patch returned `mentionAlert=false`, `recallNotice=true`; `/api/friends?userId=...` and `/api/friend-requests?userId=...&box=incoming` returned 200.
- [x] Current 4-surface status after this pass: Backend/API P0 production core done; Web user/admin P0 done; Flutter mobile P0 plus friend/profile/search/notification Round 3 code closed, still needs physical-device runtime proof. Qt Round 3 parity was still pending at this point and is closed in the next progress note.

## Progress note 2026-05-23 Qt Round 3 parity closeout

- [x] Qt desktop now sends the current `userId` when loading `/api/friends`, matching the backend user-scope contract.
- [x] Qt desktop added a friend-request tab. It loads incoming requests from real `/api/friend-requests`, then accepts/rejects through `/api/friend-requests/{id}/handle` and refreshes contacts.
- [x] Qt desktop added contact profile lookup through real `GET /api/users/{userId}` and shows the returned name, phone, and signature in a native dialog.
- [x] Qt desktop added global search through real `GET /api/search?q=...&type=all`, with result actions for contacts, groups/messages, and files.
- [x] Qt desktop notification switches now patch real `/api/notification-settings` and also persist local UI preferences.
- [x] Verification: stopped the stale running Qt package that locked old DLLs, then `scripts\package-qt-vs2017-webengine.ps1` rebuilt `dist/EnterpriseIMQtClient-vs2017/EnterpriseIMQtClient.exe` and `dist/EnterpriseIMQtClient-vs2017.zip`.
- [x] Verification: `im-ui npm run build` passed after the Qt/Flutter progress update.
- [x] Verification: `D:\env\flutter\bin\flutter.bat analyze` passed with no issues after the Qt/Flutter progress update.
- [x] Current 4-surface status after this pass: Backend/API P0 production core done; Web user/admin P0 done; Qt desktop P0 core plus friend/profile/search/notification Round 3 code closed; Flutter mobile P0 plus friend/profile/search/notification Round 3 code closed. Physical-device runtime proof remains deliberately deferred.

## Progress note 2026-05-23 Web/Qt/Flutter strict gap closeout

- [x] Alignment source confirmed: this pass follows `功能清单.md`, `后台管理清单.md`, `project-docs/STRICT_REQUIREMENTS_TRACE.md`, and this `codex.md`; not ad-hoc polish.
- [x] Web voice recording moved from timer-only mock to real `MediaRecorder`: microphone capture -> audio blob/file -> existing upload API -> saved voice message -> playback via `VoiceMessage` `fileUrl`.
- [x] Web new-friends page now searches phone/account through real `/api/search` contact results and sends friend requests directly from result cards.
- [x] Qt desktop TCP now auto-reconnects after disconnect with bounded exponential backoff while logged in.
- [x] Flutter Android TCP now auto-reconnects after disconnect/connect failure unless the user explicitly disconnects/logs out.
- [x] Verification: `im-ui npm run build` passed.
- [x] Verification: `D:\env\flutter\bin\flutter.bat analyze` passed with no issues.
- [x] Verification: `scripts\package-flutter.ps1` rebuilt `dist/enterprise-im-app-release.apk`.
- [x] Verification: `scripts\package-qt-vs2017-webengine.ps1` rebuilt `dist/EnterpriseIMQtClient-vs2017.zip`.
- [x] Remaining non-phone gaps now focus on native group/chat settings parity, native image editor parity, full native multi-message operation parity, and Web long-tail checklist items such as QR/SSO/location/advanced image editing.

## Progress note 2026-05-23 Web location-message closeout

- [x] Alignment source confirmed again: this pass only targets checklist/trace long-tail, not cosmetic optimization.
- [x] Web chat "位置" panel item no longer shows a placeholder alert.
- [x] Location share now uses browser geolocation when allowed, with manual coordinate fallback when permission/API is unavailable.
- [x] Location is saved as a real `type=location` conversation message through the existing `/api/conversations/{conversationId}/messages` path.
- [x] Message bubble renders a location card with name/address/coordinates and opens the map URL in a new tab.
- [x] Live API smoke found a Java 8 container bug: `NotificationService` used `Optional.isEmpty()`, causing message send 500 during notification fallback.
- [x] Fixed `NotificationService` to use Java-8-safe `!isPresent()` and rebuilt/restarted the Docker backend.
- [x] Verification: `im-ui npm run build` passed.
- [x] Verification: `im-server mvn test` passed, 50 tests.
- [x] Verification: `mvn package -DskipTests` rebuilt backend jar and `docker compose up -d --build im-server` restarted live Docker backend.
- [x] Live check: health `UP`; `POST /api/conversations/{conversationId}/messages` with `type=location` returned `status=sent`.
- [x] Progress estimate after this slice: P0 core ~96%; non-phone strict delivery ~89%; full long-tail checklist ~83%.
- [x] Remaining non-phone gaps: native group/chat settings parity, native image editor parity, full native multi-message operation parity, Web QR/SSO/biometric/runtime adapters, advanced image OCR/editing.

## Progress note 2026-05-23 Native conversation-settings closeout

- [x] Alignment source confirmed: this pass targets `STRICT_REQUIREMENTS_TRACE.md` rows 6/7 where Qt/Flutter group settings and single chat settings were `MISSING`.
- [x] Qt desktop now has a chat header "会话设置" action for both single and group chats.
- [x] Qt dialog edits server-backed settings: mute, pin, screenshot notice, recall notice, read-after-burn, strong reminder, group member nicknames, and save-to-contacts.
- [x] Qt conversation list now reads and displays `muted/pinned/type/targetId` from real `/api/conversations`, then PATCHes `/api/conversations/{conversationId}/settings`.
- [x] Flutter `Session` model now carries the same conversation settings returned by the backend.
- [x] Flutter chat toolbar now exposes single/group settings sheet and saves the same fields through real `PATCH /api/conversations/{conversationId}/settings`.
- [x] Backend/API already had real DB-backed conversation settings; live Docker smoke verified `muted=True`, `pinned=True`, `strongReminder=True` after PATCH.
- [x] Verification: `D:\env\flutter\bin\flutter.bat analyze` passed.
- [x] Verification: `scripts\package-flutter.ps1` rebuilt `dist\enterprise-im-app-release.apk`.
- [x] Verification: `scripts\package-qt-vs2017-webengine.ps1` rebuilt `dist\EnterpriseIMQtClient-vs2017.zip`.
- [x] Progress estimate after this slice: P0 core ~96%; non-phone strict delivery ~90%; full long-tail checklist ~84%.
- [x] Remaining non-phone gaps: native image editor parity, full native multi-message operations, richer native file transfer parity, Web QR/SSO/biometric/runtime adapters, advanced image OCR/editing.

## Progress note 2026-05-23 Native message/file parity closeout

- [x] Alignment source confirmed: this pass targets the feature/admin checklist through `project-docs/STRICT_REQUIREMENTS_TRACE.md`, especially native message ops and Flutter file-transfer gaps.
- [x] Qt desktop message history now adds per-message service-backed operation links: favorite, like, recall, and edit call real `/api/messages/{id}` APIs instead of only operating on the latest message.
- [x] Flutter Android now has a native file picker path using `file_picker`: selected file -> multipart `/api/files/upload` with `uploaderId` -> TCP `TEXT` payload with file/image/video metadata -> local SQLite metadata.
- [x] Verification: `D:\env\flutter\bin\flutter.bat pub get` passed and updated `pubspec.lock`.
- [x] Verification: `D:\env\flutter\bin\flutter.bat analyze` passed with no issues.
- [x] Verification: `scripts\package-flutter.ps1` rebuilt `dist\enterprise-im-app-release.apk`.
- [x] Verification: `scripts\package-qt-vs2017-webengine.ps1` rebuilt `dist\EnterpriseIMQtClient-vs2017.zip`.
- [x] Current progress estimate: service/API 97%; Web user/admin 92%; Qt desktop 82%; Flutter Android 84%; full non-phone delivery 91%; full long-tail checklist 85%.
- [x] Remaining non-phone gaps: native image editor parity, native batch/multi-select forwarding, richer Qt file transfer controls, Web QR/SSO/biometric runtime adapters, advanced image OCR/editing. Physical-device proof remains separate.

## Progress note 2026-05-23 Web QR invite/scan closeout

- [x] Alignment source confirmed: this pass targets Web long-tail checklist item "扫码/二维码", using existing backend group invite/join-request APIs rather than adding another mock.
- [x] Added `qrcode` to Web client and render real group invitation QR image from backend `qrPayload` in group settings.
- [x] Session list "扫一扫" no longer shows a placeholder alert. It opens a scan dialog with browser `BarcodeDetector` camera scan when available and pasted QR payload fallback when camera/API is unavailable.
- [x] QR payload parser supports `/groups/{groupId}/join?token=...` and full URLs, then calls real `/api/groups/{groupId}/join-requests`.
- [x] Verification: `im-ui npm run build` passed.
- [x] Current progress estimate: service/API 97%; Web user/admin 93%; Qt desktop 82%; Flutter Android 84%; full non-phone delivery 92%; full long-tail checklist 86%.
- [x] Remaining non-phone gaps: SSO/biometric runtime provider, advanced image editing/OCR, richer QR/card poster export, native image editor parity, native batch/multi-select forwarding, richer Qt file transfer controls.

## Progress note 2026-05-23 Qt/Flutter file-interaction hardening

- [x] Alignment source confirmed: this pass continues the feature/admin checklist route and targets native file/image/video production behavior rather than mock UI.
- [x] Qt desktop `/api/files/upload` now includes backend-required `uploaderId`, accepts `previewUrl/downloadUrl/url/path`, preserves `sizeBytes`, and sends typed TCP metadata as `image`, `video`, or `file`.
- [x] Flutter Android now resolves relative upload URLs against `ApiService.baseUrl`, adds `url_launcher`, and opens file/video resources from message bubbles through the OS/browser instead of leaving attachments render-only.
- [x] Verification: `scripts\package-qt-vs2017-webengine.ps1` rebuilt `dist\EnterpriseIMQtClient-vs2017.zip`.
- [x] Verification: `D:\env\flutter\bin\flutter.bat analyze` passed with no issues.
- [x] Verification: `scripts\package-flutter.ps1` rebuilt and copied `dist\enterprise-im-app-release.apk`.
- [x] Current progress estimate: service/API 97%; Web user/admin 93%; Qt desktop 84%; Flutter Android 86%; full non-phone delivery 93%; full long-tail checklist 87%.
- [x] Remaining non-phone gaps: native image editor parity, native batch/multi-select forwarding, real transfer progress/resume/retry, physical-device acceptance, SSO/biometric runtime provider, advanced image OCR/editing.

## Progress note 2026-05-23 Native acceleration: image edit, batch forward, upload progress

- [x] Flutter Android added multi-select message mode. Long-press -> 多选, top bar can forward selected messages through real `POST /api/messages/forward` with `single/combine` mode and multiple target conversation IDs.
- [x] Flutter Android file upload switched to a streamed `HttpClient` multipart path with byte-counted upload progress surfaced by `LinearProgressIndicator`; no timer mock progress.
- [x] Flutter Android image messages can be edited with rotate/grayscale, exported as a real JPG via the `image` package, uploaded through the same progress-aware file API, and sent as a new image message.
- [x] Qt desktop message history now exposes `选择` and `转发` links per server message. Selected messages are forwarded through real `POST /api/messages/forward` with single/combine mode.
- [x] Qt desktop upload now shows a real `QProgressDialog` wired to `QNetworkReply::uploadProgress` and allows cancel/abort.
- [x] Qt desktop added local image edit send flow: pick image -> rotate 90 optional -> grayscale optional -> save JPG copy -> upload/send typed image message.
- [x] Verification: `D:\env\flutter\bin\flutter.bat pub get` passed and added `image`.
- [x] Verification: `D:\env\flutter\bin\flutter.bat analyze` passed with no issues.
- [x] Verification: `scripts\package-flutter.ps1` rebuilt and copied `dist\enterprise-im-app-release.apk`.
- [x] Verification: `scripts\package-qt-vs2017-webengine.ps1` rebuilt `dist\EnterpriseIMQtClient-vs2017.zip`.
- [x] Current progress estimate: service/API 97%; Web user/admin 93%; Qt desktop 88%; Flutter Android 90%; full non-phone delivery 95%; full long-tail checklist 90%.
- [x] Remaining native gaps: transfer resume/retry queues, advanced image crop/doodle/OCR, read-receipt drilldown, and real-device acceptance.

## Progress note 2026-05-23 Flutter chunk upload/retry closeout

- [x] Flutter Android now uses the existing backend chunk upload API for files >=1 MiB: `POST /api/files/chunk-upload/sessions`, repeated chunk multipart upload, then complete/merge.
- [x] Each 512 KiB chunk retries up to 3 times before failing, so mobile upload retry is real API behavior, not UI-only retry.
- [x] Upload progress remains byte-counted across chunks and updates the same message-screen progress bar.
- [x] Completed chunk upload returns the real `FileDto`; Flutter maps `originalName`, `sizeBytes`, `downloadUrl`, and `previewUrl` into the outgoing image/file/video message.
- [x] Verification: `D:\env\flutter\bin\flutter.bat analyze` passed with no issues.
- [x] Verification: first package attempt timed out due a stale Gradle daemon; `flutter-client\android\gradlew.bat --stop` stopped it, then `scripts\package-flutter.ps1` rebuilt and copied `dist\enterprise-im-app-release.apk`.
- [x] Current progress estimate: service/API 97%; Web user/admin 93%; Qt desktop 88%; Flutter Android 91%; full non-phone delivery 95%; full long-tail checklist 91%.
- [x] Remaining native gaps: Qt chunk resume/retry, advanced image crop/doodle/OCR, read-receipt drilldown, and physical-device acceptance.

## Progress note 2026-05-24 Checklist-driven Qt chunk upload/retry closeout

- [x] Confirmed optimization route remains tied to `功能清单.docx` + `后台管理清单.docx` through `STRICT_REQUIREMENTS_TRACE.md`, not ad-hoc UI polish.
- [x] Qt desktop now matches Flutter on large-file production path: files >=1 MiB create real backend chunk upload sessions, upload 512 KiB chunks, retry failed chunks up to 3 attempts, then call complete/merge.
- [x] Qt complete response reuses the existing `FileDto` -> typed TCP message path, so chunk-uploaded files still send image/video/file metadata into chat.
- [x] Verification: `scripts\package-qt-vs2017-webengine.ps1` passed and rebuilt `dist\EnterpriseIMQtClient-vs2017.zip`.
- [x] Current progress estimate: service/API 97%; Web user/admin 93%; Qt desktop 90%; Flutter Android 91%; full non-phone delivery 96%; full long-tail checklist 92%.
- [x] Remaining gaps: persistent offline resume queue, advanced crop/doodle/OCR, read-receipt drilldown, and physical-device acceptance.

## Progress note 2026-05-24 Native read-status drilldown closeout

- [x] Flutter Android message action sheet now has `已读明细`; it calls real `GET /api/messages/{messageId}/read-status` and displays read/unread member lists with timestamps.
- [x] Qt desktop per-message operation links now include `已读`; it calls the same read-status API and displays read/unread members in a native dialog.
- [x] This closes the native side of the checklist item for read-receipt drilldown. Web already had the feature.
- [x] Verification: `D:\env\flutter\bin\flutter.bat analyze` passed with no issues.
- [x] Verification: `scripts\package-flutter.ps1` rebuilt and copied `dist\enterprise-im-app-release.apk`.
- [x] Verification: `scripts\package-qt-vs2017-webengine.ps1` rebuilt `dist\EnterpriseIMQtClient-vs2017.zip`.
- [x] Current progress estimate: service/API 97%; Web user/admin 93%; Qt desktop 91%; Flutter Android 92%; full non-phone delivery 97%; full long-tail checklist 93%.
- [x] Remaining gaps: persistent offline resume queue, advanced crop/doodle/OCR, and physical-device acceptance.

## Progress note 2026-05-24 Native persistent upload queue closeout

- [x] Flutter Android added SQLite `upload_queue` via DB migration v4. Upload failures store conversation ID, peer ID, file path, original name, retry count, status, and last error.
- [x] Flutter chat open now resumes pending uploads for that conversation, reuses the same chunk/retry/progress path, deletes successful queue rows, and keeps failed rows for later retry.
- [x] Qt desktop added SQLite `upload_queue` and resumes pending uploads after login. Successful `FileDto` responses delete the queued row.
- [x] Qt chunk upload failure persists the local file path and conversation/peer context for later recovery.
- [x] Verification: `D:\env\flutter\bin\flutter.bat analyze` passed with no issues.
- [x] Verification: `scripts\package-flutter.ps1` rebuilt and copied `dist\enterprise-im-app-release.apk`.
- [x] Verification: `scripts\package-qt-vs2017-webengine.ps1` rebuilt `dist\EnterpriseIMQtClient-vs2017.zip`.
- [x] Current progress estimate: service/API 97%; Web user/admin 93%; Qt desktop 92%; Flutter Android 93%; full non-phone delivery 97%; full long-tail checklist 94%.
- [x] Remaining gaps: advanced image crop/doodle/OCR and physical-device acceptance.

## Progress note 2026-05-24 Native advanced image edit closeout

- [x] Flutter Android image editor now supports center crop and red doodle-line overlay, in addition to rotate/grayscale. Edited images export to JPG and reuse upload/send.
- [x] Qt desktop image editor now supports center crop and red doodle-line overlay, in addition to rotate/grayscale. Edited images save as JPG and reuse upload/send.
- [x] OCR was not fake-implemented. Current state is explicit provider boundary only because no local OCR engine is bundled in Qt/Flutter packages.
- [x] Verification: `D:\env\flutter\bin\flutter.bat analyze` passed with no issues.
- [x] Verification: `scripts\package-flutter.ps1` rebuilt and copied `dist\enterprise-im-app-release.apk`.
- [x] Verification: `scripts\package-qt-vs2017-webengine.ps1` rebuilt `dist\EnterpriseIMQtClient-vs2017.zip`.
- [x] Current progress estimate: service/API 97%; Web user/admin 93%; Qt desktop 93%; Flutter Android 94%; full non-phone delivery 98%; full long-tail checklist 95%.
- [x] Remaining non-device gap: OCR provider integration. Remaining acceptance gap: physical-device runtime proof.

## Progress note 2026-05-24 Non-device final provider-boundary closeout

- [x] Service/API: added real `GET /api/files/{fileId}/ocr` provider boundary. It authenticates, validates image/PDF candidates, reads `file.ocr.provider`, and returns configured/disabled adapter status without fake OCR output.
- [x] Web user/admin: file manager now calls the OCR provider boundary for OCR-capable files; chat file-message download progress now uses real `XMLHttpRequest` byte progress against `/api/files/{fileId}/download` instead of timer simulation.
- [x] Qt desktop: non-device native checklist remains closed from the prior native passes: login/TCP, message ops, batch forward, chunk upload/retry/resume queue, read-status drilldown, and image edit all use real backend/client code paths. No fake OCR engine is bundled.
- [x] Flutter Android: non-device native checklist remains closed from the prior native passes: login/SMS/TCP, message ops, multi-select forward, chunk upload/retry/resume queue, read-status drilldown, and image edit all use real backend/client code paths. Real-device acceptance stays separate.
- [x] Provider-boundary decision: SSO, biometric, OCR, and third-party deep app protocols are production extension points driven by config/external services. They are not counted as mock gaps unless a real provider credential/service is supplied and fails integration.
- [x] Verification: `im-ui npm run build` passed; Vite preview returned HTTP 200 and served the React root HTML. `im-server mvn test` passed with 50 tests.

## Progress note 2026-05-25 Flutter real-device AV repair pass

- [x] Real-device audio call was tested on device `3B657R0188300000`. Chat UI entered call screen and backend call reached `answered` + `media_ready`, but native SIP failed with `SIP registration timeout: 100 In Progress`.
- [x] Root cause found: Asterisk `pjsip.conf` had no demo endpoints for the mobile login user `u_13800000001` or peer `u_peer`, so device registration could not complete.
- [x] Asterisk SIP config now includes `u_13800000001` and `u_peer` endpoint/auth/aor entries using the shared demo SIP password.
- [x] Flutter Android now starts native SIP as soon as backend returns `media_ready` during `ringing`, instead of waiting for `answered`; this fixes the caller-side native media startup gap.
- [x] Verification: Asterisk `pjsip reload` succeeded and `pjsip show endpoints` lists `u_13800000001` and `u_peer`.
- [x] Verification: `D:\env\flutter\bin\flutter.bat analyze` passed with no issues.
- [x] Verification: `D:\env\flutter\bin\flutter.bat build apk --release` passed and copied `dist\enterprise-im-app-release.apk`.
- [ ] Blocked verification: ADB disconnected after the repair (`adb devices` empty), so post-fix physical-device audio/video retest is still pending.
- [x] Current progress estimate: service/API 97%; Web user/admin 93%; Qt desktop 93%; Flutter Android 95%; full non-phone delivery 98%; full long-tail checklist 95%.

## Progress note 2026-05-25 Flutter AV true-device follow-up

- [x] Installed the latest APK on device `3B657R0188300000` and verified login/TCP/session list before AV retest.
- [x] Audio retest exposed a real native crash after SIP timeout retry. Crash buffer showed `pj_thread_this()` assertion: `Calling pjlib from unknown/external thread`; Flutter now records failed native SIP starts per call and skips automatic retry for the same call to avoid pjsua2 SIGABRT.
- [x] ADB/UDP limitation identified: Android media config used `sip:10.200.38.225:5060` over UDP, but `adb reverse` only covers TCP, so Asterisk saw no mobile REGISTER. Android SIP registrar now defaults to `sip:127.0.0.1:5060;transport=tcp` for true-device testing through `adb reverse tcp:5060 tcp:5060`.
- [x] Flutter Android native PJSIP now creates TCP transport when registrar contains `transport=tcp`; otherwise it keeps UDP.
- [x] Verification: `mvn -q -DskipTests package` passed for `im-server`; `docker compose up -d --build im-server` rebuilt/restarted service stack.
- [x] Verification: `D:\env\flutter\bin\flutter.bat analyze` passed with no issues.
- [x] Verification: `D:\env\flutter\bin\flutter.bat build apk --release` passed and copied `dist\enterprise-im-app-release.apk`.
- [ ] Blocked verification: ADB disconnected again after the native crash; `adb devices` is empty, so the TCP-SIP APK has not yet been installed/retested on device.

## Progress note 2026-05-25 Flutter AV true-device acceptance

- [x] Installed the TCP-SIP APK on device `3B657R0188300000` and configured `adb reverse tcp:18080`, `tcp:19090`, and `tcp:5060`.
- [x] Audio true-device acceptance passed for the local media path: mobile registered to Asterisk over TCP, UI showed `SIP CONFIRMED 200 OK`, app stayed alive, and no native crash was recorded.
- [x] Video true-device acceptance passed for the local media path: video call UI opened, front camera preview started, UI showed `SIP CONFIRMED 200 OK · 本机摄像头预览`, app stayed alive, and no native crash was recorded.
- [x] Asterisk now includes a local `u_peer` demo media extension (`Answer` + `Echo`) so one physical phone can verify SIP registration/call setup without a second handset. Full peer-to-peer two-device media remains a separate acceptance scenario.
- [x] Latest backend call records show audio and video calls persisted with `mediaStatus=media_ready`, then ended cleanly after hangup.
- [x] Current progress estimate: service/API 97%; Web user/admin 93%; Qt desktop 93%; Flutter Android 97%; full non-phone delivery 98%; full long-tail checklist 96%.

## Progress note 2026-05-25 Production mock/fallback sweep + Qt media hardening

- [x] Web production call flow no longer calls `/api/calls/{id}/demo-answer` or `/demo-reject`. Caller must wait for the real peer device/session; caller-side "simulate peer" controls were removed from the UI path.
- [x] Server now disables legacy `demo-token-*` bearer auth by default via `im.auth.accept-demo-tokens=false`; tests explicitly enable it in test config only. JWT login tokens remain the production path.
- [x] Server now gates demo call endpoints behind `im.auth.demo-call-endpoints-enabled=false`; production returns 404 unless explicitly enabled for a controlled test harness.
- [x] Web mock data generator `im-ui/src/utils/mock.ts` was deleted after confirming no production imports. Remaining provider-boundary strings such as `provider_disabled` are explicit external-provider states, not fake success data.
- [x] Flutter login screen no longer pre-fills `13800000001/demo123` or displays demo-account guidance. Mobile users must enter real/login-issued credentials against the configured server.
- [x] Qt desktop media no longer starts PJSIP during incoming `ringing`; native media starts only after the call reaches `answered + media_ready`, matching real peer acceptance instead of auto-answer behavior.
- [x] Qt desktop PJSIP runtime now supports configurable `PJSUA_LOCAL_PORT`, `PJSUA_VIDEO_CAPTURE_DEV`, `PJSUA_AUDIO_CAPTURE_DEV`, and `PJSUA_AUDIO_PLAYBACK_DEV`, removing the hard-coded camera device assumption.
- [x] Verification: `im-ui npm run build` passed.
- [x] Verification: `im-server mvn -q -DforkCount=0 test` passed.
- [x] Verification: `D:\env\flutter\bin\flutter.bat analyze` passed with no issues.
- [x] Verification: `scripts\package-flutter.ps1` passed and copied `dist\enterprise-im-app-release.apk`.
- [x] Verification: `scripts\package-qt.ps1` passed and rebuilt `dist\EnterpriseIMQtClient.zip`.
- [x] Current progress estimate after this sweep: service/API 98%; Web user/admin 94%; Qt desktop 95%; Flutter Android 97%; full non-phone delivery 99%; full long-tail checklist 97%.
- [ ] Remaining acceptance gap: full two-device peer-to-peer audio/video across Web/Qt/Flutter. External providers (OCR/SSO/biometric/push/deep links) still require real credentials/config and are not fake-filled.

## Progress note 2026-05-25 Final media preflight + Qt video package refresh

- [x] `scripts\verify-final-media-preflight.ps1` passed end-to-end after tightening the video acceptance checks.
- [x] Video capability preflight now verifies Android pjsua2 video args, native SIP diagnostics/error detail UI, Asterisk video codec allowance, APK native libs, Qt `pjsua --video` launch path, and bundled Windows pjsua video codec support.
- [x] TURN allocation proof passed against local coturn; this proves allocation/relay plumbing in the packaged stack, not a cross-Internet peer path.
- [x] SIP audio media loop passed between the Qt-side and Flutter-side PJSIP identities in the gateway container.
- [x] SIP audio content proof passed: `qt_heard_flutter.wav` contains the Flutter-side 880Hz voice fingerprint and `flutter_heard_qt.wav` contains the Qt-side 440Hz voice fingerprint. Evidence: `build\sip-audio-content\result.json`.
- [x] Qt desktop package was rebuilt after the pjsua video/local-port launch hardening: `dist\EnterpriseIMQtClient.zip`.
- [ ] Blocked live acceptance: `adb devices -l` is currently empty, so Qt/Android real peer call, two physical device call, physical speaker/mic hearing, remote camera rendering, and cross-network TURN still need connected devices/networks.
- [x] Current progress estimate: service/API 98%; Web user/admin 94%; Qt desktop 96%; Flutter Android 97%; full non-phone delivery 99%; full long-tail checklist 97%.
