// 2026/11/04：链接只用于填表，认证仍经原登录表单、CSRF 与服务端会话。
const storageKey = 'teaneko.web.admin-token'

/** 取出链接令牌并立即从当前历史记录移除，推荐使用不传给服务器的片段。 */
export function takeLoginToken(): string {
  const url = new URL(window.location.href)
  const fragment = new URLSearchParams(url.hash.slice(1))
  const token = fragment.get('token') ?? url.searchParams.get('token') ?? ''
  if (fragment.has('token') || url.searchParams.has('token')) {
    fragment.delete('token')
    url.searchParams.delete('token')
    url.hash = fragment.toString()
    window.history.replaceState(window.history.state, '', url)
  }
  return token
}

/** 浏览器禁用本地存储时仍允许手动登录。 */
export function savedLoginToken(): string {
  try {
    return window.localStorage.getItem(storageKey) ?? ''
  } catch {
    return ''
  }
}

/** 成功登录后按明确选择保存或删除令牌，返回浏览器是否允许操作。 */
export function rememberLoginToken(token: string, remember: boolean): boolean {
  try {
    if (remember) window.localStorage.setItem(storageKey, token)
    else window.localStorage.removeItem(storageKey)
    return true
  } catch {
    return false
  }
}
