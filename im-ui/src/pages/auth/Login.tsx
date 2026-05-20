import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { MessageSquare, ShieldCheck, Smartphone } from "lucide-react"
import { useAuthStore } from "@/stores/useAuthStore"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { loginApi } from "@/services/api"
import { imSocket } from "@/services/imSocket"

export default function Login() {
  const navigate = useNavigate()
  const login = useAuthStore((state) => state.login)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [agreed, setAgreed] = useState(true)
  const [loginMethod, setLoginMethod] = useState("phone")

  const [formData, setFormData] = useState({
    phone: "13800000001",
    code: "123456",
    username: "13800000001",
    password: "demo123",
  })

  const handleLogin = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!agreed) {
      alert("请先阅读并同意服务协议")
      return
    }

    setIsLoading(true)
    setError(null)

    try {
      const phone = loginMethod === "phone" ? formData.phone : formData.username
      const result = await loginApi({
        phone,
        code: formData.code,
        password: formData.password,
      })
      login(result.user, result.token)
      imSocket.connect(result.token, result.user.id)
      navigate("/")
    } catch (err) {
      setError(err instanceof Error ? err.message : "登录失败")
      setIsLoading(false)
    }
  }

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
              <TabsTrigger value="phone">手机号登录</TabsTrigger>
              <TabsTrigger value="password">密码登录</TabsTrigger>
            </TabsList>

            <form onSubmit={handleLogin} className="mt-4 space-y-4">
              <TabsContent value="phone" className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="phone">手机号</Label>
                  <div className="flex gap-2">
                    <Button variant="outline" type="button" className="w-20">+86</Button>
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
                    <Button variant="outline" type="button" className="w-28">获取验证码</Button>
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
                  <div className="flex items-center justify-between">
                    <Label htmlFor="password">密码</Label>
                    <Button variant="link" className="h-auto p-0 text-xs text-muted-foreground" type="button">
                      忘记密码？
                    </Button>
                  </div>
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
                演示账号：任意手机号均可登录。后台入口使用 18800000000 / admin123。
              </div>

              <Button className="mt-6 w-full" type="submit" disabled={isLoading}>
                {isLoading ? "登录中..." : "登录"}
              </Button>
              {error && <p className="text-sm text-destructive">{error}</p>}
            </form>
          </Tabs>

          <div className="flex items-center space-x-2">
            <Checkbox id="terms" checked={agreed} onCheckedChange={(checked: boolean) => setAgreed(checked)} />
            <label
              htmlFor="terms"
              className="text-xs leading-none text-muted-foreground peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
            >
              已阅读并同意 <span className="cursor-pointer text-primary">《用户协议》</span> 和 <span className="cursor-pointer text-primary">《隐私政策》</span>
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
            <Button variant="outline" size="icon" className="rounded-full" title="手机扫码">
              <Smartphone className="h-4 w-4" />
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
    </div>
  )
}
