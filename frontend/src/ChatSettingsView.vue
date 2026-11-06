<script setup lang="ts">
// 2026/11/04：刷新类操作统一使用带完整动作提示的图标按钮。
import RefreshButton from './RefreshButton.vue'
import {computed, onBeforeUnmount, onMounted, reactive, ref, watch} from 'vue'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElCheckbox,
  ElEmpty,
  ElInput,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch
} from 'element-plus'
import type {Platform} from './api'
import {ApiError, request} from './api'
import DetailPanel from './DetailPanel.vue'
import ConsoleIcon from './ConsoleIcon.vue'
import AgentAssemblyEditor from './AgentAssemblyEditor.vue'
import ValueEditor from './ValueEditor.vue'
import {useConsolePreferences} from './console-preferences'
import type {AgentTarget} from './AgentWorkspaceView.vue'
import {usePageDraft} from './pages/page-guards'

interface Account { ref: string; name: string; platformId: string; observedAt: string }
interface Target { ref: string; kind: string; name: string; platformId: string; source: string; shared: boolean; observedAt: string }
interface Page { items: Target[]; next: string; importMore: boolean }
interface Section { id: string; name: string; group: string; description: string; registered?: boolean | null }
interface Field { name: string; label: string; group: string; kind: string; editor?: string; description: string; value: string; defaultValue: string; source: string; editable: boolean; choices: Record<string, string>; jsonValue?: boolean; nullable?: boolean }
interface Receipt { operationId: string; revision: string; registered: boolean; action: string; fields: string[]; at: string; replayed: boolean }
interface View { section: string; name: string; targetName: string; registered: boolean; revision: string; sharedExplanation: string; fields: Field[] }
interface Change { operationId: string; expectedRevision: string; action: string; fields: Record<string, string> }

const props = defineProps<{ clients: Platform[]; initialClient: string; fixedTarget?: AgentTarget; embedded?: boolean }>()
const emit = defineEmits<{ expired: []; inspect: [target: { client: string; account: string; target: string; name: string }]; console: [target: { client: string; account: string; target: string; name: string }] }>()
const client = ref('')
const account = ref('')
const accounts = ref<Account[]>([])
const targets = ref<Target[]>([])
const next = ref('')
// 2026/11/04：配置发现与目标分页独立，未扫描完的迁移数据提供明确的继续入口。
const importMore = ref(false)
const kind = ref('')
const search = ref('')
const selected = ref<Target>()
const sections = ref<Section[]>([])
const catalog = ref<Section[]>([])
const configGroup = ref('agent')
// 2026/11/04：登记状态来自当前聊天的实际读取，同状态保持原目录顺序，不改变当前选择。
const sectionOptions = computed(() => sections.value
  .filter(item => (item.group === 'agent') === (configGroup.value === 'agent'))
  .sort((left, right) => Number(right.registered === true) - Number(left.registered === true)))
const section = ref('agent')
// 2026/11/05：配置卡片按需展开；切换卡片继续保存各自草稿和原操作编号。
const cardOpen = ref(true)
const view = ref<View>()
const values = ref<Record<string, string>>({})
const busy = ref(false)
const error = ref('')
const notice = ref('')
const pending = ref<Change>()
// 2026/11/04：不允许在组件 JSON 编辑尚未完成时提交其他配置。
const assemblyValid = ref(true)
const lastReceipt = ref<Receipt>()
// 2026/11/04：未提交内容按真实目标与配置区分别保留，仅保存在当前组件内存。
interface Draft {view: View; values: Record<string, string>; pending?: Change; receipt?: Receipt}
// 2026/11/05：卡片草稿提示随独立保存、恢复和放弃实时更新。
const drafts = reactive(new Map<string, Draft>())
const lastSections: Record<string, string> = {agent: 'agent'}
const previousNonNull: Record<string, string> = {}
const draftKey = () => JSON.stringify([client.value, account.value, selected.value?.ref, section.value])
// 2026/11/04：简洁模式只收起 Agent 的次要选项，编辑值仍保留在完整草稿中。
const {advanced} = useConsolePreferences()
const showAllFields = ref(false)
const commonFields = new Set(['enabled', 'components', 'mode', 'failureReplyEnabled'])
const displayedFields = computed(() => view.value?.fields.filter(field => section.value !== 'agent' || advanced.value || showAllFields.value || commonFields.has(field.name)) ?? [])
const hiddenCount = computed(() => (view.value?.fields.length ?? 0) - displayedFields.value.length)
const hiddenDirtyCount = computed(() => view.value?.fields.filter(field => field.editable && values.value[field.name] !== field.value && !displayedFields.value.includes(field)).length ?? 0)
const available = computed(() => props.clients.filter(value => value.settingsAvailable))
const visibleTargets = computed(() => targets.value.filter(value => `${value.name} ${value.platformId}`.toLowerCase().includes(search.value.toLowerCase())))
const dirty = computed(() => !!view.value && view.value.fields.some(field => field.editable && values.value[field.name] !== field.value))
const groups = computed(() => [...new Set(displayedFields.value.map(field => field.group))])
const fieldsIn = (group: string) => displayedFields.value.filter(field => field.group === group)
const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })
const part = (value: string) => encodeURIComponent(value)
const defaultLabel = (field: Field) => field.jsonValue && field.defaultValue === 'null' ? '空值（null）' : field.kind === 'BOOLEAN' ? (field.defaultValue === 'true' ? '开启' : '关闭')
  : field.choices[displayValue(field, field.defaultValue)] ?? (displayValue(field, field.defaultValue) || '留空')
