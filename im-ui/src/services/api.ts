import type { Message, User } from "@/types"

export const API_BASE_URL = import.meta.env.VITE_IM_API_URL ?? ""

type ApiResponse<T> = {
  success: boolean
  data: T
  error?: string
}

export type LoginPayload = {
  phone: string
  password?: string
  code?: string
}

export type LoginResult = {
  user: User
  token: string
}

export type AuthProvider = {
  name: string
  enabled: boolean
  mode: string
  message: string
}

export type AuthProviders = {
  password: AuthProvider
  sms: AuthProvider
  sso: AuthProvider
  biometric: AuthProvider
}

export type SmsCodeResult = {
  phone: string
  providerMode: string
  expiresAt: string
  debugCode?: string
}

export type AdminOverview = {
  enterpriseUsers: number
  onlineUsers: number
  offlineUsers: number
  singleConversations: number
  groupConversations: number
  todayMessages: number
  pendingFriendRequests: number
  totalCalls: number
  activeCalls: number
  answeredCalls: number
  missedCalls: number
  todayActiveUsers: number
  totalStorageBytes: number
  totalFiles: number
  messageTrend?: Array<{ day: string; count: number }>
  storageBreakdown?: Array<{ kind: string; files: number; bytes: number }>
  riskTrend?: Array<{ day: string; count: number }>
  permissionMatrix?: Array<{ role: string; dashboard: boolean; organizationWrite: boolean; resourceWrite: boolean; auditRead: boolean; configWrite: boolean }>
}

export type AdminUser = {
  id: string
  enterpriseId?: string
  enterpriseName?: string
  phone?: string
  email?: string
  displayName: string
  avatarUrl?: string
  shortNo?: string
  gender?: string
  signature?: string
  positionName?: string
  status: string
  createdAt?: string
}

export type AdminDeviceSession = {
  id: string
  userId: string
  deviceId?: string
  deviceType: string
  deviceName?: string
  ipAddress?: string
  userAgent?: string
  online: boolean
  lastSeenAt?: string
}

export type AdminAuditLog = {
  id: string
  operatorId?: string
  action: string
  targetType?: string
  targetId?: string
  detail?: string
  createdAt?: string
}

export type AdminDepartment = {
  id: string
  enterpriseId: string
  enterpriseName: string
  parentId?: string
  name: string
  sortOrder: number
  memberCount: number
}

export type AdminEnterprise = {
  id: string
  name: string
  code: string
  userCount: number
  departmentCount: number
}

export type AdminRole = {
  id: string
  name: string
  description?: string
  adminCount: number
}

export type AdminAccount = {
  id: string
  userId: string
  displayName: string
  roleId: string
  roleName: string
  enabled: boolean
}

export type AdminGroup = {
  id: string
  enterpriseId?: string
  enterpriseName?: string
  ownerId: string
  name: string
  status: string
  memberCount: number
  createdAt?: string
}

export type AdminFile = {
  id: string
  uploaderId: string
  originalName: string
  contentType?: string
  sizeBytes: number
  status: string
  createdAt?: string
  downloadUrl?: string
  previewUrl?: string
}

export type AdminFileTransfer = {
  id: string
  fileId: string
  userId: string
  direction: "upload" | "download" | "preview"
  progress: number
  status: string
  sizeBytes?: number
  createdAt?: string
  updatedAt?: string
}

export type AdminDevicePolicy = {
  key: string
  value: string
  updatedAt?: string
}

export type AdminBatchImportResult = {
  created: number
  skipped: number
  total: number
  errors: string[]
}

export type AdminMessageAudit = {
  id: string
  conversationId: string
  senderId: string
  type: string
  content?: string
  status: string
  createdAt?: string
}

export type AdminCallAudit = {
  id: string
  conversationId?: string
  callerId: string
  calleeId?: string
  groupId?: string
  mediaType: "audio" | "video"
  status: "ringing" | "answered" | "rejected" | "ended"
  startedAt?: string
  answeredAt?: string
  endedAt?: string
  turnSessionId?: string
}

export type AdminRiskEvent = {
  id: string
  eventType: string
  userId?: string
  conversationId?: string
  messageId?: string
  detail?: string
  status: string
  createdAt?: string
}

export type AdminSensitiveWord = {
  id: string
  word: string
  action: string
  enabled: boolean
  createdAt?: string
}

export type AdminKeyValue = {
  key: string
  value: string
  updatedAt?: string
}

export type AdminWorkspaceApp = {
  id: string
  name: string
  icon?: string
  url?: string
  visibleDepartmentId?: string
  sortOrder: number
  enabled: boolean
}

export type AdminAppVersion = {
  id: string
  platform: string
  versionName: string
  versionCode: number
  downloadUrl?: string
  forceUpdate: boolean
  notes?: string
  createdAt?: string
}

export type AdminFriendRequest = {
  id: string
  requesterId: string
  receiverId: string
  message?: string
  status: string
  createdAt?: string
  handledAt?: string
}

export type AdminSession = {
  userId: string
  displayName: string
  role: string
  token: string
  expiresAt: string
  permissions?: string[]
}

export type CallConfig = {
  turnUrl: string
  turnUsername: string
  turnPassword: string
  pjsipSignalUrl: string
}

export type CallReadiness = {
  ready: boolean
  supportedMediaTypes: Array<"audio" | "video">
  checks: Array<{
    name: string
    ready: boolean
    message: string
  }>
  blockers: string[]
}

export type CallConnectivity = {
  reachable: boolean
  checks: Array<{
    name: string
    reachable: boolean
    host?: string
    port: number
    message: string
    durationMs: number
  }>
}

export type CallRecord = {
  id: string
  conversationId: string
  callerId: string
  calleeId?: string
  groupId?: string
  mediaType: "audio" | "video"
  status: "ringing" | "answered" | "rejected" | "ended"
  startedAt?: string
  answeredAt?: string
  endedAt?: string
  turnSessionId: string
}

export type ServerMessage = {
  id: string
  conversationId: string
  senderId: string
  type: Message["type"]
  content?: string
  fileId?: string
  status: string
  clientSeq?: string
  createdAt?: string
  editedAt?: string
  recalledAt?: string
  expireAfterRead?: boolean
  destroyedAt?: string
}

export type FileRecord = {
  id: string
  uploaderId: string
  originalName: string
  contentType?: string
  sizeBytes: number
  status: string
  createdAt?: string
  downloadUrl?: string
  previewUrl?: string
}

