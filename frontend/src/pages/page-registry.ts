import type {PageDefinition} from './page-contracts'

// 2026/11/06：导航、标题、挂载和可见条件共用此构建期声明；新增页面不修改宿主分支。
const definitions = [
  {key: 'overview', title: '运行概览', group: '日常', icon: 'overview', retention: 'active', load: () => import('./OverviewPage.vue')},
  {key: 'platforms', title: '客户端', group: '日常', icon: 'platforms', load: () => import('./PlatformsPage.vue'), props: (_, active) => ({active})},
  {key: 'chat', title: '网页聊天', group: '日常', icon: 'chat', load: () => import('../WebChatView.vue'), props: (context, active) => ({clients: context.platforms.value, active})},
  {key: 'settings', title: '聊天配置', group: '管理', icon: 'settings', load: () => import('./SettingsPage.vue'), visible: context => context.platforms.value.some(item => item.settingsAvailable)},
  {key: 'agent', title: 'Agent 工作区', group: '管理', icon: 'agent', load: () => import('./AgentPage.vue'), props: (_, active) => ({active})},
  {key: 'models', title: '模型配置', group: '管理', icon: 'models', load: () => import('../FileSettingsView.vue'), props: () => ({modelMode: true})},
  {key: 'files', title: '本地配置', group: '管理', icon: 'files', load: () => import('../FileSettingsView.vue')},
  {key: 'runtime', title: '任务与日志', group: '管理', icon: 'runtime', load: () => import('../RuntimeView.vue'), props: (_, active) => ({active})},
  {key: 'console', title: '指令台', group: '管理', icon: 'console', load: () => import('./CommandPage.vue'), props: (_, active) => ({active})},
  {key: 'commands', title: '指令一览', group: 'API 文档', icon: 'commands', load: () => import('../CommandsView.vue')},
  {key: 'components', title: '组件与系统', group: 'API 文档', icon: 'components', load: () => import('../AgentApiDocsView.vue'), props: () => ({mode: 'components'})},
  {key: 'tools', title: '工具目录', group: 'API 文档', icon: 'tools', load: () => import('../AgentApiDocsView.vue'), props: () => ({mode: 'tools'})},
  {key: 'session', title: '当前会话', group: 'footer', icon: 'session', load: () => import('./SessionPage.vue')},
] as const satisfies readonly PageDefinition[]

export type ConsolePageKey = typeof definitions[number]['key']
export type ConsolePage = Omit<PageDefinition, 'key'> & {key: ConsolePageKey}
export const consolePages: readonly ConsolePage[] = definitions
// 2026/11/06：重复标识属于构建声明错误，不按顺序静默覆盖。
if (new Set(consolePages.map(page => page.key)).size !== consolePages.length) {
  throw new Error('控制台页面标识重复，请检查页面声明。')
}
export function consolePage(key: ConsolePageKey): ConsolePage {
  const page = consolePages.find(item => item.key === key)
  if (!page) throw new Error('未登记的控制台页面。')
  return page
}
