<script setup lang="ts">
// 2026/11/04：重新读取采用统一图标，放弃修改仍明确显示动作文字。
import RefreshButton from './RefreshButton.vue'
import {computed, onBeforeUnmount, onMounted, reactive, ref} from 'vue'
import {ElAlert, ElButton, ElCard, ElEmpty, ElInput, ElMessageBox, ElOption, ElSelect, ElTag} from 'element-plus'
import {ApiError, request} from './api'
import DetailPanel from './DetailPanel.vue'
import {useConsolePreferences} from './console-preferences'

interface Stamp { revision: string; generation: number; current: boolean }
interface Field { path: string; label: string; description: string; kind: string; secret: boolean; configured: boolean; source: string; value: unknown }
interface Collection { path: string; label: string; maximum: number }
interface Section { id: string; name: string; application: string }
interface View extends Section { revision: string; exists: boolean; runtime: Stamp; fields: Field[]; collections: Collection[]; collectionSizes: Record<string, number> }
interface Edit { path: string; action: string; value?: unknown }
interface Receipt { operationId: string; status: string; diskRevision: string; runtime: Stamp; note: string; fields: string[]; at: string; replayed: boolean }
interface Pending { id: string; action: 'save' | 'apply'; body: { operationId: string; expectedRevision: string; edits?: Edit[] } }
const emit = defineEmits<{ expired: [] }>()
const {advanced} = useConsolePreferences()
const sections = ref<Section[]>([])
const view = ref<View>()
const busy = ref(false)
const error = ref('')
const notice = ref('')
const receipt = ref<Receipt>()
// 2026/11/03：秘密与原请求仅留在当前组件内存，退出或卸载后释放，不写入浏览器存储。
const pending = ref<Pending>()
const drafts = reactive<Record<string, { action: string; text: string }>>({})
const structure = ref<Edit>()
const appendPath = ref('')
const appendText = ref('{}')
const dirty = computed(() => !!structure.value || Object.values(drafts).some(item => item.action !== 'KEEP'))
const locked = computed(() => busy.value || !!pending.value)
const statusNames: Record<string, string> = { PREPARED: '尚待确认', SAVED: '已保存，尚未应用', APPLIED: '本进程应用已确认', APPLY_FAILED: '应用未确认', RESTART_REQUIRED: '需要重启', INDETERMINATE: '保存结果无法确认', NOT_SAVED: '原文件未替换' }
const actionNames: Record<string, string> = { SET: '设置', REMOVE: '移除声明', REPLACE_SECRET: '替换秘密', CLEAR_SECRET: '清除秘密', APPEND: '新增条目' }
const summary = computed(() => [
  ...Object.entries(drafts).filter(([, item]) => item.action !== 'KEEP').map(([path, item]) => `${actionNames[item.action]}：${view.value?.fields.find(field => field.path === path)?.label} (${path})`),
  ...(structure.value ? [`${actionNames[structure.value.action]}：${structure.value.path}`] : []),
])

function resetDrafts() {
  for (const path of Object.keys(drafts)) delete drafts[path]
  for (const field of view.value?.fields ?? []) drafts[field.path] = { action: 'KEEP', text: field.secret || field.value == null ? '' : typeof field.value === 'string' ? field.value : JSON.stringify(field.value, null, 2) }
  structure.value = undefined
  appendText.value = '{}'
  appendPath.value = ''
}

// 2026/11/04：普通字段直接编辑，恢复原文时撤销该字段变更；秘密仍必须显式选择动作。
function editField(field: Field, text: string) {
  const draft = drafts[field.path]!
  draft.text = text
  if (field.secret) return
  const original = field.value == null ? '' : typeof field.value === 'string' ? field.value : JSON.stringify(field.value, null, 2)
  draft.action = text === original ? 'KEEP' : 'SET'
}

async function run(action: () => Promise<void>) {
  busy.value = true
  error.value = ''
  try { await action() }
  catch (failure) {
    error.value = failure instanceof Error ? failure.message : '文件配置操作未完成。'
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
  } finally { busy.value = false }
}

