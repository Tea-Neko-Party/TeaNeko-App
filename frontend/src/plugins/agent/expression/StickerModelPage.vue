<script setup lang="ts">
// 2026/11/10：备用模型复用文件版本与操作回执，保存并应用后供新作业读取。
import {computed, onMounted, onScopeDispose, ref, watch} from 'vue'
import {ElAlert, ElButton, ElCard, ElMessageBox, ElTag} from 'element-plus'
import type {ModelSelection} from '../../../features/models/ModelSelectionEditor.vue'
import ModelSelectionEditor from '../../../features/models/ModelSelectionEditor.vue'
import ModelVisionProbeButton from '../../../features/models/ModelVisionProbeButton.vue'
import type {ModelCatalog} from '../../../features/models/ModelTemplateEditor.vue'
import type {FileField} from '../../../features/settings/files/FileFieldEditor.vue'
import {ApiError} from '../../../shared/api/client'
import {PluginRequestSuperseded, usePluginRequests} from '../../../shared/plugins/plugin-console'
import {usePageDraft} from '../../../shared/lifecycle/page-guards'
import {useActivePolling} from '../../../shared/lifecycle/active-polling'

interface View {revision: string; fields: FileField[]; runtime: {current: boolean; revision: string}}
interface Catalog {models: ModelCatalog; systemRoutes: Record<string, string>; modelError: string}
interface Receipt {operationId: string; status: string; diskRevision: string; note: string}
interface Edit {path: string; action: string; value?: unknown}
const emit = defineEmits<{expired: []}>()
const props = defineProps<{active: boolean}>()
const {request} = usePluginRequests()
const section = 'agent.sticker-model'
const view = ref<View>()
const catalog = ref<Catalog>()
const draft = ref<ModelSelection>({source: 'none'})
const original = ref('')
const busy = ref(false)
const valid = ref(true)
const error = ref('')
const notice = ref('')
const pending = ref<{action: 'save' | 'apply'; body: {operationId: string; expectedRevision: string; edits?: Edit[]}}>()
const dirty = computed(() => JSON.stringify(draft.value) !== original.value)
const locked = computed(() => busy.value || !!pending.value)
usePageDraft(() => ({dirty: dirty.value, pending: !!pending.value, busy: busy.value}))
async function load() {
  const next = await request<View>(`file-configs/${section}`)
  const selection: ModelSelection = {source: String(next.fields.find(field => field.path === '/fallback/source')?.value ?? 'none')}
  if (selection.source === 'system') selection.route = String(next.fields.find(field => field.path === '/fallback/route')?.value ?? '')
  if (selection.source === 'custom') {
    selection.configuration = {}
    for (const field of next.fields.filter(field => field.path.startsWith('/fallback/configuration/'))) {
      const key = field.path.slice('/fallback/configuration/'.length)
      if (field.secret) { if (field.configured) selection.configuration[key] = {$keep: true} }
      else if (field.value !== null && field.value !== undefined) selection.configuration[key] = field.value
    }
  }
  view.value = next
  draft.value = selection
  original.value = JSON.stringify(selection)
}
async function loadCatalog() { catalog.value = await request<Catalog>('agent/catalog/components') }
async function run(action: () => Promise<void>) {
  busy.value = true; error.value = ''
  try {await action()}
  catch (failure) {
    if (!(failure instanceof PluginRequestSuperseded)) {
      if (failure instanceof ApiError && failure.status === 401) emit('expired')
      error.value = failure instanceof Error ? failure.message : '模型设置操作未完成。'
    }
  } finally {busy.value = false}
}
function edits(): Edit[] {
  const selected = draft.value
  const values: Record<string, unknown> = {'/fallback/source': selected.source ?? 'none'}
  if (selected.source === 'system') values['/fallback/route'] = selected.route ?? ''
  if (selected.source === 'custom') {
    for (const [key, value] of Object.entries(selected.configuration ?? {})) values[`/fallback/configuration/${key}`] = value
  }
  const changes: Edit[] = []
  for (const field of view.value!.fields) {
    if (!(field.path in values)) continue
    const value = values[field.path]
    if (field.secret) {
      if (value && typeof value === 'object') continue
      changes.push({path: field.path, action: value === '' ? 'CLEAR_SECRET' : 'REPLACE_SECRET', ...(value === '' ? {} : {value})})
    } else if (JSON.stringify(value) !== JSON.stringify(field.value)) changes.push({path: field.path, action: 'SET', value})
  }
  // 切换私有适配器时移除旧可选参数，避免看不见的旧 metadata 继续生效。
  if (selected.source === 'custom') for (const field of view.value!.fields) {
    if (!field.secret && field.path.startsWith('/fallback/configuration/') && !(field.path in values) && field.value !== null) changes.push({path: field.path, action: 'REMOVE'})
  }
  if (!changes.length) throw new Error('请先修改模型设置。')
  return changes
}
async function receive(result: Receipt) {
  const operation = pending.value!
  if (!result || result.operationId !== operation.body.operationId || !result.diskRevision
      || !['PREPARED', 'SAVED', 'APPLIED', 'APPLY_FAILED', 'INDETERMINATE', 'NOT_SAVED'].includes(result.status)) throw new Error('回执未确认，请查询原操作结果。')
  if (['PREPARED', 'INDETERMINATE'].includes(result.status)) throw new Error('操作结果尚未确认，请查询原操作结果。')
  if (operation.action === 'save' && !['SAVED', 'NOT_SAVED'].includes(result.status)
      || operation.action === 'apply' && !['APPLIED', 'APPLY_FAILED'].includes(result.status)) throw new Error('回执与原操作不符，请查询原操作结果。')
  pending.value = undefined
  notice.value = result.note
  if (result.status === 'NOT_SAVED') throw new Error(result.note)
  if (operation.action === 'save' && result.status === 'SAVED') {
    await load()
    pending.value = {action: 'apply', body: {operationId: crypto.randomUUID(), expectedRevision: result.diskRevision}}
    await deliver()
    return
  }
  await load()
  if (result.status === 'APPLY_FAILED') throw new Error('模型已保存但尚未应用：' + result.note)
}
async function deliver() {
  const operation = pending.value!
  try {
    await receive(await request<Receipt>(`file-configs/${section}/actions/${operation.action}`, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(operation.body)}))
  } catch (failure) {
    if (pending.value === operation && failure instanceof ApiError && failure.status === 400) pending.value = undefined
    throw failure
  }
}
function save() {void run(async () => {pending.value = {action: 'save', body: {operationId: crypto.randomUUID(), expectedRevision: view.value!.revision, edits: edits()}}; await deliver()})}
function apply() {void run(async () => {pending.value = {action: 'apply', body: {operationId: crypto.randomUUID(), expectedRevision: view.value!.revision}}; await deliver()})}
function recover() {void run(async () => {await receive(await request<Receipt>(`file-configs/${section}/operations/${pending.value!.body.operationId}`))})}
async function discard() {
  try {
    await ElMessageBox.confirm(pending.value
      ? `这不会撤销可能已经提交的操作。请先保留编号 ${pending.value.body.operationId}，随后重新读取当前配置。`
      : '放弃本页未保存的修改，重新读取当前配置。', '重新读取配置', {confirmButtonText: '放弃草稿并读取', cancelButtonText: '保留草稿', type: 'warning'})
  } catch {return}
  await run(async () => {await load(); pending.value = undefined; notice.value = ''})
}
// 目录刷新不改变编辑状态，避免定时轮询使正在进行的检测失效。
function refreshCatalog() {void loadCatalog().catch(failure => {
  if (failure instanceof ApiError && failure.status === 401) emit('expired')
  else if (!(failure instanceof PluginRequestSuperseded)) error.value = '模型目录刷新失败，请稍后刷新。'
})}
useActivePolling(() => props.active && !busy.value, refreshCatalog, 10000)
const focus = () => {if (props.active && !busy.value && !document.hidden) refreshCatalog()}
watch(() => props.active, active => {if (active && !busy.value) refreshCatalog()})
onMounted(() => window.addEventListener('focus', focus))
onScopeDispose(() => window.removeEventListener('focus', focus))
onMounted(() => {void run(async () => {await Promise.all([load(), loadCatalog()])})})
onScopeDispose(() => {draft.value = {source: 'none'}; pending.value = undefined})
</script>

