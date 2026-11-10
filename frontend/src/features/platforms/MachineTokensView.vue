<script setup lang="ts">
// 2026/11/10：插件归属统一优先展示名称，稳定 ID 保留在悬停说明及筛选中。
import PluginLabel from '../../shared/plugins/PluginLabel.vue'
// 2026/11/07：按功能目录引用共享能力与页面契约。
// 2026/11/04：刷新类操作统一使用带完整动作提示的图标按钮。
import RefreshButton from '../../shared/components/RefreshButton.vue'
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {ElAlert, ElButton, ElCard, ElEmpty, ElInput, ElMessageBox, ElOption, ElSelect, ElTag} from 'element-plus'
import {ApiError, request} from '../../shared/api/client'
import DetailPanel from '../../shared/components/DetailPanel.vue'
import ConsoleIcon from '../../shared/components/ConsoleIcon.vue'
import {usePageDraft} from '../../shared/lifecycle/page-guards'
import {pluginAvailability, pluginEnabled} from '../../shared/plugins/plugin-console'
import PluginFilter from '../../shared/plugins/PluginFilter.vue'

// 2026/11/05：公开连接地址与后端代理端口分开显示，普通目录不包含令牌。
interface Client {pluginId?: string | null; client: string; name: string; disk: string; active: string; address: string; port: number; backendPort: number}
interface View {revision: string; runtime: {revision: string; current: boolean}; clients: Client[]}
interface Receipt {operationId: string; status: string; note: string; replayed: boolean}
interface Pending {path: string; body: {operationId: string; expectedRevision: string; action?: string; value?: string}}
const props = defineProps<{active: boolean}>()
const emit = defineEmits<{expired: []; applied: []}>()
const view = ref<View>()
const editing = ref('')
const owner = ref('')
const availableClients = computed(() => (view.value?.clients ?? []).filter(client => pluginEnabled(client.pluginId)))
const matchingClients = computed(() => availableClients.value.filter(client => !owner.value || client.pluginId === owner.value))
const action = ref('SET')
const value = ref('')
const revealed = ref<Record<string, string>>({})
const busy = ref(false)
const error = ref('')
const notice = ref('')
const receipt = ref<Receipt>()
const pending = ref<Pending>()
const lastOperation = ref(storedOperation())
const names: Record<string, string> = {DENIED: '未配置，拒绝连接', OPEN: '任意令牌可连接', PROTECTED: '需要令牌'}
const statusNames: Record<string, string> = {PREPARED: '保存结果待确认', SAVED: '原保存已确认', APPLYING: '应用结果待确认', APPLIED: '原应用已确认', APPLY_FAILED: '原应用未确认', NOT_SAVED: '原文件未保存', INDETERMINATE: '原结果无法确认'}
const unapplied = computed(() => view.value && (!view.value.runtime.current || view.value.runtime.revision !== view.value.revision))
const locked = computed(() => busy.value || !!pending.value)
// 2026/11/06：令牌编辑和未确认写入同样阻止无提示释放会话状态。
usePageDraft(() => ({dirty: !!editing.value, pending: !!pending.value, busy: busy.value}))

// 2026/11/04：跨重新登录仅保留非秘密操作编号，令牌与原请求从不写入浏览器存储。
function storedOperation(): string {
  try {
    const stored = sessionStorage.getItem('teaneko.console.machine-operation') || ''
    return /^[0-9a-f-]{36}$/i.test(stored) ? stored : ''
  } catch {
    return ''
  }
}

function rememberOperation(id: string) {
  lastOperation.value = id
  try {
    if (id) sessionStorage.setItem('teaneko.console.machine-operation', id)
    else sessionStorage.removeItem('teaneko.console.machine-operation')
  } catch {
    // 2026/11/04：受限浏览器仍在当前页面显示编号，允许操作者复制。
  }
}

async function run(work: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await work()
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : '机器令牌操作未完成。'
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
  } finally {
    busy.value = false
  }
}

async function load() {
  view.value = await request<View>('client-tokens')
  revealed.value = {}
}

function edit(client: Client) {
  editing.value = client.client
  action.value = client.disk === 'OPEN' ? 'OPEN' : 'SET'
  value.value = ''
}

async function reveal(client: string) {
  await run(async () => {
    const result = await request<{revision: string; value: string}>(`client-tokens/${encodeURIComponent(client)}/reveal`, {method: 'POST'})
    if (view.value?.revision !== result.revision) {
      notice.value = '磁盘版本已变化，请重新读取后查看。'
      return
    }
    if (!availableClients.value.some(item => item.client === client)) return
    revealed.value[client] = result.value
  })
}

async function copy(client: string) {
  await run(async () => {
    await navigator.clipboard.writeText(revealed.value[client]!)
    notice.value = '令牌已复制。'
  })
}

