import type { Message, User } from "@/types"

const API_BASE_URL = import.meta.env.VITE_IM_API_URL ?? ""

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
}

export type AdminUser = {
  id: string
  enterpriseId?: string
  enterpriseName?: string
  phone?: string
  email?: string
  displayName: string
  status: string
  createdAt?: string
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

export type AdminSession = {
  userId: string
  displayName: string
  role: string
  token: string
  expiresAt: string
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

export async function fetchConversationMessagesApi(conversationId: string, limit = 50): Promise<Message[]> {
  const data = await get<Array<{
    id: string
    conversationId: string
    senderId: string
    type: Message["type"]
    content: string
    status: Message["status"]
    clientSeq?: string
    createdAt?: string
  }>>(`/api/conversations/${encodeURIComponent(conversationId)}/messages?limit=${limit}`)

  return data.map((item) => ({
    id: item.clientSeq || item.id,
    serverId: item.id,
    sessionId: item.conversationId,
    senderId: item.senderId,
    content: item.content,
    type: item.type,
    timestamp: item.createdAt ? Date.parse(item.createdAt) : Date.now(),
    status: item.status,
  }))
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

export async function demoAnswerCallApi(callId: string, token?: string): Promise<CallRecord> {
  return post<CallRecord>(`/api/calls/${encodeURIComponent(callId)}/demo-answer`, {}, token)
}

export async function demoRejectCallApi(callId: string, token?: string): Promise<CallRecord> {
  return post<CallRecord>(`/api/calls/${encodeURIComponent(callId)}/demo-reject`, {}, token)
}

export async function hangupCallApi(callId: string, actorId: string, token?: string): Promise<CallRecord> {
  return post<CallRecord>(`/api/calls/${encodeURIComponent(callId)}/hangup`, { actorId }, token)
}