const targetPath = () => `clients/${part(client.value)}/accounts/${part(account.value)}/targets/${part(selected.value!.ref)}/settings/${part(section.value)}`
// 2026/11/04：跨页面定位等待目录初始化，保留原目标的草稿与待确认请求。
let ready: () => void
const initialized = new Promise<void>(resolve => { ready = resolve })

async function openTarget(target: AgentTarget) {
  await initialized
  if (busy.value || !await leaveDraft()) return
  await run(async () => {
    stash()
    clearEditor()
    client.value = target.client
    accounts.value = await request<Account[]>(`clients/${part(target.client)}/accounts`)
    if (!accounts.value.some(item => item.ref === target.account)) throw new Error('当前聊天账号已不可用，请刷新目录。')
    account.value = target.account
    kind.value = ''
    search.value = ''
    await loadTargets()
    selected.value = targets.value.find(item => item.ref === target.target)
      ?? {ref: target.target, name: target.name, kind: '', platformId: '', source: '', shared: false, observedAt: ''}
    await loadSections()
    configGroup.value = 'agent'
    section.value = sectionOptions.value[0]?.id ?? ''
    if (section.value) await restoreOrRead()
  })
}
// 2026/11/06：跨页客户端导航等待初始化，仍经过原编辑器的切换保护。
async function openClient(value: string) {
  await initialized
  await chooseClient(value)
}
defineExpose({openTarget, openClient})
usePageDraft(() => ({busy: busy.value, dirty: dirty.value || !!drafts.size, pending: !!pending.value}))

async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await action()
  } catch (failure) {
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
    error.value = failure instanceof Error ? failure.message : '暂时无法读取聊天设置。'
  } finally {
    busy.value = false
  }
}

async function leaveDraft(): Promise<boolean> {
  if (!dirty.value && !pending.value) return true
  try {
    await ElMessageBox.confirm(pending.value
      ? `原操作 ${pending.value.operationId} 尚待确认，会保留原请求。切换查看其他配置？`
      : '当前修改会作为本地草稿保留，切换查看其他配置？', '切换配置', { confirmButtonText: '保留并切换', cancelButtonText: '留在当前设置' })
    return true
  } catch { return false }
}

function stash() {
  if (view.value && (dirty.value || pending.value)) {
    drafts.set(draftKey(), {view: view.value, values: {...values.value}, pending: pending.value, receipt: lastReceipt.value})
  } else {
    drafts.delete(draftKey())
  }
}

async function restoreOrRead() {
  // 2026/11/04：已序列化草稿不携带上一组件弹窗尚未完成的输入状态。
  assemblyValid.value = true
  const saved = drafts.get(draftKey())
  if (saved) {
    view.value = saved.view
    values.value = {...saved.values}
    pending.value = saved.pending
    lastReceipt.value = saved.receipt
    notice.value = '已恢复本地草稿，保存时仍会检查原版本。'
  } else {
    await readView()
  }
}

