import {onMounted, onScopeDispose, watch} from 'vue'

/** 2026/11/06：只在已激活且浏览器可见时安装轮询；停止轮询不取消用户提交的写操作。 */
export function useActivePolling(active: () => boolean, poll: () => void, intervalMs: number): void {
  let timer: ReturnType<typeof setInterval> | undefined
  let mounted = false
  function sync() {
    clearInterval(timer)
    timer = undefined
    if (mounted && active() && !document.hidden) timer = setInterval(poll, intervalMs)
  }
  watch(active, sync, {flush: 'sync'})
  onMounted(() => {
    mounted = true
    document.addEventListener('visibilitychange', sync)
    sync()
  })
  onScopeDispose(() => {
    mounted = false
    clearInterval(timer)
    document.removeEventListener('visibilitychange', sync)
  })
}
