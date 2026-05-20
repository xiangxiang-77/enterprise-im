import { useMemo, useState } from "react"
import { useNavigate } from "react-router-dom"
import { ChevronDown, ChevronRight, Folder, Search, UserPlus, Users } from "lucide-react"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { useAuthStore } from "@/stores/useAuthStore"
import { useChatStore } from "@/stores/useChatStore"
import { cn } from "@/lib/utils"
import type { User } from "@/types"

type OrgNode = {
  name: string
  count: number
  children?: OrgNode[]
}

const orgData: OrgNode = {
  name: "企业即时通讯演示公司",
  count: 128,
  children: [
    { name: "产品部", count: 28, children: [{ name: "设计组", count: 12 }, { name: "产品组", count: 16 }] },
    { name: "研发部", count: 64, children: [{ name: "前端组", count: 20 }, { name: "后端组", count: 30 }, { name: "测试组", count: 14 }] },
    { name: "市场部", count: 15 },
  ],
}

export default function ContactList() {
  const navigate = useNavigate()
  const { users } = useChatStore()
  const { user: currentUser } = useAuthStore()
  const [searchQuery, setSearchQuery] = useState("")
  const [activeTab, setActiveTab] = useState("org")

  const allUsers = useMemo(() => (
    Object.values(users).filter((user) => user.id !== currentUser?.id)
  ), [users, currentUser?.id])

  const filteredUsers = allUsers.filter((user) => (
    user.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    user.phone?.includes(searchQuery)
  ))

  const groupedUsers = allUsers.reduce((acc, user) => {
    const key = /^[A-Za-z]/.test(user.name[0] || "") ? user.name[0].toUpperCase() : "#"
    acc[key] = [...(acc[key] || []), user]
    return acc
  }, {} as Record<string, User[]>)

  const initials = Object.keys(groupedUsers).sort()

  const OrgItem = ({ item, level = 0 }: { item: OrgNode; level?: number }) => {
    const [isOpen, setIsOpen] = useState(level === 0)
    const hasChildren = Boolean(item.children?.length)

    return (
      <div>
        <div
          className={cn("flex cursor-pointer select-none items-center gap-2 px-4 py-3 hover:bg-muted/50", level > 0 && "pl-8")}
          onClick={() => hasChildren && setIsOpen(!isOpen)}
        >
          {hasChildren ? (
            isOpen ? <ChevronDown className="h-4 w-4 text-muted-foreground" /> : <ChevronRight className="h-4 w-4 text-muted-foreground" />
          ) : (
            <div className="w-4" />
          )}
          <Folder className="h-5 w-5 fill-blue-100 text-blue-500" />
          <span className="flex-1 text-sm font-medium">{item.name}</span>
          <span className="text-xs text-muted-foreground">({item.count})</span>
        </div>
        {isOpen && hasChildren && item.children?.map((child) => <OrgItem key={child.name} item={child} level={level + 1} />)}
      </div>
    )
  }

  const UserListItem = ({ user }: { user: User }) => (
    <div
      className="flex cursor-pointer items-center gap-3 px-4 py-3 hover:bg-muted/50"
      onClick={() => navigate(`/contact/profile/${user.id}`)}
    >
      <div className="relative">
        <Avatar>
          <AvatarImage src={user.avatar} />
          <AvatarFallback>{user.name[0]}</AvatarFallback>
        </Avatar>
        {user.status === "online" && <span className="absolute bottom-0 right-0 h-3 w-3 rounded-full border-2 border-background bg-green-500" />}
        {user.status === "busy" && <span className="absolute bottom-0 right-0 h-3 w-3 rounded-full border-2 border-background bg-orange-500" />}
      </div>
      <div className="min-w-0 flex-1">
        <h3 className="truncate font-medium">{user.name}</h3>
        <p className="truncate text-sm text-muted-foreground">{user.signature || "暂无签名"}</p>
      </div>
    </div>
  )

  return (
    <div className="flex h-full flex-col bg-background">
      <header className="sticky top-0 z-10 flex flex-col border-b bg-background/95 backdrop-blur">
        <div className="flex items-center justify-between px-4 py-3">
          <div className="relative mr-4 flex-1">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder={activeTab === "org" ? "搜索部门、成员" : "搜索联系人"}
              className="border-none bg-muted/50 pl-9 focus-visible:ring-1"
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
            />
          </div>
          <Button variant="ghost" size="icon" onClick={() => navigate("/contact/new")}>
            <UserPlus className="h-5 w-5" />
          </Button>
        </div>

        <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
          <TabsList className="grid w-full grid-cols-2 rounded-none border-t bg-transparent p-0">
            <TabsTrigger value="org" className="rounded-none border-b-2 border-transparent data-[state=active]:border-primary data-[state=active]:bg-transparent">
              组织架构
            </TabsTrigger>
            <TabsTrigger value="contacts" className="rounded-none border-b-2 border-transparent data-[state=active]:border-primary data-[state=active]:bg-transparent">
              联系人
            </TabsTrigger>
          </TabsList>
        </Tabs>
      </header>

      <ScrollArea className="flex-1">
        {activeTab === "org" ? (
          <div className="pb-4">
            <div className="flex items-center justify-between border-b bg-muted/10 px-4 py-3">
              <span className="text-sm font-semibold">{orgData.name}</span>
              <span className="text-xs text-muted-foreground">{orgData.count} 人</span>
            </div>
            {orgData.children?.map((dept) => <OrgItem key={dept.name} item={dept} />)}
            <div className="mt-4 bg-muted/20 px-4 py-2 text-xs font-semibold text-muted-foreground">常用联系人</div>
            {allUsers.slice(0, 5).map((user) => <UserListItem key={user.id} user={user} />)}
          </div>
        ) : (
          <>
            {!searchQuery && (
              <div className="flex flex-col border-b">
                <div className="flex cursor-pointer items-center gap-3 px-4 py-3 hover:bg-muted/50" onClick={() => navigate("/contact/new")}>
                  <div className="flex h-10 w-10 items-center justify-center rounded bg-orange-500 text-white">
                    <UserPlus className="h-6 w-6" />
                  </div>
                  <span className="flex-1 font-medium">新的朋友</span>
                  <ChevronRight className="h-4 w-4 text-muted-foreground opacity-50" />
                </div>
                <div className="flex cursor-pointer items-center gap-3 px-4 py-3 hover:bg-muted/50" onClick={() => navigate("/contact/groups")}>
                  <div className="flex h-10 w-10 items-center justify-center rounded bg-green-500 text-white">
                    <Users className="h-6 w-6" />
                  </div>
                  <span className="flex-1 font-medium">群聊</span>
                  <ChevronRight className="h-4 w-4 text-muted-foreground opacity-50" />
                </div>
              </div>
            )}

            {searchQuery ? (
              <div className="flex flex-col">
                {filteredUsers.length === 0 && <div className="p-8 text-center text-muted-foreground">无结果</div>}
                {filteredUsers.map((user) => <UserListItem key={user.id} user={user} />)}
              </div>
            ) : (
              <div className="flex flex-col">
                {initials.map((initial) => (
                  <div key={initial}>
                    <div className="sticky top-0 z-0 bg-muted/30 px-4 py-1 text-xs font-semibold text-muted-foreground backdrop-blur-sm">
                      {initial}
                    </div>
                    {groupedUsers[initial].map((user) => <UserListItem key={user.id} user={user} />)}
                  </div>
                ))}
                <div className="py-8 text-center text-xs text-muted-foreground">共 {allUsers.length} 位联系人</div>
              </div>
            )}
          </>
        )}
      </ScrollArea>
    </div>
  )
}
