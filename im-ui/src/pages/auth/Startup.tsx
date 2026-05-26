import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { MessageSquare } from "lucide-react"
import { useAuthStore } from "@/stores/useAuthStore"
import { fetchAuthProvidersApi } from "@/services/api"

export default function Startup() {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuthStore()
  const [phase, setPhase] = useState(0)
  const [authMode, setAuthMode] = useState("认证检查中")

  useEffect(() => {
    fetchAuthProvidersApi()
      .then((providers) => setAuthMode(`短信 ${providers.sms.mode} / 密码 ${providers.password.mode}`))
      .catch(() => setAuthMode("认证能力不可用"))
    const t1 = setTimeout(() => setPhase(1), 200)
    const t2 = setTimeout(() => setPhase(2), 600)
    const t3 = setTimeout(() => {
      if (isAuthenticated) {
        navigate("/")
      } else if (!localStorage.getItem("onboarding-done")) {
        navigate("/auth/onboarding")
      } else {
        navigate("/auth/login")
      }
    }, 1800)
    return () => { clearTimeout(t1); clearTimeout(t2); clearTimeout(t3) }
  }, [isAuthenticated, navigate])

  return (
    <div className="flex h-screen w-full flex-col items-center justify-between overflow-hidden bg-gradient-to-b from-primary/5 to-background pb-10 pt-[28vh]">
      <div className="flex flex-col items-center gap-5">
        <div
          className={`flex h-24 w-24 items-center justify-center rounded-3xl bg-primary text-primary-foreground shadow-2xl transition-all duration-700 ${
            phase >= 1 ? "scale-100 opacity-100" : "scale-50 opacity-0"
          }`}
          style={{ animation: phase >= 1 ? "bounce 0.6s ease-out" : "none" }}
        >
          <MessageSquare className="h-12 w-12 fill-current" />
        </div>

        <div
          className={`flex flex-col items-center gap-1 transition-all duration-500 ${
            phase >= 1 ? "translate-y-0 opacity-100" : "translate-y-4 opacity-0"
          }`}
        >
          <h1 className="text-2xl font-bold tracking-tight">企业即时通讯</h1>
          <p className="text-sm text-muted-foreground">安全的企业级即时通讯</p>
        </div>

        {phase >= 2 && (
          <div className="flex gap-1.5 pt-4">
            <div className="h-1.5 w-1.5 animate-bounce rounded-full bg-primary" style={{ animationDelay: "0ms" }} />
            <div className="h-1.5 w-1.5 animate-bounce rounded-full bg-primary" style={{ animationDelay: "150ms" }} />
            <div className="h-1.5 w-1.5 animate-bounce rounded-full bg-primary" style={{ animationDelay: "300ms" }} />
          </div>
        )}
      </div>

      <div className="flex flex-col items-center gap-1 text-muted-foreground opacity-40">
        <p className="text-[10px]">2026</p>
      </div>

      <style>{`
        @keyframes bounce {
          0% { transform: scale(0.3); opacity: 0; }
          50% { transform: scale(1.05); }
          70% { transform: scale(0.95); }
          100% { transform: scale(1); opacity: 1; }
        }
      `}</style>
    </div>
  )
}
