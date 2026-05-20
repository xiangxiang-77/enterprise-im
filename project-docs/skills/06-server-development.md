# Skill: 服务端开发

## 目标

用 Java 实现 IM 服务端、后台 API、TCP 网关、Docker 镜像。

## 推荐模块

- `auth`：登录、Token、设备会话。
- `user`：用户资料。
- `org`：企业、部门、员工。
- `friend`：好友申请、关系、黑名单。
- `group`：群、成员、公告、权限。
- `message`：消息存储、撤回、编辑、已读、点赞。
- `file`：文件元数据、上传下载签名。
- `socket`：Netty TCP 网关、心跳、ACK、离线同步。
- `admin`：后台管理 API。
- `audit`：审计日志。
- `call`：PJSIP/coturn 信令和通话记录。

## 开发顺序

1. Spring Boot 工程和配置。
2. DB migration。
3. Auth + user。
4. Netty TCP 鉴权、心跳。
5. 单聊文本闭环。
6. 群聊、回执、离线消息。
7. 文件、资源、后台审计。
8. Docker 镜像。

## 完成标准

- `docker build` 成功。
- 服务启动后健康检查成功。
- 核心 API 有测试。
- TCP 收发有集成测试。