export type FileTransferRecord = {
  id: string
  fileId: string
  userId: string
  direction: "upload" | "download" | "preview"
  progress: number
  status: string
  updatedAt?: string
}

export type FileUploadSession = {
  id: string
  uploaderId: string
  originalName: string
  contentType?: string
  totalSize: number
  totalChunks: number
  uploadedChunks: number
  uploadedBytes: number
  status: string
}

export type FilePreviewAdapter = {
  fileId: string
  providerMode: string
  enabled: boolean
  message: string
  previewUrl?: string
}

export type NotificationSettingsRecord = {
  userId?: string
  newMessage: boolean
  calls: boolean
  detail: boolean
  sound: boolean
  vibration: boolean
  screenshotNotice: boolean
  recallNotice: boolean
  mentionAlert: boolean
  dndEnabled: boolean
  dndStart: string
  dndEnd: string
  updatedAt?: string
}

export type PushProvider = {
  name: string
  enabled: boolean
  mode: string
}

export type PushProviders = {
  apns: PushProvider
  fcm: PushProvider
  vendor: PushProvider
}

export type PushDeviceToken = {
  id: string
  userId: string
  platform: string
  provider: string
  token: string
  enabled: boolean
  updatedAt?: string
}

export type MessageReadMember = {
  userId: string
  userName?: string
  readAt?: string
}

export type MessageReadStatus = {
  messageId: string
  conversationId: string
  read: MessageReadMember[]
  unread: MessageReadMember[]
}

export type DirectoryEnterprise = {
  id: string
  name: string
  code: string
}

export type DirectoryDepartment = {
  id: string
  enterpriseId: string
  parentId?: string
  name: string
  sortOrder: number
  memberCount: number
}

export type DirectoryUser = {
  id: string
  enterpriseId?: string
  phone?: string
  email?: string
  name: string
  avatarUrl?: string
  signature?: string
  status: User["status"]
}

export type FriendRequestRecord = {
  id: string
  requesterId: string
  requesterName: string
  receiverId: string
  receiverName: string
  message?: string
  status: "pending" | "accepted" | "rejected"
  createdAt?: string
  handledAt?: string
}

export type GroupRecord = {
  id: string
  enterpriseId?: string
  ownerId: string
  name: string
  avatarUrl?: string
  notice?: string
  status: string
  memberCount: number
  createdAt?: string
  joinApprovalRequired?: boolean
}

function mapServerMessage(item: ServerMessage): Message {
  const recalled = item.status === "recalled"
  const destroyed = item.status === "destroyed"
  return {
    id: item.clientSeq || item.id,
    serverId: item.id,
    sessionId: item.conversationId,
    senderId: item.senderId,
    content: item.content || "",
    type: recalled ? "system" : item.type,
    timestamp: item.createdAt ? Date.parse(item.createdAt) : Date.now(),
    status: item.status === "read" ? "read" : "sent",
    fileId: item.fileId,
    isRecall: recalled,
    isEdited: Boolean(item.editedAt),
    isReadAfterBurn: Boolean(item.expireAfterRead),
    ...(destroyed ? { type: "system" as const, content: "阅后即焚消息已销毁" } : {}),
  }
}

async function post<T>(path: string, body: unknown, token?: string): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  })

  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }

  const payload = (await res.json()) as ApiResponse<T>
  if (!payload.success) {
    throw new Error(payload.error || "Request failed")
  }
  return payload.data
}

async function get<T>(path: string, token?: string): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  })

  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }

  const payload = (await res.json()) as ApiResponse<T>
  if (!payload.success) {
    throw new Error(payload.error || "Request failed")
  }
  return payload.data
}

async function patch<T>(path: string, body: unknown, token?: string): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  })

  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }

  const payload = (await res.json()) as ApiResponse<T>
  if (!payload.success) {
    throw new Error(payload.error || "Request failed")
  }
  return payload.data
}

async function del<T>(path: string, body: unknown, token?: string): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: "DELETE",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  })

  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }

  const payload = (await res.json()) as ApiResponse<T>
  if (!payload.success) {
    throw new Error(payload.error || "Request failed")
  }
  return payload.data
}

export async function loginApi(payload: LoginPayload): Promise<LoginResult> {
  const data = await post<{
    userId: string
    displayName: string
    token: string
    expiresAt: string
  }>("/api/auth/login", payload)

  return {
    token: data.token,
    user: {
      id: data.userId,
      name: data.displayName,
      avatar: "https://github.com/shadcn.png",
      status: "online",
      phone: payload.phone,
    },
  }
}

export async function fetchAuthProvidersApi(): Promise<AuthProviders> {
  return get<AuthProviders>("/api/auth/providers")
}

export async function sendSmsCodeApi(phone: string): Promise<SmsCodeResult> {
  return post<SmsCodeResult>("/api/auth/sms/send", { phone })
}

export type SsoLoginPayload = {
  enterpriseCode: string
  phone?: string
  password?: string
}

export async function ssoLoginApi(payload: SsoLoginPayload): Promise<LoginResult> {
  const data = await post<{
    userId: string
    displayName: string
    token: string
    expiresAt: string
  }>("/api/auth/sso/login", payload)

  return {
    token: data.token,
    user: {
      id: data.userId,
      name: data.displayName,
      avatar: "https://github.com/shadcn.png",
      status: "online",
      phone: payload.phone || data.userId,
    },
  }
}

export async function fetchConversationMessagesApi(conversationId: string, limit = 50): Promise<Message[]> {
  const data = await get<ServerMessage[]>(`/api/conversations/${encodeURIComponent(conversationId)}/messages?limit=${limit}`)
  return data.map(mapServerMessage)
}

export async function createFileApi(payload: {
  uploaderId: string
  objectKey?: string
  originalName: string
  contentType?: string
  sizeBytes: number
}, token?: string): Promise<FileRecord> {
  return post<FileRecord>("/api/files", payload, token)
}

