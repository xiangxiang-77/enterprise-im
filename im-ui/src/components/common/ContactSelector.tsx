import { useState, useMemo } from "react"
import { Search, Check, Users, User as UserIcon } from "lucide-react"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { ScrollArea } from "@/components/ui/scroll-area"
import { useChatStore } from "@/stores/useChatStore"
import type { User, Group } from "@/types"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"

interface ContactSelectorProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSelect: (selected: (User | Group)[]) => void
  multiSelect?: boolean
  title?: string
  excludeIds?: string[]
  includeIds?: string[]
  includeGroups?: boolean
}

export function ContactSelector({ 
  open, 
  onOpenChange, 
  onSelect, 
  multiSelect = true, 
  title = "选择联系人",
  excludeIds = [],
  includeIds,
  includeGroups = false
}: ContactSelectorProps) {
  const { users, groups } = useChatStore()
  const [query, setQuery] = useState("")
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [activeTab, setActiveTab] = useState("contacts")

  const filteredUsers = useMemo(() => {
    return Object.values(users)
      .filter(u => !excludeIds.includes(u.id))
      .filter(u => !includeIds || includeIds.includes(u.id))
      .filter(u => 
        u.name.toLowerCase().includes(query.toLowerCase()) || 
        u.phone?.includes(query)
      )
  }, [users, excludeIds, includeIds, query])

  const filteredGroups = useMemo(() => {
    if (!includeGroups) return []
    return Object.values(groups)
      .filter(g => !excludeIds.includes(g.id))
      .filter(g => 
        g.name.toLowerCase().includes(query.toLowerCase())
      )
  }, [groups, excludeIds, query, includeGroups])

  const handleSelect = (item: User | Group) => {
    if (multiSelect) {
      const newSet = new Set(selectedIds)
      if (newSet.has(item.id)) {
        newSet.delete(item.id)
      } else {
        newSet.add(item.id)
      }
      setSelectedIds(newSet)
    } else {
      onSelect([item])
      onOpenChange(false)
    }
  }

  const handleConfirm = () => {
    const selectedUsers = filteredUsers.filter(u => selectedIds.has(u.id))
    const selectedGroups = filteredGroups.filter(g => selectedIds.has(g.id))
    onSelect([...selectedUsers, ...selectedGroups])
    onOpenChange(false)
    setSelectedIds(new Set())
  }

  const renderItem = (item: User | Group, type: 'user' | 'group') => {
    const isSelected = selectedIds.has(item.id)
    return (
      <div 
        key={item.id} 
        className="flex items-center gap-3 p-2 hover:bg-muted rounded-lg cursor-pointer"
        onClick={() => handleSelect(item)}
      >
        {multiSelect && (
          <div className={`h-5 w-5 rounded-full border flex items-center justify-center ${isSelected ? 'bg-primary border-primary' : 'border-muted-foreground'}`}>
            {isSelected && <Check className="h-3 w-3 text-primary-foreground" />}
          </div>
        )}
        <Avatar>
          <AvatarImage src={item.avatar} />
          <AvatarFallback>
            {type === 'group' ? <Users className="h-4 w-4" /> : item.name[0]}
          </AvatarFallback>
        </Avatar>
        <div className="flex-1 overflow-hidden">
          <div className="flex items-center gap-2">
            <p className="font-medium truncate">{item.name}</p>
            {type === 'group' && <span className="text-xs bg-muted px-1 rounded">群组</span>}
          </div>
          <p className="text-xs text-muted-foreground truncate">
            {type === 'user' ? (item as User).signature || "暂无签名" : `${(item as Group).members.length} 人`}
          </p>
        </div>
      </div>
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[425px] flex flex-col h-[80vh]">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        
        <div className="relative">
          <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input 
            placeholder="搜索" 
            className="pl-8" 
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>

        {includeGroups ? (
          <Tabs defaultValue="contacts" className="flex-1 flex flex-col" onValueChange={setActiveTab}>
            <TabsList className="grid w-full grid-cols-2">
              <TabsTrigger value="contacts">联系人</TabsTrigger>
              <TabsTrigger value="groups">群组</TabsTrigger>
            </TabsList>
            <TabsContent value="contacts" className="flex-1 mt-2">
              <ScrollArea className="h-full -mx-6 px-6">
                <div className="space-y-2 pb-4">
                  {filteredUsers.map(u => renderItem(u, 'user'))}
                  {filteredUsers.length === 0 && <p className="text-center text-muted-foreground text-sm py-8">未找到联系人</p>}
                </div>
              </ScrollArea>
            </TabsContent>
            <TabsContent value="groups" className="flex-1 mt-2">
              <ScrollArea className="h-full -mx-6 px-6">
                <div className="space-y-2 pb-4">
                  {filteredGroups.map(g => renderItem(g, 'group'))}
                  {filteredGroups.length === 0 && <p className="text-center text-muted-foreground text-sm py-8">未找到群组</p>}
                </div>
              </ScrollArea>
            </TabsContent>
          </Tabs>
        ) : (
          <ScrollArea className="flex-1 -mx-6 px-6">
            <div className="space-y-2 py-4">
              {filteredUsers.map(u => renderItem(u, 'user'))}
              {filteredUsers.length === 0 && (
                <p className="text-center text-muted-foreground text-sm py-8">未找到联系人</p>
              )}
            </div>
          </ScrollArea>
        )}

        {multiSelect && (
          <div className="flex items-center justify-between pt-4 border-t mt-auto">
            <span className="text-sm text-muted-foreground">已选择 {selectedIds.size} 项</span>
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
              <Button onClick={handleConfirm} disabled={selectedIds.size === 0}>确定</Button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
