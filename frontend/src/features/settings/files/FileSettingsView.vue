<script setup lang="ts">
// 2026/11/10：插件归属统一优先展示名称，稳定 ID 保留在悬停说明及筛选中。
import PluginLabel from '../../../shared/plugins/PluginLabel.vue'
// 2026/11/07：按功能目录引用共享能力与页面契约。
// 2026/11/04：重新读取采用统一图标，放弃修改仍明确显示动作文字。
import RefreshButton from '../../../shared/components/RefreshButton.vue'
import {computed, onBeforeUnmount, onMounted, reactive, ref, watch} from 'vue'
import {ElAlert, ElButton, ElCard, ElDialog, ElEmpty, ElInput, ElTag} from 'element-plus'
import {ApiError} from '../../../shared/api/client'
import {useConsolePreferences} from '../../../shared/state/console-preferences'
import type {FileField as Field} from './FileFieldEditor.vue'
import FileFieldEditor from './FileFieldEditor.vue'
import type {ModelCatalog} from '../../models/ModelTemplateEditor.vue'
import ModelTemplateEditor from '../../models/ModelTemplateEditor.vue'
import ModelNamePicker from '../../models/ModelNamePicker.vue'
import ModelIcon from '../../models/ModelIcon.vue'
import {modelConfigurationApplied} from '../../models/model-configuration-state'
import {usePageDraft} from '../../../shared/lifecycle/page-guards'
import type {PageIntent} from '../../../app/pages/page-contracts'
import PluginFilter from '../../../shared/plugins/PluginFilter.vue'
import {
  pluginAvailability,
  pluginEnabled,
  PluginRequestSuperseded,
  usePluginRequests
} from '../../../shared/plugins/plugin-console'
import {usePluginDialogs} from '../../../shared/plugins/plugin-dialogs'

interface Stamp { revision: string; generation: number; current: boolean }
interface Collection { path: string; label: string; maximum: number }
interface Section { pluginId?: string | null; id: string; name: string; application: string; description?: string }
interface View extends Section { revision: string; exists: boolean; runtime: Stamp; fields: Field[]; collections: Collection[]; collectionSizes: Record<string, number> }
interface Edit { path: string; action: string; value?: unknown }
interface Receipt { operationId: string; status: string; diskRevision: string; runtime: Stamp; note: string; fields: string[]; at: string; replayed: boolean }
interface Pending { id: string; action: 'save' | 'apply'; body: { operationId: string; expectedRevision: string; edits?: Edit[] } }
const emit = defineEmits<{ expired: [] }>()
const props = withDefaults(defineProps<{modelMode?: boolean}>(), {modelMode: false})
// 2026/11/07：文件读取、保存回执和检测均不得跨越插件停启或页面销毁。
const {request, capture} = usePluginRequests()
const {advanced} = useConsolePreferences()
const sections = ref<Section[]>([])
const pluginId = ref('')
const visibleSections = computed(() => sections.value.filter(item => pluginEnabled(item.pluginId) && (!pluginId.value || item.pluginId === pluginId.value)))
const view = ref<View>()
const ElMessageBox = usePluginDialogs(() => view.value?.pluginId)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const receipt = ref<Receipt>()
// 2026/11/05：敏感值与原请求仅留在当前组件内存，退出或卸载后释放，不写入浏览器存储。
const pending = ref<Pending>()
const drafts = reactive<Record<string, { action: string; text: string }>>({})
const structure = ref<Edit>()
// 2026/11/04：模型专页复用同一文件事务与回执，只改变模型字段的呈现和样板创建方式。
const modelCatalog = ref<ModelCatalog>({routes: [], inputs: []})
const modelDialogOpen = ref(false)
// 2026/11/05：检测只绑定已保存的文件版本，修改后不沿用旧成功标记。
interface Probe { revision: string; status: string; reason: string }
const probes = reactive<Record<string, Probe>>({})
const probing = ref('')
const editingModelIndex = ref(0)
const fieldErrors = reactive<Record<string, string>>({})
const rootFields = computed(() => props.modelMode ? view.value?.fields.filter(field => !field.path.startsWith('/models/')) ?? [] : view.value?.fields ?? [])
const modelGroups = computed(() => Array.from({length: view.value?.collectionSizes['/models'] ?? 0}, (_, index) => ({index,
  fields: view.value!.fields.filter(field => field.path.startsWith(`/models/${index}/`)),
  id: modelValue(index, 'id'), adapter: modelValue(index, 'adapter') || modelValue(index, 'id')}))
  // 2026/11/10：只调整展示顺序，编辑与删除继续使用原文件索引。
  .sort((left, right) => Number(right.id === drafts['/default-model-id']?.text) - Number(left.id === drafts['/default-model-id']?.text)))
