import { RouterProvider } from "react-router-dom"
import { ThemeProvider } from "@/components/theme-provider"
import { router } from "./router"

function App() {
  return (
    <ThemeProvider defaultTheme="light" storageKey="vite-ui-theme">
      <RouterProvider router={router} />
    </ThemeProvider>
  )
}

export default App
