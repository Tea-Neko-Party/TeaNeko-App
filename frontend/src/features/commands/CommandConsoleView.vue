<script setup lang="ts">
// 2026/11/10：插件归属统一优先展示名称，稳定 ID 保留在悬停说明及筛选中。
import PluginLabel from '../../shared/plugins/PluginLabel.vue'
// 2026/11/07：按功能目录引用共享能力与页面契约。
// 2026/11/04：刷新类操作统一使用带完整动作提示的图标按钮。
import RefreshButton from '../../shared/components/RefreshButton.vue'
// 2026/11/05：详细操作回执只在高级模式展示。
import {useConsolePreferences} from '../../shared/state/console-preferences'
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {ElAlert, ElButton, ElCard, ElEmpty, ElInput, ElMessageBox, ElTag} from 'element-plus'
import {ApiError, request} from '../../shared/api/client'
import {checkConsoleReceipt} from '../../shared/api/management-receipts'
import DetailPanel from '../../shared/components/DetailPanel.vue'
import ConsoleIcon from '../../shared/components/ConsoleIcon.vue'
import type {AgentTarget} from '../agent/AgentWorkspaceView.vue'
import {usePageDraft} from '../../shared/lifecycle/page-guards'
import {pluginEnabled} from '../../shared/plugins/plugin-console'
import PluginFilter from '../../shared/plugins/PluginFilter.vue'
import {useActivePolling} from '../../shared/lifecycle/active-polling'

const {advanced} = useConsolePreferences()

interface Catalog { targetName: string; state: { state: string; connection: string; explanation: string }; commands: { pluginId?: string | null; usage: string; description: string; sendsMessages: boolean }[]; observedAt: string }
interface Receipt { operationId: string; status: string; commandStatus: string; output: string[]; confirmedMessages: string[]; note: string; at: string }
interface Submission { operationId: string; text: string; connection: string; observedAt: string }
const props = defineProps<{ target: AgentTarget; active: boolean }>()
const emit = defineEmits<{ expired: []; selectTarget: [] }>()
const catalog = ref<Catalog>()
const pluginId = ref('')
const visibleCommands = computed(() => (catalog.value?.commands ?? []).filter(command => pluginEnabled(command.pluginId) && (!pluginId.value || command.pluginId === pluginId.value)))
const receipt = ref<Receipt>()
const original = ref<Submission>()
const text = ref('')
const busy = ref(false)
const error = ref('')
let generation = 0
const ongoing = computed(() => !!receipt.value && ['ACCEPTED', 'RUNNING'].includes(receipt.value.status))
const locked = computed(() => busy.value || !!original.value || ongoing.value)
// 2026/11/06：离开仅停止查回执，不重发或释放原请求；重新进入继续查询。
usePageDraft(() => ({dirty: !!text.value.trim(), pending: !!original.value || ongoing.value, busy: busy.value}))
useActivePolling(() => props.active && ongoing.value, () => { if (!busy.value) void query() }, 2000)
const states: Record<string, string> = { UNSUPPORTED: '尚未支持指令台', DISCONNECTED: '未连接', IDENTITY_PENDING: '等待账号确认', CONFLICT: '连接不唯一', ACCOUNT_MISMATCH: '账号不匹配', PLATFORM_RESTRICTED: '平台当前限用', READY: '可请求执行' }
const outcomes: Record<string, string> = { ACCEPTED: '已受理，尚未确认启动', RUNNING: '原处理链执行中', COMPLETED: '原处理链已结束', REJECTED: '请求被拒绝或未执行完成', INDETERMINATE: '结果未确认，禁止自动重放' }
const endpoint = () => `clients/${encodeURIComponent(props.target.client)}/accounts/${encodeURIComponent(props.target.account)}/targets/${encodeURIComponent(props.target.target)}/console`

async function confirm(message: string, label = '确认操作') {
  try { await ElMessageBox.confirm(message, '指令台', { confirmButtonText: label, cancelButtonText: '取消', type: 'warning' }); return true }
  catch { return false }
}

async function canLeave() {
  if (!original.value && !ongoing.value && !text.value.trim()) return true
  return confirm('切换目标将释放本页草稿及原请求。已受理指令可能继续执行，切换不会撤销它；请先保存操作编号并查询原回执。', '放弃并切换')
}
defineExpose({ canLeave })

async function run(action: () => Promise<void>) {
  if (busy.value) return
  const current = generation
  busy.value = true
  error.value = ''
  try { await action() }
  catch (failure) {
    if (current !== generation) return
    error.value = failure instanceof Error ? failure.message : '指令台请求未完成。'
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
  } finally { if (current === generation) busy.value = false }
}

async function load() {
  const current = generation
  const result = await request<Catalog>(endpoint())
  if (current === generation) catalog.value = result
}

function receive(result: Receipt, current: number) {
  if (current !== generation) return
  checkConsoleReceipt(result, original.value?.operationId ?? receipt.value?.operationId)
  receipt.value = result
  original.value = undefined
  text.value = ''
}

