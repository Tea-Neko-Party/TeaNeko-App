import type {Component, InjectionKey, Ref} from 'vue'
import {inject} from 'vue'
import type {Overview, Platform, Session} from '../api'
import type {AgentTarget} from '../AgentWorkspaceView.vue'
import type ConsoleIcon from '../ConsoleIcon.vue'
import type {ConsolePageKey} from './page-registry'

// 2026/11/06：页面只接收受控导航意图，不接收任意组件名、脚本地址或服务端权限声明。
export type PageIntent = {kind: 'target'; target: AgentTarget} | {kind: 'client'; client: string}
export interface PageEnvironment {
  platforms: Readonly<Ref<Platform[]>>
  overview: Readonly<Ref<Overview | undefined>>
  session: Readonly<Ref<Session | undefined>>
  busy: Readonly<Ref<boolean>>
  refresh: () => Promise<void>
  expired: () => void
  tokensApplied: () => void
  signOut: () => Promise<void>
}
export interface PageContext extends PageEnvironment {
  navigate: (key: ConsolePageKey, intent?: PageIntent) => Promise<boolean>
}
export const pageContextKey: InjectionKey<PageContext> = Symbol('控制台页面环境')
export function usePageContext(): PageContext {
  const context = inject(pageContextKey)
  if (!context) throw new Error('控制台页面环境尚未装配。')
  return context
}

export interface PageDefinition {
  key: string
  title: string
  group: string
  icon: InstanceType<typeof ConsoleIcon>['$props']['name']
  load: () => Promise<{default: Component}>
  // 2026/11/06：可见条件仅控制导航；真实访问权限仍由服务端复核。
  visible?: (context: PageEnvironment) => boolean
  props?: (context: PageEnvironment, active: boolean) => Record<string, unknown>
  retention?: 'session' | 'active'
}
export interface PageLeave {
  reason: 'navigate' | 'signout'
  retained: boolean
}
export interface PageHandle {
  open?: (intent: PageIntent) => Promise<boolean>
  canLeave?: (leave: PageLeave) => Promise<boolean> | boolean
  refresh?: () => Promise<void>
  headingHidden?: boolean
}
export interface PageOutletHandle {
  open: (intent: PageIntent) => Promise<boolean>
  canLeave: (leave: PageLeave) => Promise<boolean>
  refresh: () => Promise<void>
  hasRefresh: Readonly<Ref<boolean>>
  headingHidden: Readonly<Ref<boolean>>
}
