import path from "path"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vite"

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          react: ["react", "react-dom", "react-router-dom"],
          radix: [
            "@radix-ui/react-avatar",
            "@radix-ui/react-checkbox",
            "@radix-ui/react-context-menu",
            "@radix-ui/react-dialog",
            "@radix-ui/react-dropdown-menu",
            "@radix-ui/react-label",
            "@radix-ui/react-popover",
            "@radix-ui/react-scroll-area",
            "@radix-ui/react-select",
            "@radix-ui/react-separator",
            "@radix-ui/react-slider",
            "@radix-ui/react-slot",
            "@radix-ui/react-switch",
            "@radix-ui/react-tabs",
            "@radix-ui/react-tooltip",
          ],
          icons: ["lucide-react"],
          utils: ["class-variance-authority", "clsx", "date-fns", "tailwind-merge", "zustand"],
        },
      },
    },
  },
  server: {
    proxy: {
      "/api": {
        target: process.env.VITE_IM_API_PROXY_TARGET ?? "http://127.0.0.1:8080",
        changeOrigin: true,
      },
      "/ws": {
        target: process.env.VITE_IM_WS_PROXY_TARGET ?? "ws://127.0.0.1:8080",
        ws: true,
      },
    },
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
})
