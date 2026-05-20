export interface User {
  id: string
  name: string
  avatar: string
  status: "online" | "offline" | "busy" | "away"
  email?: string
  phone?: string
  region?: string
  signature?: string
  bio?: string
  isOfficial?: boolean // Official account
  isRobot?: boolean // Robot account
}

export interface Group {
  id: string
  name: string
  avatar: string
  members: string[] // User IDs
  ownerId: string
  createdAt?: string
  notice?: string
  memberAliases?: Record<string, string> // userId -> alias
}

export interface Message {
  id: string
  serverId?: string
  sessionId: string
  senderId: string
  content: string
  type: "text" | "image" | "voice" | "file" | "video" | "card" | "system" | "record"
  timestamp: number
  status: "sending" | "sent" | "read" | "failed"
  isRead?: boolean
  fileUrl?: string
  fileName?: string
  fileSize?: number
  videoThumbnail?: string
  voiceDuration?: number // In seconds
  cardInfo?: {
    userId: string
    name: string
    avatar: string
    signature?: string
  }
  recordInfo?: {
    title: string
    content: string[]
  }
  isReadAfterBurn?: boolean
  isRecall?: boolean
  quoteId?: string // Referenced message ID
  readBy?: string[] // List of user IDs who read this message
  likes?: string[] // List of user IDs who liked this message
  isEdited?: boolean
}

export interface Session {
  id: string
  type: "single" | "group"
  targetId: string // User ID or Group ID
  name: string
  avatar: string
  lastMessage?: Message
  unreadCount: number
  isPinned: boolean
  isMuted: boolean
  isReadAfterBurn?: boolean
  isStrongReminder?: boolean
  isSavedToContacts?: boolean // Save group to contacts
  isBlocked?: boolean // Block user
  isScreenshotNotificationEnabled?: boolean // Screenshot notification
  draft?: string // Message draft
  updatedAt: number
}

export interface ContactSection {
  title: string
  data: User[]
}
