import { useMemo, useState } from "react"
import { useNavigate } from "react-router-dom"
import { ChevronLeft, Plus, Search, Users } from "lucide-react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import { ContactSelector } from "@/components/common/ContactSelector"
import { useChatStore } from "@/stores/useChatStore"
import type { Group, User } from "@/types"

export default function GroupList() {
  const navigate = useNavigate()
  const { sessions, createGroup } = useChatStore()
  const [query, setQuery] = useState("")
  const [isCreateOpen, setIsCreateOpen] = useState(false)

  const groups = useMemo(() => (
    sessions.filter((session) => session.type === "group" && session.name.toLowerCase().includes(query.toLowerCase()))
  ), [sessions, query])

  const handleCreateGroup = (selected: (User | Group)[]) => {
    if (selected.length === 0) return
    const name = window.prompt("请输入群名称", "新建群聊") || "新建群聊"
    createGroup(name, selected.map((item) => item.id))
    setIsCreateOpen(false)

    window.setTimeout(() => {
      const activeSessionId = useChatStore.getState().activeSessionId
      if (activeSessionId) navigate(`/chat/${activeSessionId}`)
    }, 100)
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
        <div className="flex flex-col">
          {groups.length > 0 ? (
            groups.map((group) => (
              <div key={group.id} onClick={() => navigate(`/chat/${group.id}`)} className="flex cursor-pointer items-center gap-3 border-b bg-background px-4 py-3 hover:bg-muted/50">
                <Avatar className="h-12 w-12 rounded-lg">
                  <AvatarImage src={group.avatar} />
                  <AvatarFallback>{group.name[0]}</AvatarFallback>
                </Avatar>
                <div className="min-w-0 flex-1">
                  <h3 className="truncate font-medium">{group.name}</h3>
                  <p className="truncate text-xs text-muted-foreground">{group.lastMessage?.content || "暂无消息"}</p>
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
