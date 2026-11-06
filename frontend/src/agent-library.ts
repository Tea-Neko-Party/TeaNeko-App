// 2026/11/06：持久目录统一遍历、去重与取消，空游标页不再成为用户必须处理的状态。
import {onBeforeUnmount, ref, watch} from 'vue'
import {ApiError, request} from './api'

export interface LibraryItem {id: string; name: string; detail: string; generation: number; revision: number; readOnly: boolean; mappings: {client: string; platformId: string; nickname: string}[]}
export interface LibraryQuery {kind: 'memory' | 'personality' | 'profiles'; namespace?: string; generation?: number}
interface LibraryPage {items: LibraryItem[]; next: string}

export function useAgentLibrary(query: () => LibraryQuery, expired: () => void) {
  const items = ref<LibraryItem[]>([])
  const busy = ref(false)
  const error = ref('')
  let controller: AbortController | undefined
  async function load() {
    controller?.abort()
    const current = new AbortController()
    controller = current
    const selected = query()
    items.value = []
    error.value = ''
    busy.value = true
    try {
      if (selected.kind === 'profiles' && (!selected.namespace || (selected.generation ?? -1) < 0)) return
      const merged = new Map<string, LibraryItem>()
      const visited = new Set<string>()
      let after = ''
      do {
        if (visited.has(after)) throw new Error('目录游标没有前进，请刷新后重试。')
        visited.add(after)
        const page = await request<LibraryPage>('management/teaneko.agent:library', {
          method: 'POST', signal: current.signal, headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({namespace: '', generation: -1, ...selected, after}),
        })
        if (current.signal.aborted) return
        page.items.forEach(item => merged.set(item.id, item))
        items.value = [...merged.values()].sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
        after = page.next
      } while (after)
    } catch (failure) {
      if (current.signal.aborted) return
      if (failure instanceof ApiError && failure.status === 401) expired()
      error.value = failure instanceof ApiError && failure.status === 403
        ? '当前账号未开放该目录及对应空间的管理权限。'
        : failure instanceof Error ? failure.message : '持久目录读取失败。'
    } finally {
      if (!current.signal.aborted) busy.value = false
    }
  }
  watch(() => JSON.stringify(query()), () => { void load() }, {immediate: true})
  onBeforeUnmount(() => controller?.abort())
  return {items, busy, error, load}
}
