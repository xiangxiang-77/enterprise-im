import { useState } from "react"
import { useNavigate, useSearchParams } from "react-router-dom"
import { ChevronLeft, FileText, MessageSquare, Search } from "lucide-react"
import { format } from "date-fns"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { useChatStore } from "@/stores/useChatStore"

export default function GlobalSearch() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const filterSessionId = searchParams.get("sessionId")
  const [query, setQuery] = useState("")
  const { users, groups, messages, sessions } = useChatStore()

  const normalizedQuery = query.toLowerCase()
  const contactResults = filterSessionId ? [] : Object.values(users).filter((user) => (
    user.name.toLowerCase().includes(normalizedQuery) || user.phone?.includes(query)
  ))
  const groupResults = filterSessionId ? [] : Object.values(groups).filter((group) => (
    group.name.toLowerCase().includes(normalizedQuery)
  ))

  const scopedMessages = Object.entries(messages).filter(([sessionId]) => !filterSessionId || sessionId === filterSessionId)
  const messageResults = scopedMessages.flatMap(([sessionId, items]) => (
    items
      .filter((message) => message.type === "text" && message.content.toLowerCase().includes(normalizedQuery))
      .map((message) => ({ sessionId, sessionName: sessions.find((session) => session.id === sessionId)?.name || "未知会话", message }))
  ))
  const fileResults = scopedMessages.flatMap(([sessionId, items]) => (
    items
      .filter((message) => message.type === "file" && message.fileName?.toLowerCase().includes(normalizedQuery))
      .map((message) => ({ sessionId, sessionName: sessions.find((session) => session.id === sessionId)?.name || "未知会话", message }))
  ))
  const imageResults = scopedMessages.flatMap(([sessionId, items]) => (
    items
      .filter((message) => message.type === "image")
      .map((message) => ({ sessionId, sessionName: sessions.find((session) => session.id === sessionId)?.name || "未知会话", message }))
  ))

  const SearchResultItem = ({
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
    <div onClick={onClick} className="flex cursor-pointer items-center gap-3 border-b bg-background px-4 py-3 hover:bg-muted/50">
      <div className="flex h-10 w-10 items-center justify-center overflow-hidden rounded-full bg-muted">{icon}</div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between">
          <h3 className="truncate font-medium">{title}</h3>
          {time && <span className="ml-2 whitespace-nowrap text-xs text-muted-foreground">{time}</span>}
        </div>
        {subtitle && <p className="truncate text-xs text-muted-foreground">{subtitle}</p>}
      </div>
    </div>
  )

  const empty = contactResults.length === 0 && groupResults.length === 0 && messageResults.length === 0 && fileResults.length === 0

  return (
    <div className="flex h-full flex-col bg-background">
      <header className="flex shrink-0 items-center gap-2 border-b bg-background px-4 py-3">
        <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="-ml-2">
          <ChevronLeft className="h-6 w-6" />
        </Button>
        <div className="relative flex-1">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder={filterSessionId ? "搜索聊天记录" : "搜索联系人、群聊、聊天记录、文件"}
            className="h-9 border-none bg-muted/50 pl-9 focus-visible:ring-1"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            autoFocus
          />
        </div>
        {query && (
          <Button variant="ghost" size="sm" onClick={() => setQuery("")} className="px-2">
            清空
          </Button>
        )}
      </header>

      <div className="flex flex-1 flex-col overflow-hidden">
        {query ? (
          <Tabs defaultValue={filterSessionId ? "messages" : "all"} className="flex h-full flex-col">
            <div className="shrink-0 border-b bg-background px-4 py-2">
              <TabsList className="h-9 w-full justify-start overflow-x-auto bg-transparent p-0">
                {!filterSessionId && <TabsTrigger value="all" className="h-8 rounded-full px-4 data-[state=active]:bg-muted/50 data-[state=active]:shadow-none">全部</TabsTrigger>}
                {!filterSessionId && <TabsTrigger value="contacts" className="h-8 rounded-full px-4 data-[state=active]:bg-muted/50 data-[state=active]:shadow-none">联系人 ({contactResults.length})</TabsTrigger>}
                {!filterSessionId && <TabsTrigger value="groups" className="h-8 rounded-full px-4 data-[state=active]:bg-muted/50 data-[state=active]:shadow-none">群组 ({groupResults.length})</TabsTrigger>}
                <TabsTrigger value="messages" className="h-8 rounded-full px-4 data-[state=active]:bg-muted/50 data-[state=active]:shadow-none">聊天记录 ({messageResults.length})</TabsTrigger>
                <TabsTrigger value="files" className="h-8 rounded-full px-4 data-[state=active]:bg-muted/50 data-[state=active]:shadow-none">文件 ({fileResults.length})</TabsTrigger>
                <TabsTrigger value="images" className="h-8 rounded-full px-4 data-[state=active]:bg-muted/50 data-[state=active]:shadow-none">图片 ({imageResults.length})</TabsTrigger>
              </TabsList>
            </div>

            <ScrollArea className="flex-1">
              <div className="pb-4">
                <TabsContent value="all" className="m-0">
                  {contactResults.length > 0 && (
                    <div className="mb-2">
                      <div className="bg-muted/30 px-4 py-2 text-xs font-semibold text-muted-foreground">联系人</div>
                      {contactResults.slice(0, 3).map((user) => (
                        <SearchResultItem
                          key={user.id}
                          icon={<Avatar><AvatarImage src={user.avatar} /><AvatarFallback>{user.name[0]}</AvatarFallback></Avatar>}
                          title={user.name}
                          subtitle={user.phone}
                          onClick={() => navigate(`/contact/profile/${user.id}`)}
                        />
                      ))}
                    </div>
                  )}

                  {groupResults.length > 0 && (
                    <div className="mb-2">
                      <div className="bg-muted/30 px-4 py-2 text-xs font-semibold text-muted-foreground">群组</div>
                      {groupResults.slice(0, 3).map((group) => (
                        <SearchResultItem
                          key={group.id}
                          icon={<Avatar><AvatarImage src={group.avatar} /><AvatarFallback>{group.name[0]}</AvatarFallback></Avatar>}
                          title={group.name}
                          subtitle={`${group.members.length} 位成员`}
                          onClick={() => navigate(`/chat/${group.id}`)}
                        />
                      ))}
                    </div>
                  )}

                  {messageResults.length > 0 && (
                    <div className="mb-2">
                      <div className="bg-muted/30 px-4 py-2 text-xs font-semibold text-muted-foreground">聊天记录</div>
                      {messageResults.slice(0, 3).map(({ sessionId, sessionName, message }) => (
                        <SearchResultItem
                          key={message.id}
                          icon={<MessageSquare className="h-5 w-5 text-muted-foreground" />}
                          title={sessionName}
                          subtitle={message.content}
                          time={format(message.timestamp, "MM-dd HH:mm")}
                          onClick={() => navigate(`/chat/${sessionId}`)}
                        />
                      ))}
                    </div>
                  )}

                  {fileResults.length > 0 && (
                    <div className="mb-2">
                      <div className="bg-muted/30 px-4 py-2 text-xs font-semibold text-muted-foreground">文件</div>
                      {fileResults.slice(0, 3).map(({ sessionId, sessionName, message }) => (
                        <SearchResultItem
                          key={message.id}
                          icon={<FileText className="h-5 w-5 text-blue-500" />}
                          title={message.fileName || "未知文件"}
                          subtitle={`来自：${sessionName}`}
                          time={format(message.timestamp, "MM-dd HH:mm")}
                          onClick={() => navigate(`/chat/${sessionId}`)}
                        />
                      ))}
                    </div>
                  )}

                  {empty && (
                    <div className="flex flex-col items-center justify-center py-10 text-muted-foreground">
                      <Search className="mb-2 h-10 w-10 opacity-20" />
                      <p>未找到相关内容</p>
                    </div>
                  )}
                </TabsContent>

                <TabsContent value="contacts" className="m-0">
                  {contactResults.map((user) => (
                    <SearchResultItem key={user.id} icon={<Avatar><AvatarImage src={user.avatar} /><AvatarFallback>{user.name[0]}</AvatarFallback></Avatar>} title={user.name} subtitle={user.phone} onClick={() => navigate(`/contact/profile/${user.id}`)} />
                  ))}
                  {contactResults.length === 0 && <div className="p-8 text-center text-muted-foreground">无结果</div>}
                </TabsContent>

                <TabsContent value="groups" className="m-0">
                  {groupResults.map((group) => (
                    <SearchResultItem key={group.id} icon={<Avatar><AvatarImage src={group.avatar} /><AvatarFallback>{group.name[0]}</AvatarFallback></Avatar>} title={group.name} subtitle={`${group.members.length} 位成员`} onClick={() => navigate(`/chat/${group.id}`)} />
                  ))}
                  {groupResults.length === 0 && <div className="p-8 text-center text-muted-foreground">无结果</div>}
                </TabsContent>

                <TabsContent value="messages" className="m-0">
                  {messageResults.map(({ sessionId, sessionName, message }) => (
                    <SearchResultItem key={message.id} icon={<MessageSquare className="h-5 w-5 text-muted-foreground" />} title={sessionName} subtitle={message.content} time={format(message.timestamp, "MM-dd HH:mm")} onClick={() => navigate(`/chat/${sessionId}`)} />
                  ))}
                  {messageResults.length === 0 && <div className="p-8 text-center text-muted-foreground">无结果</div>}
                </TabsContent>

                <TabsContent value="files" className="m-0">
                  {fileResults.map((item) => (
                    <SearchResultItem key={item.message.id} icon={<FileText className="h-5 w-5 text-blue-500" />} title={item.message.fileName || "未知文件"} subtitle={item.sessionName} time={format(item.message.timestamp, "MM-dd HH:mm")} onClick={() => navigate(`/chat/${item.sessionId}`)} />
                  ))}
                  {fileResults.length === 0 && <div className="p-8 text-center text-muted-foreground">无结果</div>}
                </TabsContent>

                <TabsContent value="images" className="m-0">
                  <div className="grid grid-cols-3 gap-2 p-4">
                    {imageResults.map((item) => (
                      <div key={item.message.id} className="relative aspect-square cursor-pointer overflow-hidden rounded-md bg-muted group" onClick={() => navigate(`/chat/${item.sessionId}`)}>
                        <img src={item.message.fileUrl} className="h-full w-full object-cover" alt="" />
                        <div className="absolute inset-0 flex items-center justify-center bg-black/50 opacity-0 transition-opacity group-hover:opacity-100">
                          <span className="truncate px-2 text-xs text-white">{item.sessionName}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </TabsContent>
              </div>
            </ScrollArea>
          </Tabs>
        ) : (
          <div className="flex h-full flex-col items-center justify-center text-muted-foreground opacity-50">
            <Search className="mb-4 h-12 w-12" />
            <p>搜索联系人、群聊、聊天记录、文件</p>
          </div>
        )}
      </div>
    </div>
  )
}
