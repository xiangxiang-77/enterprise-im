import { useMemo, useState } from "react"
import { useNavigate } from "react-router-dom"
import { Check, Pin, Plus, Scan, Search, Trash2, UserPlus, Users, VolumeX } from "lucide-react"
import { useChatStore } from "@/stores/useChatStore"
import { formatTime } from "@/utils/date"
import { cn } from "@/lib/utils"
import { ContactSelector } from "@/components/common/ContactSelector"
import type { Group, Message, Session, User } from "@/types"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuSeparator,
  ContextMenuTrigger,
} from "@/components/ui/context-menu"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"

function messagePreview(message?: Message) {
  if (!message) return "暂无消息"
  if (message.type === "image") return "[图片]"
  if (message.type === "voice") return "[语音]"
  if (message.type === "video") return "[视频]"
  if (message.type === "file") return "[文件]"
  if (message.type === "card") return "[名片]"
  if (message.type === "record") return "[聊天记录]"
  return message.content
}

export default function SessionList() {
  const navigate = useNavigate()
  const { sessions, deleteSession, createGroup, updateSession } = useChatStore()
  const [searchQuery, setSearchQuery] = useState("")
  const [isCreateGroupOpen, setIsCreateGroupOpen] = useState(false)

  const filteredSessions = useMemo(() => (
    sessions.filter((session) => session.name.toLowerCase().includes(searchQuery.toLowerCase()))
  ), [sessions, searchQuery])

  const pinnedSessions = filteredSessions.filter((session) => session.isPinned)
  const regularSessions = filteredSessions.filter((session) => !session.isPinned)

  const handleCreateGroup = (selected: (User | Group)[]) => {
    if (selected.length === 0) return
    const name = window.prompt("请输入群名称", "新建群聊") || "新建群聊"
    createGroup(name, selected.map((item) => item.id))
    setIsCreateGroupOpen(false)
    window.setTimeout(() => {
      const activeSessionId = useChatStore.getState().activeSessionId
      if (activeSessionId) navigate(`/chat/${activeSessionId}`)
    }, 100)
  }

  const SessionItem = ({ session }: { session: Session }) => (
    <ContextMenu>
      <ContextMenuTrigger>
        <div
          onClick={() => navigate(`/chat/${session.id}`)}
          className={cn(
            "flex cursor-pointer items-center gap-3 border-b p-3 transition-colors hover:bg-accent/50",
            session.isPinned && "bg-muted/30",
          )}
        >
          <div className="relative">
            <Avatar className="h-12 w-12">
              <AvatarImage src={session.avatar} alt={session.name} />
              <AvatarFallback>{session.name.slice(0, 2)}</AvatarFallback>
            </Avatar>
            <span className="absolute bottom-0 right-0 h-3 w-3 rounded-full border-2 border-background bg-green-500" />
          </div>

          <div className="min-w-0 flex flex-1 flex-col">
            <div className="flex justify-between gap-2">
              <h3 className="truncate font-medium">{session.name}</h3>
              <span className="shrink-0 text-xs text-muted-foreground">
                {session.lastMessage ? formatTime(session.lastMessage.timestamp) : ""}
              </span>
            </div>
            <div className="flex items-center justify-between gap-2">
              <p className="truncate text-sm text-muted-foreground">
                {session.draft ? <span className="text-red-500">[草稿] {session.draft}</span> : messagePreview(session.lastMessage)}
              </p>
              {session.isMuted && <VolumeX className="h-4 w-4 text-muted-foreground" />}
            </div>
          </div>

          {session.unreadCount > 0 && (
            <Badge
              variant={session.isMuted ? "secondary" : "destructive"}
              className="ml-auto flex h-5 min-w-5 items-center justify-center rounded-full px-1 text-[10px]"
            >
              {session.unreadCount > 99 ? "99+" : session.unreadCount}
            </Badge>
          )}
        </div>
      </ContextMenuTrigger>
      <ContextMenuContent>
        <ContextMenuItem onClick={() => updateSession(session.id, { isPinned: !session.isPinned })}>
          <Pin className="mr-2 h-4 w-4" />
          {session.isPinned ? "取消置顶" : "置顶会话"}
        </ContextMenuItem>
        <ContextMenuItem onClick={() => updateSession(session.id, { unreadCount: 0 })}>
          <Check className="mr-2 h-4 w-4" />
          标为已读
        </ContextMenuItem>
        <ContextMenuItem onClick={() => updateSession(session.id, { isMuted: !session.isMuted })}>
          <VolumeX className="mr-2 h-4 w-4" />
          {session.isMuted ? "开启通知" : "关闭通知"}
        </ContextMenuItem>
        <ContextMenuSeparator />
        <ContextMenuItem className="text-destructive focus:text-destructive" onClick={() => deleteSession(session.id)}>
          <Trash2 className="mr-2 h-4 w-4" />
          删除聊天
        </ContextMenuItem>
      </ContextMenuContent>
    </ContextMenu>
  )

  return (
    <div className="flex h-full flex-col bg-background">
      <header className="flex items-center justify-between border-b bg-background/95 px-4 py-3 backdrop-blur">
        <div className="relative mr-4 flex-1" onClick={() => navigate("/search")}>
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <div className="flex h-9 cursor-text items-center rounded-md bg-muted/50 pl-9 text-sm text-muted-foreground">
            搜索联系人、群聊、聊天记录
          </div>
        </div>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon" className="shrink-0">
              <Plus className="h-5 w-5" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-40">
            <DropdownMenuItem onClick={() => setIsCreateGroupOpen(true)}>
              <Users className="mr-2 h-4 w-4" />
              创建群聊
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => navigate("/contact/new")}>
              <UserPlus className="mr-2 h-4 w-4" />
              添加好友
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => window.alert("扫码功能尚未接入硬件")}>
              <Scan className="mr-2 h-4 w-4" />
              扫一扫
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </header>

      <div className="border-b px-4 py-2">
        <div className="relative">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            value={searchQuery}
            onChange={(event) => setSearchQuery(event.target.value)}
            placeholder="筛选当前会话"
            className="h-9 border-none bg-muted/50 pl-9"
          />
        </div>
      </div>

      <ScrollArea className="flex-1">
        <div className="flex flex-col">
          {pinnedSessions.map((session) => <SessionItem key={session.id} session={session} />)}
          {regularSessions.map((session) => <SessionItem key={session.id} session={session} />)}
          {filteredSessions.length === 0 && (
            <div className="flex flex-col items-center justify-center py-10 text-muted-foreground">
              <Search className="mb-2 h-8 w-8 opacity-20" />
              <p>无搜索结果</p>
            </div>
          )}
        </div>
      </ScrollArea>

      <ContactSelector
        open={isCreateGroupOpen}
        onOpenChange={setIsCreateGroupOpen}
        onSelect={handleCreateGroup}
        title="发起群聊"
      />
    </div>
  )
}
