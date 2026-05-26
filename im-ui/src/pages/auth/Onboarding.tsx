import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { MessageSquare, ShieldCheck, Users, Smartphone } from "lucide-react"
import { Button } from "@/components/ui/button"

const slides = [
  {
    icon: MessageSquare,
    title: "即时通讯",
    description: "支持文字、语音、图片、视频、文件等多种消息类型，消息状态实时同步，阅后即焚保护隐私。",
  },
  {
    icon: ShieldCheck,
    title: "安全可靠",
    description: "端到端加密传输，消息内容加密存储。后台审计与敏感词过滤保障企业合规安全。",
  },
  {
    icon: Users,
    title: "组织管理",
    description: "企业组织架构管理，部门层级灵活配置。群聊权限管控，好友关系与黑名单管理。",
  },
  {
    icon: Smartphone,
    title: "多端同步",
    description: "支持手机、电脑、网页多端同时登录，消息实时同步。设备管理后台可控。",
  },
]

export default function Onboarding() {
  const navigate = useNavigate()
  const [current, setCurrent] = useState(0)

  const handleFinish = () => {
    localStorage.setItem("onboarding-done", "true")
    navigate("/auth/login")
  }

  return (
    <div className="flex h-screen w-full flex-col bg-background">
      <div className="flex flex-1 flex-col items-center justify-center gap-8 px-8">
        <div className="flex flex-col items-center gap-6 transition-all duration-500">
          {(() => {
            const Icon = slides[current].icon
            return (
              <div className="flex h-24 w-24 items-center justify-center rounded-3xl bg-primary text-primary-foreground shadow-2xl">
                <Icon className="h-12 w-12" />
              </div>
            )
          })()}
          <h2 className="text-2xl font-bold">{slides[current].title}</h2>
          <p className="max-w-xs text-center text-sm text-muted-foreground">{slides[current].description}</p>
        </div>

        <div className="flex gap-2">
          {slides.map((_, index) => (
            <button
              key={index}
              className={`h-2 rounded-full transition-all ${index === current ? "w-8 bg-primary" : "w-2 bg-muted-foreground/30"}`}
              onClick={() => setCurrent(index)}
            />
          ))}
        </div>
      </div>

      <div className="flex items-center justify-between px-8 pb-12">
        <Button variant="ghost" onClick={handleFinish}>
          跳过
        </Button>
        <Button onClick={() => (current < slides.length - 1 ? setCurrent(current + 1) : handleFinish())}>
          {current < slides.length - 1 ? "下一步" : "开始使用"}
        </Button>
      </div>
    </div>
  )
}
