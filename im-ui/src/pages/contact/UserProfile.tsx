import { useNavigate, useParams } from "react-router-dom"
import { ChevronLeft, Globe, MessageSquare, MoreHorizontal, Phone } from "lucide-react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { useAuthStore } from "@/stores/useAuthStore"
import { useChatStore } from "@/stores/useChatStore"

export default function UserProfile() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { createSession, sessions, users } = useChatStore()
  const { user: currentUser } = useAuthStore()

  const user = id === currentUser?.id ? currentUser : id ? users[id] : undefined

  if (!user) {
    return <div className="flex h-full items-center justify-center">用户不存在</div>
  }

  const handleSendMessage = () => {
    const existingSession = sessions.find((session) => session.targetId === user.id && session.type === "single")
    if (existingSession) {
      navigate(`/chat/${existingSession.id}`)
      return
    }

    createSession(user.id, "single")
    const newSession = useChatStore.getState().sessions.find((session) => session.targetId === user.id && session.type === "single")
    if (newSession) navigate(`/chat/${newSession.id}`)
  }

  return (
    <div className="flex h-full flex-col bg-background">
      <header className="sticky top-0 z-10 flex items-center justify-between bg-background/95 px-4 py-3 backdrop-blur">
        <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="-ml-2">
          <ChevronLeft className="h-6 w-6" />
        </Button>
        <Button variant="ghost" size="icon">
          <MoreHorizontal className="h-6 w-6" />
        </Button>
      </header>

      <div className="flex-1 overflow-y-auto">
        <div className="flex flex-col px-6 pb-8 pt-4">
          <div className="mb-6 flex items-start gap-4">
            <Avatar className="h-20 w-20 rounded-lg border">
              <AvatarImage src={user.avatar} />
              <AvatarFallback>{user.name[0]}</AvatarFallback>
            </Avatar>
            <div className="min-w-0 flex-1 pt-1">
              <h1 className="truncate text-xl font-bold">{user.name}</h1>
              <p className="mt-1 text-sm text-muted-foreground">ID: {user.id.slice(0, 8)}</p>
              <p className="mt-1 flex items-center gap-1 text-sm text-muted-foreground">
                <Globe className="h-3 w-3" /> 地区：{user.region || "北京"}
              </p>
            </div>
          </div>

          <Separator className="my-4" />

          <div className="space-y-6">
            <div className="flex cursor-pointer items-center justify-between hover:opacity-70">
              <span className="font-medium">设置备注和标签</span>
              <ChevronLeft className="h-4 w-4 rotate-180 text-muted-foreground" />
            </div>

            <div className="flex cursor-pointer items-center justify-between hover:opacity-70">
              <span className="font-medium">朋友权限</span>
              <ChevronLeft className="h-4 w-4 rotate-180 text-muted-foreground" />
            </div>

            <div className="space-y-2">
              <div className="flex items-start justify-between">
                <span className="w-20 shrink-0 font-medium">个性签名</span>
                <p className="flex-1 text-right text-sm text-muted-foreground">
                  {user.id === currentUser?.id ? "编辑你的签名..." : user.signature || "这个用户暂未填写签名"}
                </p>
              </div>
              <div className="flex items-center justify-between pt-2">
                <span className="w-20 shrink-0 font-medium">朋友圈</span>
                <div className="flex items-center gap-1">
                  <div className="h-12 w-12 rounded bg-muted" />
                  <div className="h-12 w-12 rounded bg-muted" />
                  <div className="h-12 w-12 rounded bg-muted" />
                  <ChevronLeft className="ml-2 h-4 w-4 rotate-180 text-muted-foreground" />
                </div>
              </div>
              <div className="flex items-center justify-between pt-2">
                <span className="w-20 shrink-0 font-medium">更多信息</span>
                <ChevronLeft className="h-4 w-4 rotate-180 text-muted-foreground" />
              </div>
            </div>
          </div>

          <Separator className="my-6" />

          <div className="flex flex-col gap-3">
            <Button className="w-full gap-2 font-medium" size="lg" onClick={handleSendMessage}>
              <MessageSquare className="h-5 w-5" />
              发消息
            </Button>
            <Button variant="secondary" className="w-full gap-2 font-medium" size="lg">
              <Phone className="h-5 w-5" />
              音视频通话
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
