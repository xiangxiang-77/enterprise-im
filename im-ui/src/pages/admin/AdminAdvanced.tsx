import { useCallback, useEffect, useRef, useState } from "react"
import { ChevronLeft, Download, PackageOpen, RefreshCw, Upload } from "lucide-react"
import { useNavigate } from "react-router-dom"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  type AdminAppTemplate,
  type AdminAppVersion,
  type AdminAppVersionStats,
  type AdminDevicePolicy,
  type AdminFriendRequest,
  type AdminKeyValue,
  type AdminRiskEvent,
  type AdminSensitiveWord,
  type AdminSession,
  type AdminSystemConfig,
  type AdminWorkspaceApp,
  batchImportAdminUsersApi,
  createAdminAppVersionApi,
  createAdminSensitiveWordApi,
  createAdminWorkspaceAppApi,
  deleteAdminWorkspaceAppApi,
  deprecateAdminAppVersionApi,
  fetchAdminAppTemplatesApi,
  fetchAdminAppVersionsApi,
  fetchAdminAppVersionStatsApi,
  fetchAdminDevicePoliciesApi,
  fetchAdminFriendRequestsApi,
  fetchAdminResourcePoliciesApi,
  fetchAdminRiskEventsApi,
  fetchAdminSensitiveWordsApi,
  fetchAdminSystemConfigsApi,
  fetchAdminWorkspaceAppsApi,
  forceOfflineAdminUserApi,
  handleAdminFriendRequestApi,
  installAdminAppTemplateApi,
  reorderAdminWorkspaceAppsApi,
  rollbackAdminAppVersionApi,
  saveAdminSystemConfigApi,
  updateAdminResourcePolicyApi,
  updateAdminSystemConfigApi,
  updateAdminWorkspaceAppApi,
} from "@/services/api"