async function confirm(message: string): Promise<boolean> {
  try { await ElMessageBox.confirm(message, '确认文件操作', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }); return true }
  catch { return false }
}

async function load(id: string) {
  view.value = await request<View>(`file-configs/${encodeURIComponent(id)}`)
  resetDrafts()
}

async function select(id: string) {
  if ((dirty.value || pending.value) && !await confirm('切换将丢弃当前草稿和内存中的原请求。未确认操作仍可能已经完成，请先查询回执。')) return
  await run(async () => { await load(id); pending.value = undefined; receipt.value = undefined; notice.value = '' })
}

function edits(): Edit[] {
  const result = Object.entries(drafts).filter(([, item]) => item.action !== 'KEEP').map(([path, item]) => {
    const field = view.value!.fields.find(value => value.path === path)!
    if (item.action === 'REMOVE' || item.action === 'CLEAR_SECRET') return { path, action: item.action }
    if (!item.text.trim()) throw new Error(`请填写“${field.label}”的新值，清除声明请使用对应动作。`)
    let value: unknown = item.text
    if (field.kind === 'JSON') value = JSON.parse(item.text)
    else if (!item.text.includes('${') && (field.kind === 'NUMBER' || field.kind === 'BOOLEAN')) {
      value = JSON.parse(item.text)
      if (typeof value !== (field.kind === 'NUMBER' ? 'number' : 'boolean')) throw new Error(`“${field.label}”需要${field.kind === 'NUMBER' ? '数字' : 'true 或 false'}，也可使用环境占位符。`)
    }
    return { path, action: item.action, value }
  })
  if (structure.value) result.push(structure.value)
  if (!result.length) throw new Error('请先选择需要修改的字段。')
  return result
}