function displayValue(field: Field, raw = values.value[field.name] ?? field.value): string {
  if (!field.jsonValue || field.kind === 'JSON') return raw
  if (raw === 'null') return ''
  if (!['TEXT', 'SELECT', 'LONG_TEXT'].includes(field.kind)) return raw
  const parsed: unknown = JSON.parse(raw || '""')
  return String(parsed)
}

function updateValue(field: Field, raw: string | boolean) {
  values.value[field.name] = field.jsonValue && ['TEXT', 'SELECT', 'LONG_TEXT'].includes(field.kind)
    ? JSON.stringify(raw) : String(raw)
}

function setNull(field: Field, checked: boolean) {
  const key = draftKey() + field.name
  if (checked) {
    previousNonNull[key] = values.value[field.name]!
    values.value[field.name] = 'null'
  } else {
    values.value[field.name] = previousNonNull[key] ?? (field.kind === 'TEXT' ? '""' : field.kind === 'BOOLEAN' ? 'false' : '')
  }
}

function clearEditor() {
  // 2026/11/05：切换聊天后默认展开新目标配置，不继承上一个目标的折叠状态。
  cardOpen.value = true
  assemblyValid.value = true
  // 2026/11/04：切换聊天时清除上一聊天的登记标记，全局目录的状态仍为未知。
  sections.value = catalog.value
  selected.value = undefined
  view.value = undefined
  values.value = {}
  pending.value = undefined
  lastReceipt.value = undefined
  notice.value = ''
}

async function loadTargets(more = false) {
  if (!account.value) { targets.value = []; next.value = ''; return }
  const query = new URLSearchParams({ after: more ? next.value : '', kind: kind.value })
  const result = await request<Page>(`clients/${part(client.value)}/accounts/${part(account.value)}/targets?${query}`)
  targets.value = more ? [...targets.value, ...result.items] : result.items
  next.value = result.next
  importMore.value = result.importMore
}

async function chooseClient(value: string) {
  if (value === client.value || !await leaveDraft()) return
  await run(async () => {
    stash()
    client.value = value
    clearEditor()
    sections.value = catalog.value
    accounts.value = await request<Account[]>(`clients/${part(value)}/accounts`)
    account.value = accounts.value[0]?.ref ?? ''
    await loadTargets()
  })
}

async function chooseAccount(value: string) {
  if (value === account.value || !await leaveDraft()) return
  await run(async () => { stash(); account.value = value; clearEditor(); sections.value = catalog.value; await loadTargets() })
}

async function readView() {
  const current = await request<View>(targetPath())
  assemblyValid.value = true
  view.value = current
  // 2026/11/04：保留原引用和草稿身份，只刷新当前目标的已知显示名称。
  if (selected.value) {
    selected.value = {...selected.value, name: current.targetName}
    targets.value = targets.value.map(item => item.ref === selected.value!.ref ? {...item, name: current.targetName} : item)
  }
  // 2026/11/04：登记、注销或重新读取后即刻更新排序，以服务端当前状态为准。
  sections.value = sections.value.map(item => item.id === current.section ? {...item, registered: current.registered} : item)
  values.value = Object.fromEntries(current.fields.map(field => [field.name, field.value]))
  drafts.delete(draftKey())
}

/** 2026/11/04：只读取当前聊天的配置目录和登记状态，不触碰已编辑的字段草稿。 */
async function loadSections() {
  sections.value = await request<Section[]>(`clients/${part(client.value)}/accounts/${part(account.value)}/targets/${part(selected.value!.ref)}/settings`)
}

async function chooseTarget(value: Target) {
  if (selected.value?.ref === value.ref || !await leaveDraft()) return
  await run(async () => {
    stash()
    clearEditor()
    selected.value = value
    await loadSections()
    section.value = sectionOptions.value.find(item => item.id === lastSections[configGroup.value])?.id ?? sectionOptions.value[0]?.id ?? ''
    if (section.value) await restoreOrRead()
  })
}

async function chooseSection(value: string) {
  if (busy.value) return
  if (value === section.value) { cardOpen.value = !cardOpen.value; return }
  await run(async () => {
    stash()
    section.value = value
    cardOpen.value = true
    lastSections[configGroup.value] = value
    // 2026/11/04：读失败时不能把上一配置表单用于新配置路径。
    view.value = undefined
    values.value = {}
    notice.value = ''
    pending.value = undefined
    lastReceipt.value = undefined
    await restoreOrRead()
  })
}

