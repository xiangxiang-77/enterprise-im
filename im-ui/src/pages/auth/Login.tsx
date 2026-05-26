import { useEffect, useRef, useState } from "react"
import { useNavigate } from "react-router-dom"
import { Fingerprint, MessageSquare, ShieldCheck, Smartphone, X } from "lucide-react"
import { useAuthStore } from "@/stores/useAuthStore"
import { useChatStore } from "@/stores/useChatStore"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { fetchAuthProvidersApi, loginApi, sendSmsCodeApi, ssoLoginApi, webAuthnBeginAuthApi, webAuthnCompleteAuthApi, webAuthnBeginRegisterApi, webAuthnCompleteRegisterApi, type AuthProviders } from "@/services/api"
import { imSocket } from "@/services/imSocket"

export default function Login() {
  const navigate = useNavigate()
  const login = useAuthStore((state) => state.login)
  const [isLoading, setIsLoading] = useState(false)
  const [isSendingCode, setIsSendingCode] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [agreed, setAgreed] = useState(true)
  const [loginMethod, setLoginMethod] = useState("phone")
  const [providers, setProviders] = useState<AuthProviders | null>(null)
  const [countdown, setCountdown] = useState(0)
  const [countryCode, setCountryCode] = useState("+86")
  const [legalModal, setLegalModal] = useState<"terms" | "privacy" | null>(null)
  const [ssoModalOpen, setSsoModalOpen] = useState(false)
  const [ssoForm, setSsoForm] = useState({ enterpriseCode: "", phone: "", password: "" })
  const [bioModalOpen, setBioModalOpen] = useState(false)
  const [bioPhone, setBioPhone] = useState("")
  const [bioPhase, setBioPhase] = useState<"input" | "waiting">("input")
  const errorRef = useRef<HTMLParagraphElement>(null)

  const countryCodes = ["+86", "+1", "+81", "+82", "+44", "+852", "+886"]
  const [formData, setFormData] = useState({
    phone: "13800000001",
    code: "",
    username: "13800000001",
    password: "",
  })

  useEffect(() => {
    fetchAuthProvidersApi()
      .then(setProviders)
      .catch(() => setError("认证能力读取失败"))
  }, [])

  useEffect(() => {
    if (countdown <= 0) return
    const timer = window.setTimeout(() => setCountdown((value) => Math.max(value - 1, 0)), 1000)
    return () => window.clearTimeout(timer)
  }, [countdown])

  useEffect(() => {
    if (!error || !errorRef.current) return
    const el = errorRef.current
    el.classList.remove("animate-shake")
    void el.offsetWidth
    el.classList.add("animate-shake")
  }, [error])

  const handleSendCode = async () => {
    if (countdown > 0 || isSendingCode) return
    setError(null)
    setNotice(null)
    setIsSendingCode(true)
    try {
      const result = await sendSmsCodeApi(formData.phone)
      setCountdown(60)
      setNotice(result.debugCode ? `演示验证码：${result.debugCode}` : `验证码已发送，${result.providerMode}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : "验证码发送失败")
    } finally {
      setIsSendingCode(false)
    }
  }

  const handleLogin = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!agreed) {
      setError("请先同意用户协议和隐私政策")
      return
    }

    setIsLoading(true)
    setError(null)
    try {
      const phone = loginMethod === "phone" ? formData.phone : formData.username
      const result = await loginApi({
        phone,
        code: loginMethod === "phone" ? formData.code : undefined,
        password: loginMethod === "password" ? formData.password : undefined,
      })
      login(result.user, result.token)
      imSocket.connect(result.token, result.user.id)
      useChatStore.getState().loadConversations(result.token)
      navigate("/")
    } catch (err) {
      setError(err instanceof Error ? err.message : "登录失败")
      setIsLoading(false)
    }
  }

  const handleSsoLogin = async () => {
    if (!ssoForm.enterpriseCode.trim()) {
      setError("请输入企业代码")
      return
    }
    setSsoModalOpen(false)
    setIsLoading(true)
    setError(null)
    try {
      const result = await ssoLoginApi({
        enterpriseCode: ssoForm.enterpriseCode.trim().toUpperCase(),
        phone: ssoForm.phone.trim() || undefined,
        password: ssoForm.password || undefined,
      })
      login(result.user, result.token)
      imSocket.connect(result.token, result.user.id)
      useChatStore.getState().loadConversations(result.token)
      navigate("/")
    } catch (err) {
      setError(err instanceof Error ? err.message : "SSO 登录失败")
      setIsLoading(false)
    }
  }

  const handleBiometricLogin = async () => {
    if (!bioPhone.trim()) {
      setError("请输入手机号")
      return
    }
    if (!window.PublicKeyCredential) {
      setError("当前浏览器不支持生物识别（需要 WebAuthn）")
      setBioModalOpen(false)
      return
    }
    setBioPhase("waiting")
    setBioModalOpen(false)
    setIsLoading(true)
    setError(null)

    try {
      const authOptions = await webAuthnBeginAuthApi(bioPhone.trim())

      if (authOptions.allowCredentialIds.length > 0) {
        const publicKey: PublicKeyCredentialRequestOptions = {
          challenge: base64urlToBuffer(authOptions.challenge),
          allowCredentials: authOptions.allowCredentialIds.map((id) => ({
            id: base64urlToBuffer(id),
            type: "public-key",
          })),
          timeout: 60000,
          userVerification: "required",
        }
        const assertion = (await navigator.credentials.get({ publicKey })) as PublicKeyCredential
        const credId = bufferToBase64url(assertion.rawId)
        const result = await webAuthnCompleteAuthApi(authOptions.challengeId, credId)
        login(result.user, result.token)
        imSocket.connect(result.token, result.user.id)
        useChatStore.getState().loadConversations(result.token)
        navigate("/")
      } else {
        const regOptions = await webAuthnBeginRegisterApi(bioPhone.trim())
        const publicKey: PublicKeyCredentialCreationOptions = {
          challenge: base64urlToBuffer(regOptions.challenge),
          rp: { name: regOptions.rpName },
          user: {
            id: base64urlToBuffer(regOptions.userId),
            name: regOptions.userName,
            displayName: regOptions.userDisplayName,
          },
          pubKeyCredParams: [
            { type: "public-key", alg: -7 },
            { type: "public-key", alg: -257 },
          ],
          timeout: 60000,
          attestation: "none",
          authenticatorSelection: {
            authenticatorAttachment: "platform",
            userVerification: "required",
          },
        }
        const credential = (await navigator.credentials.create({ publicKey })) as PublicKeyCredential
        const credId = bufferToBase64url(credential.rawId)
        const result = await webAuthnCompleteRegisterApi(regOptions.challengeId, credId)
        login(result.user, result.token)
        imSocket.connect(result.token, result.user.id)
        useChatStore.getState().loadConversations(result.token)
        navigate("/")
      }
    } catch (err) {
      if (err instanceof DOMException) {
        if (err.name === "NotAllowedError") {
          setError("生物识别已被取消或超时")
        } else if (err.name === "SecurityError") {
          setError("安全域不匹配：请通过 localhost 或 HTTPS 访问本页面")
        } else if (err.name === "InvalidStateError") {
          setError("此设备已注册过生物识别，请使用已注册的指纹/面容")
        } else {
          setError(`生物识别失败: ${err.message}`)
        }
      } else {
        setError(err instanceof Error ? err.message : "生物识别登录失败")
      }
      setIsLoading(false)
      setBioPhase("input")
    }
  }

  function base64urlToBuffer(base64url: string): ArrayBuffer {
    const base64 = base64url.replace(/-/g, "+").replace(/_/g, "/")
    const raw = atob(base64)
    const buffer = new Uint8Array(raw.length)
    for (let i = 0; i < raw.length; i++) {
      buffer[i] = raw.charCodeAt(i)
    }
    return buffer.buffer
  }

  function bufferToBase64url(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer)
    let binary = ""
    for (let i = 0; i < bytes.length; i++) {
      binary += String.fromCharCode(bytes[i])
    }
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "")
  }

  const smsEnabled = providers?.sms.enabled ?? false
  const passwordEnabled = providers?.password.enabled ?? true

  return (
    <div className="flex h-screen w-full flex-col bg-background">
      <div className="flex flex-1 flex-col items-center justify-center gap-8 px-8">
        <div className="flex flex-col items-center gap-2">
          <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-primary text-primary-foreground shadow-lg">
            <MessageSquare className="h-8 w-8 fill-current" />
          </div>
          <h1 className="text-2xl font-bold tracking-tight">企业即时通讯</h1>
          <p className="text-sm text-muted-foreground">安全 / 高效 / 可管理</p>
        </div>

        <div className="w-full max-w-sm space-y-4">
          <Tabs defaultValue="phone" className="w-full" onValueChange={setLoginMethod}>
            <TabsList className="grid w-full grid-cols-2">
              <TabsTrigger value="phone" disabled={!smsEnabled}>手机号登录</TabsTrigger>
              <TabsTrigger value="password" disabled={!passwordEnabled}>密码登录</TabsTrigger>
            </TabsList>

            <form onSubmit={handleLogin} className="mt-4 space-y-4">
              <TabsContent value="phone" className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="phone">手机号</Label>
                  <div className="flex gap-2">
                    <select
                      value={countryCode}
                      onChange={(e) => setCountryCode(e.target.value)}
                      className="w-20 rounded-md border bg-background px-2 text-sm"
                    >
                      {countryCodes.map((code) => (
                        <option key={code} value={code}>{code}</option>
                      ))}
                    </select>
                    <Input
                      id="phone"
                      placeholder="请输入手机号"
                      value={formData.phone}
                      onChange={(event) => setFormData({ ...formData, phone: event.target.value })}
                      required={loginMethod === "phone"}
                    />
                  </div>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="code">验证码</Label>
                  <div className="flex gap-2">
                    <Input
                      id="code"
                      placeholder="请输入验证码"
                      value={formData.code}
                      onChange={(event) => setFormData({ ...formData, code: event.target.value })}
                      required={loginMethod === "phone"}
                    />
                    <Button variant="outline" type="button" className="w-28" onClick={handleSendCode} disabled={!smsEnabled || countdown > 0 || isSendingCode}>
                      {countdown > 0 ? `${countdown}s` : "获取验证码"}
                    </Button>
                  </div>
                </div>
              </TabsContent>

              <TabsContent value="password" className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="username">账号</Label>
                  <Input
                    id="username"
                    placeholder="请输入手机号或邮箱"
                    value={formData.username}
                    onChange={(event) => setFormData({ ...formData, username: event.target.value })}
                    required={loginMethod === "password"}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="password">密码</Label>
                  <Input
                    id="password"
                    type="password"
                    placeholder="请输入密码"
                    value={formData.password}
                    onChange={(event) => setFormData({ ...formData, password: event.target.value })}
                    required={loginMethod === "password"}
                  />
                </div>
              </TabsContent>

              <div className="rounded-md border bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
                认证：短信 {providers?.sms.mode ?? "读取中"} / 密码 {providers?.password.mode ?? "读取中"} / SSO {providers?.sso.mode ?? "读取中"} / 生物识别 {providers?.biometric.mode ?? "读取中"}
              </div>

              <Button className="mt-6 w-full" type="submit" disabled={isLoading || (loginMethod === "phone" && !smsEnabled) || (loginMethod === "password" && !passwordEnabled)}>
                {isLoading ? "登录中..." : "登录"}
              </Button>
              {notice && <p className="text-sm text-primary">{notice}</p>}
              {error && <p ref={errorRef} className="text-sm text-destructive animate-shake">{error}</p>}
            </form>
          </Tabs>

          <div className="flex items-center space-x-2">
            <Checkbox id="terms" checked={agreed} onCheckedChange={(checked: boolean) => setAgreed(checked)} />
            <label htmlFor="terms" className="text-xs leading-none text-muted-foreground">
              已阅读并同意 <button type="button" className="cursor-pointer text-primary underline" onClick={() => setLegalModal("terms")}>《用户协议》</button> 和 <button type="button" className="cursor-pointer text-primary underline" onClick={() => setLegalModal("privacy")}>《隐私政策》</button>
            </label>
          </div>

          <div className="relative my-6">
            <div className="absolute inset-0 flex items-center">
              <span className="w-full border-t" />
            </div>
            <div className="relative flex justify-center text-xs uppercase">
              <span className="bg-background px-2 text-muted-foreground">其他登录方式</span>
            </div>
          </div>

          <div className="flex justify-center gap-4">
            <Button variant="outline" size="icon" className="rounded-full" title={providers?.sso.enabled ? "SSO 登录" : "SSO 未启用"} disabled={!providers?.sso.enabled} onClick={() => setSsoModalOpen(true)}>
              <Smartphone className="h-4 w-4" />
            </Button>
            <Button variant="outline" size="icon" className="rounded-full" title={providers?.biometric.enabled ? "生物识别登录" : "生物识别未启用"} disabled={!providers?.biometric.enabled} onClick={() => { setBioPhone(formData.phone); setBioModalOpen(true); }}>
              <Fingerprint className="h-4 w-4" />
            </Button>
            <Button variant="outline" size="icon" className="rounded-full" title="进入后台" onClick={() => navigate("/admin")}>
              <ShieldCheck className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </div>

      <div className="pb-8 text-center text-xs text-muted-foreground opacity-50">
        © 2026 企业即时通讯演示系统
      </div>

      {legalModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={() => setLegalModal(null)}>
          <div className="mx-4 max-h-[70vh] w-full max-w-lg overflow-y-auto rounded-lg bg-background p-6 shadow-xl" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-bold">{legalModal === "terms" ? "用户协议" : "隐私政策"}</h2>
              <button onClick={() => setLegalModal(null)} className="rounded-md p-1 hover:bg-muted"><X className="h-5 w-5" /></button>
            </div>
            <div className="space-y-3 text-sm text-muted-foreground">
              {legalModal === "terms" ? (
                <>
                  <h3 className="font-semibold text-foreground">一、服务条款</h3>
                  <p>欢迎使用企业即时通讯平台。本协议是您与企业即时通讯服务提供方之间关于使用本服务的法律协议。</p>
                  <h3 className="font-semibold text-foreground">二、账号注册</h3>
                  <p>用户需通过企业管理员分配的账号或手机号注册使用本服务。用户应妥善保管账号信息，不得将账号出借或转让给他人使用。</p>
                  <h3 className="font-semibold text-foreground">三、使用规范</h3>
                  <p>用户不得利用本服务发布、传输违法违规内容，包括但不限于：危害国家安全、破坏民族团结、侵犯他人合法权益等信息。</p>
                  <h3 className="font-semibold text-foreground">四、数据安全</h3>
                  <p>本平台采用端到端加密传输，所有消息内容在服务器端以加密形式存储。企业管理员可依据合规要求对数据进行审计。</p>
                  <h3 className="font-semibold text-foreground">五、免责声明</h3>
                  <p>因不可抗力因素导致的服务中断，本平台不承担法律责任。本平台保留对协议条款进行修改的权利。</p>
                </>
              ) : (
                <>
                  <h3 className="font-semibold text-foreground">一、信息收集</h3>
                  <p>我们收集的信息包括：手机号码、设备信息、日志信息。手机号码用于账号注册和身份认证。</p>
                  <h3 className="font-semibold text-foreground">二、信息使用</h3>
                  <p>收集的信息仅用于提供即时通讯服务、改善用户体验和保障账号安全。未经用户同意，不会将信息用于其他目的。</p>
                  <h3 className="font-semibold text-foreground">三、信息存储</h3>
                  <p>用户数据存储于企业自有服务器，消息内容以加密形式存储。用户可随时联系企业管理员导出或删除个人数据。</p>
                  <h3 className="font-semibold text-foreground">四、信息共享</h3>
                  <p>我们不会将用户信息出售或分享给第三方，法律法规要求披露的情况除外。</p>
                  <h3 className="font-semibold text-foreground">五、Cookie使用</h3>
                  <p>本平台使用必要的会话Cookie以维持登录状态。用户可通过浏览器设置管理Cookie。</p>
                  <h3 className="font-semibold text-foreground">六、联系方式</h3>
                  <p>如有隐私相关问题，请联系企业管理员或发送邮件至 privacy@enterprise-im.com。</p>
                </>
              )}
            </div>
            <Button className="mt-4 w-full" onClick={() => { setAgreed(true); setLegalModal(null); }}>我已阅读并同意</Button>
          </div>
        </div>
      )}

      {ssoModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={() => setSsoModalOpen(false)}>
          <div className="mx-4 w-full max-w-sm rounded-lg bg-background p-6 shadow-xl" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-bold">SSO 企业登录</h2>
              <button onClick={() => setSsoModalOpen(false)} className="rounded-md p-1 hover:bg-muted"><X className="h-5 w-5" /></button>
            </div>
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="sso-enterprise-code">企业代码</Label>
                <Input
                  id="sso-enterprise-code"
                  placeholder="请输入企业代码"
                  value={ssoForm.enterpriseCode}
                  onChange={(e) => setSsoForm({ ...ssoForm, enterpriseCode: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="sso-phone">手机号（可选）</Label>
                <Input
                  id="sso-phone"
                  placeholder="选填，用于验证身份"
                  value={ssoForm.phone}
                  onChange={(e) => setSsoForm({ ...ssoForm, phone: e.target.value })}
                />
              </div>
              {ssoForm.phone.trim() && (
                <div className="space-y-2">
                  <Label htmlFor="sso-password">密码</Label>
                  <Input
                    id="sso-password"
                    type="password"
                    placeholder="请输入密码"
                    value={ssoForm.password}
                    onChange={(e) => setSsoForm({ ...ssoForm, password: e.target.value })}
                  />
                </div>
              )}
              <Button className="w-full" onClick={handleSsoLogin}>登录</Button>
            </div>
          </div>
        </div>
      )}

      {bioModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={() => { setBioModalOpen(false); setBioPhase("input"); }}>
          <div className="mx-4 w-full max-w-sm rounded-lg bg-background p-6 shadow-xl" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-bold">生物识别登录</h2>
              <button onClick={() => { setBioModalOpen(false); setBioPhase("input"); }} className="rounded-md p-1 hover:bg-muted"><X className="h-5 w-5" /></button>
            </div>
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="bio-phone">手机号</Label>
                <Input
                  id="bio-phone"
                  placeholder="请输入手机号"
                  value={bioPhone}
                  onChange={(e) => setBioPhone(e.target.value)}
                />
              </div>
              <p className="text-xs text-muted-foreground">
                使用 Windows Hello、Touch ID 或指纹识别进行登录。首次使用将自动注册生物识别凭据。
              </p>
              <Button className="w-full" onClick={handleBiometricLogin} disabled={bioPhase === "waiting"}>
                {bioPhase === "waiting" ? "等待生物识别..." : "开始验证"}
              </Button>
            </div>
          </div>
        </div>
      )}

      <style>{`
        @keyframes shake {
          0%, 100% { transform: translateX(0); }
          10%, 30%, 50%, 70%, 90% { transform: translateX(-4px); }
          20%, 40%, 60%, 80% { transform: translateX(4px); }
        }
        .animate-shake {
          animation: shake 0.5s ease-in-out;
        }
      `}</style>
    </div>
  )
}
