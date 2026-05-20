# 企业即时通讯交付工作区

本工作区包含企业 IM 系统当前交付切片。

## 项目

- `im-server`: Java Spring Boot + Netty TCP 后端，Compose 默认映射 HTTP `18080`、TCP `19090`。
- `im-ui`: React/Vite Web 客户端与管理控制台。
- `qt-client`: Qt 5.9.3 桌面客户端，包含会话栏、聊天区、TCP 协议、音视频信令入口。
- `flutter-client`: Flutter Android 客户端，包含移动端 IM 首页、聊天发送、音视频信令入口。
- `project-docs`: 路线图、能力说明、交付记录。

## 验证

运行全部可用检查：

```powershell
.\scripts\verify.ps1
```

必过检查：

- `im-server`: `mvn test`
- `im-ui`: `npm run build`
- Docker: `docker compose up -d` 后健康检查通过。
- Docker live API: 管理员连通性探针、用户 token 获取通话配置、无 token 访问通话配置被拒绝。

当前本机已生成交付物：

- 后端 jar：`im-server\target\im-server-0.1.0-SNAPSHOT.jar`
- Windows 客户端：`dist\EnterpriseIMQtClient-vs2017.zip`
- Android 客户端：`dist\enterprise-im-app-release.apk`
- PJSIP 网关合约：`project-docs\PJSIP_GATEWAY_CONTRACT.md`
- 提交范围说明：`project-docs\SUBMISSION_SCOPE.md`

依赖本机环境的检查缺工具时会标记为 `BLOCKED`。如只验证已有产物，可运行：

```powershell
.\scripts\verify.ps1 -SkipNativeBuild
```

音视频最终自动化预检：

```powershell
.\scripts\verify-final-media-preflight.ps1
```

该命令通过后，剩余只是真机验收：Qt/Android 麦克风、扬声器、摄像头画面、外网 TURN 穿透。

独立打包入口：

```powershell
.\scripts\package-qt.ps1
.\scripts\package-flutter.ps1
.\scripts\package-delivery.ps1
```

## 演示账号

- 用户端登录走 `/api/auth/login` 演示凭据。
- 管理端：`18800000000 / admin123`；密码可用 `ADMIN_PASSWORD` 覆盖。
