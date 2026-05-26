import { useEffect, useMemo, useRef, useState } from "react"
import { useNavigate } from "react-router-dom"
import { ChevronDown, ChevronRight, Folder, Search, Star, UserPlus, Users } from "lucide-react"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { useAuthStore } from "@/stores/useAuthStore"
import { useChatStore } from "@/stores/useChatStore"
import { cn } from "@/lib/utils"
import {
  fetchDirectoryDepartmentsApi,
  fetchDirectoryEnterprisesApi,
  fetchDirectoryUsersApi,
  fetchFriendsApi,
  type DirectoryDepartment,
  type DirectoryEnterprise,
  type DirectoryUser,
} from "@/services/api"
import type { User } from "@/types"

type OrgNode = DirectoryDepartment & {
  children: OrgNode[]
}

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

function buildTree(departments: DirectoryDepartment[]) {
  const nodes = new Map<string, OrgNode>()
  departments.forEach((department) => nodes.set(department.id, { ...department, children: [] }))
  const roots: OrgNode[] = []
  nodes.forEach((node) => {
    if (node.parentId && nodes.has(node.parentId)) {
      nodes.get(node.parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
}

export default function ContactList() {
  const navigate = useNavigate()
  const { user: currentUser, token } = useAuthStore()
  const { addUser } = useChatStore()
  const [searchQuery, setSearchQuery] = useState("")
  const [activeTab, setActiveTab] = useState("org")
  const [enterprises, setEnterprises] = useState<DirectoryEnterprise[]>([])
  const [departments, setDepartments] = useState<DirectoryDepartment[]>([])
  const [directoryUsers, setDirectoryUsers] = useState<User[]>([])
  const [friends, setFriends] = useState<User[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")
  const [starredIds, setStarredIds] = useState<string[]>(() => {
    try { return JSON.parse(localStorage.getItem("starred-contacts") || "[]") } catch { return [] }
  })
  const [recentVisits, setRecentVisits] = useState<string[]>(() => {
    try { return JSON.parse(localStorage.getItem("recent-visits") || "[]") } catch { return [] }
  })
  const scrollRef = useRef<HTMLDivElement>(null)

  const loadContacts = async () => {
    if (!currentUser?.id || !token) return
    setLoading(true)
    setError("")
    try {
      const [enterpriseRows, departmentRows, directoryRows, friendRows] = await Promise.all([
        fetchDirectoryEnterprisesApi(token),
        fetchDirectoryDepartmentsApi(token),
        fetchDirectoryUsersApi(token, { limit: 200 }),
        fetchFriendsApi(currentUser.id, token),
      ])
      const mappedDirectory = directoryRows.map(toUser)
      const mappedFriends = friendRows.map(toUser)
      mappedDirectory.forEach(addUser)
      mappedFriends.forEach(addUser)
      setEnterprises(enterpriseRows)
      setDepartments(departmentRows)
      setDirectoryUsers(mappedDirectory)
      setFriends(mappedFriends)
    } catch (err) {
      setError(err instanceof Error ? err.message : "load failed")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadContacts()
  }, [currentUser?.id, token])

  const allUsers = activeTab === "org" ? directoryUsers : friends
  const filteredUsers = allUsers.filter((item) => (
    item.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    item.phone?.includes(searchQuery)
  ))

  const groupedUsers = filteredUsers.reduce((acc, item) => {
    const key = /^[A-Za-z]/.test(item.name[0] || "") ? item.name[0].toUpperCase() : "#"
    acc[key] = [...(acc[key] || []), item]
    return acc
  }, {} as Record<string, User[]>)

  const initials = Object.keys(groupedUsers).sort()
  const orgTree = useMemo(() => buildTree(departments), [departments])
  const enterpriseName = enterprises[0]?.name || "企业通讯录"
  const [expandedPath, setExpandedPath] = useState<string[]>([])

  const departmentMap = useMemo(() => {
    const map = new Map<string, DirectoryDepartment>()
    departments.forEach((d) => map.set(d.id, d))
    return map
  }, [departments])

  const breadcrumb = useMemo(() => {
    const crumbs: { id: string; name: string }[] = [{ id: "__root__", name: enterpriseName }]
    expandedPath.forEach((id) => {
      const dept = departmentMap.get(id)
      if (dept) crumbs.push({ id: dept.id, name: dept.name })
    })
    return crumbs
  }, [expandedPath, departmentMap, enterpriseName])

  const toggleStar = (userId: string) => {
    setStarredIds((prev) => {
      const next = prev.includes(userId) ? prev.filter((id) => id !== userId) : [...prev, userId]
      localStorage.setItem("starred-contacts", JSON.stringify(next))
      return next
    })
  }

  const addRecentVisit = (id: string) => {
    setRecentVisits((prev) => {
      const next = [id, ...prev.filter((v) => v !== id)].slice(0, 10)
      localStorage.setItem("recent-visits", JSON.stringify(next))
      return next
    })
  }

  const recentUsers = useMemo(() => {
    const all = [...directoryUsers, ...friends]
    return recentVisits.map((id) => all.find((u) => u.id === id)).filter(Boolean) as User[]
  }, [recentVisits, directoryUsers, friends])

  const starredUsers = useMemo(() => friends.filter((f) => starredIds.includes(f.id)), [friends, starredIds])

  const scrollToLetter = (letter: string) => {
    const el = document.getElementById(`letter-${letter}`)
    el?.scrollIntoView({ behavior: "smooth", block: "start" })
  }

  const handleBreadcrumbClick = (index: number) => {
    setExpandedPath((prev) => prev.slice(0, index))
  }

  const OrgItem = ({ item, level = 0 }: { item: OrgNode; level?: number }) => {
    const [isOpen, setIsOpen] = useState(level === 0)
    const hasChildren = item.children.length > 0

    const handleClick = () => {
      if (hasChildren) {
        setIsOpen(!isOpen)
        if (!isOpen) {
          setExpandedPath((prev) => [...prev.slice(0, level), item.id])
        }
      }
    }

    return (
      <div>
        <div
          className={cn("flex cursor-pointer select-none items-center gap-2 px-4 py-3 hover:bg-muted/50", level > 0 && "pl-8")}
          onClick={handleClick}
        >
          {hasChildren ? (
            isOpen ? <ChevronDown className="h-4 w-4 text-muted-foreground" /> : <ChevronRight className="h-4 w-4 text-muted-foreground" />
          ) : (
            <div className="w-4" />
          )}
          <Folder className="h-5 w-5 fill-blue-100 text-blue-500" />
          <span className="flex-1 text-sm font-medium">{item.name}</span>
          <span className="text-xs text-muted-foreground">({item.memberCount})</span>
        </div>
        {isOpen && item.children.map((child) => <OrgItem key={child.id} item={child} level={level + 1} />)}
      </div>
    )
  }

  const UserListItem = ({ item }: { item: User }) => (
    <div
      className="flex cursor-pointer items-center gap-3 px-4 py-3 hover:bg-muted/50"
      onClick={() => { addRecentVisit(item.id); navigate(`/contact/profile/${item.id}`) }}
    >
      <div className="relative">
        <Avatar>
          <AvatarImage src={item.avatar} />
          <AvatarFallback>{item.name[0]}</AvatarFallback>
        </Avatar>
        {item.status === "online" && <span className="absolute bottom-0 right-0 h-3 w-3 rounded-full border-2 border-background bg-green-500" />}
        {item.status === "busy" && <span className="absolute bottom-0 right-0 h-3 w-3 rounded-full border-2 border-background bg-orange-500" />}
      </div>
      <div className="min-w-0 flex-1">
        <h3 className="truncate font-medium">{item.name}</h3>
        <p className="truncate text-sm text-muted-foreground">{item.signature || item.phone || "暂无签名"}</p>
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
        {loading && <div className="px-4 py-3 text-sm text-muted-foreground">加载中...</div>}
        {error && <div className="px-4 py-3 text-sm text-red-500">加载失败：{error}</div>}
        {activeTab === "org" ? (
          <div className="pb-4">
            {expandedPath.length > 0 && (
              <div className="flex items-center gap-1 border-b bg-muted/5 px-4 py-2 text-xs text-muted-foreground">
                {breadcrumb.map((crumb, idx) => (
                  <span key={crumb.id} className="flex items-center gap-1">
                    {idx > 0 && <ChevronRight className="h-3 w-3" />}
                    <button
                      className="hover:text-foreground hover:underline"
                      onClick={() => handleBreadcrumbClick(idx)}
                    >
                      {crumb.name}
                    </button>
                  </span>
                ))}
              </div>
            )}
            <div className="flex items-center justify-between border-b bg-muted/10 px-4 py-3">
              <span className="text-sm font-semibold">{enterpriseName}</span>
              <span className="text-xs text-muted-foreground">{directoryUsers.length} 人</span>
            </div>

            {/* Quick access */}
            {!searchQuery && (
              <div className="flex flex-col border-b">
                <div className="flex cursor-pointer items-center gap-3 px-4 py-3 hover:bg-muted/50">
                  <div className="flex h-9 w-9 items-center justify-center rounded bg-blue-500 text-white">
                    <Folder className="h-5 w-5" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <span className="text-sm font-medium">我的部门</span>
                    <p className="text-xs text-muted-foreground">{currentUser?.name ? `${currentUser.name} 所在部门` : "点击查看"}</p>
                  </div>
                  <ChevronRight className="h-4 w-4 text-muted-foreground opacity-50" />
                </div>
              </div>
            )}

            {/* Recent visits */}
            {!searchQuery && recentUsers.length > 0 && (
              <div className="border-b">
                <div className="bg-muted/30 px-4 py-1 text-xs font-semibold text-muted-foreground">最近访问</div>
                <div className="flex gap-3 overflow-x-auto px-4 py-3">
                  {recentUsers.slice(0, 10).map((user) => (
                    <div key={user.id} className="flex flex-col items-center gap-1 cursor-pointer" onClick={() => navigate(`/contact/profile/${user.id}`)}>
                      <Avatar className="h-10 w-10">
                        <AvatarImage src={user.avatar} />
                        <AvatarFallback>{user.name[0]}</AvatarFallback>
                      </Avatar>
                      <span className="w-12 truncate text-center text-[10px]">{user.name}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {orgTree.map((department) => <OrgItem key={department.id} item={department} />)}
            <div className="mt-4 bg-muted/20 px-4 py-2 text-xs font-semibold text-muted-foreground">企业成员</div>
            {filteredUsers.map((item) => <UserListItem key={item.id} item={item} />)}
            {!loading && filteredUsers.length === 0 && <div className="p-8 text-center text-muted-foreground">暂无成员</div>}
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

            {!searchQuery && starredUsers.length > 0 && (
              <div className="border-b">
                <div className="bg-muted/30 px-4 py-1 text-xs font-semibold text-muted-foreground">特别关注</div>
                <div className="flex gap-3 overflow-x-auto px-4 py-3">
                  {starredUsers.map((user) => (
                    <div key={user.id} className="flex flex-col items-center gap-1 cursor-pointer" onClick={() => navigate(`/contact/profile/${user.id}`)}>
                      <Avatar className="h-12 w-12">
                        <AvatarImage src={user.avatar} />
                        <AvatarFallback>{user.name[0]}</AvatarFallback>
                      </Avatar>
                      <span className="w-14 truncate text-center text-xs">{user.name}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div className="relative flex flex-col">
              {initials.map((initial) => (
                <div key={initial} id={`letter-${initial}`}>
                  <div className="sticky top-0 z-0 bg-muted/30 px-4 py-1 text-xs font-semibold text-muted-foreground backdrop-blur-sm">
                    {initial}
                  </div>
                  {groupedUsers[initial].map((item) => (
                    <div key={item.id} className="group relative">
                      <UserListItem item={item} />
                      <button
                        className="absolute right-4 top-1/2 -translate-y-1/2 opacity-0 group-hover:opacity-100 transition-opacity"
                        onClick={(e) => { e.stopPropagation(); toggleStar(item.id) }}
                      >
                        <Star className={`h-4 w-4 ${starredIds.includes(item.id) ? "fill-yellow-400 text-yellow-400" : "text-muted-foreground"}`} />
                      </button>
                    </div>
                  ))}
                </div>
              ))}
              {!loading && filteredUsers.length === 0 && <div className="p-8 text-center text-muted-foreground">暂无联系人</div>}
              <div className="py-8 text-center text-xs text-muted-foreground">共 {filteredUsers.length} 位联系人</div>

              {/* Alphabet sidebar */}
              {initials.length > 2 && (
                <div className="fixed right-1 top-1/2 z-30 flex -translate-y-1/2 flex-col items-center gap-0.5 rounded-full bg-background/80 px-1 py-2 text-[10px] shadow-sm backdrop-blur">
                  {initials.map((letter) => (
                    <button
                      key={letter}
                      className="flex h-4 w-4 items-center justify-center rounded text-muted-foreground hover:bg-primary hover:text-primary-foreground"
                      onClick={() => scrollToLetter(letter)}
                    >
                      {letter}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </>
        )}
      </ScrollArea>
    </div>
  )
}
