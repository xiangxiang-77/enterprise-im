import { useNavigate } from "react-router-dom"
import { ChevronLeft } from "lucide-react"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Switch } from "@/components/ui/switch"

export default function NotificationSettings() {
  const navigate = useNavigate()

  const SettingItem = ({
    label,
    description,
    action,
  }: {
    label: string
    description?: string
    action?: React.ReactNode
  }) => (
    <div className="flex items-center justify-between bg-background px-4 py-3 hover:bg-muted/50">
      <div className="flex flex-col gap-0.5">
        <span className="text-sm font-medium">{label}</span>
        {description && <span className="text-xs text-muted-foreground">{description}</span>}
      </div>
      <div>{action}</div>
    </div>
  )

  return (
    <div className="flex h-full flex-col bg-muted/20">
      <header className="sticky top-0 z-10 flex items-center gap-2 border-b bg-background px-4 py-3">
        <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="-ml-2">
          <ChevronLeft className="h-6 w-6" />
        </Button>
        <h2 className="font-semibold">新消息通知</h2>
      </header>

      <ScrollArea className="flex-1 py-4">
        <div className="flex flex-col gap-6">
          <div className="flex flex-col divide-y border-y">
            <SettingItem label="接收新消息通知" description="关闭后，需要打开应用才能收到新消息" action={<Switch defaultChecked />} />
            <SettingItem label="接收语音和视频通话通知" action={<Switch defaultChecked />} />
          </div>

          <div className="flex flex-col divide-y border-y">
            <SettingItem label="通知显示消息详情" description="关闭后，通知只显示“你收到了一条消息”" action={<Switch defaultChecked />} />
          </div>

          <div className="flex flex-col divide-y border-y">
            <SettingItem label="声音" action={<Switch defaultChecked />} />
            <SettingItem label="振动" action={<Switch defaultChecked />} />
          </div>

          <div className="px-4 text-xs text-muted-foreground">
            <p>如果关闭新消息通知，你将无法在系统通知栏看到新消息提醒。</p>
          </div>
        </div>
      </ScrollArea>
    </div>
  )
}
