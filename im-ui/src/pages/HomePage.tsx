import { Button } from "@/components/ui/button"

export default function HomePage() {
  return (
    <div className="container mx-auto space-y-4 p-4">
      <h1 className="text-3xl font-bold">企业即时通讯</h1>
      <p className="text-muted-foreground">工作台与会话入口。</p>
      <Button variant="default">新建会话</Button>
    </div>
  )
}
