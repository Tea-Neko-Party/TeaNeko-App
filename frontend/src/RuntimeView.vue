<script setup lang="ts">
import {onBeforeUnmount, onMounted, reactive, ref} from 'vue'
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

interface Timer { id: string; name: string; state: string; revision: number; rule: { kind: string; rule: string; nextFire?: string; waiting: boolean; zone: string }; actions: string[] }
interface TimerPage { items: Timer[]; next: string; more: boolean; limited: boolean }
interface Run { id: string; name: string; state: string; stage: string; createdAt: string; startedAt?: string; finishedAt?: string }
interface RunPage { instance: string; startedAt: string; items: Run[]; next: number; reset: boolean; more: boolean; dropped: number }
interface Log { sequence: number; at: string; level: string; namespace: string; message: string; requestId: string; scope: string; plugin: string }
interface LogPage { instance: string; items: Log[]; next: number; more: boolean; reset: boolean; gap: boolean; dropped: number; policy: { maxLogs: number; retentionMinutes: number; captureMessages: boolean } }
interface Receipt { operationId: string; timer: string; revision: number; paused: boolean; at: string; note: string }
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
const logs = ref<LogPage>()
const displayedLogs = ref<Log[]>([])
const runState = ref('')
const runSearch = ref('')
const pending = ref<Pending>()
const receipt = ref<Receipt>()
const filters = reactive({ level: '', namespace: '', text: '', requestId: '', scope: '', plugin: '', from: '', through: '' })
const applied = ref({ ...filters })
const stateNames: Record<string, string> = { ACTIVE: '等待触发', PAUSED: '网页已暂停', SOURCE_PAUSED: '来源已暂停', REMOVED: '已移除', UNKNOWN: '状态未知', QUEUED: '排队中', RUNNING: '同步执行中', WAITING_RESULT: '等待外部结果', SUCCESS: '原任务成功', FAILED: '原任务失败' }
const date = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '尚未确认'
let interval: ReturnType<typeof setInterval> | undefined

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
  timers.value = await call<TimerPage>('runtime', query('TIMERS', { after: more ? timers.value?.next ?? '' : '' }))
}

