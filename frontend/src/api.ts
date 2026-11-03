export interface Session {
  name: string
  authenticatedAt: string
  expiresAt: string
}

export interface Platform {
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
  constructor(public status: number, message: string) {
    super(message)
  }
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  // 2026/11/03：刷新页面后仍须先取得本会话防伪值，不能假设仅登录时需要。
  if (!csrf && init.method && init.method !== 'GET') await refreshCsrf()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (csrf && init.method && init.method !== 'GET') headers.set(csrf.headerName, csrf.token)
  const response = await fetch(`/api/v1/${path}`, { ...init, headers, credentials: 'same-origin' })
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(response.status, typeof body?.message === 'string' ? body.message : '请求未完成，请稍后重试。')
  }
  return body as T
}

export async function refreshCsrf(): Promise<void> {
  csrf = await request<Csrf>('auth/csrf')
}

export async function login(token: string): Promise<void> {
  await refreshCsrf()
  await request('auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ username: 'admin', token }),
  })
  // 登录会轮换会话及防伪值，写操作不能继续使用登录前的值。
  await refreshCsrf()
}

export async function logout(): Promise<void> {
  await request('auth/logout', { method: 'POST' })
  csrf = undefined
}
