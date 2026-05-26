import { Download, File, FileArchive, FileImage, FileSpreadsheet, FileText, FileType, Loader2 } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { API_BASE_URL } from "@/services/api"
import { useAuthStore } from "@/stores/useAuthStore"

interface FileMessageProps {
  fileId?: string
  name?: string
  size?: number
  url?: string
  isMe: boolean
  onTransferProgress?: (progress: number, status: string) => void
}

function getFileIcon(name: string) {
  const ext = name.split(".").pop()?.toLowerCase() || ""
  if (ext === "pdf") return { icon: FileText, color: "text-red-500", bg: "bg-red-50" }
  if (["doc", "docx"].includes(ext)) return { icon: FileText, color: "text-blue-500", bg: "bg-blue-50" }
  if (["xls", "xlsx", "csv"].includes(ext)) return { icon: FileSpreadsheet, color: "text-green-500", bg: "bg-green-50" }
  if (["ppt", "pptx"].includes(ext)) return { icon: FileType, color: "text-orange-500", bg: "bg-orange-50" }
  if (["zip", "rar", "7z", "tar", "gz"].includes(ext)) return { icon: FileArchive, color: "text-purple-500", bg: "bg-purple-50" }
  if (["jpg", "jpeg", "png", "gif", "bmp", "webp", "svg"].includes(ext)) return { icon: FileImage, color: "text-pink-500", bg: "bg-pink-50" }
  return { icon: File, color: "text-gray-500", bg: "bg-gray-50" }
}

function formatSize(bytes: number = 0) {
  if (bytes === 0) return "0 B"
  const k = 1024
  const sizes = ["B", "KB", "MB", "GB", "TB"]
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`
}

export function FileMessage({ fileId, name = "unknown file", size = 0, url, onTransferProgress }: FileMessageProps) {
  const { token } = useAuthStore()
  const [downloading, setDownloading] = useState(false)
  const [progress, setProgress] = useState(0)
  const { icon: FileIcon, color, bg } = getFileIcon(name)

  const handleDownload = () => {
    if (!url && !fileId) return

    setDownloading(true)
    setProgress(0)
    onTransferProgress?.(0, "started")

    const source = fileId ? `${API_BASE_URL}/api/files/${encodeURIComponent(fileId)}/download` : url!
    const xhr = new XMLHttpRequest()
    xhr.open("GET", source)
    if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`)
    xhr.responseType = "blob"
    xhr.onprogress = (event) => {
      if (!event.lengthComputable) return
      const currentProgress = Math.max(1, Math.min(99, Math.round((event.loaded / event.total) * 100)))
      setProgress(currentProgress)
      onTransferProgress?.(currentProgress, "transferring")
    }
    xhr.onload = () => {
      setDownloading(false)
      if (xhr.status < 200 || xhr.status >= 300) {
        onTransferProgress?.(progress, "failed")
        return
      }
      setProgress(100)
      onTransferProgress?.(100, "completed")
      const blobUrl = URL.createObjectURL(xhr.response)
      const link = document.createElement("a")
      link.href = blobUrl
      link.download = name
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      setTimeout(() => URL.revokeObjectURL(blobUrl), 30000)
    }
    xhr.onerror = () => {
      setDownloading(false)
      onTransferProgress?.(progress, "failed")
    }
    xhr.send()
  }

  return (
    <div className="flex min-w-[220px] items-center gap-3 p-1">
      <div className={`rounded-lg p-3 ${bg}`}>
        <FileIcon className={`h-8 w-8 ${color}`} />
      </div>
      <div className="min-w-0 flex-1">
        <p className="max-w-[150px] truncate text-sm font-medium">{name}</p>
        <div className="flex items-center gap-2">
          <p className="text-xs opacity-70">{formatSize(size)}</p>
          {downloading && <p className="text-xs text-primary">{progress}%</p>}
        </div>
        {downloading && (
          <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-muted">
            <div className="h-full bg-primary transition-all" style={{ width: `${progress}%` }} />
          </div>
        )}
      </div>
      {(url || fileId) && (
        <Button variant="ghost" size="icon" className="h-8 w-8" onClick={handleDownload} disabled={downloading}>
          {downloading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
        </Button>
      )}
    </div>
  )
}
