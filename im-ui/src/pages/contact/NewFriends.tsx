import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { Check, ChevronLeft, Search, UserPlus, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { ScrollArea } from "@/components/ui/scroll-area"
import { useChatStore } from "@/stores/useChatStore"
import type { User } from "@/types"

const initialRequests = [
  { id: "req-1", name: "林一鸣", message: "我是产品研发部林一鸣，请求添加你为好友" },
  { id: "req-2", name: "陈思远", message: "客户项目联调需要同步进度" },
  { id: "req-3", name: "周雨桐", message: "请通过好友申请，方便发送资料" },
  { id: "req-4", name: "赵明轩", message: "运营协调群成员申请加好友" },
  { id: "req-5", name: "沈佳宁", message: "移动端测试反馈需要沟通" },
].map((item) => ({
  ...item,
  avatar: `https://api.dicebear.com/9.x/initials/svg?seed=${encodeURIComponent(item.name)}`,
  status: "pending" as "pending" | "accepted" | "rejected",
}))

export default function NewFriends() {
  const navigate = useNavigate()
  const [searchQuery, setSearchQuery] = useState("")
  const { addUser } = useChatStore()
  const [requests, setRequests] = useState(initialRequests)

  const handleAction = (id: string, action: "accept" | "reject") => {
    if (action === "accept") {
      const req = requests.find((item) => item.id === id)
      if (req) {
        const newUser: User = {
          id: req.id,
          name: req.name,
          avatar: req.avatar,
          status: "online",
          phone: `1380000${id.slice(-1).padStart(4, "0")}`,
          email: `${id}@example.com`,
          bio: req.message,
        }
        addUser(newUser)
      }
    }

    setRequests(requests.map((req) => (
      req.id === id ? { ...req, status: action === "accept" ? "accepted" : "rejected" } : req
    )))
  }

  const handleAddFriend = () => {
    const account = prompt("请输入对方账号或手机号")
    if (!account) return

    const name = `用户 ${account}`
    setRequests([
      {
        id: `req-new-${Date.now()}`,
        name,
        avatar: `https://api.dicebear.com/9.x/initials/svg?seed=${encodeURIComponent(name)}`,
        message: "请求添加你为好友",
        status: "pending",
      },
      ...requests,
    ])
  }

  const filteredRequests = requests.filter((req) => (
    req.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    req.message.includes(searchQuery)
  ))

  return (
    <div className="flex h-full flex-col bg-background">
      <header className="sticky top-0 z-10 flex items-center gap-2 border-b bg-background/95 px-4 py-3 backdrop-blur">
        <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="-ml-2">
          <ChevronLeft className="h-6 w-6" />
        </Button>
        <h1 className="flex-1 text-lg font-semibold">新的朋友</h1>
        <Button variant="ghost" size="sm" onClick={handleAddFriend}>添加朋友</Button>
      </header>

      <div className="border-b bg-muted/10 px-4 py-3">
        <div className="relative">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="搜索手机号或账号"
            className="h-9 border-none bg-background pl-9"
            value={searchQuery}
            onChange={(event) => setSearchQuery(event.target.value)}
          />
        </div>
      </div>

      <ScrollArea className="flex-1">
        <div className="flex flex-col">
          {filteredRequests.length > 0 ? (
            filteredRequests.map((req) => (
              <div key={req.id} className="flex items-center gap-3 border-b bg-background px-4 py-3">
                <Avatar className="h-10 w-10">
                  <AvatarImage src={req.avatar} />
                  <AvatarFallback>{req.name[0]}</AvatarFallback>
                </Avatar>
                <div className="min-w-0 flex-1">
                  <h3 className="truncate font-medium">{req.name}</h3>
                  <p className="truncate text-xs text-muted-foreground">{req.message}</p>
                </div>

                {req.status === "pending" ? (
                  <div className="flex gap-2">
                    <Button size="sm" variant="outline" className="h-8 w-8 rounded-full border-red-200 p-0 text-muted-foreground hover:bg-red-50 hover:text-red-600" onClick={() => handleAction(req.id, "reject")}>
                      <X className="h-4 w-4" />
                    </Button>
                    <Button size="sm" className="h-8 w-8 rounded-full p-0" onClick={() => handleAction(req.id, "accept")}>
                      <Check className="h-4 w-4" />
                    </Button>
                  </div>
                ) : (
                  <span className="px-2 text-xs text-muted-foreground">
                    {req.status === "accepted" ? "已添加" : "已拒绝"}
                  </span>
                )}
              </div>
            ))
          ) : (
            <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
              <UserPlus className="mb-4 h-12 w-12 opacity-20" />
              <p>暂无新的朋友请求</p>
            </div>
          )}
        </div>
      </ScrollArea>
    </div>
  )
}