<template>
  <div class="file-settings">
    <ElCard shadow="never">
      <h2>表情包识图模型</h2>
      <p class="config-description">组件默认跟随聊天的 LLM，也可在聊天配置中单独选择。这里设置全实例备用模型：主模型不可用或调用报错后，最多再尝试一次备用模型，并继续占用采集额度。</p>
      <ElAlert v-if="error" :title="error" type="error" :closable="false" />
      <ElAlert v-if="notice" :title="notice" type="info" :closable="false" />
      <template v-if="view && catalog">
        <ElTag :type="view.runtime.current && view.runtime.revision === view.revision ? 'success' : 'warning'">{{ view.runtime.current && view.runtime.revision === view.revision ? '设置已应用' : '保存后需应用到运行配置' }}</ElTag>
        <ElAlert v-if="catalog.modelError" :title="catalog.modelError" type="warning" :closable="false" />
        <ModelSelectionEditor v-model="draft" :catalog="catalog.models" :routes="catalog.systemRoutes" :disabled="locked" default-source="none" @validity="value => valid = value" />
        <div class="editor-actions sticker-model-actions">
          <ElButton type="primary" :disabled="locked || !dirty || !valid" @click="save">保存并应用</ElButton>
          <ElButton v-if="!dirty && (!view.runtime.current || view.runtime.revision !== view.revision)" :disabled="locked" @click="apply">应用已保存设置</ElButton>
          <ElButton v-if="pending" :disabled="busy" @click="recover">查询原操作结果</ElButton>
          <ElButton v-if="dirty || pending" :disabled="busy" @click="discard">放弃本页草稿并重新读取</ElButton>
          <ElButton :disabled="locked || dirty" @click="run(load)">刷新</ElButton>
        </div>
        <p v-if="pending" class="small muted">待确认操作：{{ pending.body.operationId }}。不会自动重发保存请求。</p>
        <ModelVisionProbeButton endpoint="plugins/sticker-models/probe/global" :context="{revision: view.revision}" :disabled="locked || dirty || draft.source === 'none'" @expired="emit('expired')" />
      </template>
    </ElCard>
  </div>
</template>

<style scoped>
/* 2026/11/10：模型选择与保存操作之间保留留白，适用于已有路由和私有模型表单。 */
.sticker-model-actions { margin-top: 1.25rem; }
</style>
