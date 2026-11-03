<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {ElAlert, ElButton, ElCard, ElEmpty, ElInput, ElMessageBox, ElOption, ElSelect, ElSwitch} from 'element-plus'
import type {Platform} from './api'
import {ApiError, request} from './api'

interface Account { ref: string; name: string; platformId: string; observedAt: string }
interface Target { ref: string; kind: string; name: string; platformId: string; source: string; shared: boolean; observedAt: string }
interface Page { items: Target[]; next: string; importMore: boolean }
interface Section { id: string; name: string }
interface Field { name: string; label: string; group: string; kind: string; description: string; value: string; defaultValue: string; source: string; editable: boolean; choices: Record<string, string> }
interface Receipt { operationId: string; revision: string; registered: boolean; action: string; fields: string[]; at: string; replayed: boolean }
interface View { section: string; name: string; targetName: string; registered: boolean; revision: string; sharedExplanation: string; fields: Field[] }
interface Change { operationId: string; expectedRevision: string; action: string; fields: Record<string, string> }

const props = defineProps<{ clients: Platform[]; initialClient: string }>()
const emit = defineEmits<{ expired: []; inspect: [target: { client: string; account: string; target: string; name: string }]; console: [target: { client: string; account: string; target: string; name: string }] }>()
const client = ref('')
const account = ref('')
const accounts = ref<Account[]>([])
const targets = ref<Target[]>([])
const next = ref('')
const kind = ref('')
const search = ref('')
const selected = ref<Target>()
const sections = ref<Section[]>([])
const section = ref('agent')
const view = ref<View>()
const values = ref<Record<string, string>>({})
const busy = ref(false)
const error = ref('')
const notice = ref('')
const pending = ref<Change>()
const lastReceipt = ref<Receipt>()
const available = computed(() => props.clients.filter(value => value.settingsAvailable))
const visibleTargets = computed(() => targets.value.filter(value => `${value.name} ${value.platformId}`.toLowerCase().includes(search.value.toLowerCase())))
const dirty = computed(() => !!view.value && view.value.fields.some(field => field.editable && values.value[field.name] !== field.value))
const groups = computed(() => [...new Set(view.value?.fields.map(field => field.group) ?? [])])
const fieldsIn = (group: string) => view.value?.fields.filter(field => field.group === group) ?? []
const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })
const part = (value: string) => encodeURIComponent(value)
const defaultLabel = (field: Field) => field.kind === 'BOOLEAN' ? (field.defaultValue === 'true' ? '开启' : '关闭')
  : field.choices[field.defaultValue] ?? (field.defaultValue || '留空')
const targetPath = () => `clients/${part(client.value)}/accounts/${part(account.value)}/targets/${part(selected.value!.ref)}/settings/${part(section.value)}`

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
      ? `上次操作结果尚未确认，编号 ${pending.value.operationId}。仍要离开这份设置吗？`
      : '当前修改尚未保存，是否放弃这份草稿？', '切换聊天', { confirmButtonText: '放弃并切换', cancelButtonText: '留在当前设置', type: 'warning' })
    return true
  } catch { return false }
}

function clearEditor() {
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
}

async function chooseClient(value: string) {
  if (value === client.value || !await leaveDraft()) return
  await run(async () => {
    client.value = value
    clearEditor()
    accounts.value = await request<Account[]>(`clients/${part(value)}/accounts`)
    account.value = accounts.value[0]?.ref ?? ''
    await loadTargets()
  })
}

async function chooseAccount(value: string) {
  if (value === account.value || !await leaveDraft()) return
  await run(async () => { account.value = value; clearEditor(); await loadTargets() })
}

async function readView() {
  const current = await request<View>(targetPath())
  view.value = current
  values.value = Object.fromEntries(current.fields.map(field => [field.name, field.value]))
}

async function chooseTarget(value: Target) {
  if (!section.value || selected.value?.ref === value.ref || !await leaveDraft()) return
  await run(async () => { clearEditor(); selected.value = value; await readView() })
}

async function chooseSection(value: string) {
  if (value === section.value || !await leaveDraft()) return
  await run(async () => { section.value = value; pending.value = undefined; lastReceipt.value = undefined; await readView() })
}

