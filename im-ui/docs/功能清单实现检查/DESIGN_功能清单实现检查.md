# 系统架构设计：功能清单实现

## 1. 核心架构扩展

### 1.1 消息系统增强 (Message System)
- **Message Types**: 扩展 `src/types/index.ts` 中的 `Message` 类型，增加 `voice` (duration, url), `file` (size, name, url), `card` (userId, name, avatar), `system` (content, action), `video` (thumbnail, duration)。
- **Message Components**:
  - `VoiceMessage`: 播放按钮、波形动画、时长显示。
  - `FileMessage`: 图标、文件名、大小、下载按钮。
  - `CardMessage`: 用户头像、昵称、点击跳转。
  - `SystemMessage`: 居中显示的灰色小字（如“撤回了一条消息”）。
- **Input Area**:
  - `VoiceRecorder`: 长按录音按钮，上滑取消，松手发送。
  - `EmojiPicker`: 集成 Emoji 选择器。
  - `MorePanel`: 发送图片、文件、名片入口。

### 1.2 会话管理增强 (Session Management)
- **Group Management**:
  - `GroupSettings`: 群名称修改、公告编辑、成员列表（添加/移除）。
  - `MemberSelector`: 通用联系人选择器组件（用于建群、转发、发送名片）。
- **Contact Management**:
  - `NewFriend`: 好友申请列表、接受/拒绝操作。
  - `ContactDetail`: 资料页、发消息/音视频入口。

### 1.3 全局状态与设置 (Global State)
- **Settings Store**: 管理主题（Dark/Light）、语言、字体大小、通知开关。
- **Search Engine**: 前端搜索实现（Fuse.js 或简单 filter），支持搜索联系人、群组、聊天记录。

## 2. 接口契约 (Mock API)
由于无后端，所有数据操作通过 `src/utils/mock.ts` 和 `useChatStore` 模拟。
- `uploadFile(file)`: 返回 Promise<{ url, name, size }>，模拟进度。
- `search(query)`: 返回 { users, groups, messages }。

## 3. 组件依赖关系
- `ChatRoom` -> `MessageList` -> `MessageBubble` -> [Text, Image, Voice, File, Card]
- `ChatInput` -> [VoiceRecorder, EmojiPicker, ActionPanel]
- `ChatSettings` -> [GroupInfo, MemberList]
- `ForwardDialog` -> `ContactSelector`

## 4. 异常处理
- **发送失败**: 模拟网络错误，显示红色感叹号，点击重试。
- **文件下载失败**: 提示错误。

## 5. 数据流向
User Action -> Component -> Store Action -> Mock API (Delay) -> Store Update -> UI Re-render
