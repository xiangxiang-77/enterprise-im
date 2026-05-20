import { useState, useRef, useEffect } from "react"
import { Play, Pause } from "lucide-react"
import { Button } from "@/components/ui/button"

interface VoiceMessageProps {
  url?: string
  duration?: number // seconds
  isMe: boolean
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
      audioRef.current.play().catch(e => console.error("Play error:", e))
    }
    setIsPlaying(!isPlaying)
  }

  return (
    <div className={`flex items-center gap-2 min-w-[100px] ${isMe ? "flex-row-reverse" : "flex-row"}`}>
      <Button
        variant="ghost"
        size="icon"
        className={`h-8 w-8 rounded-full ${isMe ? "bg-primary-foreground/20 hover:bg-primary-foreground/30" : "bg-muted/20 hover:bg-muted/30"}`}
        onClick={togglePlay}
      >
        {isPlaying ? <Pause className="h-4 w-4" /> : <Play className="h-4 w-4" />}
      </Button>
      
      <div className="flex flex-col gap-1 w-full max-w-[150px]">
         <div className="h-1 bg-gray-300 rounded-full w-full overflow-hidden">
             <div 
               className={`h-full ${isMe ? "bg-white" : "bg-primary"} transition-all duration-200`}
               style={{ width: `${progress}%` }}
             />
         </div>
         <span className="text-xs opacity-70">{duration}s</span>
      </div>
    </div>
  )
}
