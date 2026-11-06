<script setup lang="ts">
// 2026/11/04：刷新类操作统一使用带完整动作提示的图标按钮。
import RefreshButton from './RefreshButton.vue'
// 2026/11/05：详细操作回执只在高级模式展示。
import {useConsolePreferences} from './console-preferences'
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {ElAlert, ElButton, ElCard, ElEmpty, ElInput, ElMessageBox, ElOption, ElSelect, ElSwitch} from 'element-plus'
import {ApiError, request} from './api'
import {confirmedReceipt, type ManagementReceipt as Receipt, type PendingManagement as Pending} from './management-receipts'
import DetailPanel from './DetailPanel.vue'
import ConsoleIcon from './ConsoleIcon.vue'
import AgentLibraryPicker from './AgentLibraryPicker.vue'
import ValueEditor from './ValueEditor.vue'
import {usePageDraft} from './pages/page-guards'
import {useActivePolling} from './pages/active-polling'

const {advanced} = useConsolePreferences()

export interface AgentTarget { client: string; account: string; target: string; name: string }
interface Evidence { id: string; revision: number; subjectId: string; text: string; kind: string; locked: boolean; createdAt: string; updatedAt: string; time: unknown; client: string; sourceScope: string }
interface Memory { namespace: string; generation: number; revision: number; exists: boolean; records: Evidence[]; next?: string }
interface Profile { subjectId: string; namespace: string; generation: number; complete: boolean; scanned: number; matched: number; records: Evidence[] }
interface LongItem { id: string; kind: string; subjectId: string | null; attribute: string; text: string; status: string; revision: number; time: string }
interface LongResult { items: LongItem[]; scanned: number; limited: boolean }
// 2026/11/05：整理作业显示安全状态和版本，恢复不等于立即完成模型调用。
interface Consolidation { revision: number; through: number; state: 'READY' | 'RUNNING' | 'BACKOFF' | 'PAUSED'; failures: number; invalidAttempts: number; reasonCode: string; failedAt: string | null; retryAt: string; inputFingerprint: string }
interface Layers { namespace: string; generation: number; compressedThrough: number; recent: { sequence: number; role: string; text: string; at: string; estimated: boolean; truncated: boolean }[]; episodes: { id: string; brief: string; details: string; from: string; through: string }[]; longTerm: LongResult; consolidation: Consolidation | null }
const consolidationStates = { READY: '等待整理', RUNNING: '正在整理', BACKOFF: '稍后重试', PAUSED: '重复无效生成，已暂停' }
// 2026/11/06：简洁视图说明固定失败原因，诊断代码保留在高级模式。
const consolidationReasons: Record<string, string> = {
  QUOTE_MISMATCH: '引用与原话不一致', SUBJECT_MISMATCH: '引用作者不一致', SEGMENT_MISMATCH: '引用不在所选片段内',
  EVIDENCE_ID_INVALID: '证据不属于本批', SEGMENT_OUTSIDE_BATCH: '片段不属于本批', TIME_HINT_MISMATCH: '时间不来自原话',
  STYLE_TARGET_MISMATCH: '表达观察目标不匹配', EVIDENCE_MISSING: '来源已不存在', EVIDENCE_CHANGED: '来源已变化',
  EVIDENCE_EXPIRED: '来源已到期', EVIDENCE_ROLE_INVALID: '来源不是真实用户', STYLE_TARGET_CHANGED: '来源人格已变化',
  INVALID_OUTPUT: '模型输出格式无效', OUTPUT_LIMIT: '模型输出被截断', INPUT_LIMIT: '输入超过预算',
  // 2026/11/06：存储格式故障不与模型无效候选混为一类。
  MEMORY_STORAGE_FORMAT: '持久记忆格式不匹配，请查看后台记录类型与字段',
  ACCESS_REVOKED: '当前配置或权限不允许整理', EXECUTION_DEADLINE_EXCEEDED: '整理超时',
  EXECUTION_CANCELLED: '整理已取消', EXECUTION_CLOSED: '整理已关闭', PROCESSING_FAILED: '整理失败，请查看后台同批次日志',
}
interface Correction { id: string; field: string; text: string; locked: boolean; occurredAt: string; origin: string }
interface Personality { name: string; rules: string[]; fields: string[]; key: string; bindingRevision: number; revision: number; current: boolean; backup: boolean; corrections: Correction[]; backups: string[] }
interface Event { sequence: number; requestId: string; activity: string; stage: string; state: string; at: string; metrics: Record<string, number> }
interface Timeline { instance: string; startedAt: string; reset: boolean; truncated: boolean; next: number; events: Event[] }
interface Schedule { id: string; title: string; action: string; state: string; revision: number; nextFire: string; zoneId: string; intervalSeconds: number; outcome: string; cancellable: boolean }
interface Schedules { items: Schedule[]; next: string }
const schedules = ref<Schedules>()
const scheduleStates: Record<string, string> = { ACTIVE: '等待执行', RUNNING: '已认领，动作执行中', COMPLETED: '已完成', CANCELLED: '未来计划已取消', EXPIRED: '已过期', PAUSED: '已暂停，需检查权限或执行器', INDETERMINATE: '结果不明，禁止自动重试' }

