    import { useEffect, useRef, useState } from "react"
import { useParams, useNavigate, useSearchParams } from "react-router-dom"
import { format } from "date-fns"
import { ChevronLeft, ChevronDown, MoreHorizontal, Phone, Video, Trash2, Forward, Star, X, Share2, Megaphone, AtSign } from "lucide-react"
import { useChatStore } from "@/stores/useChatStore"
import { useAuthStore } from "@/stores/useAuthStore"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { MessageBubble } from "@/components/chat/MessageBubble"
import { ChatInput } from "@/components/chat/ChatInput"
import { ImageViewer } from "@/components/common/ImageViewer"
import { ContactSelector } from "@/components/common/ContactSelector"
import { imSocket } from "@/services/imSocket"
import {
  type CallConfig,
  type CallRecord,
  answerCallApi,
  createFileTransferApi,
  editMessageApi,
  favoriteMessageApi,
  fetchCallConfigApi,
  fetchCallHistoryApi,
  fetchConversationMessagesApi,
  fetchMessageReadStatusApi,
  forwardMessagesApi,
  hangupCallApi,
  initiateCallApi,
  reactMessageApi,
  recallMessageApi,
  readMessageApi,
  rejectCallApi,
  screenshotConversationApi,
  sendConversationMessageApi,
  uploadFileApi,
  fetchOnlineStatusApi,
} from "@/services/api"
import type { Message, User, Group, Session } from "@/types"

type WebRtcSignalPayload = {
  callId?: string
  kind?: "offer" | "answer" | "ice" | "hangup"
  data?: unknown
}

function callStatusLabel(status?: string) {
  const labels: Record<string, string> = {
    ringing: "呼叫中",
    answered: "已接听",
    rejected: "已拒绝",
    ended: "已挂断",
  }
  return status ? labels[status] ?? status : "-"
}

function mediaTypeLabel(type?: string) {
  const labels: Record<string, string> = {
    audio: "语音",
    video: "视频",
  }
  return type ? labels[type] ?? type : "-"
}

