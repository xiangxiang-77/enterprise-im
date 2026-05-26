import { create } from "zustand"

type Language = "system" | "zh-CN" | "en-US"
type FontSize = "small" | "standard" | "large" | "extra"

export type NotificationSettings = {
  newMessage: boolean
  calls: boolean
  detail: boolean
  sound: boolean
  vibration: boolean
  screenshotNotice: boolean
  recallNotice: boolean
  mentionAlert: boolean
  dndEnabled: boolean
  dndStart: string
  dndEnd: string
}

type AppSettingsState = {
  language: Language
  fontSize: FontSize
  chatBackground: string
  chatBackgroundBlur: number
  cacheSizeMb: number
  notification: NotificationSettings
  networkStatus: "connected" | "connecting" | "offline" | "syncing"
  desktopOnline: boolean
  setLanguage: (language: Language) => void
  setFontSize: (fontSize: FontSize) => void
  setChatBackground: (chatBackground: string) => void
  setChatBackgroundBlur: (blur: number) => void
  clearCache: () => void
  updateNotification: (updates: Partial<NotificationSettings>) => void
  replaceNotification: (notification: NotificationSettings) => void
  setNetworkStatus: (networkStatus: AppSettingsState["networkStatus"]) => void
  setDesktopOnline: (desktopOnline: boolean) => void
}

const storageKey = "enterprise-im-app-settings"

const defaults: Omit<
  AppSettingsState,
  | "setLanguage"
  | "setFontSize"
  | "setChatBackground"
  | "setChatBackgroundBlur"
  | "clearCache"
  | "updateNotification"
  | "replaceNotification"
  | "setNetworkStatus"
  | "setDesktopOnline"
> = {
  language: "system",
  fontSize: "standard",
  chatBackground: "default",
  chatBackgroundBlur: 0,
  cacheSizeMb: 128,
  notification: {
    newMessage: true,
    calls: true,
    detail: true,
    sound: true,
    vibration: true,
    screenshotNotice: true,
    recallNotice: true,
    mentionAlert: true,
    dndEnabled: false,
    dndStart: "22:00",
    dndEnd: "08:00",
  },
  networkStatus: "connected",
  desktopOnline: true,
}

function loadSettings() {
  try {
    const raw = localStorage.getItem(storageKey)
    if (!raw) return defaults
    const parsed = JSON.parse(raw)
    return {
      ...defaults,
      ...parsed,
      notification: { ...defaults.notification, ...parsed.notification },
    }
  } catch {
    return defaults
  }
}

function persist(state: Partial<AppSettingsState>) {
  const serializable = {
    language: state.language,
    fontSize: state.fontSize,
    chatBackground: state.chatBackground,
    chatBackgroundBlur: state.chatBackgroundBlur,
    cacheSizeMb: state.cacheSizeMb,
    notification: state.notification,
    networkStatus: state.networkStatus,
    desktopOnline: state.desktopOnline,
  }
  localStorage.setItem(storageKey, JSON.stringify(serializable))
}

export const useAppSettingsStore = create<AppSettingsState>((set, get) => ({
  ...loadSettings(),
  setLanguage: (language) => set((state) => {
    const next = { ...state, language }
    persist(next)
    return { language }
  }),
  setFontSize: (fontSize) => set((state) => {
    const next = { ...state, fontSize }
    persist(next)
    return { fontSize }
  }),
  setChatBackground: (chatBackground) => set((state) => {
    const next = { ...state, chatBackground }
    persist(next)
    return { chatBackground }
  }),
  setChatBackgroundBlur: (chatBackgroundBlur) => set((state) => {
    const next = { ...state, chatBackgroundBlur }
    persist(next)
    return { chatBackgroundBlur }
  }),
  clearCache: () => set((state) => {
    const next = { ...state, cacheSizeMb: 0 }
    persist(next)
    return { cacheSizeMb: 0 }
  }),
  updateNotification: (updates) => set((state) => {
    const notification = { ...state.notification, ...updates }
    const next = { ...state, notification }
    persist(next)
    return { notification }
  }),
  replaceNotification: (notification) => set((state) => {
    const next = { ...state, notification }
    persist(next)
    return { notification }
  }),
  setNetworkStatus: (networkStatus) => set((state) => {
    const next = { ...state, networkStatus }
    persist(next)
    return { networkStatus }
  }),
  setDesktopOnline: (desktopOnline) => set((state) => {
    const next = { ...state, desktopOnline }
    persist(next)
    return { desktopOnline }
  }),
}))

export const fontScaleMap: Record<FontSize, string> = {
  small: "14px",
  standard: "16px",
  large: "18px",
  extra: "20px",
}
