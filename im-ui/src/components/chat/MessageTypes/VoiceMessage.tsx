import { useState, useRef, useEffect } from "react"
import { Play, Pause } from "lucide-react"
import { Button } from "@/components/ui/button"

interface VoiceMessageProps {
  url?: string
  duration?: number // seconds
  isMe: boolean
}

function SoundWave({ isPlaying, isMe }: { isPlaying: boolean; isMe: boolean }) {
  const bars = [0.4, 0.7, 1, 0.6, 0.9, 0.5, 0.8, 0.3, 0.7, 0.5, 0.9, 0.6]
  return (
    <div className="flex h-5 items-end gap-[2px]">
      {bars.map((height, i) => (
        <div
          key={i}
          className={`w-[3px] rounded-full ${isMe ? "bg-primary-foreground/60" : "bg-primary/60"}`}
          style={{
            height: `${height * 20}px`,
            animation: isPlaying ? `soundWave 0.8s ease-in-out ${i * 0.05}s infinite alternate` : "none",
            opacity: isPlaying ? 1 : 0.4,
          }}
        />
      ))}
      <style>{`
        @keyframes soundWave {
          0% { transform: scaleY(0.3); }
          100% { transform: scaleY(1); }
        }
      `}</style>
    </div>
  )
}

export function VoiceMessage({ url, duration = 0, isMe }: VoiceMessageProps) {
  const [isPlaying, setIsPlaying] = useState(false)
  const [progress, setProgress] = useState(0)
  const audioRef = useRef<HTMLAudioElement | null>(null)

  useEffect(() => {
    if (!url) return
    audioRef.current = new Audio(url)

    const audio = audioRef.current

    audio.addEventListener("ended", () => {
      setIsPlaying(false)
      setProgress(0)
    })

    audio.addEventListener("timeupdate", () => {
      if (audio.duration) {
        setProgress((audio.currentTime / audio.duration) * 100)
      }
    })

    return () => {
      audio.pause()
      audio.src = ""
    }
  }, [url])

  const togglePlay = () => {
    if (!audioRef.current) return

    if (isPlaying) {
      audioRef.current.pause()
    } else {
      audioRef.current.play().catch((e) => console.error("Play error:", e))
    }
    setIsPlaying(!isPlaying)
  }

  return (
    <div className={`flex items-center gap-2 min-w-[120px] max-w-[200px] ${isMe ? "flex-row-reverse" : "flex-row"}`}>
      <Button
        variant="ghost"
        size="icon"
        className={`h-8 w-8 shrink-0 rounded-full ${isMe ? "bg-primary-foreground/20 hover:bg-primary-foreground/30" : "bg-muted/20 hover:bg-muted/30"}`}
        onClick={togglePlay}
      >
        {isPlaying ? <Pause className="h-4 w-4" /> : <Play className="h-4 w-4" />}
      </Button>

      <div className="flex flex-1 flex-col gap-1">
        <SoundWave isPlaying={isPlaying} isMe={isMe} />
        <div className="flex items-center justify-between">
          <span className="text-xs opacity-70">{duration}s</span>
          <div className="h-1 w-16 overflow-hidden rounded-full bg-current/20">
            <div
              className={`h-full transition-all duration-200 ${isMe ? "bg-primary-foreground/50" : "bg-primary/50"}`}
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>
      </div>
    </div>
  )
}