async function validate() {
  await run(async () => {
    const body = { operationId: crypto.randomUUID(), expectedRevision: view.value!.revision, edits: edits() }
    const result = await request<{ message: string }>(`file-configs/${view.value!.id}/actions/validate`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
    notice.value = result.message
  })
}

async function deliver() {
  const original = pending.value!
  const result = await request<Receipt>(`file-configs/${original.id}/actions/${original.action}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(original.body) })
  await receive(result)
}

async function receive(result: Receipt) {
  receipt.value = result
  if (['SAVED', 'APPLIED', 'RESTART_REQUIRED', 'APPLY_FAILED', 'INDETERMINATE'].includes(result.status)) {
    // 2026/11/03：保留原操作编号供后续查询，但立即释放包含秘密的请求体。
    pending.value = undefined
    await load(view.value!.id)
  }
}

async function save() {
  if (!await confirm('只保存明确修改的字段；未修改秘密、环境模板和未知字段保留。YAML 会重新排版，原注释不会保留。保存完成后仍须单独应用。')) return
  await run(async () => {
    pending.value = { id: view.value!.id, action: 'save', body: { operationId: crypto.randomUUID(), expectedRevision: view.value!.revision, edits: edits() } }
    for (const field of view.value!.fields.filter(item => item.secret)) drafts[field.path]!.text = ''
    appendText.value = '{}'
    notice.value = ''
    await deliver()
  })
}

async function apply() {
  if (!await confirm('将当前磁盘版本应用到本进程。固定连接及资源可能要求重启；已开始的模型请求继续使用其原配置。')) return
  await run(async () => {
    pending.value = { id: view.value!.id, action: 'apply', body: { operationId: crypto.randomUUID(), expectedRevision: view.value!.revision } }
    await deliver()
  })
}

async function query() {
  const operation = pending.value?.body.operationId ?? receipt.value?.operationId
  if (!operation) return
  await run(async () => { await receive(await request<Receipt>(`file-configs/${view.value!.id}/operations/${operation}`)) })
}

async function discard() {
  if (!await confirm('重新读取磁盘并释放草稿及原请求。此操作不会撤销已经完成的保存或应用；结果不明时应先查询原编号。')) return
  await run(async () => { await load(view.value!.id); pending.value = undefined; notice.value = '' })
}

async function append() {
  await run(async () => {
    const value = JSON.parse(appendText.value)
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('新增条目必须是 JSON 对象。')
    structure.value = { path: appendPath.value, action: 'APPEND', value }
    appendText.value = '{}'
  })
}

async function remove(collection: Collection, index: number) {
  if (await confirm(`删除“${collection.label}”第 ${index + 1} 项？还需保存后才会修改磁盘。`)) structure.value = { path: `${collection.path}/${index}`, action: 'REMOVE' }
}

function fieldLocked(path: string) {
  const collection = view.value?.collections.find(item => path.startsWith(`${item.path}/`))
  return !!(structure.value && collection && (structure.value.path === collection.path || structure.value.path.startsWith(`${collection.path}/`)))
}

function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value || pending.value) { event.preventDefault(); event.returnValue = '' }
}

onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
  void run(async () => { sections.value = await request<Section[]>('file-configs'); if (sections.value[0]) await load(sections.value[0].id) })
})
onBeforeUnmount(() => { window.removeEventListener('beforeunload', beforeUnload); pending.value = undefined; resetDrafts() })
</script>

<template>
  <!-- 2026/11/04：直接编辑普通字段，秘密动作保持显式，原提交确认流程继续可见。 -->
  <div class="file-settings">
    <ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <ElAlert v-if="notice" :title="notice" type="success" :closable="false" />
    <div class="settings-columns">
      <ElCard shadow="never" class="target-list"><h2>本地配置</h2>
        <button v-for="section in sections" :key="section.id" class="target-button" :class="{ selected: view?.id === section.id }" :disabled="busy" @click="select(section.id)"><strong>{{ section.name }}</strong><span>{{ section.application === 'RESTART' ? '保存后需重启' : '支持本进程应用' }}</span></button>
        <ElEmpty v-if="!sections.length && !busy" description="没有已开放的配置区" />
      </ElCard>
      <div v-if="view" class="settings-editor">
        <ElCard shadow="never"><h2>{{ view.name }}</h2>
          <ElTag :type="view.runtime.current && view.runtime.revision === view.revision ? 'success' : 'warning'">{{ view.runtime.current && view.runtime.revision === view.revision ? '运行与磁盘一致' : '磁盘与运行状态不同' }}</ElTag>
          <DetailPanel title="文件与应用说明"><p>保存写入磁盘后仍需单独应用。移除声明会恢复默认行为；预检不会请求远端 API。</p><p class="small revision">{{ view.exists ? '磁盘文件已存在' : '当前是未保存的模板' }} · 运行代次 {{ view.runtime.generation }}<br />磁盘版本：{{ view.revision }}<br />运行版本：{{ view.runtime.revision }}</p></DetailPanel>
          <div v-for="field in view.fields" :key="field.path" class="settings-field">
            <div><label :for="`file-${field.path}${field.secret && drafts[field.path]!.action !== 'REPLACE_SECRET' ? '-action' : ''}`">{{ field.label }}</label><span v-if="field.secret" class="field-source"> · {{ field.configured ? '已配置' : '未配置' }}</span><DetailPanel title="说明"><p>{{ field.description }}</p><span class="field-source">{{ field.path }} · {{ field.source }}</span></DetailPanel></div>
            <div class="file-input">
              <ElSelect v-show="advanced || field.secret || !['KEEP', 'SET'].includes(drafts[field.path]!.action)" :id="`file-${field.path}-action`" v-model="drafts[field.path]!.action" :aria-label="`${field.label}修改方式`" :disabled="locked || fieldLocked(field.path)">
                <ElOption label="保持原值" value="KEEP" />
                <ElOption v-if="!field.secret" label="设置新值" value="SET" /><ElOption v-if="!field.secret" label="移除声明" value="REMOVE" />
                <ElOption v-if="field.secret" label="替换秘密" value="REPLACE_SECRET" /><ElOption v-if="field.secret" label="清除秘密" value="CLEAR_SECRET" />
              </ElSelect>
              <ElInput v-if="!field.secret || drafts[field.path]!.action === 'REPLACE_SECRET'" :id="`file-${field.path}`" :model-value="drafts[field.path]!.text" @update:model-value="value => editField(field, value)" :type="field.secret ? 'password' : field.kind === 'JSON' ? 'textarea' : 'text'" :rows="3" :disabled="locked || fieldLocked(field.path) || !['KEEP', 'SET', 'REPLACE_SECRET'].includes(drafts[field.path]!.action)" :placeholder="field.secret ? '新令牌或环境占位符' : '支持环境占位符'" autocomplete="off" :aria-label="field.label" />
            </div>
          </div>
        </ElCard>
        <ElCard v-for="collection in view.collections" :key="collection.path" shadow="never"><h3>{{ collection.label }} · {{ view.collectionSizes[collection.path] ?? 0 }} / {{ collection.maximum }}</h3>
          <DetailPanel title="条目编辑说明"><p>集合增删需单独保存。模型示例：{"id":"new-model","model":"模型名称"}；人格示例：{"id":"new-character","display-name":"显示名称"}。</p></DetailPanel>
          <div class="editor-actions"><template v-if="advanced"><ElButton v-for="index in (view.collectionSizes[collection.path] ?? 0)" :key="index" :disabled="locked || !!structure" @click="remove(collection, index - 1)">删除第 {{ index }} 项</ElButton></template>
            <ElButton :disabled="locked || !!structure || (view.collectionSizes[collection.path] ?? 0) >= collection.maximum" @click="appendPath = collection.path">新增条目</ElButton></div>
          <div v-if="appendPath === collection.path" class="file-append"><ElInput v-model="appendText" type="textarea" :rows="5" aria-label="新增配置条目 JSON" :disabled="locked" /><ElButton :disabled="locked" @click="append">加入本次修改</ElButton></div>
        </ElCard>
        <ElCard v-if="dirty" shadow="never"><h3>本次修改</h3><ul><li v-for="line in summary" :key="line" class="small revision">{{ line }}</li></ul><p class="small muted">保存将重新排版 YAML，原注释不会保留。未修改的值、环境模板和未知配置项保留。</p></ElCard>
        <ElCard v-if="pending" shadow="never" class="pending-operation"><h3>原操作尚待确认</h3><p class="small revision">操作编号：{{ pending.body.operationId }}</p><p class="small muted">连接中断不代表写入失败。先查询原回执；如尚无记录，可使用原请求重试。</p><ElButton :disabled="busy" @click="query">查询原操作</ElButton><ElButton :disabled="busy" @click="run(deliver)">原样重试</ElButton></ElCard>
        <div class="editor-actions"><ElButton v-show="advanced" :disabled="locked || !dirty" @click="validate">仅预检</ElButton><ElButton type="primary" :disabled="locked || !dirty" @click="save">保存到磁盘</ElButton><ElButton :disabled="locked || dirty || !view.exists" @click="apply">{{ view.application === 'RESTART' ? '确认重启要求' : '应用磁盘配置' }}</ElButton><ElButton v-if="dirty" :disabled="busy" @click="discard">放弃修改</ElButton><RefreshButton v-else :disabled="busy" label="重新读取配置文件" @click="discard" /></div>
        <ElCard v-if="receipt" shadow="never" class="operation-receipt"><h3>{{ statusNames[receipt.status] ?? receipt.status }}</h3><p>{{ receipt.note }}</p><p>操作编号：{{ receipt.operationId }}</p><p>{{ new Date(receipt.at).toLocaleString('zh-CN') }}{{ receipt.replayed ? ' · 原结果重放' : '' }} · 历史回执不代表当前进程或磁盘仍是同一状态。</p><ElButton :disabled="busy" @click="query">查询此回执</ElButton></ElCard>
      </div>
    </div>
  </div>
</template>
