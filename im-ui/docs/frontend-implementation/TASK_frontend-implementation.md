# TASK: Frontend Implementation Plan

## 1. Project Initialization & Setup
- [ ] **Task 1.1**: Initialize React + Vite + TypeScript project.
- [ ] **Task 1.2**: Configure Tailwind CSS & PostCSS.
- [ ] **Task 1.3**: Install and configure `shadcn/ui` (Button, Input, Avatar, Dialog, ScrollArea, Tabs, Badge, Separator, Sheet, Switch, Skeleton, Popover, Tooltip, DropdownMenu).
- [ ] **Task 1.4**: Set up project structure (folders: components, features, hooks, layouts, pages, store, types, utils).
- [ ] **Task 1.5**: Set up React Router DOM and basic routes (MainLayout, AuthLayout).
- [ ] **Task 1.6**: Implement Theme Provider (Dark/Light mode support).

## 2. Authentication & Onboarding
- [ ] **Task 2.1**: Implement Splash Screen with animation.
- [ ] **Task 2.2**: Implement Login Page (Phone input, OTP simulation, Password toggle, SSO button).
- [ ] **Task 2.3**: Implement User Agreement & Privacy Policy Modal.

## 3. Main Layout & Navigation
- [ ] **Task 3.1**: Create Bottom Navigation Bar (Mobile) / Sidebar (Desktop) with active states and badges.
- [ ] **Task 3.2**: Create Top Navigation Bar (Dynamic title, back button, actions).
- [ ] **Task 3.3**: Implement "Network Status" indicator (simulated).

## 4. Chat List Feature
- [ ] **Task 4.1**: Create `ChatList` component with mock data.
- [ ] **Task 4.2**: Implement `ChatItem` component (Avatar, Name, Last Message, Time, Unread Badge, Pinned status).
- [ ] **Task 4.3**: Implement Search Bar (Global search placeholder).
- [ ] **Task 4.4**: Implement "Create Chat" / "Add Friend" menu (Plus button).

## 5. Chat Detail Feature (Core)
- [ ] **Task 5.1**: Create `ChatDetail` page layout (Header, MessageList, InputArea).
- [ ] **Task 5.2**: Implement `MessageList` with scroll-to-bottom and date separators.
- [ ] **Task 5.3**: Implement `MessageBubble` base component (Left/Right alignment, status ticks).
- [ ] **Task 5.4**: Implement Text Message rendering (Link detection, Emoji).
- [ ] **Task 5.5**: Implement Image Message rendering (Grid layout, progressive loading simulation).
- [ ] **Task 5.6**: Implement Voice Message rendering (Waveform visualization mock).
- [ ] **Task 5.7**: Implement File Message rendering (Icon, Name, Size, Progress).

## 6. Input Area & Advanced Messaging
- [ ] **Task 6.1**: Create `MessageInput` component (Text area, Emoji picker toggle, Voice toggle, More actions).
- [ ] **Task 6.2**: Implement "More Actions" panel (Photo, Camera, File, Card).
- [ ] **Task 6.3**: Implement Voice Recording simulation (Long press to record).
- [ ] **Task 6.4**: Implement Reply/Quote functionality.

## 7. Contacts & Organization
- [ ] **Task 7.1**: Create `Contacts` page with Tabs (Organization / Contacts).
- [ ] **Task 7.2**: Implement `OrgTree` component (Expandable departments, breadcrumbs).
- [ ] **Task 7.3**: Implement `ContactList` component (Grouped by alphabet, sticky headers).
- [ ] **Task 7.4**: Implement Friend Request Manager (New Friends list, Accept/Reject).

## 8. Settings & Profile
- [ ] **Task 8.1**: Create `Profile` page (User info, actions).
- [ ] **Task 8.2**: Create `Settings` page (General, Notifications, Privacy).
- [ ] **Task 8.3**: Implement Chat Settings (Group members grid, Admin controls).

## 9. Media Viewer & File Manager
- [ ] **Task 9.1**: Implement `ImageViewer` (Zoom, Pan, Swipe).
- [ ] **Task 9.2**: Implement `ImageEditor` (Crop, Filter, Doodle - basic implementation).
- [ ] **Task 9.3**: Implement `FileManager` (List, Filter, Search).

## 10. Final Polish
- [ ] **Task 10.1**: Review and refine UI consistency (colors, spacing).
- [ ] **Task 10.2**: Ensure Dark Mode works across all components.
- [ ] **Task 10.3**: Verify all "mock" interactions (e.g., sending a message updates the list).
