import { useEffect, useMemo, useState } from "react"
import { Activity, AlertCircle, ClipboardList, RefreshCw, ShieldCheck, Users } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  type AdminAccount,
  type AdminAuditLog,
  type AdminCallAudit,
  type AdminDepartment,
  type AdminEnterprise,
  type AdminFile,
  type AdminGroup,
  type AdminMessageAudit,
  type AdminOverview,
  type AdminRole,
  type AdminSession,
  type AdminUser,
  type CallConnectivity,
  type CallReadiness,
  adminLoginApi,
  createAdminAccountApi,
  createAdminDepartmentApi,
  createAdminEnterpriseApi,
  createAdminUserApi,
  deleteAdminDepartmentApi,
  fetchAdminAccountsApi,
  fetchAdminAuditLogsApi,
  fetchAdminCallsApi,
  fetchAdminCallConnectivityApi,
  fetchAdminDepartmentsApi,
  fetchAdminEnterprisesApi,
  fetchAdminFilesApi,
  fetchAdminGroupsApi,
  fetchAdminMessagesApi,
  fetchAdminOverviewApi,
  fetchAdminRolesApi,
  fetchAdminUsersApi,
  fetchCallReadinessApi,
  updateAdminAccountEnabledApi,
  updateAdminDepartmentApi,
  updateAdminUserStatusApi,
} from "@/services/api"

type LoadState = "idle" | "loading" | "ready" | "error"

const emptyOverview: AdminOverview = {
  enterpriseUsers: 0,
  onlineUsers: 0,
  offlineUsers: 0,
  singleConversations: 0,
  groupConversations: 0,
  todayMessages: 0,
  pendingFriendRequests: 0,
  totalCalls: 0,
  activeCalls: 0,
  answeredCalls: 0,
  missedCalls: 0,
}

function formatTime(value?: string) {
  if (!value) return "-"
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value))
}

function statusLabel(value?: string) {
  const labels: Record<string, string> = {
    active: "启用",
    disabled: "禁用",
    pending: "待处理",
    sent: "已发送",
    read: "已读",
    failed: "失败",
  }
  return value ? labels[value] ?? value : "-"
}

function callStatusLabel(value?: string) {
  const labels: Record<string, string> = {
    ringing: "呼叫中",
    answered: "已接通",
    rejected: "已拒绝",
    ended: "已结束",
  }
  return value ? labels[value] ?? value : "-"
}

function mediaTypeLabel(value?: string) {
  const labels: Record<string, string> = {
    audio: "音频",
    video: "视频",
  }
  return value ? labels[value] ?? value : "-"
}

function roleLabel(value?: string) {
  const labels: Record<string, string> = {
    SUPER_ADMIN: "超级管理员",
    OPERATOR: "运营管理员",
    AUDITOR: "审计员",
    READONLY_OPS: "只读运维",
  }
  return value ? labels[value] ?? value : "-"
}

