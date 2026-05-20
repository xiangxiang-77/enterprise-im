# 待办事项 (TODO) - IM UI Frontend

## 必须解决 (Critical)
- [ ] **后端集成**: 替换 Mock 数据，对接真实后端 API (Login, User, Chat, Contact)。
- [ ] **WebSocket 通信**: 实现即时消息收发、在线状态同步、正在输入状态。
- [ ] **鉴权机制**: 实现 JWT Token 的存储、刷新和请求拦截器。

## 优化建议 (Improvement)
- [ ] **桌面端适配**: 增加桌面端分栏布局 (左侧列表，右侧聊天窗口)，提升大屏体验。
- [ ] **虚拟列表**: 对长消息列表和长联系人列表引入 `react-window` 或 `react-virtuoso` 进行性能优化。
- [ ] **图片编辑**: 增强图片查看器功能，添加裁剪、涂鸦、马赛克等编辑功能。
- [ ] **多媒体发送**: 实现语音录制、视频拍摄和发送功能 (目前仅有 UI 入口)。
- [ ] **文件上传**: 集成文件上传进度条和断点续传逻辑。

## 缺失配置 (Configuration)
- [ ] **环境变量**: 创建 `.env.production` 和 `.env.development` 配置 API Base URL。
- [ ] **CI/CD**: 配置 GitHub Actions 或 Jenkins 自动化构建流程。
- [ ] **Nginx 配置**: 编写 Nginx 配置文件以支持 React Router 的 History 模式部署。
