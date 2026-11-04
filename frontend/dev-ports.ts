// 2026/11/04：只读取两个十进制端口字段；联合启动优先使用后端传入的 Spring 实际生效值。
/** 从统一配置或开发进程环境读取端口，拒绝无效端口及前后端监听冲突。 */
export function developmentPorts(properties: string, environment: Record<string, string | undefined>) {
  /** 读取指定端口，缺失时报告配置键，不另设隐藏默认值。 */
  function port(key: string, variable: string): number {
    const escaped = key.replaceAll('.', '\\.')
    const configured = properties.match(new RegExp(`^\\s*${escaped}\\s*=\\s*(\\d+)\\s*$`, 'm'))?.[1]
    const value = environment[variable] ?? configured
    if (!value || !/^\d+$/.test(value) || Number(value) < 1 || Number(value) > 65535) {
      throw new Error(`开发端口配置无效：${key}，应为 1 至 65535 的整数。`)
    }
    return Number(value)
  }
  const backend = port('server.port', 'TEANEKO_BACKEND_PORT')
  const frontend = port('tea-neko.web.frontend.port', 'TEANEKO_FRONTEND_PORT')
  if (backend === frontend) throw new Error('前后端开发进程不能监听同一个端口。')
  return { backend, frontend, authority: `localhost:${frontend}` }
}