const editingGroup = computed(() => modelGroups.value.find(group => group.index === editingModelIndex.value))
function modelValue(index: number, name: string) {
  return drafts[`/models/${index}/${name}`]?.text ?? String(view.value?.fields.find(field => field.path === `/models/${index}/${name}`)?.value ?? '')
}
function modelType(adapter: string) { return modelCatalog.value.routes.find(route => route.id === adapter) }
function editModel(index: number) {
  editingModelIndex.value = index
  delete probes[modelValue(index, 'id')]
  modelDialogOpen.value = true
}

/** 2026/11/05：显式检测已保存配置，迟到响应不得覆盖新文件版本。 */
async function probeModel(id: string) {
  if (!view.value || dirty.value || probing.value) return
  const revision = view.value.revision
  probing.value = id
  try {
    const result = await request<Probe>(`model-configurations/${encodeURIComponent(id)}/probe?revision=${encodeURIComponent(revision)}`, {method: 'POST'})
    if (view.value?.revision === revision) probes[id] = result
  } catch (failure) {
    if (failure instanceof PluginRequestSuperseded) return
    if (view.value?.revision === revision) probes[id] = {revision, status: 'FAILED', reason: failure instanceof Error ? failure.message : '模型检测失败。'}
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
  } finally { probing.value = '' }
}
const configuredRoutes = computed(() => modelGroups.value.map(group => group.id))
const modelStructuralLocked = computed(() => locked.value || !!structure.value || Object.entries(drafts).some(([path, value]) => path.startsWith('/models/') && value.action !== 'KEEP'))
const basicModelFields = new Set(['id', 'adapter', 'model', 'api-key', 'base-url', 'api-path', 'max-tokens', 'context-window-tokens', 'context-input-ratio', 'image-input'])
// 2026/11/05：连接配置置于路由和模型参数之前，其余字段继续沿用原始声明顺序。
const modelFieldOrder: Record<string, number> = {adapter: 0, 'base-url': 1, 'api-path': 2, id: 3}
const modelHelp: Record<string, string> = {
  'default-model-id': '保存后自动应用；继承默认模型的聊天与后台任务从新请求开始使用此路由。',
  id: '唯一配置路由；同一类型可以添加多条，标识不能重复。',
  adapter: '该路由实际使用的协议适配器；旧条目未填写时使用同名适配器。',
  model: '供应商的具体模型名称。可使用适配器默认模型，也可填写兼容的自定义模型。',
  'api-key': '原密钥不会回显；替换时可手动输入，或选择环境变量并只填写变量名。',
  'max-tokens': '本路由每次模型调用的最大输出额度。业务模块直接使用此值，不另设隐含上限。',
  'context-window-tokens': '具体模型的真实上下文总容量，计入输入与输出；更换模型后请一并核对。',
  'context-input-ratio': '输入最多占上下文的比例，默认 0.8；同时仍为完整输出额度预留空间。',
  'base-url': '模型服务基础地址；显示原始声明或环境模板，不展开环境变量。',
  'api-path': '相对于基础地址的接口路径；供应商样板已经提供默认值。',
  metadata: '供应商扩展参数，使用 JSON 对象。凭据和连接设置应使用各自字段。',
}

function displayField(field: Field): Field {
  if (!props.modelMode) return field
  const name = field.path.split('/').at(-1)!
  return {...field, description: modelHelp[name] ?? field.description}
}
function choices(field: Field) {
  if (!props.modelMode) return undefined
  if (field.path === '/default-model-id') return modelGroups.value.map(group => ({value: group.id, label: `${group.id} · ${modelValue(group.index, 'model')}`}))
  if (field.path.endsWith('/adapter')) return modelCatalog.value.routes.map(route => ({value: route.id, label: route.name}))
  return undefined
}
/**
 * 2026/11/05：优先展示适配器、服务地址和接口路径，保留高级字段的原显示边界。
 * @param fields 当前模型的原字段列表
 * @return 当前模式可见且按连接优先排列的字段
 */
