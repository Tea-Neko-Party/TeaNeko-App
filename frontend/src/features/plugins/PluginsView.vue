<script setup lang="ts">
// 2026/11/07：按功能目录引用共享能力与页面契约。
import {computed, ref} from 'vue'
import {ElAlert, ElButton, ElCard, ElDialog, ElEmpty, ElInput, ElOption, ElSelect, ElTag} from 'element-plus'
import {ApiError, request} from '../../shared/api/client'
import {pluginCatalog, pluginCatalogBusy, pluginCatalogError, pluginCatalogPollingPaused, pluginPages, pluginReceipt, refreshPluginCatalog} from '../../shared/plugins/plugin-console'
import type {PluginContribution, PluginItem, PluginPreview, PluginReceipt} from '../../shared/plugins/plugin-console'
import {usePageContext} from '../../app/pages/page-contracts'
import {usePageDraft} from '../../shared/lifecycle/page-guards'
import {missingPluginModules} from '../../app/pages/page-registry'
import RefreshButton from '../../shared/components/RefreshButton.vue'
import DetailPanel from '../../shared/components/DetailPanel.vue'

// 2026/11/07：启停先使用同一版本预览，再提交固定操作编号；未知结果只查询原回执。
defineProps<{active: boolean}>()
const context = usePageContext()
const search = ref('')
const category = ref('')
const state = ref('')
const busy = ref(false)
const error = ref('')
const receipt = ref<PluginReceipt>()
const preview = ref<PluginPreview>()
const confirmation = ref(false)
const pending = ref<{operationId: string; pluginId: string; enabled: boolean; expectedRevision: string; includeDependencies: boolean}>()
const categories = computed(() => [...new Set(pluginCatalog.value.items.map(item => item.category))].sort())
const items = computed(() => pluginCatalog.value.items.filter(item => (!category.value || item.category === category.value)
  && (!state.value || (state.value === 'enabled' ? item.enabled : !item.enabled))
  && `${item.name} ${item.id} ${item.description} ${item.contributions.map(value => `${value.id} ${value.name} ${value.description}`).join(' ')}`
    .toLowerCase().includes(search.value.toLowerCase())))
const states: Record<string, string> = {ENABLED: '已启用', ACTIVE: '已启用', DISABLED: '已关闭', BLOCKED: '依赖不可用', FAILED: '启停失败', STARTING: '正在启用', STOPPING: '正在关闭', RESTART_REQUIRED: '待重启', PENDING_RESTART: '待重启'}
// 2026/11/07：安装功能说明与启用资格分离，停用卡片仍可查看完整声明。
const kinds: Record<string, string> = {component: 'Agent 组件', system: 'Agent 系统', tool: 'Agent 工具', feature: 'Agent 能力',
  command: '聊天命令', 'file-config': '本地配置', 'chat-config': '聊天配置', task: '后台任务', page: '独立页面'}
function contributions(item: PluginItem) {
  const groups = new Map<string, PluginContribution[]>()
  for (const value of item.contributions) {
    const group = groups.get(value.kind) || []
    group.push(value)
    groups.set(value.kind, group)
  }
  return [...groups].map(([kind, values]) => ({kind, title: kinds[kind] || kind, values}))
}
const receiptType = computed(() => receipt.value?.status === 'APPLIED' ? 'success' : receipt.value?.status === 'FAILED' ? 'error' : 'warning')
function receive(value: unknown, operationId: string) {
  receipt.value = pluginReceipt(value, operationId)
  if (!['APPLYING', 'STARTING', 'STOPPING'].includes(receipt.value.status)) pending.value = undefined
}
usePageDraft(() => ({dirty: false, pending: !!pending.value, busy: busy.value}))

async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try { await action() }
  catch (failure) {
    if (failure instanceof ApiError && failure.status === 401) context.expired()
    error.value = failure instanceof Error ? failure.message : '插件操作未完成。'
  } finally { busy.value = false }
}