async function discover() {
  await run(async () => {
    accounts.value = await request<Account[]>(`clients/${part(client.value)}/actions/discover`, { method: 'POST' })
    if (!accounts.value.some(value => value.ref === account.value)) account.value = accounts.value[0]?.ref ?? ''
    await loadTargets()
    notice.value = '已更新已知目录。在线且账号唯一就绪时同步平台联系人；历史目录不代表当前全部联系人。'
  })
}

async function submit(action: string) {
  if (!view.value) return
  if (action !== 'PATCH') {
    try {
      await ElMessageBox.confirm(action === 'REGISTER'
        ? '登记后会按下方默认值启用 Agent。请确认当前聊天和共享影响。'
        : '仅注销当前聊天的 Agent 配置，记忆、人格、历史和图库不会删除。共享入口也会受影响。',
      action === 'REGISTER' ? '登记并启用 Agent' : '注销聊天配置', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' })
    } catch { return }
  }
  const fields = action === 'PATCH' ? Object.fromEntries(view.value.fields
    .filter(field => field.editable && values.value[field.name] !== field.value)
    .map(field => [field.name, values.value[field.name]!])) : {}
  if (action === 'PATCH' && !Object.keys(fields).length) return
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
  if (dirty.value || pending.value) { event.preventDefault(); event.returnValue = '' }
}

watch(() => props.initialClient, value => { if (value) void chooseClient(value) })
onMounted(async () => {
  window.addEventListener('beforeunload', beforeUnload)
  await run(async () => { sections.value = await request<Section[]>('settings/sections'); section.value = sections.value[0]?.id ?? '' })
  await chooseClient(props.initialClient || available.value[0]?.clientId || '')
})
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <div class="chat-settings">
    <ElAlert v-if="!sections.length && !busy" title="本地配置尚未允许任何聊天设置分区。" type="info" :closable="false" />
    <ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <ElAlert v-if="notice" :title="notice" type="success" :closable="true" @close="notice = ''" />
    <div class="settings-toolbar">
      <label>聊天平台<ElSelect :model-value="client" :disabled="busy" @update:model-value="chooseClient">
        <ElOption v-for="item in available" :key="item.clientId" :value="item.clientId" :label="item.name" />
      </ElSelect></label>
      <label>机器人账号 / 历史目录<ElSelect :model-value="account" :disabled="busy" placeholder="尚未发现账号" @update:model-value="chooseAccount">
        <ElOption v-for="item in accounts" :key="item.ref" :value="item.ref" :label="item.name" />
      </ElSelect></label>
      <ElButton :disabled="busy || !client" :loading="busy" @click="discover">更新已知目录</ElButton>
    </div>
    <div class="settings-columns">
      <ElCard shadow="never" class="target-list">
        <h2>群聊与私聊</h2>
        <ElSelect v-model="kind" :disabled="busy" placeholder="全部已知聊天" @change="run(() => loadTargets())">
          <ElOption value="" label="全部已知聊天" /><ElOption value="GROUP" label="群聊" /><ElOption value="PRIVATE" label="私聊" />
        </ElSelect>
        <ElInput v-model="search" placeholder="筛选已加载的名称或号码" clearable />
        <ElEmpty v-if="!visibleTargets.length" description="暂无匹配的已知聊天" :image-size="70" />
        <button v-for="item in visibleTargets" :key="item.ref" class="target-button" :class="{ selected: selected?.ref === item.ref }"
          :disabled="busy" @click="chooseTarget(item)"><strong>{{ item.name }}</strong>
          <span>{{ item.kind === 'GROUP' ? '群聊' : '私聊' }} · {{ item.source === 'PLATFORM' ? '平台查询' : item.source === 'MESSAGE' ? '真实消息' : '已登记配置' }}</span></button>
        <ElButton v-if="next" text :disabled="busy" @click="run(() => loadTargets(true))">加载更多聊天</ElButton>
        <p class="small muted">离线时显示已知目录，不将历史记录标为当前全部联系人。</p>
      </ElCard>
      <div class="settings-editor">
        <ElEmpty v-if="!selected" description="选择一个聊天，查看与修改设置" />
        <template v-else-if="view">
          <div class="editor-heading"><div><h2>{{ view.targetName }}</h2><span class="connection-state">{{ view.registered ? '已登记' : '尚未登记' }}</span></div>
            <ElButton :disabled="busy || !!pending" @click="emit('inspect', { client, account, target: selected.ref, name: view.targetName })">查看 Agent 活动与记忆</ElButton>
            <ElButton :disabled="busy || !!pending" @click="emit('console', { client, account, target: selected.ref, name: view.targetName })">打开客户端指令台</ElButton>
            <ElSelect v-if="sections.length > 1" :model-value="section" :disabled="busy" @update:model-value="chooseSection">
              <ElOption v-for="item in sections" :key="item.id" :value="item.id" :label="item.name" />
            </ElSelect></div>
          <ElAlert :title="view.sharedExplanation" type="warning" :closable="false" show-icon />
          <ElAlert v-if="!view.registered" title="下方是登记后的默认值，并未写入或启用。请明确登记后再修改。" type="info" :closable="false" />
          <ElCard v-for="group in groups" :key="group" shadow="never" class="field-group"><h3>{{ group }}</h3>
            <div v-for="field in fieldsIn(group)" :key="field.name" class="settings-field">
              <div><label :for="'field-' + field.name">{{ field.label }}</label><p class="small muted">{{ field.description }}</p>
                <span class="field-source">{{ field.source }} · 默认：{{ defaultLabel(field) }}</span></div>
              <ElSwitch v-if="field.kind === 'BOOLEAN'" :id="'field-' + field.name" :model-value="values[field.name] === 'true'"
                @update:model-value="value => values[field.name] = String(value)" :disabled="busy || !view.registered || !field.editable || !!pending" />
              <ElSelect v-else-if="field.kind === 'SELECT'" :id="'field-' + field.name" v-model="values[field.name]"
                :disabled="busy || !view.registered || !field.editable || !!pending">
                <ElOption v-for="(label, value) in field.choices" :key="value" :value="value" :label="label" />
              </ElSelect>
              <ElInput v-else :id="'field-' + field.name" v-model="values[field.name]"
                :type="field.kind === 'LONG_TEXT' ? 'textarea' : field.kind === 'NUMBER' ? 'number' : 'text'" :rows="3"
                :disabled="busy || !view.registered || !field.editable || !!pending" :maxlength="8192" />
            </div>
          </ElCard>
          <ElCard v-if="pending" shadow="never"><h3>上次操作尚待确认</h3><p class="small">操作编号：{{ pending.operationId }}</p>
            <p class="small muted">失败或断开不证明未提交。请查询真实结果，或使用相同编号重试原请求。</p>
            <ElButton :disabled="busy" @click="queryPending">查询提交结果</ElButton><ElButton :disabled="busy" @click="resend">重试原请求</ElButton>
            <ElButton :disabled="busy" @click="discardPending">放弃草稿并刷新</ElButton></ElCard>
          <div class="editor-actions">
            <ElButton v-if="!view.registered" type="primary" :disabled="busy || !!pending" @click="submit('REGISTER')">登记并启用 Agent</ElButton>
            <template v-else><ElButton type="primary" :disabled="busy || !dirty || !!pending" @click="submit('PATCH')">保存修改</ElButton>
              <ElButton :disabled="busy || !!pending" @click="run(readView)">重新读取</ElButton>
              <ElButton type="danger" plain :disabled="busy || !!pending" @click="submit('UNREGISTER')">注销登记</ElButton></template>
            <span v-if="dirty" class="small muted">有未保存的修改</span>
          </div>
          <ElCard v-if="lastReceipt" shadow="never" class="operation-receipt"><h3>真实操作回执</h3>
            <p>操作者：本地管理员 · {{ date(lastReceipt.at) }}{{ lastReceipt.replayed ? ' · 原结果重放' : '' }}</p>
            <p class="small">操作编号：{{ lastReceipt.operationId }}</p>
            <p class="small muted">{{ lastReceipt.action === 'PATCH' ? '修改字段：' + lastReceipt.fields.map(name => view?.fields.find(field => field.name === name)?.label ?? name).join('、') : lastReceipt.action === 'REGISTER' ? '登记完成' : '注销完成' }}</p></ElCard>
        </template>
      </div>
    </div>
  </div>
</template>
