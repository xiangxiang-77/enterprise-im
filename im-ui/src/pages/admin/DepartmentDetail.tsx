import { useCallback, useEffect, useState } from "react"
import { ArrowLeft, ChevronRight, Send, UserPlus } from "lucide-react"
import { useNavigate, useParams } from "react-router-dom"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  type AdminDepartmentMember,
  type AdminDepartmentPermissions,
  type AdminDepartmentTreeNode,
  type AdminSession,
  addAdminDepartmentMembersApi,
  broadcastAdminDepartmentApi,
  fetchAdminDepartmentApi,
  fetchAdminDepartmentMembersApi,
  fetchAdminDepartmentsTreeApi,
  fetchDirectoryUsersApi,
  removeAdminDepartmentMemberApi,
  updateAdminDepartmentPermissionsApi,
} from "@/services/api"

function formatTime(value?: string) {
  if (!value) return "-"
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value))
}

export default function DepartmentDetail() {
  const navigate = useNavigate()
  const { id } = useParams<{ id: string }>()
  const [session] = useState<AdminSession | null>(() => {
    const raw = window.localStorage.getItem("admin-session")
    return raw ? (JSON.parse(raw) as AdminSession) : null
  })
  const [department, setDepartment] = useState<AdminDepartmentTreeNode | null>(null)
  const [members, setMembers] = useState<AdminDepartmentMember[]>([])
  const [perms, setPerms] = useState<AdminDepartmentPermissions>({
    canCreateGroup: false,
    canInviteExternal: false,
    canShareFiles: false,
    canVideoCall: false,
    storageQuotaMb: 1024,
  })
  const [broadcastMsg, setBroadcastMsg] = useState("")
  const [broadcastSent, setBroadcastSent] = useState(false)
  const [error, setError] = useState("")

  // Add member dialog state
  const [showAddDialog, setShowAddDialog] = useState(false)
  const [userSearch, setUserSearch] = useState("")
  const [searchResults, setSearchResults] = useState<Array<{ id: string; name: string; phone?: string }>>([])
  const [selectedUserIds, setSelectedUserIds] = useState<Set<string>>(new Set())
  const [searching, setSearching] = useState(false)

  const canOrganizationWrite = Boolean(session?.permissions?.includes("organization.write"))

  const loadDepartment = useCallback(async () => {
    if (!session?.token || !id) return
    setError("")
    try {
      const dept = await fetchAdminDepartmentApi(id, session.token)
      setDepartment(dept)
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载部门信息失败")
    }
  }, [session?.token, id])

  const loadMembers = useCallback(async () => {
    if (!session?.token || !id) return
    try {
      setMembers(await fetchAdminDepartmentMembersApi(id, session.token))
    } catch { /* ignore */ }
  }, [session?.token, id])

  useEffect(() => {
    void loadDepartment()
    void loadMembers()
  }, [loadDepartment, loadMembers])

  if (!session) {
    return (
      <div className="flex h-screen items-center justify-center bg-muted/20 p-4">
        <Card className="w-full max-w-md rounded-md">
          <CardHeader>
            <CardTitle className="text-base">请先登录管理台</CardTitle>
          </CardHeader>
          <CardContent>
            <Button variant="outline" onClick={() => navigate("/admin")}>返回管理台</Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  // 72. Search users for add-member dialog
  async function searchUsers() {
    if (!session?.token || !userSearch.trim()) return
    setSearching(true)
    try {
      const results = await fetchDirectoryUsersApi(session.token, { query: userSearch.trim(), limit: 20 })
      setSearchResults(results.map((u) => ({ id: u.id, name: u.name, phone: u.phone })))
    } catch {
      setSearchResults([])
    } finally {
      setSearching(false)
    }
  }

  function toggleUserSelection(userId: string) {
    setSelectedUserIds((prev) => {
      const next = new Set(prev)
      if (next.has(userId)) next.delete(userId)
      else next.add(userId)
      return next
    })
  }

  async function addMembers() {
    if (!session?.token || !id || selectedUserIds.size === 0) return
    try {
      await addAdminDepartmentMembersApi(id, Array.from(selectedUserIds), session.token)
      setSelectedUserIds(new Set())
      setShowAddDialog(false)
      setUserSearch("")
      setSearchResults([])
      await loadMembers()
    } catch (err) {
      setError(err instanceof Error ? err.message : "添加成员失败")
    }
  }

  async function removeMember(userId: string) {
    if (!session?.token || !id) return
    const member = members.find((m) => m.userId === userId)
    const ok = window.confirm(`确认将 ${member?.displayName || userId} 移出部门？`)
    if (!ok) return
    try {
      await removeAdminDepartmentMemberApi(id, userId, session.token)
      await loadMembers()
    } catch (err) {
      setError(err instanceof Error ? err.message : "移除成员失败")
    }
  }

  // 73. Save permissions
  async function savePermissions() {
    if (!session?.token || !id) return
    try {
      await updateAdminDepartmentPermissionsApi(id, perms, session.token)
      setError("权限已保存")
      setTimeout(() => setError(""), 2000)
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存权限失败")
    }
  }

  // 73. Send broadcast
  async function sendBroadcast() {
    if (!session?.token || !id || !broadcastMsg.trim()) return
    try {
      await broadcastAdminDepartmentApi(id, broadcastMsg.trim(), session.token)
      setBroadcastSent(true)
      setBroadcastMsg("")
      setTimeout(() => setBroadcastSent(false), 3000)
    } catch (err) {
      setError(err instanceof Error ? err.message : "发送广播失败")
    }
  }

  return (
    <div className="flex h-screen flex-col bg-muted/20">
      {/* Header with breadcrumb */}
      <header className="flex items-center gap-2 border-b bg-background px-4 py-3">
        <Button variant="ghost" size="icon" onClick={() => navigate("/admin")}>
          <ArrowLeft className="h-5 w-5" />
        </Button>
        <ChevronRight className="h-4 w-4 text-muted-foreground" />
        <span className="text-sm text-muted-foreground">部门管理</span>
        <ChevronRight className="h-4 w-4 text-muted-foreground" />
        <h1 className="text-base font-semibold truncate">{department?.name || id || "加载中..."}</h1>
        {department && (
          <Badge variant="secondary" className="ml-auto text-xs">{department.memberCount} 人</Badge>
        )}
      </header>

      <ScrollArea className="flex-1">
        <div className="mx-auto flex w-full max-w-5xl flex-col gap-4 p-4">
          {error && (
            <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">{error}</div>
          )}
          {broadcastSent && (
            <div className="rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">广播已发送</div>
          )}

          {/* 71. Member Management Panel */}
          <Card className="rounded-md">
            <CardHeader className="p-4 pb-3">
              <div className="flex items-center justify-between gap-3">
                <CardTitle className="text-base">部门成员</CardTitle>
                <Button
                  size="sm"
                  onClick={() => setShowAddDialog(true)}
                  disabled={!canOrganizationWrite}
                >
                  <UserPlus className="mr-1 h-4 w-4" />
                  添加成员
                </Button>
              </div>
            </CardHeader>
            <CardContent className="p-0">
              <div className="grid grid-cols-[1fr_0.8fr_1fr_auto] border-b px-4 py-2 text-xs font-medium text-muted-foreground">
                <span>姓名</span>
                <span>角色</span>
                <span>加入时间</span>
                <span>操作</span>
              </div>
              <div className="divide-y">
                {members.map((member) => (
                  <div key={member.userId} className="grid grid-cols-[1fr_0.8fr_1fr_auto] items-center gap-3 px-4 py-3 text-sm">
                    <span className="truncate font-medium">{member.displayName || member.userId}</span>
                    <Badge variant="outline" className="w-fit">{member.role || "成员"}</Badge>
                    <span className="text-muted-foreground">{formatTime(member.joinedAt)}</span>
                    <Button
                      variant="destructive"
                      size="sm"
                      onClick={() => removeMember(member.userId)}
                      disabled={!canOrganizationWrite}
                    >
                      移除
                    </Button>
                  </div>
                ))}
                {members.length === 0 && (
                  <div className="px-4 py-8 text-center text-sm text-muted-foreground">暂无成员</div>
                )}
              </div>
            </CardContent>
          </Card>

          {/* 73. Permission Configuration */}
          <Card className="rounded-md">
            <CardHeader className="p-4 pb-3">
              <CardTitle className="text-base">权限配置</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4 p-4 pt-0 sm:grid-cols-2">
              <PermToggle
                label="允许创建群组"
                checked={perms.canCreateGroup}
                onChange={() => setPerms((p) => ({ ...p, canCreateGroup: !p.canCreateGroup }))}
              />
              <PermToggle
                label="允许邀请外部"
                checked={perms.canInviteExternal}
                onChange={() => setPerms((p) => ({ ...p, canInviteExternal: !p.canInviteExternal }))}
              />
              <PermToggle
                label="允许分享文件"
                checked={perms.canShareFiles}
                onChange={() => setPerms((p) => ({ ...p, canShareFiles: !p.canShareFiles }))}
              />
              <PermToggle
                label="允许视频通话"
                checked={perms.canVideoCall}
                onChange={() => setPerms((p) => ({ ...p, canVideoCall: !p.canVideoCall }))}
              />
              <div className="flex items-center gap-3 rounded-md border p-3">
                <label className="text-sm font-medium">存储配额 (MB)</label>
                <Input
                  type="number"
                  className="h-8 w-28"
                  value={perms.storageQuotaMb}
                  onChange={(e) => setPerms((p) => ({ ...p, storageQuotaMb: Number(e.target.value) || 0 }))}
                />
              </div>
              <div className="flex items-end sm:col-span-2">
                <Button onClick={savePermissions} disabled={!canOrganizationWrite}>
                  保存权限
                </Button>
              </div>
            </CardContent>
          </Card>

          {/* 73. Broadcast Panel */}
          <Card className="rounded-md">
            <CardHeader className="p-4 pb-3">
              <CardTitle className="text-base">广播消息</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-3 p-4 pt-0">
              <textarea
                className="min-h-24 rounded-md border bg-background p-3 text-sm"
                value={broadcastMsg}
                onChange={(e) => setBroadcastMsg(e.target.value)}
                placeholder="输入要广播的消息内容..."
              />
              <div>
                <Button onClick={sendBroadcast} disabled={!canOrganizationWrite || !broadcastMsg.trim()}>
                  <Send className="mr-1 h-4 w-4" />
                  发送广播
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      </ScrollArea>

      {/* Add Member Dialog */}
      {showAddDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={() => setShowAddDialog(false)}>
          <div className="w-full max-w-md rounded-md border bg-background p-6 shadow-lg" onClick={(e) => e.stopPropagation()}>
            <h2 className="text-lg font-semibold">添加成员</h2>
            <div className="mt-4 flex gap-2">
              <Input
                value={userSearch}
                onChange={(e) => setUserSearch(e.target.value)}
                placeholder="搜索用户姓名或手机号..."
                className="h-9 flex-1"
                onKeyDown={(e) => { if (e.key === "Enter") void searchUsers() }}
              />
              <Button size="sm" onClick={() => void searchUsers()} disabled={searching || !userSearch.trim()}>
                {searching ? "搜索中..." : "搜索"}
              </Button>
            </div>
            {selectedUserIds.size > 0 && (
              <div className="mt-2 text-sm text-muted-foreground">
                已选择 {selectedUserIds.size} 个用户
              </div>
            )}
            <div className="mt-3 max-h-60 divide-y overflow-y-auto rounded-md border">
              {searchResults.map((user) => {
                const selected = selectedUserIds.has(user.id)
                return (
                  <div
                    key={user.id}
                    className={`flex cursor-pointer items-center gap-3 px-3 py-2 text-sm hover:bg-muted/50 ${selected ? "bg-primary/5" : ""}`}
                    onClick={() => toggleUserSelection(user.id)}
                  >
                    <div className={`flex h-5 w-5 items-center justify-center rounded border ${selected ? "border-primary bg-primary text-primary-foreground" : "border-input"}`}>
                      {selected && <span className="text-xs">✓</span>}
                    </div>
                    <div className="min-w-0">
                      <p className="truncate font-medium">{user.name}</p>
                      <p className="truncate text-xs text-muted-foreground">{user.id}</p>
                    </div>
                    {user.phone && <span className="ml-auto shrink-0 text-xs text-muted-foreground">{user.phone}</span>}
                  </div>
                )
              })}
              {searchResults.length === 0 && userSearch.trim() && !searching && (
                <div className="px-3 py-6 text-center text-sm text-muted-foreground">未找到匹配用户</div>
              )}
            </div>
            <div className="mt-4 flex justify-end gap-2">
              <Button variant="outline" onClick={() => { setShowAddDialog(false); setUserSearch(""); setSearchResults([]); setSelectedUserIds(new Set()) }}>
                取消
              </Button>
              <Button onClick={() => void addMembers()} disabled={selectedUserIds.size === 0}>
                添加 ({selectedUserIds.size})
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function PermToggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: () => void }) {
  return (
    <div className="flex items-center justify-between rounded-md border p-3">
      <span className="text-sm font-medium">{label}</span>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={onChange}
        className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors ${checked ? "bg-primary" : "bg-muted"}`}
      >
        <span className={`inline-block h-4 w-4 rounded-full bg-background shadow transition-transform ${checked ? "translate-x-[22px]" : "translate-x-[4px]"}`} />
      </button>
    </div>
  )
}