export default function ChatRoom() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const targetMessageId = searchParams.get("messageId")
  const { 
    sessions, 
    messages, 
    addMessage, 
    setMessages, 
    users, 
    groups, 
    deleteMessage, 
    recallMessage, 
    forwardMessages, 
    updateMessage, 
    addFavorite,
    markMessageAsRead,
    toggleLikeMessage,
    editMessage,
    typingStatus,
    setTyping,
    setDraft,
    resendMessage
  } = useChatStore()
  const { user, token } = useAuthStore()
  const currentUser: User = user || { id: "", name: "未登录", avatar: "", status: "offline" }
  
  const [viewerOpen, setViewerOpen] = useState(false)
  const [viewerImageIndex, setViewerImageIndex] = useState(0)
  
  // Forwarding
  const [forwardDialogOpen, setForwardDialogOpen] = useState(false)
  const [forwardingMessageIds, setForwardingMessageIds] = useState<string[]>([])
  const [forwardingMode, setForwardingMode] = useState<'single' | 'combine'>('single')

  // Multi-select mode
  const [isMultiSelectMode, setIsMultiSelectMode] = useState(false)
  const [selectedMessageIds, setSelectedMessageIds] = useState<Set<string>>(new Set())
  const [replyingTo, setReplyingTo] = useState<any | null>(null)
  const [editingMessage, setEditingMessage] = useState<any | null>(null)
  const [typingUser, setTypingUser] = useState<string | null>(null)
  
  const [readStatusOpen, setReadStatusOpen] = useState(false)
  const [readStatusUsers, setReadStatusUsers] = useState<User[]>([])
  const [showScrollToBottom, setShowScrollToBottom] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const scrollContainerRef = useRef<HTMLDivElement>(null)
  const messageRefs = useRef<Record<string, HTMLDivElement | null>>({})
  const [callDialogOpen, setCallDialogOpen] = useState(false)
  const [callRecord, setCallRecord] = useState<CallRecord | null>(null)
  const [callConfig, setCallConfig] = useState<CallConfig | null>(null)
  const [callHistory, setCallHistory] = useState<CallRecord[]>([])
  const [callError, setCallError] = useState("")
  const [callLoading, setCallLoading] = useState(false)
  const [audioDemoStatus, setAudioDemoStatus] = useState("未开启")
  const [audioDemoRunning, setAudioDemoRunning] = useState(false)
  const [webRtcStatus, setWebRtcStatus] = useState("未连接")
  const audioElementRef = useRef<HTMLAudioElement | null>(null)
  const remoteAudioRef = useRef<HTMLAudioElement | null>(null)
  const localAudioStreamRef = useRef<MediaStream | null>(null)
  const peerConnectionsRef = useRef<RTCPeerConnection[]>([])
  const localVideoRef = useRef<HTMLVideoElement | null>(null)
  const remoteVideoRef = useRef<HTMLVideoElement | null>(null)
  const peerConnectionRef = useRef<RTCPeerConnection | null>(null)
  const localMediaStreamRef = useRef<MediaStream | null>(null)
  const remoteMediaStreamRef = useRef<MediaStream | null>(null)
  const pendingOfferRef = useRef<{ callId: string; data: RTCSessionDescriptionInit } | null>(null)
  const pendingIceCandidatesRef = useRef<RTCIceCandidateInit[]>([])
  const directSession: Session | null = id
    ? {
        id,
        targetId: id,
        type: "single",
        name: users[id]?.name || id,
        avatar: users[id]?.avatar || "",
        unreadCount: 0,
        updatedAt: Date.now(),
        isPinned: false,
        isMuted: false,
      }
    : null
  const session = sessions.find((s) => s.id === id) || directSession
  const sessionMessages = id ? (messages[id] || []) : []
  const isOutgoingCall = Boolean(callRecord && callRecord.callerId === currentUser.id)

  // Get typing users for current session
  const currentTypingUsers = id && typingStatus[id] ? typingStatus[id].filter(t => t.userId !== currentUser.id) : []
  const typingText = currentTypingUsers.length > 0 
      ? (session?.type === 'group' 
          ? `${currentTypingUsers.map(u => u.username).join(', ')} 正在输入...`
          : "对方正在输入...")
      : null

  const handleReadStatusClick = (readBy: string[]) => {
      const readUsers = readBy.map(uid => Object.values(users).find(u => u.id === uid)).filter(Boolean) as User[]
      setReadStatusUsers(readUsers)
      setReadStatusOpen(true)
  }

  const getCallPeerId = (record: CallRecord) =>
    record.callerId === currentUser.id ? record.calleeId : record.callerId

  const bindLocalMedia = () => {
    if (localVideoRef.current) {
      localVideoRef.current.srcObject = localMediaStreamRef.current
    }
    if (remoteVideoRef.current) {
      remoteVideoRef.current.srcObject = remoteMediaStreamRef.current
    }
    if (remoteAudioRef.current) {
      remoteAudioRef.current.srcObject = remoteMediaStreamRef.current
    }
  }

  const stopWebRtcCall = (status = "已断开") => {
      peerConnectionRef.current?.close()
      peerConnectionRef.current = null
      localMediaStreamRef.current?.getTracks().forEach((track) => track.stop())
      localMediaStreamRef.current = null
      remoteMediaStreamRef.current?.getTracks().forEach((track) => track.stop())
      remoteMediaStreamRef.current = null
      pendingIceCandidatesRef.current = []
      if (localVideoRef.current) localVideoRef.current.srcObject = null
      if (remoteVideoRef.current) remoteVideoRef.current.srcObject = null
      if (remoteAudioRef.current) remoteAudioRef.current.srcObject = null
      setWebRtcStatus(status)
  }

  const createPeerConnection = (record: CallRecord, config = callConfig) => {
      const peerId = getCallPeerId(record)
      const pc = new RTCPeerConnection({
        iceServers: config?.turnUrl
          ? [{ urls: config.turnUrl, username: config.turnUsername, credential: config.turnPassword }]
          : [{ urls: "stun:stun.l.google.com:19302" }],
      })
      peerConnectionRef.current = pc
      remoteMediaStreamRef.current = new MediaStream()
      bindLocalMedia()

      pc.onicecandidate = (event) => {
        if (!event.candidate || !peerId) return
        imSocket.sendWebRtcSignal({
          to: peerId,
          conversationId: record.conversationId,
          callId: record.id,
          kind: "ice",
          data: event.candidate.toJSON(),
        })
      }
      pc.ontrack = (event) => {
        const remoteStream = remoteMediaStreamRef.current || new MediaStream()
        remoteMediaStreamRef.current = remoteStream
        event.streams[0]?.getTracks().forEach((track) => {
          if (!remoteStream.getTracks().some((existing) => existing.id === track.id)) {
            remoteStream.addTrack(track)
          }
        })
        if (!event.streams[0]) {
          remoteStream.addTrack(event.track)
        }
        bindLocalMedia()
        setWebRtcStatus(record.mediaType === "video" ? "已接通：正在传输视频/语音" : "已接通：正在传输语音")
      }
      pc.onconnectionstatechange = () => {
        if (pc.connectionState === "connected") {
          setWebRtcStatus(record.mediaType === "video" ? "已接通：视频/语音可用" : "已接通：语音可用")
        }
        if (pc.connectionState === "failed" || pc.connectionState === "disconnected") {
          setWebRtcStatus(`连接${pc.connectionState}`)
        }
      }

      return pc
  }

  const addPendingIceCandidates = async () => {
      const pc = peerConnectionRef.current
      if (!pc?.remoteDescription) return
      const candidates = [...pendingIceCandidatesRef.current]
      pendingIceCandidatesRef.current = []
      for (const candidate of candidates) {
        await pc.addIceCandidate(candidate)
      }
  }

  const startOutgoingWebRtc = async (record: CallRecord, config = callConfig) => {
      const peerId = getCallPeerId(record)
      if (!peerId) {
        setWebRtcStatus("缺少对方用户，无法建立媒体")
        return
      }
      if (!navigator.mediaDevices?.getUserMedia) {
        setWebRtcStatus("当前浏览器不支持麦克风/摄像头")
        return
      }
      stopWebRtcCall("正在请求媒体权限...")
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: true,
        video: record.mediaType === "video",
      })
      localMediaStreamRef.current = stream
      const pc = createPeerConnection(record, config)
      stream.getTracks().forEach((track) => pc.addTrack(track, stream))
      bindLocalMedia()
      const offer = await pc.createOffer()
      await pc.setLocalDescription(offer)
      imSocket.sendWebRtcSignal({
        to: peerId,
        conversationId: record.conversationId,
        callId: record.id,
        kind: "offer",
        data: offer,
      })
      setWebRtcStatus(record.mediaType === "video" ? "已发起视频请求，等待对方接听" : "已发起语音请求，等待对方接听")
  }

  const startAnswerWebRtc = async (record: CallRecord, offer: RTCSessionDescriptionInit, config = callConfig) => {
      const peerId = getCallPeerId(record)
      if (!peerId) {
        setWebRtcStatus("缺少对方用户，无法接听媒体")
        return
      }
      if (!navigator.mediaDevices?.getUserMedia) {
        setWebRtcStatus("当前浏览器不支持麦克风/摄像头")
        return
      }
      stopWebRtcCall("正在请求媒体权限...")
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: true,
        video: record.mediaType === "video",
      })
      localMediaStreamRef.current = stream
      const pc = createPeerConnection(record, config)
      stream.getTracks().forEach((track) => pc.addTrack(track, stream))
      bindLocalMedia()
      await pc.setRemoteDescription(offer)
      await addPendingIceCandidates()
      const answer = await pc.createAnswer()
      await pc.setLocalDescription(answer)
      imSocket.sendWebRtcSignal({
        to: peerId,
        conversationId: record.conversationId,
        callId: record.id,
        kind: "answer",
        data: answer,
      })
      setWebRtcStatus(record.mediaType === "video" ? "已接听视频，等待媒体连接" : "已接听语音，等待媒体连接")
  }

  const startRealCall = async (type: 'voice' | 'video') => {
      if (!id || !session) return
      if (token && currentUser.id) {
        imSocket.connect(token, currentUser.id)
      }
      setCallDialogOpen(true)
      setCallLoading(true)
      setCallError("")
      setCallRecord(null)
      setWebRtcStatus("正在创建通话...")

      try {
        const [config, record] = await Promise.all([
          fetchCallConfigApi(token ?? undefined),
          initiateCallApi({
            callerId: currentUser.id,
            calleeId: session.targetId,
            conversationId: id,
            mediaType: type === "voice" ? "audio" : "video",
          }, token ?? undefined),
        ])
        setCallConfig(config)
        setCallRecord(record)
        void startOutgoingWebRtc(record, config).catch((err) => {
          setWebRtcStatus(err instanceof Error ? `媒体启动失败：${err.message}` : "媒体启动失败")
        })
        void refreshCallHistory()
        addMessage(id, {
            id: `sys-${Date.now()}`,
            sessionId: id,
            senderId: 'system',
            content: `Call started: ${record.mediaType}, status ${record.status}`,
            type: 'system',
            timestamp: Date.now(),
            status: 'sent'
        })
      } catch (err) {
        setCallError(err instanceof Error ? err.message : "Call failed")
      } finally {
        setCallLoading(false)
      }
  }

  const handleAnswerCall = async () => {
      if (!callRecord) return
      setCallLoading(true)
      setCallError("")
      try {
        if (isOutgoingCall) {
          throw new Error("Waiting for peer device to answer")
        }
        const answered = await answerCallApi(callRecord.id, currentUser.id, token ?? undefined)
        setCallRecord(answered)
        const pendingOffer = pendingOfferRef.current
        if (!isOutgoingCall && pendingOffer?.callId === answered.id) {
          const config = callConfig ?? await fetchCallConfigApi(token ?? undefined)
          setCallConfig(config)
          await startAnswerWebRtc(answered, pendingOffer.data, config)
          pendingOfferRef.current = null
        }
        void refreshCallHistory()
        if (answered.conversationId) {
          addMessage(answered.conversationId, {
              id: `sys-call-answer-${Date.now()}`,
              sessionId: answered.conversationId,
              senderId: 'system',
              content: `Call answered: ${answered.id}`,
              type: 'system',
              timestamp: Date.now(),
              status: 'sent'
          })
        }
      } catch (err) {
        setCallError(err instanceof Error ? err.message : "Answer failed")
      } finally {
        setCallLoading(false)
      }
  }

  const handleRejectCall = async () => {
      if (!callRecord) return
      setCallLoading(true)
      setCallError("")
      try {
        if (isOutgoingCall) {
          throw new Error("Waiting for peer device to reject or hang up")
        }
        const rejected = await rejectCallApi(callRecord.id, currentUser.id, token ?? undefined)
        setCallRecord(rejected)
        stopWebRtcCall("已拒绝")
        void refreshCallHistory()
        if (rejected.conversationId) {
          addMessage(rejected.conversationId, {
              id: `sys-call-reject-${Date.now()}`,
              sessionId: rejected.conversationId,
              senderId: 'system',
              content: `Call rejected: ${rejected.id}`,
              type: 'system',
              timestamp: Date.now(),
              status: 'sent'
          })
        }
      } catch (err) {
        setCallError(err instanceof Error ? err.message : "Reject failed")
      } finally {
        setCallLoading(false)
      }
  }

  const handleHangupCall = async () => {
      if (!callRecord) {
        setCallDialogOpen(false)
        return
      }
      setCallLoading(true)
      setCallError("")
      try {
        const ended = await hangupCallApi(callRecord.id, currentUser.id, token ?? undefined)
        setCallRecord(ended)
        const peerId = getCallPeerId(callRecord)
        if (peerId) {
          imSocket.sendWebRtcSignal({
            to: peerId,
            conversationId: callRecord.conversationId,
            callId: callRecord.id,
            kind: "hangup",
          })
        }
        stopWebRtcCall("已挂断")
        void refreshCallHistory()
        if (id) {
          addMessage(id, {
              id: `sys-${Date.now()}`,
              sessionId: id,
              senderId: 'system',
              content: `Call ended: ${ended.id}`,
              type: 'system',
              timestamp: Date.now(),
              status: 'sent'
          })
        }
      } catch (err) {
        setCallError(err instanceof Error ? err.message : "Hangup failed")
      } finally {
        setCallLoading(false)
      }
  }

  const refreshCallHistory = async () => {
      if (!token || !currentUser.id) return
      try {
        const history = await fetchCallHistoryApi(currentUser.id, 5, token)
        setCallHistory(history)
      } catch {
        setCallHistory([])
      }
  }

  const stopLocalAudioDemo = () => {
      peerConnectionsRef.current.forEach((pc) => pc.close())
      peerConnectionsRef.current = []
      localAudioStreamRef.current?.getTracks().forEach((track) => track.stop())
      localAudioStreamRef.current = null
      if (audioElementRef.current) {
        audioElementRef.current.pause()
        audioElementRef.current.srcObject = null
      }
      setAudioDemoRunning(false)
      setAudioDemoStatus("已关闭")
  }

  const startLocalAudioDemo = async () => {
      if (!navigator.mediaDevices?.getUserMedia) {
        setAudioDemoStatus("当前浏览器不支持麦克风采集")
        return
      }
      stopLocalAudioDemo()
      setAudioDemoStatus("正在请求麦克风权限...")
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false })
        localAudioStreamRef.current = stream

        const caller = new RTCPeerConnection()
        const receiver = new RTCPeerConnection()
        peerConnectionsRef.current = [caller, receiver]

        caller.onicecandidate = (event) => {
          if (event.candidate) void receiver.addIceCandidate(event.candidate)
        }
        receiver.onicecandidate = (event) => {
          if (event.candidate) void caller.addIceCandidate(event.candidate)
        }
        receiver.ontrack = async (event) => {
          const [remoteStream] = event.streams
          if (!audioElementRef.current || !remoteStream) return
          audioElementRef.current.srcObject = remoteStream
          audioElementRef.current.muted = false
          audioElementRef.current.volume = 1
          await audioElementRef.current.play()
        }

        stream.getAudioTracks().forEach((track) => caller.addTrack(track, stream))
        const offer = await caller.createOffer()
        await caller.setLocalDescription(offer)
        await receiver.setRemoteDescription(offer)
        const answer = await receiver.createAnswer()
        await receiver.setLocalDescription(answer)
        await caller.setRemoteDescription(answer)

        setAudioDemoRunning(true)
        setAudioDemoStatus("已开启：请对着麦克风说话，扬声器会播放本机回环声音")
      } catch (err) {
        stopLocalAudioDemo()
        setAudioDemoStatus(err instanceof Error ? `开启失败：${err.message}` : "开启失败")
      }
  }

  useEffect(() => () => {
    stopLocalAudioDemo()
    stopWebRtcCall()
  }, [])

  const handleLoadMore = () => {
      if (!id || !token) return
      fetchConversationMessagesApi(id, 50)
        .then((history) => setMessages(id, history))
        .catch(() => undefined)
  }

  useEffect(() => {
    if (id) {
      // Clear selection when session changes
      setIsMultiSelectMode(false)
      setSelectedMessageIds(new Set())
      setReplyingTo(null)
      setEditingMessage(null)
    }
  }, [id])

  useEffect(() => {
    if (!id || !token || !currentUser.id) return
    imSocket.connect(token, currentUser.id)
    const unsubscribe = imSocket.onMessage((socketMessage) => {
      if (socketMessage.type === "ACK" && socketMessage.conversationId === id) {
        const serverId = typeof socketMessage.payload?.messageId === "string" ? socketMessage.payload.messageId : undefined
        updateMessage(id, socketMessage.requestId, { status: "sent", serverId })
        return
      }
      if (socketMessage.type === "CALL_INVITE" || socketMessage.type === "CALL_UPDATE") {
        const record = socketMessage.payload as CallRecord | undefined
        if (!record?.id || !record.conversationId) return
        setCallRecord(record)
        setCallDialogOpen(true)
        if (record.status === "ended" || record.status === "rejected") {
          stopWebRtcCall(record.status === "ended" ? "对方已挂断" : "对方已拒绝")
        }
        void refreshCallHistory()
        addMessage(record.conversationId, {
          id: `sys-${socketMessage.type}-${record.id}-${Date.now()}`,
          sessionId: record.conversationId,
          senderId: "system",
          content: `${socketMessage.type === "CALL_INVITE" ? "收到通话邀请" : "通话状态更新"}：${mediaTypeLabel(record.mediaType)}，${callStatusLabel(record.status)}`,
          type: "system",
          timestamp: socketMessage.timestamp || Date.now(),
          status: "sent",
        })
        return
      }
      if (socketMessage.type === "WEBRTC_SIGNAL") {
        const payload = socketMessage.payload as WebRtcSignalPayload | undefined
        if (!payload?.callId || !payload.kind) return
        if (payload.kind === "offer") {
          pendingOfferRef.current = {
            callId: payload.callId,
            data: payload.data as RTCSessionDescriptionInit,
          }
          setWebRtcStatus("收到媒体请求，点击接听后建立真实音视频")
          setCallDialogOpen(true)
          return
        }
        if (payload.kind === "answer") {
          const pc = peerConnectionRef.current
          if (pc && !pc.remoteDescription) {
            void pc.setRemoteDescription(payload.data as RTCSessionDescriptionInit)
              .then(addPendingIceCandidates)
              .then(() => setWebRtcStatus("对方已接听，正在建立媒体连接"))
              .catch((err) => setWebRtcStatus(err instanceof Error ? `接收应答失败：${err.message}` : "接收应答失败"))
          }
          return
        }
        if (payload.kind === "ice") {
          const candidate = payload.data as RTCIceCandidateInit
          const pc = peerConnectionRef.current
          if (!pc?.remoteDescription) {
            pendingIceCandidatesRef.current.push(candidate)
          } else {
            void pc.addIceCandidate(candidate).catch((err) => {
              setWebRtcStatus(err instanceof Error ? `ICE 添加失败：${err.message}` : "ICE 添加失败")
            })
          }
          return
        }
        if (payload.kind === "hangup") {
          stopWebRtcCall("对方已挂断")
        }
        return
      }
      if (socketMessage.type === "TYPING_DELIVER") {
        const from = socketMessage.from
        const convId = socketMessage.conversationId
        const isTyping = socketMessage.payload?.isTyping === true
        if (from && convId) {
          const sender = users[from]
          setTyping(convId, from, sender?.name || from, isTyping)
        }
        return
      }
      if (socketMessage.type !== "TEXT_DELIVER") return
      if (!socketMessage.conversationId || socketMessage.conversationId !== id) return
      const content = typeof socketMessage.payload?.content === "string" ? socketMessage.payload.content : ""
      if (!content) return
      addMessage(socketMessage.conversationId, {
        id: socketMessage.requestId || `ws-${Date.now()}`,
        sessionId: socketMessage.conversationId,
        senderId: socketMessage.from || "unknown",
        content,
        type: "text",
        timestamp: socketMessage.timestamp || Date.now(),
        status: "read",
      })
    })
    return unsubscribe
  }, [addMessage, currentUser.id, id, token, updateMessage])

  useEffect(() => {
    if (callDialogOpen) {
      void refreshCallHistory()
    }
  }, [callDialogOpen, currentUser.id, token])

  useEffect(() => {
    if (!token || !id || session?.type !== 'group') return
    const group = groups[session.targetId]
    if (!group?.members?.length) return

    const pollOnline = () => {
      fetchOnlineStatusApi(group.members, token)
        .then(statusMap => {
          const store = useChatStore.getState()
          group.members.forEach((uid: string) => {
            const current = store.users[uid]
            store.addUser({ ...(current || { id: uid, name: uid, avatar: '', status: 'offline', isFriend: false }), status: statusMap[uid] ? 'online' : 'offline' })
          })
        })
        .catch(() => {})
    }
    pollOnline()
    const interval = setInterval(pollOnline, 30000)
    return () => clearInterval(interval)
  }, [token, id, session?.targetId, session?.type, groups])

  const mentionUsers = session?.type === 'group' && groups[session.targetId]
      ? groups[session.targetId].members.map(uid => Object.values(users).find(u => u.id === uid)).filter(Boolean) as User[]
      : []

  const images = sessionMessages
    .filter(m => m.type === 'image' && m.fileUrl)
    .map(m => m.fileUrl!)

  useEffect(() => {
    if (!id) return
    let cancelled = false

    if (token) {
      fetchConversationMessagesApi(id)
        .then((history) => {
          if (!cancelled) {
            setMessages(id, history)
          }
        })
        .catch(() => undefined)
      return () => {
        cancelled = true
      }
    }

    if (!messages[id] || messages[id].length === 0) {
      setMessages(id, [])
    }
  }, [id, token, setMessages, currentUser.id])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" })
    setShowScrollToBottom(false)
  }, [sessionMessages])

  const handleScroll = () => {
    const container = scrollContainerRef.current
    if (!container) return
    const distanceFromBottom = container.scrollHeight - container.scrollTop - container.clientHeight
    setShowScrollToBottom(distanceFromBottom > 100)
  }

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" })
    setShowScrollToBottom(false)
  }

  useEffect(() => {
    if (!targetMessageId) return
    const timer = window.setTimeout(() => {
      const target = messageRefs.current[targetMessageId]
        || Object.entries(messageRefs.current).find(([key]) => key.endsWith(`:${targetMessageId}`))?.[1]
      target?.scrollIntoView({ behavior: "smooth", block: "center" })
    }, 300)
    return () => window.clearTimeout(timer)
  }, [targetMessageId, sessionMessages.length])

  useEffect(() => {
    if (!token || !currentUser.id) return
    sessionMessages
      .filter((message) => message.serverId && message.senderId !== currentUser.id && message.status !== "read")
      .slice(-10)
      .forEach((message) => {
        void readMessageApi(message.serverId!, token).then(() => {
          if (id) markMessageAsRead(id, message.id, currentUser.id)
        }).catch(() => undefined)
      })
  }, [currentUser.id, id, markMessageAsRead, sessionMessages, token])

  if (!session) {
    return <div className="flex h-full items-center justify-center">未找到会话</div>
  }

  const handleSendMessage = async (content: string, type: "text" | "image" | "file" | "voice" | "card" | "location", extra?: any) => {
      if (!id) return
      
      setDraft(id, "") // Clear draft

      const messageId = `msg-${Date.now()}-${Math.random().toString(36).slice(2)}`
      const isRealTextSend = type === "text" && Boolean(token)
      const newMessage = {
          id: messageId,
          sessionId: id,
          senderId: currentUser.id,
          content,
          type: type as any,
          timestamp: Date.now(),
          status: "sending" as const,
          // Merge extra properties (fileUrl, fileName, fileSize, voiceDuration, cardInfo)
          ...extra
      }
      addMessage(id, newMessage)

      if (type === "text" && token) {
        imSocket.connect(token, currentUser.id)
        imSocket.sendText({
          to: session.targetId,
          conversationId: id,
          content,
          requestId: messageId,
        })
      }

      if (isRealTextSend) {
        return
      }

      if (!token || !currentUser.id) {
        updateMessage(id, newMessage.id, { status: "failed" })
        return
      }

      try {
        let fileId = extra?.fileId as string | undefined
        if ((type === "file" || type === "image" || type === "voice") && extra?.rawFile instanceof File) {
          const file = extra.rawFile as File
          const created = await uploadFileApi(currentUser.id, file, token)
          fileId = created.id
          updateMessage(id, newMessage.id, { fileId, fileUrl: created.previewUrl || created.downloadUrl || extra?.fileUrl, status: "sending" } as Partial<Message>)
          await createFileTransferApi(fileId, { direction: "upload", progress: 100, status: "completed" }, token)
        }

        const saved = await sendConversationMessageApi(id, {
          senderId: currentUser.id,
          type: type as Message["type"],
          content,
          fileId,
          clientSeq: messageId,
          conversationType: session.type,
          targetId: session.targetId,
          expireAfterRead: Boolean(extra?.isReadAfterBurn),
        }, token)
        updateMessage(id, newMessage.id, {
          status: "sent",
          serverId: saved.serverId,
          fileId: saved.fileId || fileId,
          isReadAfterBurn: saved.isReadAfterBurn || Boolean(extra?.isReadAfterBurn),
        } as Partial<Message>)
      } catch (err) {
        updateMessage(id, newMessage.id, { status: "failed" })
      }
  }

  const handlePreviewImage = (url: string) => {
    // Force re-render viewer by key or ensure images array is stable
    const index = images.indexOf(url)
    if (index !== -1) {
      setViewerImageIndex(index)
      setViewerOpen(true)
    }
  }

  // Message Actions
  const toggleSelectMessage = (msgId: string, checked: boolean) => {
    const newSelected = new Set(selectedMessageIds)
    if (checked) {
      newSelected.add(msgId)
    } else {
      newSelected.delete(msgId)
    }
    setSelectedMessageIds(newSelected)
  }

  const handleDeleteMessages = () => {
    if (!id) return
    const newMessages = sessionMessages.filter(m => !selectedMessageIds.has(m.id))
    setMessages(id, newMessages)
    setIsMultiSelectMode(false)
    setSelectedMessageIds(new Set())
  }
  
  const handleDeleteSingleMessage = (msgId: string) => {
    if (!id) return
    deleteMessage(id, msgId)
  }

  const handleRecallMessage = async (msgId: string) => {
     if (!id) return
     const message = sessionMessages.find((item) => item.id === msgId)
     if (token && message?.serverId) {
       try {
         const saved = await recallMessageApi(message.serverId, "user_recall", token)
         updateMessage(id, msgId, { ...saved, id: msgId })
       } catch {
         updateMessage(id, msgId, { status: "failed" })
       }
       return
     }
     recallMessage(id, msgId)
  }

  const handleForwardMessages = (mode: 'single' | 'combine' = 'single') => {
    setForwardingMode(mode)
    setForwardingMessageIds(Array.from(selectedMessageIds))
    setForwardDialogOpen(true)
  }

  const handleSingleForward = (msgId: string) => {
    setForwardingMode('single')
    setForwardingMessageIds([msgId])
    setForwardDialogOpen(true)
  }

  const handleForwardConfirm = (targets: (User | Group)[]) => {
    if (!id || forwardingMessageIds.length === 0) return
    const targetIds = targets.map(t => t.id)
    if (forwardingMessageIds.length > 100) {
      setSelectedMessageIds(new Set(Array.from(selectedMessageIds).slice(0, 100)))
      return
    }
    const serverMessageIds = forwardingMessageIds
      .map((messageId) => sessionMessages.find((item) => item.id === messageId)?.serverId)
      .filter(Boolean) as string[]
    if (token && serverMessageIds.length === forwardingMessageIds.length) {
      void forwardMessagesApi(serverMessageIds, targetIds, forwardingMode, token)
        .then(() => forwardMessages(id, forwardingMessageIds, targetIds, forwardingMode))
        .catch(() => undefined)
        .finally(() => {
          setForwardDialogOpen(false)
          setForwardingMessageIds([])
          setIsMultiSelectMode(false)
          setSelectedMessageIds(new Set())
        })
      return
    }
    forwardMessages(id, forwardingMessageIds, targetIds, forwardingMode)
    setForwardDialogOpen(false)
    setForwardingMessageIds([])
    setIsMultiSelectMode(false)
    setSelectedMessageIds(new Set())
  }

  const handleScreenshot = async () => {
    if (!id) return
    const newMessage: Message = {
        id: Date.now().toString(),
        sessionId: id,
        senderId: 'system',
        content: `${currentUser.name} 进行了截屏`,
        type: "system",
        timestamp: Date.now(),
        status: "read"
    }
    // Local screenshot notice setting is still client-side for this submit slice.
    if (session.isScreenshotNotificationEnabled !== false) {
       addMessage(id, newMessage)
    }
    if (token) {
      await screenshotConversationApi(id, token).catch(() => undefined)
    }
  }

  const handleFavoriteMessage = async (message: Message) => {
    addFavorite(message)
    if (token && message.serverId) {
      await favoriteMessageApi(message.serverId, token).catch(() => undefined)
    }
  }

  const handleFavoriteMessages = () => {
    selectedMessageIds.forEach(messageId => {
      const msg = sessionMessages.find(m => m.id === messageId)
      if (msg) addFavorite(msg)
      if (msg?.serverId && token) void favoriteMessageApi(msg.serverId, token).catch(() => undefined)
    })
    setIsMultiSelectMode(false)
    setSelectedMessageIds(new Set())
  }

  const handleLikeMessage = async (message: Message) => {
    if (!id) return
    toggleLikeMessage(id, message.id, currentUser.id)
    if (token && message.serverId) {
      await reactMessageApi(message.serverId, "like", token).catch(() => undefined)
    }
  }

  const handleEditMessage = async (messageId: string, content: string) => {
    if (!id) return
    const message = sessionMessages.find((item) => item.id === messageId)
    if (token && message?.serverId) {
      try {
        const saved = await editMessageApi(message.serverId, content, token)
        updateMessage(id, messageId, { content: saved.content, isEdited: true, serverId: saved.serverId })
        setEditingMessage(null)
      } catch {
        updateMessage(id, messageId, { status: "failed" })
      }
      return
    }
    editMessage(id, messageId, content)
    setEditingMessage(null)
  }

  const handleFileTransferProgress = async (message: Message, progress: number, status: string) => {
    if (!token || !message.fileId) return
    await createFileTransferApi(message.fileId, { direction: "download", progress, status }, token).catch(() => undefined)
  }

  const handleServerReadStatusClick = async (message: Message) => {
    if (token && message.serverId) {
      const status = await fetchMessageReadStatusApi(message.serverId, token).catch(() => undefined)
      const members = status ? [...status.read, ...status.unread] : []
      setReadStatusUsers(members.map((member) => {
        const known = Object.values(users).find((item) => item.id === member.userId)
        return known || { id: member.userId, name: member.userName || member.userId, avatar: "", status: "offline" as const }
      }))
    } else {
      handleReadStatusClick(message.readBy || [])
    }
    setReadStatusOpen(true)
  }

  return (
    <div className="flex h-full flex-col bg-background relative">
      {/* Header */}
      <header className="flex items-center justify-between border-b px-4 py-3 bg-background/95 backdrop-blur z-10 sticky top-0">
        <div className="flex items-center gap-2">
          {isMultiSelectMode ? (
            <Button variant="ghost" onClick={() => {
              setIsMultiSelectMode(false)
              setSelectedMessageIds(new Set())
            }}>取消</Button>
          ) : (
            <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="-ml-2">
              <ChevronLeft className="h-6 w-6" />
            </Button>
          )}
          
          <div className="flex items-center gap-2">
             {!isMultiSelectMode && (
               <Avatar className="h-8 w-8">
                  <AvatarImage src={session.avatar} />
                  <AvatarFallback>{session.name[0]}</AvatarFallback>
               </Avatar>
             )}
             <div>
                 <h2 className="text-sm font-semibold">
                   {isMultiSelectMode ? `已选择 ${selectedMessageIds.size} 条` : session.name}
                 </h2>
                 {!isMultiSelectMode && typingText && (
                   <p className="text-[10px] text-primary mt-0.5 leading-none animate-pulse">
                     {typingText}
                   </p>
                 )}
                 {!isMultiSelectMode && !typingText && session.type === 'single' && users[session.targetId] && (
                   <div className="flex items-center gap-1.5 mt-0.5">
                     <span className={`h-1.5 w-1.5 rounded-full ${users[session.targetId].status === 'online' ? 'bg-green-500' : users[session.targetId].status === 'busy' ? 'bg-orange-500' : 'bg-gray-400'}`} />
                     <p className="text-[10px] text-muted-foreground leading-none">
                       {users[session.targetId].status === 'online' ? '在线' : users[session.targetId].status === 'busy' ? '忙碌' : '离线'}
                     </p>
                   </div>
                 )}
                 {!isMultiSelectMode && !typingText && session.type === 'group' && groups[session.targetId] && (
                   <p className="text-[10px] text-muted-foreground mt-0.5 leading-none">
                     {groups[session.targetId].members.length} 人 ({groups[session.targetId].members.filter(uid => users[uid]?.status === 'online').length} 在线)
                   </p>
                 )}
             </div>
          </div>
        </div>
        {!isMultiSelectMode && (
          <div className="flex items-center gap-1">
            <Button variant="outline" size="sm" className="gap-1" onClick={() => startRealCall('voice')}>
              <Phone className="h-5 w-5" />
              语音
            </Button>
            <Button variant="outline" size="sm" className="gap-1" onClick={() => startRealCall('video')}>
              <Video className="h-5 w-5" />
              视频
            </Button>
            <Button variant="ghost" size="icon" onClick={() => navigate(`/chat/settings/${id}`)}>
              <MoreHorizontal className="h-5 w-5" />
            </Button>
          </div>
        )}
      </header>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-4" ref={scrollContainerRef} onScroll={handleScroll}>
        <div className="flex flex-col gap-4 pb-4">
          <div ref={messagesEndRef} />
          <div className="flex justify-center mb-2">
            <Button variant="ghost" size="sm" onClick={handleLoadMore} className="text-xs text-muted-foreground">
              查看更多历史消息
            </Button>
          </div>

          {session.type === 'group' && groups[session.targetId]?.notice && (
            <div className="mb-4 flex items-start gap-3 rounded-lg border bg-amber-50 p-3 dark:bg-amber-950/30">
              <Megaphone className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
              <div className="min-w-0 flex-1">
                <p className="text-xs font-semibold text-amber-700 dark:text-amber-400">群公告</p>
                <p className="mt-0.5 text-sm text-amber-800 dark:text-amber-300">{groups[session.targetId].notice}</p>
              </div>
            </div>
          )}
          {(() => {
            // Group consecutive image messages from the same sender into grids
            const rendered: React.ReactNode[] = []
            let i = 0
            while (i < sessionMessages.length) {
              const message = sessionMessages[i]
              const isMe = message.senderId === currentUser.id

              // Check for consecutive images from same sender
              if (message.type === "image" && message.fileUrl) {
                const group: Message[] = [message]
                let j = i + 1
                while (j < sessionMessages.length && sessionMessages[j].type === "image" && sessionMessages[j].senderId === message.senderId && sessionMessages[j].fileUrl && group.length < 9) {
                  group.push(sessionMessages[j])
                  j++
                }

                if (group.length >= 2) {
                  // Render as image grid
                  const senderUser = Object.values(users).find(u => u.id === message.senderId)
                  let sender = isMe ? currentUser : senderUser
                  if (!isMe && session.type === 'group' && senderUser && groups[session.targetId]) {
                    const alias = groups[session.targetId].memberAliases?.[message.senderId]
                    if (alias) sender = { ...senderUser, name: alias }
                  }

                  const gridCols = group.length <= 4 ? 2 : 3
                  rendered.push(
                    <div key={`img-grid-${message.id}`} className={`group relative mb-4 flex w-full gap-2 ${isMe ? "flex-row-reverse" : "flex-row"}`}>
                      <Avatar className="h-9 w-9 shrink-0 cursor-pointer" onClick={() => sender?.id && navigate(`/contact/profile/${sender.id}`)}>
                        <AvatarImage src={sender?.avatar} />
                        <AvatarFallback>{sender?.name?.slice(0, 2)}</AvatarFallback>
                      </Avatar>
                      <div className={`flex max-w-[70%] flex-col ${isMe ? "items-end" : "items-start"}`}>
                        {!isMe && session.type === 'group' && <span className="mb-1 ml-1 text-xs text-muted-foreground">{sender?.name}</span>}
                        <div className={`grid gap-1 rounded-2xl p-1 ${isMe ? "bg-primary" : "border bg-white dark:bg-muted"}`} style={{ gridTemplateColumns: `repeat(${gridCols}, 1fr)` }}>
                          {group.map((img) => (
                            <div key={img.id} className="cursor-pointer overflow-hidden rounded-lg" onClick={() => handlePreviewImage(img.fileUrl!)}>
                              <img src={img.fileUrl} alt="图片" className="h-24 w-24 object-cover" loading="lazy" />
                            </div>
                          ))}
                        </div>
                        <span className="mt-1 text-[10px] text-muted-foreground">{format(group[0].timestamp, "HH:mm")}</span>
                      </div>
                    </div>
                  )
                  i = j
                  continue
                }
              }

              // Regular message rendering
              const senderUser = Object.values(users).find(u => u.id === message.senderId)
              let sender = isMe ? currentUser : senderUser
              if (!isMe && session.type === 'group' && senderUser && groups[session.targetId]) {
                  const alias = groups[session.targetId].memberAliases?.[message.senderId]
                  if (alias) sender = { ...senderUser, name: alias }
              }
              const quotedMessage = message.quoteId ? sessionMessages.find(m => m.id === message.quoteId) : undefined

              rendered.push(
                <div
                  key={message.id}
                  ref={(node) => {
                    messageRefs.current[message.id] = node
                    if (message.serverId) messageRefs.current[`${message.id}:${message.serverId}`] = node
                  }}
                  className={targetMessageId && (targetMessageId === message.id || targetMessageId === message.serverId) ? "rounded-md ring-2 ring-primary/50" : undefined}
                >
                  <MessageBubble
                    message={message}
                    isMe={isMe}
                    isGroup={session.type === 'group'}
                    sender={sender}
                    quotedMessage={quotedMessage}
                    onPreviewImage={handlePreviewImage}
                    isMultiSelectMode={isMultiSelectMode}
                    isSelected={selectedMessageIds.has(message.id)}
                    onSelect={() => toggleSelectMessage(message.id, !selectedMessageIds.has(message.id))}
                    onDelete={() => handleDeleteSingleMessage(message.id)}
                    onRecall={() => handleRecallMessage(message.id)}
                    onReply={() => setReplyingTo(message)}
                    onEdit={() => setEditingMessage(message)}
                    onAvatarClick={(userId) => navigate(`/contact/profile/${userId}`)}
                    onForward={() => handleSingleForward(message.id)}
                    onFavorite={() => void handleFavoriteMessage(message)}
                    onLike={() => void handleLikeMessage(message)}
                    onReadStatusClick={() => void handleServerReadStatusClick(message)}
                    onFileTransferProgress={(progress, status) => void handleFileTransferProgress(message, progress, status)}
                    onResend={() => id && resendMessage(id, message.id)}
                    onEnterMultiSelect={() => {
                      setIsMultiSelectMode(true)
                      toggleSelectMessage(message.id, true)
                    }}
                  />
                </div>
              )
              i++
            }
            return rendered
          })()}
        </div>
      </div>

      {/* Input or Multi-select Actions */}
      {isMultiSelectMode ? (
        <div className="grid grid-cols-4 gap-4 border-t bg-background/95 backdrop-blur p-4 pb-safe shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.1)] z-30">
           <div className="flex flex-col items-center gap-1 cursor-pointer hover:bg-muted/50 p-2 rounded transition-colors" onClick={() => handleForwardMessages('single')}>
             <Forward className="h-5 w-5" />
             <span className="text-[10px]">逐条转发</span>
           </div>
           <div className="flex flex-col items-center gap-1 cursor-pointer hover:bg-muted/50 p-2 rounded transition-colors" onClick={() => handleForwardMessages('combine')}>
             <Share2 className="h-5 w-5" />
             <span className="text-[10px]">合并转发</span>
           </div>
           <div className="flex flex-col items-center gap-1 cursor-pointer hover:bg-muted/50 p-2 rounded transition-colors" onClick={handleFavoriteMessages}>
             <Star className="h-5 w-5" />
             <span className="text-[10px]">收藏</span>
           </div>
           <div className="flex flex-col items-center gap-1 cursor-pointer text-destructive hover:bg-destructive/10 p-2 rounded transition-colors" onClick={handleDeleteMessages}>
             <Trash2 className="h-5 w-5" />
             <span className="text-[10px]">删除</span>
           </div>
        </div>
      ) : (
        <ChatInput 
          onSendMessage={handleSendMessage} 
          replyingTo={replyingTo}
          onCancelReply={() => setReplyingTo(null)}
          editingMessage={editingMessage}
          onUpdateMessage={(msgId, content) => void handleEditMessage(msgId, content)}
          onCancelEdit={() => setEditingMessage(null)}
          mentionUsers={mentionUsers}
          onTyping={(isTyping) => {
            if (!id) return
            setTyping(id, currentUser.id, currentUser.name, isTyping)
            if (token && currentUser.id && session?.targetId) {
              imSocket.connect(token, currentUser.id)
              imSocket.sendTyping({ to: session.targetId, conversationId: id, isTyping })
            }
          }}
          defaultMessage={session?.draft}
          onMessageChange={(draft) => id && setDraft(id, draft)}
          onScreenshot={handleScreenshot}
          isGroup={session.type === 'group'}
        />
      )}

      <Dialog open={readStatusOpen} onOpenChange={setReadStatusOpen}>
        <DialogContent className="sm:max-w-md">
            <DialogHeader>
                <DialogTitle>已读成员</DialogTitle>
            </DialogHeader>
            <div className="max-h-60 overflow-y-auto">
                {readStatusUsers.length > 0 ? (
                    readStatusUsers.map(user => (
                        <div key={user.id} className="flex items-center gap-3 p-2 hover:bg-muted/50 rounded-md">
                            <Avatar className="h-8 w-8">
                                <AvatarImage src={user.avatar} />
                                <AvatarFallback>{user.name[0]}</AvatarFallback>
                            </Avatar>
                            <span className="text-sm">{user.name}</span>
                        </div>
                    ))
                ) : (
                    <div className="p-4 text-center text-muted-foreground text-sm">暂无已读成员</div>
                )}
            </div>
        </DialogContent>
      </Dialog>

      <Dialog
        open={callDialogOpen}
        onOpenChange={(open) => {
          setCallDialogOpen(open)
          if (!open) {
            stopWebRtcCall()
          }
        }}
      >
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>通话演示</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <div className="rounded-md bg-blue-50 px-3 py-2 text-sm text-blue-800">
              当前已接入浏览器 WebRTC：语音用麦克风，视频用摄像头。需要两个登录用户、两个浏览器窗口互相呼叫。
            </div>
            <div className="space-y-2 rounded-md border p-3 text-sm">
              <div className="flex justify-between gap-3">
                <span className="text-muted-foreground">媒体连接</span>
                <span className="font-medium">{webRtcStatus}</span>
              </div>
              {callRecord?.mediaType === "video" ? (
                <div className="grid gap-2 sm:grid-cols-2">
                  <div className="space-y-1">
                    <p className="text-xs text-muted-foreground">本地画面</p>
                    <video ref={localVideoRef} autoPlay muted playsInline className="aspect-video w-full rounded bg-black object-cover" />
                  </div>
                  <div className="space-y-1">
                    <p className="text-xs text-muted-foreground">对方画面</p>
                    <video ref={remoteVideoRef} autoPlay playsInline className="aspect-video w-full rounded bg-black object-cover" />
                  </div>
                </div>
              ) : (
                <audio ref={remoteAudioRef} autoPlay playsInline />
              )}
            </div>
            <div className="space-y-2 rounded-md border p-3 text-sm">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="font-medium">本机真实音频测试</p>
                  <p className="text-xs text-muted-foreground">{audioDemoStatus}</p>
                </div>
                <Button
                  variant={audioDemoRunning ? "destructive" : "outline"}
                  size="sm"
                  onClick={audioDemoRunning ? stopLocalAudioDemo : startLocalAudioDemo}
                >
                  {audioDemoRunning ? "关闭声音" : "开启声音"}
                </Button>
              </div>
              <audio ref={audioElementRef} autoPlay playsInline />
            </div>
            {isOutgoingCall && callRecord?.status === "ringing" && (
              <div className="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">
                等待对方设备接听或拒绝。生产模式不允许发起方代替对方操作。
              </div>
            )}
            {callLoading && <p className="text-sm text-muted-foreground">正在创建通话...</p>}
            {callError && <p className="text-sm text-destructive">{callError}</p>}
            {callRecord && (
              <div className="space-y-2 rounded-md border p-3 text-sm">
                <div className="flex justify-between gap-3">
                  <span className="text-muted-foreground">当前状态</span>
                  <span className="font-medium">{callStatusLabel(callRecord.status)}</span>
                </div>
                <div className="flex justify-between gap-3">
                  <span className="text-muted-foreground">通话类型</span>
                  <span>{mediaTypeLabel(callRecord.mediaType)}</span>
                </div>
                <div className="flex justify-between gap-3">
                  <span className="text-muted-foreground">对方用户</span>
                  <span className="max-w-[210px] truncate">{callRecord.calleeId || session?.targetId || "-"}</span>
                </div>
                <div className="flex justify-between gap-3">
                  <span className="text-muted-foreground">后端记录</span>
                  <span className="font-medium text-emerald-600">已写入</span>
                </div>
                <div className="flex justify-between gap-3">
                  <span className="text-muted-foreground">TURN 会话</span>
                  <span className="max-w-[210px] truncate">{callRecord.turnSessionId}</span>
                </div>
              </div>
            )}
            {callConfig && (
              <div className="space-y-2 rounded-md bg-muted p-3 text-xs">
                <div className="flex justify-between gap-3">
                  <span className="text-muted-foreground">TURN 服务</span>
                  <span className="max-w-[240px] truncate">{callConfig.turnUrl}</span>
                </div>
                <div className="flex justify-between gap-3">
                  <span className="text-muted-foreground">PJSIP 网关</span>
                  <span className="max-w-[240px] truncate">{callConfig.pjsipSignalUrl}</span>
                </div>
              </div>
            )}
            <div className="space-y-2 rounded-md border p-3 text-xs">
              <div className="flex items-center justify-between gap-3">
                <span className="font-medium">最近通话</span>
                <Button variant="ghost" size="sm" className="h-7 px-2 text-xs" onClick={refreshCallHistory} disabled={callLoading || !token}>
                  刷新
                </Button>
              </div>
              {callHistory.length > 0 ? (
                <div className="space-y-1">
                  {callHistory.map((item) => (
                    <button
                      key={item.id}
                      type="button"
                      className="grid w-full grid-cols-[1fr_auto] gap-2 rounded px-2 py-1 text-left hover:bg-muted"
                      onClick={() => setCallRecord(item)}
                    >
                      <span className="truncate">{mediaTypeLabel(item.mediaType)} / {item.callerId} -&gt; {item.calleeId || "-"}</span>
                      <span className="text-muted-foreground">{callStatusLabel(item.status)}</span>
                    </button>
                  ))}
                </div>
              ) : (
                <p className="text-muted-foreground">{token ? "暂无通话记录" : "登录后可查看通话记录"}</p>
              )}
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setCallDialogOpen(false)}>关闭</Button>
              <Button variant="outline" onClick={handleAnswerCall} disabled={callLoading || isOutgoingCall || !callRecord || callRecord.status !== "ringing"}>
                接听
              </Button>
              <Button variant="outline" onClick={handleRejectCall} disabled={callLoading || isOutgoingCall || !callRecord || callRecord.status !== "ringing"}>
                拒绝
              </Button>
              <Button variant="destructive" onClick={handleHangupCall} disabled={callLoading || !callRecord || callRecord.status === "ended"}>
                挂断
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* Image Viewer */}
      <ImageViewer 
        isOpen={viewerOpen}
        onClose={() => setViewerOpen(false)}
        images={images}
        initialIndex={viewerImageIndex}
      />
      
      <ContactSelector
        open={forwardDialogOpen}
        onOpenChange={setForwardDialogOpen}
        onSelect={handleForwardConfirm}
        title="选择转发对象"
        includeGroups={true}
      />
    </div>
  )
}
