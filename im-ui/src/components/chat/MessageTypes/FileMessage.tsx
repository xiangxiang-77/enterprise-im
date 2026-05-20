import { Download, FileText, Loader2 } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"

interface FileMessageProps {
  name?: string
  size?: number
  url?: string
  isMe: boolean
}

function formatSize(bytes: number = 0) {
  if (bytes === 0) return "0 B"
  const k = 1024
  const sizes = ["B", "KB", "MB", "GB", "TB"]
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`
}

export function FileMessage({ name = "未知文件", size = 0, url }: FileMessageProps) {
  const [downloading, setDownloading] = useState(false)
  const [progress, setProgress] = useState(0)

  const handleDownload = () => {
    if (!url) return

    setDownloading(true)
    let currentProgress = 0
    const interval = setInterval(() => {
      currentProgress += 10
      setProgress(currentProgress)
      if (currentProgress < 100) return

      clearInterval(interval)
      setDownloading(false)

      const link = document.createElement("a")
      link.href = url
      link.download = name
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
    }, 100)
  }

  return (
    <div className="flex min-w-[200px] items-center gap-3 p-1">
      <div className="rounded-lg bg-secondary/50 p-3">
        <FileText className="h-8 w-8" />
      </div>
      <div className="min-w-0 flex-1">
        <p className="max-w-[150px] truncate text-sm font-medium">{name}</p>
        <div className="flex items-center gap-2">
          <p className="text-xs opacity-70">{formatSize(size)}</p>
          {downloading && <p className="text-xs text-primary">{progress}%</p>}
        </div>
      </div>
      {url && (
        <Button variant="ghost" size="icon" className="h-8 w-8" onClick={handleDownload} disabled={downloading}>
          {downloading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
        </Button>
      )}
    </div>
  )
}
