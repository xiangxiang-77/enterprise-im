import { useEffect } from "react"
import { useNavigate } from "react-router-dom"
import { MessageSquare } from "lucide-react"
import { useAuthStore } from "@/stores/useAuthStore"

export default function Startup() {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuthStore()

  useEffect(() => {
    const timer = window.setTimeout(() => {
      navigate(isAuthenticated ? "/" : "/auth/login")
    }, 900)

    return () => window.clearTimeout(timer)
  }, [isAuthenticated, navigate])

  return (
    <div className="flex h-screen w-full flex-col items-center justify-between bg-background pb-10 pt-[30vh]">
      <div className="flex animate-in flex-col items-center gap-4 fade-in zoom-in duration-500">
        <div className="flex h-20 w-20 items-center justify-center rounded-3xl bg-primary text-primary-foreground shadow-xl">
          <MessageSquare className="h-10 w-10 fill-current" />
        </div>
        <h1 className="text-2xl font-bold tracking-tight">企业即时通讯</h1>
      </div>

      <div className="flex flex-col items-center gap-2 text-muted-foreground opacity-50">
        <p className="text-xs">安全企业通信</p>
        <p className="text-[10px]">© 2026 IM Team</p>
      </div>
    </div>
  )
}
