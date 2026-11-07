<script setup lang="ts">
import AccessibleTabs from './AccessibleTabs.vue'
// 2026/11/04：刷新类操作统一使用带完整动作提示的图标按钮。
import RefreshButton from './RefreshButton.vue'
// 2026/11/05：详细操作回执只在高级模式展示。
import {useConsolePreferences} from './console-preferences'
import {computed, onBeforeUnmount, onMounted, reactive, ref} from 'vue'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElEmpty,
  ElInput,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTag
} from 'element-plus'
import {ApiError, request} from './api'
import DetailPanel from './DetailPanel.vue'
import {usePageDraft} from './pages/page-guards'
import {useActivePolling} from './pages/active-polling'
import {timerReceipt, type TimerReceipt as Receipt} from './operation-receipts'

const {advanced} = useConsolePreferences()

interface Timer { id: string; name: string; state: string; revision: number; rule: { kind: string; rule: string; nextFire?: string; waiting: boolean; zone: string }; actions: string[]; category: string; categoryName: string; description: string; source: string }
interface TimerPage { items: Timer[]; next: string; more: boolean; limited: boolean; categories: {id: string; name: string}[] }
interface Run { id: string; name: string; state: string; stage: string; createdAt: string; startedAt?: string; finishedAt?: string }
interface RunPage { instance: string; startedAt: string; items: Run[]; next: number; reset: boolean; more: boolean; dropped: number }
interface ManagementCall { requestId: string; resource: string; ageMillis: number; waitingEnded: boolean }
interface Log { sequence: number; at: string; level: string; namespace: string; message: string; requestId: string; scope: string; plugin: string }
interface LogPage { instance: string; items: Log[]; next: number; more: boolean; reset: boolean; gap: boolean; dropped: number; policy: { maxLogs: number; retentionMinutes: number; captureMessages: boolean } }
interface Pending { id: string; change: { operationId: string; revision: number; paused: boolean } }
const props = defineProps<{ active: boolean }>()
const emit = defineEmits<{ expired: [] }>()
const panel = ref('timers')
const busy = ref(false)
const error = ref('')
const notice = ref('')
const auto = ref(false)
const timers = ref<TimerPage>()
const runs = ref<RunPage>()
const managementCalls = ref<ManagementCall[]>([])
const logs = ref<LogPage>()
const displayedLogs = ref<Log[]>([])
const runState = ref('')
const runSearch = ref('')
// 2026/11/04：已应用筛选与编辑中的条件分开，自动刷新沿用同一查询，变更重置游标。
const timerFilters = reactive({category: '', kind: '', state: '', search: ''})
const appliedTimers = ref({...timerFilters})
const ruleNames: Record<string, string> = {FIXED_RATE: '固定频率', FIXED_DELAY: '完成后间隔', SMART_RATE: '智能间隔', CRON: 'Cron 日程', CUSTOM: '自定义规则'}
const pending = ref<Pending>()
const receipt = ref<Receipt>()
const filters = reactive({ level: '', namespace: '', text: '', requestId: '', scope: '', plugin: '', from: '', through: '' })
const applied = ref({ ...filters })
// 2026/11/04：折叠详细筛选后仍提示实际生效的条件，不把隐藏控件当作清空。
const detailedFiltersActive = computed(() => [applied.value.namespace, applied.value.requestId, applied.value.scope, applied.value.plugin, applied.value.from, applied.value.through].some(Boolean))
const stateNames: Record<string, string> = { ACTIVE: '等待触发', PAUSED: '网页已暂停', SOURCE_PAUSED: '来源已暂停', REMOVED: '已移除', UNKNOWN: '状态未知', QUEUED: '排队中', RUNNING: '同步执行中', WAITING_RESULT: '等待外部结果', SUCCESS: '原任务成功', FAILED: '原任务失败' }
const date = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '尚未确认'
// 2026/11/06：页签隐藏时停止计时，保留日志筛选、原操作和已读窗口。
usePageDraft(() => ({pending: !!pending.value, busy: busy.value}))
useActivePolling(() => props.active && auto.value && !pending.value,
  () => { if (!busy.value) void run(refresh) }, 5000)