export default function AdminDashboard() {
  const [state, setState] = useState<LoadState>("idle")
  const [error, setError] = useState("")
  const [phone, setPhone] = useState("18800000000")
  const [password, setPassword] = useState("admin123")
  const [session, setSession] = useState<AdminSession | null>(() => {
    const raw = window.localStorage.getItem("admin-session")
    return raw ? JSON.parse(raw) as AdminSession : null
  })
  const [overview, setOverview] = useState<AdminOverview>(emptyOverview)
  const [users, setUsers] = useState<AdminUser[]>([])
  const [auditLogs, setAuditLogs] = useState<AdminAuditLog[]>([])
  const [enterprises, setEnterprises] = useState<AdminEnterprise[]>([])
  const [departments, setDepartments] = useState<AdminDepartment[]>([])
  const [roles, setRoles] = useState<AdminRole[]>([])
  const [adminAccounts, setAdminAccounts] = useState<AdminAccount[]>([])
  const [groups, setGroups] = useState<AdminGroup[]>([])
  const [files, setFiles] = useState<AdminFile[]>([])
  const [messages, setMessages] = useState<AdminMessageAudit[]>([])
  const [calls, setCalls] = useState<AdminCallAudit[]>([])
  const [callReadiness, setCallReadiness] = useState<CallReadiness | null>(null)
  const [callConnectivity, setCallConnectivity] = useState<CallConnectivity | null>(null)
  const [auditOperatorId, setAuditOperatorId] = useState("")
  const [auditAction, setAuditAction] = useState("")
  const [auditTargetType, setAuditTargetType] = useState("")
  const [departmentEnterpriseId, setDepartmentEnterpriseId] = useState("")
  const [departmentName, setDepartmentName] = useState("")
  const [enterpriseName, setEnterpriseName] = useState("")
  const [enterpriseCode, setEnterpriseCode] = useState("")
  const [userFilterStatus, setUserFilterStatus] = useState("")
  const [userFilterEnterpriseId, setUserFilterEnterpriseId] = useState("")
  const [newUserEnterpriseId, setNewUserEnterpriseId] = useState("")
  const [newUserPhone, setNewUserPhone] = useState("")
  const [newUserName, setNewUserName] = useState("")
  const [adminAccountUserId, setAdminAccountUserId] = useState("")
  const [adminAccountRoleId, setAdminAccountRoleId] = useState("role_readonly_ops")
  const [groupEnterpriseId, setGroupEnterpriseId] = useState("")
  const [groupStatus, setGroupStatus] = useState("")
  const [fileUploaderId, setFileUploaderId] = useState("")
  const [fileStatus, setFileStatus] = useState("")
  const [messageConversationId, setMessageConversationId] = useState("")
  const [messageSenderId, setMessageSenderId] = useState("")
  const [callUserId, setCallUserId] = useState("")
  const [callStatus, setCallStatus] = useState("")
  const [callMediaType, setCallMediaType] = useState("")

  const stats = useMemo(() => [
    { label: "用户总数", value: overview.enterpriseUsers, icon: Users, tone: "text-blue-600" },
    { label: "在线用户", value: overview.onlineUsers, icon: Activity, tone: "text-emerald-600" },
    { label: "今日消息", value: overview.todayMessages, icon: ClipboardList, tone: "text-violet-600" },
    { label: "通话总数", value: overview.totalCalls, icon: Activity, tone: "text-cyan-600" },
    { label: "进行中通话", value: overview.activeCalls, icon: AlertCircle, tone: "text-amber-600" },
    { label: "已接通话", value: overview.answeredCalls, icon: ShieldCheck, tone: "text-emerald-600" },
    { label: "未接通话", value: overview.missedCalls, icon: AlertCircle, tone: "text-red-600" },
    { label: "待处理申请", value: overview.pendingFriendRequests, icon: AlertCircle, tone: "text-orange-600" },
  ], [overview])

  async function load() {
    if (!session?.token) return
    setState("loading")
    setError("")
    try {
      const [overviewData, userData, auditData, enterpriseData, departmentData, roleData, adminAccountData, groupData, fileData, messageData, callData, callReadinessData, callConnectivityData] = await Promise.all([
        fetchAdminOverviewApi(session.token),
        fetchAdminUsersApi(20, session.token, {
          status: userFilterStatus.trim(),
          enterpriseId: userFilterEnterpriseId.trim(),
        }),
        fetchAdminAuditLogsApi(20, session.token, {
          operatorId: auditOperatorId.trim(),
          action: auditAction.trim(),
          targetType: auditTargetType.trim(),
        }),
        fetchAdminEnterprisesApi(20, session.token),
        fetchAdminDepartmentsApi(20, session.token),
        fetchAdminRolesApi(session.token),
        fetchAdminAccountsApi(20, session.token),
        fetchAdminGroupsApi(20, session.token, { enterpriseId: groupEnterpriseId.trim(), status: groupStatus.trim() }),
        fetchAdminFilesApi(20, session.token, { uploaderId: fileUploaderId.trim(), status: fileStatus.trim() }),
        fetchAdminMessagesApi(20, session.token, { conversationId: messageConversationId.trim(), senderId: messageSenderId.trim() }),
        fetchAdminCallsApi(20, session.token, { userId: callUserId.trim(), status: callStatus.trim(), mediaType: callMediaType.trim() }),
        fetchCallReadinessApi(),
        fetchAdminCallConnectivityApi(session.token),
      ])
      setOverview(overviewData)
      setUsers(userData)
      setAuditLogs(auditData)
      setEnterprises(enterpriseData)
      setDepartments(departmentData)
      setRoles(roleData)
      setAdminAccounts(adminAccountData)
      setGroups(groupData)
      setFiles(fileData)
      setMessages(messageData)
      setCalls(callData)
      setCallReadiness(callReadinessData)
      setCallConnectivity(callConnectivityData)
      setState("ready")
    } catch (err) {
      setError(err instanceof Error ? err.message : "后台接口请求失败")
      if (err instanceof Error && err.message === "HTTP 401") {
        setSession(null)
        window.localStorage.removeItem("admin-session")
      }
      setState("error")
    }
  }

  useEffect(() => {
    void load()
  }, [session?.token])

  async function login() {
    setState("loading")
    setError("")
    try {
      const nextSession = await adminLoginApi(phone, password)
      setSession(nextSession)
      window.localStorage.setItem("admin-session", JSON.stringify(nextSession))
      setState("ready")
    } catch (err) {
      setError(err instanceof Error ? err.message : "后台登录失败")
      setState("error")
    }
  }

  function logout() {
    setSession(null)
    setUsers([])
    setAuditLogs([])
    setEnterprises([])
    setDepartments([])
    setRoles([])
    setAdminAccounts([])
    setGroups([])
    setFiles([])
    setMessages([])
    setCalls([])
    setCallReadiness(null)
    setCallConnectivity(null)
    window.localStorage.removeItem("admin-session")
  }

  async function toggleUser(user: AdminUser) {
    if (!session?.token) return
    const nextStatus = user.status === "active" ? "disabled" : "active"
    const ok = window.confirm(`确认将 ${user.displayName || user.id} 状态改为${nextStatus === "active" ? "启用" : "禁用"}？`)
    if (!ok) return
    setState("loading")
    setError("")
    try {
      await updateAdminUserStatusApi(user.id, nextStatus, session.token)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : "用户状态更新失败")
      setState("error")
    }
  }

  async function createEnterprise() {
    if (!session?.token || !enterpriseName.trim() || !enterpriseCode.trim()) return
    setState("loading")
    setError("")
    try {
      const created = await createAdminEnterpriseApi({ name: enterpriseName.trim(), code: enterpriseCode.trim() }, session.token)
      setEnterpriseName("")
      setEnterpriseCode("")
      setDepartmentEnterpriseId(created.id)
      setNewUserEnterpriseId(created.id)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : "企业创建失败")
      setState("error")
    }
  }

  async function createUser() {
    if (!session?.token || !newUserPhone.trim() || !newUserName.trim()) return
    setState("loading")
    setError("")
    try {
      await createAdminUserApi({
        enterpriseId: newUserEnterpriseId.trim(),
        phone: newUserPhone.trim(),
        displayName: newUserName.trim(),
      }, session.token)
      setNewUserPhone("")
      setNewUserName("")
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : "用户创建失败")
      setState("error")
    }
  }

  async function createDepartment() {
    if (!session?.token || !departmentEnterpriseId.trim() || !departmentName.trim()) return
    setState("loading")
    setError("")
    try {
      await createAdminDepartmentApi({
        enterpriseId: departmentEnterpriseId.trim(),
        name: departmentName.trim(),
      }, session.token)
      setDepartmentName("")
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : "部门创建失败")
      setState("error")
    }
  }

  async function renameDepartment(department: AdminDepartment) {
    if (!session?.token) return
    const nextName = window.prompt("部门名称", department.name)
    if (!nextName?.trim() || nextName.trim() === department.name) return
    setState("loading")
    setError("")
    try {
      await updateAdminDepartmentApi(department.id, {
        parentId: department.parentId,
        name: nextName.trim(),
        sortOrder: department.sortOrder,
      }, session.token)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : "部门更新失败")
      setState("error")
    }
  }

  async function deleteDepartment(department: AdminDepartment) {
    if (!session?.token) return
    const ok = window.confirm(`确认删除部门 ${department.name}？服务端会拒绝删除有成员或子部门的部门。`)
    if (!ok) return
    setState("loading")
    setError("")
    try {
      await deleteAdminDepartmentApi(department.id, session.token)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : "部门删除失败")
      setState("error")
    }
  }

  async function createAdminAccount() {
    if (!session?.token || !adminAccountUserId.trim() || !adminAccountRoleId.trim()) return
    setState("loading")
    setError("")
    try {
      await createAdminAccountApi({
        userId: adminAccountUserId.trim(),
        roleId: adminAccountRoleId.trim(),
      }, session.token)
      setAdminAccountUserId("")
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : "管理员账号创建失败")
      setState("error")
    }
  }

  async function toggleAdminAccount(account: AdminAccount) {
    if (!session?.token) return
    const nextEnabled = !account.enabled
    const ok = window.confirm(`确认将 ${account.displayName || account.userId} 的管理员权限改为${nextEnabled ? "启用" : "禁用"}？`)
    if (!ok) return
    setState("loading")
    setError("")
    try {
      await updateAdminAccountEnabledApi(account.id, nextEnabled, session.token)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : "管理员账号更新失败")
      setState("error")
    }
  }

  if (!session) {
    return (
      <div className="flex h-full items-center justify-center bg-background p-4">
        <Card className="w-full max-w-sm rounded-md">
          <CardHeader>
            <CardTitle className="text-lg">后台登录</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <Input value={phone} onChange={(event) => setPhone(event.target.value)} placeholder="手机号" />
            <Input value={password} onChange={(event) => setPassword(event.target.value)} placeholder="密码" type="password" />
            {error && <p className="text-sm text-destructive">{error}</p>}
            <Button className="w-full" onClick={login} disabled={state === "loading"}>
              登录
            </Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col bg-background">
      <header className="flex items-center justify-between border-b bg-background px-4 py-3">
        <div className="flex min-w-0 items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-md bg-primary text-primary-foreground">
            <ShieldCheck className="h-5 w-5" />
          </div>
          <div className="min-w-0">
            <h1 className="truncate text-lg font-semibold">后台管理台</h1>
            <p className="truncate text-xs text-muted-foreground">实时运营、用户管理、审计追踪</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant="outline">{roleLabel(session.role)}</Badge>
          <Button variant="ghost" size="sm" onClick={logout}>退出</Button>
          <Button variant="outline" size="icon" onClick={load} disabled={state === "loading"} title="刷新">
            <RefreshCw className={state === "loading" ? "h-4 w-4 animate-spin" : "h-4 w-4"} />
          </Button>
        </div>
      </header>

      <ScrollArea className="flex-1">
        <div className="mx-auto flex w-full max-w-6xl flex-col gap-4 p-4">
          {state === "error" && (
            <div className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {error}
            </div>
          )}

          <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            {stats.map((stat) => (
              <Card key={stat.label} className="rounded-md">
                <CardContent className="flex items-center justify-between p-4">
                  <div>
                    <p className="text-sm text-muted-foreground">{stat.label}</p>
                    <p className="mt-1 text-2xl font-semibold">{stat.value}</p>
                  </div>
                  <stat.icon className={`h-6 w-6 ${stat.tone}`} />
                </CardContent>
              </Card>
            ))}
          </section>

          <section>
            <Card className="rounded-md">
              <CardHeader className="p-4 pb-3">
                <div className="flex items-center justify-between gap-3">
                  <CardTitle className="text-base">音视频就绪状态</CardTitle>
                  <Badge variant={callReadiness?.ready ? "default" : "destructive"}>
                    {callReadiness?.ready ? "已就绪" : "需处理"}
                  </Badge>
                </div>
              </CardHeader>
              <CardContent className="grid gap-3 p-4 pt-0 md:grid-cols-[1fr_2fr]">
                <div className="rounded-md border bg-muted/30 p-3">
                  <p className="text-sm text-muted-foreground">支持媒体</p>
                  <p className="mt-1 text-lg font-semibold">
                    {callReadiness?.supportedMediaTypes?.join(" / ") || "-"}
                  </p>
                  <p className="mt-2 text-xs text-muted-foreground">
                    检查 TURN 与 PJSIP 配置，不展示 TURN 密码明文。
                  </p>
                </div>
                <div className="grid gap-2 sm:grid-cols-2">
                  {(callReadiness?.checks || []).map((item) => (
                    <div key={item.name} className="flex items-center justify-between gap-3 rounded-md border px-3 py-2 text-sm">
                      <div>
                        <p className="font-medium">{item.name}</p>
                        <p className="text-xs text-muted-foreground">{item.message}</p>
                      </div>
                      <Badge variant={item.ready ? "secondary" : "destructive"}>
                        {item.ready ? "通过" : "阻塞"}
                      </Badge>
                    </div>
                  ))}
                  {!callReadiness && (
                    <div className="rounded-md border px-3 py-2 text-sm text-muted-foreground">
                      等待加载音视频配置状态
                    </div>
                  )}
                </div>
                <div className="md:col-span-2 rounded-md border p-3">
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <p className="text-sm font-medium">媒体连通性</p>
                      <p className="text-xs text-muted-foreground">检测 TURN 与 PJSIP 信令端点是否可达</p>
                    </div>
                    <Badge variant={callConnectivity?.reachable ? "default" : "destructive"}>
                      {callConnectivity?.reachable ? "可达" : "阻塞"}
                    </Badge>
                  </div>
                  <div className="mt-3 grid gap-2 sm:grid-cols-2">
                    {(callConnectivity?.checks || []).map((item) => (
                      <div key={item.name} className="flex items-center justify-between gap-3 rounded-md bg-muted/30 px-3 py-2 text-sm">
                        <div className="min-w-0">
                          <p className="font-medium">{item.name}</p>
                          <p className="truncate text-xs text-muted-foreground">{item.host || "-"}:{item.port || "-"} / {item.message} / {item.durationMs}ms</p>
                        </div>
                        <Badge variant={item.reachable ? "secondary" : "destructive"}>
                          {item.reachable ? "正常" : "失败"}
                        </Badge>
                      </div>
                    ))}
                    {!callConnectivity && (
                      <div className="rounded-md bg-muted/30 px-3 py-2 text-sm text-muted-foreground">
                        Waiting for connectivity probe
                      </div>
                    )}
                  </div>
                </div>
              </CardContent>
            </Card>
          </section>

          <section>
            <Card className="rounded-md">
              <CardHeader className="p-4 pb-3">
                <CardTitle className="text-base">企业</CardTitle>
                <div className="grid gap-2 sm:grid-cols-[1fr_1fr_auto]">
                  <Input value={enterpriseName} onChange={(event) => setEnterpriseName(event.target.value)} placeholder="企业名称" className="h-8 text-xs" />
                  <Input value={enterpriseCode} onChange={(event) => setEnterpriseCode(event.target.value)} placeholder="企业编码" className="h-8 text-xs" />
                  <Button size="sm" onClick={createEnterprise} disabled={state === "loading" || !enterpriseName.trim() || !enterpriseCode.trim()}>
                    新增
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="p-0">
                <div className="divide-y">
                  {enterprises.map((enterprise) => (
                    <div key={enterprise.id} className="grid grid-cols-[1fr_auto] gap-3 px-4 py-3 text-sm">
                      <div className="min-w-0">
                        <p className="truncate font-medium">{enterprise.name}</p>
                        <p className="truncate text-xs text-muted-foreground">{enterprise.code} · {enterprise.id}</p>
                      </div>
                      <div className="flex items-center gap-2">
                        <Badge variant="secondary">{enterprise.userCount} 人</Badge>
                        <Badge variant="outline">{enterprise.departmentCount} 个部门</Badge>
                      </div>
                    </div>
                  ))}
                  {enterprises.length === 0 && (
                    <div className="px-4 py-8 text-center text-sm text-muted-foreground">暂无企业</div>
                  )}
                </div>
              </CardContent>
            </Card>
          </section>

          <section className="grid gap-4 xl:grid-cols-2">
            <Card className="rounded-md">
              <CardHeader className="p-4 pb-3">
                <CardTitle className="text-base">部门</CardTitle>
                <div className="grid gap-2 sm:grid-cols-[1fr_1fr_auto]">
                  <Input
                    value={departmentEnterpriseId}
                    onChange={(event) => setDepartmentEnterpriseId(event.target.value)}
                    placeholder="企业 ID"
                    className="h-8 text-xs"
                  />
                  <Input
                    value={departmentName}
                    onChange={(event) => setDepartmentName(event.target.value)}
                    placeholder="部门名称"
                    className="h-8 text-xs"
                  />
                  <Button size="sm" onClick={createDepartment} disabled={state === "loading" || !departmentEnterpriseId.trim() || !departmentName.trim()}>
                    新增
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="p-0">
                <div className="divide-y">
                  {departments.map((department) => (
                    <div key={department.id} className="grid grid-cols-[1fr_auto] gap-3 px-4 py-3 text-sm">
                      <div className="min-w-0">
                        <p className="truncate font-medium">{department.name}</p>
                        <p className="truncate text-xs text-muted-foreground">{department.enterpriseName} · {department.id}</p>
                      </div>
                      <div className="flex items-center gap-2">
                        <Badge variant="secondary">{department.memberCount} 人</Badge>
                        <Button variant="outline" size="sm" onClick={() => renameDepartment(department)} disabled={state === "loading"}>
                          重命名
                        </Button>
                        <Button variant="destructive" size="sm" onClick={() => deleteDepartment(department)} disabled={state === "loading"}>
                          删除
                        </Button>
                      </div>
                    </div>
                  ))}
                  {departments.length === 0 && (
                    <div className="px-4 py-8 text-center text-sm text-muted-foreground">暂无部门</div>
                  )}
                </div>
              </CardContent>
            </Card>

            <Card className="rounded-md">
              <CardHeader className="p-4 pb-2">
                <CardTitle className="text-base">管理员角色</CardTitle>
              </CardHeader>
              <CardContent className="p-0">
                <div className="divide-y">
                  {roles.map((role) => (
                    <div key={role.id} className="grid grid-cols-[1fr_auto] gap-3 px-4 py-3 text-sm">
                      <div className="min-w-0">
                        <p className="truncate font-medium">{role.name}</p>
                        <p className="truncate text-xs text-muted-foreground">{role.description || role.id}</p>
                      </div>
                      <Badge variant="outline">{role.adminCount} 个管理员</Badge>
                    </div>
                  ))}
                  {roles.length === 0 && (
                    <div className="px-4 py-8 text-center text-sm text-muted-foreground">暂无角色</div>
                  )}
                </div>
              </CardContent>
            </Card>
          </section>

          <section>
            <Card className="rounded-md">
              <CardHeader className="p-4 pb-3">
                <CardTitle className="text-base">管理员账号</CardTitle>
                <div className="grid gap-2 sm:grid-cols-[1fr_1fr_auto]">
                  <Input
                    value={adminAccountUserId}
                    onChange={(event) => setAdminAccountUserId(event.target.value)}
                    placeholder="用户 ID"
                    className="h-8 text-xs"
                  />
                  <Input
                    value={adminAccountRoleId}
                    onChange={(event) => setAdminAccountRoleId(event.target.value)}
                    placeholder="角色 ID"
                    className="h-8 text-xs"
                  />
                  <Button size="sm" onClick={createAdminAccount} disabled={state === "loading" || !adminAccountUserId.trim() || !adminAccountRoleId.trim()}>
                    分配
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="p-0">
                <div className="divide-y">
                  {adminAccounts.map((account) => (
                    <div key={account.id} className="grid grid-cols-[1fr_auto] gap-3 px-4 py-3 text-sm">
                      <div className="min-w-0">
                        <p className="truncate font-medium">{account.displayName || account.userId}</p>
                        <p className="truncate text-xs text-muted-foreground">{account.roleName} · {account.userId}</p>
                      </div>
                      <div className="flex items-center gap-2">
                        <Badge variant={account.enabled ? "default" : "secondary"}>{account.enabled ? "已启用" : "已禁用"}</Badge>
                        <Button variant="outline" size="sm" onClick={() => toggleAdminAccount(account)} disabled={state === "loading"}>
                          {account.enabled ? "禁用" : "启用"}
                        </Button>
                      </div>
                    </div>
                  ))}
                  {adminAccounts.length === 0 && (
                    <div className="px-4 py-8 text-center text-sm text-muted-foreground">暂无管理员账号</div>
                  )}
                </div>
              </CardContent>
            </Card>
          </section>

          <section className="grid gap-4 xl:grid-cols-4">
            <Card className="rounded-md">
              <CardHeader className="p-4 pb-3">
                <div className="flex items-center justify-between gap-3">
                  <CardTitle className="text-base">群组</CardTitle>
                  <Button variant="outline" size="sm" onClick={load} disabled={state === "loading"}>筛选</Button>
                </div>
                <div className="grid gap-2 sm:grid-cols-2">
                  <Input value={groupEnterpriseId} onChange={(event) => setGroupEnterpriseId(event.target.value)} placeholder="企业 ID" className="h-8 text-xs" />
                  <Input value={groupStatus} onChange={(event) => setGroupStatus(event.target.value)} placeholder="状态" className="h-8 text-xs" />
                </div>
              </CardHeader>
              <CardContent className="p-0">
                <div className="divide-y">
                  {groups.map((group) => (
                    <div key={group.id} className="px-4 py-3 text-sm">
                      <div className="flex items-center justify-between gap-3">
                        <p className="truncate font-medium">{group.name}</p>
                        <Badge variant={group.status === "active" ? "default" : "secondary"}>{statusLabel(group.status)}</Badge>
                      </div>
                      <p className="mt-1 truncate text-xs text-muted-foreground">{group.enterpriseName || group.enterpriseId || "-"} · {group.memberCount} 人</p>
                    </div>
                  ))}
                  {groups.length === 0 && <div className="px-4 py-8 text-center text-sm text-muted-foreground">暂无群组</div>}
                </div>
              </CardContent>
            </Card>

            <Card className="rounded-md">
              <CardHeader className="p-4 pb-3">
                <div className="flex items-center justify-between gap-3">
                  <CardTitle className="text-base">文件</CardTitle>
                  <Button variant="outline" size="sm" onClick={load} disabled={state === "loading"}>筛选</Button>
                </div>
                <div className="grid gap-2 sm:grid-cols-2">
                  <Input value={fileUploaderId} onChange={(event) => setFileUploaderId(event.target.value)} placeholder="上传者 ID" className="h-8 text-xs" />
                  <Input value={fileStatus} onChange={(event) => setFileStatus(event.target.value)} placeholder="状态" className="h-8 text-xs" />
                </div>
              </CardHeader>
              <CardContent className="p-0">
                <div className="divide-y">
                  {files.map((file) => (
                    <div key={file.id} className="px-4 py-3 text-sm">
                      <div className="flex items-center justify-between gap-3">
                        <p className="truncate font-medium">{file.originalName}</p>
                        <Badge variant="outline">{statusLabel(file.status)}</Badge>
                      </div>
                      <p className="mt-1 truncate text-xs text-muted-foreground">{file.uploaderId} · {file.sizeBytes} 字节</p>
                    </div>
                  ))}
                  {files.length === 0 && <div className="px-4 py-8 text-center text-sm text-muted-foreground">暂无文件</div>}
                </div>
              </CardContent>
            </Card>

            <Card className="rounded-md">
              <CardHeader className="p-4 pb-3">
                <div className="flex items-center justify-between gap-3">
                  <CardTitle className="text-base">消息</CardTitle>
                  <Button variant="outline" size="sm" onClick={load} disabled={state === "loading"}>筛选</Button>
                </div>
                <div className="grid gap-2 sm:grid-cols-2">
                  <Input value={messageConversationId} onChange={(event) => setMessageConversationId(event.target.value)} placeholder="会话 ID" className="h-8 text-xs" />
                  <Input value={messageSenderId} onChange={(event) => setMessageSenderId(event.target.value)} placeholder="发送者 ID" className="h-8 text-xs" />
                </div>
              </CardHeader>
              <CardContent className="p-0">
                <div className="divide-y">
                  {messages.map((message) => (
                    <div key={message.id} className="px-4 py-3 text-sm">
                      <div className="flex items-center justify-between gap-3">
                        <p className="truncate font-medium">{message.content || message.type}</p>
                        <Badge variant="secondary">{statusLabel(message.status)}</Badge>
                      </div>
                      <p className="mt-1 truncate text-xs text-muted-foreground">{message.conversationId} · {message.senderId}</p>
                    </div>
                  ))}
                  {messages.length === 0 && <div className="px-4 py-8 text-center text-sm text-muted-foreground">暂无消息</div>}
                </div>
              </CardContent>
            </Card>
            <Card className="rounded-md">
              <CardHeader className="p-4 pb-3">
                <div className="flex items-center justify-between gap-3">
                  <CardTitle className="text-base">通话记录</CardTitle>
                  <Button variant="outline" size="sm" onClick={load} disabled={state === "loading"}>筛选</Button>
                </div>
                <div className="grid gap-2">
                  <Input value={callUserId} onChange={(event) => setCallUserId(event.target.value)} placeholder="用户 ID" className="h-8 text-xs" />
                  <Input value={callStatus} onChange={(event) => setCallStatus(event.target.value)} placeholder="状态" className="h-8 text-xs" />
                  <Input value={callMediaType} onChange={(event) => setCallMediaType(event.target.value)} placeholder="音频 / 视频" className="h-8 text-xs" />
                </div>
              </CardHeader>
              <CardContent className="p-0">
                <div className="divide-y">
                  {calls.map((call) => (
                    <div key={call.id} className="px-4 py-3 text-sm">
                      <div className="flex items-center justify-between gap-3">
                        <p className="truncate font-medium">{mediaTypeLabel(call.mediaType)} / {callStatusLabel(call.status)}</p>
                        <Badge variant={call.status === "answered" ? "default" : "secondary"}>{callStatusLabel(call.status)}</Badge>
                      </div>
                      <p className="mt-1 truncate text-xs text-muted-foreground">{call.callerId} -&gt; {call.calleeId || call.groupId || "-"}</p>
                      <p className="mt-1 truncate text-xs text-muted-foreground">{call.turnSessionId || call.id}</p>
                    </div>
                  ))}
                  {calls.length === 0 && <div className="px-4 py-8 text-center text-sm text-muted-foreground">暂无通话记录</div>}
                </div>
              </CardContent>
            </Card>
          </section>

          <section className="grid gap-4 xl:grid-cols-[1.2fr_0.8fr]">
            <Card className="rounded-md">
              <CardHeader className="p-4 pb-3">
                <CardTitle className="text-base">用户目录</CardTitle>
                <div className="grid gap-2 lg:grid-cols-[1fr_1fr_auto_1fr_1fr_auto]">
                  <Input value={userFilterStatus} onChange={(event) => setUserFilterStatus(event.target.value)} placeholder="状态筛选" className="h-8 text-xs" />
                  <Input value={userFilterEnterpriseId} onChange={(event) => setUserFilterEnterpriseId(event.target.value)} placeholder="企业筛选" className="h-8 text-xs" />
                  <Button variant="outline" size="sm" onClick={load} disabled={state === "loading"}>筛选</Button>
                  <Input value={newUserPhone} onChange={(event) => setNewUserPhone(event.target.value)} placeholder="新用户手机号" className="h-8 text-xs" />
                  <Input value={newUserName} onChange={(event) => setNewUserName(event.target.value)} placeholder="新用户姓名" className="h-8 text-xs" />
                  <Button size="sm" onClick={createUser} disabled={state === "loading" || !newUserPhone.trim() || !newUserName.trim()}>新增</Button>
                </div>
                <Input value={newUserEnterpriseId} onChange={(event) => setNewUserEnterpriseId(event.target.value)} placeholder="新用户所属企业 ID" className="h-8 text-xs" />
              </CardHeader>
              <CardContent className="p-0">
                <div className="grid grid-cols-[1.4fr_1fr_0.8fr_0.9fr_0.7fr] border-b px-4 py-2 text-xs font-medium text-muted-foreground">
                  <span>用户</span>
                  <span>手机号</span>
                  <span>状态</span>
                  <span>创建时间</span>
                  <span>操作</span>
                </div>
                <div className="divide-y">
                  {users.map((user) => (
                    <div key={user.id} className="grid grid-cols-[1.4fr_1fr_0.8fr_0.9fr_0.7fr] items-center gap-3 px-4 py-3 text-sm">
                      <div className="min-w-0">
                        <p className="truncate font-medium">{user.displayName || user.id}</p>
                        <p className="truncate text-xs text-muted-foreground">{user.enterpriseName || user.enterpriseId || user.id}</p>
                      </div>
                      <span className="truncate text-muted-foreground">{user.phone || "-"}</span>
                      <Badge variant={user.status === "active" ? "default" : "secondary"} className="w-fit">
                        {statusLabel(user.status)}
                      </Badge>
                      <span className="text-muted-foreground">{formatTime(user.createdAt)}</span>
                      <Button variant="outline" size="sm" onClick={() => toggleUser(user)} disabled={state === "loading"}>
                        {user.status === "active" ? "禁用" : "启用"}
                      </Button>
                    </div>
                  ))}
                  {users.length === 0 && (
                    <div className="px-4 py-8 text-center text-sm text-muted-foreground">暂无用户</div>
                  )}
                </div>
              </CardContent>
            </Card>

            <Card className="rounded-md">
              <CardHeader className="p-4 pb-3">
                <div className="flex items-center justify-between gap-3">
                  <CardTitle className="text-base">审计日志</CardTitle>
                  <Button variant="outline" size="sm" onClick={load} disabled={state === "loading"}>
                    筛选
                  </Button>
                </div>
                <div className="grid gap-2 sm:grid-cols-3">
                  <Input
                    value={auditOperatorId}
                    onChange={(event) => setAuditOperatorId(event.target.value)}
                    placeholder="操作者"
                    className="h-8 text-xs"
                  />
                  <Input
                    value={auditAction}
                    onChange={(event) => setAuditAction(event.target.value)}
                    placeholder="动作"
                    className="h-8 text-xs"
                  />
                  <Input
                    value={auditTargetType}
                    onChange={(event) => setAuditTargetType(event.target.value)}
                    placeholder="对象"
                    className="h-8 text-xs"
                  />
                </div>
              </CardHeader>
              <CardContent className="p-0">
                <div className="divide-y">
                  {auditLogs.map((log) => (
                    <div key={log.id} className="px-4 py-3">
                      <div className="flex items-center justify-between gap-3">
                        <Badge variant="outline">{log.action}</Badge>
                        <span className="shrink-0 text-xs text-muted-foreground">{formatTime(log.createdAt)}</span>
                      </div>
                      <p className="mt-2 truncate text-sm">{log.detail || `${log.targetType || "target"} ${log.targetId || "-"}`}</p>
                      <p className="mt-1 truncate text-xs text-muted-foreground">{log.operatorId || "系统"}</p>
                    </div>
                  ))}
                  {auditLogs.length === 0 && (
                    <div className="px-4 py-8 text-center text-sm text-muted-foreground">暂无审计日志</div>
                  )}
                </div>
              </CardContent>
            </Card>
          </section>

          <section className="grid gap-3 md:grid-cols-3">
            <Card className="rounded-md">
              <CardContent className="p-4">
                <p className="text-sm text-muted-foreground">离线用户</p>
                <p className="mt-1 text-xl font-semibold">{overview.offlineUsers}</p>
              </CardContent>
            </Card>
            <Card className="rounded-md">
              <CardContent className="p-4">
                <p className="text-sm text-muted-foreground">单聊会话</p>
                <p className="mt-1 text-xl font-semibold">{overview.singleConversations}</p>
              </CardContent>
            </Card>
            <Card className="rounded-md">
              <CardContent className="p-4">
                <p className="text-sm text-muted-foreground">群聊会话</p>
                <p className="mt-1 text-xl font-semibold">{overview.groupConversations}</p>
              </CardContent>
            </Card>
          </section>
        </div>
      </ScrollArea>
    </div>
  )
}