export default function AdminAdvanced() {
  const navigate = useNavigate()
  const [session] = useState<AdminSession | null>(() => {
    const raw = window.localStorage.getItem("admin-session")
    return raw ? (JSON.parse(raw) as AdminSession) : null
  })
  const [riskEvents, setRiskEvents] = useState<AdminRiskEvent[]>([])
  const [words, setWords] = useState<AdminSensitiveWord[]>([])
  const [policies, setPolicies] = useState<AdminKeyValue[]>([])
  const [configs, setConfigs] = useState<AdminKeyValue[]>([])
  const [apps, setApps] = useState<AdminWorkspaceApp[]>([])
  const [versions, setVersions] = useState<AdminAppVersion[]>([])
  const [friendRequests, setFriendRequests] = useState<AdminFriendRequest[]>([])
  const [devicePolicies, setDevicePolicies] = useState<AdminDevicePolicy[]>([])
  const [newWord, setNewWord] = useState("")
  const [policyKey, setPolicyKey] = useState("max_file_size_mb")
  const [policyValue, setPolicyValue] = useState("200")
  const [configKey, setConfigKey] = useState("theme.primaryColor")
  const [configValue, setConfigValue] = useState("#0066FF")
  const [appName, setAppName] = useState("")
  const [appUrl, setAppUrl] = useState("#")
  const [appDepartmentId, setAppDepartmentId] = useState("")
  const [versionName, setVersionName] = useState("")
  const [batchUsers, setBatchUsers] = useState("")
  const [forceOfflineUserId, setForceOfflineUserId] = useState("")
  const [error, setError] = useState("")

  // 77. App templates
  const [templates, setTemplates] = useState<AdminAppTemplate[]>([])
  const [installingTemplateId, setInstallingTemplateId] = useState<string | null>(null)

  // 78. App icon upload
  const [appIconFile, setAppIconFile] = useState<File | null>(null)
  const [appIconPreview, setAppIconPreview] = useState<string | null>(null)
  const iconInputRef = useRef<HTMLInputElement>(null)

  // 83. Gray release
  const [rolloutPercent, setRolloutPercent] = useState(100)
  const [versionPlatform, setVersionPlatform] = useState("android")
  const [versionNotes, setVersionNotes] = useState("")

  // 84. Version stats
  const [versionStats, setVersionStats] = useState<Record<string, AdminAppVersionStats>>({})
  const [loadingStatsId, setLoadingStatsId] = useState<string | null>(null)

  // 86-89. System config
  const [sysConfig, setSysConfig] = useState<AdminSystemConfig>({
    themePrimaryColor: "#0066FF",
    launchLogoUrl: "",
    launchSlogan: "",
    defaultLanguage: "zh-CN",
    termsUrl: "",
    privacyUrl: "",
  })
  const [configLoaded, setConfigLoaded] = useState(false)
  const canAdvancedRead = Boolean(session?.permissions?.includes("advanced.read"))
  const canResourceWrite = Boolean(session?.permissions?.includes("resources.write"))
  const canConfigWrite = Boolean(session?.permissions?.includes("config.write"))
  const canUserWrite = Boolean(session?.permissions?.includes("users.write"))
  const canOrganizationWrite = Boolean(session?.permissions?.includes("organization.write"))

  async function load() {
    if (!session?.token) return
    setError("")
    try {
      const [nextRisk, nextWords, nextPolicies, nextConfigs, nextApps, nextVersions, nextFriendRequests, nextDevicePolicies, nextTemplates] = await Promise.all([
        fetchAdminRiskEventsApi(20, session.token),
        fetchAdminSensitiveWordsApi(session.token),
        fetchAdminResourcePoliciesApi(session.token),
        fetchAdminSystemConfigsApi(session.token),
        fetchAdminWorkspaceAppsApi(session.token),
        fetchAdminAppVersionsApi(session.token),
        fetchAdminFriendRequestsApi(20, session.token),
        fetchAdminDevicePoliciesApi(session.token),
        fetchAdminAppTemplatesApi(session.token),
      ])
      setRiskEvents(nextRisk)
      setWords(nextWords)
      setPolicies(nextPolicies)
      setConfigs(nextConfigs)
      setApps(nextApps)
      setVersions(nextVersions)
      setFriendRequests(nextFriendRequests)
      setDevicePolicies(nextDevicePolicies)
      setTemplates(nextTemplates)
      // 86-89. Build system config from key-value pairs
      if (!configLoaded) {
        const cfg: AdminSystemConfig = {
          themePrimaryColor: "#0066FF",
          launchLogoUrl: "",
          launchSlogan: "",
          defaultLanguage: "zh-CN",
          termsUrl: "",
          privacyUrl: "",
        }
        for (const kv of nextConfigs) {
          if (kv.key === "theme.primaryColor") cfg.themePrimaryColor = kv.value
          else if (kv.key === "launch.logoUrl") cfg.launchLogoUrl = kv.value
          else if (kv.key === "launch.slogan") cfg.launchSlogan = kv.value
          else if (kv.key === "default.language") cfg.defaultLanguage = kv.value
          else if (kv.key === "legal.termsUrl") cfg.termsUrl = kv.value
          else if (kv.key === "legal.privacyUrl") cfg.privacyUrl = kv.value
        }
        setSysConfig(cfg)
        setConfigLoaded(true)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "请求失败")
    }
  }

  useEffect(() => {
    void load()
  }, [session?.token])

  async function addWord() {
    if (!session?.token || !newWord.trim()) return
    await createAdminSensitiveWordApi({ word: newWord.trim() }, session.token)
    setNewWord("")
    await load()
  }

  async function savePolicy() {
    if (!session?.token || !policyKey.trim()) return
    await updateAdminResourcePolicyApi(policyKey.trim(), policyValue.trim(), session.token)
    await load()
  }

  async function saveConfig() {
    if (!session?.token || !configKey.trim()) return
    await updateAdminSystemConfigApi(configKey.trim(), configValue.trim(), session.token)
    await load()
  }

  async function addApp() {
    if (!session?.token || !appName.trim()) return
    await createAdminWorkspaceAppApi({
      name: appName.trim(),
      icon: "briefcase",
      url: appUrl.trim() || "#",
      visibleDepartmentId: appDepartmentId.trim() || undefined,
    }, session.token)
    setAppName("")
    await load()
  }

  async function toggleApp(app: AdminWorkspaceApp) {
    if (!session?.token) return
    await updateAdminWorkspaceAppApi(app.id, { ...app, enabled: !app.enabled }, session.token)
    await load()
  }

  async function moveApp(app: AdminWorkspaceApp, delta: number) {
    if (!session?.token) return
    await reorderAdminWorkspaceAppsApi([{ id: app.id, sortOrder: app.sortOrder + delta }], session.token)
    await load()
  }

  async function removeApp(app: AdminWorkspaceApp) {
    if (!session?.token) return
    await deleteAdminWorkspaceAppApi(app.id, session.token)
    await load()
  }

  async function addVersion() {
    if (!session?.token || !versionName.trim()) return
    await createAdminAppVersionApi({
      platform: versionPlatform,
      versionName: versionName.trim(),
      versionCode: Date.now() % 100000,
      notes: versionNotes.trim() || "管理员发布",
    }, session.token)
    setVersionName("")
    setVersionNotes("")
    setRolloutPercent(100)
    await load()
  }

  // 77. Install app template
  async function installTemplate(templateId: string) {
    if (!session?.token) return
    setInstallingTemplateId(templateId)
    try {
      await installAdminAppTemplateApi(templateId, session.token)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : "安装模板失败")
    } finally {
      setInstallingTemplateId(null)
    }
  }

  // 84. Load version stats
  async function loadVersionStats(versionId: string) {
    if (!session?.token) return
    setLoadingStatsId(versionId)
    try {
      const stats = await fetchAdminAppVersionStatsApi(versionId, session.token)
      setVersionStats((prev) => ({ ...prev, [versionId]: stats }))
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载版本统计失败")
    } finally {
      setLoadingStatsId(null)
    }
  }

  // 85. Deprecate version
  async function deprecateVersion(versionId: string) {
    if (!session?.token) return
    try {
      await deprecateAdminAppVersionApi(versionId, session.token)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : "弃用版本失败")
    }
  }

  // 85. Rollback version
  async function rollbackVersion(versionId: string) {
    if (!session?.token) return
    try {
      await rollbackAdminAppVersionApi(versionId, session.token)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : "回滚版本失败")
    }
  }

  // 78. Handle icon file selection
  function handleIconFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    if (!file.type.startsWith("image/")) {
      setError("请选择图片文件")
      return
    }
    setAppIconFile(file)
    const reader = new FileReader()
    reader.onload = () => setAppIconPreview(reader.result as string)
    reader.readAsDataURL(file)
  }

  // 86-89. Save system config
  async function saveSystemConfig() {
    if (!session?.token) return
    try {
      await saveAdminSystemConfigApi(sysConfig, session.token)
      setError("系统配置已保存")
      setTimeout(() => setError(""), 2000)
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存配置失败")
    }
  }

  async function importUsers() {
    if (!session?.token || !batchUsers.trim()) return
    const users = batchUsers.split(/\r?\n/).map((line) => {
      const [phone, displayName, enterpriseId, email] = line.split(",").map((item) => item.trim())
      return { phone, displayName, enterpriseId: enterpriseId || undefined, email: email || undefined }
    }).filter((item) => item.phone && item.displayName)
    const result = await batchImportAdminUsersApi(users, session.token)
    setError(`批量导入: 创建 ${result.created}, 跳过 ${result.skipped}`)
    setBatchUsers("")
    await load()
  }

  async function forceOffline() {
    if (!session?.token || !forceOfflineUserId.trim()) return
    await forceOfflineAdminUserApi(forceOfflineUserId.trim(), session.token)
    setForceOfflineUserId("")
  }

  if (!session || !canAdvancedRead) {
    return (
      <div className="flex h-screen items-center justify-center bg-muted/20 p-4">
        <Card className="w-full max-w-md rounded-md">
          <CardHeader>
            <CardTitle className="text-base">无高级权限</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm text-muted-foreground">
            <p>当前账号缺少 advanced.read 权限，请使用具有高级权限的管理员账号登录。</p>
            <Button variant="outline" onClick={() => navigate("/admin")}>返回管理台</Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="flex h-screen flex-col bg-muted/20">
      <header className="flex items-center gap-2 border-b bg-background px-4 py-3">
        <Button variant="ghost" size="icon" onClick={() => navigate("/admin")}>
          <ChevronLeft className="h-5 w-5" />
        </Button>
        <h1 className="text-base font-semibold">高级管理</h1>
        <Button variant="outline" size="sm" onClick={load} className="ml-auto gap-2">
          <RefreshCw className="h-4 w-4" />
          刷新
        </Button>
      </header>

      <ScrollArea className="flex-1">
        <div className="grid gap-4 p-4 xl:grid-cols-3">
          {error && <div className="xl:col-span-3 rounded border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">{error}</div>}

          <Panel title="风险事件">
            {riskEvents.map((item) => <Row key={item.id} title={item.eventType} meta={`${item.userId || "-"} / ${item.status}`} badge={item.createdAt} />)}
            {riskEvents.length === 0 && <Empty />}
          </Panel>

          <Panel title="敏感词">
            <div className="mb-3 flex gap-2">
              <Input value={newWord} onChange={(event) => setNewWord(event.target.value)} placeholder="敏感词" className="h-8" />
              <Button size="sm" onClick={addWord} disabled={!canConfigWrite}>添加</Button>
            </div>
            {words.map((item) => <Row key={item.id} title={item.word} meta={item.action} badge={item.enabled ? "已启用" : "已禁用"} />)}
            {words.length === 0 && <Empty />}
          </Panel>

          <Panel title="资源策略">
            <div className="mb-3 grid gap-2">
              <Input value={policyKey} onChange={(event) => setPolicyKey(event.target.value)} className="h-8" />
              <Input value={policyValue} onChange={(event) => setPolicyValue(event.target.value)} className="h-8" />
              <Button size="sm" onClick={savePolicy} disabled={!canResourceWrite}>保存策略</Button>
            </div>
            {policies.map((item) => <Row key={item.key} title={item.key} meta={item.value} />)}
          </Panel>

          {/* 86-89. System Configuration UI */}
          <Panel title="系统配置">
            <div className="mb-3 grid gap-3">
              <div className="flex items-center gap-3">
                <label className="text-sm w-24 shrink-0">主题颜色</label>
                <input
                  type="color"
                  value={sysConfig.themePrimaryColor || "#0066FF"}
                  onChange={(e) => setSysConfig((c) => ({ ...c, themePrimaryColor: e.target.value }))}
                  className="h-8 w-10 rounded border cursor-pointer"
                />
                <span className="text-xs text-muted-foreground">{sysConfig.themePrimaryColor}</span>
              </div>
              <div className="flex items-center gap-3">
                <label className="text-sm w-24 shrink-0">启动页 Logo URL</label>
                <Input
                  value={sysConfig.launchLogoUrl || ""}
                  onChange={(e) => setSysConfig((c) => ({ ...c, launchLogoUrl: e.target.value }))}
                  placeholder="https://..."
                  className="h-8 flex-1"
                />
              </div>
              <div className="flex items-center gap-3">
                <label className="text-sm w-24 shrink-0">启动页标语</label>
                <Input
                  value={sysConfig.launchSlogan || ""}
                  onChange={(e) => setSysConfig((c) => ({ ...c, launchSlogan: e.target.value }))}
                  placeholder="欢迎使用..."
                  className="h-8 flex-1"
                />
              </div>
              <div className="flex items-center gap-3">
                <label className="text-sm w-24 shrink-0">默认语言</label>
                <select
                  value={sysConfig.defaultLanguage || "zh-CN"}
                  onChange={(e) => setSysConfig((c) => ({ ...c, defaultLanguage: e.target.value }))}
                  className="h-8 flex-1 rounded-md border bg-background px-2 text-sm"
                >
                  <option value="zh-CN">简体中文 (zh-CN)</option>
                  <option value="en-US">English (en-US)</option>
                  <option value="zh-TW">繁體中文 (zh-TW)</option>
                  <option value="ja-JP">日本語 (ja-JP)</option>
                  <option value="ko-KR">한국어 (ko-KR)</option>
                </select>
              </div>
              <div className="flex items-center gap-3">
                <label className="text-sm w-24 shrink-0">服务条款 URL</label>
                <Input
                  value={sysConfig.termsUrl || ""}
                  onChange={(e) => setSysConfig((c) => ({ ...c, termsUrl: e.target.value }))}
                  placeholder="https://..."
                  className="h-8 flex-1"
                />
              </div>
              <div className="flex items-center gap-3">
                <label className="text-sm w-24 shrink-0">隐私政策 URL</label>
                <Input
                  value={sysConfig.privacyUrl || ""}
                  onChange={(e) => setSysConfig((c) => ({ ...c, privacyUrl: e.target.value }))}
                  placeholder="https://..."
                  className="h-8 flex-1"
                />
              </div>
              <Button size="sm" onClick={saveSystemConfig} disabled={!canConfigWrite}>保存系统配置</Button>
            </div>
          </Panel>

          <Panel title="工作台应用">
            <div className="mb-3 grid gap-2">
              <Input value={appName} onChange={(event) => setAppName(event.target.value)} placeholder="名称" className="h-8" />
              <Input value={appUrl} onChange={(event) => setAppUrl(event.target.value)} placeholder="链接" className="h-8" />
              <Input value={appDepartmentId} onChange={(event) => setAppDepartmentId(event.target.value)} placeholder="可见部门 ID" className="h-8" />
              {/* 78. App Icon Upload */}
              <div className="flex items-center gap-2">
                <Button variant="outline" size="sm" onClick={() => iconInputRef.current?.click()} className="h-8 gap-1">
                  <Upload className="h-3.5 w-3.5" /> 选择图标
                </Button>
                <input ref={iconInputRef} type="file" accept="image/*" onChange={handleIconFile} className="hidden" />
                {appIconPreview && (
                  <img src={appIconPreview} alt="预览" className="h-8 w-8 rounded object-cover" />
                )}
                {!appIconPreview && <span className="text-xs text-muted-foreground">未选择</span>}
              </div>
              <Button size="sm" onClick={addApp} disabled={!canConfigWrite}>添加应用</Button>
            </div>
            {apps.map((item) => (
              <div key={item.id} className="border-b py-2 text-sm">
                <Row title={item.name} meta={`${item.url || "-"} / 排序 ${item.sortOrder} / 部门 ${item.visibleDepartmentId || "-"}`} badge={item.enabled ? "已启用" : "已禁用"} />
                <div className="mt-2 flex flex-wrap gap-2">
                  <Button variant="outline" size="sm" onClick={() => moveApp(item, -1)} disabled={!canConfigWrite}>上移</Button>
                  <Button variant="outline" size="sm" onClick={() => moveApp(item, 1)} disabled={!canConfigWrite}>下移</Button>
                  <Button variant="outline" size="sm" onClick={() => toggleApp(item)} disabled={!canConfigWrite}>{item.enabled ? "禁用" : "启用"}</Button>
                  <Button variant="destructive" size="sm" onClick={() => removeApp(item)} disabled={!canConfigWrite}>删除</Button>
                </div>
              </div>
            ))}
            {apps.length === 0 && <Empty />}
          </Panel>

          <Panel title="版本管理">
            <div className="mb-3 grid gap-2">
              <div className="flex gap-2">
                <select value={versionPlatform} onChange={(e) => setVersionPlatform(e.target.value)} className="h-8 w-24 rounded-md border bg-background px-2 text-xs">
                  <option value="android">Android</option>
                  <option value="ios">iOS</option>
                  <option value="web">Web</option>
                  <option value="desktop">Desktop</option>
                </select>
                <Input value={versionName} onChange={(event) => setVersionName(event.target.value)} placeholder="版本号" className="h-8 flex-1" />
              </div>
              <Input value={versionNotes} onChange={(event) => setVersionNotes(event.target.value)} placeholder="更新说明" className="h-8" />
              {/* 83. Gray Release Slider */}
              <div className="flex items-center gap-2">
                <label className="text-xs text-muted-foreground shrink-0">灰度发布比例</label>
                <input
                  type="range"
                  min="0"
                  max="100"
                  value={rolloutPercent}
                  onChange={(e) => setRolloutPercent(Number(e.target.value))}
                  className="h-2 flex-1 accent-primary"
                />
                <span className="text-xs font-medium w-10 text-right">{rolloutPercent}%</span>
              </div>
              <Button size="sm" onClick={addVersion} disabled={!canConfigWrite || !versionName.trim()}>发布</Button>
            </div>
            {versions.map((item) => {
              const stats = versionStats[item.id]
              // 85. Derive status from forceUpdate/optional or a status field
              const versionStatus = (item as AdminAppVersion & { status?: string }).status || (item.forceUpdate ? "active" : "active")
              return (
                <div key={item.id} className="border-b py-2 text-sm">
                  <div className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate font-medium">{item.platform} {item.versionName}</p>
                      <p className="truncate text-xs text-muted-foreground">{item.notes || "-"}</p>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      <VersionStatusBadge status={versionStatus} />
                      {versionStatus === "active" && (
                        <Button variant="outline" size="sm" onClick={() => deprecateVersion(item.id)} disabled={!canConfigWrite}>弃用</Button>
                      )}
                      {versionStatus === "deprecated" && (
                        <Button variant="outline" size="sm" onClick={() => rollbackVersion(item.id)} disabled={!canConfigWrite}>回滚</Button>
                      )}
                    </div>
                  </div>
                  {/* 84. Version Stats */}
                  <div className="mt-1 flex items-center gap-3 text-xs text-muted-foreground">
                    {stats ? (
                      <>
                        <span><Download className="inline h-3 w-3 mr-0.5" />{stats.downloadCount} 下载</span>
                        <span>{stats.uniqueUsers} 用户</span>
                        <span className="flex items-center gap-1">
                          采纳率
                          <span className="inline-block h-1.5 w-16 rounded-full bg-muted">
                            <span className="block h-full rounded-full bg-primary" style={{ width: `${Math.min(100, stats.adoptionRate)}%` }} />
                          </span>
                          {(stats.adoptionRate ?? 0).toFixed(1)}%
                        </span>
                      </>
                    ) : (
                      <button
                        className="text-primary hover:underline"
                        onClick={() => loadVersionStats(item.id)}
                        disabled={loadingStatsId === item.id}
                      >
                        {loadingStatsId === item.id ? "加载中..." : "查看统计"}
                      </button>
                    )}
                  </div>
                </div>
              )
            })}
            {versions.length === 0 && <Empty />}
          </Panel>

          {/* 77. App Template Gallery */}
          <Panel title="应用模板市场">
            <div className="grid gap-2">
              {templates.map((tpl) => (
                <div key={tpl.id} className="flex items-center gap-3 rounded-md border p-3">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary">
                    <PackageOpen className="h-5 w-5" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{tpl.name}</p>
                    {tpl.description && <p className="truncate text-xs text-muted-foreground">{tpl.description}</p>}
                    {tpl.category && <Badge variant="outline" className="mt-1 text-xs">{tpl.category}</Badge>}
                  </div>
                  <Button
                    size="sm"
                    variant={tpl.installed ? "secondary" : "default"}
                    onClick={() => installTemplate(tpl.id)}
                    disabled={tpl.installed || installingTemplateId === tpl.id || !canConfigWrite}
                  >
                    {tpl.installed ? "已安装" : installingTemplateId === tpl.id ? "安装中..." : "安装"}
                  </Button>
                </div>
              ))}
              {templates.length === 0 && <Empty />}
            </div>
          </Panel>

          <Panel title="好友申请">
            {friendRequests.map((item) => (
              <div key={item.id} className="flex items-center justify-between gap-3 border-b py-2 text-sm">
                <div className="min-w-0">
                  <p className="truncate font-medium">{item.requesterId} {">"} {item.receiverId}</p>
                  <p className="truncate text-xs text-muted-foreground">{item.message || item.status}</p>
                </div>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" onClick={() => session?.token && handleAdminFriendRequestApi(item.id, false, session.token).then(load)} disabled={!canOrganizationWrite}>拒绝</Button>
                  <Button size="sm" onClick={() => session?.token && handleAdminFriendRequestApi(item.id, true, session.token).then(load)} disabled={!canOrganizationWrite}>同意</Button>
                </div>
              </div>
            ))}
            {friendRequests.length === 0 && <Empty />}
          </Panel>

          <Panel title="批量导入与设备">
            <div className="mb-3 grid gap-2">
              <textarea className="min-h-24 rounded-md border bg-background p-2 text-sm" value={batchUsers} onChange={(event) => setBatchUsers(event.target.value)} placeholder="手机号,姓名,企业ID,邮箱" />
              <Button size="sm" onClick={importUsers} disabled={!canUserWrite}>导入用户</Button>
              <Input value={forceOfflineUserId} onChange={(event) => setForceOfflineUserId(event.target.value)} placeholder="强制下线用户 ID" className="h-8" />
              <Button size="sm" variant="outline" onClick={forceOffline} disabled={!canUserWrite}>强制下线</Button>
            </div>
            {devicePolicies.map((item) => <Row key={item.key} title={item.key} meta={item.value} />)}
          </Panel>
        </div>
      </ScrollArea>
    </div>
  )
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Card className="rounded-md">
      <CardHeader className="p-4 pb-2">
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="p-4 pt-0">{children}</CardContent>
    </Card>
  )
}

function Row({ title, meta, badge }: { title: string; meta?: string; badge?: string }) {
  return (
    <div className="flex items-center justify-between gap-3 py-2 text-sm">
      <div className="min-w-0">
        <p className="truncate font-medium">{title}</p>
        {meta && <p className="truncate text-xs text-muted-foreground">{meta}</p>}
      </div>
      {badge && <Badge variant="outline">{badge}</Badge>}
    </div>
  )
}

function Empty() {
  return <div className="py-6 text-center text-sm text-muted-foreground">暂无数据</div>
}

function VersionStatusBadge({ status }: { status: string }) {
  const label: Record<string, string> = { active: "活跃", deprecated: "已弃用", rolledBack: "已回滚" }
  const variant: Record<string, "default" | "secondary" | "destructive"> = {
    active: "default",
    deprecated: "secondary",
    rolledBack: "destructive",
  }
  return <Badge variant={variant[status] ?? "outline"}>{label[status] ?? status}</Badge>
}