async function deliver() {
  const original = pending.value!
  const result = await request<Receipt>(original.path, {
    method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(original.body),
  })
  receipt.value = result
  pending.value = undefined
  editing.value = ''
  value.value = ''
  if (result.status === 'APPLIED' && !result.replayed) {
    revealed.value = {}
    emit('applied')
    return
  }
  await load()
}

async function save() {
  const client = editing.value
  if (!client || !view.value) return
  try {
    await ElMessageBox.confirm(`只修改此客户端的机器令牌。${action.value === 'OPEN' || action.value === 'SET' && !value.value.trim() ? '空白表示允许任意令牌连接。' : ''}保存会重新排版 YAML 并丢失原注释，其他值与环境模板保留。保存后仍需应用。`, '保存机器令牌', {confirmButtonText: '保存', cancelButtonText: '取消'})
  } catch {
    return
  }
  const body: Pending['body'] = {operationId: crypto.randomUUID(), expectedRevision: view.value.revision, action: action.value}
  if (action.value === 'SET') body.value = value.value
  pending.value = {path: `client-tokens/${encodeURIComponent(client)}/actions/save`, body}
  rememberOperation(body.operationId)
  value.value = ''
  await run(deliver)
}

async function apply() {
  if (!view.value) return
  try {
    await ElMessageBox.confirm('应用磁盘上的机器令牌。旧机器连接将重新校验，本次管理员会话将失效，需要重新登录；管理员令牌保持不变。', '应用机器令牌', {confirmButtonText: '应用并重新登录', cancelButtonText: '取消'})
  } catch {
    return
  }
  const body = {operationId: crypto.randomUUID(), expectedRevision: view.value.revision}
  pending.value = {path: 'client-tokens/actions/apply', body}
  rememberOperation(body.operationId)
  await run(deliver)
}

async function query() {
  if (!lastOperation.value) return
  await run(async () => {
    const result = await request<Receipt>(`client-tokens/operations/${lastOperation.value}`)
    receipt.value = result
    if (result.status !== 'NOT_SAVED') {
      pending.value = undefined
      editing.value = ''
      value.value = ''
    }
    await load()
  })
}

async function discard() {
  try {
    await ElMessageBox.confirm('只释放本页原请求，不撤销已经保存或应用的内容。操作编号仍可查询。', '放弃原请求', {confirmButtonText: '确认', cancelButtonText: '取消'})
  } catch {
    return
  }
  pending.value = undefined
  await run(load)
}

