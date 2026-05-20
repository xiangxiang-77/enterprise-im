import type { Group, Message, Session, User } from "@/types"

const names = ["林一鸣", "陈思远", "周雨桐", "赵明轩", "沈佳宁", "韩子昂", "许若曦", "唐亦辰", "陆嘉禾", "顾清欢"]
const cities = ["北京", "上海", "深圳", "杭州", "成都", "南京", "武汉", "广州", "西安", "苏州"]
const signatures = ["今天也要高效沟通", "在线协作中", "专注工作，请稍候", "移动端在线", "消息请发群里"]
const groupNames = ["产品研发群", "运营协调群", "客户成功群", "交付项目群", "销售支持群", "技术委员会"]
const textSamples = [
  "这个需求我已经看过，下午给你反馈。",
  "会议纪要已同步到工作台。",
  "文件收到，我稍后确认版本。",
  "服务端接口已经联调通过。",
  "请大家下班前更新一下进度。",
]

const now = Date.now()

function pick<T>(items: T[], index: number): T {
  return items[index % items.length]
}

function avatar(seed: string) {
  return `https://api.dicebear.com/9.x/initials/svg?seed=${encodeURIComponent(seed)}`
}

export const currentUser: User = {
  id: "u_me",
  name: "当前用户",
  avatar: avatar("当前用户"),
  status: "online",
  email: "me@example.com",
  phone: "13800138000",
  region: "北京",
  signature: "企业即时通讯演示账号",
}

export const generateUsers = (count = 50): User[] => (
  Array.from({ length: count }).map((_, index) => {
    const name = `${pick(names, index)}${index + 1}`
    return {
      id: `u_demo_${index + 1}`,
      name,
      avatar: avatar(name),
      status: pick(["online", "offline", "busy", "away"] as User["status"][], index),
      email: `user${index + 1}@example.com`,
      phone: `1380000${String(index + 1).padStart(4, "0")}`,
      region: pick(cities, index),
      signature: pick(signatures, index),
    }
  })
)

export const generateGroups = (users: User[], count = 10): Group[] => (
  Array.from({ length: count }).map((_, index) => {
    const members = users.slice(index, index + 8).map((user) => user.id)
    const name = `${pick(groupNames, index)} ${index + 1}`
    return {
      id: `g_demo_${index + 1}`,
      name,
      avatar: avatar(name),
      members: [currentUser.id, ...members],
      ownerId: members[0] || currentUser.id,
      notice: "请使用本群同步项目进展、风险和待办。",
    }
  })
)

export const generateMessages = (sessionId: string, senderIds: string[], count = 20): Message[] => (
  Array.from({ length: count }).map((_, index): Message => {
    const type = pick(["text", "text", "text", "image", "voice", "file", "card"] as Message["type"][], index)
    const senderId = pick(senderIds.length ? senderIds : [currentUser.id], index)
    const id = `m_${sessionId}_${index + 1}`
    const contentByType: Record<string, string> = {
      text: pick(textSamples, index),
      image: "[图片]",
      voice: "[语音]",
      file: "[文件]",
      card: "[名片]",
    }

    return {
      id,
      sessionId,
      senderId,
      content: contentByType[type] || pick(textSamples, index),
      type,
      timestamp: now - (count - index) * 1000 * 60 * 8,
      status: "read",
      fileUrl: type === "image" ? avatar(`image-${id}`) : type === "voice" ? "https://actions.google.com/sounds/v1/alarms/beep_short.ogg" : undefined,
      fileName: type === "file" ? `项目资料-${index + 1}.pdf` : undefined,
      fileSize: type === "file" ? 1024 * (index + 2) : undefined,
      voiceDuration: type === "voice" ? 8 + index : undefined,
      cardInfo: type === "card" ? {
        userId: `card_${index}`,
        name: pick(names, index + 2),
        avatar: avatar(`card-${index}`),
        signature: "企业成员",
      } : undefined,
    }
  }).sort((a, b) => a.timestamp - b.timestamp)
)

export const generateSessions = (users: User[], groups: Group[]): Session[] => {
  const singleSessions: Session[] = users.slice(0, 10).map((user, index) => ({
    id: user.id,
    type: "single",
    targetId: user.id,
    name: user.name,
    avatar: user.avatar,
    unreadCount: index % 4,
    isPinned: index === 0,
    isMuted: index % 7 === 0,
    updatedAt: now - index * 1000 * 60 * 20,
    lastMessage: {
      id: `last_single_${index}`,
      sessionId: user.id,
      senderId: user.id,
      content: pick(textSamples, index),
      type: "text",
      timestamp: now - index * 1000 * 60 * 20,
      status: "read",
    },
  }))

  const groupSessions: Session[] = groups.map((group, index) => ({
    id: group.id,
    type: "group",
    targetId: group.id,
    name: group.name,
    avatar: group.avatar,
    unreadCount: (index * 3) % 11,
    isPinned: index === 1,
    isMuted: index % 4 === 0,
    updatedAt: now - (index + 1) * 1000 * 60 * 35,
    lastMessage: {
      id: `last_group_${index}`,
      sessionId: group.id,
      senderId: group.members[0],
      content: pick(textSamples, index + 1),
      type: "text",
      timestamp: now - (index + 1) * 1000 * 60 * 35,
      status: "read",
    },
  }))

  return [...singleSessions, ...groupSessions].sort((a, b) => b.updatedAt - a.updatedAt)
}

export const mockUsers = generateUsers(50)
export const mockGroups = generateGroups(mockUsers, 10)
export const mockSessions = generateSessions(mockUsers, mockGroups)
