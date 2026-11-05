// 2026/11/05：读取 HTTP、前端与 WebSocket 十进制端口；联合启动优先使用 Spring 实际生效值。
/** 从统一配置或开发进程环境读取端口，拒绝无效端口及前后端监听冲突。 */
export function developmentPorts(properties: string, environment: Record<string, string | undefined>) {
  /** 读取指定端口，缺失时报告配置键，不另设隐藏默认值。 */
  function port(key: string, variable: string, minimum = 1): number {
    const escaped = key.replaceAll('.', '\\.')
    const configured = properties.match(new RegExp(`^\\s*${escaped}\\s*=\\s*(\\d+)\\s*$`, 'm'))?.[1]
    const value = environment[variable] ?? configured
    if (!value || !/^\d+$/.test(value) || Number(value) < minimum || Number(value) > 65535) {
      throw new Error(`开发端口配置无效：${key}，应为 ${minimum} 至 65535 的整数。`)
    }
    return Number(value)
  }
  const backend = port('server.port', 'TEANEKO_BACKEND_PORT')
  const frontend = port('tea-neko.web.frontend.port', 'TEANEKO_FRONTEND_PORT')
  // 2026/11/05：机器 WebSocket 可独立代理，零表示沿用实际后端端口。
  const configuredSocket = port('tea-neko.websocket.port', 'TEANEKO_WEBSOCKET_PORT', 0)
  const websocket = configuredSocket || backend
  if (backend === frontend) throw new Error('前后端开发进程不能监听同一个端口。')
  if (websocket === frontend) throw new Error('WebSocket 后端监听端口不能与前端开发端口相同。')
  return { backend, frontend, websocket, authority: `localhost:${frontend}` }
}
