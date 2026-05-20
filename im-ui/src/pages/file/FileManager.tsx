import { useNavigate } from "react-router-dom"
import { ChevronLeft, FileText, Image as ImageIcon, MoreHorizontal, Music, Search, Video } from "lucide-react"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"

const mockFiles = [
  { id: "file-1", name: "产品需求说明.pdf", size: "842 KB", date: "05/13", type: "doc", author: "林一鸣" },
  { id: "file-2", name: "后台管理截图.png", size: "1.8 MB", date: "05/13", type: "image", author: "陈思远" },
  { id: "file-3", name: "移动端演示.mp4", size: "8.4 MB", date: "05/12", type: "video", author: "周雨桐" },
  { id: "file-4", name: "会议录音.m4a", size: "2.1 MB", date: "05/12", type: "audio", author: "赵明轩" },
  { id: "file-5", name: "接口联调记录.xlsx", size: "316 KB", date: "05/11", type: "doc", author: "沈佳宁" },
  { id: "file-6", name: "部署清单.md", size: "42 KB", date: "05/11", type: "doc", author: "韩子昂" },
]

export default function FileManager() {
  const navigate = useNavigate()

  const getIcon = (type: string) => {
    switch (type) {
      case "image":
        return <ImageIcon className="h-8 w-8 text-blue-500" />
      case "video":
        return <Video className="h-8 w-8 text-purple-500" />
      case "audio":
        return <Music className="h-8 w-8 text-orange-500" />
      default:
        return <FileText className="h-8 w-8 text-gray-500" />
    }
  }

  const FileItem = ({ file }: { file: typeof mockFiles[0] }) => (
    <div className="flex cursor-pointer items-center gap-3 border-b bg-background px-4 py-3 hover:bg-muted/50">
      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded bg-muted/30">
        {getIcon(file.type)}
      </div>
      <div className="min-w-0 flex-1">
        <h4 className="truncate text-sm font-medium">{file.name}</h4>
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <span>{file.size}</span>
          <span>/</span>
          <span>{file.author}</span>
          <span>/</span>
          <span>{file.date}</span>
        </div>
      </div>
      <Button variant="ghost" size="icon">
        <MoreHorizontal className="h-4 w-4" />
      </Button>
    </div>
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
        <Button variant="ghost" size="icon">
          <Search className="h-5 w-5" />
        </Button>
      </header>

      <div className="flex flex-1 flex-col overflow-hidden">
        <Tabs defaultValue="recent" className="flex flex-1 flex-col">
          <div className="bg-background px-4 pb-2">
            <TabsList className="grid w-full grid-cols-3">
              <TabsTrigger value="recent">最近</TabsTrigger>
              <TabsTrigger value="local">本机</TabsTrigger>
              <TabsTrigger value="cloud">云盘</TabsTrigger>
            </TabsList>
          </div>

          <ScrollArea className="flex-1">
            <TabsContent value="recent" className="mt-0">
              <div className="bg-muted/20 px-4 py-2 text-xs text-muted-foreground">本周</div>
              {mockFiles.map((file) => <FileItem key={file.id} file={file} />)}
            </TabsContent>
            <TabsContent value="local" className="mt-0">
              <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                <p>暂无本地文件</p>
              </div>
            </TabsContent>
            <TabsContent value="cloud" className="mt-0">
              <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
                <p>需要登录云盘查看</p>
              </div>
            </TabsContent>
          </ScrollArea>
        </Tabs>
      </div>
    </div>
  )
}
