import { format } from "date-fns"
import {
  AlertCircle,
  Check,
  CheckCheck,
  Copy,
  Edit,
  Flame,
  Forward,
  Globe,
  ListChecks,
  MapPin,
  Navigation,
  Reply,
  Star,
  ThumbsUp,
  Trash2,
  Undo2,
} from "lucide-react"
import { useEffect, useRef, useState } from "react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Checkbox } from "@/components/ui/checkbox"
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuSeparator,
  ContextMenuTrigger,
} from "@/components/ui/context-menu"
import { cn } from "@/lib/utils"
import type { Message, User } from "@/types"
import { fetchLinkPreviewApi, type LinkPreviewDto } from "@/services/api"
import { useAuthStore } from "@/stores/useAuthStore"
import { CardMessage } from "./MessageTypes/CardMessage"
import { FileMessage } from "./MessageTypes/FileMessage"
import { VideoMessage } from "./MessageTypes/VideoMessage"
import { VoiceMessage } from "./MessageTypes/VoiceMessage"

interface MessageBubbleProps {
  message: Message
  isMe: boolean
  sender?: User
  onPreviewImage?: (url: string) => void
  isMultiSelectMode?: boolean
  isSelected?: boolean
  onSelect?: (checked: boolean) => void
  onDelete?: () => void
  onRecall?: () => void
  onForward?: () => void
  onFavorite?: () => void
  onEnterMultiSelect?: () => void
  onReply?: () => void
  onEdit?: () => void
  onLike?: () => void
  onAvatarClick?: (userId: string) => void
  onReadStatusClick?: () => void
  onFileTransferProgress?: (progress: number, status: string) => void
  quotedMessage?: Message
  isGroup?: boolean
  currentUserName?: string
  onResend?: () => void
}

const typeLabel: Partial<Record<Message["type"], string>> = {
  image: "图片",
  voice: "语音",
  file: "文件",
  card: "名片",
  video: "视频",
  location: "位置",
}