export async function uploadFileApi(uploaderId: string, file: File | Blob, token?: string, filename?: string): Promise<FileRecord> {
  const form = new FormData()
  form.set("uploaderId", uploaderId)
  form.set("file", file, filename || (file instanceof File ? file.name : "upload.bin"))
  const res = await fetch(`${API_BASE_URL}/api/files/upload`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    body: form,
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const payload = (await res.json()) as ApiResponse<FileRecord>
  if (!payload.success) throw new Error(payload.error || "Request failed")
  return payload.data
}

export async function createChunkUploadSessionApi(payload: {
  uploaderId: string
  originalName: string
  contentType?: string
  totalSize: number
  totalChunks: number
}, token?: string): Promise<FileUploadSession> {
  return post<FileUploadSession>("/api/files/chunk-upload/sessions", payload, token)
}

export async function uploadFileChunkApi(sessionId: string, chunkIndex: number, chunk: Blob, token?: string): Promise<FileUploadSession> {
  const form = new FormData()
  form.set("chunkIndex", String(chunkIndex))
  form.set("file", chunk, `${chunkIndex}.part`)
  const res = await fetch(`${API_BASE_URL}/api/files/chunk-upload/sessions/${encodeURIComponent(sessionId)}/chunks`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    body: form,
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const payload = (await res.json()) as ApiResponse<FileUploadSession>
  if (!payload.success) throw new Error(payload.error || "Request failed")
  return payload.data
}

export async function completeChunkUploadApi(sessionId: string, token?: string): Promise<FileRecord> {
  return post<FileRecord>(`/api/files/chunk-upload/sessions/${encodeURIComponent(sessionId)}/complete`, {}, token)
}

export async function fetchOfficePreviewApi(fileId: string, token?: string): Promise<FilePreviewAdapter> {
  return get<FilePreviewAdapter>(`/api/files/${encodeURIComponent(fileId)}/office-preview`, token)
}

export async function fetchFileOcrApi(fileId: string, token?: string): Promise<FilePreviewAdapter> {
  return get<FilePreviewAdapter>(`/api/files/${encodeURIComponent(fileId)}/ocr`, token)
}

export async function fetchFileBlobApi(fileId: string, mode: "download" | "preview", token?: string): Promise<Blob> {
  const res = await fetch(`${API_BASE_URL}/api/files/${encodeURIComponent(fileId)}/${mode}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.blob()
}

export async function createFileTransferApi(fileId: string, payload: {
  direction: "upload" | "download"
  progress: number
  status: string
}, token?: string): Promise<FileTransferRecord> {
  return post<FileTransferRecord>(`/api/files/${encodeURIComponent(fileId)}/transfers`, payload, token)
}

export async function fetchFilesApi(userId: string, token?: string, limit = 100): Promise<FileRecord[]> {
  return get<FileRecord[]>(`/api/files?userId=${encodeURIComponent(userId)}&limit=${limit}`, token)
}

export async function fetchDirectoryEnterprisesApi(token?: string): Promise<DirectoryEnterprise[]> {
  return get<DirectoryEnterprise[]>("/api/directory/enterprises", token)
}

export async function fetchDirectoryDepartmentsApi(token?: string, enterpriseId?: string): Promise<DirectoryDepartment[]> {
  const params = new URLSearchParams()
  if (enterpriseId) params.set("enterpriseId", enterpriseId)
  const suffix = params.toString() ? `?${params.toString()}` : ""
  return get<DirectoryDepartment[]>(`/api/directory/departments${suffix}`, token)
}

export async function fetchDirectoryUsersApi(token?: string, filters?: {
  enterpriseId?: string
  departmentId?: string
  query?: string
  limit?: number
}): Promise<DirectoryUser[]> {
  const params = new URLSearchParams({ limit: String(filters?.limit ?? 100) })
  if (filters?.enterpriseId) params.set("enterpriseId", filters.enterpriseId)
  if (filters?.departmentId) params.set("departmentId", filters.departmentId)
  if (filters?.query) params.set("query", filters.query)
  return get<DirectoryUser[]>(`/api/directory/users?${params.toString()}`, token)
}

export async function fetchFriendsApi(userId: string, token?: string): Promise<DirectoryUser[]> {
  return get<DirectoryUser[]>(`/api/friends?userId=${encodeURIComponent(userId)}`, token)
}

export async function fetchFriendRequestsApi(userId: string, token?: string, options?: {
  status?: string
  box?: "all" | "incoming" | "outgoing"
}): Promise<FriendRequestRecord[]> {
  const params = new URLSearchParams({ userId, box: options?.box ?? "all" })
  if (options?.status) params.set("status", options.status)
  return get<FriendRequestRecord[]>(`/api/friend-requests?${params.toString()}`, token)
}

export async function createFriendRequestApi(payload: {
  receiverId?: string
  receiverAccount?: string
  message?: string
}, token?: string): Promise<FriendRequestRecord> {
  return post<FriendRequestRecord>("/api/friend-requests", payload, token)
}

export async function handleFriendRequestApi(requestId: string, accept: boolean, token?: string): Promise<FriendRequestRecord> {
  return post<FriendRequestRecord>(`/api/friend-requests/${encodeURIComponent(requestId)}/handle`, { accept }, token)
}

export async function fetchGroupsApi(userId: string, token?: string): Promise<GroupRecord[]> {
  return get<GroupRecord[]>(`/api/groups?userId=${encodeURIComponent(userId)}`, token)
}

export async function createGroupApi(payload: {
  name: string
  memberIds: string[]
  avatarUrl?: string
  notice?: string
}, token?: string): Promise<GroupRecord> {
  return post<GroupRecord>("/api/groups", payload, token)
}

export async function sendConversationMessageApi(conversationId: string, payload: {
  senderId: string
  type: Message["type"]
  content: string
  fileId?: string
  clientSeq?: string
  conversationType?: "single" | "group"
  targetId?: string
  expireAfterRead?: boolean
}, token?: string): Promise<Message> {
  const item = await post<ServerMessage>(`/api/conversations/${encodeURIComponent(conversationId)}/messages`, payload, token)
  return mapServerMessage(item)
}

export async function editMessageApi(messageId: string, content: string, token?: string): Promise<Message> {
  const item = await patch<ServerMessage>(`/api/messages/${encodeURIComponent(messageId)}/edit`, { content }, token)
  return mapServerMessage(item)
}

export async function recallMessageApi(messageId: string, reason = "user_recall", token?: string): Promise<Message> {
  const item = await post<ServerMessage>(`/api/messages/${encodeURIComponent(messageId)}/recall`, { reason }, token)
  return mapServerMessage(item)
}

export async function readMessageApi(messageId: string, token?: string) {
  return post<{ messageId: string; userId: string; readAt: string }>(`/api/messages/${encodeURIComponent(messageId)}/read`, {}, token)
}

export async function fetchMessageReceiptsApi(messageId: string, token?: string) {
  return get<Array<{ messageId: string; userId: string; readAt?: string }>>(`/api/messages/${encodeURIComponent(messageId)}/receipts`, token)
}

export async function fetchMessageReadStatusApi(messageId: string, token?: string): Promise<MessageReadStatus> {
  return get<MessageReadStatus>(`/api/messages/${encodeURIComponent(messageId)}/read-status`, token)
}

export async function reactMessageApi(messageId: string, reaction = "like", token?: string) {
  return post<{ messageId: string; userId: string; reaction: string }>(`/api/messages/${encodeURIComponent(messageId)}/reactions`, { reaction }, token)
}

export async function favoriteMessageApi(messageId: string, token?: string) {
  return post<{ userId: string; messageId: string; favorited: boolean }>(`/api/messages/${encodeURIComponent(messageId)}/favorite`, {}, token)
}

export async function screenshotConversationApi(conversationId: string, token?: string) {
  return post<{ id: string; conversationId: string; userId: string; createdAt: string }>(`/api/conversations/${encodeURIComponent(conversationId)}/screenshot`, {}, token)
}

export async function fetchNotificationSettingsApi(token?: string): Promise<NotificationSettingsRecord> {
  return get<NotificationSettingsRecord>("/api/notification-settings", token)
}

export async function updateNotificationSettingsApi(
  updates: Partial<NotificationSettingsRecord>,
  token?: string
): Promise<NotificationSettingsRecord> {
  return patch<NotificationSettingsRecord>("/api/notification-settings", updates, token)
}

export async function fetchPushProvidersApi(token?: string): Promise<PushProviders> {
  return get<PushProviders>("/api/push/providers", token)
}

export async function fetchPushDeviceTokensApi(token?: string): Promise<PushDeviceToken[]> {
  return get<PushDeviceToken[]>("/api/push/device-tokens", token)
}

export async function registerPushDeviceTokenApi(payload: { platform: string; provider: string; token: string }, token?: string): Promise<PushDeviceToken> {
  return post<PushDeviceToken>("/api/push/device-tokens", payload, token)
}

export async function disablePushDeviceTokenApi(tokenId: string, token?: string): Promise<PushDeviceToken> {
  return del<PushDeviceToken>(`/api/push/device-tokens/${encodeURIComponent(tokenId)}`, {}, token)
}

export async function adminLoginApi(phone: string, password: string): Promise<AdminSession> {
  return post<AdminSession>("/api/admin/auth/login", { phone, password })
}

export async function fetchAdminOverviewApi(token?: string): Promise<AdminOverview> {
  return get<AdminOverview>("/api/admin/overview", token)
}

export async function fetchAdminUsersApi(
  limit = 20,
  token?: string,
  filters?: { status?: string; enterpriseId?: string }
): Promise<AdminUser[]> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (filters?.status) params.set("status", filters.status)
  if (filters?.enterpriseId) params.set("enterpriseId", filters.enterpriseId)
  return get<AdminUser[]>(`/api/admin/users?${params.toString()}`, token)
}

export async function createAdminUserApi(
  payload: { enterpriseId?: string; phone: string; email?: string; displayName: string },
  token?: string
): Promise<AdminUser> {
  return post<AdminUser>("/api/admin/users", payload, token)
}

export async function updateAdminUserProfileApi(
  userId: string,
  payload: { displayName?: string; email?: string; avatarUrl?: string; shortNo?: string; gender?: string; signature?: string; positionName?: string },
  token?: string
): Promise<AdminUser> {
  return patch<AdminUser>(`/api/admin/users/${encodeURIComponent(userId)}/profile`, payload, token)
}

export async function fetchAdminUserDeviceSessionsApi(userId: string, token?: string): Promise<AdminDeviceSession[]> {
  return get<AdminDeviceSession[]>(`/api/admin/users/${encodeURIComponent(userId)}/device-sessions`, token)
}

export async function batchImportAdminUsersApi(
  users: Array<{ enterpriseId?: string; phone: string; email?: string; displayName: string }>,
  token?: string
): Promise<AdminBatchImportResult> {
  return post<AdminBatchImportResult>("/api/admin/users/batch-import", { users }, token)
}

export async function forceOfflineAdminUserApi(userId: string, token?: string) {
  return post<{ userId: string; tcpClosed: boolean; websocketClosed: boolean }>(
    `/api/admin/users/${encodeURIComponent(userId)}/force-offline`,
    { confirmText: "CONFIRM" },
    token
  )
}

export async function fetchAdminDevicePoliciesApi(token?: string): Promise<AdminDevicePolicy[]> {
  return get<AdminDevicePolicy[]>("/api/admin/users/device-policies", token)
}

export async function updateAdminDevicePolicyApi(key: string, value: string, token?: string): Promise<AdminDevicePolicy> {
  return patch<AdminDevicePolicy>(`/api/admin/users/device-policies/${encodeURIComponent(key)}`, { value }, token)
}

export async function fetchAdminAuditLogsApi(
  limit = 20,
  token?: string,
  filters?: { operatorId?: string; action?: string; targetType?: string }
): Promise<AdminAuditLog[]> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (filters?.operatorId) params.set("operatorId", filters.operatorId)
  if (filters?.action) params.set("action", filters.action)
  if (filters?.targetType) params.set("targetType", filters.targetType)
  return get<AdminAuditLog[]>(`/api/admin/audit-logs?${params.toString()}`, token)
}

export async function fetchAdminDepartmentsApi(limit = 20, token?: string, enterpriseId?: string): Promise<AdminDepartment[]> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (enterpriseId) params.set("enterpriseId", enterpriseId)
  return get<AdminDepartment[]>(`/api/admin/departments?${params.toString()}`, token)
}

export async function fetchAdminEnterprisesApi(limit = 20, token?: string): Promise<AdminEnterprise[]> {
  return get<AdminEnterprise[]>(`/api/admin/enterprises?limit=${limit}`, token)
}

export async function createAdminEnterpriseApi(payload: { name: string; code: string }, token?: string): Promise<AdminEnterprise> {
  return post<AdminEnterprise>("/api/admin/enterprises", payload, token)
}

export async function createAdminDepartmentApi(
  payload: { enterpriseId: string; parentId?: string; name: string; sortOrder?: number },
  token?: string
): Promise<AdminDepartment> {
  return post<AdminDepartment>("/api/admin/departments", { ...payload, sortOrder: payload.sortOrder ?? 0 }, token)
}

export async function updateAdminDepartmentApi(
  departmentId: string,
  payload: { parentId?: string; name: string; sortOrder?: number },
  token?: string
): Promise<AdminDepartment> {
  return patch<AdminDepartment>(
    `/api/admin/departments/${encodeURIComponent(departmentId)}`,
    { ...payload, sortOrder: payload.sortOrder ?? 0 },
    token
  )
}

export async function deleteAdminDepartmentApi(departmentId: string, token?: string) {
  return del<{ departmentId: string; status: string }>(
    `/api/admin/departments/${encodeURIComponent(departmentId)}`,
    { confirmText: "CONFIRM" },
    token
  )
}

export async function fetchAdminRolesApi(token?: string): Promise<AdminRole[]> {
  return get<AdminRole[]>("/api/admin/roles", token)
}

export async function fetchAdminAccountsApi(limit = 20, token?: string): Promise<AdminAccount[]> {
  return get<AdminAccount[]>(`/api/admin/admin-users?limit=${limit}`, token)
}

export async function createAdminAccountApi(payload: { userId: string; roleId: string }, token?: string): Promise<AdminAccount> {
  return post<AdminAccount>("/api/admin/admin-users", payload, token)
}

export async function updateAdminAccountEnabledApi(adminUserId: string, enabled: boolean, token?: string): Promise<AdminAccount> {
  return patch<AdminAccount>(
    `/api/admin/admin-users/${encodeURIComponent(adminUserId)}/enabled`,
    { enabled, confirmText: "CONFIRM" },
    token
  )
}

export async function fetchAdminGroupsApi(
  limit = 20,
  token?: string,
  filters?: { enterpriseId?: string; status?: string }
): Promise<AdminGroup[]> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (filters?.enterpriseId) params.set("enterpriseId", filters.enterpriseId)
  if (filters?.status) params.set("status", filters.status)
  return get<AdminGroup[]>(`/api/admin/groups?${params.toString()}`, token)
}

export async function fetchAdminFilesApi(
  limit = 20,
  token?: string,
  filters?: { uploaderId?: string; status?: string }
): Promise<AdminFile[]> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (filters?.uploaderId) params.set("uploaderId", filters.uploaderId)
  if (filters?.status) params.set("status", filters.status)
  return get<AdminFile[]>(`/api/admin/files?${params.toString()}`, token)
}

export async function fetchAdminFileTransfersApi(fileId: string, token?: string): Promise<AdminFileTransfer[]> {
  return get<AdminFileTransfer[]>(`/api/admin/files/${encodeURIComponent(fileId)}/transfers`, token)
}

export async function updateAdminFileStatusApi(fileId: string, status: "available" | "disabled" | "deleted", token?: string): Promise<AdminFile> {
  return patch<AdminFile>(`/api/admin/files/${encodeURIComponent(fileId)}/status`, { status }, token)
}

export async function deleteAdminFileApi(fileId: string, token?: string): Promise<AdminFile> {
  return del<AdminFile>(`/api/admin/files/${encodeURIComponent(fileId)}`, { confirmText: "CONFIRM" }, token)
}

export async function fetchAdminMessagesApi(
  limit = 20,
  token?: string,
  filters?: { conversationId?: string; senderId?: string }
): Promise<AdminMessageAudit[]> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (filters?.conversationId) params.set("conversationId", filters.conversationId)
  if (filters?.senderId) params.set("senderId", filters.senderId)
  return get<AdminMessageAudit[]>(`/api/admin/messages?${params.toString()}`, token)
}

export async function fetchAdminCallsApi(
  limit = 20,
  token?: string,
  filters?: { userId?: string; status?: string; mediaType?: string }
): Promise<AdminCallAudit[]> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (filters?.userId) params.set("userId", filters.userId)
  if (filters?.status) params.set("status", filters.status)
  if (filters?.mediaType) params.set("mediaType", filters.mediaType)
  return get<AdminCallAudit[]>(`/api/admin/calls?${params.toString()}`, token)
}

export async function fetchAdminCallConnectivityApi(token?: string): Promise<CallConnectivity> {
  return get<CallConnectivity>("/api/admin/call-connectivity", token)
}

export async function fetchAdminRiskEventsApi(limit = 20, token?: string, filters?: { status?: string; eventType?: string }): Promise<AdminRiskEvent[]> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (filters?.status) params.set("status", filters.status)
  if (filters?.eventType) params.set("eventType", filters.eventType)
  return get<AdminRiskEvent[]>(`/api/admin/risk-events?${params.toString()}`, token)
}

export async function fetchAdminSensitiveWordsApi(token?: string): Promise<AdminSensitiveWord[]> {
  return get<AdminSensitiveWord[]>("/api/admin/sensitive-words", token)
}

export async function createAdminSensitiveWordApi(payload: { word: string; action?: string; enabled?: boolean }, token?: string): Promise<AdminSensitiveWord> {
  return post<AdminSensitiveWord>("/api/admin/sensitive-words", { action: "block", enabled: true, ...payload }, token)
}

export async function fetchAdminResourcePoliciesApi(token?: string): Promise<AdminKeyValue[]> {
  return get<AdminKeyValue[]>("/api/admin/resource-policies", token)
}

export async function updateAdminResourcePolicyApi(key: string, value: string, token?: string): Promise<AdminKeyValue> {
  return patch<AdminKeyValue>(`/api/admin/resource-policies/${encodeURIComponent(key)}`, { value }, token)
}

export async function fetchAdminWorkspaceAppsApi(token?: string): Promise<AdminWorkspaceApp[]> {
  return get<AdminWorkspaceApp[]>("/api/admin/workspace-apps", token)
}

export async function fetchWorkspaceAppsApi(token?: string): Promise<AdminWorkspaceApp[]> {
  return get<AdminWorkspaceApp[]>("/api/workspace-apps", token)
}

export async function createAdminWorkspaceAppApi(payload: { name: string; icon?: string; url?: string; visibleDepartmentId?: string; sortOrder?: number; enabled?: boolean }, token?: string): Promise<AdminWorkspaceApp> {
  return post<AdminWorkspaceApp>("/api/admin/workspace-apps", { sortOrder: 0, enabled: true, ...payload }, token)
}

export async function updateAdminWorkspaceAppApi(id: string, payload: { name: string; icon?: string; url?: string; visibleDepartmentId?: string; sortOrder?: number; enabled?: boolean }, token?: string): Promise<AdminWorkspaceApp> {
  return patch<AdminWorkspaceApp>(`/api/admin/workspace-apps/${encodeURIComponent(id)}`, { sortOrder: 0, enabled: true, ...payload }, token)
}

export async function reorderAdminWorkspaceAppsApi(items: Array<{ id: string; sortOrder: number }>, token?: string): Promise<AdminWorkspaceApp[]> {
  return patch<AdminWorkspaceApp[]>("/api/admin/workspace-apps/reorder", { items }, token)
}

export async function deleteAdminWorkspaceAppApi(id: string, token?: string): Promise<AdminWorkspaceApp> {
  return del<AdminWorkspaceApp>(`/api/admin/workspace-apps/${encodeURIComponent(id)}`, { confirmText: "CONFIRM" }, token)
}

export async function fetchAdminAppVersionsApi(token?: string): Promise<AdminAppVersion[]> {
  return get<AdminAppVersion[]>("/api/admin/app-versions", token)
}

export async function createAdminAppVersionApi(payload: { platform: string; versionName: string; versionCode: number; downloadUrl?: string; forceUpdate?: boolean; notes?: string }, token?: string): Promise<AdminAppVersion> {
  return post<AdminAppVersion>("/api/admin/app-versions", { forceUpdate: false, ...payload }, token)
}

export async function fetchAdminSystemConfigsApi(token?: string): Promise<AdminKeyValue[]> {
  return get<AdminKeyValue[]>("/api/admin/system-configs", token)
}

export async function updateAdminSystemConfigApi(key: string, value: string, token?: string): Promise<AdminKeyValue> {
  return patch<AdminKeyValue>(`/api/admin/system-configs/${encodeURIComponent(key)}`, { value }, token)
}

export async function fetchAdminFriendRequestsApi(limit = 20, token?: string, status?: string): Promise<AdminFriendRequest[]> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (status) params.set("status", status)
  return get<AdminFriendRequest[]>(`/api/admin/friend-requests?${params.toString()}`, token)
}

export async function handleAdminFriendRequestApi(id: string, accept: boolean, token?: string): Promise<AdminFriendRequest> {
  return post<AdminFriendRequest>(`/api/admin/friend-requests/${encodeURIComponent(id)}/handle`, { accept }, token)
}

export async function publishAdminGroupNoticeApi(groupId: string, notice: string, token?: string) {
  return patch<{ groupId: string; status: string }>(`/api/admin/groups/${encodeURIComponent(groupId)}/notice`, { notice }, token)
}

export async function transferAdminGroupOwnerApi(groupId: string, ownerId: string, token?: string) {
  return patch<{ groupId: string; status: string }>(`/api/admin/groups/${encodeURIComponent(groupId)}/owner`, { ownerId }, token)
}

export async function updateAdminUserStatusApi(userId: string, status: "active" | "disabled", token?: string) {
  return patch<{ userId: string; status: string }>(
    `/api/admin/users/${encodeURIComponent(userId)}/status`,
    { status, confirmText: "CONFIRM" },
    token
  )
}

export async function fetchCallConfigApi(token?: string): Promise<CallConfig> {
  return get<CallConfig>("/api/calls/config", token)
}

export async function fetchCallReadinessApi(): Promise<CallReadiness> {
  return get<CallReadiness>("/api/calls/readiness")
}

export async function fetchCallHistoryApi(userId: string, limit = 20, token?: string): Promise<CallRecord[]> {
  const params = new URLSearchParams({ userId, limit: String(limit) })
  return get<CallRecord[]>(`/api/calls?${params.toString()}`, token)
}

export async function initiateCallApi(payload: {
  callerId: string
  calleeId: string
  conversationId: string
  mediaType: "audio" | "video"
}, token?: string): Promise<CallRecord> {
  return post<CallRecord>("/api/calls", payload, token)
}

export async function answerCallApi(callId: string, actorId: string, token?: string): Promise<CallRecord> {
  return post<CallRecord>(`/api/calls/${encodeURIComponent(callId)}/answer`, { actorId }, token)
}

export async function rejectCallApi(callId: string, actorId: string, token?: string): Promise<CallRecord> {
  return post<CallRecord>(`/api/calls/${encodeURIComponent(callId)}/reject`, { actorId }, token)
}

export async function hangupCallApi(callId: string, actorId: string, token?: string): Promise<CallRecord> {
  return post<CallRecord>(`/api/calls/${encodeURIComponent(callId)}/hangup`, { actorId }, token)
}

// === 会话管理 API ===

export type ConversationRecord = {
  id: string
  type: "single" | "group"
  targetId: string
  muted: boolean
  pinned: boolean
  unreadCount: number
  lastContent?: string
  lastSender?: string
  lastTime?: string
  lastType?: string
  updatedAt?: string
  screenshotNotice?: boolean
  recallNotice?: boolean
  readAfterBurn?: boolean
  strongReminder?: boolean
  displayMemberNicknames?: boolean
  savedToContacts?: boolean
}

export async function fetchConversationsApi(token?: string): Promise<ConversationRecord[]> {
  return get<ConversationRecord[]>("/api/conversations", token)
}

export async function updateConversationSettingsApi(
  conversationId: string,
  settings: {
    muted?: boolean
    pinned?: boolean
    screenshotNotice?: boolean
    recallNotice?: boolean
    readAfterBurn?: boolean
    strongReminder?: boolean
    displayMemberNicknames?: boolean
    savedToContacts?: boolean
  },
  token?: string
): Promise<{
  conversationId: string
  muted: boolean
  pinned: boolean
  screenshotNotice: boolean
  recallNotice: boolean
  readAfterBurn: boolean
  strongReminder: boolean
  displayMemberNicknames: boolean
  savedToContacts: boolean
}> {
  return patch(`/api/conversations/${encodeURIComponent(conversationId)}/settings`, settings, token)
}

// === 消息转发 API ===

export async function forwardMessageApi(
  messageId: string,
  targetConversationId: string,
  token?: string
): Promise<Message> {
  const item = await post<ServerMessage>(
    `/api/messages/${encodeURIComponent(messageId)}/forward`,
    { targetConversationId },
    token
  )
  return mapServerMessage(item)
}

export async function forwardMessagesApi(
  messageIds: string[],
  targetConversationIds: string[],
  mode: "single" | "combine" = "single",
  token?: string
): Promise<Message[]> {
  const items = await post<ServerMessage[]>(
    "/api/messages/forward",
    { messageIds, targetConversationIds, mode },
    token
  )
  return items.map(mapServerMessage)
}

// === 全局搜索 API ===

export type SearchResult = {
  contacts?: Array<{ id: string; name: string; phone?: string }>
  groups?: Array<{ id: string; name: string; notice?: string; jumpUrl?: string }>
  messages?: Array<{ id: string; conversationId: string; senderId: string; type?: string; content: string; createdAt?: string; jumpUrl?: string }>
  files?: Array<{ id: string; name: string; contentType?: string; sizeBytes: number; jumpUrl?: string }>
}

export type SearchRecommendations = {
  contacts?: SearchResult["contacts"]
  groups?: SearchResult["groups"]
  messages?: SearchResult["messages"]
}

export async function searchApi(q: string, type = "all", token?: string): Promise<SearchResult> {
  return get<SearchResult>(`/api/search?q=${encodeURIComponent(q)}&type=${encodeURIComponent(type)}`, token)
}

export async function searchRecommendationsApi(token?: string): Promise<SearchRecommendations> {
  return get<SearchRecommendations>("/api/search/recommendations", token)
}

// === 群成员管理 API ===

export type GroupMemberRecord = {
  userId: string
  userName?: string
  role: string
  alias?: string
  muted: boolean
  joinedAt?: string
}

export async function fetchGroupMembersApi(groupId: string, token?: string): Promise<GroupMemberRecord[]> {
  return get<GroupMemberRecord[]>(`/api/groups/${encodeURIComponent(groupId)}/members`, token)
}

export async function addGroupMembersApi(groupId: string, userIds: string[], token?: string): Promise<string> {
  await post<GroupRecord>(`/api/groups/${encodeURIComponent(groupId)}/members/batch`, { userIds }, token)
  return "ok"
}

export async function batchAddGroupMembersApi(groupId: string, userIds: string[], token?: string): Promise<GroupRecord> {
  return post<GroupRecord>(`/api/groups/${encodeURIComponent(groupId)}/members/batch`, { userIds }, token)
}

export async function exportGroupMembersApi(groupId: string, token?: string): Promise<GroupMemberRecord[]> {
  return get<GroupMemberRecord[]>(`/api/groups/${encodeURIComponent(groupId)}/members/export`, token)
}

export async function removeGroupMemberApi(groupId: string, userId: string, token?: string): Promise<string> {
  return del<string>(`/api/groups/${encodeURIComponent(groupId)}/members/${encodeURIComponent(userId)}`, {}, token)
}

export async function muteGroupMemberApi(groupId: string, userId: string, muted: boolean, token?: string): Promise<string> {
  return patch<string>(`/api/groups/${encodeURIComponent(groupId)}/members/${encodeURIComponent(userId)}/mute`, { muted }, token)
}

// === 群操作 API ===

export async function updateGroupApi(groupId: string, updates: { name?: string; notice?: string; avatarUrl?: string }, token?: string): Promise<string> {
  return patch<string>(`/api/groups/${encodeURIComponent(groupId)}`, updates, token)
}

export async function transferGroupOwnerApi(groupId: string, newOwnerId: string, token?: string): Promise<string> {
  return post<string>(`/api/groups/${encodeURIComponent(groupId)}/transfer-owner`, { newOwnerId }, token)
}

export type GroupInvite = {
  groupId: string
  token: string
  qrPayload: string
  expiresAt: string
}

export type GroupJoinRequest = {
  id: string
  groupId: string
  requesterId: string
  requesterName: string
  message?: string
  status: string
  createdAt?: string
  handledAt?: string
}

export async function createGroupInviteApi(groupId: string, token?: string): Promise<GroupInvite> {
  return post<GroupInvite>(`/api/groups/${encodeURIComponent(groupId)}/invites`, {}, token)
}

export async function requestJoinGroupApi(groupId: string, inviteToken: string, message = "扫码申请加入群聊", token?: string): Promise<GroupJoinRequest> {
  return post<GroupJoinRequest>(
    `/api/groups/${encodeURIComponent(groupId)}/join-requests`,
    { inviteToken, message },
    token,
  )
}

export async function setGroupApprovalApi(groupId: string, enabled: boolean, token?: string): Promise<GroupRecord> {
  return patch<GroupRecord>(`/api/groups/${encodeURIComponent(groupId)}/approval`, { enabled }, token)
}

export async function fetchGroupJoinRequestsApi(groupId: string, token?: string): Promise<GroupJoinRequest[]> {
  return get<GroupJoinRequest[]>(`/api/groups/${encodeURIComponent(groupId)}/join-requests`, token)
}

export async function handleGroupJoinRequestApi(groupId: string, requestId: string, accept: boolean, token?: string): Promise<GroupJoinRequest> {
  return post<GroupJoinRequest>(`/api/groups/${encodeURIComponent(groupId)}/join-requests/${encodeURIComponent(requestId)}/handle`, { accept }, token)
}

export async function dissolveGroupApi(groupId: string, token?: string): Promise<GroupRecord> {
  return del<GroupRecord>(`/api/groups/${encodeURIComponent(groupId)}`, {}, token)
}

// === 好友关系 API ===

export type LinkPreviewDto = {
  url: string
  title?: string | null
  description?: string | null
  imageUrl?: string | null
  siteName?: string | null
  error?: string | null
}

export async function fetchLinkPreviewApi(url: string, token?: string): Promise<LinkPreviewDto> {
  return get<LinkPreviewDto>(`/api/link-preview?url=${encodeURIComponent(url)}`, token)
}

export async function deleteFriendApi(userId: string, friendId: string, token?: string): Promise<string> {
  return del<string>(`/api/friends/${encodeURIComponent(friendId)}?userId=${encodeURIComponent(userId)}`, {}, token)
}

export type BlacklistEntry = {
  userId: string
  blockedUserId: string
  blockedName: string
  createdAt: string
}

export async function fetchBlacklistApi(userId: string, token?: string): Promise<BlacklistEntry[]> {
  return get<BlacklistEntry[]>(`/api/friends/blacklist?userId=${encodeURIComponent(userId)}`, token)
}

export async function blockUserApi(userId: string, blockedUserId: string, token?: string): Promise<unknown> {
  return post<unknown>("/api/friends/blacklist", { userId, blockedUserId }, token)
}

export async function unblockUserApi(userId: string, blockedUserId: string, token?: string): Promise<unknown> {
  return del<unknown>(`/api/friends/blacklist/${encodeURIComponent(blockedUserId)}?userId=${encodeURIComponent(userId)}`, {}, token)
}

// === 在线状态 API ===

export async function fetchOnlineStatusApi(userIds: string[], token?: string): Promise<Record<string, boolean>> {
  return get<Record<string, boolean>>(`/api/users/online-status?userIds=${encodeURIComponent(userIds.join(","))}`, token)
}

// === WebAuthn 生物识别 API ===

export type WebAuthnCreationOptions = {
  challengeId: string
  challenge: string
  rpId: string
  rpName: string
  userId: string
  userName: string
  userDisplayName: string
}

export type WebAuthnRequestOptions = {
  challengeId: string
  challenge: string
  rpId: string
  allowCredentialIds: string[]
}

export async function webAuthnBeginRegisterApi(phone: string): Promise<WebAuthnCreationOptions> {
  return post<WebAuthnCreationOptions>("/api/auth/webauthn/register/begin", { phone })
}

export async function webAuthnCompleteRegisterApi(challengeId: string, credentialId: string, publicKeyJson?: string): Promise<LoginResult> {
  const data = await post<{
    userId: string; displayName: string; token: string; expiresAt: string
  }>("/api/auth/webauthn/register/complete", { challengeId, credentialId, publicKeyJson })
  return {
    token: data.token,
    user: { id: data.userId, name: data.displayName, avatar: "https://github.com/shadcn.png", status: "online", phone: data.userId },
  }
}

export async function webAuthnBeginAuthApi(phone: string): Promise<WebAuthnRequestOptions> {
  return post<WebAuthnRequestOptions>("/api/auth/webauthn/auth/begin", { phone })
}

export async function webAuthnCompleteAuthApi(challengeId: string, credentialId: string): Promise<LoginResult> {
  const data = await post<{
    userId: string; displayName: string; token: string; expiresAt: string
  }>("/api/auth/webauthn/auth/complete", { challengeId, credentialId })
  return {
    token: data.token,
    user: { id: data.userId, name: data.displayName, avatar: "https://github.com/shadcn.png", status: "online", phone: data.userId },
  }
}

// === 实时监控与管理 API ===

export type AdminThroughputPoint = {
  hour: string
  count: number
}

export type AdminHealthStatus = {
  database: "ok" | "error"
  storageFreeBytes: number
  storageTotalBytes: number
  uptimeSeconds: number
}

export type AdminDepartmentTreeNode = {
  id: string
  enterpriseId: string
  parentId?: string
  name: string
  sortOrder: number
  memberCount: number
  children?: AdminDepartmentTreeNode[]
}

export type AdminDepartmentMember = {
  userId: string
  displayName: string
  role: string
  joinedAt?: string
}

export type AdminDepartmentPermissions = {
  canCreateGroup: boolean
  canInviteExternal: boolean
  canShareFiles: boolean
  canVideoCall: boolean
  storageQuotaMb: number
}

export type AdminAppTemplate = {
  id: string
  name: string
  icon?: string
  description?: string
  category?: string
  installed?: boolean
}

export type AdminAppVersionStats = {
  id: string
  versionName: string
  downloadCount: number
  uniqueUsers: number
  adoptionRate: number
}

export type AdminSystemConfig = {
  themePrimaryColor?: string
  launchLogoUrl?: string
  launchSlogan?: string
  defaultLanguage?: string
  termsUrl?: string
  privacyUrl?: string
}

export async function fetchAdminThroughputApi(token?: string): Promise<AdminThroughputPoint[]> {
  return get<AdminThroughputPoint[]>("/api/admin/metrics/throughput", token)
}

export async function fetchAdminHealthApi(token?: string): Promise<AdminHealthStatus> {
  return get<AdminHealthStatus>("/api/admin/health", token)
}

export async function fetchAdminDepartmentsTreeApi(token?: string, enterpriseId?: string): Promise<AdminDepartmentTreeNode[]> {
  const params = enterpriseId ? `?enterpriseId=${encodeURIComponent(enterpriseId)}` : ""
  return get<AdminDepartmentTreeNode[]>(`/api/admin/departments/tree${params}`, token)
}

export async function fetchAdminDepartmentApi(departmentId: string, token?: string): Promise<AdminDepartmentTreeNode> {
  return get<AdminDepartmentTreeNode>(`/api/admin/departments/${encodeURIComponent(departmentId)}`, token)
}

export async function fetchAdminDepartmentMembersApi(departmentId: string, token?: string): Promise<AdminDepartmentMember[]> {
  return get<AdminDepartmentMember[]>(`/api/admin/departments/${encodeURIComponent(departmentId)}/members`, token)
}

export async function addAdminDepartmentMembersApi(departmentId: string, userIds: string[], token?: string): Promise<AdminDepartmentMember[]> {
  return post<AdminDepartmentMember[]>(`/api/admin/departments/${encodeURIComponent(departmentId)}/members`, { userIds }, token)
}

export async function removeAdminDepartmentMemberApi(departmentId: string, userId: string, token?: string): Promise<void> {
  await del(`/api/admin/departments/${encodeURIComponent(departmentId)}/members/${encodeURIComponent(userId)}`, {}, token)
}

export async function updateAdminDepartmentPermissionsApi(departmentId: string, permissions: Partial<AdminDepartmentPermissions>, token?: string): Promise<AdminDepartmentPermissions> {
  return patch<AdminDepartmentPermissions>(`/api/admin/departments/${encodeURIComponent(departmentId)}/permissions`, permissions, token)
}

export async function broadcastAdminDepartmentApi(departmentId: string, message: string, token?: string): Promise<{ departmentId: string; message: string; sentAt: string }> {
  return post<{ departmentId: string; message: string; sentAt: string }>(`/api/admin/departments/${encodeURIComponent(departmentId)}/broadcast`, { message }, token)
}

export async function fetchAdminAppTemplatesApi(token?: string): Promise<AdminAppTemplate[]> {
  return get<AdminAppTemplate[]>("/api/admin/app-templates", token)
}

export async function installAdminAppTemplateApi(templateId: string, token?: string): Promise<AdminAppTemplate> {
  return post<AdminAppTemplate>(`/api/admin/app-templates/${encodeURIComponent(templateId)}/install`, {}, token)
}

export async function fetchAdminAppVersionStatsApi(versionId: string, token?: string): Promise<AdminAppVersionStats> {
  return get<AdminAppVersionStats>(`/api/admin/app-versions/${encodeURIComponent(versionId)}/stats`, token)
}

export async function deprecateAdminAppVersionApi(versionId: string, token?: string): Promise<AdminAppVersion> {
  return patch<AdminAppVersion>(`/api/admin/app-versions/${encodeURIComponent(versionId)}/deprecate`, {}, token)
}

export async function rollbackAdminAppVersionApi(versionId: string, token?: string): Promise<AdminAppVersion> {
  return patch<AdminAppVersion>(`/api/admin/app-versions/${encodeURIComponent(versionId)}/rollback`, {}, token)
}

export async function saveAdminSystemConfigApi(config: AdminSystemConfig, token?: string): Promise<AdminSystemConfig> {
  return patch<AdminSystemConfig>("/api/admin/system-config", config, token)
}
