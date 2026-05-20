import { create } from "zustand"
import type { Session, Message, User, Group } from "@/types"
import { mockSessions, mockUsers, mockGroups } from "@/utils/mock"

interface ChatState {
  sessions: Session[]
  activeSessionId: string | null
  messages: Record<string, Message[]> // Key: sessionId, Value: Messages
  favorites: Message[]
  users: Record<string, User> // Cache of users
  groups: Record<string, Group> // Cache of groups
  
  setActiveSession: (id: string | null) => void
  addMessage: (sessionId: string, message: Message) => void
  setMessages: (sessionId: string, messages: Message[]) => void
  createSession: (targetId: string, type: "single" | "group") => void
  createGroup: (name: string, memberIds: string[]) => void
  deleteSession: (id: string) => void
  clearMessages: (sessionId: string) => void
  updateSession: (id: string, updates: Partial<Session>) => void
  deleteMessage: (sessionId: string, messageId: string) => void
  recallMessage: (sessionId: string, messageId: string) => void
  updateGroup: (groupId: string, updates: Partial<Group>) => void
  addUser: (user: User) => void
  addMember: (groupId: string, userId: string) => void
  removeMember: (groupId: string, userId: string) => void
  setMemberAlias: (groupId: string, userId: string, alias: string) => void
  forwardMessages: (sourceSessionId: string, messageIds: string[], targetIds: string[], mode?: 'single' | 'combine') => void
  updateMessage: (sessionId: string, messageId: string, updates: Partial<Message>) => void
  addFavorite: (message: Message) => void
  removeFavorite: (messageId: string) => void
  markMessageAsRead: (sessionId: string, messageId: string, userId: string) => void
  toggleLikeMessage: (sessionId: string, messageId: string, userId: string) => void
  editMessage: (sessionId: string, messageId: string, newContent: string) => void
  typingStatus: Record<string, { userId: string, username: string, timestamp: number }[]> // sessionId -> typing users
  setTyping: (sessionId: string, userId: string, username: string, isTyping: boolean) => void
  setDraft: (sessionId: string, draft: string) => void
  resendMessage: (sessionId: string, messageId: string) => void
  transferGroupOwner: (groupId: string, newOwnerId: string) => void
}

