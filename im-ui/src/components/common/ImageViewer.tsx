import { X, ChevronLeft, ChevronRight, Download, Forward, MoreVertical, Share } from "lucide-react"
import { useState, useEffect, useRef } from "react"
import { Dialog, DialogContent } from "@/components/ui/dialog"

interface ImageViewerProps {
  isOpen: boolean
  onClose: () => void
  images: string[]
  initialIndex?: number
  onForward?: (url: string) => void
}

export function ImageViewer({ isOpen, onClose, images, initialIndex = 0, onForward }: ImageViewerProps) {
  const [currentIndex, setCurrentIndex] = useState(initialIndex)
  const [scale, setScale] = useState(1)
  const [showMenu, setShowMenu] = useState(false)
  const touchStartRef = useRef<{ x: number; y: number; time: number } | null>(null)
  const lastTapRef = useRef<number>(0)
  const longPressRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (isOpen) {
      setCurrentIndex(initialIndex)
      setScale(1)
      setShowMenu(false)
    }
  }, [isOpen, initialIndex])

  useEffect(() => {
    if (!isOpen) return
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "ArrowLeft") {
        setCurrentIndex((prev) => (prev > 0 ? prev - 1 : images.length - 1))
        setScale(1)
      } else if (e.key === "ArrowRight") {
        setCurrentIndex((prev) => (prev < images.length - 1 ? prev + 1 : 0))
        setScale(1)
      } else if (e.key === "Escape") {
        onClose()
      }
    }
    window.addEventListener("keydown", handleKeyDown)
    return () => window.removeEventListener("keydown", handleKeyDown)
  }, [isOpen, images.length, onClose])

  const handleWheel = (e: React.WheelEvent) => {
    e.preventDefault()
    setScale((prev) => {
      const next = prev - e.deltaY * 0.001
      return Math.max(0.5, Math.min(5, next))
    })
  }

  const handleTouchStart = (e: React.TouchEvent) => {
    if (e.touches.length === 1) {
      const touch = e.touches[0]
      touchStartRef.current = { x: touch.clientX, y: touch.clientY, time: Date.now() }
      longPressRef.current = setTimeout(() => {
        setShowMenu(true)
      }, 600)
    }
  }

  const handleTouchMove = (e: React.TouchEvent) => {
    if (longPressRef.current && touchStartRef.current) {
      const dx = Math.abs(e.touches[0].clientX - touchStartRef.current.x)
      const dy = Math.abs(e.touches[0].clientY - touchStartRef.current.y)
      if (dx > 10 || dy > 10) {
        clearTimeout(longPressRef.current)
        longPressRef.current = null
      }
    }
  }

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (longPressRef.current) {
      clearTimeout(longPressRef.current)
      longPressRef.current = null
    }
    if (!touchStartRef.current || e.changedTouches.length !== 1) return
    const dx = e.changedTouches[0].clientX - touchStartRef.current.x
    const dy = e.changedTouches[0].clientY - touchStartRef.current.y
    const dt = Date.now() - touchStartRef.current.time
    touchStartRef.current = null

    const now = Date.now()
    if (dt < 300 && Math.abs(dx) < 10 && Math.abs(dy) < 10) {
      if (now - lastTapRef.current < 300) {
        setScale((prev) => (prev > 1 ? 1 : 2))
        lastTapRef.current = 0
        return
      }
      lastTapRef.current = now
    }

    if (dt < 500 && Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy)) {
      if (dx < 0) setCurrentIndex((prev) => (prev < images.length - 1 ? prev + 1 : 0))
      else setCurrentIndex((prev) => (prev > 0 ? prev - 1 : images.length - 1))
      setScale(1)
    }
  }

  const goPrev = () => { setCurrentIndex((prev) => (prev > 0 ? prev - 1 : images.length - 1)); setScale(1) }
  const goNext = () => { setCurrentIndex((prev) => (prev < images.length - 1 ? prev + 1 : 0)); setScale(1) }

  const handleDownload = () => {
    const a = document.createElement("a")
    a.href = images[currentIndex]
    a.download = `image-${currentIndex + 1}`
    a.click()
    setShowMenu(false)
  }

  const handleForward = () => {
    onForward?.(images[currentIndex])
    setShowMenu(false)
  }

  if (!isOpen) return null

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-[100vw] h-screen w-screen border-none bg-black/95 p-0 shadow-none sm:rounded-none z-50 flex flex-col items-center justify-center">
        {/* Top bar */}
        <div className="absolute top-0 left-0 right-0 z-50 flex items-center justify-between px-4 py-3">
          <button onClick={onClose} className="text-white/70 hover:text-white">
            <X className="h-7 w-7" />
          </button>
          <span className="text-sm text-white/70">
            {currentIndex + 1} / {images.length}
          </span>
          <button onClick={() => setShowMenu(!showMenu)} className="text-white/70 hover:text-white">
            <MoreVertical className="h-6 w-6" />
          </button>
        </div>

        {/* Context menu */}
        {showMenu && (
          <div className="absolute right-4 top-14 z-50 rounded-lg bg-white/95 shadow-xl backdrop-blur dark:bg-gray-800/95">
            <button className="flex w-40 items-center gap-3 px-4 py-3 text-sm hover:bg-muted/50" onClick={handleDownload}>
              <Download className="h-4 w-4" /> 保存到相册
            </button>
            <button className="flex w-40 items-center gap-3 px-4 py-3 text-sm hover:bg-muted/50" onClick={handleForward}>
              <Forward className="h-4 w-4" /> 转发
            </button>
            <button className="flex w-40 items-center gap-3 px-4 py-3 text-sm hover:bg-muted/50" onClick={() => setShowMenu(false)}>
              <Share className="h-4 w-4" /> 收藏
            </button>
          </div>
        )}

        <div
          className="relative flex h-full w-full items-center justify-center"
          onWheel={handleWheel}
          onTouchStart={handleTouchStart}
          onTouchMove={handleTouchMove}
          onTouchEnd={handleTouchEnd}
          onContextMenu={(e) => { e.preventDefault(); setShowMenu(!showMenu) }}
        >
          {images.length > 1 && (
            <button onClick={goPrev} className="absolute left-3 z-40 rounded-full bg-black/40 p-2 text-white/70 hover:bg-black/60 hover:text-white">
              <ChevronLeft className="h-7 w-7" />
            </button>
          )}

          <img
            src={images[currentIndex]}
            className="max-w-full max-h-full select-none object-contain transition-transform duration-150"
            style={{ transform: `scale(${scale})` }}
            draggable={false}
            alt=""
          />

          {images.length > 1 && (
            <button onClick={goNext} className="absolute right-3 z-40 rounded-full bg-black/40 p-2 text-white/70 hover:bg-black/60 hover:text-white">
              <ChevronRight className="h-7 w-7" />
            </button>
          )}
        </div>

        {/* Bottom thumbnail strip */}
        {images.length > 1 && (
          <div className="absolute bottom-4 left-1/2 z-50 flex -translate-x-1/2 gap-2 rounded-lg bg-black/50 p-2">
            {images.map((img, i) => (
              <button
                key={i}
                onClick={() => { setCurrentIndex(i); setScale(1) }}
                className={`h-12 w-12 overflow-hidden rounded border-2 transition-all ${
                  i === currentIndex ? "border-white opacity-100" : "border-transparent opacity-50 hover:opacity-80"
                }`}
              >
                <img src={img} className="h-full w-full object-cover" alt="" />
              </button>
            ))}
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
