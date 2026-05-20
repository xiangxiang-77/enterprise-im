# DESIGN: Enterprise IM Frontend Architecture

## 1. System Architecture

### 1.1 High-Level Architecture
The application follows a standard Single Page Application (SPA) architecture using React.

```mermaid
graph TD
    App[App Entry] --> Router[React Router]
    Router --> Auth[Auth Layout]
    Router --> Main[Main Layout]
    
    Auth --> Login[Login Page]
    Auth --> Splash[Splash Screen]
    
    Main --> Chat[Chat Module]
    Main --> Contacts[Contacts Module]
    Main --> Discover[Discover Module]
    Main --> Profile[Profile Module]
    
    Chat --> ChatList[Chat List]
    Chat --> ChatRoom[Chat Room (Single/Group)]
    ChatRoom --> MessageList[Message List]
    ChatRoom --> InputArea[Input Area]
    ChatRoom --> ChatSettings[Chat Settings]
    
    Contacts --> OrgChart[Organization Chart]
    Contacts --> ContactList[Contact List]
    Contacts --> FriendRequest[Friend Requests]
    
    Profile --> UserProfile[User Profile]
    Profile --> MyCard[My Card]
    
    Shared --> Components[UI Components (shadcn/ui)]
    Shared --> Stores[Zustand Stores]
    Shared --> Hooks[Custom Hooks]
    Shared --> Utils[Utilities]
```

### 1.2 Directory Structure
```
src/
├── assets/          # Static assets (images, icons)
├── components/      # Shared UI components
│   ├── ui/          # Base UI components (shadcn/ui)
│   └── common/      # App-specific shared components (Avatar, Badge, etc.)
├── features/        # Feature-based modules
│   ├── auth/        # Authentication (Login, Register)
│   ├── chat/        # Chat functionality (List, Room, Message types)
│   ├── contacts/    # Contacts & Organization
│   ├── settings/    # App settings
│   └── file/        # File management
├── hooks/           # Custom hooks (useTheme, useAudio, etc.)
├── layouts/         # Page layouts (MainLayout, AuthLayout)
├── lib/             # Utilities and helpers (date formatting, mock data)
├── pages/           # Page components mapped to routes
├── store/           # Global state management (Zustand)
├── styles/          # Global styles (Tailwind)
└── types/           # TypeScript definitions
```

## 2. Core Modules & Data Models

### 2.1 Chat Module
**Key Components:**
- `ConversationList`: Displays list of chats with unread counts, status, timestamps.
- `MessageBubble`: Factory component for rendering different message types (Text, Image, Voice, File).
- `ChatInput`: Complex input with emoji picker, voice recorder, file attachment.
- `ChatSettings`: Sidebar/Modal for managing group members, notifications.

**Data Model (Message):**
```typescript
interface Message {
  id: string;
  conversationId: string;
  senderId: string;
  type: 'text' | 'image' | 'voice' | 'file' | 'system' | 'card';
  content: string | FileMetadata | VoiceMetadata;
  status: 'sending' | 'sent' | 'read' | 'failed';
  timestamp: number;
  readBy: string[]; // Array of user IDs
  reactions: Reaction[];
  isRevoked?: boolean;
}
```

### 2.2 Contacts Module
**Key Components:**
- `ContactList`: Alphabetical list with sticky headers.
- `OrgTree`: Recursive component for organization structure.
- `RequestManager`: For handling friend requests.

**Data Model (User):**
```typescript
interface User {
  id: string;
  name: string;
  avatar: string;
  status: 'online' | 'offline' | 'busy';
  departmentId?: string;
  position?: string;
}
```

### 2.3 Settings & Theme
- **Theme Store**: Manages dark/light mode preference.
- **Language Store**: i18n support (mocked or using i18next).

## 3. Interaction Design
- **Transitions**: Use `framer-motion` for smooth page transitions and micro-interactions (e.g., message sending, list filtering).
- **Gestures**: Swipe-to-delete, pull-to-refresh (simulated for web).
- **Responsive**: Mobile-first layout, adapting to desktop with a sidebar navigation.

## 4. Mocking Strategy
- **Faker.js**: Generate realistic user names, messages, and file metadata.
- **Local Storage**: Persist "sent" messages and settings to survive page reloads during the session.
- **setTimeout**: Simulate network delays for "sending" status and file uploads.

## 5. Implementation Plan (Phased)
1.  **Setup**: Project init, Tailwind, shadcn/ui.
2.  **Core UI**: Layouts, Navigation, Basic Components.
3.  **Feature 1: Chat List & Detail**: Basic messaging.
4.  **Feature 2: Contacts & Org**: Directory structure.
5.  **Feature 3: Advanced Messaging**: Voice, Image, Files.
6.  **Feature 4: Settings & Profile**: Theming, User management.
7.  **Refinement**: Animations, Polish, Edge cases.