async function deliver() {
  const current = generation
  const body = original.value!
  const result = await request<Receipt>(endpoint(), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
  receive(result, current)
}

async function submit() {
  const current = generation
  const selected = catalog.value
  if (!selected || locked.value || !text.value.trim()) return
  if (!await confirm(`执行目标：${props.target.name}。声明发送能力的命令会向这个真实聊天发送消息；不会自动重试。确认提交当前命令？`, '确认提交')) return
  if (current !== generation) return
  original.value = { operationId: crypto.randomUUID(), text: text.value.trim(), connection: selected.state.connection, observedAt: selected.observedAt }
  receipt.value = undefined
  await run(deliver)
}

async function query() {
  const id = original.value?.operationId ?? receipt.value?.operationId
  if (!id) return
  const current = generation
  await run(async () => { receive(await request<Receipt>(`${endpoint()}/operations/${id}`), current) })
}

async function discard() {
  if (!await confirm('放弃本页原请求不撤销已受理指令，也不证明此前没有发送。请先查询原回执，确认后再准备新操作。', '放弃并刷新')) return
  original.value = undefined
  await run(load)
}

function beforeUnload(event: BeforeUnloadEvent) {
  if (original.value || ongoing.value || text.value.trim()) { event.preventDefault(); event.returnValue = '' }
}

watch(() => props.target, () => {
  generation++
  busy.value = false
  catalog.value = undefined
  receipt.value = undefined
  original.value = undefined
  text.value = ''
  void run(load)
})
onMounted(() => {
  void run(load)
  window.addEventListener('beforeunload', beforeUnload)
})
onBeforeUnmount(() => { generation++; original.value = undefined; window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <!-- 2026/11/04：收起常驻长说明，实际发送标记与操作回执保持可见。 -->
  <div class="command-console">
    <!-- 2026/11/05：与聊天和 Agent 工作区统一返回图标，不改变进行中操作的禁用约束。 -->
    <div class="workspace-heading"><button class="icon-button" :disabled="busy" aria-label="返回聊天列表" title="返回聊天列表" @click="emit('selectTarget')"><ConsoleIcon name="back" /></button><h2>{{ target.name }}</h2></div>
    <DetailPanel title="指令台说明"><p>以网页管理员身份执行 TeaNeko 命令，继续检查原命令权限和平台连接。标注“会向真实聊天发送”的命令会产生外部消息。</p></DetailPanel>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <ElCard v-if="catalog" shadow="never" class="runtime-card"><div class="editor-heading"><h3>当前目标 · {{ catalog.targetName }}</h3><ElTag :type="catalog.state.state === 'READY' ? 'success' : 'warning'">{{ states[catalog.state.state] ?? catalog.state.state }}</ElTag></div><p>{{ catalog.state.explanation }}</p><p class="small muted">目录时刻：{{ new Date(catalog.observedAt).toLocaleString('zh-CN') }}；新受理需使用五分钟内的目录。下方最多显示 100 个明确声明的管理方法，最终仍按当前权限和作用域校验。</p><RefreshButton :disabled="locked" @click="run(load)" label="刷新目录与连接" /></ElCard>
    <ElCard shadow="never" class="runtime-card"><h3>命令及用法</h3><PluginFilter v-model="pluginId" :items="catalog?.commands ?? []" /><ElEmpty v-if="catalog && !visibleCommands.length" description="没有已开放的管理命令；请检查本机 console-prefixes 与方法声明" />
      <div v-for="command in visibleCommands" :key="command.usage" class="console-command"><strong>{{ command.usage }}</strong><ElTag v-if="command.pluginId" type="info"><PluginLabel :plugin-id="command.pluginId" /></ElTag><ElTag v-if="command.sendsMessages" type="warning" size="small">会向真实聊天发送</ElTag><p class="evidence-text">{{ command.description }}</p><ElButton text :disabled="locked" @click="text = command.usage + ' '">填入命令</ElButton></div>
    </ElCard>
    <ElCard shadow="never" class="runtime-card"><label for="console-command-input">命令正文</label><ElInput id="console-command-input" v-model="text" type="textarea" :rows="4" maxlength="4096" :disabled="locked" placeholder="例如 /console 或 /console say 大家好；这里不执行操作系统命令或任意平台 JSON" aria-label="指令台命令正文" autocomplete="off" /><div class="editor-actions"><ElButton type="primary" :disabled="locked || catalog?.state.state !== 'READY' || !visibleCommands.length || !text.trim()" @click="submit">提交指令</ElButton></div></ElCard>
    <ElCard v-if="original" shadow="never" class="pending-operation"><h3>原请求受理结果待确认</h3><p class="small revision">操作编号：{{ original.operationId }}</p><p class="small muted">连接中断不等于未执行。原请求只保留在当前页面内存；先查询原编号，不能更换编号猜测重发。</p><ElButton :disabled="busy" @click="query">查询原回执</ElButton><ElButton :disabled="busy" @click="run(deliver)">原样重试</ElButton><RefreshButton :disabled="busy" @click="discard" label="放弃原请求并刷新" /></ElCard>
    <!-- 2026/11/05：简洁模式保留真正的指令输出，只隐藏回执诊断。 -->
    <template v-if="!advanced && receipt"><p role="status">{{ outcomes[receipt.status] ?? receipt.status }}<span v-if="receipt.note"> · {{ receipt.note }}</span></p><pre v-for="(line, index) in receipt.output" :key="index" class="log-message">{{ line }}</pre></template>
    <ElCard v-if="advanced && receipt" shadow="never" class="operation-receipt"><h3>{{ outcomes[receipt.status] ?? receipt.status }}</h3><p>{{ receipt.note }}</p><p>操作编号：{{ receipt.operationId }}</p><p>原分发状态：{{ receipt.commandStatus || '尚未确认' }} · {{ new Date(receipt.at).toLocaleString('zh-CN') }}</p><p v-if="ongoing" class="small muted">页面可见时每两秒查询原状态，不会重新提交命令。</p><pre v-for="(line, index) in receipt.output" :key="index" class="log-message">{{ line }}</pre><p v-if="receipt.confirmedMessages.length" class="small revision">平台已确认消息：{{ receipt.confirmedMessages.join('、') }}</p><p v-else class="small muted">当前没有已记录的平台消息确认；这不表示此前必然没有产生外部动作。</p><ElButton :disabled="busy" @click="query">查询此回执</ElButton></ElCard>
  </div>
</template>
