import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from "react"
import type { ForwardedRef } from "react"
import {
  AtSign,
  Camera,
  FileText,
  Image as ImageIcon,
  Keyboard,
  MapPin,
  Mic,
  Plus,
  Scissors,
  Send,
  Smile,
  User as UserIcon,
  X,
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { ImageEditor } from "@/components/common/ImageEditor"
import { cn } from "@/lib/utils"
import type { Message } from "@/types"

interface ChatInputProps {
  onSendMessage: (content: string, type: "text" | "image" | "file" | "voice" | "card" | "location", extra?: any) => void
  replyingTo?: Message | null
  onCancelReply?: () => void
  editingMessage?: Message | null
  onUpdateMessage?: (messageId: string, content: string) => void
  onCancelEdit?: () => void
  mentionUsers?: { id: string; name: string; avatar: string }[]
  onTyping?: (isTyping: boolean) => void
  defaultMessage?: string
  onMessageChange?: (message: string) => void
  onScreenshot?: () => void
  isGroup?: boolean
}

export type ChatInputRef = {
  focus: () => void
  insertText: (text: string) => void
}

const emojis = ["😀", "😂", "😊", "😎", "🤔", "😄", "😭", "👍", "🙏", "OK", "👋", "🎉", "🔥", "💙", "💪", "👀", "💬", "✅", "🚀", "🤝"]

export const ChatInput = forwardRef(({
  onSendMessage,
  replyingTo,
  onCancelReply,
  editingMessage,
  onUpdateMessage,
  onCancelEdit,
  mentionUsers = [],
  onTyping,
  defaultMessage = "",
  onMessageChange,
  onScreenshot,
  isGroup,
}: ChatInputProps, ref: ForwardedRef<ChatInputRef>) => {
  const [message, setMessage] = useState(defaultMessage)
  const [mode, setMode] = useState<"text" | "voice">("text")
  const [isRecording, setIsRecording] = useState(false)
  const [recordDuration, setRecordDuration] = useState(0)
  const [showMore, setShowMore] = useState(false)
  const [imageEditorOpen, setImageEditorOpen] = useState(false)
  const [selectedImageSrc, setSelectedImageSrc] = useState<string | null>(null)
  const [mentionOpen, setMentionOpen] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const typingTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const mediaRecorderRef = useRef<MediaRecorder | null>(null)
  const audioChunksRef = useRef<Blob[]>([])
  const recordDurationRef = useRef(0)

  useEffect(() => {
    setMessage((current) => (current === defaultMessage ? current : defaultMessage))
  }, [defaultMessage])

  useImperativeHandle(ref, () => ({
    focus: () => inputRef.current?.focus(),
    insertText: (text: string) => {
      setMessage((prev) => prev + text)
      inputRef.current?.focus()
    },
  }))

  useEffect(() => {
    if (replyingTo) inputRef.current?.focus()
  }, [replyingTo])

  useEffect(() => {
    if (editingMessage) {
      setMessage(editingMessage.content)
      setMode("text")
      inputRef.current?.focus()
    }
  }, [editingMessage])

  const stopTypingLater = () => {
    onTyping?.(true)
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current)
    typingTimeoutRef.current = setTimeout(() => onTyping?.(false), 8000)
  }

  const handleSend = () => {
    if (!message.trim()) return

    if (editingMessage && onUpdateMessage) {
      onUpdateMessage(editingMessage.id, message)
    } else {
      onSendMessage(message, "text", replyingTo ? { quoteId: replyingTo.id } : undefined)
    }

    setMessage("")
    if (!editingMessage) onMessageChange?.("")
    onCancelReply?.()
    onCancelEdit?.()
    onTyping?.(false)
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current)
    inputRef.current?.focus()
  }

  const handleInputChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const next = event.target.value
    setMessage(next)
    if (!editingMessage) onMessageChange?.(next)
    if (next.endsWith("@")) setMentionOpen(true)
    stopTypingLater()
  }

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault()
      handleSend()
    }
    if (event.key === "@") setMentionOpen(true)
  }

  const handleMentionSelect = (user: { id: string; name: string }) => {
    setMessage((prev) => prev + user.name + " ")
    setMentionOpen(false)
    inputRef.current?.focus()
  }

  const startRecording = async (event: React.MouseEvent | React.TouchEvent) => {
    event.preventDefault()
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === "undefined") {
      window.alert("当前浏览器不支持录音")
      mediaRecorderRef.current?.stop()
      return
    }
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true }).catch(() => null)
    if (!stream) {
      window.alert("无法访问麦克风")
      return
    }
    audioChunksRef.current = []
    const recorder = new MediaRecorder(stream)
    mediaRecorderRef.current = recorder
    recorder.ondataavailable = (evt) => {
      if (evt.data.size > 0) audioChunksRef.current.push(evt.data)
    }
    recorder.onstop = () => {
      stream.getTracks().forEach((track) => track.stop())
      if (recordDurationRef.current < 1 || audioChunksRef.current.length === 0) return
      const blob = new Blob(audioChunksRef.current, { type: recorder.mimeType || "audio/webm" })
      const file = new File([blob], `voice-${Date.now()}.webm`, { type: blob.type || "audio/webm" })
      const url = URL.createObjectURL(blob)
      onSendMessage(`语音消息 (${recordDurationRef.current}s)`, "voice", {
        rawFile: file,
        fileUrl: url,
        fileName: file.name,
        fileSize: file.size,
        voiceDuration: recordDurationRef.current,
      })
    }
    setIsRecording(true)
    setRecordDuration(0)
    recordDurationRef.current = 0
    recorder.start()
    timerRef.current = setInterval(() => {
      setRecordDuration((prev) => {
        if (prev >= 60) {
          if (timerRef.current) clearInterval(timerRef.current)
          setIsRecording(false)
          recordDurationRef.current = 60
          mediaRecorderRef.current?.stop()
          return 60
        }
        const next = prev + 1
        recordDurationRef.current = next
        return next
      })
    }, 1000)
  }

  const stopRecording = (event: React.MouseEvent | React.TouchEvent) => {
    event.preventDefault()
    if (!isRecording) return
    setIsRecording(false)
    if (timerRef.current) clearInterval(timerRef.current)

    if (recordDuration < 1) {
      window.alert("录制时间太短")
      mediaRecorderRef.current?.stop()
      return
    }

    recordDurationRef.current = recordDuration
    mediaRecorderRef.current?.stop()
  }

  const handleFileSelect = (type: "image" | "file") => {
    const input = document.createElement("input")
    input.type = "file"
    input.accept = type === "image" ? "image/*" : "*"
    input.multiple = type === "image"

    input.onchange = (event) => {
      const files = (event.target as HTMLInputElement).files
      if (!files?.length) return

      if (type === "image") {
        if (files.length === 1) {
          setSelectedImageSrc(URL.createObjectURL(files[0]))
          setImageEditorOpen(true)
          return
        }

        const count = Math.min(files.length, 9)
        if (files.length > 9) window.alert("最多只能发送 9 张图片")
        Array.from(files).slice(0, count).forEach((file) => {
          const url = URL.createObjectURL(file)
          onSendMessage("图片", "image", { rawFile: file, fileUrl: url, fileName: file.name, fileSize: file.size })
        })
        return
      }

      const file = files[0]
      onSendMessage(file.name, "file", {
        rawFile: file,
        fileName: file.name,
        fileSize: file.size,
        fileUrl: URL.createObjectURL(file),
      })
    }

    input.click()
  }

  const handleEditorSave = (blob: Blob) => {
    const file = new File([blob], `edited-image-${Date.now()}.png`, { type: blob.type || "image/png" })
    const url = URL.createObjectURL(blob)
    onSendMessage("图片", "image", { rawFile: file, fileUrl: url, fileName: file.name, fileSize: file.size })
    setSelectedImageSrc(null)
  }

  const handleCardSelect = () => {
    if (mentionUsers.length === 0) {
      window.alert("当前没有可发送的真实联系人")
      return
    }
    const selectedId = window.prompt("输入要发送的联系人 ID", mentionUsers[0].id)
    const selected = mentionUsers.find((user) => user.id === selectedId)
    if (!selected) {
      window.alert("未找到该联系人")
      return
    }
    onSendMessage("个人名片", "card", {
      cardInfo: {
        userId: selected.id,
        name: selected.name,
        avatar: selected.avatar || "",
        signature: "企业通讯录联系人",
      },
    })
    setShowMore(false)
  }

  const handleLocationShare = async () => {
    const fallback = () => {
      const raw = window.prompt("输入位置：名称,纬度,经度", "当前位置,39.9042,116.4074")
      if (!raw) return
      const [name = "位置", latRaw, lngRaw] = raw.split(",").map((part) => part.trim())
      const latitude = Number(latRaw)
      const longitude = Number(lngRaw)
      if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        window.alert("位置格式错误")
        return
      }
      const locationInfo = { latitude, longitude, name, address: `${latitude.toFixed(6)}, ${longitude.toFixed(6)}` }
      onSendMessage(JSON.stringify(locationInfo), "location", { locationInfo })
      setShowMore(false)
    }

    if (!navigator.geolocation) {
      fallback()
      return
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const { latitude, longitude } = position.coords
        const locationInfo = {
          latitude,
          longitude,
          name: "当前位置",
          address: `${latitude.toFixed(6)}, ${longitude.toFixed(6)}`,
        }
        onSendMessage(JSON.stringify(locationInfo), "location", { locationInfo })
        setShowMore(false)
      },
      fallback,
      { enableHighAccuracy: true, timeout: 8000, maximumAge: 30000 },
    )
  }

  return (
    <div className="relative z-20 flex flex-col border-t bg-background">
      {editingMessage && (
        <div className="flex items-center justify-between border-b bg-muted/50 px-4 py-2 text-sm">
          <div className="flex min-w-0 items-center gap-2 text-muted-foreground">
            <span className="font-medium text-primary">编辑：</span>
            <span className="max-w-52 truncate">{editingMessage.content}</span>
          </div>
          <Button variant="ghost" size="icon" className="h-6 w-6 hover:bg-muted" onClick={() => {
            setMessage("")
            onCancelEdit?.()
          }}>
            <X className="h-4 w-4" />
          </Button>
        </div>
      )}

      {replyingTo && !editingMessage && (
        <div className="flex items-center justify-between border-b bg-muted/50 px-4 py-2 text-sm">
          <div className="flex min-w-0 items-center gap-2 text-muted-foreground">
            <span className="font-medium text-primary">回复：</span>
            <span className="max-w-52 truncate">{replyingTo.content}</span>
          </div>
          <Button variant="ghost" size="icon" className="h-6 w-6 hover:bg-muted" onClick={onCancelReply}>
            <X className="h-4 w-4" />
          </Button>
        </div>
      )}

      {isRecording && (
        <div className="fixed inset-0 z-50 flex select-none flex-col items-center justify-center bg-black/50 text-white" onMouseUp={stopRecording} onTouchEnd={stopRecording}>
          <div className="mb-4 rounded-full bg-primary p-8 animate-pulse">
            <Mic className="h-12 w-12" />
          </div>
          <p className="text-lg font-medium">正在录音... {recordDuration}s</p>
          <p className="mt-2 text-sm opacity-70">松手发送</p>
        </div>
      )}

      <div className="flex items-end gap-2 p-3 pb-safe">
        <Button variant="ghost" size="icon" className="mb-0.5 shrink-0 text-muted-foreground" onClick={() => setMode(mode === "text" ? "voice" : "text")}>
          {mode === "text" ? <Mic className="h-6 w-6" /> : <Keyboard className="h-6 w-6" />}
        </Button>

        <div className="flex min-h-10 flex-1 items-center">
          <Popover open={mentionOpen} onOpenChange={setMentionOpen}>
            <PopoverTrigger asChild>
              <span className="hidden">@</span>
            </PopoverTrigger>
            <PopoverContent className="w-60 p-0" align="start" side="top">
              <div className="border-b p-2 text-xs font-medium text-muted-foreground">选择提醒的人</div>
              <div className="max-h-48 overflow-y-auto">
                {mentionUsers.map((user) => (
                  <div key={user.id} className="flex cursor-pointer items-center gap-2 p-2 text-sm hover:bg-muted/50" onClick={() => handleMentionSelect(user)}>
                    <div className="flex h-6 w-6 items-center justify-center overflow-hidden rounded-full bg-muted text-[10px]">
                      {user.avatar ? <img src={user.avatar} className="h-full w-full object-cover" alt="" /> : user.name[0]}
                    </div>
                    <span>{user.name}</span>
                  </div>
                ))}
                {mentionUsers.length === 0 && <div className="p-4 text-center text-xs text-muted-foreground">暂无成员</div>}
              </div>
            </PopoverContent>
          </Popover>

          {mode === "text" ? (
            <Input
              ref={inputRef}
              value={message}
              onChange={handleInputChange}
              onKeyDown={handleKeyDown}
              placeholder="发送消息..."
              className="min-h-10 border-none bg-muted/50 py-2 focus-visible:ring-1 focus-visible:ring-primary/50"
            />
          ) : (
            <Button className={cn("flex-1", isRecording && "bg-destructive text-destructive-foreground animate-pulse")} onMouseDown={startRecording} onMouseUp={stopRecording} onTouchStart={startRecording} onTouchEnd={stopRecording}>
              {isRecording ? `松开发送 (${recordDuration}s)` : "按住说话"}
            </Button>
          )}
        </div>

        <div className="mb-0.5 flex items-center gap-1">
          {isGroup && mentionUsers.length > 0 && (
            <Button variant="ghost" size="icon" className="shrink-0 text-muted-foreground" onClick={() => setMentionOpen(true)}>
              <AtSign className="h-5 w-5" />
            </Button>
          )}
          <Popover>
            <PopoverTrigger asChild>
              <Button variant="ghost" size="icon" className="shrink-0 text-muted-foreground">
                <Smile className="h-6 w-6" />
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-80 p-2" align="end" side="top">
              <div className="grid h-48 grid-cols-8 gap-1 overflow-y-auto">
                {emojis.map((emoji) => (
                  <button key={emoji} className="rounded p-1 text-xl transition-colors hover:bg-muted" onClick={() => setMessage((prev) => prev + emoji)}>
                    {emoji}
                  </button>
                ))}
              </div>
            </PopoverContent>
          </Popover>

          {message.trim() ? (
            <Button onClick={handleSend} size="icon" className="shrink-0 bg-primary text-primary-foreground hover:bg-primary/90">
              <Send className="h-4 w-4" />
            </Button>
          ) : (
            <Button variant="ghost" size="icon" className={cn("shrink-0 text-muted-foreground", showMore && "bg-muted text-foreground")} onClick={() => setShowMore(!showMore)}>
              {showMore ? <X className="h-6 w-6" /> : <Plus className="h-6 w-6" />}
            </Button>
          )}
        </div>
      </div>

      {showMore && (
        <div className="grid grid-cols-4 gap-4 border-t bg-muted/30 p-4 animate-in slide-in-from-bottom-10 duration-200">
          <PanelItem icon={ImageIcon} label="相册" onClick={() => handleFileSelect("image")} />
          <PanelItem icon={Camera} label="拍摄" onClick={() => handleFileSelect("image")} />
          <PanelItem icon={FileText} label="文件" onClick={() => handleFileSelect("file")} />
          <PanelItem icon={UserIcon} label="名片" onClick={handleCardSelect} />
          <PanelItem icon={MapPin} label="位置" onClick={handleLocationShare} />
          <PanelItem icon={Scissors} label="截屏" onClick={() => {
            onScreenshot?.()
            setShowMore(false)
          }} />
        </div>
      )}

      {selectedImageSrc && (
        <ImageEditor open={imageEditorOpen} onOpenChange={setImageEditorOpen} imageSrc={selectedImageSrc} onSave={handleEditorSave} />
      )}
    </div>
  )
})

function PanelItem({ icon: Icon, label, onClick }: { icon: any; label: string; onClick: () => void }) {
  return (
    <div className="group flex cursor-pointer flex-col items-center gap-2" onClick={onClick}>
      <div className="flex h-12 w-12 items-center justify-center rounded-xl border bg-background shadow-sm transition-colors group-hover:bg-muted">
        <Icon className="h-6 w-6 text-muted-foreground" />
      </div>
      <span className="text-xs text-muted-foreground">{label}</span>
    </div>
  )
}
