<script setup lang="ts">
// 2026/11/04：刷新类操作统一使用带完整动作提示的图标按钮。
import RefreshButton from './RefreshButton.vue'
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {ElAlert, ElButton, ElCard, ElEmpty, ElInput, ElMessageBox, ElOption, ElSelect, ElTag} from 'element-plus'
import {ApiError, request} from './api'
import DetailPanel from './DetailPanel.vue'

interface Client {client: string; name: string; disk: string; active: string}
interface View {revision: string; runtime: {revision: string; current: boolean}; clients: Client[]}
interface Receipt {operationId: string; status: string; note: string; replayed: boolean}
interface Pending {path: string; body: {operationId: string; expectedRevision: string; action?: string; value?: string}}
const props = defineProps<{active: boolean}>()
const emit = defineEmits<{expired: []; applied: []}>()
const view = ref<View>()
const editing = ref('')
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
    <div class="section-title"><h2>机器令牌</h2><RefreshButton :disabled="locked || !!editing" @click="run(load)" label="刷新" /></div>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <ElAlert v-if="notice" :title="notice" type="info" :closable="true" @close="notice = ''" />
    <ElEmpty v-if="view && !view.clients.length" description="没有已注册的机器连接入口" />
    <ElCard v-for="client in view?.clients ?? []" :key="client.client" shadow="never" class="runtime-card">
      <div class="editor-heading"><h3>{{ client.name }}</h3><ElTag :type="client.active === 'OPEN' ? 'warning' : client.active === 'DENIED' ? 'info' : 'success'">{{ names[client.active] }}</ElTag></div>
      <p v-if="unapplied" class="small muted">磁盘策略：{{ names[client.disk] }} · 尚有未应用的文件版本</p>
      <div v-if="editing !== client.client" class="editor-actions">
        <ElButton v-if="client.disk !== 'DENIED' && revealed[client.client] === undefined" :disabled="locked || !!editing" @click="reveal(client.client)">查看令牌</ElButton>
        <template v-if="revealed[client.client] !== undefined"><ElInput :model-value="revealed[client.client]" readonly :aria-label="`${client.name}当前令牌`" /><ElButton :disabled="busy" @click="copy(client.client)">复制</ElButton><ElButton @click="delete revealed[client.client]">隐藏</ElButton></template>
        <ElButton :disabled="locked || !!editing" @click="edit(client)">修改</ElButton>
      </div>
      <div v-else class="token-editor">
        <ElSelect v-model="action" :disabled="locked" aria-label="机器令牌策略"><ElOption label="设置令牌" value="SET" /><ElOption label="空白放行（允许任意令牌）" value="OPEN" /><ElOption label="移除配置（拒绝连接）" value="DENY" /></ElSelect>
        <ElInput v-if="action === 'SET'" v-model="value" type="password" show-password maxlength="256" :disabled="locked" placeholder="新令牌或环境占位符；空白即放行" aria-label="新机器令牌" autocomplete="off" />
        <div class="editor-actions"><ElButton type="primary" :disabled="locked" @click="save">保存到磁盘</ElButton><ElButton :disabled="locked" @click="editing = ''; value = ''">取消</ElButton></div>
      </div>
    </ElCard>
    <div v-if="view?.clients.length" class="editor-actions"><ElButton type="primary" :disabled="locked || !!editing || !unapplied" @click="apply">应用机器令牌</ElButton><span v-if="unapplied" class="small muted">保存与应用分开，管理员令牌不会更改。</span></div>
    <ElCard v-if="pending" shadow="never" class="pending-operation"><h3>原操作尚待确认</h3><p class="small revision">{{ pending.body.operationId }}</p><p class="small muted">中断不代表没有完成。应用后若登录失效，请重新登录并查询此编号。</p><div class="editor-actions"><ElButton :disabled="busy" @click="query">查询原操作</ElButton><ElButton :disabled="busy" @click="run(deliver)">原样重试</ElButton><ElButton :disabled="busy" @click="discard">放弃原请求</ElButton></div></ElCard>
    <ElCard v-if="receipt" shadow="never" class="operation-receipt"><h3>{{ statusNames[receipt.status] ?? receipt.status }}</h3><p>{{ receipt.status === 'APPLIED' && receipt.replayed ? '该操作已应用；当前状态以目录为准。' : receipt.note }}</p></ElCard>
    <DetailPanel title="连接与操作说明"><p>机器令牌只用于机器连接，与管理员登录分开。空白放行仍要求正确的 HTTPS/WSS 入口；未配置客户端继续拒绝。</p><p>查看返回的是当前磁盘令牌，环境模板会展开；修改原文时仅替换明确选择的客户端。</p><p v-if="lastOperation" class="small revision">最近操作：{{ lastOperation }}</p><div v-if="lastOperation" class="editor-actions"><ElButton :disabled="busy" @click="query">查询最近操作</ElButton><ElButton :disabled="locked" @click="rememberOperation('')">清除编号记录</ElButton></div></DetailPanel>
  </section>
</template>
