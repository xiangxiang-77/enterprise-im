import { Outlet, useLocation, useNavigate } from "react-router-dom"
import { MessageSquare, Users, Briefcase, User, Settings, ShieldCheck } from "lucide-react"
import { cn } from "@/lib/utils"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { useAuthStore } from "@/stores/useAuthStore"
import { useChatStore } from "@/stores/useChatStore"
import { Button } from "@/components/ui/button"

export default function MainLayout() {
  const location = useLocation()
  const navigate = useNavigate()
  const { user } = useAuthStore()
  const { sessions } = useChatStore()
  const unreadCount = sessions.reduce((acc, session) => acc + (session.unreadCount || 0), 0)

  const tabs = [
    {
      id: "chat",
      label: "消息",
      icon: MessageSquare,
      path: "/",
      badge: unreadCount > 0 ? (unreadCount > 99 ? "99+" : unreadCount) : undefined,
    },
    {
      id: "contact",
      label: "通讯录",
      icon: Users,
      path: "/contact",
    },
    {
      id: "workspace",
      label: "工作台",
      icon: Briefcase,
      path: "/workspace",
    },
    {
      id: "me",
      label: "我",
      icon: User,
      path: "/me",
    },
    {
      id: "admin",
      label: "管理",
      icon: ShieldCheck,
      path: "/admin",
    },
  ]

  const NavItem = ({ tab, isActive, isDesktop = false }: { tab: any; isActive: boolean; isDesktop?: boolean }) => (
    <button
      onClick={() => navigate(tab.path)}
      className={cn(
        "relative flex items-center justify-center transition-colors",
        isDesktop
          ? "mb-4 h-12 w-12 rounded-xl hover:bg-white/10"
          : "flex-1 flex-col gap-1 py-2",
        isActive
          ? (isDesktop ? "bg-primary text-primary-foreground" : "text-primary")
          : (isDesktop ? "text-gray-400 hover:text-white" : "text-muted-foreground hover:text-foreground"),
      )}
      title={isDesktop ? tab.label : undefined}
    >
      <div className="relative">
        <tab.icon
          className={cn(
            isDesktop ? "h-6 w-6" : "h-6 w-6",
            isActive && !isDesktop && "fill-current",
          )}
        />
        {tab.badge && (
          <span className="absolute -right-2 -top-2 z-10 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-medium text-destructive-foreground">
            {tab.badge}
          </span>
        )}
      </div>
      {!isDesktop && <span className="text-xs font-medium">{tab.label}</span>}
    </button>
  )

  return (
    <div className="flex h-screen w-full overflow-hidden bg-background">
      <aside className="hidden w-16 flex-col items-center bg-[#2E2E2E] py-6 md:flex">
        <div className="mb-8">
          <Avatar className="h-10 w-10 cursor-pointer hover:opacity-80" onClick={() => navigate("/me")}>
            <AvatarImage src={user?.avatar} />
            <AvatarFallback>{user?.name?.[0]}</AvatarFallback>
          </Avatar>
        </div>

        <nav className="flex w-full flex-1 flex-col items-center">
          {tabs.map((tab) => (
            <NavItem
              key={tab.id}
              tab={tab}
              isActive={location.pathname === tab.path || (tab.path !== "/" && location.pathname.startsWith(tab.path))}
              isDesktop
            />
          ))}
        </nav>

        <div className="mb-4 flex flex-col gap-4">
          <Button variant="ghost" size="icon" className="text-gray-400 hover:bg-white/10 hover:text-white" onClick={() => navigate("/settings")}>
            <Settings className="h-6 w-6" />
          </Button>
        </div>
      </aside>

      <div className="flex h-full w-full flex-1 flex-col overflow-hidden">
        <main className="relative flex-1 overflow-hidden">
          <Outlet />
        </main>

        <nav className="border-t bg-background pb-safe md:hidden">
          <div className="flex h-16 items-center justify-around px-2">
            {tabs.map((tab) => (
              <NavItem
                key={tab.id}
                tab={tab}
                isActive={location.pathname === tab.path || (tab.path !== "/" && location.pathname.startsWith(tab.path))}
              />
            ))}
          </div>
        </nav>
      </div>
    </div>
  )
}
