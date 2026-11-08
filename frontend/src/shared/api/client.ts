export interface Session {
  name: string
  authenticatedAt: string
  expiresAt: string
}

export interface Platform {
  pluginId?: string | null
  clientId: string
  name: string
  state: 'CONNECTED' | 'DISCONNECTED' | 'DISABLED' | 'AVAILABLE'
  connections: number
  explanation: string
  observedAt: string
  settingsAvailable?: boolean
}

export interface Overview {
  name: string
  version: string
  observedAt: string
  available: string[]
}

interface Csrf {
  headerName: string
  token: string
}

// 2026/11/04：CSRF 只在当前页面内存保存，根令牌的可选保存由登录界面单独管理。
let csrf: Csrf | undefined

export class ApiError extends Error {
  // 2026/11/07：保留服务端安全错误码，区分本机资源限制和真正的会话失效。
  constructor(public status: number, message: string, public code?: string) {
    super(message)
  }
}

/** 2026/11/06：JSON 入口不把坏正文或 null 视为成功；空响应须显式声明。 */
export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  return await exchange(path, init, false) as T
}
export async function requestEmpty(path: string, init: RequestInit = {}): Promise<void> {
  await exchange(path, init, true)
}
async function exchange(path: string, init: RequestInit, empty: boolean): Promise<unknown> {
  // 2026/11/03：刷新页面后仍须先取得本会话防伪值，不能假设仅登录时需要。
  if (!csrf && init.method && init.method !== 'GET') await refreshCsrf()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (csrf && init.method && init.method !== 'GET') headers.set(csrf.headerName, csrf.token)
  const response = await fetch(`/api/v1/${path}`, { ...init, headers, credentials: 'same-origin' })
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(response.status, typeof body?.message === 'string' ? body.message : '请求未完成，请稍后重试。',
      typeof body?.code === 'string' ? body.code : undefined)
  }
  if (empty) return undefined
  if (body === null) throw new ApiError(response.status, '结果未确认：服务未返回有效 JSON，请保留原操作编号。')
  return body
}

export async function refreshCsrf(): Promise<void> {
  const next = await request<Csrf>('auth/csrf')
  if (!next || typeof next.headerName !== 'string' || !next.headerName || typeof next.token !== 'string' || !next.token) {
    throw new ApiError(200, '安全校验值响应无效，请重新登录。')
  }
  csrf = next
}

export async function login(token: string): Promise<void> {
  await refreshCsrf()
  await requestEmpty('auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ username: 'admin', token }),
  })
  // 登录会轮换会话及防伪值，写操作不能继续使用登录前的值。
  await refreshCsrf()
}

export async function logout(): Promise<void> {
  await requestEmpty('auth/logout', { method: 'POST' })
  csrf = undefined
}
