import { useEffect, useMemo, useRef, useState } from "react"
import { useNavigate } from "react-router-dom"
import { Check, ChevronLeft, Search, Trash2, UserPlus, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  createFriendRequestApi,
  fetchFriendRequestsApi,
  handleFriendRequestApi,
  searchApi,
  type FriendRequestRecord,
  type SearchResult,
} from "@/services/api"
import { useAuthStore } from "@/stores/useAuthStore"

export default function NewFriends() {
  const navigate = useNavigate()
  const { user, token } = useAuthStore()
  const [searchQuery, setSearchQuery] = useState("")
  const [requests, setRequests] = useState<FriendRequestRecord[]>([])
  const [searchUsers, setSearchUsers] = useState<NonNullable<SearchResult["contacts"]>>([])
  const [loading, setLoading] = useState(false)
  const [searchLoading, setSearchLoading] = useState(false)
  const [error, setError] = useState("")

  const loadRequests = async () => {
    if (!user?.id || !token) return
    setLoading(true)
    setError("")
    try {
      setRequests(await fetchFriendRequestsApi(user.id, token))
    } catch (err) {
      setError(err instanceof Error ? err.message : "load failed")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadRequests()
  }, [user?.id, token])

  useEffect(() => {
    if (!token || searchQuery.trim().length < 2) {
      setSearchUsers([])
      return
    }
    const timer = window.setTimeout(async () => {
      setSearchLoading(true)
      try {
        const data = await searchApi(searchQuery.trim(), "contacts", token)
        setSearchUsers((data.contacts || []).filter((item) => item.id !== user?.id))
      } catch {
        setSearchUsers([])
      } finally {
        setSearchLoading(false)
      }
    }, 300)
    return () => window.clearTimeout(timer)
  }, [searchQuery, token, user?.id])

  const handleAction = async (id: string, accept: boolean) => {
    if (!token) return
    try {
      const updated = await handleFriendRequestApi(id, accept, token)
      setRequests((items) => items.map((item) => (item.id === id ? updated : item)))
    } catch (err) {
      setError(err instanceof Error ? err.message : "handle failed")
    }
  }

  const [addDialogOpen, setAddDialogOpen] = useState(false)
  const [addAccount, setAddAccount] = useState("")
  const [addMessage, setAddMessage] = useState("请求添加你为好友")
  const [addLoading, setAddLoading] = useState(false)

  const handleAddFriend = async () => {
    if (!token || !addAccount.trim()) return
    setAddLoading(true)
    setError("")
    try {
      const created = await createFriendRequestApi({
        receiverAccount: addAccount.trim(),
        message: addMessage || "请求添加你为好友",
      }, token)
      setRequests((items) => [created, ...items.filter((item) => item.id !== created.id)])
      setAddDialogOpen(false)
      setAddAccount("")
      setAddMessage("请求添加你为好友")
    } catch (err) {
      setError(err instanceof Error ? err.message : "create failed")
    } finally {
      setAddLoading(false)
    }
  }

  const [swipedId, setSwipedId] = useState<string | null>(null)
  const touchStartRef = useRef<{ x: number; id: string } | null>(null)

  const handleDeleteRequest = (id: string) => {
    setRequests((items) => items.filter((item) => item.id !== id))
    setSwipedId(null)
  }

  const filteredRequests = useMemo(() => requests.filter((req) => {
    const peerName = req.requesterId === user?.id ? req.receiverName : req.requesterName
    return peerName.toLowerCase().includes(searchQuery.toLowerCase()) || (req.message || "").includes(searchQuery)
  }), [requests, searchQuery, user?.id])

  const peerName = (req: FriendRequestRecord) => (req.requesterId === user?.id ? req.receiverName : req.requesterName)
  const isIncoming = (req: FriendRequestRecord) => req.receiverId === user?.id
  const sendFriendRequest = async (account: string) => {
    if (!token || !account.trim()) return
    setAddLoading(true)
    setError("")
    try {
      const created = await createFriendRequestApi({
        receiverAccount: account.trim(),
        message: addMessage || "请求添加你为好友",
      }, token)
      setRequests((items) => [created, ...items.filter((item) => item.id !== created.id)])
    } catch (err) {
      setError(err instanceof Error ? err.message : "create failed")
    } finally {
      setAddLoading(false)
    }
  }

  return (
    <div className="flex h-full flex-col bg-background">
      <header className="sticky top-0 z-10 flex items-center gap-2 border-b bg-background/95 px-4 py-3 backdrop-blur">
        <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="-ml-2">
          <ChevronLeft className="h-6 w-6" />
        </Button>
        <h1 className="flex-1 text-lg font-semibold">新的朋友</h1>
        <Button variant="ghost" size="sm" onClick={() => setAddDialogOpen(true)}>添加好友</Button>
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
        {searchLoading && <div className="px-4 py-2 text-xs text-muted-foreground">搜索用户中...</div>}
        {loading && <div className="px-4 py-3 text-sm text-muted-foreground">加载中...</div>}
        {error && <div className="px-4 py-3 text-sm text-red-500">操作失败：{error}</div>}
        <div className="flex flex-col">
          {searchUsers.length > 0 && (
            <div className="border-b bg-muted/20 py-2">
              <div className="px-4 pb-2 text-xs font-medium text-muted-foreground">手机号/账号搜索结果</div>
              {searchUsers.map((item) => (
                <div key={item.id} className="flex items-center gap-3 bg-background px-4 py-3">
                  <Avatar className="h-10 w-10">
                    <AvatarFallback>{item.name?.[0] || "U"}</AvatarFallback>
                  </Avatar>
                  <div className="min-w-0 flex-1">
                    <h3 className="truncate font-medium">{item.name}</h3>
                    <p className="truncate text-xs text-muted-foreground">{item.phone || item.id}</p>
                  </div>
                  <Button size="sm" disabled={addLoading} onClick={() => sendFriendRequest(item.phone || item.id)}>
                    添加
                  </Button>
                </div>
              ))}
            </div>
          )}
          {filteredRequests.length > 0 ? (
            filteredRequests.map((req) => (
              <div
                key={req.id}
                className="relative overflow-hidden"
                onTouchStart={(e) => { touchStartRef.current = { x: e.touches[0].clientX, id: req.id } }}
                onTouchEnd={(e) => {
                  if (!touchStartRef.current || touchStartRef.current.id !== req.id) return
                  const dx = touchStartRef.current.x - e.changedTouches[0].clientX
                  if (dx > 60) setSwipedId(req.id)
                  else if (dx < -30) setSwipedId(null)
                  touchStartRef.current = null
                }}
              >
                <div className="absolute right-0 top-0 flex h-full items-center">
                  <button
                    className="flex h-full w-16 items-center justify-center bg-red-500 text-white"
                    onClick={() => handleDeleteRequest(req.id)}
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
                <div
                  className="flex items-center gap-3 border-b bg-background px-4 py-3 transition-transform"
                  style={{ transform: swipedId === req.id ? "translateX(-64px)" : "translateX(0)" }}
                >
                  <Avatar className="h-10 w-10">
                    <AvatarFallback>{peerName(req)[0] || "U"}</AvatarFallback>
                  </Avatar>
                  <div className="min-w-0 flex-1">
                    <h3 className="truncate font-medium">{peerName(req)}</h3>
                    <p className="truncate text-xs text-muted-foreground">{req.message || "好友申请"}</p>
                  </div>

                  {req.status === "pending" && isIncoming(req) ? (
                    <div className="flex gap-2">
                      <Button size="sm" variant="outline" className="h-8 w-8 rounded-full border-red-200 p-0 text-muted-foreground hover:bg-red-50 hover:text-red-600" onClick={() => handleAction(req.id, false)}>
                        <X className="h-4 w-4" />
                      </Button>
                      <Button size="sm" className="h-8 w-8 rounded-full p-0" onClick={() => handleAction(req.id, true)}>
                        <Check className="h-4 w-4" />
                      </Button>
                    </div>
                  ) : (
                    <span className="px-2 text-xs text-muted-foreground">
                      {req.status === "pending" ? "等待处理" : req.status === "accepted" ? "已添加" : "已拒绝"}
                    </span>
                  )}
                </div>
              </div>
            ))
          ) : (
            <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
              <UserPlus className="mb-4 h-12 w-12 opacity-20" />
              <p>暂无好友申请</p>
            </div>
          )}
        </div>
      </ScrollArea>
      <Dialog open={addDialogOpen} onOpenChange={setAddDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>添加好友</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-3 py-2">
            <Input placeholder="输入对方账号、手机号或用户 ID" value={addAccount} onChange={(e) => setAddAccount(e.target.value)} />
            <Input placeholder="验证消息（可选）" value={addMessage} onChange={(e) => setAddMessage(e.target.value)} />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setAddDialogOpen(false)}>取消</Button>
            <Button onClick={handleAddFriend} disabled={!addAccount.trim() || addLoading}>
              {addLoading ? "发送中..." : "发送申请"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
