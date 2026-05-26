import { useState, useRef, useEffect, useCallback } from "react"
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Slider } from "@/components/ui/slider"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs"
import { Crop, Type, Wand2, RotateCw, Undo2, Pen } from "lucide-react"

interface ImageEditorProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  imageSrc: string
  onSave: (blob: Blob) => void
}

type CropPreset = "free" | "1:1" | "4:3" | "16:9"

export function ImageEditor({ open, onOpenChange, imageSrc, onSave }: ImageEditorProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [activeTab, setActiveTab] = useState("filter")
  const [filter, setFilter] = useState("none")
  const [rotation, setRotation] = useState(0)
  const [text, setText] = useState("")
  const [textColor, setTextColor] = useState("#ffffff")
  const [textSize, setTextSize] = useState(30)
  const [brightness, setBrightness] = useState(100)
  const [contrast, setContrast] = useState(100)

  // Doodle state
  const [doodleColor, setDoodleColor] = useState("#ff0000")
  const [doodleWidth, setDoodleWidth] = useState(4)
  const [isDrawing, setIsDrawing] = useState(false)
  const [doodlePaths, setDoodlePaths] = useState<{ color: string; width: number; points: { x: number; y: number }[] }[]>([])
  const doodleCanvasRef = useRef<HTMLCanvasElement>(null)

  // Crop state
  const [cropPreset, setCropPreset] = useState<CropPreset>("free")
  const [cropRect, setCropRect] = useState({ x: 0, y: 0, w: 100, h: 100 })
  const [isDragging, setIsDragging] = useState(false)
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 })
  const [imgSize, setImgSize] = useState({ w: 0, h: 0 })
  const cropCanvasRef = useRef<HTMLCanvasElement>(null)

  const drawImage = useCallback(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext("2d")
    if (!ctx) return

    const img = new Image()
    img.src = imageSrc
    img.onload = () => {
      canvas.width = img.width
      canvas.height = img.height
      setImgSize({ w: img.width, h: img.height })
      if (cropRect.w === 100 && cropRect.h === 100) {
        setCropRect({ x: 0, y: 0, w: img.width, h: img.height })
      }

      ctx.clearRect(0, 0, canvas.width, canvas.height)
      ctx.save()
      ctx.translate(canvas.width / 2, canvas.height / 2)
      ctx.rotate((rotation * Math.PI) / 180)
      ctx.translate(-canvas.width / 2, -canvas.height / 2)

      let filterString = `brightness(${brightness}%) contrast(${contrast}%)`
      if (filter === "grayscale") filterString += " grayscale(100%)"
      if (filter === "sepia") filterString += " sepia(100%)"
      if (filter === "invert") filterString += " invert(100%)"
      if (filter === "vibrant") filterString += " saturate(200%) brightness(110%)"
      if (filter === "faded") filterString += " saturate(50%) brightness(120%)"
      ctx.filter = filterString
      ctx.drawImage(img, 0, 0)
      ctx.restore()

      if (text) {
        ctx.save()
        ctx.font = `bold ${textSize}px sans-serif`
        ctx.fillStyle = textColor
        ctx.strokeStyle = "black"
        ctx.lineWidth = textSize / 15
        ctx.textAlign = "center"
        ctx.textBaseline = "middle"
        ctx.strokeText(text, canvas.width / 2, canvas.height / 2)
        ctx.fillText(text, canvas.width / 2, canvas.height / 2)
        ctx.restore()
      }
    }
  }, [imageSrc, filter, rotation, text, textColor, textSize, brightness, contrast])

  useEffect(() => {
    if (open && imageSrc) drawImage()
  }, [open, imageSrc, drawImage])

  const applyCropPreset = (preset: CropPreset) => {
    setCropPreset(preset)
    if (preset === "free") return
    const ratios: Record<string, number> = { "1:1": 1, "4:3": 4 / 3, "16:9": 16 / 9 }
    const ratio = ratios[preset]
    if (!ratio || !imgSize.w) return
    const w = imgSize.w
    const h = Math.round(w / ratio)
    const finalH = Math.min(h, imgSize.h)
    const finalW = Math.round(finalH * ratio)
    setCropRect({ x: Math.round((imgSize.w - finalW) / 2), y: Math.round((imgSize.h - finalH) / 2), w: finalW, h: finalH })
  }

  const handleCropMouseDown = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const rect = cropCanvasRef.current?.getBoundingClientRect()
    if (!rect) return
    const x = ((e.clientX - rect.left) / rect.width) * imgSize.w
    const y = ((e.clientY - rect.top) / rect.height) * imgSize.h
    setIsDragging(true)
    setDragStart({ x, y })
    setCropRect((prev) => ({ ...prev, x, y, w: 0, h: 0 }))
  }

  const handleCropMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!isDragging || !cropCanvasRef.current) return
    const rect = cropCanvasRef.current.getBoundingClientRect()
    const mx = ((e.clientX - rect.left) / rect.width) * imgSize.w
    const my = ((e.clientY - rect.top) / rect.height) * imgSize.h
    let w = mx - dragStart.x
    let h = my - dragStart.y

    if (cropPreset !== "free") {
      const ratios: Record<string, number> = { "1:1": 1, "4:3": 4 / 3, "16:9": 16 / 9 }
      const ratio = ratios[cropPreset] || 1
      h = Math.abs(w) / ratio * Math.sign(h || 1)
    }

    setCropRect({
      x: Math.min(dragStart.x, dragStart.x + w),
      y: Math.min(dragStart.y, dragStart.y + h),
      w: Math.abs(w),
      h: Math.abs(h),
    })
  }

  const handleCropMouseUp = () => setIsDragging(false)

  const drawCropPreview = useCallback(() => {
    const canvas = cropCanvasRef.current
    if (!canvas || !imgSize.w) return
    const ctx = canvas.getContext("2d")
    if (!ctx) return

    canvas.width = imgSize.w
    canvas.height = imgSize.h

    const img = new Image()
    img.src = imageSrc
    img.onload = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height)
      ctx.drawImage(img, 0, 0)

      // Dim outside crop area
      ctx.fillStyle = "rgba(0,0,0,0.5)"
      ctx.fillRect(0, 0, canvas.width, canvas.height)

      // Clear crop area
      if (cropRect.w > 0 && cropRect.h > 0) {
        ctx.clearRect(cropRect.x, cropRect.y, cropRect.w, cropRect.h)
        ctx.drawImage(img, cropRect.x, cropRect.y, cropRect.w, cropRect.h, cropRect.x, cropRect.y, cropRect.w, cropRect.h)

        // Border
        ctx.strokeStyle = "white"
        ctx.lineWidth = 2
        ctx.strokeRect(cropRect.x, cropRect.y, cropRect.w, cropRect.h)

        // Grid
        ctx.strokeStyle = "rgba(255,255,255,0.3)"
        ctx.lineWidth = 1
        for (let i = 1; i < 3; i++) {
          ctx.beginPath()
          ctx.moveTo(cropRect.x + (cropRect.w / 3) * i, cropRect.y)
          ctx.lineTo(cropRect.x + (cropRect.w / 3) * i, cropRect.y + cropRect.h)
          ctx.stroke()
          ctx.beginPath()
          ctx.moveTo(cropRect.x, cropRect.y + (cropRect.h / 3) * i)
          ctx.lineTo(cropRect.x + cropRect.w, cropRect.y + (cropRect.h / 3) * i)
          ctx.stroke()
        }
      }
    }
  }, [imageSrc, cropRect, imgSize])

  useEffect(() => {
    if (activeTab === "crop") drawCropPreview()
  }, [activeTab, drawCropPreview])

  const handleSave = () => {
    const canvas = canvasRef.current
    if (!canvas) return

    if (activeTab === "crop" && cropRect.w > 0 && cropRect.h > 0) {
      // Save cropped image
      const out = document.createElement("canvas")
      out.width = cropRect.w
      out.height = cropRect.h
      const octx = out.getContext("2d")
      if (octx) {
        const img = new Image()
        img.src = imageSrc
        img.onload = () => {
          octx.drawImage(img, cropRect.x, cropRect.y, cropRect.w, cropRect.h, 0, 0, cropRect.w, cropRect.h)
          out.toBlob((blob) => { if (blob) { onSave(blob); onOpenChange(false) } }, "image/jpeg", 0.9)
        }
      }
      return
    }

    canvas.toBlob((blob) => {
      if (blob) {
        onSave(blob)
        onOpenChange(false)
      }
    }, "image/jpeg", 0.9)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl h-[90vh] flex flex-col p-0 gap-0">
        <div className="flex-1 bg-black/90 flex items-center justify-center overflow-hidden relative p-4">
          {activeTab === "crop" ? (
            <canvas
              ref={cropCanvasRef}
              className="max-w-full max-h-full object-contain cursor-crosshair"
              onMouseDown={handleCropMouseDown}
              onMouseMove={handleCropMouseMove}
              onMouseUp={handleCropMouseUp}
              onMouseLeave={handleCropMouseUp}
            />
          ) : (
            <canvas ref={canvasRef} className="max-w-full max-h-full object-contain shadow-2xl" />
          )}
        </div>

        <div className="bg-background border-t p-4 shrink-0">
          <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
            <div className="flex items-center justify-between mb-4">
              <TabsList>
                <TabsTrigger value="filter" className="flex items-center gap-2"><Wand2 className="h-4 w-4" /> 滤镜</TabsTrigger>
                <TabsTrigger value="adjust" className="flex items-center gap-2"><RotateCw className="h-4 w-4" /> 调整</TabsTrigger>
                <TabsTrigger value="crop" className="flex items-center gap-2"><Crop className="h-4 w-4" /> 裁剪</TabsTrigger>
                <TabsTrigger value="text" className="flex items-center gap-2"><Type className="h-4 w-4" /> 文字</TabsTrigger>
                <TabsTrigger value="doodle" className="flex items-center gap-2"><Pen className="h-4 w-4" /> 涂鸦</TabsTrigger>
              </TabsList>

              <div className="flex items-center gap-2">
                <Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
                <Button onClick={handleSave}>发送</Button>
              </div>
            </div>

            <TabsContent value="filter" className="mt-0">
              <div className="flex gap-3 overflow-x-auto pb-2">
                {[
                  { key: "none", label: "原图" },
                  { key: "grayscale", label: "黑白" },
                  { key: "sepia", label: "复古" },
                  { key: "vibrant", label: "鲜明" },
                  { key: "faded", label: "褪色" },
                  { key: "invert", label: "反转" },
                ].map((f) => (
                  <Button key={f.key} variant={filter === f.key ? "default" : "outline"} onClick={() => setFilter(f.key)} size="sm">
                    {f.label}
                  </Button>
                ))}
              </div>
            </TabsContent>

            <TabsContent value="adjust" className="mt-0 space-y-4">
              <div className="grid gap-4 max-w-md">
                <div className="grid gap-2">
                  <Label>旋转 ({rotation}deg)</Label>
                  <div className="flex items-center gap-2">
                    <Slider value={[rotation]} onValueChange={([v]) => setRotation(v)} min={0} max={360} step={90} />
                    <Button size="icon" variant="ghost" onClick={() => setRotation((r) => (r + 90) % 360)}>
                      <RotateCw className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
                <div className="grid gap-2">
                  <Label>亮度 ({brightness}%)</Label>
                  <Slider value={[brightness]} onValueChange={([v]) => setBrightness(v)} min={0} max={200} step={1} />
                </div>
                <div className="grid gap-2">
                  <Label>对比度 ({contrast}%)</Label>
                  <Slider value={[contrast]} onValueChange={([v]) => setContrast(v)} min={0} max={200} step={1} />
                </div>
              </div>
            </TabsContent>

            <TabsContent value="crop" className="mt-0 space-y-3">
              <div className="flex gap-2">
                {(["free", "1:1", "4:3", "16:9"] as CropPreset[]).map((preset) => (
                  <Button key={preset} variant={cropPreset === preset ? "default" : "outline"} size="sm" onClick={() => applyCropPreset(preset)}>
                    {preset === "free" ? "自由" : preset}
                  </Button>
                ))}
              </div>
              <p className="text-xs text-muted-foreground">在图片上拖拽选择裁剪区域</p>
            </TabsContent>

            <TabsContent value="text" className="mt-0 space-y-4">
              <div className="flex gap-4 items-end">
                <div className="grid gap-2 flex-1">
                  <Label>添加文字</Label>
                  <Input value={text} onChange={(e) => setText(e.target.value)} placeholder="输入文字..." />
                </div>
                <div className="grid gap-2 w-24">
                  <Label>颜色</Label>
                  <input type="color" value={textColor} onChange={(e) => setTextColor(e.target.value)} className="h-10 w-full cursor-pointer rounded border p-1" />
                </div>
                <div className="grid gap-2 w-32">
                  <Label>大小 ({textSize}px)</Label>
                  <Slider value={[textSize]} onValueChange={([v]) => setTextSize(v)} min={10} max={100} step={1} />
                </div>
              </div>
            </TabsContent>

            <TabsContent value="doodle" className="mt-0 space-y-3">
              <div className="flex items-center gap-4">
                <div className="flex items-center gap-2">
                  <Label>画笔颜色</Label>
                  <input type="color" value={doodleColor} onChange={(e) => setDoodleColor(e.target.value)} className="h-8 w-8 cursor-pointer rounded border p-0.5" />
                </div>
                <div className="flex items-center gap-2 flex-1">
                  <Label>粗细 ({doodleWidth}px)</Label>
                  <Slider value={[doodleWidth]} onValueChange={([v]) => setDoodleWidth(v)} min={1} max={20} step={1} />
                </div>
                <Button variant="outline" size="sm" onClick={() => setDoodlePaths([])}>
                  <Undo2 className="h-4 w-4 mr-1" /> 撤销全部
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">在图片上自由涂鸦</p>
            </TabsContent>
          </Tabs>
        </div>
      </DialogContent>
    </Dialog>
  )
}
