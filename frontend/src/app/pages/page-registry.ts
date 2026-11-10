// 2026/11/07：按功能目录引用共享能力与页面契约。
import type {PageDefinition} from './page-contracts'
import {computed} from 'vue'
import {pluginEnabled, pluginPages} from '../../shared/plugins/plugin-console'
import type {PluginConsoleModule} from '../../shared/plugins/plugin-page'

// 2026/11/06：导航、标题、挂载和可见条件共用此构建期声明；新增页面不修改宿主分支。
const definitions = [
  {key: 'overview', title: '运行概览', group: '日常', icon: 'overview', retention: 'active', load: () => import('../../features/runtime/OverviewPage.vue')},
  {key: 'platforms', title: '客户端', group: '日常', icon: 'platforms', load: () => import('../../features/platforms/PlatformsPage.vue'), props: (_, active) => ({active})},
  {key: 'chat', title: '网页聊天', group: '日常', icon: 'chat', load: () => import('../../features/chat/WebChatView.vue'), props: (context, active) => ({clients: context.platforms.value, active})},
  // 2026/11/10：插件管理位于管理组首位，与独立插件配置导航分开。
  {key: 'plugins', title: '插件管理', group: '管理', icon: 'components', load: () => import('../../features/plugins/PluginsView.vue'), props: (_, active) => ({active})},
  {key: 'settings', title: '聊天配置', group: '管理', icon: 'settings', load: () => import('../../features/settings/chat/SettingsPage.vue'), visible: context => context.platforms.value.some(item => item.settingsAvailable)},
  {key: 'agent', title: 'Agent 工作区', group: '管理', icon: 'agent', load: () => import('../../features/agent/AgentPage.vue'), props: (_, active) => ({active})},
  {key: 'models', title: '模型配置', group: '管理', icon: 'models', load: () => import('../../features/settings/files/FileSettingsView.vue'), props: () => ({modelMode: true})},
  {key: 'files', title: '本地配置', group: '管理', icon: 'files', load: () => import('../../features/settings/files/FileSettingsView.vue')},
  {key: 'runtime', title: '任务与日志', group: '管理', icon: 'runtime', load: () => import('../../features/runtime/RuntimeView.vue'), props: (_, active) => ({active})},
  {key: 'console', title: '指令台', group: '管理', icon: 'console', load: () => import('../../features/commands/CommandPage.vue'), props: (_, active) => ({active})},
  {key: 'commands', title: '指令一览', group: 'API 文档', icon: 'commands', load: () => import('../../features/commands/CommandsView.vue')},
  {key: 'components', title: '组件与系统', group: 'API 文档', icon: 'components', load: () => import('../../features/agent/AgentApiDocsView.vue'), props: () => ({mode: 'components'})},
  {key: 'tools', title: '工具目录', group: 'API 文档', icon: 'tools', load: () => import('../../features/agent/AgentApiDocsView.vue'), props: () => ({mode: 'tools'})},
  {key: 'session', title: '当前会话', group: 'footer', icon: 'session', load: () => import('../../features/auth/SessionPage.vue')},
] as const satisfies readonly PageDefinition[]

// 2026/11/07：插件页面使用受校验的全局键，导航无需为每个插件增加联合类型。
export type ConsolePageKey = string
export type ConsolePage = Omit<PageDefinition, 'key'> & {key: ConsolePageKey}
const modules = new Map<string, PluginConsoleModule>()
for (const entry of Object.values(import.meta.glob<{default: PluginConsoleModule}>('../../plugins/**/entry.ts', {eager: true}))) {
  if (modules.has(entry.default.key)) throw new Error('插件页面模块标识重复：' + entry.default.key)
  modules.set(entry.default.key, entry.default)
}
export const missingPluginModules = computed(() => pluginPages.value.filter(page => !modules.has(page.page.moduleKey)))
export const consolePages = computed<readonly ConsolePage[]>(() => [...definitions, ...pluginPages.value.flatMap(item => {
  const module = modules.get(item.page.moduleKey)
  if (!module || !pluginEnabled(item.pluginId)) return []
  return [{key: `plugin:${item.key}`, title: item.page.title, group: '插件配置', icon: 'components' as const,
    pluginId: item.pluginId, load: module.load, props: (_: unknown, active: boolean) => ({active})}]
})])
// 2026/11/06：重复标识属于构建声明错误，不按顺序静默覆盖。
if (new Set(definitions.map(page => page.key)).size !== definitions.length) {
  throw new Error('控制台页面标识重复，请检查页面声明。')
}
export function consolePage(key: ConsolePageKey): ConsolePage {
  const page = consolePages.value.find(item => item.key === key)
  if (!page) throw new Error('未登记的控制台页面。')
  return page
}