async function chooseGroup(value: string) {
  if (value === configGroup.value || !await leaveDraft()) return
  await run(async () => {
    stash()
    configGroup.value = value
    // 2026/11/05：切换配置分类后展开当前分类的首个配置。
    cardOpen.value = true
    section.value = sectionOptions.value.find(item => item.id === lastSections[value])?.id ?? sectionOptions.value[0]?.id ?? ''
    view.value = undefined
    values.value = {}
    pending.value = undefined
    lastReceipt.value = undefined
    if (selected.value && section.value) await restoreOrRead()
  })
}

async function reload() {
  if (dirty.value) {
    try { await ElMessageBox.confirm('放弃当前配置草稿并重新读取？', '重新读取', {confirmButtonText: '放弃并读取', cancelButtonText: '取消'}) }
    catch { return }
  }
  await run(readView)
}

async function discover() {
  await run(async () => {
    const discovered = await request<Account[]>(`clients/${part(client.value)}/actions/discover`, { method: 'POST' })
    // 2026/11/04：账号目录变化也须保留原目标草稿，清除旧目标与新账号的错误组合。
    if (!discovered.some(value => value.ref === account.value)) {
      if (!await leaveDraft()) return
      stash()
      clearEditor()
      account.value = discovered[0]?.ref ?? ''
      sections.value = catalog.value
    }
    accounts.value = discovered
    await loadTargets()
    // 2026/11/04：手动更新目录也同步登记标记，仍保留当前字段草稿及原版本。
    if (selected.value) await loadSections()
    notice.value = '已更新已知目录。在线且账号唯一就绪时同步平台联系人；历史目录不代表当前全部联系人。'
  })
}

async function submit(action: string) {
  if (!view.value) return
  if (action !== 'PATCH') {
    try {
      await ElMessageBox.confirm(action === 'REGISTER'
        ? `为当前聊天登记“${view.value.name}”，使用类型默认值创建。请确认共享影响。`
        : `仅注销当前聊天的“${view.value.name}”配置，不删除聊天、历史、记忆或其他配置。`,
      action === 'REGISTER' ? '登记配置' : '注销配置', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' })
    } catch { return }
  }
  const fields = action === 'PATCH' ? Object.fromEntries(view.value.fields
    .filter(field => field.editable && values.value[field.name] !== field.value)
    .map(field => [field.name, values.value[field.name]!])) : {}
  if (action === 'PATCH' && !Object.keys(fields).length) return
  for (const field of view.value.fields.filter(item => item.jsonValue && item.name in fields)) {
    try { JSON.parse(fields[field.name]!) }
    catch { error.value = `“${field.label}”需要合法的 JSON 值，请检查格式。`; return }
  }
  pending.value = { operationId: crypto.randomUUID(), expectedRevision: view.value.revision, action, fields }
  await resend()
}

async function resend() {
  if (!pending.value) return
  await run(async () => {
    lastReceipt.value = await request<Receipt>(targetPath(), {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(pending.value)
    })
    pending.value = undefined
    await readView()
    notice.value = '操作已真实提交，已重新读取当前配置。原操作回执保留二十四小时。'
  })
}

async function queryPending() {
  if (!pending.value) return
  await run(async () => {
    lastReceipt.value = await request<Receipt>(`${targetPath()}/operations/${part(pending.value!.operationId)}`)
    pending.value = undefined
    await readView()
    notice.value = '已查到原操作的真实回执，当前值已重新读取。'
  })
}

async function discardPending() {
  if (!pending.value) return
  try {
    await ElMessageBox.confirm(`这不会撤销可能已经提交的操作。请保存编号 ${pending.value.operationId}，必要时查询原结果。随后重新读取配置并放弃当前草稿。`,
      '重新读取当前状态', { type: 'warning', confirmButtonText: '放弃草稿并刷新', cancelButtonText: '保留原操作' })
  } catch { return }
  await run(async () => {
    await readView()
    pending.value = undefined
    notice.value = '已重新读取当前配置。此前操作如已提交，仍会保留原回执。'
  })
}

function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value || pending.value || drafts.size) { event.preventDefault(); event.returnValue = '' }
}

