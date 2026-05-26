import { useEffect, useMemo, useState } from "react"
import { useNavigate } from "react-router-dom"
import { ChevronLeft, Plus, Search, Users } from "lucide-react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import { ContactSelector } from "@/components/common/ContactSelector"
import { useAuthStore } from "@/stores/useAuthStore"
import { useChatStore } from "@/stores/useChatStore"
import {
  createGroupApi,
  fetchDirectoryUsersApi,
  fetchFriendsApi,
  fetchGroupsApi,
  type DirectoryUser,
  type GroupRecord,
} from "@/services/api"
import type { Group, Session, User } from "@/types"

function toUser(item: DirectoryUser): User {
  return {
    id: item.id,
    name: item.name,
    avatar: item.avatarUrl || "",
    status: item.status || "offline",
    phone: item.phone,
    email: item.email,
    signature: item.signature,
  }
}

function toGroup(item: GroupRecord): Group {
  return {
    id: item.id,
    name: item.name,
    avatar: item.avatarUrl || "",
    members: [],
    ownerId: item.ownerId,
    createdAt: item.createdAt,
    notice: item.notice,
  }
}

function toSession(item: GroupRecord): Session {
  return {
    id: item.id,
    type: "group",
    targetId: item.id,
    name: item.name,
    avatar: item.avatarUrl || "",
    unreadCount: 0,
    isPinned: false,
    isMuted: false,
    updatedAt: item.createdAt ? Date.parse(item.createdAt) : Date.now(),
  }
}

export default function GroupList() {
  const navigate = useNavigate()
  const { user, token } = useAuthStore()
  const { addUser, addGroup, addSession } = useChatStore()
  const [query, setQuery] = useState("")
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [groups, setGroups] = useState<GroupRecord[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  const loadData = async () => {
    if (!user?.id || !token) return
    setLoading(true)
    setError("")
    try {
      const [groupRows, friendRows, directoryRows] = await Promise.all([
        fetchGroupsApi(user.id, token),
        fetchFriendsApi(user.id, token),
        fetchDirectoryUsersApi(token, { limit: 200 }),
      ])
      friendRows.concat(directoryRows).map(toUser).forEach(addUser)
      groupRows.forEach((item) => {
        addGroup(toGroup(item))
        addSession(toSession(item))
      })
      setGroups(groupRows)
    } catch (err) {
      setError(err instanceof Error ? err.message : "load failed")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadData()
  }, [user?.id, token])

  const filteredGroups = useMemo(() => (
    groups.filter((group) => group.name.toLowerCase().includes(query.toLowerCase()))
  ), [groups, query])

  const handleCreateGroup = async (selected: (User | Group)[]) => {
    if (selected.length === 0 || !token) return
    const name = window.prompt("请输入群名称", "新建群聊") || "新建群聊"
    try {
      const created = await createGroupApi({
        name,
        memberIds: selected.map((item) => item.id),
        notice: "暂无公告",
      }, token)
      addGroup(toGroup(created))
      addSession(toSession(created))
      setGroups((items) => [created, ...items.filter((item) => item.id !== created.id)])
      setIsCreateOpen(false)
      navigate(`/chat/${created.id}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : "create failed")
    }
  }

  return (
    <div className="flex h-full flex-col bg-background">
      <header className="sticky top-0 z-10 flex items-center justify-between border-b bg-background/95 px-4 py-3 backdrop-blur">
        <div className="flex flex-1 items-center gap-2">
          <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="-ml-2">
            <ChevronLeft className="h-6 w-6" />
          </Button>
          <div className="relative mr-2 flex-1">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索群聊" className="h-9 border-none bg-muted/50 pl-9" />
          </div>
        </div>
        <Button variant="ghost" size="icon" onClick={() => setIsCreateOpen(true)}>
          <Plus className="h-5 w-5" />
        </Button>
      </header>

      <ScrollArea className="flex-1">
        {loading && <div className="px-4 py-3 text-sm text-muted-foreground">加载中...</div>}
        {error && <div className="px-4 py-3 text-sm text-red-500">操作失败：{error}</div>}
        <div className="flex flex-col">
          {filteredGroups.length > 0 ? (
            filteredGroups.map((group) => (
              <div key={group.id} onClick={() => navigate(`/chat/${group.id}`)} className="flex cursor-pointer items-center gap-3 border-b bg-background px-4 py-3 hover:bg-muted/50">
                <Avatar className="h-12 w-12 rounded-lg">
                  <AvatarImage src={group.avatarUrl || ""} />
                  <AvatarFallback>{group.name[0]}</AvatarFallback>
                </Avatar>
                <div className="min-w-0 flex-1">
                  <h3 className="truncate font-medium">{group.name}</h3>
                  <p className="truncate text-xs text-muted-foreground">{group.memberCount} 人 / {group.notice || "暂无公告"}</p>
                </div>
              </div>
            ))
          ) : (
            <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
              <Users className="mb-4 h-12 w-12 opacity-20" />
              <p>暂无群聊</p>
            </div>
          )}
        </div>
      </ScrollArea>

      <ContactSelector open={isCreateOpen} onOpenChange={setIsCreateOpen} onSelect={handleCreateGroup} title="发起群聊" />
    </div>
  )
}
