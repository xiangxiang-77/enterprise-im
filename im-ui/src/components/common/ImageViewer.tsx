import { X, ChevronLeft, ChevronRight, Download, RotateCw, ZoomIn, ZoomOut } from "lucide-react"
import { useState, useEffect } from "react"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogOverlay } from "@/components/ui/dialog"

interface ImageViewerProps {
  isOpen: boolean
  onClose: () => void
  images: string[]
  initialIndex?: number
}

export function ImageViewer({ isOpen, onClose, images, initialIndex = 0 }: ImageViewerProps) {
  const [currentIndex, setCurrentIndex] = useState(initialIndex)
  
  // Sync internal state when props change
  useEffect(() => {
    if (isOpen) {
      setCurrentIndex(initialIndex)
    }
  }, [isOpen, initialIndex])

  if (!isOpen) return null

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-[100vw] h-screen w-screen border-none bg-black/90 p-0 shadow-none sm:rounded-none z-50 flex flex-col items-center justify-center">
        {/* Close Button */}
        <button 
          onClick={onClose}
          className="absolute top-4 right-4 z-50 text-white/70 hover:text-white"
        >
          <X className="h-8 w-8" />
        </button>

        <div className="relative w-full h-full flex items-center justify-center">
            <img 
              src={images[currentIndex]} 
              className="max-w-full max-h-full object-contain"
            />
        </div>
      </DialogContent>
    </Dialog>
  )
}
