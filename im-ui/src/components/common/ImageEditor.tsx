import { useState, useRef, useEffect } from "react"
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Slider } from "@/components/ui/slider"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs"
import { Crop, Type, Wand2, Check, X, RotateCw } from "lucide-react"

interface ImageEditorProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  imageSrc: string
  onSave: (blob: Blob) => void
}

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

  useEffect(() => {
    if (open && imageSrc) {
      drawImage()
    }
  }, [open, imageSrc, filter, rotation, text, textColor, textSize, brightness, contrast])

  const drawImage = () => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext("2d")
    if (!ctx) return

    const img = new Image()
    img.src = imageSrc
    img.onload = () => {
      // Set canvas size
      canvas.width = img.width
      canvas.height = img.height

      // Clear
      ctx.clearRect(0, 0, canvas.width, canvas.height)

      // Apply transformations
      ctx.save()
      
      // Rotate center
      ctx.translate(canvas.width / 2, canvas.height / 2)
      ctx.rotate((rotation * Math.PI) / 180)
      ctx.translate(-canvas.width / 2, -canvas.height / 2)

      // Draw image
      // Filter logic
      let filterString = `brightness(${brightness}%) contrast(${contrast}%)`
      if (filter === 'grayscale') filterString += ' grayscale(100%)'
      if (filter === 'sepia') filterString += ' sepia(100%)'
      if (filter === 'invert') filterString += ' invert(100%)'
      
      ctx.filter = filterString
      ctx.drawImage(img, 0, 0)
      
      ctx.restore()

      // Draw Text
      if (text) {
        ctx.save()
        ctx.font = `bold ${textSize}px sans-serif`
        ctx.fillStyle = textColor
        ctx.strokeStyle = 'black'
        ctx.lineWidth = textSize / 15
        ctx.textAlign = 'center'
        ctx.textBaseline = 'middle'
        
        // Draw at center for simplicity
        ctx.strokeText(text, canvas.width / 2, canvas.height / 2)
        ctx.fillText(text, canvas.width / 2, canvas.height / 2)
        ctx.restore()
      }
    }
  }

  const handleSave = () => {
    const canvas = canvasRef.current
    if (!canvas) return
    canvas.toBlob((blob) => {
      if (blob) {
        onSave(blob)
        onOpenChange(false)
      }
    }, 'image/jpeg', 0.9)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl h-[90vh] flex flex-col p-0 gap-0">
        <div className="flex-1 bg-black/90 flex items-center justify-center overflow-hidden relative p-4">
          <canvas 
            ref={canvasRef} 
            className="max-w-full max-h-full object-contain shadow-2xl"
          />
        </div>
        
        <div className="bg-background border-t p-4 shrink-0">
          <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
            <div className="flex items-center justify-between mb-4">
              <TabsList>
                <TabsTrigger value="filter" className="flex items-center gap-2"><Wand2 className="h-4 w-4"/> 滤镜</TabsTrigger>
                <TabsTrigger value="adjust" className="flex items-center gap-2"><Crop className="h-4 w-4"/> 调整</TabsTrigger>
                <TabsTrigger value="text" className="flex items-center gap-2"><Type className="h-4 w-4"/> 文字</TabsTrigger>
              </TabsList>
              
              <div className="flex items-center gap-2">
                <Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
                <Button onClick={handleSave}>发送</Button>
              </div>
            </div>

            <TabsContent value="filter" className="mt-0">
              <div className="flex gap-4 overflow-x-auto pb-2">
                {['none', 'grayscale', 'sepia', 'invert'].map(f => (
                  <Button 
                    key={f} 
                    variant={filter === f ? "default" : "outline"}
                    onClick={() => setFilter(f)}
                    className="capitalize"
                  >
                    {f === 'none' ? '原图' : f}
                  </Button>
                ))}
              </div>
            </TabsContent>

            <TabsContent value="adjust" className="mt-0 space-y-4">
               <div className="grid gap-4 max-w-md">
                 <div className="grid gap-2">
                   <Label>旋转 ({rotation}°)</Label>
                   <div className="flex items-center gap-2">
                     <Slider 
                       value={[rotation]} 
                       onValueChange={([v]) => setRotation(v)} 
                       min={0} max={360} step={90} 
                     />
                     <Button size="icon" variant="ghost" onClick={() => setRotation((r) => (r + 90) % 360)}>
                       <RotateCw className="h-4 w-4" />
                     </Button>
                   </div>
                 </div>
                 <div className="grid gap-2">
                   <Label>亮度 ({brightness}%)</Label>
                   <Slider 
                     value={[brightness]} 
                     onValueChange={([v]) => setBrightness(v)} 
                     min={0} max={200} step={1} 
                   />
                 </div>
                 <div className="grid gap-2">
                   <Label>对比度 ({contrast}%)</Label>
                   <Slider 
                     value={[contrast]} 
                     onValueChange={([v]) => setContrast(v)} 
                     min={0} max={200} step={1} 
                   />
                 </div>
               </div>
            </TabsContent>

            <TabsContent value="text" className="mt-0 space-y-4">
              <div className="flex gap-4 items-end">
                <div className="grid gap-2 flex-1">
                  <Label>添加文字</Label>
                  <Input value={text} onChange={(e) => setText(e.target.value)} placeholder="输入文字..." />
                </div>
                <div className="grid gap-2 w-24">
                  <Label>颜色</Label>
                  <input 
                    type="color" 
                    value={textColor} 
                    onChange={(e) => setTextColor(e.target.value)}
                    className="h-10 w-full cursor-pointer rounded border p-1"
                  />
                </div>
                <div className="grid gap-2 w-32">
                   <Label>大小 ({textSize}px)</Label>
                   <Slider 
                     value={[textSize]} 
                     onValueChange={([v]) => setTextSize(v)} 
                     min={10} max={100} step={1} 
                   />
                </div>
              </div>
            </TabsContent>
          </Tabs>
        </div>
      </DialogContent>
    </Dialog>
  )
}
