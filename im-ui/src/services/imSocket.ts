type TcpMessage = {
  version: string
  type: string
  requestId: string
  from?: string
  to?: string
  conversationId?: string
  timestamp: number
  payload?: Record<string, unknown>
}

type MessageHandler = (message: TcpMessage) => void
type WebRtcSignalKind = "offer" | "answer" | "ice" | "hangup"

const WS_URL =
  import.meta.env.VITE_IM_WS_URL ??
  `${window.location.protocol === "https:" ? "wss" : "ws"}://${window.location.host}/ws/im`

class ImSocketClient {
  private socket: WebSocket | null = null
  private token: string | null = null
  private userId: string | null = null
  private handlers = new Set<MessageHandler>()
  private pending: TcpMessage[] = []

  connect(token: string, userId: string) {
    this.token = token
    this.userId = userId

    if (
      this.socket &&
      (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)
    ) {
      return
    }

    this.socket = new WebSocket(WS_URL)
    this.socket.onopen = () => {
      this.sendRaw({
        version: "1",
        type: "AUTH",
        requestId: `auth-${Date.now()}`,
        from: userId,
        timestamp: Date.now(),
        payload: { token },
      })
      const queued = [...this.pending]
      this.pending = []
      queued.forEach((message) => this.sendRaw(message))
    }
    this.socket.onmessage = (event) => {
      const message = JSON.parse(event.data) as TcpMessage
      this.handlers.forEach((handler) => handler(message))
    }
    this.socket.onclose = () => {
      this.socket = null
    }
  }

  onMessage(handler: MessageHandler) {
    this.handlers.add(handler)
    return () => {
      this.handlers.delete(handler)
    }
  }

  sendText(params: { to: string; conversationId: string; content: string; requestId?: string }) {
    const requestId = params.requestId ?? `msg-${Date.now()}-${Math.random().toString(36).slice(2)}`
    const message: TcpMessage = {
      version: "1",
      type: "TEXT",
      requestId,
      from: this.userId ?? undefined,
      to: params.to,
      conversationId: params.conversationId,
      timestamp: Date.now(),
      payload: { content: params.content },
    }

    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      this.pending.push(message)
      if (this.token && this.userId) {
        this.connect(this.token, this.userId)
      }
      return requestId
    }

    this.sendRaw(message)
    return requestId
  }

  sendTyping(params: { to: string; conversationId: string; isTyping: boolean }) {
    const message: TcpMessage = {
      version: "1",
      type: "TYPING",
      requestId: `typing-${Date.now()}`,
      from: this.userId ?? undefined,
      to: params.to,
      conversationId: params.conversationId,
      timestamp: Date.now(),
      payload: { isTyping: params.isTyping },
    }
    this.sendRaw(message)
  }

  sendWebRtcSignal(params: {
    to: string
    conversationId: string
    callId: string
    kind: WebRtcSignalKind
    data?: unknown
    requestId?: string
  }) {
    const requestId = params.requestId ?? `rtc-${params.kind}-${Date.now()}-${Math.random().toString(36).slice(2)}`
    const message: TcpMessage = {
      version: "1",
      type: "WEBRTC_SIGNAL",
      requestId,
      from: this.userId ?? undefined,
      to: params.to,
      conversationId: params.conversationId,
      timestamp: Date.now(),
      payload: {
        callId: params.callId,
        kind: params.kind,
        data: params.data,
      },
    }

    this.sendRaw(message)
    return requestId
  }

  private sendRaw(message: TcpMessage) {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      this.pending.push(message)
      return
    }
    this.socket.send(JSON.stringify(message))
  }
}

export const imSocket = new ImSocketClient()
export type { TcpMessage }
