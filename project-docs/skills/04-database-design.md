# Skill: 数据库设计

## 目标

设计支持 IM、后台、审计、文件、音视频的关系模型。

## 核心表

- `users`
- `enterprises`
- `departments`
- `department_members`
- `friend_requests`
- `friendships`
- `blacklists`
- `conversations`
- `conversation_members`
- `messages`
- `message_receipts`
- `message_reactions`
- `message_edits`
- `message_recalls`
- `groups`
- `group_members`
- `group_announcements`
- `files`
- `file_transfers`
- `audit_logs`
- `admin_users`
- `admin_roles`
- `admin_permissions`
- `admin_role_permissions`
- `call_records`
- `device_sessions`

## 设计规则

- ID 使用雪花 ID 或 UUID，所有端统一。
- 消息表按时间/会话可分区。
- 消息正文和扩展字段分离：常用字段列化，扩展用 JSON。
- 文件表只存元数据和对象存储 key。
- 审计日志追加写，不允许物理删除。
- 所有高频查询必须有索引方案。

## 完成标准

- 有 ER 图或表关系说明。
- 有初始化 SQL 或迁移脚本。
- 有索引、唯一约束、软删除、审计字段。