watch(() => props.initialClient, value => { if (value) void chooseClient(value) })
onMounted(async () => {
  window.addEventListener('beforeunload', beforeUnload)
  await run(async () => {
    catalog.value = await request<Section[]>('settings/sections')
    sections.value = catalog.value
    if (!sections.value.some(item => item.group === configGroup.value)) configGroup.value = 'general'
    section.value = sectionOptions.value[0]?.id ?? ''
  })
  // 2026/11/04：房间内配置直接绑定服务端目标，独立实例保留各房间草稿。
  if (props.fixedTarget) {
    ready()
    await openTarget(props.fixedTarget)
  } else {
    await chooseClient(props.initialClient || available.value[0]?.clientId || '')
    ready()
  }
})
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <!-- 2026/11/04：常用字段优先展示，隐藏修改和登记状态仍明确提示。 -->
  <div class="chat-settings">
    <div class="agent-tabs" role="tablist" aria-label="聊天配置分区">
      <button role="tab" :aria-selected="configGroup === 'agent'" :class="{selected: configGroup === 'agent'}" :disabled="busy" @click="chooseGroup('agent')">Agent 配置</button>
      <!-- 2026/11/04：功能分区与聊天类型都使用本地矢量图标辅助识别。 -->
      <button role="tab" :aria-selected="configGroup === 'general'" :class="{selected: configGroup !== 'agent'}" :disabled="busy" @click="chooseGroup('general')"><ConsoleIcon name="other" /> 其他功能配置</button>
    </div>
    <ElAlert v-if="!sections.length && !busy" title="当前没有可用的聊天配置，请检查客户端及插件是否已启用。" type="info" :closable="false" />
    <ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <ElAlert v-if="notice" :title="notice" type="success" :closable="true" @close="notice = ''" />
    <div v-if="!embedded" class="settings-toolbar">
      <label>聊天平台<ElSelect :model-value="client" :disabled="busy" @update:model-value="chooseClient">
        <ElOption v-for="item in available" :key="item.clientId" :value="item.clientId" :label="item.name" />
      </ElSelect></label>
      <label>机器人账号 / 历史目录<ElSelect :model-value="account" :disabled="busy" placeholder="尚未发现账号" @update:model-value="chooseAccount">
        <ElOption v-for="item in accounts" :key="item.ref" :value="item.ref" :label="item.name" />
      </ElSelect></label>
      <RefreshButton :disabled="busy || !client" :loading="busy" @click="discover" label="更新已知目录" />
    </div>
    <div class="settings-columns" :class="{'embedded-settings-columns': embedded}">
      <ElCard v-if="!embedded" shadow="never" class="target-list">
        <h2>群聊与私聊</h2>
        <ElSelect v-model="kind" :disabled="busy" placeholder="全部已知聊天" @change="run(() => loadTargets())">
          <template #prefix><ConsoleIcon :name="kind === 'GROUP' ? 'group' : kind === 'PRIVATE' ? 'private' : 'chat'" /></template>
          <ElOption value="" label="全部已知聊天"><ConsoleIcon name="chat" /> 全部已知聊天</ElOption>
          <ElOption value="GROUP" label="群聊"><ConsoleIcon name="group" /> 群聊</ElOption>
          <ElOption value="PRIVATE" label="私聊"><ConsoleIcon name="private" /> 私聊</ElOption>
        </ElSelect>
        <ElInput v-model="search" placeholder="筛选已加载的名称或号码" clearable />
        <ElEmpty v-if="!visibleTargets.length" description="暂无匹配的已知聊天" :image-size="70" />
        <button v-for="item in visibleTargets" :key="item.ref" class="target-button" :class="{ selected: selected?.ref === item.ref }"
          :disabled="busy" @click="chooseTarget(item)"><strong>{{ item.name }}</strong>
          <span class="chat-kind"><ConsoleIcon :name="item.kind === 'GROUP' ? 'group' : item.kind === 'PRIVATE' ? 'private' : 'other'" />{{ item.kind === 'GROUP' ? '群聊' : item.kind === 'PRIVATE' ? '私聊' : '其他' }} · {{ item.source === 'PLATFORM' ? '平台查询' : item.source === 'MESSAGE' ? '真实消息' : '已登记配置' }}</span></button>
        <ElButton v-if="next" text :disabled="busy" @click="run(() => loadTargets(true))">加载更多聊天</ElButton>
        <ElButton v-if="importMore" text :disabled="busy" @click="discover">继续发现已登记配置</ElButton>
        <DetailPanel title="目录说明"><p>离线时显示已知目录，可用“更新已知目录”向已连接的平台查询。</p></DetailPanel>
      </ElCard>
      <div class="settings-editor">
        <!-- 2026/11/04：读取失败时仍保留配置选择与重读入口，避免卡在上一份表单。 -->
        <ElEmpty v-if="!selected" description="选择一个聊天，查看与修改设置" />
        <!-- 2026/11/04：管理员直接编辑适用配置，空目录只说明当前客户端或插件未提供配置。 -->
        <ElEmpty v-else-if="!sectionOptions.length" description="这个聊天暂无适用的此类配置"><p class="small muted">这里只显示适用于当前客户端且所属插件已启用的聊天配置。</p></ElEmpty>
        <!-- 2026/11/05：每个配置独立成卡，操作只使用该卡的版本与字段，不提供跨卡批量保存。 -->
        <ElCard v-for="item in selected ? sectionOptions : []" :key="item.id" shadow="never" class="config-section-card">
          <button class="config-section-heading" :aria-expanded="section === item.id && cardOpen" :disabled="busy" @click="chooseSection(item.id)">
            <span><span v-if="item.registered === true" class="config-registered-dot" role="img" aria-label="已登记"></span><strong>{{ item.name }}</strong><span v-if="drafts.has(JSON.stringify([client, account, selected?.ref, item.id]))" class="small muted"> · 有草稿</span></span>
            <ConsoleIcon :name="section === item.id && cardOpen ? 'collapse' : 'expand'" />
          </button>
          <div v-if="section === item.id && cardOpen" class="config-section-content">
          <RefreshButton v-if="!view" :disabled="busy" @click="run(restoreOrRead)" label="重新读取配置" />
          <template v-if="view && selected">
          <!-- 2026/11/04：左侧已标明聊天，右侧只展示配置名称与登记状态。 -->
          <div class="editor-heading"><span class="connection-state">{{ view.registered ? '已登记' : '尚未登记' }}</span>
            <ElButton v-if="configGroup === 'agent'" :disabled="busy || !!pending" @click="emit('inspect', { client, account, target: selected.ref, name: view.targetName })">活动与记忆</ElButton>
            <ElButton v-show="advanced" :disabled="busy || !!pending" @click="emit('console', { client, account, target: selected.ref, name: view.targetName })">指令台</ElButton>
          </div>
          <p class="config-description">{{ sections.find(item => item.id === section)?.description }}</p>
          <p class="small muted">{{ view.sharedExplanation }}</p>
          <ElButton v-if="!view.registered" type="primary" :disabled="busy || !!pending" @click="submit('REGISTER')">登记此配置</ElButton>
          <template v-if="view.registered">
          <ElCard v-for="group in groups" :key="group" shadow="never" class="field-group"><h3>{{ group }}</h3>
            <div v-for="field in fieldsIn(group)" :key="field.name" class="settings-field">
              <div><label :for="'field-' + field.name">{{ field.label }}</label>
                <p class="config-description">{{ field.description }}</p><span v-if="field.kind !== 'ASSEMBLY'" class="field-source">{{ field.source }} · 默认：{{ defaultLabel(field) }}</span></div>
              <div class="config-field-input">
              <ElCheckbox v-if="field.jsonValue && field.nullable && field.kind !== 'JSON'" :model-value="values[field.name] === 'null'" :disabled="busy || !view.registered || !field.editable || !!pending" @update:model-value="value => setNull(field, !!value)">不设置（null）</ElCheckbox>
              <AgentAssemblyEditor v-if="field.kind === 'ASSEMBLY'" :model-value="values[field.name] ?? field.value" :disabled="busy || !!pending" @update:model-value="value => updateValue(field, value)" @expired="emit('expired')" @validity="value => assemblyValid = value" />
              <ElSwitch v-else-if="field.kind === 'BOOLEAN'" :id="'field-' + field.name" :model-value="values[field.name] === 'true'"
                @update:model-value="value => updateValue(field, !!value)" :disabled="busy || !view.registered || !field.editable || !!pending || field.jsonValue && values[field.name] === 'null'" />
              <ElSelect v-else-if="field.kind === 'SELECT'" :id="'field-' + field.name" :model-value="displayValue(field)" @update:model-value="value => updateValue(field, String(value))"
                :disabled="busy || !view.registered || !field.editable || !!pending || field.jsonValue && values[field.name] === 'null'">
                <ElOption v-for="(label, value) in field.choices" :key="value" :value="value" :label="label" />
              </ElSelect>
              <!-- 2026/11/06：只读空间绑定直接展示，不伪装成可手填的输入框。 -->
              <p v-else-if="section === 'agent' && field.name === 'memoryNamespace'" class="small">{{ displayValue(field) }}</p>
              <ValueEditor v-else-if="['JSON', 'LONG_TEXT'].includes(field.kind) || field.editor && field.editor !== 'AUTO'" :kind="field.editor && field.editor !== 'AUTO' ? field.editor : field.kind" :label="field.label" :model-value="displayValue(field)" :maxlength="8192" :disabled="busy || !field.editable || !!pending || field.jsonValue && values[field.name] === 'null' && field.kind !== 'JSON'" @update:model-value="value => updateValue(field, value)" />
              <ElInput v-else :id="'field-' + field.name" :model-value="displayValue(field)" @update:model-value="value => updateValue(field, value)"
                :type="['JSON', 'LONG_TEXT'].includes(field.kind) ? 'textarea' : field.kind === 'NUMBER' ? 'number' : 'text'" :rows="3"
                :disabled="busy || !view.registered || !field.editable || !!pending || field.jsonValue && values[field.name] === 'null' && field.kind !== 'JSON'" :maxlength="8192" />
              <ElButton v-if="advanced && field.editable" text :disabled="busy || !view.registered || !!pending" @click="values[field.name] = field.defaultValue">恢复默认值</ElButton>
              </div>
            </div>
          </ElCard>
          <ElButton v-if="hiddenCount" text @click="showAllFields = true">展开其余 {{ hiddenCount }} 项配置</ElButton>
          <ElButton v-else-if="showAllFields && !advanced && section === 'agent'" text @click="showAllFields = false">仅显示常用配置</ElButton>
          <div class="editor-actions">
            <ElButton type="primary" :disabled="busy || !dirty || !!pending || !assemblyValid" @click="submit('PATCH')">应用修改</ElButton>
            <RefreshButton :disabled="busy || !!pending" @click="reload" label="重新读取" />
            <ElButton type="danger" plain :disabled="busy || !!pending" @click="submit('UNREGISTER')">取消登记</ElButton>
            <span v-if="dirty" class="small muted">有未保存的修改{{ hiddenDirtyCount ? `（${hiddenDirtyCount} 项已折叠）` : '' }}</span>
          </div>
          </template>
          <ElCard v-if="pending" shadow="never"><h3>上次操作尚待确认</h3><p class="small">操作编号：{{ pending.operationId }}</p>
            <p class="small muted">失败或断开不证明未提交。请查询真实结果，或使用相同编号重试原请求。</p>
            <ElButton :disabled="busy" @click="queryPending">查询提交结果</ElButton><ElButton :disabled="busy" @click="resend">重试原请求</ElButton>
            <RefreshButton :disabled="busy" @click="discardPending" label="放弃草稿并刷新" /></ElCard>
          <ElCard v-if="advanced && lastReceipt" shadow="never" class="operation-receipt"><h3>真实操作回执</h3>
            <p>操作者：本地管理员 · {{ date(lastReceipt.at) }}{{ lastReceipt.replayed ? ' · 原结果重放' : '' }}</p>
            <DetailPanel><p class="small">操作编号：{{ lastReceipt.operationId }}</p></DetailPanel>
            <p class="small muted">{{ lastReceipt.action === 'PATCH' ? '修改字段：' + lastReceipt.fields.map(name => view?.fields.find(field => field.name === name)?.label ?? name).join('、') : lastReceipt.action === 'REGISTER' ? '登记完成' : '注销完成' }}</p></ElCard>
          </template>
          </div>
        </ElCard>
      </div>
    </div>
  </div>
</template>