const props = defineProps<{ target?: AgentTarget; library?: {kind: 'memory' | 'personality'; key: string; name: string}; active: boolean; embedded?: boolean }>()
const emit = defineEmits<{ expired: []; selectTarget: [] }>()
const targetName = ref(props.target?.name ?? props.library?.name ?? '')
/** 2026/11/04：刷新名称使用真实目标引用，迟到结果不能替换其他聊天名称。 */
async function refreshName() {
  const selected = props.target
  if (!selected) return
  const result = await request<{name: string}>(`clients/${encodeURIComponent(selected.client)}/accounts/${encodeURIComponent(selected.account)}/targets/${encodeURIComponent(selected.target)}`)
  if (props.target?.client === selected.client && props.target?.account === selected.account && props.target?.target === selected.target) targetName.value = result.name
}
const panel = ref(props.library?.kind ?? 'activity')
const busy = ref(false)
const error = ref('')
const notice = ref('')
const auto = ref(true)
const events = ref<Event[]>([])
const timeline = ref<Timeline>()
const memory = ref<Memory>()
const namespace = ref(props.library?.kind === 'memory' ? props.library.key : '')
const subject = ref('')
const profile = ref<Profile>()
const layers = ref<Layers>()
const profileLearned = ref<LongResult>()
const topic = ref('')
const personality = ref<Personality>()
interface PersonalityBase {id: string; version: number; name: string; fields: string[]; fingerprint: string}
const bases = ref<PersonalityBase[]>([])
const correctionBases = ref<Record<string, PersonalityBase>>({})
const baseChoice = ref('')
const selectedBase = computed(() => bases.value.find(item => item.id === baseChoice.value))
const editableFields = computed(() => props.library ? selectedBase.value?.fields ?? [] : personality.value?.fields ?? [])
watch(editableFields, values => { if (!values.includes(field.value)) field.value = values[0] ?? '' })
const personalityKey = ref(props.library?.kind === 'personality' ? props.library.key : '')
const field = ref('speakingStyle')
const text = ref('')
const pending = ref<Pending>()
const receipt = ref<Receipt>()
const dirty = computed(() => !!text.value.trim())
const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })
const fieldNames: Record<string, string> = { speakingStyle: '说话风格', relationshipStyle: '相处风格', 'speaking detail': '表达细节', relationship: '相处细节' }
const stageNames: Record<string, string> = { 'agent.turn': '模型轮次', 'agent.admit': '检查准入与记忆引用', 'agent.prompt': '准备上下文资料', 'agent.interact': '模型与工具交互', 'agent.complete': '校验结果与记录历史', 'agent.plugins.prepare': '运行前置插件', 'agent.plugins.review': '运行结果插件' }
const stateNames: Record<string, string> = { PREPARED: '已登记，等待显式启动', STARTED: '开始执行', RETURNED: '阶段返回', SUCCESS: '模型轮次成功', FAILED: '执行失败', SENT: '平台已确认该段', INDETERMINATE: '交付结果不确定', CANCELLED: '已取消', DENIED: '准入拒绝', TIMED_OUT: '超时', CONFIGURATION_ERROR: '配置不可用', INVALID_INPUT: '输入无效' }
const activityNames: Record<string, string> = { COMMAND: '指令会话', PASSIVE: '被动回复', PROACTIVE: '主动活动', SCHEDULED: '计划任务', DELIVERY: '消息交付', SUMMARY: '上下文摘要', MEMORY_LEARNING: '记忆学习', PERSONALITY_LEARNING: '人格学习' }
Object.assign(activityNames, { SERVICE: '对话服务', AUTOMATIC_REPLY: '自动回复', COLLECT: '上下文采集', TOOL: '工具执行', LEARNING: '后台学习', STICKER_ANALYSIS: '素材分析' })
const runs = computed(() => {
  const grouped = new Map<string, Event[]>()
  for (const event of events.value) grouped.set(event.requestId, [...(grouped.get(event.requestId) ?? []), event])
  return [...grouped.entries()].reverse().slice(0, 15)
})
const endpoint = () => {
  if (props.library) return 'management/'
  if (!props.target) throw new Error('尚未选择管理目标。')
  return `clients/${encodeURIComponent(props.target.client)}/accounts/${encodeURIComponent(props.target.account)}/targets/${encodeURIComponent(props.target.target)}/management/`
}
const tabs = computed(() => props.library?.kind === 'personality' ? [{id: 'personality', name: '人格与补正'}]
  : props.library?.kind === 'memory' ? [{id: 'layers', name: '长期记忆'}, {id: 'memory', name: '记忆证据'}, {id: 'profile', name: '用户画像'}]
  : [{id: 'activity', name: '活动时间线'}, {id: 'layers', name: '短中长期记忆'}, {id: 'memory', name: '记忆证据'}, {id: 'profile', name: '用户画像'}, {id: 'personality', name: '人格与补正'}, {id: 'schedules', name: '持久计划'}])
// 2026/11/06：包含嵌套工作区的草稿；未激活页面不安装活动轮询。
usePageDraft(() => ({dirty: dirty.value, pending: !!pending.value, busy: busy.value}))
useActivePolling(() => props.active && auto.value && panel.value === 'activity' && !pending.value && !dirty.value,
  () => { if (!busy.value) void run(loadActivity) }, 10000)

