import { useNavigate } from "react-router-dom"
import { ChevronLeft, Star, Trash2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { MessageBubble } from "@/components/chat/MessageBubble"
import { useAuthStore } from "@/stores/useAuthStore"
import { useChatStore } from "@/stores/useChatStore"

export default function Favorites() {
  const navigate = useNavigate()
  const { favorites, removeFavorite } = useChatStore()
  const { user } = useAuthStore()

  return (
    <div className="flex h-full flex-col bg-background">
      <header className="sticky top-0 z-10 flex items-center gap-2 border-b bg-background px-4 py-3">
        <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="-ml-2">
          <ChevronLeft className="h-6 w-6" />
        </Button>
        <h1 className="text-lg font-semibold">收藏</h1>
      </header>

      <div className="flex-1 space-y-4 overflow-y-auto p-4">
        {favorites.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center gap-2 text-muted-foreground">
            <Star className="h-10 w-10 opacity-30" />
            <p>暂无收藏内容</p>
          </div>
        ) : (
          favorites.map((message) => (
            <div key={message.id} className="group relative rounded-lg border bg-card p-3 transition-all hover:shadow-sm">
              <div className="absolute right-2 top-2 z-10">
                <Button variant="ghost" size="icon" className="h-8 w-8 text-muted-foreground hover:bg-destructive/10 hover:text-destructive" onClick={() => removeFavorite(message.id)}>
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>

              <div className="pointer-events-none origin-top-left scale-90 pr-10 opacity-90">
                <MessageBubble message={message} isMe={message.senderId === user?.id} sender={undefined} />
              </div>

              <div className="mt-2 flex items-center justify-between border-t pt-2 text-xs text-muted-foreground">
                <span>{message.senderId === user?.id ? "我" : "对方"}</span>
                <span>{new Date(message.timestamp).toLocaleString()}</span>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}