function modelFields(fields: Field[]) {
  return fields.filter(field => advanced.value || basicModelFields.has(field.path.split('/').at(-1)!))
    .sort((left, right) => (modelFieldOrder[left.path.split('/').at(-1)!] ?? 4)
      - (modelFieldOrder[right.path.split('/').at(-1)!] ?? 4))
}
// 2026/11/04：已保存模型也使用同一供应商目录，选择已知型号时同步真实上下文容量。
function selectModel(index: number, value: string) {
  const route = modelType(modelGroups.value.find(group => group.index === index)?.adapter ?? '')
  const capacity = route?.contextWindows?.[value]
  const field = view.value?.fields.find(item => item.path === `/models/${index}/context-window-tokens`)
  if (capacity && field) editField(field, String(capacity))
}
async function appendModel(value: Record<string, unknown>, makeDefault: boolean) {
  structure.value = {path: '/models', action: 'APPEND', value}
  if (makeDefault && drafts['/default-model-id']) {
    drafts['/default-model-id'] = {action: 'SET', text: String(value.id)}
  }
  await saveModel()
}
const appendPath = ref('')
const appendText = ref('{}')
const dirty = computed(() => !!structure.value || Object.values(drafts).some(item => item.action !== 'KEEP'))
// 2026/11/06：保留页切换不丢草稿，退出会话前纳入宿主统一检查。
usePageDraft(() => ({dirty: dirty.value, pending: !!pending.value, busy: busy.value || !!probing.value}))
const locked = computed(() => busy.value || !!pending.value)
const statusNames: Record<string, string> = { PREPARED: '尚待确认', SAVED: '已保存，尚未应用', APPLIED: '本进程应用已确认', APPLY_FAILED: '应用未确认', RESTART_REQUIRED: '需要重启', INDETERMINATE: '保存结果无法确认', NOT_SAVED: '原文件未替换' }
// 2026/11/05：摘要沿用字段名称区分密钥与其他敏感值，不再使用“秘密”动作名称。
const actionNames: Record<string, string> = { SET: '设置', REMOVE: '移除声明', REPLACE_SECRET: '替换', CLEAR_SECRET: '清除', APPEND: '新增条目' }
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
  for (const path of Object.keys(fieldErrors)) delete fieldErrors[path]
}

// 2026/11/05：普通字段直接编辑，恢复原文时撤销该字段变更；敏感值仍必须显式选择动作。
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
    if (failure instanceof PluginRequestSuperseded) return
    error.value = failure instanceof Error ? failure.message : '文件配置操作未完成。'
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
  } finally { busy.value = false }
}

async function confirm(message: string): Promise<boolean> {
  const current = capture()
  try { await ElMessageBox.confirm(message, '确认文件操作', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }); return current() }
  catch { return false }
}

async function load(id: string) {
  const loaded = await request<View>(`file-configs/${encodeURIComponent(id)}`)
  // 2026/11/05：保存或外部编辑改变文件版本后，所有旧检测结果失效。
  if (view.value?.revision !== loaded.revision) for (const id of Object.keys(probes)) delete probes[id]
  if (!pluginEnabled(loaded.pluginId)) return
  view.value = loaded
  resetDrafts()
  if (props.modelMode) {
    modelCatalog.value = {routes: [], inputs: []}
    modelCatalog.value = await request<ModelCatalog>('model-configurations')
  }
}

async function select(id: string) {
  if ((dirty.value || pending.value) && !await confirm('切换将丢弃当前草稿和内存中的原请求。未确认操作仍可能已经完成，请先查询回执。')) return
  await run(async () => { await load(id); pending.value = undefined; receipt.value = undefined; notice.value = '' })
}

