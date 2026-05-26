import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { ChevronLeft, LogOut, Trash2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Slider } from "@/components/ui/slider"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { useAppSettingsStore } from "@/stores/useAppSettingsStore"
import { useAuthStore } from "@/stores/useAuthStore"
import { useTheme } from "@/components/theme-provider"

const fontIndex = ["small", "standard", "large", "extra"] as const

function calculateCacheSize(): number {
  let total = 0
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (key) {
      const value = localStorage.getItem(key) || ""
      total += key.length + value.length
    }
  }
  return Math.round((total * 2) / 1024 / 1024 * 100) / 100 // MB
}

export default function GeneralSettings() {
  const navigate = useNavigate()
  const { language, fontSize, cacheSizeMb, chatBackground, chatBackgroundBlur, setLanguage, setFontSize, setChatBackground, setChatBackgroundBlur, clearCache } = useAppSettingsStore()
  const logout = useAuthStore((state) => state.logout)
  const { theme, setTheme } = useTheme()
  const currentFontIndex = Math.max(0, fontIndex.indexOf(fontSize))
  const [realCacheSize, setRealCacheSize] = useState(0)
  const [agreementOpen, setAgreementOpen] = useState<"user" | "privacy" | null>(null)

  useEffect(() => {
    setRealCacheSize(calculateCacheSize())
  }, [])

  function signOut() {
    logout()
    navigate("/auth/login")
  }

  function handleClearCache() {
    clearCache()
    setRealCacheSize(0)
  }

  return (
    <div className="flex h-full flex-col bg-muted/20">
      <header className="sticky top-0 z-10 flex items-center gap-2 border-b bg-background px-4 py-3">
        <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="-ml-2">
          <ChevronLeft className="h-6 w-6" />
        </Button>
        <h2 className="font-semibold">通用设置</h2>
      </header>

      <ScrollArea className="flex-1">
        <div className="space-y-5 py-4">
          <section className="divide-y border-y bg-background">
            <div className="flex items-center justify-between px-4 py-3">
              <div>
                <p className="text-sm font-medium">深色模式</p>
                <p className="text-xs text-muted-foreground">跟随系统主题配置</p>
              </div>
              <select value={theme} onChange={(event) => setTheme(event.target.value as "dark" | "light" | "system")} className="rounded border bg-background px-2 py-1 text-sm">
                <option value="light">浅色</option>
                <option value="dark">深色</option>
                <option value="system">跟随系统</option>
              </select>
            </div>
            <label className="flex items-center justify-between px-4 py-3 text-sm">
              多语言
              <select value={language} onChange={(event) => setLanguage(event.target.value as typeof language)} className="rounded border bg-background px-2 py-1 text-sm">
                <option value="system">跟随系统</option>
                <option value="zh-CN">简体中文</option>
                <option value="en-US">英语 (English)</option>
              </select>
            </label>
            <div className="px-4 py-4">
              <div className="mb-3 flex items-center justify-between text-sm">
                <span>字体大小</span>
                <span className="text-muted-foreground">{fontSize}</span>
              </div>
              <Slider value={[currentFontIndex]} min={0} max={3} step={1} onValueChange={([value]) => setFontSize(fontIndex[value] ?? "standard")} />
            </div>
          </section>

          <section className="divide-y border-y bg-background">
            <div className="px-4 py-3">
              <p className="text-sm font-medium mb-2">聊天背景</p>
              <div className="flex gap-2 mb-3">
                {["default", "color1", "color2", "color3"].map((bg) => (
                  <button
                    key={bg}
                    className={`h-14 w-14 rounded-lg border-2 transition-all ${
                      chatBackground === bg ? "border-primary" : "border-transparent"
                    } ${
                      bg === "default" ? "bg-muted" :
                      bg === "color1" ? "bg-gradient-to-br from-blue-50 to-indigo-100" :
                      bg === "color2" ? "bg-gradient-to-br from-green-50 to-emerald-100" :
                      "bg-gradient-to-br from-orange-50 to-amber-100"
                    }`}
                    onClick={() => setChatBackground(bg)}
                  />
                ))}
              </div>
              <div className="flex items-center gap-3">
                <span className="text-xs text-muted-foreground whitespace-nowrap">模糊 {chatBackgroundBlur}px</span>
                <Slider value={[chatBackgroundBlur]} min={0} max={10} step={1} onValueChange={([v]) => setChatBackgroundBlur(v)} />
              </div>
            </div>
            <div className="flex items-center justify-between px-4 py-3">
              <div>
                <p className="text-sm font-medium">存储管理</p>
                <p className="text-xs text-muted-foreground">缓存 {realCacheSize} MB</p>
              </div>
              <Button variant="outline" size="sm" onClick={handleClearCache} className="gap-2">
                <Trash2 className="h-4 w-4" />
                清理
              </Button>
            </div>
            <div className="px-4 py-3 text-sm">
              <p className="font-medium">版本更新</p>
              <p className="text-xs text-muted-foreground">当前版本 0.1.0，后台可配置强制/可选更新</p>
            </div>
            <button className="w-full px-4 py-3 text-left text-sm" onClick={() => setAgreementOpen("user")}>
              <p className="font-medium">用户协议</p>
              <p className="text-xs text-muted-foreground">点击查看完整用户协议</p>
            </button>
            <button className="w-full px-4 py-3 text-left text-sm" onClick={() => setAgreementOpen("privacy")}>
              <p className="font-medium">隐私政策</p>
              <p className="text-xs text-muted-foreground">点击查看完整隐私政策</p>
            </button>
          </section>

          <div className="px-4">
            <Button variant="destructive" className="w-full gap-2" onClick={signOut}>
              <LogOut className="h-4 w-4" />
              退出登录
            </Button>
          </div>
        </div>
      </ScrollArea>

      <Dialog open={agreementOpen !== null} onOpenChange={(open) => { if (!open) setAgreementOpen(null) }}>
        <DialogContent className="max-w-lg max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{agreementOpen === "user" ? "用户协议" : "隐私政策"}</DialogTitle>
          </DialogHeader>
          <div className="space-y-3 text-sm text-muted-foreground leading-relaxed">
            {agreementOpen === "user" ? (
              <>
                <p>欢迎使用企业即时通讯系统。在使用本服务前，请仔细阅读以下协议条款。</p>
                <p className="font-medium text-foreground">一、服务内容</p>
                <p>本系统为企业内部通讯工具，提供即时消息、文件传输、音视频通话等功能。服务的可用性和功能范围由企业管理员配置决定。</p>
                <p className="font-medium text-foreground">二、用户责任</p>
                <p>用户应妥善保管账号密码，不得将账号转让或出借给他人使用。用户对账号下的所有行为承担法律责任。</p>
                <p className="font-medium text-foreground">三、使用规范</p>
                <p>禁止利用本系统发送违法违规内容，包括但不限于：暴力、色情、欺诈、侵权等信息。违反规定的账号将被暂停或永久封禁。</p>
                <p className="font-medium text-foreground">四、知识产权</p>
                <p>本系统的软件、界面设计、商标等知识产权归开发方所有。未经授权不得复制、修改或分发。</p>
                <p className="font-medium text-foreground">五、免责声明</p>
                <p>因不可抗力、网络故障、系统维护等原因导致的服务中断，开发方不承担责任。用户数据的备份责任由企业管理员承担。</p>
                <p className="text-xs text-muted-foreground mt-4">最后更新：2026年5月</p>
              </>
            ) : (
              <>
                <p>我们非常重视您的隐私保护。本隐私政策说明了我们如何收集、使用和保护您的个人信息。</p>
                <p className="font-medium text-foreground">一、信息收集</p>
                <p>我们收集的信息包括：注册信息（手机号、姓名）、消息内容、文件传输记录、设备信息。这些信息仅用于提供和改进服务。</p>
                <p className="font-medium text-foreground">二、信息使用</p>
                <p>您的个人信息用于：身份验证、消息投递、服务优化、安全防护。我们不会将您的信息用于广告推送。</p>
                <p className="font-medium text-foreground">三、信息存储</p>
                <p>数据存储在企业指定的服务器上，采用加密传输和存储。消息记录的保留策略由企业管理员配置。</p>
                <p className="font-medium text-foreground">四、信息共享</p>
                <p>除法律法规要求外，我们不会向第三方共享您的个人信息。企业管理员可在管理后台查看通讯统计数据。</p>
                <p className="font-medium text-foreground">五、您的权利</p>
                <p>您有权查看、修改、删除个人信息。如需行使上述权利，请联系企业管理员。</p>
                <p className="text-xs text-muted-foreground mt-4">最后更新：2026年5月</p>
              </>
            )}
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
