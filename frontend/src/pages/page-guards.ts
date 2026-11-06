import type {InjectionKey} from 'vue'
import {inject, onScopeDispose, provide} from 'vue'
import {ElMessageBox} from 'element-plus'
import type {PageLeave} from './page-contracts'

interface DraftState {dirty?: boolean; pending?: boolean; busy?: boolean}
type ReadDraft = () => DraftState
const draftKey: InjectionKey<Set<ReadDraft>> = Symbol('页面草稿与未决操作')

/** 2026/11/06：嵌套编辑器也登记到所属页面，导航保留实例，退出才询问释放状态。 */
export function usePageDraft(read: ReadDraft): void {
  const drafts = inject(draftKey, undefined)
  drafts?.add(read)
  onScopeDispose(() => drafts?.delete(read))
}

/** 2026/11/06：确认只批准离开，不清空草稿；注销失败仍可返回查询原操作。 */
export function providePageGuard(title: string) {
  const drafts = new Set<ReadDraft>()
  provide(draftKey, drafts)
  return async (leave: PageLeave): Promise<boolean> => {
    if (leave.retained) return true
    const states = [...drafts].map(read => read())
    if (!states.some(state => state.dirty || state.pending || state.busy)) return true
    const action = leave.reason === 'signout' ? '退出' : '离开页面'
    const message = states.some(state => state.pending || state.busy)
      ? `仍有进行中或未确认的操作。${action}不会撤销它，请先保留操作编号。${action}后将释放本页草稿与内存中的原请求。`
      : `尚有未保存的内容。${action}后将释放本页草稿。`
    try {
      await ElMessageBox.confirm(message, title, {
        confirmButtonText: `确认${action}`, cancelButtonText: '保留当前内容', type: 'warning',
      })
      return true
    } catch {
      return false
    }
  }
}
