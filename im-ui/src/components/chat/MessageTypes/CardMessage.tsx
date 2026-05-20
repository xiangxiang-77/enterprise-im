import { User as UserIcon } from "lucide-react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"

interface CardMessageProps {
  userId: string
  name: string
  avatar: string
  signature?: string
  isMe?: boolean
}

export function CardMessage({ name, avatar, signature }: CardMessageProps) {
  return (
    <div className="w-[200px]">
      <div className="mb-2 flex items-center gap-3 border-b border-white/20 pb-3">
        <Avatar className="h-10 w-10">
          <AvatarImage src={avatar} />
          <AvatarFallback>{name[0]}</AvatarFallback>
        </Avatar>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium">{name}</p>
          <p className="truncate text-xs opacity-70">{signature || "暂无签名"}</p>
        </div>
      </div>
      <div className="flex items-center gap-1 text-xs opacity-60">
        <UserIcon className="h-3 w-3" />
        个人名片
      </div>
    </div>
  )
}
