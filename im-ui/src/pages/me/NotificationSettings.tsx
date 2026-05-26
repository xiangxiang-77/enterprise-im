import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { ChevronLeft } from "lucide-react"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Switch } from "@/components/ui/switch"
import { useAppSettingsStore, type NotificationSettings as NotificationSettingsState } from "@/stores/useAppSettingsStore"
import { useAuthStore } from "@/stores/useAuthStore"
import {
  disablePushDeviceTokenApi,
  fetchNotificationSettingsApi,
  fetchPushDeviceTokensApi,
  fetchPushProvidersApi,
  registerPushDeviceTokenApi,
  updateNotificationSettingsApi,
  type PushDeviceToken,
  type PushProviders,
} from "@/services/api"

const timeOptions = Array.from({ length: 24 }, (_, i) => {
  const h = String(i).padStart(2, "0")
  return [`${h}:00`, `${h}:30`]
}).flat()

export default function NotificationSettings() {
  const navigate = useNavigate()
  const { notification, updateNotification, replaceNotification } = useAppSettingsStore()
  const { token } = useAuthStore()
  const [pushProviders, setPushProviders] = useState<PushProviders | null>(null)
  const [pushTokens, setPushTokens] = useState<PushDeviceToken[]>([])

  useEffect(() => {
    if (!token) return
    void fetchNotificationSettingsApi(token)
      .then((settings) => replaceNotification(toLocalNotification(settings)))
      .catch(() => undefined)
    void fetchPushProvidersApi(token).then(setPushProviders).catch(() => undefined)
    void fetchPushDeviceTokensApi(token).then(setPushTokens).catch(() => undefined)
  }, [replaceNotification, token])

  const saveNotification = (updates: Partial<NotificationSettingsState>) => {
    updateNotification(updates)
    if (token) {
      void updateNotificationSettingsApi(updates, token)
        .then((settings) => replaceNotification(toLocalNotification(settings)))
        .catch(() => undefined)
    }
  }

  const registerBrowserPushToken = async () => {
    if (!token) return
    window.alert("未配置真实 Web Push/Vendor SDK，不能登记模拟 token")
    setPushTokens(await fetchPushDeviceTokensApi(token))
  }

  const disablePushToken = async (id: string) => {
    if (!token) return
    await disablePushDeviceTokenApi(id, token)
    setPushTokens(await fetchPushDeviceTokensApi(token))
  }

  const SettingItem = ({
    label,
    description,
    action,
  }: {
    label: string
    description?: string
    action?: React.ReactNode
  }) => (
    <div className="flex items-center justify-between bg-background px-4 py-3 hover:bg-muted/50">
      <div className="flex flex-col gap-0.5">
        <span className="text-sm font-medium">{label}</span>
        {description && <span className="text-xs text-muted-foreground">{description}</span>}
      </div>
      <div>{action}</div>
    </div>
  )

  return (
    <div className="flex h-full flex-col bg-muted/20">
      <header className="sticky top-0 z-10 flex items-center gap-2 border-b bg-background px-4 py-3">
        <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="-ml-2">
          <ChevronLeft className="h-6 w-6" />
        </Button>
        <h2 className="font-semibold">新消息通知</h2>
      </header>

      <ScrollArea className="flex-1 py-4">
        <div className="flex flex-col gap-6">
          <div className="flex flex-col divide-y border-y">
            <SettingItem label="接收新消息通知" description="关闭后，需要打开应用才能收到新消息" action={<Switch checked={notification.newMessage} onCheckedChange={(c) => saveNotification({ newMessage: c })} />} />
            <SettingItem label="接收语音和视频通话通知" action={<Switch checked={notification.calls} onCheckedChange={(c) => saveNotification({ calls: c })} />} />
          </div>

          <div className="flex flex-col divide-y border-y">
            <SettingItem label="通知显示消息详情" description="关闭后，通知只显示“你收到了一条消息”" action={<Switch checked={notification.detail} onCheckedChange={(c) => saveNotification({ detail: c })} />} />
          </div>

          <div className="flex flex-col divide-y border-y">
            <SettingItem label="声音" action={<Switch checked={notification.sound} onCheckedChange={(c) => saveNotification({ sound: c })} />} />
            <SettingItem label="振动" action={<Switch checked={notification.vibration} onCheckedChange={(c) => saveNotification({ vibration: c })} />} />
          </div>

          <div className="flex flex-col divide-y border-y">
            <SettingItem label="@消息提醒" description="有人在群聊中@你时强提醒" action={<Switch checked={notification.mentionAlert} onCheckedChange={(c) => saveNotification({ mentionAlert: c })} />} />
            <SettingItem label="截屏通知" description="对方截屏时发送通知" action={<Switch checked={notification.screenshotNotice} onCheckedChange={(c) => saveNotification({ screenshotNotice: c })} />} />
            <SettingItem label="撤回通知" description="对方撤回消息时通知" action={<Switch checked={notification.recallNotice} onCheckedChange={(c) => saveNotification({ recallNotice: c })} />} />
          </div>

          <div className="flex flex-col divide-y border-y bg-background">
            <div className="flex items-center justify-between px-4 py-3">
              <div>
                <p className="text-sm font-medium">免打扰</p>
                <p className="text-xs text-muted-foreground">开启后，设定时段内不接收通知</p>
              </div>
              <Switch checked={notification.dndEnabled} onCheckedChange={(c) => saveNotification({ dndEnabled: c })} />
            </div>
            {notification.dndEnabled && (
              <>
                <div className="flex items-center justify-between px-4 py-3">
                  <span className="text-sm">开始时间</span>
                  <select value={notification.dndStart} onChange={(e) => saveNotification({ dndStart: e.target.value })} className="rounded border bg-background px-2 py-1 text-sm">
                    {timeOptions.map((t) => <option key={t} value={t}>{t}</option>)}
                  </select>
                </div>
                <div className="flex items-center justify-between px-4 py-3">
                  <span className="text-sm">结束时间</span>
                  <select value={notification.dndEnd} onChange={(e) => saveNotification({ dndEnd: e.target.value })} className="rounded border bg-background px-2 py-1 text-sm">
                    {timeOptions.map((t) => <option key={t} value={t}>{t}</option>)}
                  </select>
                </div>
              </>
            )}
          </div>

          <div className="flex flex-col divide-y border-y bg-background">
            <SettingItem
              label="外部推送 Provider"
              description={`APNs ${pushProviders?.apns.mode ?? "读取中"} / FCM ${pushProviders?.fcm.mode ?? "读取中"} / Vendor ${pushProviders?.vendor.mode ?? "读取中"}`}
              action={<Button size="sm" variant="outline" onClick={registerBrowserPushToken}>登记设备</Button>}
            />
            {pushTokens.map((item) => (
              <SettingItem
                key={item.id}
                label={`${item.platform} / ${item.provider}`}
                description={`${item.token} / ${item.enabled ? "启用" : "停用"}`}
                action={item.enabled ? <Button size="sm" variant="outline" onClick={() => void disablePushToken(item.id)}>停用</Button> : undefined}
              />
            ))}
          </div>

          <div className="px-4 text-xs text-muted-foreground">
            <p>如果关闭新消息通知，你将无法在系统通知栏看到新消息提醒。</p>
          </div>
        </div>
      </ScrollArea>
    </div>
  )
}

function toLocalNotification(settings: Partial<NotificationSettingsState>): NotificationSettingsState {
  return {
    newMessage: settings.newMessage ?? true,
    calls: settings.calls ?? true,
    detail: settings.detail ?? true,
    sound: settings.sound ?? true,
    vibration: settings.vibration ?? true,
    screenshotNotice: settings.screenshotNotice ?? true,
    recallNotice: settings.recallNotice ?? true,
    mentionAlert: settings.mentionAlert ?? true,
    dndEnabled: settings.dndEnabled ?? false,
    dndStart: settings.dndStart ?? "22:00",
    dndEnd: settings.dndEnd ?? "08:00",
  }
}
