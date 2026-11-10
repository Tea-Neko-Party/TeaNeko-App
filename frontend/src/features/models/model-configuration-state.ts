import {ref} from 'vue'

// 2026/11/10：只广播已确认的运行配置变化，不保存模型参数、秘密或聊天草稿。
export const appliedModelRevision = ref('')
export function modelConfigurationApplied(revision: string): void {
  appliedModelRevision.value = revision
}