async function call<T>(resource: string, body: Record<string, unknown>): Promise<T> {
  const name = props.library ? ({profiles: 'profile-space', memory: 'memory-space', personality: 'personality-space', layers: 'layers-space'} as Record<string, string>)[resource] : resource
  if (!name) throw new Error('此页面不提供该管理动作。')
  return request<T>(`${endpoint()}teaneko.agent:${name}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
}

async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try { await action() }
  catch (failure) {
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
    error.value = failure instanceof Error ? failure.message : '管理操作未完成。'
  } finally { busy.value = false }
}

async function canLeave(): Promise<boolean> {
  if (busy.value) { notice.value = '当前操作尚未结束，请稍后再切换聊天。'; return false }
  if (!pending.value && !dirty.value) return true
  try {
    await ElMessageBox.confirm(pending.value ? `操作 ${pending.value.body.operationId} 结果尚未确认，请保留编号后再切换。` : '补正草稿尚未保存，是否放弃？', '切换管理页面', { confirmButtonText: '放弃并切换', cancelButtonText: '保留当前内容', type: 'warning' })
    text.value = ''
    pending.value = undefined
    return true
  } catch { return false }
}
defineExpose({ canLeave })

async function loadActivity() {
  await refreshName()
  let result = await call<Timeline>('activity', { after: timeline.value?.next ?? 0, limit: 100 })
  if (result.reset || timeline.value && timeline.value.instance !== result.instance) {
    if (timeline.value && timeline.value.instance !== result.instance) result = await call<Timeline>('activity', { after: 0, limit: 100 })
    events.value = []
    notice.value = '活动窗口已重置或旧游标过期，已重新读取当前窗口。'
  }
  const merged = new Map(events.value.map(event => [event.sequence, event]))
  result.events.forEach(event => merged.set(event.sequence, event))
  events.value = [...merged.values()].slice(-300)
  timeline.value = result
}

async function loadMemory(more = false) {
  const result = await call<Memory>('memory', { action: 'READ', namespace: more ? memory.value!.namespace : namespace.value.trim(), generation: more ? memory.value!.generation : -1, revision: -1, after: more ? memory.value!.next : null })
  memory.value = more ? { ...result, records: [...memory.value!.records, ...result.records] } : result
}

async function loadProfile() {
  if (!subject.value.trim()) return
  const [facts, learned] = await Promise.all([
    call<Profile>('profiles', { subjectId: subject.value.trim(), namespace: namespace.value.trim(), generation: -1 }),
    call<Layers>('layers', { action: 'READ', subjectId: subject.value.trim(), namespace: namespace.value.trim(), generation: -1, revision: -1, topic: '' }),
  ])
  if (facts.namespace !== learned.namespace || facts.generation !== learned.generation) throw new Error('读取期间记忆代次已经变化，请重新读取画像。')
  profile.value = facts
  profileLearned.value = learned.longTerm
}

async function loadLayers() {
  layers.value = await call<Layers>('layers', { action: 'READ', namespace: namespace.value.trim(), generation: -1, revision: -1, subjectId: subject.value.trim() || null, topic: topic.value.trim() })
}

async function loadPersonality() {
  const body = { action: 'READ', key: personalityKey.value.trim(), bindingRevision: -1, stateRevision: -1 }
  if (props.library) {
    const result = await call<{personality: Personality; bases: PersonalityBase[]; correctionBases: Record<string, PersonalityBase>}>('personality', body)
    personality.value = result.personality
    bases.value = result.bases
    correctionBases.value = result.correctionBases
    if (!bases.value.some(item => item.id === baseChoice.value)) baseChoice.value = ''
  } else {
    personality.value = await call<Personality>('personality', body)
  }
}

async function loadSchedules(more = false) {
  // 2026/11/05：读取结果直接更新视图，去除无额外用途的中间变量。
  schedules.value = await call<Schedules>('schedules', { action: 'READ', after: more ? schedules.value?.next ?? '' : '', revision: 0 })
}

async function loadPanel() {
  if (panel.value === 'activity') await loadActivity()
  else if (panel.value === 'layers') await loadLayers()
  else if (panel.value === 'memory') await loadMemory()
  else if (panel.value === 'profile') { await loadMemory(); await loadProfile() }
  else if (panel.value === 'schedules') await loadSchedules()
  else await loadPersonality()
}

async function choosePanel(value: string) {
  if (value === panel.value || busy.value || !await canLeave()) return
  panel.value = value
  notice.value = ''
  await run(loadPanel)
}

async function finish(value: unknown) {
  const result = confirmedReceipt(value, pending.value)
  receipt.value = result
  pending.value = undefined
  if (result.status !== 'APPLIED' && result.status !== 'UNCHANGED') {
    error.value = `本次没有提交新修改：${result.status === 'CONFLICT' ? '版本冲突，请重新读取并确认' : result.status}。`
    return
  }
  notice.value = result.note
  text.value = ''
  try { await loadPanel() }
  catch (failure) {
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
    notice.value += ' 修改已确认，但视图刷新失败，请手动刷新；不要重新提交。'
  }
}

async function resend() {
  if (!pending.value) return
  const original = pending.value
  await run(async () => { await finish(await call<Receipt>(original.resource, original.body)) })
}

async function queryOperation() {
  if (!pending.value) return
  const original = pending.value
  // 2026/11/05：恢复回执独立查询，不能转成长期条目维护或重复执行恢复。
  const action = original.resource === 'layers' && original.body.action === 'REPLAY' ? 'REPLAY_OPERATION' : 'OPERATION'
  await run(async () => { await finish(await call<Receipt>(original.resource, { ...original.body, action })) })
}

async function discard() {
  if (!await canLeave()) return
  await run(loadPanel)
}

async function commit(resource: 'memory' | 'personality' | 'layers' | 'schedules', body: Record<string, unknown>, explanation: string) {
  if (pending.value || busy.value) return
  try { await ElMessageBox.confirm(explanation, '确认管理操作', { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' }) }
  catch { return }
  pending.value = { resource, body: { ...body, operationId: crypto.randomUUID() } }
  await resend()
}

async function editFact(item: Evidence, source: { namespace: string; generation: number }, lock = false) {
  let replacement: string | null = null
  if (!lock) {
    try {
      const result = await ElMessageBox.prompt('只修正正文，保留原主体、来源和时间。', '修正证据', { inputValue: item.text, inputType: 'textarea', inputValidator: value => !!value?.trim() && value.length <= 4096 || '正文需要一至四千零九十六字符。', confirmButtonText: '继续确认', cancelButtonText: '取消' })
      replacement = result.value
    } catch { return }
  }
  await commit('memory', { action: 'EDIT', namespace: source.namespace, generation: source.generation, revision: item.revision, recordId: item.id, text: replacement, locked: lock ? !item.locked : null }, '该空间可能被多个聊天共享，修正会影响共享此证据的画像和后续回复。')
}

async function changeMemory(action: 'CLEAR' | 'CLONE') {
  if (!memory.value) return
  const current = memory.value
  await commit('memory', { action, namespace: current.namespace, generation: current.generation, revision: current.revision }, action === 'CLEAR'
    ? '将逻辑清空整个共享空间并推进代次，所有引用它的聊天都会受到影响。人格和其他空间保留。'
    : '复制这个证据事实集合的完整有界快照，不包含自动长期条目、中期摘要和短期消息，也不自动改变当前聊天绑定。')
}

async function editLong(item: LongItem, source: { namespace: string; generation: number }, forget = false) {
  let replacement: string | null = null
  if (!forget) {
    try {
      const result = await ElMessageBox.prompt('仅修正当前长期条目，保留证据抑制和版本保护。', '修正长期记忆', { inputValue: item.text, inputType: 'textarea', inputValidator: value => !!value?.trim() && value.length <= 512 || '正文需要一至五百一十二字符。' })
      replacement = result.value
    } catch { return }
  }
  await commit('layers', { action: forget ? 'FORGET' : 'EDIT', namespace: source.namespace, generation: source.generation, revision: item.revision, id: item.id, text: replacement, topic: '' },
    forget ? '遗忘会影响整个共享空间。保留墓碑以阻止旧证据自动复活，不删除原始聊天。' : '修正共享长期记忆并保护人工纠正，所有引用此条目的聊天都会受到影响。')
}

/** 2026/11/05：管理员明确解除当前分区暂停，失败回执沿用原操作查询。 */
async function resumeConsolidation() {
  const current = layers.value
  if (!current?.consolidation || current.consolidation.state !== 'PAUSED') return
  await commit('layers', { action: 'REPLAY', namespace: current.namespace, generation: current.generation,
    revision: current.consolidation.revision, topic: '' },
  '重新尝试本聊天暂停的长期整理，不删除证据或跳过原水位。请先检查失败原因；同一输入再次连续三次无效仍会暂停。')
}

async function changePersonality(action: string, item?: Correction) {
  const current = personality.value
  if (!current) return
  let replacement: string | null = null
  if (action === 'EDIT' && item) {
    try {
      const result = await ElMessageBox.prompt('修正表达习惯，不写入身份、工具权限或用户事实。', '编辑补正', { inputValue: item.text, inputType: 'textarea', inputValidator: value => !!value?.trim() && value.length <= 512 || '补正需要一至五百一十二字符。' })
      replacement = result.value
    } catch { return }
  }
  const base = props.library ? (action === 'ADD' ? selectedBase.value : item ? correctionBases.value[item.id] : undefined) : undefined
  await commit('personality', { baseId: base?.id, baseFingerprint: base?.fingerprint, action, key: current.key, bindingRevision: current.bindingRevision, stateRevision: current.revision, correctionId: item?.id ?? null, field: action === 'ADD' ? field.value : null, text: action === 'ADD' ? text.value : replacement, locked: action === 'LOCK' ? !item!.locked : null },
    action === 'BIND' ? '将当前聊天绑定到已查看的补正 key；原补正会保留备份，后续会话会重新校验人格。' : '操作受当前绑定与内容版本保护；共享此补正 key 的聊天可能同时受到影响，原存储会保留相应备份。')
}

async function inspectSubject(item: Evidence, source: { namespace: string }) {
  if (busy.value) return
  if (!await canLeave()) return
  subject.value = item.subjectId
  namespace.value = source.namespace
  panel.value = 'profile'
  await run(loadProfile)
}

function beforeUnload(event: BeforeUnloadEvent) {
  if (pending.value || dirty.value) { event.preventDefault(); event.returnValue = '' }
}

// 2026/11/04：显示名刷新和父组件重新渲染不重置同一聊天的编辑状态。
watch(() => JSON.stringify([props.target?.client, props.target?.account, props.target?.target]), () => {
  targetName.value = props.target?.name ?? props.library?.name ?? ''
  events.value = []; timeline.value = undefined; memory.value = undefined; profile.value = undefined; personality.value = undefined; layers.value = undefined; profileLearned.value = undefined
  namespace.value = ''; personalityKey.value = ''; receipt.value = undefined; notice.value = ''; error.value = ''
  schedules.value = undefined
  void run(loadPanel)
})
onMounted(() => {
  void run(loadPanel)
  window.addEventListener('beforeunload', beforeUnload)
})
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <!-- 2026/11/04：收起重复说明，活动状态与待确认操作继续直接展示。 -->
  <div class="agent-workspace">
    <div v-if="!embedded" class="workspace-heading">
      <!-- 2026/11/05：目标返回入口统一使用图标并保留可访问名称。 -->
      <button class="icon-button" aria-label="返回聊天列表" title="返回聊天列表" @click="emit('selectTarget')"><ConsoleIcon name="back" /></button><h2>{{ targetName }}</h2>
      </div>
    <div class="agent-tabs" role="tablist" aria-label="Agent 管理分区">
      <button v-for="item in tabs" :key="item.id" role="tab" :aria-selected="panel === item.id" :class="{ selected: panel === item.id }" :disabled="busy" @click="choosePanel(item.id)">{{ item.name }}</button>
      </div>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <ElAlert v-if="notice" :title="notice" type="info" :closable="true" @close="notice = ''" />
    <ElCard v-if="pending" shadow="never" class="pending-operation">
      <h3>操作结果尚待确认</h3>
      <p class="small">{{ pending.body.operationId }}</p>
      <p>断开或失败不表示没有提交，请查询原结果或原样重试。</p>
      <ElButton :disabled="busy" @click="queryOperation">查询原回执</ElButton>
      <ElButton :disabled="busy" @click="resend">原样重试</ElButton>
      <RefreshButton :disabled="busy" @click="discard" label="保留编号后放弃并刷新" />
      </ElCard>
    <template v-if="panel === 'schedules'">
      <p class="small muted">显示当前聊天的持久计划。取消只阻止未来执行，已开始的外部动作不能保证撤销。暂停与结果不明的计划不提供无条件重试。</p>
      <RefreshButton :disabled="busy || !!pending" @click="run(() => loadSchedules())" label="刷新计划首页" />
      <ElCard v-for="item in schedules?.items ?? []" :key="item.id" shadow="never" class="runtime-card"><h3>{{ item.title }}</h3><p>{{ scheduleStates[item.state] ?? item.state }}</p><p class="small muted">发生时间：{{ date(item.nextFire) }} · 原时区 {{ item.zoneId }}<br />{{ item.intervalSeconds ? `每 ${item.intervalSeconds} 秒一次` : '单次计划' }} · 动作 {{ item.action }}</p><p>{{ item.outcome }}</p><p class="small revision">{{ item.id }} · 版本 {{ item.revision }}</p><ElButton v-if="item.cancellable" type="danger" plain :disabled="busy || !!pending" @click="commit('schedules', { action: 'CANCEL', after: '', id: item.id, revision: item.revision }, `取消“${item.title}”的未来计划？已开始的动作可能仍会完成。`)">取消未来计划</ElButton></ElCard>
      <ElEmpty v-if="schedules && !schedules.items.length" description="本页没有匹配计划；存在后页时可继续扫描" /><ElButton v-if="schedules?.next" :disabled="busy || !!pending" @click="run(() => loadSchedules(true))">继续扫描计划</ElButton>
    </template>
    <template v-if="panel === 'activity'">
      <div class="agent-toolbar">
      <label>每十秒自动刷新 <ElSwitch v-model="auto" />
      </label>
      <RefreshButton :loading="busy" @click="run(loadActivity)" label="刷新活动" />
      </div>
      <DetailPanel title="活动说明"><p>模型完成与消息发送分别记录；进程重启后清空此活动窗口。</p></DetailPanel>
      <p v-if="timeline" class="small muted">窗口开始 {{ date(timeline.startedAt) }} · {{ timeline.truncated ? '更早节点已超出窗口' : '当前窗口尚未截断' }} · 页面最多保留三百个节点</p>
      <ElEmpty v-if="!events.length" description="当前聊天尚无可观察活动" />
      <ElCard v-for="[id, items] in runs" :key="id" shadow="never" class="activity-run">
      <h3>{{ activityNames[items[0]!.activity] ?? items[0]!.activity }}</h3>
      <p class="small muted">请求 {{ id }}</p>
      <ol class="activity-steps">
      <li v-for="item in items" :key="item.sequence">
      <time>{{ date(item.at) }}</time>
      <strong>{{ item.stage.startsWith('delivery.part.') ? '发送第 ' + (Number(item.stage.split('.').pop()) + 1) + ' 段' : stageNames[item.stage] ?? item.stage }}</strong>
      <span>{{ stateNames[item.state] ?? item.state }}</span>
      <p v-if="Object.keys(item.metrics).length" class="small muted">耗时 {{ item.metrics.elapsedMillis }} 毫秒 · 模型请求 {{ item.metrics.modelCalls }} 次 · 工具结果 {{ item.metrics.toolCalls }} 个 · 报告用量 {{ item.metrics.reportedTokens }} token · 估算 {{ item.metrics.estimatedTokens }} token · 未知用量 {{ item.metrics.unknownUsageAttempts }} 次</p>
      </li>
      </ol>
      </ElCard>
    </template>
    <template v-else-if="panel === 'layers'">
      <ElAlert title="短期消息和中期摘要按当前聊天的实际引用读取；长期记忆按 namespace 共享。查询不会调用模型或触发学习。" type="info" :closable="false" />
      <div class="agent-toolbar">
      <label>记忆空间<ElInput v-model="namespace" placeholder="留空使用当前绑定" :disabled="busy || !!pending" />
      </label>
      <label>长期主体 UUID（可选）<ElInput v-model="subject" placeholder="留空查询近期长期条目" :disabled="busy || !!pending" />
      </label>
      <label>长期话题（可选）<ElInput v-model="topic" placeholder="按原字面索引查询" :disabled="busy || !!pending" maxlength="1024" />
      </label>
      <ElButton :disabled="busy || !!pending" @click="run(loadLayers)">读取三层记忆</ElButton>
      </div>
      <template v-if="layers">
      <p class="small muted">空间 {{ layers.namespace }} · 代次 {{ layers.generation }} · 压缩覆盖序号 {{ layers.compressedThrough }}</p>
      <!-- 2026/11/05：后台暂停可见且有受控恢复入口，不让页面轮询自动触发生成。 -->
      <ElCard v-if="layers.consolidation" shadow="never" class="evidence-card">
        <strong>长期整理 · {{ consolidationStates[layers.consolidation.state] }}</strong>
        <p v-if="layers.consolidation.reasonCode" class="small muted">{{ consolidationReasons[layers.consolidation.reasonCode] || '整理未完成，请查看后台日志' }}<span v-if="advanced">（{{ layers.consolidation.reasonCode }}）</span> · 无效生成 {{ layers.consolidation.invalidAttempts }}/3<span v-if="layers.consolidation.failedAt"> · {{ date(layers.consolidation.failedAt) }}</span></p>
        <p v-if="layers.consolidation.state === 'PAUSED'" class="small muted">原证据和进度已保留。输入有效变化后会重新检查，也可确认原因后手动恢复。</p>
        <ElButton v-if="!library && layers.consolidation.state === 'PAUSED'" :disabled="busy || !!pending" @click="resumeConsolidation">恢复整理</ElButton>
        <p v-if="advanced" class="small muted">整理覆盖序号 {{ layers.consolidation.through }} · 版本 {{ layers.consolidation.revision }}</p>
      </ElCard>
        <template v-if="!library"><h3>短期 · 最近二十四条持久消息</h3>
      <p class="small muted">助手存档是生成记录，平台实际确认另见活动时间线。</p>
      <ElEmpty v-if="!layers.recent.length" description="当前分区没有有效短期消息" />
      <ElCard v-for="item in layers.recent" :key="item.sequence" shadow="never" class="evidence-card">
      <strong>{{ item.role === 'USER' ? '用户消息' : '助手存档' }}</strong>
      <p class="evidence-text">{{ item.text }}</p>
      <p class="small muted">{{ date(item.at) }}{{ item.estimated ? ' · 处理时间估算' : '' }}{{ item.truncated ? ' · 正文尾部已省略' : '' }}</p>
      </ElCard>
        <h3>中期 · 最近六份有效摘要</h3>
      <ElEmpty v-if="!layers.episodes.length" description="尚无有效中期摘要" />
      <ElCard v-for="item in layers.episodes" :key="item.id" shadow="never" class="evidence-card">
      <strong>{{ item.brief }}</strong>
      <p class="evidence-text">{{ item.details }}</p>
      <p class="small muted">来源区间 {{ date(item.from) }} 至 {{ date(item.through) }}</p>
      </ElCard>
        </template><h3>长期 · 自动整理与明确保存</h3>
      <p class="small muted">检查 {{ layers.longTerm.scanned }} 个候选，最多返回二十项；{{ layers.longTerm.limited ? '本次结果受限，不能代表全部记忆' : '当前索引窗口内无输出限制' }}。</p>
      <ElEmpty v-if="!layers.longTerm.items.length" description="本次没有返回长期条目" />
      <ElCard v-for="item in layers.longTerm.items" :key="item.id" shadow="never" class="evidence-card">
      <strong>{{ item.kind === 'PREFERENCE' ? '偏好' : item.kind === 'EPISODE' ? '事件' : '事实' }} · {{ item.status === 'CONFLICT' ? '存在分歧' : '有效记录' }}</strong>
      <p class="evidence-text">{{ item.text }}</p>
      <p class="small muted">{{ item.time }} · 版本 {{ item.revision }}<br />{{ item.subjectId ? '主体 ' + item.subjectId : '无个人主体的事件记忆' }}</p>
      <ElButton :disabled="busy || !!pending" @click="editLong(item, layers)">修正长期记忆</ElButton>
      <ElButton type="danger" plain :disabled="busy || !!pending" @click="editLong(item, layers, true)">遗忘此条</ElButton>
      </ElCard>
      </template>
    </template>
    <template v-else-if="panel === 'memory' || panel === 'profile'">
      <ElAlert title="记忆按 namespace 共享，来源聊天不构成隔离。画像来自当前有效事实与偏好，修正证据会影响所有共享入口。" type="warning" :closable="false" show-icon />
      <div class="agent-toolbar">
      <label>记忆空间<ElInput v-model="namespace" placeholder="留空使用当前绑定或默认共享空间" :disabled="busy || !!pending" maxlength="128" />
      </label>
      <label v-if="panel === 'profile' && advanced">主体 UUID<ElInput v-model="subject" placeholder="输入真实 TeaNeko UUID" :disabled="busy || !!pending" />
      </label>
      <ElButton :disabled="busy || !!pending || panel === 'profile' && !subject.trim()" @click="run(panel === 'memory' ? () => loadMemory() : loadProfile)">读取{{ panel === 'memory' ? '记忆' : '画像' }}</ElButton>
      </div>
      <AgentLibraryPicker v-if="panel === 'profile' && memory?.exists" kind="profiles" :namespace="memory.namespace" :generation="memory.generation" :disabled="busy || !!pending" @expired="emit('expired')" @select="item => { subject = item.id; namespace = memory!.namespace; run(loadProfile) }" />
      <p v-if="panel === 'profile' && subject" class="small muted">当前主体：{{ subject }}</p>
      <template v-if="panel === 'memory' && memory">
      <p class="small muted">这里展示原事实证据集合；自动长期整理和当前明确保存条目见“短中长期记忆”。</p>
      <div class="agent-toolbar">
      <span class="small">空间 {{ memory.namespace }} · 代次 {{ memory.generation }} · 版本 {{ memory.revision }}</span>
      <ElButton :disabled="!memory.exists || busy || !!pending" @click="changeMemory('CLONE')">克隆证据事实</ElButton>
      <ElButton type="danger" plain :disabled="!memory.exists || busy || !!pending" @click="changeMemory('CLEAR')">清空共享空间</ElButton>
      </div>
      <ElEmpty v-if="!memory.records.length" :description="memory.exists ? '这一页没有事实证据' : '空间尚未登记；查询没有创建数据'" />
        <ElCard v-for="item in memory.records" :key="item.id" shadow="never" class="evidence-card">
      <div class="card-heading">
      <strong>{{ item.kind === 'PREFERENCE' ? '偏好' : '事实' }} · {{ item.locked ? '已锁定' : '未锁定' }}</strong>
      <span class="small muted">版本 {{ item.revision }}</span>
      </div>
      <p class="evidence-text">{{ item.text }}</p>
      <p class="small muted">主体 {{ item.subjectId }}<br />创建 {{ date(item.createdAt) }} · 更新 {{ date(item.updatedAt) }}</p>
      <details class="small muted">
      <summary>时间与来源依据</summary>
      <p>平台 {{ item.client }} · 原聊天 {{ item.sourceScope }}</p>
      <pre>{{ JSON.stringify(item.time, null, 2) }}</pre>
      <p>记录 {{ item.id }}</p>
      </details>
      <div class="agent-toolbar">
      <ElButton :disabled="busy || !!pending" @click="editFact(item, memory)">修正正文</ElButton>
      <ElButton :disabled="busy || !!pending" @click="editFact(item, memory, true)">{{ item.locked ? '解锁证据' : '锁定证据' }}</ElButton>
      <ElButton text @click="inspectSubject(item, memory)">查看此人画像</ElButton>
      </div>
      </ElCard>
        <ElButton v-if="memory.next && memory.records.length < 200" :disabled="busy || !!pending" @click="run(() => loadMemory(true))">加载下一页</ElButton>
      <p v-if="memory.records.length >= 200" class="small muted">页面已达到二百条显示上限，可重新读取或使用原管理导出入口。</p>
      </template>
      <template v-if="panel === 'profile' && profile">
      <ElAlert :title="profile.complete ? '本次预算范围内证据已完整检查。' : '本次证据有扫描或输出限制，不能把未显示的信息解释为不存在。'" :type="profile.complete ? 'info' : 'warning'" :closable="false" />
      <p class="small muted">空间 {{ profile.namespace }} · 代次 {{ profile.generation }} · 扫描 {{ profile.scanned }} 条 · 命中 {{ profile.matched }} 条</p>
      <ElEmpty v-if="!profile.records.length" description="原事实集合本次未返回画像证据" />
      <ElCard v-for="item in profile.records" :key="item.id" shadow="never" class="evidence-card">
      <strong>{{ item.kind === 'PREFERENCE' ? '偏好' : '事实' }} · {{ item.locked ? '已锁定' : '未锁定' }}</strong>
      <p class="evidence-text">{{ item.text }}</p>
      <p class="small muted">记录 {{ item.id }} · 版本 {{ item.revision }}</p>
      <ElButton :disabled="busy || !!pending" @click="editFact(item, profile)">修正画像证据</ElButton>
      </ElCard>
      </template>
    </template>
    <template v-else>
      <div class="agent-toolbar">
      <label>补正 key<ElInput v-model="personalityKey" placeholder="留空读取当前绑定，也可查看克隆或备份" :disabled="busy || !!pending" maxlength="256" />
      </label>
      <ElButton :disabled="busy || !!pending" @click="run(loadPersonality)">读取人格</ElButton>
      </div>
      <template v-if="personality">
      <ElCard v-if="!library" shadow="never" class="personality-base">
      <h3>基础人格 · {{ personality.name }}</h3>
      <p v-for="(rule, index) in personality.rules" :key="index" class="evidence-text">{{ rule }}</p>
      <p class="small muted">基础内容来自本地配置；本页管理表达补正。</p>
      </ElCard>
      <ElAlert v-if="!library && !personality.bindingRevision" title="当前聊天尚未形成人格绑定；首次正常 Agent 交互后建立。本页读取不会自动初始化。" type="info" :closable="false" />
      <template v-else>
      <p class="small muted">补正 {{ personality.key }} · 绑定版本 {{ personality.bindingRevision }} · 内容版本 {{ personality.revision }} · {{ personality.backup ? '不可变备份' : personality.current ? '当前绑定' : '其他补正空间' }}</p>
      <div class="agent-toolbar">
      <ElButton :disabled="busy || !!pending || personality.revision < 0" @click="changePersonality('BACKUP')">创建备份</ElButton>
      <ElButton :disabled="busy || !!pending || personality.revision < 0" @click="changePersonality('CLONE')">克隆补正</ElButton>
      <ElButton v-if="!library && !personality.current && !personality.backup" :disabled="busy || !!pending" @click="changePersonality('BIND')">绑定此补正空间</ElButton>
      </div>
      <ElSelect v-if="personality.backups.length" v-model="personalityKey" placeholder="选择最近备份再读取" :disabled="busy || !!pending">
      <ElOption v-for="key in personality.backups" :key="key" :value="key" :label="key" />
      </ElSelect>
      <ElEmpty v-if="!personality.corrections.length" description="当前没有补正记录" />
      <ElCard v-for="item in personality.corrections" :key="item.id" shadow="never" class="evidence-card">
      <strong>{{ fieldNames[item.field] ?? item.field }} · {{ item.locked ? '已锁定' : '未锁定' }}</strong>
      <p class="evidence-text">{{ item.text }}</p>
      <p v-if="library" class="small muted">{{ correctionBases[item.id]?.name ?? '基础人格已移除或版本不兼容，只能撤销或保存副本' }}</p>
      <p class="small muted">{{ date(item.occurredAt) }} · {{ item.origin === 'MANAGEMENT' ? '独立管理主体' : '真实聊天来源' }}</p>
      <div v-if="(library || personality.current) && !personality.backup" class="agent-toolbar">
      <ElButton :disabled="busy || !!pending || !!library && !correctionBases[item.id]" @click="changePersonality('EDIT', item)">编辑补正</ElButton>
      <ElButton :disabled="busy || !!pending || !!library && !correctionBases[item.id]" @click="changePersonality('LOCK', item)">{{ item.locked ? '解锁补正' : '锁定补正' }}</ElButton>
      <ElButton type="danger" plain :disabled="busy || !!pending" @click="changePersonality('REMOVE', item)">撤销补正</ElButton>
      </div>
      </ElCard>
      <ElCard v-if="(library || personality.current) && !personality.backup" shadow="never" class="new-correction">
      <h3>添加表达补正</h3>
      <ElSelect v-if="library" v-model="baseChoice" placeholder="选择补正适用的基础人格" :disabled="busy || !!pending">
      <ElOption v-for="base in bases" :key="base.id" :value="base.id" :label="base.name + ' · v' + base.version" />
      </ElSelect>
      <ElSelect v-model="field" :disabled="busy || !!pending">
      <ElOption v-for="value in editableFields" :key="value" :value="value" :label="fieldNames[value] ?? value" />
      </ElSelect>
      <ValueEditor v-model="text" label="表达补正" :maxlength="512" :disabled="busy || !!pending" />
      <ElButton type="primary" :disabled="busy || !!pending || !text.trim() || !field" @click="changePersonality('ADD')">提交补正</ElButton>
      </ElCard>
      </template>
      </template>
    </template>
    <template v-if="panel === 'profile' && profile && profileLearned">
      <h3>自动长期画像证据</h3>
      <p class="small muted">检查 {{ profileLearned.scanned }} 个长期候选；{{ profileLearned.limited ? '本次输出受限' : '当前索引窗口内无输出限制' }}，不代表全量历史。</p>
      <ElEmpty v-if="!profileLearned.items.length" description="本次没有返回自动长期画像条目" />
      <ElCard v-for="item in profileLearned.items" :key="item.id" shadow="never" class="evidence-card">
      <strong>{{ item.kind === 'PREFERENCE' ? '偏好' : '事实' }} · {{ item.status === 'CONFLICT' ? '存在分歧' : '有效记录' }}</strong>
      <p class="evidence-text">{{ item.text }}</p>
      <p class="small muted">{{ item.time }} · 版本 {{ item.revision }}</p>
      <ElButton :disabled="busy || !!pending" @click="editLong(item, profile)">修正长期画像</ElButton>
      <ElButton type="danger" plain :disabled="busy || !!pending" @click="editLong(item, profile, true)">遗忘长期画像条目</ElButton>
      </ElCard>
      </template>
    <ElCard v-if="advanced && receipt" shadow="never" class="operation-receipt">
      <h3>原事务回执</h3>
      <p>操作 {{ receipt.operationId }} · {{ receipt.status === 'APPLIED' ? '已提交' : receipt.status === 'UNCHANGED' ? '无需变更' : receipt.status }}</p>
      <p>结果目标 {{ receipt.target }} · 版本 {{ receipt.revision }}</p>
      <p>{{ receipt.note }}</p>
      </ElCard>
  </div>
</template>
