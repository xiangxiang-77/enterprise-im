import { useEffect, useMemo, useState } from "react"
import { useNavigate } from "react-router-dom"
import { ChevronLeft, FileArchive, File, FileSpreadsheet, FileText, FileType, Image as ImageIcon, MoreHorizontal, Music, RefreshCw, Search, Video } from "lucide-react"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Input } from "@/components/ui/input"
import { fetchFileBlobApi, fetchFileOcrApi, fetchFilesApi, type FileRecord } from "@/services/api"
import { useAuthStore } from "@/stores/useAuthStore"

function fileKind(file: FileRecord) {
  const contentType = file.contentType || ""
  if (contentType.startsWith("image/")) return "image"
  if (contentType.startsWith("video/")) return "video"
  if (contentType.startsWith("audio/")) return "audio"
  return "doc"
}

function canOcr(file: FileRecord) {
  const contentType = file.contentType || ""
  const ext = file.originalName.split(".").pop()?.toLowerCase() || ""
  return contentType.startsWith("image/") || contentType === "application/pdf" || ["pdf", "png", "jpg", "jpeg", "gif", "webp", "bmp"].includes(ext)
}

function formatSize(bytes: number) {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${Math.ceil(bytes / 1024)} KB`
  return `${bytes} B`
}

function formatDate(value?: string) {
  if (!value) return "-"
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return "-"
  return `${String(date.getMonth() + 1).padStart(2, "0")}/${String(date.getDate()).padStart(2, "0")}`
}

export default function FileManager() {
  const navigate = useNavigate()
  const { user, token } = useAuthStore()
  const [files, setFiles] = useState<FileRecord[]>([])
  const [query, setQuery] = useState("")
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  const loadFiles = async () => {
    if (!user?.id || !token) return
    setLoading(true)
    setError("")
    try {
      setFiles(await fetchFilesApi(user.id, token))
    } catch (err) {
      setError(err instanceof Error ? err.message : "load failed")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadFiles()
  }, [user?.id, token])

  const filteredFiles = useMemo(() => (
    files.filter((file) => file.originalName.toLowerCase().includes(query.toLowerCase()))
  ), [files, query])

  const getIcon = (file: FileRecord) => {
    const ext = file.originalName.split(".").pop()?.toLowerCase() || ""
    const kind = fileKind(file)
    if (kind === "image") return <ImageIcon className="h-8 w-8 text-pink-500" />
    if (kind === "video") return <Video className="h-8 w-8 text-purple-500" />
    if (kind === "audio") return <Music className="h-8 w-8 text-orange-500" />
    if (ext === "pdf") return <FileText className="h-8 w-8 text-red-500" />
    if (["doc", "docx"].includes(ext)) return <FileText className="h-8 w-8 text-blue-500" />
    if (["xls", "xlsx", "csv"].includes(ext)) return <FileSpreadsheet className="h-8 w-8 text-green-500" />
    if (["ppt", "pptx"].includes(ext)) return <FileType className="h-8 w-8 text-orange-500" />
    if (["zip", "rar", "7z", "tar", "gz"].includes(ext)) return <FileArchive className="h-8 w-8 text-purple-500" />
    return <File className="h-8 w-8 text-gray-500" />
  }

  const openFile = async (file: FileRecord, mode: "preview" | "download") => {
    if (!token) return
    setError("")
    try {
      const blob = await fetchFileBlobApi(file.id, mode, token)
      const url = URL.createObjectURL(blob)
      if (mode === "download") {
        const anchor = document.createElement("a")
        anchor.href = url
        anchor.download = file.originalName
        anchor.click()
        setTimeout(() => URL.revokeObjectURL(url), 30000)
        return
      }
      window.open(url, "_blank", "noopener,noreferrer")
      setTimeout(() => URL.revokeObjectURL(url), 60000)
    } catch (err) {
      setError(err instanceof Error ? err.message : "文件打开失败")
    }
  }

  const checkOcr = async (file: FileRecord) => {
    if (!token) return
    setError("")
    try {
      const adapter = await fetchFileOcrApi(file.id, token)
      setError(adapter.enabled ? `OCR adapter: ${adapter.providerMode}` : adapter.message)
    } catch (err) {
      setError(err instanceof Error ? err.message : "OCR provider unavailable")
    }
  }

  const FileItem = ({ file }: { file: FileRecord }) => (
    <div className="flex cursor-pointer items-center gap-3 border-b bg-background px-4 py-3 hover:bg-muted/50" onClick={() => openFile(file, file.previewUrl ? "preview" : "download")}>
      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded bg-muted/30">
        {getIcon(file)}
      </div>
      <div className="min-w-0 flex-1">
        <h4 className="truncate text-sm font-medium">{file.originalName}</h4>
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <span>{formatSize(file.sizeBytes)}</span>
          <span>/</span>
          <span>{file.status}</span>
          <span>/</span>
          <span>{formatDate(file.createdAt)}</span>
        </div>
      </div>
      <Button variant="ghost" size="icon" title="下载" onClick={(event) => {
        event.stopPropagation()
        void openFile(file, "download")
      }}>
        <MoreHorizontal className="h-4 w-4" />
      </Button>
      {canOcr(file) && (
        <Button variant="ghost" size="sm" title="OCR" onClick={(event) => {
          event.stopPropagation()
          void checkOcr(file)
        }}>
          OCR
        </Button>
      )}
    </div>
  )

  const renderList = (items: FileRecord[]) => (
    <>
      {loading && <div className="px-4 py-3 text-sm text-muted-foreground">加载中...</div>}
      {error && <div className="px-4 py-3 text-sm text-red-500">加载失败：{error}</div>}
      {!loading && items.length === 0 && (
        <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
          <p>暂无文件</p>
        </div>
      )}
      {items.map((file) => <FileItem key={file.id} file={file} />)}
    </>
  )

  return (
    <div className="flex h-full flex-col bg-muted/20">
      <header className="sticky top-0 z-10 flex items-center justify-between border-b bg-background px-4 py-3">
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="-ml-2">
            <ChevronLeft className="h-6 w-6" />
          </Button>
          <h2 className="font-semibold">文件管理</h2>
        </div>
        <Button variant="ghost" size="icon" onClick={loadFiles}>
          <RefreshCw className="h-5 w-5" />
        </Button>
      </header>

      <div className="border-b bg-background px-4 py-3">
        <div className="relative">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input value={query} onChange={(event) => setQuery(event.target.value)} className="h-9 pl-9" placeholder="搜索文件" />
        </div>
      </div>

      <div className="flex flex-1 flex-col overflow-hidden">
        <Tabs defaultValue="all" className="flex flex-1 flex-col">
          <div className="bg-background px-4 pb-2">
            <TabsList className="grid w-full grid-cols-5">
              <TabsTrigger value="all">全部</TabsTrigger>
              <TabsTrigger value="doc">文档</TabsTrigger>
              <TabsTrigger value="image">图片</TabsTrigger>
              <TabsTrigger value="video">视频</TabsTrigger>
              <TabsTrigger value="other">其他</TabsTrigger>
            </TabsList>
          </div>

          <ScrollArea className="flex-1">
            <TabsContent value="all" className="mt-0">
              {renderList(filteredFiles)}
            </TabsContent>
            <TabsContent value="doc" className="mt-0">
              {renderList(filteredFiles.filter((file) => {
                const ext = file.originalName.split(".").pop()?.toLowerCase() || ""
                return ["pdf", "doc", "docx", "xls", "xlsx", "csv", "ppt", "pptx", "txt"].includes(ext)
              }))}
            </TabsContent>
            <TabsContent value="image" className="mt-0">
              {renderList(filteredFiles.filter((file) => fileKind(file) === "image"))}
            </TabsContent>
            <TabsContent value="video" className="mt-0">
              {renderList(filteredFiles.filter((file) => fileKind(file) === "video"))}
            </TabsContent>
            <TabsContent value="other" className="mt-0">
              {renderList(filteredFiles.filter((file) => {
                const kind = fileKind(file)
                const ext = file.originalName.split(".").pop()?.toLowerCase() || ""
                return kind !== "image" && kind !== "video" && !["pdf", "doc", "docx", "xls", "xlsx", "csv", "ppt", "pptx", "txt"].includes(ext)
              }))}
            </TabsContent>
          </ScrollArea>
        </Tabs>
      </div>
    </div>
  )
}
