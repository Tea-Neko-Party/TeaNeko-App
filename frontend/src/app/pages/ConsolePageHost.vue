<script setup lang="ts">
import type {Component} from 'vue'
// 2026/11/07：按功能目录引用共享能力与页面契约。
import {computed, markRaw, nextTick, onBeforeUnmount, onMounted, provide, ref, shallowReactive, watch} from 'vue'
import {ElAlert} from 'element-plus'
import {ApiError} from '../../shared/api/client'
import RefreshButton from '../../shared/components/RefreshButton.vue'
import ConsolePageOutlet from './ConsolePageOutlet.vue'
import type {ConsolePageKey} from './page-registry'
import {consolePage, consolePages} from './page-registry'
import type {PageContext, PageEnvironment, PageIntent, PageOutletHandle} from './page-contracts'
import {pageContextKey} from './page-contracts'
import {pluginAvailability, PluginRequestSuperseded} from '../../shared/plugins/plugin-console'
// 2026/11/10：插件专页标题使用统一名称标签，稳定 ID 留在悬停说明中。
import PluginLabel from '../../shared/plugins/PluginLabel.vue'

const props = defineProps<{environment: PageEnvironment; initialPage: ConsolePageKey}>()
const current = ref(props.initialPage)
const title = computed(() => consolePages.value.find(page => page.key === current.value)?.title ?? '插件管理')
const pluginId = computed(() => consolePages.value.find(page => page.key === current.value)?.pluginId)
const changing = ref(false)
const refreshing = ref(false)
const error = ref('')
const loaded = shallowReactive(new Map<ConsolePageKey, Component>())
const handles = shallowReactive(new Map<ConsolePageKey, PageOutletHandle>())
const mountedPages = computed(() => consolePages.value.filter(page => loaded.has(page.key)))
const selected = computed(() => handles.get(current.value))
let disposed = false
let availabilityEpoch = 0

// 2026/11/07：权限变化销毁已挂载编辑器及弹窗，迟到模块加载不能恢复关闭的插件。
watch(() => `${pluginAvailability.value}|${consolePages.value.map(page => page.key).join('|')}`, () => {
  availabilityEpoch++
  for (const key of [...loaded.keys()]) {
    if (key.startsWith('plugin:') && !consolePages.value.some(page => page.key === key)) {
      loaded.delete(key)
      handles.delete(key)
    }
  }
  if (!consolePages.value.some(page => page.key === current.value)) {
    current.value = 'plugins'
    void nextTick().then(() => { if (!disposed && !changing.value) void navigate('plugins') })
  }
}, {flush: 'sync'})

// 2026/11/06：会话销毁后，迟到的模块加载及子页事件不能重建旧页面或退出新会话。
const context: PageContext = {
  ...props.environment,
  navigate,
  expired: () => { if (!disposed) props.environment.expired() },
  tokensApplied: () => { if (!disposed) props.environment.tokensApplied() },
  signOut,
}
provide(pageContextKey, context)

/** 2026/11/06：先加载并处理受控意图，再切换可见页；失败保留原页且不自动重放业务请求。 */
async function navigate(key: ConsolePageKey, intent?: PageIntent): Promise<boolean> {
  if (changing.value || disposed) return false
  const definition = consolePage(key)
  if (definition.visible && !definition.visible(context)) {
    error.value = '该页面当前不可用，请刷新客户端目录。'
    return false
  }
  changing.value = true
  const epoch = availabilityEpoch
  // 2026/11/07：每个异步边界均复核目录、权限和代次，已挂载页面的迟到 open 也不能复活入口。
  const valid = () => !disposed && epoch === availabilityEpoch
    && consolePages.value.some(page => page.key === key) && (!definition.visible || definition.visible(context))
  error.value = ''
  try {
    const previous = current.value
    if (previous !== key && await handles.get(previous)?.canLeave({
      reason: 'navigate', retained: consolePage(previous).retention !== 'active',
    }) === false) return false
    if (!valid()) return false
    if (!loaded.has(key)) {
      const module = await definition.load().catch(failure => {
        throw new Error('页面资源加载失败，请重试。若刚更新部署，请先保留草稿与操作编号，再刷新浏览器。', {cause: failure})
      })
      if (!valid()) return false
      loaded.set(key, markRaw(module.default))
      await nextTick()
    }
    if (!valid()) return false
    const handle = handles.get(key)
    if (!handle) throw new Error('页面尚未完成初始化，请重试。')
    if (intent && !await handle.open(intent)) return false
    if (!valid()) return false
    current.value = key
    if (previous !== key && consolePage(previous).retention === 'active') {
      loaded.delete(previous)
      handles.delete(previous)
    }
    return true
  } catch (failure) {
    if (valid() && !(failure instanceof PluginRequestSuperseded)) report(failure)
    return false
  } finally {
    changing.value = false
    // 2026/11/07：加载期间撤销插件资格后，确保回退页也实际挂载，避免只更换标题。
    if (!disposed && epoch !== availabilityEpoch && current.value === 'plugins' && !loaded.has('plugins')) {
      void nextTick().then(() => { if (!disposed && !changing.value) void navigate('plugins') })
    }
  }
}

function report(failure: unknown) {
  if (failure instanceof ApiError && failure.status === 401) context.expired()
  error.value = failure instanceof Error ? failure.message : '页面操作未完成，请重试。'
}

async function refresh() {
  if (refreshing.value || changing.value) return
  refreshing.value = true
  error.value = ''
  try { await selected.value?.refresh() }
  catch (failure) { if (!disposed) report(failure) }
  finally { refreshing.value = false }
}

/** 2026/11/06：检查所有已挂载页，包括隐藏草稿；只有服务器注销成功后由外壳卸载。 */
async function signOut() {
  if (changing.value || disposed) return
  changing.value = true
  try {
    for (const handle of handles.values()) {
      if (!await handle.canLeave({reason: 'signout', retained: false}) || disposed) return
    }
    await props.environment.signOut()
  } catch (failure) {
    if (!disposed) report(failure)
  } finally {
    changing.value = false
  }
}
onMounted(() => void navigate(props.initialPage))
onBeforeUnmount(() => { disposed = true; handles.clear(); loaded.clear() })
defineExpose({navigate, current, title, changing})
</script>

<template>
  <div v-show="!selected?.headingHidden.value" class="page-heading">
    <div><h1>{{ title }}</h1><PluginLabel v-if="pluginId" :plugin-id="pluginId" /></div>
    <RefreshButton v-if="selected?.hasRefresh.value" :loading="refreshing || environment.busy.value" @click="refresh" label="刷新" />
  </div>
  <p v-if="changing" role="status" class="small muted">正在打开页面…</p>
  <ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon />
  <ConsolePageOutlet v-for="page in mountedPages" :key="page.key" v-show="current === page.key"
    :page="page" :component="loaded.get(page.key)!" :active="current === page.key"
    @ready="handle => handles.set(page.key, handle)" />
</template>
