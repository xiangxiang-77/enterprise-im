import { useState, useRef } from "react"
import { Play, Pause, Download } from "lucide-react"

interface VideoMessageProps {
  url?: string
  thumbnail?: string
  fileName?: string
  fileSize?: number
  isMe: boolean
}

function formatSize(bytes?: number): string {
  if (!bytes) return ""
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function VideoMessage({ url, thumbnail, fileName, fileSize, isMe }: VideoMessageProps) {
  const [isPlaying, setIsPlaying] = useState(false)
  const [showControls, setShowControls] = useState(false)
  const videoRef = useRef<HTMLVideoElement | null>(null)

  const togglePlay = () => {
    if (!videoRef.current) return
    if (isPlaying) {
      videoRef.current.pause()
    } else {
      videoRef.current.play().catch(() => {})
    }
    setIsPlaying(!isPlaying)
  }

  if (!url) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Play className="h-4 w-4" />
        <span>视频不可用</span>
      </div>
    )
  }

  return (
    <div
      className="group/video relative cursor-pointer overflow-hidden rounded-lg"
      onClick={togglePlay}
      onMouseEnter={() => setShowControls(true)}
      onMouseLeave={() => setShowControls(false)}
    >
      {isPlaying ? (
        <video
          ref={videoRef}
          src={url}
          className="max-h-64 max-w-full rounded-lg object-contain"
          onEnded={() => setIsPlaying(false)}
          playsInline
          controls={showControls}
        />
      ) : (
        <div className="relative">
          {thumbnail ? (
            <img
              src={thumbnail}
              alt={fileName || "视频"}
              className="max-h-64 max-w-full rounded-lg object-cover"
              loading="lazy"
            />
          ) : (
            <div className="flex h-40 w-56 items-center justify-center rounded-lg bg-black/10 dark:bg-white/10">
              <Play className="h-10 w-10 text-muted-foreground/60" />
            </div>
          )}
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-black/50 backdrop-blur-sm transition-transform group-hover/video:scale-110">
              <Play className="h-6 w-6 text-white" fill="white" />
            </div>
          </div>
          {fileSize && (
            <div className="absolute bottom-2 right-2 rounded bg-black/60 px-1.5 py-0.5 text-[10px] text-white">
              {formatSize(fileSize)}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