async function call<T>(resource: string, body: object): Promise<T> {
  return request<T>(`management/teaneko.core:${resource}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
}

function query(action: string, values: object = {}) {
  return { action, after: '', before: 0, limit: 50, instance: '', state: '', search: '', id: null, change: null, operationId: null, ...values }
}

async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try { await action() }
  catch (failure) {
    error.value = failure instanceof Error ? failure.message : '运行管理请求未完成。'
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
  } finally { busy.value = false }
}

async function loadTimers(more = false) {
  timers.value = await call<TimerPage>('runtime', query('TIMERS', {...appliedTimers.value, after: more ? timers.value?.next ?? '' : ''}))
}

async function applyTimerFilters(category = timerFilters.category) {
  if (busy.value) return
  timerFilters.category = category
  appliedTimers.value = {...timerFilters}
  auto.value = false
  await run(() => loadTimers())
}

async function loadRuns(more = false) {
  // 2026/11/07：管理调用的真实占用可独立查看；逻辑超时后仍显示正在收尾的工作。
  const [tasks, calls] = await Promise.all([
    call<RunPage>('runtime', query('RUNS', { before: more ? runs.value?.next ?? 0 : 0, instance: runs.value?.instance ?? '', state: runState.value, search: runSearch.value })),
    request<ManagementCall[]>('management-calls')
  ])
  runs.value = tasks
  managementCalls.value = calls
  if (runs.value.reset) notice.value = '进程已更换，任务窗口已重置。'
}

async function loadLogs(reset = false) {
  const selected = applied.value
  const result = await call<LogPage>('logs', { ...selected, instance: reset ? '' : logs.value?.instance ?? '', after: reset ? 0 : logs.value?.next ?? 0, limit: 100,
    from: selected.from ? new Date(selected.from).toISOString() : null, through: selected.through ? new Date(selected.through).toISOString() : null })
  if (reset || result.reset) displayedLogs.value = []
  displayedLogs.value = [...displayedLogs.value, ...result.items].slice(-300)
  logs.value = result
  if (result.reset || result.gap) notice.value = '日志窗口已重置或旧游标已淘汰；当前显示不代表完整历史。'
}

async function refresh() {
  if (panel.value === 'timers') await loadTimers()
  else if (panel.value === 'runs') await loadRuns()
  else await loadLogs()
}

async function select(value: string) {
  panel.value = value
  auto.value = false
  await run(refresh)
}

// 2026/11/06：仅匹配原编号和目标的确定回执可以结束恢复状态。
async function receive(result: unknown) {
  receipt.value = timerReceipt(result, pending.value?.change.operationId ?? receipt.value!.operationId,
    pending.value?.id ?? receipt.value!.timer, pending.value)
  pending.value = undefined
  await loadTimers()
}

async function deliver() {
  const original = pending.value!
  await receive(await call<Receipt>('runtime', query('CHANGE', original)))
}

async function change(timer: Timer, paused: boolean) {
  try {
    await ElMessageBox.confirm(`${paused ? '暂停' : '恢复'}“${timer.name}”的后续触发？已开始的任务继续执行。网页门禁在重启后复位，来源自身暂停不受此操作覆盖。`, '确认定时器操作', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  pending.value = { id: timer.id, change: { operationId: crypto.randomUUID(), revision: timer.revision, paused } }
  await run(deliver)
}

async function operation() {
  const id = pending.value?.id ?? receipt.value?.timer
  const operationId = pending.value?.change.operationId ?? receipt.value?.operationId
  if (id && operationId) await run(async () => { await receive(await call<Receipt>('runtime', query('OPERATION', { id, operationId }))) })
}

async function discard() {
  try { await ElMessageBox.confirm('释放原请求并重新读取。此前操作可能已经完成，此处不会撤销它。', '放弃原请求', { confirmButtonText: '确认', cancelButtonText: '取消' }) }
  catch { return }
  await run(async () => { await loadTimers(); pending.value = undefined })
}

async function applyFilters() {
  applied.value = { ...filters }
  await run(() => loadLogs(true))
}

function beforeUnload(event: BeforeUnloadEvent) {
  if (pending.value) { event.preventDefault(); event.returnValue = '' }
}

onMounted(() => {
  void run(refresh)
  window.addEventListener('beforeunload', beforeUnload)
})
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <!-- 2026/11/04：日志详细筛选按需展开，已应用条件保留可见提示。 -->
  <div class="runtime-view">
    <!-- 2026/11/06：共享键盘页签，页面切换仍经过原草稿守卫。 -->
    <AccessibleTabs :model-value="panel" :items="[{id: 'timers', name: '定时规则'}, {id: 'runs', name: 'Core 任务'}, {id: 'logs', name: '运行日志'}]" label="运行管理分区" :disabled="busy" @update:model-value="select">
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <ElAlert v-if="notice" :title="notice" type="info" :closable="false" />
    <div class="editor-actions"><RefreshButton :disabled="busy" @click="run(refresh)" label="刷新当前页" /><label class="small muted">每 5 秒刷新 <ElSwitch v-model="auto" :disabled="!!pending" /></label></div>
    <ElCard v-if="pending" shadow="never" class="pending-operation"><h3>定时器操作尚待确认</h3><p class="small revision">{{ pending.change.operationId }}</p><p class="small muted">中断不代表没有执行，先查原回执。回执只在原进程保留。</p><ElButton :disabled="busy" @click="operation">查询原回执</ElButton><ElButton :disabled="busy" @click="run(deliver)">原样重试</ElButton><RefreshButton :disabled="busy" @click="discard" label="重新读取并放弃原请求" /></ElCard>
    <template v-if="panel === 'timers'">
      <!-- 2026/11/04：分类来源于服务端完整观测窗口，所有过滤在分页前执行。 -->
      <div class="timer-category-filters" role="group" aria-label="定时任务分类">
        <ElButton :type="appliedTimers.category === '' ? 'primary' : 'default'" :disabled="busy" @click="applyTimerFilters('')">全部分类</ElButton>
        <ElButton v-for="category in timers?.categories ?? []" :key="category.id" :type="appliedTimers.category === category.id ? 'primary' : 'default'" :disabled="busy" @click="applyTimerFilters(category.id)">{{ category.name }}</ElButton>
      </div>
      <div class="agent-toolbar">
        <label>规则类型<ElSelect v-model="timerFilters.kind" aria-label="定时规则类型" placeholder="全部规则" :disabled="busy"><ElOption label="全部规则" value="" /><ElOption v-for="(name, kind) in ruleNames" :key="kind" :label="name" :value="kind" /></ElSelect></label>
        <label>运行状态<ElSelect v-model="timerFilters.state" aria-label="定时规则状态" placeholder="全部状态" :disabled="busy"><ElOption label="全部状态" value="" /><ElOption v-for="state in ['ACTIVE', 'RUNNING', 'PAUSED', 'SOURCE_PAUSED', 'REMOVED', 'UNKNOWN']" :key="state" :label="stateNames[state]" :value="state" /></ElSelect></label>
        <label>任务名称<ElInput v-model="timerFilters.search" aria-label="定时规则名称" maxlength="256" clearable @keyup.enter="applyTimerFilters()" /></label>
        <ElButton :disabled="busy" @click="applyTimerFilters()">筛选规则</ElButton>
      </div>
      <DetailPanel title="定时规则说明"><p>暂停只阻止后续触发，已开始任务继续执行。Spring 与关键维护任务只读；状态只反映原调度方法，不表示后台模型或数据库事务已完成。Agent 的单条持久计划在对应聊天工作区管理。</p></DetailPanel>
      <ElAlert v-if="timers?.limited" title="已达到目录观测上限，当前列表不代表全部计时器。" type="warning" :closable="false" />
      <ElCard v-for="timer in timers?.items ?? []" :key="timer.id" shadow="never" class="runtime-card"><div class="editor-heading"><h3>{{ timer.name }}</h3><ElTag>{{ stateNames[timer.state] ?? timer.state }}</ElTag></div>
        <div class="timer-category-filters"><ElTag type="info">{{ timer.categoryName }}</ElTag><ElTag v-if="!timer.actions.length" type="info">只读</ElTag><span class="small muted">{{ timer.source === 'core' ? 'Core Timer' : timer.source === 'spring' ? 'Spring 定时任务' : '扩展调度' }}</span></div>
        <p v-if="timer.description" class="small muted">{{ timer.description }}</p>
        <p>{{ timer.rule.rule }}</p><p class="small muted">{{ timer.rule.waiting ? '等待上个原任务完成' : timer.state === 'ACTIVE' && timer.rule.nextFire ? `预计触发：${date(timer.rule.nextFire)}` : '下次触发时间未确认' }}{{ timer.rule.zone ? ` · ${timer.rule.zone}` : '' }}</p>
        <div class="editor-actions"><ElButton v-if="timer.actions.includes('PAUSE')" :disabled="busy || !!pending" @click="change(timer, true)">暂停</ElButton><ElButton v-if="timer.actions.includes('RESUME')" :disabled="busy || !!pending" @click="change(timer, false)">恢复</ElButton></div><DetailPanel><p class="small revision">计时器 {{ timer.id }}<template v-if="timer.source === 'core'"> · 门禁版本 {{ timer.revision }}</template></p></DetailPanel>
      </ElCard>
      <ElEmpty v-if="timers && !timers.items.length" description="当前筛选没有匹配的定时规则" /><ElButton v-if="timers?.more" :disabled="busy" @click="auto = false; run(() => loadTimers(true))">下一页规则</ElButton>
    </template>
    <template v-else-if="panel === 'runs'">
      <ElCard v-for="item in managementCalls" :key="item.requestId" shadow="never" class="runtime-card">
        <div class="editor-heading"><h3>{{ item.resource }}</h3><ElTag :type="item.waitingEnded ? 'warning' : 'info'">{{ item.waitingEnded ? '等待结束，工作仍在收尾' : '管理调用进行中' }}</ElTag></div>
        <p class="small muted">已占用 {{ (item.ageMillis / 1000).toFixed(1) }} 秒</p>
        <p v-if="advanced" class="small revision">{{ item.requestId }}</p>
      </ElCard>
      <DetailPanel title="任务状态说明"><p>这里只跟踪 Core 任务。完整模型交互与消息发送进度请查看 Agent 活动时间线。</p></DetailPanel>
      <div class="agent-toolbar"><label>任务状态<ElSelect v-model="runState" aria-label="任务状态"><ElOption label="全部状态" value="" /><ElOption v-for="state in ['QUEUED', 'RUNNING', 'WAITING_RESULT', 'SUCCESS', 'FAILED']" :key="state" :label="stateNames[state]" :value="state" /></ElSelect></label><label>任务名称<ElInput v-model="runSearch" maxlength="256" aria-label="任务名称筛选" /></label><ElButton :disabled="busy" @click="run(() => loadRuns())">筛选任务</ElButton></div>
      <p v-if="runs" class="small muted">进程窗口始于 {{ date(runs.startedAt) }} · 已淘汰 {{ runs.dropped }} 项；窗口不保存到数据库。</p>
      <ElCard v-for="item in runs?.items ?? []" :key="item.id" shadow="never" class="runtime-card"><div class="editor-heading"><h3>{{ item.name }}</h3><ElTag>{{ stateNames[item.state] ?? item.state }}</ElTag></div><p>{{ item.stage }}</p><p class="small muted">注册：{{ date(item.createdAt) }}<br />开始：{{ date(item.startedAt) }}<br />原回执结束：{{ date(item.finishedAt) }}</p><p class="small revision">{{ item.id }}</p></ElCard>
      <ElEmpty v-if="runs && !runs.items.length" description="当前窗口没有匹配任务" /><ElButton v-if="runs?.more" :disabled="busy" @click="auto = false; run(() => loadRuns(true))">读取更早任务</ElButton>
    </template>
    <template v-else>
      <DetailPanel title="日志窗口说明"><p>显示启动后的最近日志，页面最多保留 300 条。请求号、聊天与插件筛选依赖来源提供的结构化字段。</p></DetailPanel>
      <div class="log-filters"><label>等级<ElSelect v-model="filters.level" aria-label="日志等级"><ElOption label="全部等级" value="" /><ElOption v-for="level in ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR']" :key="level" :label="level" :value="level" /></ElSelect></label>
        <label>正文包含<ElInput v-model="filters.text" maxlength="256" aria-label="日志正文筛选" /></label></div>
      <DetailPanel title="更多筛选"><div class="log-filters">
        <label>来源包含<ElInput v-model="filters.namespace" maxlength="256" aria-label="日志来源" /></label><label>请求号<ElInput v-model="filters.requestId" maxlength="256" aria-label="日志请求号" /></label><label>聊天标识<ElInput v-model="filters.scope" maxlength="256" aria-label="日志聊天标识" /></label><label>插件标识<ElInput v-model="filters.plugin" maxlength="256" aria-label="日志插件标识" /></label><label>起始时间（本机时区）<input v-model="filters.from" type="datetime-local" /></label><label>结束时间（本机时区）<input v-model="filters.through" type="datetime-local" /></label>
      </div></DetailPanel>
      <p v-if="detailedFiltersActive" class="active-filter-note">已应用详细筛选，可在“更多筛选”中调整。</p>
      <ElButton :disabled="busy" @click="applyFilters">应用日志筛选</ElButton>
      <p v-if="logs" class="small muted">当前窗口最多 {{ logs.policy.maxLogs }} 条 / {{ logs.policy.retentionMinutes }} 分钟 · 丢弃或淘汰 {{ logs.dropped }} 条。</p>
      <ElAlert v-if="logs && !logs.policy.captureMessages" title="当前只采集等级与来源。可在本地配置的“任务与日志观测”中开启日志正文采集，保存后应用；也可在本机修改 config/runtime/observability.yml 并重载。" type="info" :closable="false" />
      <ElCard v-for="log in displayedLogs" :key="`${logs?.instance}-${log.sequence}`" shadow="never" class="runtime-card"><div class="editor-heading"><strong>{{ log.level }}</strong><time class="small muted">{{ date(log.at) }}</time></div><p class="small revision">{{ log.namespace }}</p><pre class="log-message">{{ log.message }}</pre><p v-if="log.requestId || log.scope || log.plugin" class="small revision">{{ log.requestId }} {{ log.scope }} {{ log.plugin }}</p></ElCard>
      <ElEmpty v-if="logs && !displayedLogs.length" description="当前扫描页没有匹配日志" /><ElButton v-if="logs?.more" :disabled="busy" @click="run(() => loadLogs())">继续读取日志</ElButton><RefreshButton v-else :disabled="busy" label="刷新最新日志" @click="run(() => loadLogs())" />
    </template>
    <ElCard v-if="advanced && receipt" shadow="never" class="operation-receipt"><h3>定时器门禁已确认</h3><p>{{ receipt.note }}</p><p>原操作：{{ receipt.operationId }} · 版本 {{ receipt.revision }}</p><p>原提交时间：{{ date(receipt.at) }}；历史回执不代表现在仍是同一状态。</p><ElButton :disabled="busy" @click="operation">查询此回执</ElButton></ElCard>
    </AccessibleTabs>
  </div>
</template>
