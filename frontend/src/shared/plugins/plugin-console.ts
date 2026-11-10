// 2026/11/07：按功能目录引用共享能力与页面契约。
import {computed, onScopeDispose, ref, watch} from 'vue'
import {ApiError, request} from '../api/client'

// 2026/11/07：所有页面共享服务端插件资格；目录刷新不触发任何插件发现或启停。
export interface PluginItem {
  id: string; name: string; description: string; category: string; version: string; mandatory: boolean
  desiredEnabled: boolean; enabled: boolean; state: string; reason: string; restartRequired: boolean
  dependencies: string[]; requiredClients: string[]
  contributions: PluginContribution[]
}
export interface PluginContribution {pluginId: string; kind: string; id: string; name: string; description: string}
export interface PluginCatalog {revision: string; items: PluginItem[]}
export interface PluginPage {
  key: string; pluginId: string
  page: {id: string; title: string; description: string; moduleKey: string; order: number; managementResources: string[]}
}
export interface PluginPreview {pluginId: string; enabled: boolean; revision: string; affected: string[]; blockers: string[]; restartRequired: boolean}
export interface PluginReceipt {operationId: string; status: string; revision: string; note: string; restartRequired: boolean}
/** 2026/11/07：坏响应或其他编号不能释放原请求，只有已知合同才更新操作状态。 */
export function pluginReceipt(value: unknown, operationId: string): PluginReceipt {
  const result = value as Partial<PluginReceipt> | null
  if (!result || typeof result !== 'object' || result.operationId !== operationId
    || typeof result.status !== 'string' || !['APPLYING', 'APPLIED', 'FAILED', 'BLOCKED', 'DISABLED', 'ENABLED', 'STARTING', 'STOPPING', 'PENDING_RESTART', 'NOT_APPLIED', 'SAVED_NOT_APPLIED', 'INDETERMINATE'].includes(result.status)
    || typeof result.revision !== 'string' || typeof result.note !== 'string' || typeof result.restartRequired !== 'boolean') {
    throw new Error('插件结果未确认：回执无效或与原编号不匹配，请查询原操作结果。')
  }
  return {operationId, status: result.status, revision: result.revision, note: result.note, restartRequired: result.restartRequired}
}
export const pluginCatalog = ref<PluginCatalog>({revision: '', items: []})
export const pluginPages = ref<PluginPage[]>([])
export const pluginCatalogError = ref('')
export const pluginCatalogBusy = ref(false)
// 2026/11/07：明确拒绝后等待用户操作或重新聚焦复核，避免定时重复请求未授权资源。
export const pluginCatalogPollingPaused = ref(false)
export const pluginAvailability = computed(() => pluginCatalog.value.items.map(item => `${item.id}:${item.enabled}`).sort().join('|'))
let epoch = 0

/** 2026/11/07：插件状态只来自服务端；没有归属的宿主项目保留原可见性。 */
export function pluginEnabled(pluginId?: string | null): boolean {
  return !pluginId || pluginCatalog.value.items.some(item => item.id === pluginId && item.enabled)
}

/** 2026/11/10：展示名称来自同一可信目录，ID 继续作为唯一操作键及未知插件的定位信息。 */
export function pluginName(pluginId?: string | null): string {
  return pluginId ? pluginCatalog.value.items.find(item => item.id === pluginId)?.name || pluginId : '系统内置'
}

/** 2026/11/07：已失效页面请求只结束当前操作，不把旧结果或错误写回新页面。 */
export class PluginRequestSuperseded extends Error {
  constructor() { super('插件资格或当前目标已变化，请重新读取。') }
}

/** 2026/11/07：读取与回执绑定当前页面、目标和插件代次，关闭后重新开启也不接受旧响应。 */
export function usePluginRequests(identity: () => string = () => '') {
  let generation = 0
  let disposed = false
  watch(pluginAvailability, () => { generation++ }, {flush: 'sync'})
  onScopeDispose(() => { disposed = true })
  const capture = () => {
    const current = generation, target = identity()
    return () => !disposed && current === generation && target === identity()
  }
  async function guarded<T>(path: string, init?: RequestInit): Promise<T> {
    const current = capture()
    try {
      const value = await request<T>(path, init)
      if (!current()) throw new PluginRequestSuperseded()
      return value
    } catch (failure) {
      if (!current()) throw new PluginRequestSuperseded()
      throw failure
    }
  }
  return {request: guarded, capture}
}

/** 2026/11/07：注销清空共享状态并使在途目录读取失效。 */
export function clearPluginCatalog(): void {
  epoch++
  pluginCatalog.value = {revision: '', items: []}
  pluginPages.value = []
  pluginCatalogError.value = ''
  pluginCatalogBusy.value = false
  pluginCatalogPollingPaused.value = false
}

/** 2026/11/07：两份目录均成功后一次更新；失败保留诊断并停止展示不能复核的插件内容。 */
export async function refreshPluginCatalog(): Promise<void> {
  const captured = ++epoch
  pluginCatalogBusy.value = true
  try {
    const [catalog, pages] = await Promise.all([request<PluginCatalog>('plugins'), request<PluginPage[]>('plugins/pages')])
    if (captured !== epoch) return
    pluginCatalog.value = catalog
    pluginPages.value = pages
    pluginCatalogError.value = ''
    pluginCatalogPollingPaused.value = false
  } catch (failure) {
    if (captured !== epoch) return
    pluginCatalog.value = {revision: '', items: []}
    pluginPages.value = []
    pluginCatalogError.value = failure instanceof Error ? failure.message : '插件目录读取失败。'
    if (failure instanceof ApiError && failure.status === 403) pluginCatalogPollingPaused.value = true
    if (failure instanceof ApiError && failure.status === 401) throw failure
  } finally {
    if (captured === epoch) pluginCatalogBusy.value = false
  }
}