export function MessageBubble({
  message,
  isMe,
  isGroup: propIsGroup,
  sender,
  onPreviewImage,
  isMultiSelectMode,
  isSelected,
  onSelect,
  onDelete,
  onRecall,
  onForward,
  onFavorite,
  onEnterMultiSelect,
  onReply,
  onEdit,
  onLike,
  onAvatarClick,
  onReadStatusClick,
  onFileTransferProgress,
  quotedMessage,
  currentUserName,
  onResend,
}: MessageBubbleProps) {
  const isGroup = propIsGroup ?? message.sessionId.includes("group")
  const [isRevealed, setIsRevealed] = useState(false)
  const [isBurned, setIsBurned] = useState(false)
  const [burnCountdown, setBurnCountdown] = useState(10)
  const burnIntervalRef = useRef<NodeJS.Timeout | null>(null)

  useEffect(() => {
    return () => {
      if (burnIntervalRef.current) {
        clearInterval(burnIntervalRef.current)
      }
    }
  }, [])

  if (message.type === "system" || message.isRecall) {
    return (
      <div className="my-2 flex w-full justify-center">
        <span className="select-none rounded-full bg-muted/50 px-3 py-1 text-xs text-muted-foreground">
          {message.content}
        </span>
      </div>
    )
  }

  const handleReveal = () => {
    if (!message.isReadAfterBurn || isRevealed || isBurned || isMe) return

    setIsRevealed(true)
    setBurnCountdown(10)
    burnIntervalRef.current = setInterval(() => {
      setBurnCountdown((prev) => {
        if (prev <= 1) {
          if (burnIntervalRef.current) {
            clearInterval(burnIntervalRef.current)
            burnIntervalRef.current = null
          }
          setIsBurned(true)
          onRecall?.()
          return 0
        }
        return prev - 1
      })
    }, 1000)
  }

  const handleCopy = () => {
    if (message.type === "text" && !message.isReadAfterBurn) {
      navigator.clipboard.writeText(message.content)
    }
  }

  const renderQuote = () => {
    if (!quotedMessage) return null

    const content =
      quotedMessage.type === "text"
        ? quotedMessage.content
        : `[${typeLabel[quotedMessage.type] ?? "消息"}]`

    return (
      <div className="mb-2 select-none rounded border-l-2 border-primary/50 bg-muted/40 p-2 text-xs text-muted-foreground">
        <div className="mb-0.5 font-medium opacity-70">回复消息</div>
        <div className="line-clamp-2 italic">{content}</div>
      </div>
    )
  }

  const extractUrls = (text: string): string[] => {
    const urlRegex = /(https?:\/\/[^\s]+)/g
    return text.match(urlRegex) || []
  }

  const LinkPreviewCard = ({ url }: { url: string }) => {
    const token = useAuthStore((s) => s.token)
    const [preview, setPreview] = useState<LinkPreviewDto | null>(null)
    const [fetchFailed, setFetchFailed] = useState(false)
    let domain = ""
    try { domain = new URL(url).hostname } catch { domain = url.slice(0, 30) }

    useEffect(() => {
      if (!token) return
      let cancelled = false
      fetchLinkPreviewApi(url, token)
        .then((data) => { if (!cancelled) setPreview(data) })
        .catch(() => { if (!cancelled) setFetchFailed(true) })
      return () => { cancelled = true }
    }, [url, token])

    const title = preview?.title || domain
    const desc = preview?.description || url
    const imageUrl = preview?.imageUrl
    const site = preview?.siteName

    return (
      <a
        href={url}
        target="_blank"
        rel="noopener noreferrer"
        className="mt-2 block rounded-lg border bg-muted/30 transition-colors hover:bg-muted/50 overflow-hidden"
        onClick={(event) => event.stopPropagation()}
      >
        {imageUrl && !fetchFailed && (
          <img src={imageUrl} alt="" className="w-full h-32 object-cover" loading="lazy" />
        )}
        <div className="flex items-start gap-3 p-3">
          {!imageUrl && (
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded bg-primary/10 text-primary">
              <Globe className="h-5 w-5" />
            </div>
          )}
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{title}</p>
            {site && <p className="text-xs text-muted-foreground">{site}</p>}
            <p className="truncate text-xs text-muted-foreground">{desc}</p>
          </div>
        </div>
      </a>
    )
  }

  const parseLocation = () => {
    if (message.locationInfo) return message.locationInfo
    try {
      const parsed = JSON.parse(message.content) as Message["locationInfo"]
      if (parsed && Number.isFinite(parsed.latitude) && Number.isFinite(parsed.longitude)) {
        return parsed
      }
    } catch {
      return undefined
    }
    return undefined
  }

  const LocationMessage = () => {
    const location = parseLocation()
    if (!location) return <p className="text-sm">{message.content}</p>
    const mapUrl = `https://www.google.com/maps?q=${encodeURIComponent(`${location.latitude},${location.longitude}`)}`
    return (
      <a
        href={mapUrl}
        target="_blank"
        rel="noopener noreferrer"
        className="flex min-w-56 max-w-72 items-center gap-3 rounded-lg border bg-background/80 p-3 text-foreground transition-colors hover:bg-muted/70"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded bg-primary/10 text-primary">
          <MapPin className="h-6 w-6" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium">{location.name || "位置"}</p>
          <p className="truncate text-xs text-muted-foreground">{location.address || `${location.latitude.toFixed(6)}, ${location.longitude.toFixed(6)}`}</p>
        </div>
        <Navigation className="h-4 w-4 shrink-0 text-muted-foreground" />
      </a>
    )
  }

  const formatText = (text: string) => {
    const urlRegex = /(https?:\/\/[^\s]+)/g
    const phoneRegex = /(1[3-9]\d{9})/g
    const mentionRegex = /(@[\u4e00-\u9fa5\w.-]+)/g

    return text.split(urlRegex).map((part, index) => {
      if (part.match(urlRegex)) {
        return (
          <a
            key={index}
            href={part}
            target="_blank"
            rel="noopener noreferrer"
            className="break-all text-blue-500 hover:underline"
            onClick={(event) => event.stopPropagation()}
          >
            {part}
          </a>
        )
      }

      return part.split(phoneRegex).map((subPart, subIndex) => {
        if (subPart.match(phoneRegex)) {
          return (
            <a
              key={`${index}-${subIndex}`}
              href={`tel:${subPart}`}
              className="text-blue-500 hover:underline"
              onClick={(event) => event.stopPropagation()}
            >
              {subPart}
            </a>
          )
        }

        return subPart.split(mentionRegex).map((mentionPart, mentionIndex) => {
          if (mentionPart.match(mentionRegex)) {
            const mentionName = mentionPart.slice(1) // remove @
            const isMeMention = currentUserName && (mentionName === currentUserName || mentionName === "所有人")
            return (
              <span
                key={`${index}-${subIndex}-${mentionIndex}`}
                className={cn(
                  "cursor-pointer hover:underline",
                  isMeMention ? "rounded bg-blue-100 px-0.5 font-semibold text-blue-700" : "text-blue-500"
                )}
                onClick={(event) => event.stopPropagation()}
              >
                {mentionPart}
              </span>
            )
          }
          return mentionPart
        })
      })
    })
  }

  const renderContent = () => {
    if (message.isReadAfterBurn) {
      if (isBurned) {
        return (
          <div className="flex items-center gap-2 italic text-muted-foreground">
            <Flame className="h-4 w-4" />
            消息已焚毁
          </div>
        )
      }

      if (!isRevealed && !isMe) {
        return (
          <button className="flex items-center gap-2" onClick={handleReveal}>
            <Flame className="h-4 w-4 text-orange-500" />
            <span className="font-medium text-orange-500">点击查看（阅后即焚）</span>
          </button>
        )
      }

      if (isRevealed && !isBurned && !isMe) {
        const content =
          message.type === "text" ? message.content : `[${typeLabel[message.type] ?? "消息"}]`
        return (
          <div className="relative">
            <p className="whitespace-pre-wrap break-words text-sm">{content}</p>
            <div className="absolute -right-1 -top-1 flex items-center gap-1 rounded-full bg-orange-500 px-1.5 py-0.5 text-xs text-white">
              <Flame className="h-3 w-3" />
              <span>{burnCountdown}s</span>
            </div>
          </div>
        )
      }
    }

    switch (message.type) {
      case "text": {
        const urls = extractUrls(message.content)
        const hasUrl = urls.length > 0
        return (
          <div>
            <p className="whitespace-pre-wrap break-words text-sm">{formatText(message.content)}</p>
            {hasUrl && urls.slice(0, 1).map((url) => <LinkPreviewCard key={url} url={url} />)}
          </div>
        )
      }
      case "image":
        return (
          <div
            className="cursor-pointer overflow-hidden rounded-lg"
            onClick={() => onPreviewImage?.(message.fileUrl || "")}
          >
            <img
              src={message.fileUrl}
              alt="图片"
              className="max-h-64 max-w-full object-cover"
              loading="lazy"
            />
          </div>
        )
      case "voice":
        return <VoiceMessage url={message.fileUrl} duration={message.voiceDuration} isMe={isMe} />
      case "file":
        return <FileMessage fileId={message.fileId} name={message.fileName} size={message.fileSize} url={message.fileUrl} isMe={isMe} onTransferProgress={onFileTransferProgress} />
      case "card":
        return <CardMessage {...message.cardInfo!} />
      case "video":
        return <VideoMessage url={message.fileUrl} thumbnail={message.videoThumbnail} fileName={message.fileName} fileSize={message.fileSize} isMe={isMe} />
      case "location":
        return <LocationMessage />
      default:
        return <p className="text-sm">不支持的消息类型</p>
    }
  }

  return (
    <div
      className={cn(
        "group relative mb-4 flex w-full gap-2",
        isMe ? "flex-row-reverse" : "flex-row",
        isSelected && "-mx-4 bg-muted/30 px-4 py-2",
      )}
    >
      {isMultiSelectMode && (
        <div className="flex items-center justify-center px-2">
          <Checkbox checked={isSelected} onCheckedChange={(checked) => onSelect?.(!!checked)} />
        </div>
      )}

      <Avatar
        className="h-9 w-9 shrink-0 cursor-pointer transition-opacity hover:opacity-80"
        onClick={(event) => {
          event.stopPropagation()
          onAvatarClick?.(sender?.id || "")
        }}
      >
        <AvatarImage src={sender?.avatar} alt={sender?.name} />
        <AvatarFallback>{sender?.name?.slice(0, 2)}</AvatarFallback>
      </Avatar>

      <div className={cn("flex max-w-[70%] flex-col", isMe ? "items-end" : "items-start")}>
        {!isMe && isGroup && (
          <span className="mb-1 ml-1 text-xs text-muted-foreground">{sender?.name}</span>
        )}

        <ContextMenu>
          <ContextMenuTrigger>
            <div
              className={cn(
                "relative rounded-2xl px-4 py-2.5 shadow-sm transition-all",
                isMe
                  ? "rounded-tr-sm bg-primary text-primary-foreground"
                  : "rounded-tl-sm border bg-white text-foreground dark:bg-muted",
                message.type === "image" && "border-0 bg-transparent p-1 shadow-none",
              )}
            >
              {renderQuote()}
              {renderContent()}

              {(message.likes?.length || 0) > 0 && (
                <div className="mt-1 flex items-center gap-1 border-t border-border/50 pt-1">
                  <ThumbsUp className="h-3 w-3 fill-primary text-primary" />
                  <span className="text-[10px] text-muted-foreground">{message.likes?.length}</span>
                </div>
              )}

              {message.isReadAfterBurn && !isBurned && (
                <div className="absolute -right-1 -top-1">
                  <Flame className="h-3 w-3 fill-orange-500 text-orange-500" />
                </div>
              )}
            </div>
          </ContextMenuTrigger>

          <ContextMenuContent className="w-48">
            {message.type === "text" && (
              <ContextMenuItem onClick={handleCopy}>
                <Copy className="mr-2 h-4 w-4" />
                复制
              </ContextMenuItem>
            )}
            <ContextMenuItem onClick={onReply}>
              <Reply className="mr-2 h-4 w-4" />
              引用
            </ContextMenuItem>
            <ContextMenuItem onClick={onForward}>
              <Forward className="mr-2 h-4 w-4" />
              转发
            </ContextMenuItem>
            <ContextMenuItem onClick={onFavorite}>
              <Star className="mr-2 h-4 w-4" />
              收藏
            </ContextMenuItem>
            {isMe && message.type === "text" && (
              <ContextMenuItem onClick={onEdit}>
                <Edit className="mr-2 h-4 w-4" />
                编辑
              </ContextMenuItem>
            )}
            <ContextMenuItem onClick={onLike}>
              <ThumbsUp className="mr-2 h-4 w-4" />
              点赞
            </ContextMenuItem>
            <ContextMenuItem onClick={onEnterMultiSelect}>
              <ListChecks className="mr-2 h-4 w-4" />
              多选
            </ContextMenuItem>
            <ContextMenuSeparator />
            {isMe && Date.now() - message.timestamp < 3 * 60 * 1000 && (
              <ContextMenuItem onClick={onRecall} className="text-orange-500 focus:text-orange-500">
                <Undo2 className="mr-2 h-4 w-4" />
                撤回
              </ContextMenuItem>
            )}
            <ContextMenuItem onClick={onDelete} className="text-red-500 focus:text-red-500">
              <Trash2 className="mr-2 h-4 w-4" />
              删除
            </ContextMenuItem>
          </ContextMenuContent>
        </ContextMenu>

        <div className={cn("mt-1 flex items-center gap-1 text-[10px] text-muted-foreground", isMe ? "justify-end" : "justify-start")}>
          {message.isEdited && <span className="text-[10px] text-muted-foreground/70">（已编辑）</span>}
          <span>{format(message.timestamp, "HH:mm")}</span>
          {isMe && !isGroup && (
            message.status === "read" ? <CheckCheck className="h-3 w-3 text-primary" /> : <Check className="h-3 w-3" />
          )}
          {isMe && isGroup && (
            <button
              className={cn(
                "cursor-pointer text-[10px] hover:underline",
                (message.readBy?.length || 0) > 0 ? "text-primary" : "text-muted-foreground",
              )}
              onClick={(event) => {
                event.stopPropagation()
                onReadStatusClick?.()
              }}
            >
              {(message.readBy?.length || 0) > 0 ? `${message.readBy?.length}人已读` : "未读"}
            </button>
          )}
        </div>
      </div>

      {isMe && message.status === "failed" && (
        <button
          className="flex cursor-pointer items-center justify-center rounded-full p-1 text-destructive transition-colors hover:bg-destructive/10"
          title="发送失败，点击重发"
          onClick={(event) => {
            event.stopPropagation()
            if (confirm("确认重发此消息？")) {
              onResend?.()
            }
          }}
        >
          <AlertCircle className="h-5 w-5" />
        </button>
      )}
    </div>
  )
}