async function prepare(item: PluginItem, retry = false) {
  await run(async () => {
    preview.value = await request<PluginPreview>('plugins/preview', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({
      pluginId: item.id, enabled: retry || item.state === 'FAILED' ? item.desiredEnabled : !item.desiredEnabled, expectedRevision: pluginCatalog.value.revision,
    })})
    confirmation.value = true
  })
}

async function submit() {
  const proposed = preview.value
  if (!proposed || proposed.blockers.length) return
  await run(async () => {
    pending.value = {operationId: crypto.randomUUID(), pluginId: proposed.pluginId, enabled: proposed.enabled,
      expectedRevision: proposed.revision, includeDependencies: true}
    confirmation.value = false
    const original = pending.value
    receive(await request<unknown>('plugins/actions/change', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(original)}), original.operationId)
    await context.refresh()
  })
}

async function recover() {
  if (!pending.value) return
  const id = pending.value.operationId
  await run(async () => {
    receive(await request<unknown>(`plugins/operations/${encodeURIComponent(id)}`), id)
    await context.refresh()
  })
}

async function refresh() { await refreshPluginCatalog() }
defineExpose({refresh})
</script>

<template>
  <div class="plugins-page">
    <div class="editor-heading"><ElInput v-model="search" clearable placeholder="搜索插件名称、ID 或功能" aria-label="搜索插件" />
      <RefreshButton :loading="pluginCatalogBusy || busy" label="刷新插件目录" @click="run(refresh)" /></div>
    <div class="command-filters"><ElSelect v-model="category" clearable placeholder="全部分类" aria-label="插件分类">
      <ElOption v-for="value in categories" :key="value" :label="value" :value="value" /></ElSelect>
      <ElSelect v-model="state" clearable placeholder="全部状态" aria-label="插件状态"><ElOption label="已启用" value="enabled" /><ElOption label="未启用" value="disabled" /></ElSelect></div>
    <p class="small muted">插件开关影响所有聊天。关闭后保留原配置与历史数据，相关功能会从其他页面隐藏。</p>
    <ElAlert v-if="error || pluginCatalogError" :title="error || pluginCatalogError" type="error" :closable="false" />
    <p v-if="pluginCatalogPollingPaused" class="small muted">插件目录自动刷新已暂停。处理本机授权后，可点击刷新或重新聚焦窗口复核。</p>
    <ElAlert v-if="receipt" :title="receipt.note" :type="receiptType" :closable="false" />
    <ElAlert v-if="missingPluginModules.length" title="部分插件页面没有随当前前端构建交付，请重新构建应用。" type="warning" :closable="false">
      <code v-for="item in missingPluginModules" :key="item.key">{{ item.page.moduleKey }} </code>
    </ElAlert>
    <ElCard v-if="pending" shadow="never"><p>操作结果尚未确认：<code>{{ pending.operationId }}</code></p>
      <ElButton :loading="busy" @click="recover">查询原操作结果</ElButton></ElCard>
    <div class="plugin-grid">
      <ElCard v-for="item in items" :key="item.id" shadow="never" class="plugin-card">
        <div class="editor-heading"><h2>{{ item.name }}</h2></div>
        <div class="plugin-meta"><code>{{ item.id }}</code><span>v{{ item.version }}</span><ElTag>{{ item.category }}</ElTag></div>
        <div class="plugin-state"><ElTag :type="item.desiredEnabled ? 'primary' : 'info'">启用意愿：{{ item.desiredEnabled ? '开启' : '关闭' }}</ElTag>
          <ElTag :type="item.enabled ? 'success' : 'info'">当前状态：{{ states[item.state] || item.state }}</ElTag></div>
        <p class="preserve-lines">{{ item.description }}</p>
        <p v-if="item.dependencies.length" class="small muted">依赖插件：{{ item.dependencies.join('、') }}</p>
        <p v-if="item.requiredClients.length" class="small muted">需要客户端：{{ item.requiredClients.join('、') }}</p>
        <ElAlert v-if="item.reason" :title="item.reason" type="info" :closable="false" />
        <ElAlert v-if="item.restartRequired" title="此变更需要重启应用后生效。" type="warning" :closable="false" />
        <DetailPanel :title="`已安装功能（${item.contributions.length}）`">
          <p v-if="!item.contributions.length" class="small muted">此插件未声明可展示的功能说明。</p>
          <section v-for="group in contributions(item)" :key="group.kind" class="contribution-group">
            <h3>{{ group.title }}（{{ group.values.length }}）</h3>
            <ul><li v-for="value in group.values" :key="value.id"><strong>{{ value.name }}</strong>
              <code v-if="value.name !== value.id">{{ value.id }}</code>
              <p v-if="value.description" class="small muted preserve-lines">{{ value.description }}</p>
            </li></ul>
          </section>
        </DetailPanel>
        <div class="editor-actions">
          <ElButton v-if="!item.mandatory" :type="item.desiredEnabled && item.state !== 'FAILED' ? 'default' : 'primary'"
            :disabled="busy || !!pending" @click="prepare(item)">{{ item.state === 'FAILED' ? item.desiredEnabled ? '重试启用' : '重试关闭' : item.desiredEnabled ? '关闭插件' : '开启插件' }}</ElButton>
          <ElTag v-else type="info">基础模块</ElTag>
          <ElButton v-if="!item.mandatory && item.desiredEnabled !== item.enabled && !item.restartRequired
              && !['FAILED', 'STARTING', 'STOPPING'].includes(item.state)" :disabled="busy || !!pending"
            @click="prepare(item, true)">重试应用</ElButton>
          <template v-if="item.enabled">
            <ElButton @click="context.navigate('files', {kind: 'plugin', pluginId: item.id})">本地配置</ElButton>
            <ElButton @click="context.navigate('settings', {kind: 'plugin', pluginId: item.id})">聊天配置</ElButton>
            <ElButton v-for="page in pluginPages.filter(page => page.pluginId === item.id)" :key="page.key"
              @click="context.navigate('plugin:' + page.key)">{{ page.page.title }}</ElButton>
          </template>
        </div>
      </ElCard>
    </div>
    <ElEmpty v-if="!items.length && !pluginCatalogBusy" description="没有匹配的插件" />
    <ElDialog v-model="confirmation" :title="preview?.enabled ? '开启插件' : '关闭插件'" width="min(560px, 94vw)" :close-on-click-modal="false">
      <template v-if="preview"><p>插件：<code>{{ preview.pluginId }}</code></p>
        <p v-if="preview.affected.length">同时影响：{{ preview.affected.join('、') }}</p>
        <ElAlert v-for="reason in preview.blockers" :key="reason" :title="reason" type="error" :closable="false" />
        <ElAlert v-if="preview.restartRequired" title="保存后需要重启应用才能完成变更。" type="warning" :closable="false" />
        <p v-else>确认后立即应用；已提交的外部操作不因此回滚。</p>
      </template>
      <template #footer><ElButton @click="confirmation = false">返回</ElButton><ElButton type="primary" :loading="busy" :disabled="!!preview?.blockers.length" @click="submit">确认变更</ElButton></template>
    </ElDialog>
  </div>
</template>

<style scoped>
.plugin-grid {display:grid; grid-template-columns:repeat(auto-fit,minmax(min(100%,360px),1fr)); gap:16px; margin-top:16px}
.plugin-card h2 {font-size:18px; margin:0}
.plugin-meta {display:flex; flex-wrap:wrap; align-items:center; gap:10px; color:var(--muted)}
.plugin-state {display:flex; flex-wrap:wrap; gap:8px; margin-top:12px}
.contribution-group h3 {font-size:14px; margin:12px 0 8px}
.contribution-group ul {padding-left:18px; margin:0}
.contribution-group li {margin:10px 0; overflow-wrap:anywhere}
.contribution-group code {display:block; font-size:12px}
.contribution-group p {margin:4px 0}
.plugin-card .editor-actions {flex-wrap:wrap; margin-top:16px}
.preserve-lines {white-space:pre-line}
</style>
