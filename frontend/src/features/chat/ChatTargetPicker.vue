<script setup lang="ts">
// 2026/11/07：按功能目录引用共享能力与页面契约。
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {ElAlert, ElButton, ElCard, ElEmpty, ElInput, ElOption, ElSelect} from 'element-plus'
import type {Platform} from '../../shared/api/client'
import {ApiError, request, requestEmpty} from '../../shared/api/client'
import type {AgentTarget} from '../agent/AgentWorkspaceView.vue'
import ConsoleIcon from '../../shared/components/ConsoleIcon.vue'
import RefreshButton from '../../shared/components/RefreshButton.vue'

interface Account { ref: string; name: string }
interface Target { ref: string; kind: string; name: string; platformId: string }
interface Page { items: Target[]; next: string }
const props = withDefaults(defineProps<{clients: Platform[]; target?: AgentTarget; title?: string}>(), {title: 'Agent 工作区'})
const emit = defineEmits<{select: [target: AgentTarget]; expired: []}>()
const root = ref<HTMLDivElement>()
const client = ref('')
const account = ref('')
const accounts = ref<Account[]>([])
const targets = ref<Target[]>([])
const next = ref('')
const search = ref('')
const busy = ref(false)
const error = ref('')
const available = computed(() => props.clients.filter(item => item.settingsAvailable))
const visible = computed(() => targets.value.filter(item => `${item.name} ${item.platformId}`.includes(search.value.trim())))
const part = encodeURIComponent
let generation = 0

// 2026/11/04：选择器独立持有目录与筛选，已选目标只在父级完成草稿确认后更新。
async function run(action: (current: number) => Promise<void>) {
  // 2026/11/04：平台变化或卸载后，旧响应不能覆盖当前目录及读取状态。
  const current = ++generation
  busy.value = true
  error.value = ''
  try { await action(current) }
  catch (failure) {
    if (current !== generation) return
    error.value = failure instanceof Error ? failure.message : '聊天目录读取失败。'
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
  } finally { if (current === generation) busy.value = false }
}

async function loadTargets(current: number, more = false) {
  if (!client.value || !account.value) { targets.value = []; next.value = ''; return }
  const query = new URLSearchParams({after: more ? next.value : '', kind: ''})
  const page = await request<Page>(`clients/${part(client.value)}/accounts/${part(account.value)}/targets?${query}`)
  if (current !== generation) return
  targets.value = more ? [...new Map([...targets.value, ...page.items].map(item => [item.ref, item])).values()] : page.items
  next.value = page.next
}

async function loadAccounts(current: number, preferred = '') {
  if (!client.value) { accounts.value = []; account.value = ''; targets.value = []; next.value = ''; return }
  const result = await request<Account[]>(`clients/${part(client.value)}/accounts`)
  if (current !== generation) return
  accounts.value = result
  account.value = result.some(item => item.ref === preferred) ? preferred : result[0]?.ref ?? ''
  await loadTargets(current)
}

function sync() {
  const selected = props.target?.client
  client.value = available.value.some(item => item.clientId === selected) ? selected! : available.value[0]?.clientId ?? ''
  void run(current => loadAccounts(current, props.target?.account))
}

async function chooseClient(value: string) {
  client.value = value
  search.value = ''
  await run(current => loadAccounts(current))
}

async function chooseAccount(value: string) {
  account.value = value
  search.value = ''
  await run(current => loadTargets(current))
}

async function discover() {
  await run(async current => {
    await requestEmpty(`clients/${part(client.value)}/actions/discover`, {method: 'POST'})
    if (current !== generation) return
    await loadAccounts(current, account.value)
  })
}

function focus() {
  root.value?.scrollIntoView({behavior: 'smooth', block: 'start'})
  root.value?.querySelector<HTMLElement>('[role="combobox"]')?.focus()
}

defineExpose({focus})
watch(() => `${props.target?.client ?? ''}/${props.target?.account ?? ''}`, () => {
  if (props.target?.client !== client.value || props.target?.account !== account.value) sync()
})
watch(() => available.value.map(item => item.clientId).join(','), sync)
onMounted(sync)
onBeforeUnmount(() => generation++)
</script>

<template>
  <div ref="root" class="agent-target-picker">
    <ElCard shadow="never">
      <h2>{{ title }} · 选择聊天</h2>
      <p class="small muted">选择平台与账号后，点击聊天卡片进入。</p>
      <ElAlert v-if="error" :title="error" type="error" :closable="false" />
      <div class="settings-toolbar">
        <label>聊天平台<ElSelect :model-value="client" :aria-label="title + '聊天平台'" :disabled="busy" @update:model-value="chooseClient"><ElOption v-for="item in available" :key="item.clientId" :label="item.name" :value="item.clientId" /></ElSelect></label>
        <label>机器人账号<ElSelect :model-value="account" :aria-label="title + '机器人账号'" :disabled="busy" @update:model-value="chooseAccount"><ElOption v-for="item in accounts" :key="item.ref" :label="item.name" :value="item.ref" /></ElSelect></label>
        <RefreshButton label="更新聊天目录" :disabled="busy || !client" :loading="busy" @click="discover" />
      </div>
      <ElInput v-model="search" placeholder="筛选已加载的聊天名称或号码" :aria-label="'筛选' + title + '聊天'" clearable />
      <div class="agent-target-list">
        <button v-for="item in visible" :key="item.ref" class="target-button" :class="{selected: target?.client === client && target?.account === account && target?.target === item.ref}" :disabled="busy" @click="emit('select', {client, account, target: item.ref, name: item.name})">
          <span class="chat-kind"><ConsoleIcon :name="item.kind === 'GROUP' ? 'group' : item.kind === 'PRIVATE' ? 'private' : 'other'" />{{ item.kind === 'GROUP' ? '群聊' : item.kind === 'PRIVATE' ? '私聊' : '其他' }}</span><strong>{{ item.name }}</strong>
        </button>
      </div>
      <ElEmpty v-if="!visible.length && !busy" description="暂无匹配聊天，可刷新目录或切换平台与账号" :image-size="60" />
      <ElButton v-if="next" text :disabled="busy" @click="run(current => loadTargets(current, true))">加载更多聊天</ElButton>
    </ElCard>
  </div>
</template>