export const useChatStore = create<ChatState>((set, get) => ({
  sessions: mockSessions,
  activeSessionId: null,
  messages: {}, // Will load on demand or init
  favorites: [],
  typingStatus: {},
  users: mockUsers.reduce((acc, user) => ({ ...acc, [user.id]: user }), {}),
  groups: mockGroups.reduce((acc, group) => ({ ...acc, [group.id]: group }), {}),

  setDraft: (sessionId, draft) =>
    set((state) => ({
      sessions: state.sessions.map((s) =>
        s.id === sessionId ? { ...s, draft } : s
      ),
    })),

  resendMessage: (sessionId, messageId) => {
    set((state) => {
        const sessionMessages = state.messages[sessionId] || []
        const msgIndex = sessionMessages.findIndex(m => m.id === messageId)
        if (msgIndex === -1) return state
        
        const newMessages = [...sessionMessages]
        newMessages[msgIndex] = { ...newMessages[msgIndex], status: 'sending', timestamp: Date.now() }
        
        return {
            messages: {
                ...state.messages,
                [sessionId]: newMessages
            },
            sessions: state.sessions.map(s => 
                s.id === sessionId 
                  ? { ...s, lastMessage: newMessages[msgIndex], updatedAt: newMessages[msgIndex].timestamp }
                  : s
            ).sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
        }
    })

    // Simulate sending success
    setTimeout(() => {
        set((state) => {
            const sessionMessages = state.messages[sessionId] || []
            const msgIndex = sessionMessages.findIndex(m => m.id === messageId)
            if (msgIndex === -1) return state
            
            const newMessages = [...sessionMessages]
            newMessages[msgIndex] = { ...newMessages[msgIndex], status: 'sent' }
            
            return {
                messages: {
                    ...state.messages,
                    [sessionId]: newMessages
                }
            }
        })
    }, 1000)
  },

  transferGroupOwner: (groupId, newOwnerId) => {
    set((state) => {
        const group = state.groups[groupId]
        if (!group) return state
        
        const newOwnerName = state.users[newOwnerId]?.name || "未知用户"
        
        // System message
        const systemMsg: Message = {
            id: Date.now().toString(),
            sessionId: groupId,
            senderId: "system",
            content: `群主已转让给 ${newOwnerName}`,
            type: "system",
            timestamp: Date.now(),
            status: "read"
        }

        return {
            groups: {
                ...state.groups,
                [groupId]: { ...group, ownerId: newOwnerId }
            },
            messages: {
                ...state.messages,
                [groupId]: [...(state.messages[groupId] || []), systemMsg]
            },
            sessions: state.sessions.map(s => 
                s.id === groupId 
                  ? { ...s, lastMessage: systemMsg, updatedAt: systemMsg.timestamp }
                  : s
            ).sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
        }
    })
  },

  setActiveSession: (id) => set((state) => ({  
      activeSessionId: id,
      sessions: state.sessions.map(s => 
          s.id === id ? { ...s, unreadCount: 0 } : s
      )
  })),
  
  setTyping: (sessionId, userId, username, isTyping) => set(state => {
      const currentTyping = state.typingStatus[sessionId] || []
      let newTyping = [...currentTyping]
      
      if (isTyping) {
          const existing = newTyping.find(t => t.userId === userId)
          if (existing) {
              existing.timestamp = Date.now()
          } else {
              newTyping.push({ userId, username, timestamp: Date.now() })
          }
      } else {
          newTyping = newTyping.filter(t => t.userId !== userId)
      }
      
      return {
          typingStatus: {
              ...state.typingStatus,
              [sessionId]: newTyping
          }
      }
  }),

  updateGroup: (groupId, updates) =>
    set((state) => {
      const group = state.groups[groupId]
      if (!group) return state
      
      let newMessages = { ...state.messages }
      const sessionMessages = [...(state.messages[groupId] || [])]
      
      let hasUpdates = false
      if (updates.name && updates.name !== group.name) {
          sessionMessages.push({
              id: `sys-${Date.now()}`,
              sessionId: groupId,
              senderId: 'system',
              content: `群名称已修改为 "${updates.name}"`,
              type: 'system',
              timestamp: Date.now(),
              status: 'sent'
          })
          hasUpdates = true
      }
      
      if (updates.notice && updates.notice !== group.notice) {
          sessionMessages.push({
              id: `sys-${Date.now()}-notice`,
              sessionId: groupId,
              senderId: 'system',
              content: `群公告已更新: ${updates.notice}`,
              type: 'system',
              timestamp: Date.now(),
              status: 'sent'
          })
          hasUpdates = true
      }
      
      if (hasUpdates) {
          newMessages[groupId] = sessionMessages
      }

      return {
        groups: {
          ...state.groups,
          [groupId]: { ...state.groups[groupId], ...updates }
        },
        messages: newMessages,
        // Also update session name/avatar if changed
        sessions: state.sessions.map(s => 
          s.targetId === groupId 
            ? { ...s, name: updates.name || s.name, avatar: updates.avatar || s.avatar }
            : s
        )
      }
    }),

  addUser: (user) => 
    set((state) => ({
      users: { ...state.users, [user.id]: user }
    })),

  addMember: (groupId, userId) =>
    set((state) => {
       const group = state.groups[groupId]
       if (!group || group.members.includes(userId)) return state
       
       const newMembers = [...group.members, userId]
       const user = state.users[userId]
       
       const sysMsg: Message = {
          id: `sys-${Date.now()}`,
          sessionId: groupId,
          senderId: 'system',
          content: `${user?.name || userId} 加入了群聊`,
          type: 'system',
          timestamp: Date.now(),
          status: 'sent'
       }
       
       return {
         groups: {
           ...state.groups,
           [groupId]: { ...group, members: newMembers }
         },
         messages: {
             ...state.messages,
             [groupId]: [...(state.messages[groupId] || []), sysMsg]
         }
       }
    }),

  updateMessage: (sessionId, messageId, updates) =>
    set((state) => ({
      messages: {
        ...state.messages,
        [sessionId]: (state.messages[sessionId] || []).map((m) =>
          m.id === messageId ? { ...m, ...updates } : m
        ),
      },
    })),

  removeMember: (groupId, userId) =>
    set((state) => {
       const group = state.groups[groupId]
       if (!group) return state
       
       const newMembers = group.members.filter(id => id !== userId)
       const user = state.users[userId]
       
       const sysMsg: Message = {
          id: `sys-${Date.now()}`,
          sessionId: groupId,
          senderId: 'system',
          content: `${user?.name || userId} 离开了群聊`,
          type: 'system',
          timestamp: Date.now(),
          status: 'sent'
       }
       
       return {
         groups: {
           ...state.groups,
           [groupId]: { ...group, members: newMembers }
         },
         messages: {
             ...state.messages,
             [groupId]: [...(state.messages[groupId] || []), sysMsg]
         }
       }
    }),

  setMemberAlias: (groupId, userId, alias) =>
    set((state) => {
      const group = state.groups[groupId]
      if (!group) return state

      const newAliases = { ...group.memberAliases, [userId]: alias }
      return {
        groups: {
          ...state.groups,
          [groupId]: { ...group, memberAliases: newAliases }
        }
      }
    }),

  forwardMessages: (sourceSessionId, messageIds, targetIds, mode = 'single') =>
    set((state) => {
      const sourceMessages = state.messages[sourceSessionId] || []
      const messagesToForward = sourceMessages.filter(m => messageIds.includes(m.id))
      if (messagesToForward.length === 0) return state

      let newSessions = [...state.sessions]
      let newMessages = { ...state.messages }

      targetIds.forEach(targetId => {
        // 检查会话是否已存在
        let session = newSessions.find(s => s.targetId === targetId)
        
        // 不存在时创建临时会话
        if (!session) {
          let name = "新会话"
          let avatar = ""
          let type: 'single' | 'group' = 'single'
          
          if (state.users[targetId]) {
            const user = state.users[targetId]
            name = user.name
            avatar = user.avatar
            type = 'single'
          } else if (state.groups[targetId]) {
            const group = state.groups[targetId]
            name = group.name
            avatar = group.avatar
            type = 'group'
          }
          
          session = {
            id: targetId,
            targetId,
            type,
            name,
            avatar,
            unreadCount: 0,
            updatedAt: Date.now(),
            isPinned: false,
            isMuted: false,
            isStrongReminder: false
          }
          newSessions.push(session)
        }

        // 3. Add Messages
        const currentMessages = newMessages[targetId] || []
        let forwardedMessages: Message[] = []

        if (mode === 'combine') {
            const recordContent = messagesToForward.map(m => {
                const senderName = state.users[m.senderId]?.name || "未知用户"
                let content = m.content
                if (m.type === 'image') content = '[图片]'
                if (m.type === 'voice') content = '[语音]'
                if (m.type === 'file') content = '[文件]'
                if (m.type === 'card') content = '[名片]'
                if (m.type === 'video') content = '[视频]'
                return `${senderName}: ${content}`
            })
            
            const sourceName = state.users[sourceSessionId]?.name || state.groups[sourceSessionId]?.name || "会话"
            const title = `${sourceName}的聊天记录`
            
            const recordMsg: Message = {
                id: `record-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
                sessionId: targetId,
                senderId: '1', // Current user
                content: "[聊天记录]",
                type: 'record',
                timestamp: Date.now(),
                status: 'sent',
                recordInfo: {
                    title: title,
                    content: recordContent.slice(0, 4) // Preview first 4 lines
                }
            }
            forwardedMessages = [recordMsg]
        } else {
            forwardedMessages = messagesToForward.map(originalMsg => ({
              ...originalMsg,
              id: Date.now().toString() + Math.random().toString(36).substr(2, 9),
              senderId: '1', // Current user
              timestamp: Date.now(),
              status: 'sent' as const,
            }))
        }
        
        newMessages[targetId] = [...currentMessages, ...forwardedMessages]

        // 4. Update Session Last Message
        const lastMsg = forwardedMessages[forwardedMessages.length - 1]
        newSessions = newSessions.map(s => 
          s.targetId === targetId 
            ? { ...s, lastMessage: lastMsg, updatedAt: lastMsg.timestamp }
            : s
        )
      })

      return {
        sessions: newSessions.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()),
        messages: newMessages
      }
    }),

  addMessage: (sessionId, message) => 
    set((state) => ({
      messages: {
        ...state.messages,
        [sessionId]: [...(state.messages[sessionId] || []), message],
      },
      // Also update last message in session
      sessions: state.sessions.map((s) => {
        if (s.id === sessionId) {
            const isUnread = state.activeSessionId !== sessionId
            return { 
                ...s, 
                lastMessage: message, 
                unreadCount: isUnread ? (s.unreadCount || 0) + 1 : 0, 
                updatedAt: message.timestamp 
            }
        }
        return s
      }).sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
    })),

  deleteMessage: (sessionId, messageId) => 
    set(state => {
      const messages = state.messages[sessionId] || []
      const newMessages = messages.filter(m => m.id !== messageId)
      
      // Update session last message if needed
      const updatedSessions = state.sessions.map(s => {
          if (s.id === sessionId && s.lastMessage?.id === messageId) {
              return { ...s, lastMessage: newMessages[newMessages.length - 1], updatedAt: newMessages[newMessages.length - 1]?.timestamp || s.updatedAt }
          }
          return s
      })

      return {
          messages: {
              ...state.messages,
              [sessionId]: newMessages
          },
          sessions: updatedSessions
      }
    }),
  
  recallMessage: (sessionId, messageId) => {
    const { messages } = get()
    const sessionMessages = messages[sessionId] || []
    const message = sessionMessages.find(m => m.id === messageId)
    
    if (!message) return

    // Check 3 minutes limit (180000 ms)
    if (Date.now() - message.timestamp > 180000) {
        // Ideally return error, but here just log
        console.warn("Cannot recall message older than 3 minutes")
        return
    }

    set((state) => ({
      messages: {
        ...state.messages,
        [sessionId]: (state.messages[sessionId] || []).map((m) =>
          m.id === messageId ? { ...m, isRecall: true, type: "system", content: "你撤回了一条消息" } : m
        ),
      },
    }))
  },

  editMessage: (sessionId, messageId, newContent) => 
    set(state => ({
        messages: {
            ...state.messages,
            [sessionId]: (state.messages[sessionId] || []).map(m => 
                m.id === messageId ? { ...m, content: newContent, isEdited: true } : m
            )
        }
    })),

  setMessages: (sessionId, messages) => 
    set((state) => ({
      messages: {
        ...state.messages,
        [sessionId]: messages,
      },
    })),
  
  clearMessages: (sessionId) =>
    set((state) => ({
      messages: {
        ...state.messages,
        [sessionId]: [],
      },
      sessions: state.sessions.map((s) =>
        s.id === sessionId ? { ...s, lastMessage: undefined } : s
      ),
    })),

  createSession: (targetId, type) => {
    const { sessions, users, groups } = get()
    // 检查会话是否已存在
    const existing = sessions.find((s) => s.targetId === targetId)
    if (existing) {
      set({ activeSessionId: existing.id })
      return
    }
    
    // 创建临时会话
    let name = "新会话"
    let avatar = ""
    
    if (type === 'single') {
        const user = users[targetId]
        if (user) {
            name = user.name
            avatar = user.avatar
        }
    } else {
        const group = groups[targetId]
        if (group) {
            name = group.name
            avatar = group.avatar
        }
    }

    const newSession: Session = {
        id: targetId, // Simplification: Use targetId as sessionId
        targetId,
        type,
        name,
        avatar,
        unreadCount: 0,
        updatedAt: Date.now(),
        isPinned: false,
        isMuted: false,
        isStrongReminder: false
    }

    set((state) => ({
        sessions: [newSession, ...state.sessions],
        activeSessionId: newSession.id
    }))
  },

  createGroup: (name, memberIds) => {
    const id = `group-${Date.now()}`
    const newGroup: Group = {
        id,
        name,
        avatar: `https://api.dicebear.com/7.x/initials/svg?seed=${name}`,
        members: memberIds,
        ownerId: '1', // Current user
        createdAt: new Date().toISOString(),
        notice: "暂无公告"
    }
    
    set(state => ({
        groups: { ...state.groups, [id]: newGroup }
    }))
    
    get().createSession(id, 'group')
  },

  deleteSession: (id) => 
    set((state) => ({
      sessions: state.sessions.filter((s) => s.id !== id),
      activeSessionId: state.activeSessionId === id ? null : state.activeSessionId,
    })),

  updateSession: (id: string, updates: Partial<Session>) =>
    set((state) => ({
      sessions: state.sessions.map((s) => (s.id === id ? { ...s, ...updates } : s)),
    })),

  addFavorite: (message) => set((state) => ({ favorites: [...state.favorites, message] })),
  removeFavorite: (messageId) => set((state) => ({ favorites: state.favorites.filter(m => m.id !== messageId) })),
  
  markMessageAsRead: (sessionId, messageId, userId) =>
    set((state) => ({
      messages: {
        ...state.messages,
        [sessionId]: (state.messages[sessionId] || []).map((m) => {
          if (m.id !== messageId) return m
          
          const readBy = m.readBy || []
          if (readBy.includes(userId)) return m
          
          return { ...m, readBy: [...readBy, userId] }
        }),
      },
    })),

  toggleLikeMessage: (sessionId, messageId, userId) =>
    set((state) => ({
      messages: {
        ...state.messages,
        [sessionId]: (state.messages[sessionId] || []).map((m) => {
          if (m.id !== messageId) return m
          
          const likes = m.likes || []
          if (likes.includes(userId)) {
              return { ...m, likes: likes.filter(id => id !== userId) }
          }
          
          return { ...m, likes: [...likes, userId] }
        }),
      },
    })),
}))
