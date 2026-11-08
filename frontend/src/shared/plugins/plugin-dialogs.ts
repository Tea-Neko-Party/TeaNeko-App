import {onScopeDispose, watch} from 'vue'
import {ElMessageBox} from 'element-plus'
import {pluginAvailability, pluginEnabled, PluginRequestSuperseded, usePluginRequests} from './plugin-console'

/** 2026/11/07：插件确认框绑定打开时的归属，停用或销毁页面后关闭，旧确认不能继续提交。 */
export function usePluginDialogs(owner: () => string | null | undefined) {
  const {capture} = usePluginRequests()
  let opened = false
  let pluginId: string | null | undefined
  const close = () => { if (opened) ElMessageBox.close() }
  watch(pluginAvailability, () => { if (!pluginEnabled(pluginId)) close() }, {flush: 'sync'})
  onScopeDispose(close)
  async function show<T>(action: () => Promise<T>): Promise<T> {
    pluginId = owner()
    if (!pluginEnabled(pluginId)) throw new PluginRequestSuperseded()
    const current = capture()
    opened = true
    try {
      const value = await action()
      if (!current()) throw new PluginRequestSuperseded()
      return value
    } finally { opened = false; pluginId = undefined }
  }
  return {
    confirm: (...args: Parameters<typeof ElMessageBox.confirm>) => show(() => ElMessageBox.confirm(...args)),
    prompt: (...args: Parameters<typeof ElMessageBox.prompt>) => show(() => ElMessageBox.prompt(...args)),
  }
}
