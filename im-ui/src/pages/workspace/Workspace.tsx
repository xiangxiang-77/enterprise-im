import { useEffect, useMemo, useState } from "react"
import {
  BarChart,
  Calendar,
  CheckSquare,
  Cloud,
  FileText,
  Globe,
  LayoutGrid,
  Mail,
  MoreHorizontal,
  Users,
  Video,
} from "lucide-react"

import { type AdminWorkspaceApp, fetchWorkspaceAppsApi } from "@/services/api"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { useAuthStore } from "@/stores/useAuthStore"

type WorkspaceApp = {
  id: string
  name: string
  icon: typeof CheckSquare
  color: string
  description: string
  url?: string
}

const defaultApps: WorkspaceApp[] = [
  { id: "approval", name: "审批", icon: CheckSquare, color: "bg-blue-500", description: "请假、报销、采购审批" },
  { id: "checkin", name: "打卡", icon: Calendar, color: "bg-orange-500", description: "上下班打卡和考勤记录" },
  { id: "report", name: "汇报", icon: FileText, color: "bg-green-500", description: "日报、周报、项目进度" },
  { id: "calendar", name: "日程", icon: Calendar, color: "bg-indigo-500", description: "会议和待办安排" },
  { id: "mail", name: "企业邮箱", icon: Mail, color: "bg-blue-600", description: "企业邮件入口" },
  { id: "cloud", name: "云盘", icon: Cloud, color: "bg-yellow-500", description: "团队文件和资料" },
  { id: "contacts", name: "通讯录", icon: Users, color: "bg-green-600", description: "组织成员和部门" },
  { id: "meeting", name: "视频会议", icon: Video, color: "bg-blue-400", description: "发起或加入会议" },
  { id: "analytics", name: "报表", icon: BarChart, color: "bg-purple-500", description: "业务数据概览" },
  { id: "notice", name: "公告", icon: Globe, color: "bg-red-500", description: "企业通知公告" },
]

const iconMap: Record<string, typeof CheckSquare> = {
  approval: CheckSquare,
  checkin: Calendar,
  calendar: Calendar,
  mail: Mail,
  cloud: Cloud,
  contacts: Users,
  meeting: Video,
  analytics: BarChart,
  report: FileText,
  notice: Globe,
  briefcase: LayoutGrid,
}

const colorMap = ["bg-blue-500", "bg-emerald-500", "bg-orange-500", "bg-indigo-500", "bg-cyan-500", "bg-violet-500"]

function toWorkspaceApp(app: AdminWorkspaceApp, index: number): WorkspaceApp {
  return {
    id: app.id,
    name: app.name,
    icon: iconMap[app.icon || ""] ?? LayoutGrid,
    color: colorMap[index % colorMap.length],
    description: app.visibleDepartmentId ? `部门可见：${app.visibleDepartmentId}` : "企业后台配置应用",
    url: app.url,
  }
}

export default function Workspace() {
  const { token } = useAuthStore()
  const [selectedAppId, setSelectedAppId] = useState("approval")
  const [feedback, setFeedback] = useState("工作台应用已加载")
  const [serverApps, setServerApps] = useState<AdminWorkspaceApp[]>([])
  const [approvalTitle, setApprovalTitle] = useState("报销申请")
  const [reportText, setReportText] = useState("今日完成联调，明日继续验收")

  useEffect(() => {
    if (!token) return
    fetchWorkspaceAppsApi(token)
      .then((items) => {
        setServerApps(items)
        if (items[0]) setSelectedAppId(items[0].id)
        setFeedback(`已从后台加载 ${items.length} 个工作台应用`)
      })
      .catch((error) => setFeedback(error instanceof Error ? error.message : "工作台应用加载失败"))
  }, [token])

  const apps = useMemo(
    () => serverApps.length > 0 ? serverApps.map(toWorkspaceApp) : defaultApps,
    [serverApps]
  )

  const selectedApp = useMemo(
    () => apps.find((app) => app.id === selectedAppId) ?? apps[0],
    [apps, selectedAppId]
  )
  const recentApps = apps.slice(0, 4)

  const selectApp = (app: WorkspaceApp) => {
    setSelectedAppId(app.id)
    setFeedback(app.url && app.url !== "#" ? `${app.name} 已连接：${app.url}` : `${app.name}已打开`)
  }

  return (
    <div className="flex h-full flex-col bg-background">
      <header className="sticky top-0 z-10 flex items-center justify-between border-b bg-background px-4 py-3">
        <h1 className="text-lg font-semibold">工作台</h1>
        <MoreHorizontal className="h-5 w-5 text-muted-foreground" />
      </header>

      <ScrollArea className="flex-1">
        <div className="space-y-6 p-4">
          <div className="overflow-hidden rounded-lg bg-gradient-to-r from-blue-600 to-indigo-600 p-6 text-white shadow-lg">
            <h2 className="text-xl font-bold">欢迎开始新的一天</h2>
            <p className="mt-1 text-blue-100">待办事项：3 个未完成任务</p>
          </div>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-base">
                <selectedApp.icon className="h-5 w-5" />
                {selectedApp.name}
              </CardTitle>
              <p className="text-sm text-muted-foreground">{selectedApp.description}</p>
            </CardHeader>
            <CardContent className="space-y-4">
              <WorkspacePanel
                appId={selectedApp.id}
                approvalTitle={approvalTitle}
                reportText={reportText}
                onApprovalTitleChange={setApprovalTitle}
                onReportTextChange={setReportText}
                onFeedback={setFeedback}
                appUrl={selectedApp.url}
              />
              <div className="rounded-md bg-muted px-3 py-2 text-sm text-muted-foreground">{feedback}</div>
            </CardContent>
          </Card>

          <section>
            <h3 className="mb-3 text-sm font-medium text-muted-foreground">常用应用</h3>
            <div className="grid grid-cols-4 gap-4">
              {recentApps.map((app) => (
                <AppButton key={app.id} app={app} active={selectedAppId === app.id} onClick={() => selectApp(app)} />
              ))}
            </div>
          </section>

          <Separator />

          <section>
            <h3 className="mb-3 text-sm font-medium text-muted-foreground">企业应用</h3>
            <div className="grid grid-cols-4 gap-x-4 gap-y-6">
              {apps.map((app) => (
                <AppButton key={app.id} app={app} active={selectedAppId === app.id} onClick={() => selectApp(app)} />
              ))}
              <button
                type="button"
                className="flex flex-col items-center gap-2 rounded-lg p-2 transition-colors hover:bg-muted/50"
                onClick={() => setFeedback("全部应用已展开")}
              >
                <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-muted text-muted-foreground">
                  <LayoutGrid className="h-6 w-6" />
                </div>
                <span className="text-xs font-medium">全部应用</span>
              </button>
            </div>
          </section>

        </div>
      </ScrollArea>
    </div>
  )
}