// 2026/11/04：离开客户端页即收起已显示的秘密，编辑草稿仍只保留在组件内存。
watch(() => props.active, active => { if (!active) revealed.value = {} })
// 2026/11/07：撤销插件资格同时移除秘密、编辑器与该客户端的待确认操作。
watch(pluginAvailability, () => {
  const allowed = new Set(availableClients.value.map(item => item.client))
  revealed.value = Object.fromEntries(Object.entries(revealed.value).filter(([client]) => allowed.has(client)))
  if (editing.value && !allowed.has(editing.value)) { editing.value = ''; value.value = '' }
  const client = pending.value?.path.match(/^client-tokens\/([^/]+)\/actions\/save$/)?.[1]
  if (client && !allowed.has(decodeURIComponent(client))) { pending.value = undefined; receipt.value = undefined; rememberOperation('') }
})
function beforeUnload(event: BeforeUnloadEvent) {
  if (editing.value || pending.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
onMounted(async () => {
  window.addEventListener('beforeunload', beforeUnload)
  await run(load)
  if (lastOperation.value) await query()
})
</script>

<template>
  <!-- 2026/11/04：目录默认无秘密，只有明确查看或编辑的客户端显示输入。 -->
  <section class="machine-tokens" aria-label="机器令牌管理">
    <div class="section-title"><h2>客户端连接</h2><RefreshButton :disabled="locked || !!editing" @click="run(load)" label="刷新" /></div>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <ElAlert v-if="notice" :title="notice" type="info" :closable="true" @close="notice = ''" />
    <PluginFilter v-if="availableClients.some(client => client.pluginId)" v-model="owner" :items="availableClients" />
    <ElEmpty v-if="view && !matchingClients.length" description="没有已注册的机器连接入口" />
    <ElCard v-for="client in matchingClients" :key="client.client" shadow="never" class="runtime-card">
      <div class="editor-heading"><h3>{{ client.name }}</h3><ElTag v-if="client.pluginId" type="info"><PluginLabel :plugin-id="client.pluginId" /></ElTag><ElTag :type="client.active === 'OPEN' ? 'warning' : client.active === 'DENIED' ? 'info' : 'success'">{{ names[client.active] }}</ElTag></div>
      <!-- 2026/11/05：同时展示公开 WSS 和服务器本机 WS，两种传输沿用同一机器令牌。 -->
      <p class="small">公开 WSS 地址</p><p class="connection-address"><code>{{ client.address }}</code></p>
      <p class="small">服务器本机 WS 地址</p><p class="connection-address"><code>ws://127.0.0.1:{{ client.backendPort }}/ws/{{ client.client }}</code></p>
      <p class="small muted">公开 WSS 端口 {{ client.port }} · 后端 WS/HTTP 端口 {{ client.backendPort }}</p>
      <p v-if="unapplied" class="small muted">磁盘策略：{{ names[client.disk] }} · 尚有未应用的文件版本</p>
      <div v-if="editing !== client.client" class="token-display">
        <label :for="`machine-token-${client.client}`">Token（磁盘配置）</label>
        <div class="token-value-row">
          <ElInput :id="`machine-token-${client.client}`" :model-value="revealed[client.client] ?? (client.disk === 'DENIED' ? '未配置' : '••••••••')"
                   readonly :placeholder="client.disk === 'OPEN' ? '空白，允许任意令牌' : ''" :aria-label="`${client.name}当前令牌`" autocomplete="off" />
          <button class="icon-button" :disabled="locked || !!editing || client.disk === 'DENIED'"
                  :aria-label="revealed[client.client] === undefined ? `查看${client.name}令牌` : `隐藏${client.name}令牌`"
                  :title="revealed[client.client] === undefined ? '查看令牌' : '隐藏令牌'"
                  @click="revealed[client.client] === undefined ? reveal(client.client) : delete revealed[client.client]">
            <ConsoleIcon :name="revealed[client.client] === undefined ? 'eye' : 'eyeOff'" />
          </button>
          <ElButton v-if="revealed[client.client] !== undefined" :disabled="busy" @click="copy(client.client)">复制</ElButton>
        </div>
        <div class="editor-actions"><ElButton :disabled="locked || !!editing" @click="edit(client)">修改 Token</ElButton></div>
      </div>
      <div v-else class="token-editor">
        <ElSelect v-model="action" :disabled="locked" aria-label="机器令牌策略"><ElOption label="设置令牌" value="SET" /><ElOption label="空白放行（允许任意令牌）" value="OPEN" /><ElOption label="移除配置（拒绝连接）" value="DENY" /></ElSelect>
        <ElInput v-if="action === 'SET'" v-model="value" type="password" show-password maxlength="256" :disabled="locked" placeholder="新令牌或环境占位符；空白即放行" aria-label="新机器令牌" autocomplete="off" />
        <div class="editor-actions"><ElButton type="primary" :disabled="locked" @click="save">保存到磁盘</ElButton><ElButton :disabled="locked" @click="editing = ''; value = ''">取消</ElButton></div>
      </div>
    </ElCard>
    <!-- 2026/11/05：公开 WSS 端口可独立配置，容器内网通过服务名使用 WS。 -->
    <p v-if="availableClients.length" class="small muted">Docker 内网使用后端服务名及 server.port（默认 6691）连接。公开 WSS 地址通过 tea-neko.websocket.public-origin 配置，端口由 TLS 代理监听；修改后重启。</p>
    <div v-if="availableClients.length" class="editor-actions"><ElButton type="primary" :disabled="locked || !!editing || !unapplied" @click="apply">应用机器令牌</ElButton><span v-if="unapplied" class="small muted">保存与应用分开，管理员令牌不会更改。</span></div>
    <ElCard v-if="pending" shadow="never" class="pending-operation"><h3>原操作尚待确认</h3><p class="small revision">{{ pending.body.operationId }}</p><p class="small muted">中断不代表没有完成。应用后若登录失效，请重新登录并查询此编号。</p><div class="editor-actions"><ElButton :disabled="busy" @click="query">查询原操作</ElButton><ElButton :disabled="busy" @click="run(deliver)">原样重试</ElButton><ElButton :disabled="busy" @click="discard">放弃原请求</ElButton></div></ElCard>
    <ElCard v-if="receipt" shadow="never" class="operation-receipt"><h3>{{ statusNames[receipt.status] ?? receipt.status }}</h3><p>{{ receipt.status === 'APPLIED' && receipt.replayed ? '该操作已应用；当前状态以目录为准。' : receipt.note }}</p></ElCard>
    <DetailPanel title="连接与操作说明"><p>机器令牌用于 WS 和 WSS 连接，与管理员登录分开。未配置客户端继续拒绝；空白放行允许任意令牌。内网 WS 的可达范围由监听地址、Docker 端口发布和防火墙控制。</p><p>查看返回的是当前磁盘令牌，环境模板会展开；修改原文时仅替换明确选择的客户端。</p><p v-if="lastOperation" class="small revision">最近操作：{{ lastOperation }}</p><div v-if="lastOperation" class="editor-actions"><ElButton :disabled="busy" @click="query">查询最近操作</ElButton><ElButton :disabled="locked" @click="rememberOperation('')">清除编号记录</ElButton></div></DetailPanel>
  </section>
</template>
