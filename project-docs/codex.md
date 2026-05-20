# codex.md

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
- [x] Added `scripts/verify-final-media-preflight.ps1` to chain all automated media checks: native runtime bundle, video capability, TURN allocation, and SIP PCMU/RTP loop.
- [x] `scripts/verify-final-media-preflight.ps1` passed; remaining acceptance is real-device only: physical microphone/speaker, Android camera permission, camera preview/render, and external-network TURN traversal.

## Progress note 2026-05-20 Desktop/mobile call UI and mobile video bridge

- [x] Qt desktop now opens a product-style full call screen for audio/video calls, with incoming/answered state, answer/reject/hangup controls, and local camera preview when QtMultimedia is available.
- [x] Flutter Android now opens a full call page instead of only inline buttons: incoming calls show answer/reject, active calls show hangup, and video calls reserve a remote video stage plus local camera preview PiP.
- [x] Flutter call guard fixed: the caller's own outgoing audio/video call no longer exposes or allows the mobile-side "answer" action.
- [x] Flutter Android now registers `enterprise_im/pjsip_video_view` and attempts to attach the native PJSIP incoming `VideoWindow` to an Android `SurfaceView`.
- [x] Flutter packaging now stops the Gradle daemon before release build to avoid stale R8/classes.dex file locks.
- [x] Latest mobile APK rebuilt: `dist/enterprise-im-app-release.apk` (`0.1.5+6`, 2026-05-20 10:29, 56,571,344 bytes).
- [x] Latest delivery package rebuilt: `dist/EnterpriseIM-Delivery-Package.zip` (2026-05-20 10:30).
- [x] Local verification covered `flutter analyze`, `scripts/verify-video-capability.ps1`, `scripts/verify-final-media-preflight.ps1`, and delivery packaging.
- [ ] Remaining acceptance is real-device only: Android camera/speaker/microphone behavior, actual remote video rendering quality, and cross-network TURN traversal. Current workstation has no connected ADB device/emulator.

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
- [x] PJSIP/coturn 基础音视频通话通过：TURN allocation、SIP PCMU/RTP loop、视频能力预检均通过。
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
