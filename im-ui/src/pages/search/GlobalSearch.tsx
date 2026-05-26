import { useEffect, useMemo, useState } from "react"
import { useNavigate, useSearchParams } from "react-router-dom"
import { ChevronLeft, FileText, MessageSquare, Mic, Search, UserRound, UsersRound } from "lucide-react"
import { format } from "date-fns"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { searchApi, searchRecommendationsApi, type SearchRecommendations, type SearchResult } from "@/services/api"
import { useAuthStore } from "@/stores/useAuthStore"

function highlight(text = "", query: string) {
  if (!query) return text
  const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
  return text.replace(new RegExp(`(${escaped})`, "gi"), '<mark class="rounded bg-yellow-200 px-0.5 dark:bg-yellow-800">$1</mark>')
}

export default function GlobalSearch() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const filterSessionId = searchParams.get("sessionId")
  const { token } = useAuthStore()
  const [query, setQuery] = useState("")
  const [activeTab, setActiveTab] = useState(filterSessionId ? "messages" : "all")
  const [results, setResults] = useState<SearchResult>({})
  const [recommendations, setRecommendations] = useState<SearchRecommendations>({})
  const [searching, setSearching] = useState(false)
  const [listening, setListening] = useState(false)
  const [history, setHistory] = useState<string[]>(() => {
    try { return JSON.parse(localStorage.getItem("search-history") || "[]") } catch { return [] }
  })

  useEffect(() => {
    if (!token) return
    searchRecommendationsApi(token).then(setRecommendations).catch(() => setRecommendations({}))
  }, [token])

  useEffect(() => {
    if (!query.trim() || !token) {
      setResults({})
      return
    }
    const timer = window.setTimeout(async () => {
      setSearching(true)
      try {
        const data = await searchApi(query, filterSessionId ? "messages" : activeTab, token)
        setResults(data)
        setHistory((prev) => {
          const next = [query.trim(), ...prev.filter((item) => item !== query.trim())].slice(0, 20)
          localStorage.setItem("search-history", JSON.stringify(next))
          return next
        })
      } finally {
        setSearching(false)
      }
    }, 300)
    return () => window.clearTimeout(timer)
  }, [activeTab, filterSessionId, query, token])

  const counts = useMemo(() => ({
    contacts: results.contacts?.length ?? 0,
    groups: results.groups?.length ?? 0,
    messages: results.messages?.length ?? 0,
    files: results.files?.length ?? 0,
  }), [results])

  const startVoiceSearch = () => {
    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
    if (!SpeechRecognition) {
      setQuery("浏览器不支持语音搜索")
      return
    }
    const recognition = new SpeechRecognition()
    recognition.lang = "zh-CN"
    recognition.interimResults = false
    recognition.onstart = () => setListening(true)
    recognition.onend = () => setListening(false)
    recognition.onerror = () => setListening(false)
    recognition.onresult = (event: any) => setQuery(event.results?.[0]?.[0]?.transcript || "")
    recognition.start()
  }

  const jump = (url?: string, fallback?: string) => {
    navigate(url || fallback || "/")
  }

  const ResultRow = ({
    icon,
    title,
    subtitle,
    time,
    onClick,
  }: {
    icon: React.ReactNode
    title: string
    subtitle?: string
    time?: string
    onClick?: () => void
  }) => (
    <button type="button" onClick={onClick} className="flex w-full items-center gap-3 border-b bg-background px-4 py-3 text-left hover:bg-muted/50">
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-muted">{icon}</span>
      <span className="min-w-0 flex-1">
        <span className="flex items-center justify-between gap-3">
          <span className="truncate text-sm font-medium" dangerouslySetInnerHTML={{ __html: highlight(title, query) }} />
          {time && <span className="shrink-0 text-xs text-muted-foreground">{time}</span>}
        </span>
        {subtitle && <span className="block truncate text-xs text-muted-foreground" dangerouslySetInnerHTML={{ __html: highlight(subtitle, query) }} />}
      </span>
    </button>
  )

  const renderContacts = (items = results.contacts || []) => items.map((item) => (
    <ResultRow key={item.id} icon={<UserRound className="h-5 w-5" />} title={item.name} subtitle={item.phone} onClick={() => jump(undefined, `/contact/profile/${item.id}`)} />
  ))

  const renderGroups = (items = results.groups || []) => items.map((item) => (
    <ResultRow key={item.id} icon={<UsersRound className="h-5 w-5" />} title={item.name} subtitle={item.notice} onClick={() => jump(item.jumpUrl, `/chat/${item.id}`)} />
  ))

  const renderMessages = (items = results.messages || []) => items.map((item) => (
    <ResultRow
      key={item.id}
      icon={<MessageSquare className="h-5 w-5" />}
      title={item.conversationId}
      subtitle={item.content || item.type}
      time={item.createdAt ? format(new Date(item.createdAt), "MM-dd HH:mm") : undefined}
      onClick={() => jump(item.jumpUrl, `/chat/${item.conversationId}?messageId=${item.id}`)}
    />
  ))

  const renderFiles = (items = results.files || []) => items.map((item) => (
    <ResultRow key={item.id} icon={<FileText className="h-5 w-5" />} title={item.name} subtitle={`${item.contentType || "file"} · ${item.sizeBytes} bytes`} onClick={() => jump(item.jumpUrl, "/files")} />
  ))

  const isEmpty = counts.contacts + counts.groups + counts.messages + counts.files === 0

  return (
    <div className="flex h-full flex-col bg-background">
      <header className="flex shrink-0 items-center gap-2 border-b bg-background px-4 py-3">
        <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="-ml-2">
          <ChevronLeft className="h-6 w-6" />
        </Button>
        <div className="relative flex-1">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder={filterSessionId ? "搜索聊天记录" : "搜索联系人、群组、聊天记录、文件"}
            className="h-9 border-none bg-muted/50 pl-9 pr-10 focus-visible:ring-1"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            autoFocus
          />
          <Button variant="ghost" size="icon" className="absolute right-0 top-0 h-9 w-9" onClick={startVoiceSearch} title="语音搜索">
            <Mic className={`h-4 w-4 ${listening ? "text-primary" : ""}`} />
          </Button>
        </div>
      </header>

      {query ? (
        <Tabs value={activeTab} onValueChange={setActiveTab} className="flex min-h-0 flex-1 flex-col">
          <div className="shrink-0 border-b px-4 py-2">
            <TabsList className="h-9 w-full justify-start overflow-x-auto bg-transparent p-0">
              {!filterSessionId && <TabsTrigger value="all">全部</TabsTrigger>}
              {!filterSessionId && <TabsTrigger value="contacts">联系人 {counts.contacts}</TabsTrigger>}
              {!filterSessionId && <TabsTrigger value="groups">群组 {counts.groups}</TabsTrigger>}
              <TabsTrigger value="messages">消息 {counts.messages}</TabsTrigger>
              <TabsTrigger value="files">文件 {counts.files}</TabsTrigger>
            </TabsList>
          </div>
          <ScrollArea className="flex-1">
            {searching && <div className="px-4 py-2 text-xs text-muted-foreground">搜索中...</div>}
            <TabsContent value="all" className="m-0">
              {renderContacts((results.contacts || []).slice(0, 5))}
              {renderGroups((results.groups || []).slice(0, 5))}
              {renderMessages((results.messages || []).slice(0, 8))}
              {renderFiles((results.files || []).slice(0, 5))}
            </TabsContent>
            <TabsContent value="contacts" className="m-0">{renderContacts()}</TabsContent>
            <TabsContent value="groups" className="m-0">{renderGroups()}</TabsContent>
            <TabsContent value="messages" className="m-0">{renderMessages()}</TabsContent>
            <TabsContent value="files" className="m-0">{renderFiles()}</TabsContent>
            {isEmpty && !searching && <div className="px-4 py-10 text-center text-sm text-muted-foreground">无结果</div>}
          </ScrollArea>
        </Tabs>
      ) : (
        <ScrollArea className="flex-1">
          <div className="space-y-5 px-4 py-4">
            {history.length > 0 && (
              <section>
                <div className="mb-2 flex items-center justify-between">
                  <h2 className="text-xs font-semibold text-muted-foreground">搜索历史</h2>
                  <Button variant="ghost" size="sm" className="h-7 px-2 text-xs" onClick={() => { setHistory([]); localStorage.removeItem("search-history") }}>清除</Button>
                </div>
                <div className="flex flex-wrap gap-2">
                  {history.map((item) => (
                    <button key={item} className="rounded-full bg-muted px-3 py-1 text-xs" onClick={() => setQuery(item)}>{item}</button>
                  ))}
                </div>
              </section>
            )}
            <section>
              <h2 className="mb-2 text-xs font-semibold text-muted-foreground">推荐</h2>
              <div className="overflow-hidden rounded-md border">
                {renderContacts(recommendations.contacts || [])}
                {renderGroups(recommendations.groups || [])}
                {renderMessages(recommendations.messages || [])}
                {!(recommendations.contacts?.length || recommendations.groups?.length || recommendations.messages?.length) && (
                  <div className="px-4 py-8 text-center text-sm text-muted-foreground">暂无推荐</div>
                )}
              </div>
            </section>
          </div>
        </ScrollArea>
      )}
    </div>
  )
}
