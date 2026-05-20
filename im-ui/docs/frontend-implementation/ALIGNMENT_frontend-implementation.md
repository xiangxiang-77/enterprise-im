# ALIGNMENT: Frontend Implementation for Enterprise IM Application

## 1. Project Context Analysis

### 1.1 Existing Project Structure
- The current project directory `d:\usr\build\im-ui` contains only the requirements file `im-ui.txt`.
- This is a greenfield project starting from scratch.

### 1.2 Technology Stack Proposal
Given the requirement for an "Enterprise-grade Instant Messaging Application" with complex UI interactions and component library needs, the following stack is proposed:
- **Framework**: React 18+ (with TypeScript)
- **Build Tool**: Vite
- **Styling**: Tailwind CSS
- **UI Components**: shadcn/ui (based on Radix UI) + Lucide React (Icons)
- **State Management**: Zustand (lightweight, flexible for complex state)
- **Routing**: React Router DOM v6
- **Date Handling**: date-fns (for smart timestamp formatting)
- **Mocking**: Faker.js (for generating realistic mock data)

### 1.3 Architecture Pattern
- **Feature-based structure**: Grouping files by feature (e.g., `features/chat`, `features/contacts`, `features/settings`).
- **Component Library**: A shared `components/ui` directory for reusable atoms (buttons, inputs, etc.) as requested in Requirement #15.

## 2. Requirement Understanding & Confirmation

The project aims to build a comprehensive frontend for an Enterprise IM app. The key modules identified from `im-ui.txt` are:

1.  **Session List (会话列表)**: Chat history, search, online status, smart timestamps.
2.  **Single Chat (单聊界面)**: Message bubbles, rich media (text, image, voice, file), message operations (recall, reply, forward).
3.  **Group Chat (群聊界面)**: Member management, group announcements, read status, mentions (@).
4.  **Image Viewer/Editor (图片查看器/编辑器)**: Zoom, crop, filter, doodle.
5.  **File Messages & Management (文件消息/管理)**: File types, preview, upload/download progress.
6.  **Group Settings (群聊设置)**: Member grid, admin controls, search history.
7.  **Single Chat Settings (单聊设置)**: User profile, privacy settings.
8.  **Contacts (通讯录)**: Dual mode (Organization Chart vs. Contacts List).
9.  **Friend Request Management (好友申请)**: New friends, history.
10. **Personal Profile (个人名片)**: User info, actions.
11. **Global Search (全局搜索)**: Unified search for contacts, groups, messages, files.
12. **Notification Settings (通知设置)**: Granular controls.
13. **General Settings (通用设置)**: Dark mode, language, font size, wallpaper.
14. **Login/Startup (登录/启动)**: Auth flow, biometric mock, onboarding.
15. **Component System (组件库)**: Design system foundation.

### Assumptions & Boundaries
- **Frontend Only**: No backend server will be implemented. All data will be mocked in the frontend state.
- **Mock Data**: "Network status", "Login status", "Upload progress", etc., will be simulated.
- **Platform**: Web application (responsive, mobile-first design principles but running in a browser environment).
- **Assets**: Placeholder images will be used for avatars and media.

## 3. Smart Decision Strategy (Clarifications)

| Question | Assumption/Decision |
| :--- | :--- |
| **Backend Integration?** | **Decision**: Pure frontend implementation with mock data services. |
| **Mobile Native Features?** | **Decision**: Features like "Scan QR", "Camera", "Biometric" will be simulated with UI interactions (e.g., clicking "Scan" shows a camera view simulation or file upload). |
| **Voice Recording?** | **Decision**: Web Audio API can be used, or simply simulated UI for "Recording" state if browser permissions are restricted in the preview environment. |
| **Image Editing Lib?** | **Decision**: Use `react-easy-crop` or similar lightweight canvas manipulation for the editor, or build a custom canvas implementation. |

## 4. Critical Decision Points (Interruption)

None at this stage. The requirements are detailed enough to proceed with a standard modern web stack.

## 5. Next Steps
- Create `DESIGN` document to detail the component hierarchy and data stores.
- Initialize the Vite project.
- Set up the component library (shadcn/ui).
