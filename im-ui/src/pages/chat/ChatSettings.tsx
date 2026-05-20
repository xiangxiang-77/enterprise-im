import { useNavigate, useParams } from "react-router-dom"
import { 
  ChevronLeft, 
  ChevronRight, 
  Bell, 
  Search, 
  Trash2, 
  Share2, 
  Plus, 
  Minus,
  FileText,
  Image as ImageIcon,
  User as UserIcon
} from "lucide-react"
import { useChatStore } from "@/stores/useChatStore"
import { useAuthStore } from "@/stores/useAuthStore"
import { Button } from "@/components/ui/button"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Switch } from "@/components/ui/switch"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { ContactSelector } from "@/components/common/ContactSelector"
import { useState, useEffect } from "react"
import type { User, Group } from "@/types"

export default function ChatSettings() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { sessions, users, groups, updateSession, updateGroup, addMember, removeMember, setMemberAlias, createGroup } = useChatStore()
  const { user: currentUser } = useAuthStore()
  
  const session = sessions.find((s) => s.id === id)
  const isGroup = session?.type === "group"
  const group = isGroup && id ? groups[id] : undefined
  const isOwner = isGroup && group?.ownerId === currentUser?.id
  
  const [groupName, setGroupName] = useState(session?.name || "")
  const [isNameDialogOpen, setIsNameDialogOpen] = useState(false)
  const [isAliasDialogOpen, setIsAliasDialogOpen] = useState(false)
  const [myAlias, setMyAlias] = useState("")
  const [isQRCodeOpen, setIsQRCodeOpen] = useState(false)
  const [isNoticeOpen, setIsNoticeOpen] = useState(false)
  const [noticeContent, setNoticeContent] = useState(group?.notice || "暂无公告")
  
  // Member management
  const [isAddMemberOpen, setIsAddMemberOpen] = useState(false)
  const [isRemoveMemberOpen, setIsRemoveMemberOpen] = useState(false)
  const [isTransferOwnerOpen, setIsTransferOwnerOpen] = useState(false)
  const [isBackgroundOpen, setIsBackgroundOpen] = useState(false)
  const [isCreateGroupOpen, setIsCreateGroupOpen] = useState(false)

  if (!session) {
    return <div className="flex h-full items-center justify-center">未找到会话</div>
  }

  const handleCreateGroupFromSingle = (selected: (User | Group)[]) => {
      if (selected.length === 0) return
      const name = prompt("请输入群名称", "新建群聊") || "新建群聊"
      const memberIds = selected.map(s => s.id)
      // Include self and current chat target
      const allMembers = Array.from(new Set([...memberIds, currentUser?.id || "", session.targetId]))
      createGroup(name, allMembers)
      setIsCreateGroupOpen(false)
      setTimeout(() => {
          const newState = useChatStore.getState()
          if (newState.activeSessionId) {
              navigate(`/chat/${newState.activeSessionId}`)
          }
      }, 100)
  }


  const handleTransferOwner = (selected: (User | Group)[]) => {
    if (isGroup && group && selected.length > 0) {
      const newOwner = selected[0]
      useChatStore.getState().transferGroupOwner(group.id, newOwner.id)
      setIsTransferOwnerOpen(false)
      alert(`群主已转让给 ${newOwner.name}`)
    }
  }

  const handleUpdateName = () => {
    if (session && groupName.trim()) {
      if (isGroup && group) {
        updateGroup(group.id, { name: groupName })
      } else {
        updateSession(session.id, { name: groupName })
      }
      setIsNameDialogOpen(false)
    }
  }

  const handleUpdateNotice = () => {
    if (isGroup && group) {
      updateGroup(group.id, { notice: noticeContent })
      setIsNoticeOpen(false)
    }
  }

  const handleUpdateAlias = () => {
    if (isGroup && group && currentUser) {
      setMemberAlias(group.id, currentUser.id, myAlias)
      setIsAliasDialogOpen(false)
    }
  }

  const handleAddMembers = (selectedUsers: (User | Group)[]) => {
    if (isGroup && group) {
      selectedUsers.filter((u): u is User => "status" in u).forEach(u => addMember(group.id, u.id))
    }
  }

  const handleRemoveMembers = (selectedUsers: (User | Group)[]) => {
    if (isGroup && group) {
      selectedUsers.filter((u): u is User => "status" in u).forEach(u => removeMember(group.id, u.id))
    }
  }

  const handleClearHistory = () => {
    if (confirm("确定要清空聊天记录吗？")) {
      useChatStore.getState().clearMessages(session.id)
    }
  }

  const SettingsItem = ({ 
    icon: Icon, 
    label, 
    action, 
    value, 
    onClick,
    destructive = false,
    className
  }: { 
    icon?: any, 
    label: string, 
    action?: React.ReactNode, 
    value?: string,
    onClick?: () => void,
    destructive?: boolean,
    className?: string
  }) => (
    <div 
      className={`flex items-center justify-between bg-background px-4 py-3 hover:bg-muted/50 cursor-pointer ${className}`}
      onClick={onClick}
    >
      <div className="flex items-center gap-3">
        {Icon && <Icon className={`h-5 w-5 ${destructive ? "text-destructive" : "text-muted-foreground"}`} />}
        <span className={`text-sm font-medium ${destructive ? "text-destructive" : ""}`}>{label}</span>
      </div>
      <div className="flex items-center gap-2 text-muted-foreground">
        {value && <span className="text-sm max-w-[150px] truncate">{value}</span>}
        {action ? action : <ChevronRight className="h-4 w-4 opacity-50" />}
      </div>
    </div>
  )

  const MemberItem = ({ user, isAdd, isRemove }: { user?: User, isAdd?: boolean, isRemove?: boolean }) => {
    if (isAdd) {
      return (
        <div 
          className="flex flex-col items-center gap-1 cursor-pointer hover:opacity-80"
          onClick={() => isGroup ? setIsAddMemberOpen(true) : setIsCreateGroupOpen(true)}
        >
          <div className="flex h-12 w-12 items-center justify-center rounded-lg border-2 border-dashed border-muted-foreground/30 text-muted-foreground">
            <Plus className="h-6 w-6" />
          </div>
          <span className="text-xs text-muted-foreground">邀请</span>
        </div>
      )
    }
    if (isRemove) {
      return (
        <div 
          className="flex flex-col items-center gap-1 cursor-pointer hover:opacity-80"
          onClick={() => setIsRemoveMemberOpen(true)}
        >
          <div className="flex h-12 w-12 items-center justify-center rounded-lg border-2 border-dashed border-muted-foreground/30 text-muted-foreground">
            <Minus className="h-6 w-6" />
          </div>
          <span className="text-xs text-muted-foreground">移除</span>
        </div>
      )
    }

    const alias = (isGroup && group?.memberAliases?.[user?.id || ""]) || user?.name

    return (
      <div className="flex flex-col items-center gap-1 cursor-pointer" onClick={() => navigate(`/contact/profile/${user?.id}`)}>
        <Avatar className="h-12 w-12 rounded-lg">
          <AvatarImage src={user?.avatar} />
          <AvatarFallback>{user?.name?.[0]}</AvatarFallback>
        </Avatar>
        <span className="w-14 truncate text-center text-xs text-muted-foreground">
          {alias}
        </span>
      </div>
    )
  }

  // Get real members
  const memberList = isGroup && group 
    ? group.members.map(id => users[id]).filter(Boolean)
    : [users[session.targetId]].filter(Boolean)

  return (
    <div className="flex h-full flex-col bg-muted/20">
      <header className="flex items-center gap-2 border-b bg-background px-4 py-3 sticky top-0 z-10">
        <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="-ml-2">
          <ChevronLeft className="h-6 w-6" />
        </Button>
        <h2 className="font-semibold">{isGroup ? `群聊信息(${memberList.length})` : "聊天详情"}</h2>
      </header>

      <ScrollArea className="flex-1">
        <div className="flex flex-col gap-3 pb-8">
          {/* Members Area */}
          <div className="bg-background p-4 mt-2">
            <div className="grid grid-cols-5 gap-y-4">
              {memberList.slice(0, 18).map((m) => <MemberItem key={m.id} user={m} />)}
              <MemberItem isAdd />
              {isGroup && <MemberItem isRemove />}
            </div>
            {isGroup && memberList.length > 18 && (
              <Button variant="ghost" className="w-full mt-4 text-muted-foreground text-sm">
                查看全部群成员 <ChevronRight className="h-4 w-4 ml-1" />
              </Button>
            )}
          </div>

          {/* Group Info */}
          {isGroup && (
            <div className="flex flex-col divide-y border-y bg-background">
              <Dialog open={isNameDialogOpen} onOpenChange={setIsNameDialogOpen}>
                <DialogTrigger asChild>
                  <div className="flex items-center justify-between px-4 py-3 hover:bg-muted/50 cursor-pointer">
                     <span className="text-sm font-medium">群聊名称</span>
                     <div className="flex items-center gap-2 text-muted-foreground">
                       <span className="text-sm max-w-[150px] truncate">{session.name}</span>
                       <ChevronRight className="h-4 w-4 opacity-50" />
                     </div>
                  </div>
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>修改群聊名称</DialogTitle>
                  </DialogHeader>
                  <div className="grid gap-4 py-4">
                    <Input value={groupName} onChange={(e) => setGroupName(e.target.value)} />
                    <Button onClick={handleUpdateName}>保存</Button>
                  </div>
                </DialogContent>
              </Dialog>
              
              <Dialog open={isQRCodeOpen} onOpenChange={setIsQRCodeOpen}>
                <DialogTrigger asChild>
                  <div><SettingsItem label="群二维码" icon={Share2} /></div>
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>群二维码</DialogTitle>
                  </DialogHeader>
                  <div className="flex flex-col items-center justify-center py-8 gap-4">
                    <div className="h-48 w-48 bg-muted rounded-lg flex items-center justify-center">
                       <Share2 className="h-12 w-12 text-muted-foreground" />
                    </div>
                    <p className="text-sm text-muted-foreground">扫一扫加入群聊</p>
                  </div>
                </DialogContent>
              </Dialog>

              <Dialog open={isNoticeOpen} onOpenChange={setIsNoticeOpen}>
                <DialogTrigger asChild>
                   <div><SettingsItem label="群公告" icon={FileText} value={group?.notice ? "已发布" : "未发布"} /></div>
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>群公告</DialogTitle>
                  </DialogHeader>
                  <div className="grid gap-4 py-4">
                    <textarea 
                      className="min-h-[100px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                      value={noticeContent}
                      onChange={(e) => setNoticeContent(e.target.value)}
                      placeholder={isOwner ? "请输入群公告" : "暂无公告"}
                      disabled={!isOwner}
                    />
                    {isOwner && <Button onClick={handleUpdateNotice}>发布</Button>}
                  </div>
                </DialogContent>
              </Dialog>
              
              <SettingsItem 
                label="保存到通讯录" 
                action={<Switch checked={session.isSavedToContacts} onCheckedChange={(c) => updateSession(session.id, { isSavedToContacts: c })} />} 
              />
            </div>
          )}

          {/* Common Settings */}
          <div className="flex flex-col divide-y border-y bg-background">
            <SettingsItem label="查找聊天记录" icon={Search} onClick={() => navigate(`/search?sessionId=${session.id}`)} />
            
            <SettingsItem 
              label="消息免打扰" 
              icon={Bell}
              action={<Switch checked={session.isMuted} onCheckedChange={(c) => updateSession(session.id, { isMuted: c })} />} 
            />
            
            <SettingsItem 
              label="置顶聊天" 
              action={<Switch checked={session.isPinned} onCheckedChange={(c) => updateSession(session.id, { isPinned: c })} />} 
            />
            
            <SettingsItem 
              label="截屏通知" 
              action={<Switch checked={session.isScreenshotNotificationEnabled ?? true} onCheckedChange={(c) => updateSession(session.id, { isScreenshotNotificationEnabled: c })} />} 
            />

            <SettingsItem 
              label="强提醒" 
              action={<Switch checked={session.isStrongReminder} onCheckedChange={(c) => updateSession(session.id, { isStrongReminder: c })} />} 
            />
          </div>

          <div className="flex flex-col divide-y border-y bg-background">
             <Dialog open={isBackgroundOpen} onOpenChange={setIsBackgroundOpen}>
                <DialogTrigger asChild>
                  <div><SettingsItem label="设置当前聊天背景" icon={ImageIcon} /></div>
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>选择背景</DialogTitle>
                  </DialogHeader>
                  <div className="grid grid-cols-3 gap-4 py-4">
                    {['#ffffff', '#f0f0f0', '#ffcccc', '#ccffcc', '#ccccff', '#ffffcc'].map(color => (
                       <div 
                         key={color} 
                         className="h-16 rounded cursor-pointer border hover:border-primary"
                         style={{ background: color }}
                         onClick={() => {
                           // Mock background setting
                           alert("背景设置成功")
                           setIsBackgroundOpen(false)
                         }}
                       />
                    ))}
                  </div>
                </DialogContent>
             </Dialog>
             
             {isGroup && (
               <Dialog open={isAliasDialogOpen} onOpenChange={(open) => {
                 if (open && currentUser && group) {
                   setMyAlias(group.memberAliases?.[currentUser.id] || currentUser.name)
                 }
                 setIsAliasDialogOpen(open)
               }}>
                 <DialogTrigger asChild>
                   <div>
                     <SettingsItem 
                       label="我在本群的昵称" 
                       value={currentUser && group?.memberAliases?.[currentUser.id] ? group.memberAliases[currentUser.id] : currentUser?.name} 
                     />
                   </div>
                 </DialogTrigger>
                 <DialogContent>
                   <DialogHeader>
                     <DialogTitle>修改我在本群的昵称</DialogTitle>
                   </DialogHeader>
                   <div className="grid gap-4 py-4">
                     <Input value={myAlias} onChange={(e) => setMyAlias(e.target.value)} />
                     <Button onClick={handleUpdateAlias}>保存</Button>
                   </div>
                 </DialogContent>
               </Dialog>
             )}

             {isGroup && <SettingsItem label="显示群成员昵称" action={<Switch defaultChecked />} />}
             
             {isGroup && isOwner && (
                <SettingsItem label="群主管理权转让" onClick={() => setIsTransferOwnerOpen(true)} />
             )}

             {!isGroup && (
               <SettingsItem 
                 label="加入黑名单" 
                 action={<Switch checked={session.isBlocked} onCheckedChange={(c) => updateSession(session.id, { isBlocked: c })} />} 
               />
             )}
             
             <SettingsItem label="投诉" onClick={() => alert("投诉已提交")} />
          </div>

          <div className="flex flex-col divide-y border-y bg-background">
            <SettingsItem label="清空聊天记录" icon={Trash2} destructive onClick={handleClearHistory} />
          </div>
          
          <div className="px-4">
            <Button variant="destructive" className="w-full" size="lg" onClick={() => {
              if (isGroup && isOwner && group && group.members.length > 1) {
                  alert("群主退出前请先转让群主")
                  return
              }
              
              if(confirm(isGroup ? (isOwner ? "确定要解散群聊吗？" : "确定要退出群聊吗？") : "确定要删除会话吗？")) {
                if (isGroup && group && !isOwner) {
                   useChatStore.getState().removeMember(group.id, currentUser?.id || "")
                }
                useChatStore.getState().deleteSession(session.id);
                navigate('/chat');
              }
            }}>
              {isGroup ? (isOwner ? "解散群聊" : "删除并退出") : "删除会话"}
            </Button>
          </div>
        </div>
      </ScrollArea>

      {/* Dialogs */}
      {isGroup && group && (
        <>
          <ContactSelector 
             open={isAddMemberOpen} 
             onOpenChange={setIsAddMemberOpen}
             onSelect={handleAddMembers}
             excludeIds={group.members}
             title="邀请群成员"
          />
          <ContactSelector 
             open={isRemoveMemberOpen} 
             onOpenChange={setIsRemoveMemberOpen}
             onSelect={handleRemoveMembers}
             title="移除群成员"
             includeIds={group.members}
             excludeIds={[currentUser?.id || ""]}
          />
          <ContactSelector
             open={isTransferOwnerOpen}
             onOpenChange={setIsTransferOwnerOpen}
             onSelect={handleTransferOwner}
             title="选择新群主"
             includeIds={group.members}
             excludeIds={[currentUser?.id || ""]}
             multiSelect={false}
          />
        </>
      )}
      
      {!isGroup && (
          <ContactSelector 
             open={isCreateGroupOpen} 
             onOpenChange={setIsCreateGroupOpen}
             onSelect={handleCreateGroupFromSingle}
             excludeIds={[currentUser?.id || "", session.targetId]}
             title="发起群聊"
          />
      )}
    </div>
  )
}