async function loadRuns(more = false) {
  runs.value = await call<RunPage>('runtime', query('RUNS', { before: more ? runs.value?.next ?? 0 : 0, instance: runs.value?.instance ?? '', state: runState.value, search: runSearch.value }))
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

async function receive(result: Receipt) {
  receipt.value = result
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
  interval = setInterval(() => {
    if (props.active && auto.value && !document.hidden && !busy.value && !pending.value) void run(refresh)
  }, 5000)
})
onBeforeUnmount(() => { clearInterval(interval); window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <div class="runtime-view">
    <div class="agent-tabs" role="tablist" aria-label="运行管理分区"><button v-for="item in [{ id: 'timers', name: '定时规则' }, { id: 'runs', name: 'Core 任务' }, { id: 'logs', name: '运行日志' }]" :key="item.id" role="tab" :aria-selected="panel === item.id" :class="{ selected: panel === item.id }" :disabled="busy" @click="select(item.id)">{{ item.name }}</button></div>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <ElAlert v-if="notice" :title="notice" type="info" :closable="false" />
    <div class="editor-actions"><ElButton :disabled="busy" @click="run(refresh)">刷新当前页</ElButton><label class="small muted">每 5 秒刷新 <ElSwitch v-model="auto" :disabled="!!pending" /></label></div>
    <ElCard v-if="pending" shadow="never" class="pending-operation"><h3>定时器操作尚待确认</h3><p class="small revision">{{ pending.change.operationId }}</p><p class="small muted">中断不代表没有执行，先查原回执。回执只在原进程保留。</p><ElButton :disabled="busy" @click="operation">查询原回执</ElButton><ElButton :disabled="busy" @click="run(deliver)">原样重试</ElButton><ElButton :disabled="busy" @click="discard">重新读取并放弃原请求</ElButton></ElCard>
    <template v-if="panel === 'timers'">
      <p class="muted small">这里是原 Core 调度器的真实注册规则。暂停只阻止后续触发；等待上个任务完成时不编造倒计时。Agent 持久计划在对应聊天的 Agent 工作区查看。</p>
      <ElAlert v-if="timers?.limited" title="已达到目录观测上限，当前列表不代表全部计时器。" type="warning" :closable="false" />
      <ElCard v-for="timer in timers?.items ?? []" :key="timer.id" shadow="never" class="runtime-card"><div class="editor-heading"><h3>{{ timer.name }}</h3><ElTag>{{ stateNames[timer.state] ?? timer.state }}</ElTag></div>
        <p>{{ timer.rule.rule }}</p><p class="small muted">{{ timer.rule.waiting ? '等待上个原任务完成' : timer.state === 'ACTIVE' ? `预计触发：${date(timer.rule.nextFire)}` : '暂停或未知状态下不承诺下一次触发' }}{{ timer.rule.zone ? ` · ${timer.rule.zone}` : '' }}</p>
        <p class="small revision">计时器 {{ timer.id }} · 门禁版本 {{ timer.revision }}</p><div class="editor-actions"><ElButton v-if="timer.actions.includes('PAUSE')" :disabled="busy || !!pending" @click="change(timer, true)">暂停后续触发</ElButton><ElButton v-if="timer.actions.includes('RESUME')" :disabled="busy || !!pending" @click="change(timer, false)">恢复后续触发</ElButton></div>
      </ElCard>
      <ElEmpty v-if="timers && !timers.items.length" description="本页没有已注册定时器" /><ElButton v-if="timers?.more" :disabled="busy" @click="auto = false; run(() => loadTimers(true))">下一页规则</ElButton>
    </template>
    <template v-else-if="panel === 'runs'">
      <ElAlert title="这里跟踪原 Core Task 回执。指令提交任务结束不表示外层模型、发送或业务链已结束；这些结果请查看 Agent 活动时间线。当前不提供无法确认物理停止的通用强制取消。" type="info" :closable="false" />
      <div class="agent-toolbar"><label>任务状态<ElSelect v-model="runState" aria-label="任务状态"><ElOption label="全部状态" value="" /><ElOption v-for="state in ['QUEUED', 'RUNNING', 'WAITING_RESULT', 'SUCCESS', 'FAILED']" :key="state" :label="stateNames[state]" :value="state" /></ElSelect></label><label>任务名称<ElInput v-model="runSearch" maxlength="256" aria-label="任务名称筛选" /></label><ElButton :disabled="busy" @click="run(() => loadRuns())">筛选任务</ElButton></div>
      <p v-if="runs" class="small muted">进程窗口始于 {{ date(runs.startedAt) }} · 已淘汰 {{ runs.dropped }} 项；窗口不保存到数据库。</p>
      <ElCard v-for="item in runs?.items ?? []" :key="item.id" shadow="never" class="runtime-card"><div class="editor-heading"><h3>{{ item.name }}</h3><ElTag>{{ stateNames[item.state] ?? item.state }}</ElTag></div><p>{{ item.stage }}</p><p class="small muted">注册：{{ date(item.createdAt) }}<br />开始：{{ date(item.startedAt) }}<br />原回执结束：{{ date(item.finishedAt) }}</p><p class="small revision">{{ item.id }}</p></ElCard>
      <ElEmpty v-if="runs && !runs.items.length" description="当前窗口没有匹配任务" /><ElButton v-if="runs?.more" :disabled="busy" @click="auto = false; run(() => loadRuns(true))">读取更早任务</ElButton>
    </template>
    <template v-else>
      <p class="small muted">采集应用就绪后的实际日志。结构化请求号、聊天标识与插件字段仅在来源提供时可筛选，不从正文猜测。页面最多显示最近读取的 300 条，窗口不是持久日志档案。</p>
      <div class="log-filters"><label>等级<ElSelect v-model="filters.level" aria-label="日志等级"><ElOption label="全部等级" value="" /><ElOption v-for="level in ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR']" :key="level" :label="level" :value="level" /></ElSelect></label>
        <label>来源包含<ElInput v-model="filters.namespace" maxlength="256" aria-label="日志来源" /></label><label>正文包含<ElInput v-model="filters.text" maxlength="256" aria-label="日志正文筛选" /></label><label>请求号<ElInput v-model="filters.requestId" maxlength="256" /></label><label>聊天标识<ElInput v-model="filters.scope" maxlength="256" /></label><label>插件标识<ElInput v-model="filters.plugin" maxlength="256" /></label><label>起始时间（本机时区）<input v-model="filters.from" type="datetime-local" /></label><label>结束时间（本机时区）<input v-model="filters.through" type="datetime-local" /></label></div>
      <ElButton :disabled="busy" @click="applyFilters">应用日志筛选</ElButton>
      <p v-if="logs" class="small muted">当前窗口最多 {{ logs.policy.maxLogs }} 条 / {{ logs.policy.retentionMinutes }} 分钟 · 丢弃或淘汰 {{ logs.dropped }} 条。</p>
      <ElAlert v-if="logs && !logs.policy.captureMessages" title="当前只采集等级与来源。需要正文时，在本机 config/runtime/observability.yml 开启 capture-messages 并重载；不要让业务日志输出裸秘密或私密消息。" type="info" :closable="false" />
      <ElCard v-for="log in displayedLogs" :key="`${logs?.instance}-${log.sequence}`" shadow="never" class="runtime-card"><div class="editor-heading"><strong>{{ log.level }}</strong><time class="small muted">{{ date(log.at) }}</time></div><p class="small revision">{{ log.namespace }}</p><pre class="log-message">{{ log.message }}</pre><p v-if="log.requestId || log.scope || log.plugin" class="small revision">{{ log.requestId }} {{ log.scope }} {{ log.plugin }}</p></ElCard>
      <ElEmpty v-if="logs && !displayedLogs.length" description="当前扫描页没有匹配日志" /><ElButton :disabled="busy" @click="run(() => loadLogs())">{{ logs?.more ? '继续读取日志' : '读取最新日志' }}</ElButton>
    </template>
    <ElCard v-if="receipt" shadow="never" class="operation-receipt"><h3>定时器门禁已确认</h3><p>{{ receipt.note }}</p><p>原操作：{{ receipt.operationId }} · 版本 {{ receipt.revision }}</p><p>原提交时间：{{ date(receipt.at) }}；历史回执不代表现在仍是同一状态。</p><ElButton :disabled="busy" @click="operation">查询此回执</ElButton></ElCard>
  </div>
</template>
