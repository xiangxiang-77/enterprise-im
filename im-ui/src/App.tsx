import { RouterProvider } from "react-router-dom"
import { useEffect } from "react"
import { ThemeProvider } from "@/components/theme-provider"
import { fontScaleMap, useAppSettingsStore } from "@/stores/useAppSettingsStore"
import { router } from "./router"

function AppShell() {
  const fontSize = useAppSettingsStore((state) => state.fontSize)

  useEffect(() => {
    document.documentElement.style.fontSize = fontScaleMap[fontSize]
  }, [fontSize])

  return <RouterProvider router={router} />
}

function App() {
  return (
    <ThemeProvider defaultTheme="light" storageKey="vite-ui-theme">
      <AppShell />
    </ThemeProvider>
  )
}

export default App