function AppButton({ app, active, onClick }: { app: WorkspaceApp; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      className={`flex flex-col items-center gap-2 rounded-lg p-2 transition-colors hover:bg-muted/50 ${
        active ? "bg-muted" : ""
      }`}
      onClick={onClick}
    >
      <div className={`flex h-12 w-12 items-center justify-center rounded-xl ${app.color} text-white shadow-sm`}>
        <app.icon className="h-6 w-6" />
      </div>
      <span className="text-xs font-medium">{app.name}</span>
    </button>
  )
}

function WorkspacePanel({
  appId,
  approvalTitle,
  reportText,
  onApprovalTitleChange,
  onReportTextChange,
  onFeedback,
  appUrl,
}: {
  appId: string
  approvalTitle: string
  reportText: string
  onApprovalTitleChange: (value: string) => void
  onReportTextChange: (value: string) => void
  onFeedback: (value: string) => void
  appUrl?: string
}) {
  if (appId === "approval") {
    return (
      <div className="space-y-3">
        <div className="grid gap-2">
          <Label htmlFor="approval-title">审批标题</Label>
          <Input id="approval-title" value={approvalTitle} onChange={(event) => onApprovalTitleChange(event.target.value)} />
        </div>
        <div className="flex gap-2">
          <Button size="sm" onClick={() => onFeedback(`已提交审批：${approvalTitle || "未命名审批"}`)}>提交审批</Button>
          <Button size="sm" variant="outline" onClick={() => onFeedback("待我审批：2 条；我发起的：1 条")}>查看待办</Button>
        </div>
      </div>
    )
  }

  if (appId === "checkin") {
    return (
      <div className="space-y-3">
        <div className="grid grid-cols-2 gap-3 text-sm">
          <div className="rounded-md border p-3">
            <div className="text-muted-foreground">上班</div>
            <div className="font-medium">09:00</div>
          </div>
          <div className="rounded-md border p-3">
            <div className="text-muted-foreground">下班</div>
            <div className="font-medium">18:00</div>
          </div>
        </div>
        <Button size="sm" onClick={() => onFeedback(`打卡成功：${new Date().toLocaleTimeString()}`)}>立即打卡</Button>
      </div>
    )
  }

  if (appId === "report") {
    return (
      <div className="space-y-3">
        <div className="grid gap-2">
          <Label htmlFor="report-text">汇报内容</Label>
          <Input id="report-text" value={reportText} onChange={(event) => onReportTextChange(event.target.value)} />
        </div>
        <Button size="sm" onClick={() => onFeedback("汇报已保存")}>保存汇报</Button>
      </div>
    )
  }

  const messages: Record<string, string> = {
    calendar: "今日 14:00 项目验收会；16:00 周会",
    mail: "收件箱 5 封未读，已打开企业邮箱摘要",
    cloud: "云盘最近文件：需求文档、交付清单、测试报告",
    contacts: "通讯录已加载：研发部、运营部、管理部",
    meeting: "会议室已创建，会议号 10086",
    analytics: "今日消息 128 条，活跃用户 12 人",
    notice: "公告：本周五完成项目交付验收",
  }

  return (
    <div className="space-y-3">
      <div className="rounded-md border p-3 text-sm">{messages[appId] ?? (appUrl && appUrl !== "#" ? `后台应用地址：${appUrl}` : "应用已打开")}</div>
      <div className="flex gap-2">
        <Button size="sm" onClick={() => onFeedback(messages[appId] ?? "操作完成")}>刷新</Button>
        {appUrl && appUrl !== "#" && (
          <Button size="sm" variant="outline" onClick={() => window.open(appUrl, "_blank", "noopener,noreferrer")}>打开应用</Button>
        )}
      </div>
    </div>
  )
}
