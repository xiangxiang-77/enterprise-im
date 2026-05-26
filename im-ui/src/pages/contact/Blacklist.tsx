import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { ChevronLeft, UserX } from "lucide-react"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { useAuthStore } from "@/stores/useAuthStore"
import { type BlacklistEntry, fetchBlacklistApi, unblockUserApi } from "@/services/api"

export default function Blacklist() {
  const navigate = useNavigate()
  const { user, token } = useAuthStore()
  const [entries, setEntries] = useState<BlacklistEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")

  async function load() {
    if (!user?.id || !token) return
    setLoading(true)
    setError("")
    try {
      setEntries(await fetchBlacklistApi(user.id, token))
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载失败")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [user?.id, token])

  async function unblock(blockedUserId: string) {
    if (!user?.id || !token) return
    const ok = window.confirm("确认解除拉黑？")
    if (!ok) return
    try {
      await unblockUserApi(user.id, blockedUserId, token)
      setEntries((prev) => prev.filter((e) => e.blockedUserId !== blockedUserId))
    } catch (err) {
      setError(err instanceof Error ? err.message : "操作失败")
    }
  }

  return (
    <div className="flex h-full flex-col bg-muted/20">
      <header className="sticky top-0 z-10 flex items-center gap-2 border-b bg-background px-4 py-3">
        <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="-ml-2">
          <ChevronLeft className="h-6 w-6" />
        </Button>
        <h2 className="font-semibold">黑名单</h2>
      </header>

      <ScrollArea className="flex-1">
        {error && (
          <div className="mx-4 mt-4 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {error}
          </div>
        )}

        {loading ? (
          <div className="flex items-center justify-center py-20 text-sm text-muted-foreground">加载中...</div>
        ) : entries.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-3 py-20 text-muted-foreground">
            <UserX className="h-12 w-12 opacity-30" />
            <p className="text-sm">黑名单为空</p>
          </div>
        ) : (
          <div className="divide-y border-y bg-background mt-4">
            {entries.map((entry) => (
              <div key={entry.blockedUserId} className="flex items-center justify-between px-4 py-3">
                <div className="flex items-center gap-3 min-w-0">
                  <Avatar className="h-10 w-10 rounded-lg">
                    <AvatarImage src="" />
                    <AvatarFallback>{entry.blockedName?.[0] || entry.blockedUserId[0]}</AvatarFallback>
                  </Avatar>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{entry.blockedName || entry.blockedUserId}</p>
                    <p className="truncate text-xs text-muted-foreground">{entry.blockedUserId}</p>
                  </div>
                </div>
                <Button variant="outline" size="sm" onClick={() => unblock(entry.blockedUserId)}>
                  解除拉黑
                </Button>
              </div>
            ))}
          </div>
        )}
      </ScrollArea>
    </div>
  )
}