function edits(): Edit[] {
  const inputError = Object.values(fieldErrors).find(value => value)
  if (inputError) throw new Error(inputError)
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

/** 2026/11/05：明确拒绝保存时恢复草稿；连接中断和未知结果继续保留原请求与操作编号。 */
async function deliver() {
  const original = pending.value!
  let result: Receipt
  try {
    result = await request<Receipt>(`file-configs/${original.id}/actions/${original.action}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(original.body) })
  } catch (failure) {
    if (original.action === 'save' && failure instanceof ApiError && failure.status === 400) {
      for (const edit of original.body.edits ?? []) {
        if (edit.action === 'REPLACE_SECRET') {
          // 2026/11/05：metadata 等敏感 JSON 也要恢复为可编辑原结构，不转成对象占位文字。
          drafts[edit.path]!.text = typeof edit.value === 'string' ? edit.value : JSON.stringify(edit.value, null, 2)
        }
      }
      pending.value = undefined
    }
    throw failure
  }
  await receive(result)
}

async function receive(result: Receipt) {
  // 2026/11/10：自动应用只接续本页面已确认的保存操作，固定原磁盘版本；未知结果仍查询原编号。
  const operation = pending.value?.body.operationId ?? receipt.value?.operationId
  if (!result || result.operationId !== operation || typeof result.diskRevision !== 'string' || !result.diskRevision
      || !['PREPARED', 'SAVED', 'APPLIED', 'RESTART_REQUIRED', 'APPLY_FAILED', 'INDETERMINATE', 'NOT_SAVED'].includes(result.status)) {
    throw new Error('文件操作回执无效或编号不匹配，请查询原操作结果。')
  }
  const savedModel = props.modelMode && pending.value?.action === 'save' && result.status === 'SAVED'
  receipt.value = result
  if (['SAVED', 'APPLIED', 'RESTART_REQUIRED', 'APPLY_FAILED', 'INDETERMINATE'].includes(result.status)) {
    // 2026/11/05：保留原操作编号供后续查询，但立即释放包含敏感值的请求体。
    pending.value = undefined
    if (props.modelMode && result.status === 'APPLY_FAILED') error.value = '模型已保存，但应用失败；LLM 仍未确认使用新配置。' + result.note
    if (result.status === 'INDETERMINATE') error.value = '文件操作结果尚未确认，请查询原操作回执。'
    if (result.status === 'APPLIED' && props.modelMode) modelConfigurationApplied(result.diskRevision)
    if (savedModel) {
      pending.value = {id: view.value!.id, action: 'apply', body: {operationId: crypto.randomUUID(), expectedRevision: result.diskRevision}}
      await deliver()
      return
    }
    await load(view.value!.id)
  }
}

async function save() {
  // 2026/11/05：通用文件保存说明覆盖所有敏感字段，不把密码等字段误称为密钥。
  if (!await confirm('只保存明确修改的字段；未修改的敏感值、环境模板和未知字段保留。YAML 会重新排版，原注释不会保留。'
    + (props.modelMode ? '模型保存成功后自动应用，新请求使用新配置。' : '保存完成后仍须单独应用。'))) return
  await run(commitEdits)
}

/** 2026/11/10：模型编辑完成后保存并应用；应用失败保留真实回执，不能报告运行配置已更新。 */
async function saveModel() {
  if (!dirty.value) { modelDialogOpen.value = false; return }
  await run(async () => {
    await commitEdits()
    if (receipt.value?.status === 'APPLIED') {
      modelDialogOpen.value = false
      notice.value = '模型已保存并应用；新模型已加入 LLM 选择，继承默认的聊天从新请求开始使用当前默认模型。'
    }
  })
}

/** 2026/11/05：模型弹窗与普通文件编辑共用版本仲裁及原回执恢复。 */
async function commitEdits() {
    pending.value = { id: view.value!.id, action: 'save', body: { operationId: crypto.randomUUID(), expectedRevision: view.value!.revision, edits: edits() } }
    for (const field of view.value!.fields.filter(item => item.secret)) drafts[field.path]!.text = ''
    appendText.value = '{}'
    notice.value = ''
    await deliver()
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
  if (!await confirm(`删除“${collection.label}”第 ${index + 1} 项？${props.modelMode ? '确认后立即保存并应用。' : '还需保存后才会修改磁盘。'}`)) return
  structure.value = { path: `${collection.path}/${index}`, action: 'REMOVE' }
  if (props.modelMode) await saveModel()
}

function fieldLocked(path: string) {
  const collection = view.value?.collections.find(item => path.startsWith(`${item.path}/`))
  return !!(structure.value && collection && (structure.value.path === collection.path || structure.value.path.startsWith(`${collection.path}/`)))
}

function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value || pending.value) { event.preventDefault(); event.returnValue = '' }
}

// 2026/11/07：归属失效即清理本插件表单和秘密草稿，其他插件草稿不受影响。
watch(pluginAvailability, () => {
  if (view.value && !pluginEnabled(view.value.pluginId)) {
    view.value = undefined; modelDialogOpen.value = false; pending.value = undefined; resetDrafts()
    receipt.value = undefined; notice.value = ''; error.value = ''; modelCatalog.value = {routes: [], inputs: []}
    for (const id of Object.keys(probes)) delete probes[id]
  }
  void loadSections().catch(failure => {
    if (failure instanceof PluginRequestSuperseded) return
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
    error.value = failure instanceof Error ? failure.message : '配置目录暂不可用。'
  })
}, {flush: 'sync'})
/** 2026/11/07：启停只刷新目录，既有有效草稿继续保留，重新启用后由用户明确选择配置。 */
async function loadSections() {
  sections.value = (await request<Section[]>('file-configs')).filter(section => props.modelMode ? section.id === 'agent.models' : section.id !== 'agent.models')
}
let initialization: Promise<void> = Promise.resolve()
async function open(intent: PageIntent): Promise<boolean> {
  await initialization
  if (intent.kind !== 'plugin') return false
  if ((dirty.value || pending.value) && !await confirm('切换插件配置将放弃当前未保存内容，是否继续？')) return false
  pluginId.value = intent.pluginId
  await loadSections()
  const selected = visibleSections.value.find(item => item.id === intent.section) ?? visibleSections.value[0]
  if (selected) await load(selected.id)
  else { view.value = undefined; resetDrafts(); pending.value = undefined }
  return true
}
defineExpose({open})

onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
  initialization = run(async () => {
    await loadSections()
    if (sections.value[0]) await load(sections.value[0].id)
  })
})
onBeforeUnmount(() => { window.removeEventListener('beforeunload', beforeUnload); view.value = undefined; pending.value = undefined; resetDrafts() })
</script>

<template>
  <!-- 2026/11/05：直接编辑普通字段，敏感值动作保持显式，原提交确认流程继续可见。 -->
  <div class="file-settings">
    <ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <ElAlert v-if="notice" :title="notice" type="success" :closable="false" />
    <div class="settings-columns" :class="{'model-settings-columns': modelMode}">
      <ElCard v-if="!modelMode" shadow="never" class="target-list"><h2>本地配置</h2><PluginFilter v-model="pluginId" :items="sections" />
        <button v-for="section in visibleSections" :key="section.id" class="target-button" :class="{ selected: view?.id === section.id }" :disabled="busy" @click="select(section.id)"><strong>{{ section.name }}</strong><code v-if="section.pluginId"><PluginLabel :plugin-id="section.pluginId" /></code><span>{{ section.application === 'RESTART' ? '保存后需重启' : '支持本进程应用' }}</span></button>
        <ElEmpty v-if="!visibleSections.length && !busy" description="没有已开放的配置区" />
      </ElCard>
      <div v-if="view && pluginEnabled(view.pluginId) && (!pluginId || view.pluginId === pluginId)" class="settings-editor">
        <ElCard shadow="never"><h2>{{ view.name }}</h2><code v-if="view.pluginId"><PluginLabel :plugin-id="view.pluginId" /></code>
          <p v-if="view.description" class="config-description file-explanation">{{ view.description }}</p>
          <ElTag :type="view.runtime.current && view.runtime.revision === view.revision ? 'success' : 'warning'">{{ view.runtime.current && view.runtime.revision === view.revision ? '运行与磁盘一致' : '磁盘与运行状态不同' }}</ElTag>
          <!-- 2026/11/04：文件与字段说明直接显示，版本等技术细节仍由高级模式控制。 -->
          <!-- 2026/11/10：模型页自动接续应用，普通文件页仍由用户单独应用。 -->
          <p class="config-description">{{ modelMode ? '保存成功后自动应用，新请求使用新配置。' : '保存写入磁盘后仍需单独应用。' }}移除声明会恢复默认行为；预检不会请求远端 API。</p><p v-if="advanced" class="small revision">{{ view.exists ? '磁盘文件已存在' : '当前是未保存的模板' }} · 运行代次 {{ view.runtime.generation }}<br />磁盘版本：{{ view.revision }}<br />运行版本：{{ view.runtime.revision }}</p>
          <FileFieldEditor v-for="field in rootFields" :key="field.path" :field="displayField(field)" :draft="drafts[field.path]!" :disabled="locked || fieldLocked(field.path)" :advanced="advanced" :choices="choices(field)" @action="value => drafts[field.path]!.action = value" @text="value => editField(field, value)" />
        </ElCard>
        <div v-if="modelMode" class="editor-heading model-directory-toolbar"><h3>已添加模型 · {{ modelGroups.length }}</h3><ModelTemplateEditor :catalog="modelCatalog" :configured="configuredRoutes" :disabled="modelStructuralLocked || (view.collectionSizes['/models'] ?? 0) >= 64" :has-default="!!drafts['/default-model-id']?.text" @append="appendModel" /></div><p v-if="modelMode && structure" class="small muted">模型条目已加入草稿，保存成功后将自动应用。</p>
        <!-- 2026/11/04：摘要目录不挂载全部表单；仅编辑选中模型，原文件草稿与事务保持统一。 -->
        <!-- 2026/11/05：编辑与真实检测独立，卡片只显示当前文件版本的结果。 -->
        <div v-if="modelMode" class="model-config-grid"><article v-for="group in modelGroups" :key="group.index" class="model-summary-card">
          <button class="model-summary-open" :disabled="locked || !!probing" @click="editModel(group.index)"><div class="model-summary-heading"><ModelIcon :icon="modelType(group.adapter)?.icon" :name="modelType(group.adapter)?.name ?? group.adapter" /><strong>{{ group.id || '未命名路由' }}</strong><ElTag v-if="drafts['/default-model-id']?.text === group.id" size="small" type="success">默认</ElTag></div><p>{{ modelValue(group.index, 'model') || '尚未填写型号' }}</p><small class="muted">{{ modelType(group.adapter)?.name ?? group.adapter }} · 输出 {{ modelValue(group.index, 'max-tokens') }} token</small><small class="muted">上下文 {{ modelValue(group.index, 'context-window-tokens') }} token</small></button>
          <div class="model-probe-actions"><ElTag :type="probes[group.id]?.status === 'SUCCESS' ? 'success' : probes[group.id]?.status === 'FAILED' ? 'danger' : 'info'">{{ probing === group.id ? '检测中' : probes[group.id]?.status === 'SUCCESS' ? '检测成功' : probes[group.id]?.status === 'FAILED' ? '检测失败' : '未检测' }}</ElTag><ElButton :disabled="locked || dirty || !!probing" :loading="probing === group.id" @click="probeModel(group.id)">检测模型</ElButton></div>
          <p v-if="probes[group.id]?.status === 'FAILED'" class="small model-probe-error">{{ probes[group.id]?.reason }}</p></article></div>
        <ElDialog v-model="modelDialogOpen" title="编辑模型" width="min(760px, 95vw)" :close-on-click-modal="false"><ElAlert v-if="error" :title="error" type="error" :closable="false" /><p class="small muted">点击完成编辑将立即保存。YAML 会重新排版，原注释不保留；未修改的密钥和环境模板保留。保存成功后自动应用，新请求使用新配置。</p>
        <ElCard v-for="group in editingGroup ? [editingGroup] : []" :key="group.index" shadow="never" class="model-config-card"><div class="editor-heading"><h3>{{ modelType(group.adapter)?.name ?? (group.id || '未命名模型') }}</h3><ElButton type="danger" text :disabled="modelStructuralLocked" @click="remove({path: '/models', label: '模型', maximum: 64}, group.index)">删除模型</ElButton></div>
          <p class="small muted">服务地址和接口路径可直接查看与修改；检测会发起一次真实模型请求。</p>
          <template v-for="field in modelFields(group.fields)" :key="field.path"><div v-if="field.path.endsWith('/model')" class="settings-field"><div><label>{{ field.label }}</label><p class="config-description">{{ displayField(field).description }}</p></div><ModelNamePicker :model-value="drafts[field.path]!.text" :names="modelType(group.adapter)?.models ?? []" :disabled="locked || fieldLocked(field.path)" :label="field.label" @update:model-value="value => editField(field, value)" @select="value => selectModel(group.index, value)" /></div><FileFieldEditor v-else :field="displayField(field)" :draft="drafts[field.path]!" :disabled="locked || fieldLocked(field.path)" :advanced="advanced" :inputs="modelCatalog.inputs" :choices="choices(field)" @action="value => drafts[field.path]!.action = value" @text="value => editField(field, value)" @error="value => fieldErrors[field.path] = value" /></template>
          <ElAlert v-for="message in Object.entries(fieldErrors).filter(([path, value]) => path.startsWith(`/models/${group.index}/`) && value).map(([, value]) => value)" :key="message" :title="message" type="error" :closable="false" />
        </ElCard>
        <template #footer><ElButton type="primary" :disabled="locked" :loading="busy" @click="saveModel">完成编辑并保存</ElButton></template></ElDialog>
        <ElCard v-for="collection in modelMode ? [] : view.collections" :key="collection.path" shadow="never"><h3>{{ collection.label }} · {{ view.collectionSizes[collection.path] ?? 0 }} / {{ collection.maximum }}</h3>
          <p class="config-description">集合增删需单独保存。新增条目应包含本模块必填字段；模型样板请到“模型配置”选择。</p>
          <div class="editor-actions"><template v-if="advanced"><ElButton v-for="index in (view.collectionSizes[collection.path] ?? 0)" :key="index" :disabled="locked || !!structure" @click="remove(collection, index - 1)">删除第 {{ index }} 项</ElButton></template>
            <ElButton :disabled="locked || !!structure || (view.collectionSizes[collection.path] ?? 0) >= collection.maximum" @click="appendPath = collection.path">新增条目</ElButton></div>
          <div v-if="appendPath === collection.path" class="file-append"><ElInput v-model="appendText" type="textarea" :rows="5" aria-label="新增配置条目 JSON" :disabled="locked" /><ElButton :disabled="locked" @click="append">加入本次修改</ElButton></div>
        </ElCard>
        <ElCard v-if="dirty" shadow="never"><h3>本次修改</h3><ul><li v-for="line in summary" :key="line" class="small revision">{{ line }}</li></ul><p class="small muted">保存将重新排版 YAML，原注释不会保留。未修改的值、环境模板和未知配置项保留。</p></ElCard>
        <ElCard v-if="pending" shadow="never" class="pending-operation"><h3>原操作尚待确认</h3><p class="small revision">操作编号：{{ pending.body.operationId }}</p><p class="small muted">连接中断不代表写入失败。先查询原回执；如尚无记录，可使用原请求重试。</p><ElButton :disabled="busy" @click="query">查询原操作</ElButton><ElButton :disabled="busy" @click="run(deliver)">原样重试</ElButton></ElCard>
        <div class="editor-actions"><ElButton v-show="advanced" :disabled="locked || !dirty" @click="validate">仅预检</ElButton><ElButton type="primary" :disabled="locked || !dirty" @click="save">{{ modelMode ? '保存并应用' : '保存到磁盘' }}</ElButton><ElButton :disabled="locked || dirty || !view.exists" @click="apply">{{ view.application === 'RESTART' ? '确认重启要求' : '应用磁盘配置' }}</ElButton><ElButton v-if="dirty" :disabled="busy" @click="discard">放弃修改</ElButton><RefreshButton v-else :disabled="busy" label="重新读取配置文件" @click="discard" /></div>
        <ElCard v-if="receipt && (advanced || receipt.status === 'INDETERMINATE')" shadow="never" class="operation-receipt"><h3>{{ statusNames[receipt.status] ?? receipt.status }}</h3><p>{{ receipt.note }}</p><p>操作编号：{{ receipt.operationId }}</p><p>{{ new Date(receipt.at).toLocaleString('zh-CN') }}{{ receipt.replayed ? ' · 原结果重放' : '' }} · 历史回执不代表当前进程或磁盘仍是同一状态。</p><ElButton :disabled="busy" @click="query">查询此回执</ElButton></ElCard>
      </div>
      <!-- 2026/11/07：系统模型配置只提示本机文件授权，不把缺失入口误导为业务插件开关。 -->
      <ElEmpty v-else-if="modelMode && !busy && !error" description="系统模型配置暂不可用，请检查本机文件配置授权是否包含 agent.models" />
    </div>
  </div>
</template>
