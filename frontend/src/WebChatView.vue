<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, onMounted, ref} from 'vue'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElDialog,
  ElEmpty,
  ElInput,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTabPane,
  ElTabs,
  ElTag
} from 'element-plus'
import {ApiError, request} from './api'
import DetailPanel from './DetailPanel.vue'
import ConsoleIcon from './ConsoleIcon.vue'
import {useConsolePreferences} from './console-preferences'
import type {AgentTarget} from './AgentWorkspaceView.vue'

interface Profile { id: string; uuid: string; name: string }
interface Member { profile: string; card: string; role: string }
interface Room { id: string; kind: string; name: string; revision: number; members: Member[] }
interface Home { profiles: Profile[]; rooms: Room[]; self: string; observedAt: string }
interface Part { kind: string; value: string }
interface Message { id: string; sequence: number; sender: Profile; card: string; role: string; bot: boolean; origin: string; parts: Part[]; at: string }
interface Page { items: Message[]; next: number; more: boolean; reset: boolean }
interface Receipt { operationId: string; messageId: string; status: string; note: string }
interface Send { operationId: string; profile: string; text: string; mention: boolean; reply: string; media: string; observedAt: string }
interface Admin { path: string; body: { operationId: string; [key: string]: unknown }; action: string }
interface Settings { registered: boolean; fields: { name: string; label: string; value: string }[] }
const props = defineProps<{ active: boolean }>()
const {advanced} = useConsolePreferences()
const emit = defineEmits<{ expired: []; inspect: [target: AgentTarget]; settings: [] }>()
const home = ref<Home>()
const roomId = ref('')
const picker = ref('')
const room = computed(() => home.value?.rooms.find(value => value.id === roomId.value))
const messages = ref<Message[]>([])
const messagePane = ref<HTMLDivElement>()
const cursor = ref(0)
const more = ref(false)
const text = ref('')
const sender = ref('')
const mention = ref(false)
const reply = ref('')
const media = ref('')
const mediaIds = ref<string[]>([])
const pending = ref<{ room: string; body: Send }>()
const pendingAdmin = ref<Admin>()
const pendingUpload = ref<{ room: string; id: string; file: File }>()
const receipt = ref<Receipt>()
const settings = ref<Settings>()
const namespace = computed(() => settings.value?.fields.find(field => field.name === 'memoryNamespace')?.value || '尚未确认')
const busy = ref(false)
const error = ref('')
const notice = ref('')
const nickname = ref('')
const roomName = ref('')
const roomKind = ref('PRIVATE')
// 2026/11/04：直接从聊天页打开创建表单，成员管理仍保留在原标签页。
const createRoomOpen = ref(false)
const selectedProfiles = ref<string[]>([])
const memberProfile = ref('')
const memberCard = ref('')
const memberRole = ref('MEMBER')
let generation = 0
let interval: ReturnType<typeof setInterval> | undefined
const base = (id = roomId.value) => `chat/rooms/${encodeURIComponent(id)}`
const imageUrl = (id: string) => `/api/v1/${base()}/media/${encodeURIComponent(id)}`
const blocked = computed(() => busy.value || !!pending.value || !!pendingAdmin.value || !!pendingUpload.value)
const members = computed(() => room.value?.members.map(value => ({ ...value, name: home.value?.profiles.find(profile => profile.id === value.profile)?.name ?? value.profile })) ?? [])
const learning = computed(() => ['enabled', 'historyCaptureEnabled', 'autoSummaryEnabled', 'memoryLearningEnabled', 'personalityLearningEnabled']
  .map(name => settings.value?.fields.find(field => field.name === name)).filter(value => !!value))
const roles: Record<string, string> = { MEMBER: '普通成员', ADMIN: '群管理员', OWNER: '群主' }
const status: Record<string, string> = { PERSISTED: '消息已保存，入站通知待确认', DISPATCHED: '已通知原消息链', REJECTED: '原入站被拒绝', INDETERMINATE: '入站结果未确认，不自动重放' }
const profileName = (id: string) => id === home.value?.self ? 'TeaNeko（当前机器人）' : home.value?.profiles.find(value => value.id === id)?.name ?? id
const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })
const operationId = () => crypto.randomUUID()

async function confirm(message: string, label = '确认') {
  try { await ElMessageBox.confirm(message, '网页聊天', { confirmButtonText: label, cancelButtonText: '取消', type: 'warning' }); return true }
  catch { return false }
}

async function run(action: () => Promise<void>) {
  if (busy.value) return
  const current = generation
  busy.value = true
  error.value = ''
  try { await action() }
  catch (failure) {
    if (current !== generation) return
    error.value = failure instanceof Error ? failure.message : '网页聊天请求未完成。'
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
  } finally { if (current === generation) busy.value = false }
}

async function refreshHome() {
  const current = generation
  const result = await request<Home>('chat/rooms')
  if (current !== generation) return
  home.value = result
  if (!selectedProfiles.value.length && result.profiles.length) selectedProfiles.value = [result.profiles[0]!.id]
  if (room.value && !room.value.members.some(value => value.profile === sender.value)) sender.value = room.value.members[0]?.profile ?? ''
}

async function pull() {
  if (!room.value) return
  const follow = !messagePane.value || messagePane.value.scrollHeight - messagePane.value.scrollTop - messagePane.value.clientHeight < 100
  const current = generation
  const result = await request<Page>(`${base()}/messages?after=${cursor.value}`)
  if (current !== generation) return
  if (result.reset) {
    messages.value = []
    notice.value = '消息窗口已前移，正在显示当前可用记录；未显示的部分不能解释为没有发生。'
  }
  const indexed = new Map(messages.value.map(item => [item.id, item]))
  result.items.forEach(item => indexed.set(item.id, item))
  messages.value = [...indexed.values()].sort((a, b) => a.sequence - b.sequence).slice(-2000)
  cursor.value = result.next
  more.value = result.more
  await nextTick()
  if (current === generation && follow && messagePane.value) messagePane.value.scrollTop = messagePane.value.scrollHeight
}

async function context() {
  if (!room.value) return
  const current = generation
  const result = await request<Settings>(`clients/web/accounts/web-bot/targets/${encodeURIComponent(roomId.value)}/settings/agent`)
  if (current === generation) settings.value = result
}

async function images() {
  const current = generation
  const result = await request<string[]>(`${base()}/media`)
  if (current === generation) mediaIds.value = result
}

async function refresh() {
  await refreshHome()
  if (room.value) { await context(); await images() }
}

async function choose(id: string) {
  if (id === roomId.value) return
  if ((pending.value || text.value.trim() || media.value || reply.value || pendingAdmin.value || pendingUpload.value)
    && !await confirm('当前有草稿或未确认请求。切换只放弃本页草稿，不撤销已受理消息；是否切换？', '放弃并切换')) {
    picker.value = roomId.value
    return
  }
  generation++
  busy.value = false
  roomId.value = id
  picker.value = id
  messages.value = []
  cursor.value = 0
  receipt.value = undefined
  pending.value = undefined
  pendingAdmin.value = undefined
  pendingUpload.value = undefined
  text.value = ''
  media.value = ''
  reply.value = ''
  mention.value = false
  sender.value = room.value?.members[0]?.profile ?? ''
  notice.value = ''
  settings.value = undefined
  await run(async () => { await pull(); await context(); await images() })
}

async function administrative(path: string, body: Admin['body'], action: string) {
  if (blocked.value) return
  if (!await confirm(`${action}只作用于网页客户端。聊天中的成员权限与网页登录管理权限分开，确认提交？`, '确认提交')) return
  pendingAdmin.value = { path, body, action }
  await run(retryAdmin)
}

async function retryAdmin() {
  const original = pendingAdmin.value!
  const result = await request<{ id: string }>(original.path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(original.body) })
  pendingAdmin.value = undefined
  nickname.value = ''
  await refreshHome()
  notice.value = `${original.action}已保存。`
  if (original.path === 'chat/rooms') {
    // 2026/11/04：只有创建请求已确认成功后才收起表单。
    createRoomOpen.value = false
    // 2026/11/03：先结束本次写入状态再选择新聊天，不能让防重入锁吞掉初次读取。
    busy.value = false
    await choose(result.id)
  }
}

/** 2026/11/04：按入口预选聊天类型，私聊只保留一个发言档案。 */
function openCreateRoom(kind: 'PRIVATE' | 'GROUP') {
  roomKind.value = kind
  if (kind === 'PRIVATE') selectedProfiles.value = selectedProfiles.value.slice(0, 1)
  createRoomOpen.value = true
}

async function deliver() {
  const original = pending.value!
  const current = generation
  const result = await request<Receipt>(`${base(original.room)}/messages`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(original.body) })
  if (current !== generation) return
  receive(result)
  await pull()
  await images()
}

function receive(result: Receipt) {
  receipt.value = result
  pending.value = undefined
  text.value = ''
  media.value = ''
  reply.value = ''
  mention.value = false
}

async function send() {
  if (blocked.value || !room.value || !sender.value || !text.value.trim() && !media.value) return
  await run(async () => {
    await refreshHome()
    pending.value = { room: roomId.value, body: { operationId: crypto.randomUUID(), profile: sender.value, text: text.value,
      mention: mention.value, reply: reply.value, media: media.value, observedAt: home.value!.observedAt } }
    await deliver()
  })
}

async function query() {
  const original = pending.value
  const id = original?.body.operationId ?? receipt.value?.operationId
  if (!id) return
  const current = generation
  const result = await request<Receipt>(`${base(original?.room ?? roomId.value)}/operations/${encodeURIComponent(id)}`)
  if (current === generation) {
    if (original) receive(result)
    else receipt.value = result
    await pull()
  }
}

async function upload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || blocked.value || !room.value) return
  if (file.size > 1048576) { error.value = '每张图片最多一 MiB，标准 PNG 超过上限也会被拒绝。'; return }
  pendingUpload.value = { room: roomId.value, id: crypto.randomUUID(), file }
  await run(retryUpload)
}

async function retryUpload() {
  const original = pendingUpload.value!
  const current = generation
  const result = await request<{ id: string }>(`${base(original.room)}/media?operation=${original.id}`,
    { method: 'POST', headers: { 'Content-Type': 'application/octet-stream' }, body: original.file })
  if (current !== generation) return
  pendingUpload.value = undefined
  media.value = result.id
  await images()
}

async function forgetPending() {
  if (!await confirm('放弃当前未确认请求不撤销已提交结果，也不表示没有发送。确认后才能准备新操作。', '放弃原请求')) return
  pending.value = undefined
  pendingAdmin.value = undefined
  pendingUpload.value = undefined
}

async function removeImage(id: string) {
  if (!await confirm('仅删除当前消息窗口没有引用的网页图片，不影响 Agent 已保存的文字记忆。', '删除图片')) return
  await run(async () => {
    await request(`${base()}/media/${encodeURIComponent(id)}`, { method: 'DELETE' })
    if (media.value === id) media.value = ''
    await images()
  })
}

function inspect() {
  if (room.value) emit('inspect', { client: 'web', account: 'web-bot', target: room.value.id, name: room.value.name })
}

function beforeUnload(event: BeforeUnloadEvent) {
  if (text.value.trim() || media.value || pending.value || pendingAdmin.value || pendingUpload.value) { event.preventDefault(); event.returnValue = '' }
}

onMounted(() => {
  void run(refresh)
  window.addEventListener('beforeunload', beforeUnload)
  interval = setInterval(() => {
    if (props.active && !document.hidden && room.value && !busy.value) void run(async () => {
      await pull()
      if (receipt.value?.status === 'PERSISTED') await query()
    })
  }, 2000)
})
onBeforeUnmount(() => { generation++; clearInterval(interval); pending.value = undefined; pendingAdmin.value = undefined; pendingUpload.value = undefined; window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <!-- 2026/11/04：简洁模式优先展示消息与发送，保留成员选择和原请求处理。 -->
  <div class="web-chat">
    <DetailPanel title="聊天说明"><p>这是独立网页聊天。已启用 Agent 的私聊会自动回复，群聊可提及 TeaNeko；成员身份在“档案与成员”中设置。</p></DetailPanel>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <ElAlert v-if="notice" :title="notice" type="info" :closable="false" />
    <ElCard shadow="never" class="runtime-card"><div class="editor-heading"><h2>网页聊天</h2><ElButton :disabled="busy" @click="run(refresh)">刷新聊天与档案</ElButton></div>
      <!-- 2026/11/04：常驻创建入口，不要求先进入档案管理页。 -->
      <div class="editor-actions"><ElButton :disabled="blocked || !home" @click="openCreateRoom('PRIVATE')"><ConsoleIcon name="private" /> 创建私聊</ElButton><ElButton :disabled="blocked || !home" @click="openCreateRoom('GROUP')"><ConsoleIcon name="group" /> 创建模拟群</ElButton></div>
      <label for="web-room">选择私聊或模拟群</label><ElSelect id="web-room" v-model="picker" aria-label="网页聊天会话" placeholder="先创建一个聊天" :disabled="busy" @change="choose"><ElOption v-for="item in home?.rooms ?? []" :key="item.id" :label="`${item.kind === 'GROUP' ? '群聊' : '私聊'} · ${item.name}`" :value="item.id" /></ElSelect>
    </ElCard>
    <!-- 2026/11/04：关闭弹窗保留表单及原请求，可返回页面处理未确认回执。 -->
    <ElDialog v-model="createRoomOpen" title="新建私聊或模拟群" width="min(520px, 94vw)" class="web-chat">
      <template v-if="home">
        <label>聊天显示名<ElInput v-model="roomName" maxlength="80" aria-label="新网页聊天名称" :disabled="blocked" placeholder="聊天显示名" /></label>
        <label>聊天类型<ElSelect v-model="roomKind" aria-label="新网页聊天类型" :disabled="blocked"><ElOption label="私聊" value="PRIVATE"><ConsoleIcon name="private" /> 私聊</ElOption><ElOption label="模拟群" value="GROUP"><ConsoleIcon name="group" /> 模拟群</ElOption></ElSelect></label>
        <label>聊天成员<ElSelect v-model="selectedProfiles" multiple aria-label="新网页聊天成员" :disabled="blocked"><ElOption v-for="profile in home.profiles" :key="profile.id" :label="profile.name" :value="profile.id" /></ElSelect></label>
        <p class="small muted">私聊选择一位档案，每个档案只有一个私聊；模拟群可选多位。初始权限均为普通成员；创建不会启用 Agent 或更改记忆绑定。新增档案请到“档案与成员”。</p>
        <ElAlert v-if="error" :title="error" type="error" :closable="false" />
        <ElButton type="primary" :disabled="blocked || !roomName.trim() || !selectedProfiles.length || roomKind === 'PRIVATE' && selectedProfiles.length !== 1" @click="administrative('chat/rooms', { operationId: operationId(), kind: roomKind, name: roomName, profiles: [...selectedProfiles] }, '创建网页聊天')">创建聊天</ElButton>
      </template>
    </ElDialog>
    <ElTabs v-if="home" class="web-chat-tabs">
      <ElTabPane label="聊天消息">
        <ElEmpty v-if="!room" description="创建一个私聊或模拟群后开始；每个档案只对应一个私聊" />
        <template v-else>
          <ElCard shadow="never" class="runtime-card"><div class="editor-heading"><h3>{{ room.name }}</h3><ElTag>{{ room.kind === 'GROUP' ? '网页模拟群' : '网页私聊' }}</ElTag></div>
            <p v-if="settings && !settings.registered">尚未登记 Agent，消息仍可保存；请到聊天设置明确登记。</p>
            <div class="editor-actions"><ElButton @click="emit('settings')">聊天设置</ElButton><ElButton @click="inspect">活动与记忆</ElButton></div>
            <DetailPanel title="Agent 状态"><p>记忆空间：{{ namespace }}；人格按当前聊天独立。</p><p v-for="field in learning" :key="field.name">{{ field.label }}：{{ settings?.registered && field.value === 'true' ? '开启' : '关闭或尚未登记' }}</p></DetailPanel>
          </ElCard>
          <div ref="messagePane" class="web-messages" aria-label="网页聊天消息" aria-live="polite">
            <ElEmpty v-if="!messages.length" description="当前消息窗口为空" />
            <article v-for="message in messages" :key="message.id" class="web-bubble" :class="{ 'web-bot-bubble': message.bot }">
              <div class="web-message-heading"><strong>{{ message.card || message.sender.name }}</strong><ElTag size="small">{{ message.bot ? 'TeaNeko · 已存服务端' : `网页成员 · ${roles[message.role] ?? message.role}` }}</ElTag><time>{{ date(message.at) }}</time></div>
              <template v-for="(part, index) in message.parts" :key="index">
                <p v-if="part.kind === 'TEXT'" class="web-message-text">{{ part.value }}</p>
                <p v-else-if="part.kind === 'AT'" class="web-mention">@{{ profileName(part.value) }}</p>
                <p v-else-if="part.kind === 'REPLY'" class="small revision">引用：{{ part.value }}</p>
                <img v-else-if="part.kind === 'IMAGE'" :src="imageUrl(part.value)" class="web-message-image" alt="当前网页聊天的受限图片" loading="lazy" />
              </template>
              <p v-show="advanced" class="small revision">{{ message.id }}</p><ElButton text :disabled="blocked" @click="reply = message.id">引用</ElButton>
            </article>
          </div>
          <ElButton v-if="more" :disabled="busy" @click="run(pull)">继续读取消息</ElButton>
          <ElCard shadow="never" class="runtime-card"><h3>发送消息</h3><template v-if="advanced || members.length > 1"><label for="web-sender">发言档案</label><ElSelect id="web-sender" v-model="sender" aria-label="网页发言档案" :disabled="blocked"><ElOption v-for="member in members" :key="member.profile" :label="`${member.card} · ${roles[member.role]}`" :value="member.profile" /></ElSelect></template>
            <p v-if="reply" class="small revision">引用 {{ reply }} <ElButton text :disabled="blocked" @click="reply = ''">取消引用</ElButton></p>
            <p v-if="media" class="small revision">附图 {{ media }} <ElButton text :disabled="blocked" @click="media = ''">移除附图</ElButton></p>
            <ElInput v-model="text" type="textarea" :rows="4" maxlength="16384" aria-label="网页消息正文" :disabled="blocked" placeholder="输入对话或原客户端命令，Ctrl+Enter 发送；内容按纯文本显示" @keydown.ctrl.enter.prevent="send" />
            <div class="editor-actions"><ElSwitch v-if="room.kind === 'GROUP'" v-model="mention" active-text="提及 TeaNeko" :disabled="blocked" /><label class="web-file-label">上传图片<input type="file" accept="image/png,image/jpeg,image/gif" aria-label="上传网页聊天图片" :disabled="blocked" @change="upload" /></label><ElButton type="primary" :disabled="blocked || !sender || !text.trim() && !media" @click="send">发送到当前网页聊天</ElButton></div>
            <DetailPanel title="图片要求"><p>图片最大一 MiB、边长 4096、约四百万像素。保存为静态 PNG，GIF 仅取首帧。</p></DetailPanel>
          </ElCard>
          <ElCard v-if="receipt" shadow="never" class="operation-receipt"><h3>{{ status[receipt.status] ?? receipt.status }}</h3><p>{{ receipt.note }}</p><p class="small revision">原操作：{{ receipt.operationId }}<br />消息：{{ receipt.messageId }}</p><ElButton :disabled="busy" @click="run(query)">查询原消息回执</ElButton></ElCard>
        </template>
      </ElTabPane>
      <ElTabPane label="档案与成员">
        <ElCard shadow="never" class="runtime-card"><h3>新增网页档案</h3><ElInput v-model="nickname" maxlength="80" aria-label="新网页档案昵称" :disabled="blocked" placeholder="例如：小茶" /><ElButton :disabled="blocked || !nickname.trim()" @click="administrative('chat/profiles', { operationId: operationId(), name: nickname }, '创建网页档案')">创建档案</ElButton><p v-for="profile in home.profiles" :key="profile.id" class="small revision">{{ profile.name }} · 网页号 {{ profile.id }} · UUID {{ profile.uuid }}</p></ElCard>
        <ElCard v-if="room?.kind === 'GROUP'" shadow="never" class="runtime-card"><h3>当前群成员管理</h3><p class="small muted">保存会撤销旧消息的借用能力。角色只影响网页平台，不能获得其他客户端或 DEBUG 权限。</p><p v-for="member in members" :key="member.profile">{{ member.card }} · {{ roles[member.role] }} <ElButton text :disabled="blocked" @click="administrative(`${base()}/members`, { operationId: operationId(), revision: room!.revision, member: { profile: member.profile, card: member.card, role: member.role }, action: 'REMOVE' }, '移出网页成员')">移出</ElButton></p><ElSelect v-model="memberProfile" aria-label="待管理网页成员" :disabled="blocked"><ElOption v-for="profile in home.profiles" :key="profile.id" :label="profile.name" :value="profile.id" /></ElSelect><ElInput v-model="memberCard" aria-label="网页群名片" maxlength="80" :disabled="blocked" placeholder="群名片" /><ElSelect v-model="memberRole" aria-label="网页成员角色" :disabled="blocked"><ElOption label="普通成员" value="MEMBER" /><ElOption label="群管理员" value="ADMIN" /><ElOption label="群主" value="OWNER" /></ElSelect><ElButton :disabled="blocked || !memberProfile || !memberCard.trim()" @click="administrative(`${base()}/members`, { operationId: operationId(), revision: room!.revision, member: { profile: memberProfile, card: memberCard, role: memberRole }, action: 'UPSERT' }, '保存网页成员')">保存成员</ElButton></ElCard>
      </ElTabPane>
      <ElTabPane label="当前聊天图片"><ElEmpty v-if="!room" description="先选择一个聊天" /><div v-else class="web-media-grid"><ElCard v-for="id in mediaIds" :key="id" shadow="never"><img :src="imageUrl(id)" class="web-message-image" alt="当前聊天图片" loading="lazy" /><p class="small revision">{{ id }}</p><ElButton :disabled="blocked" @click="media = id">选为附图</ElButton><ElButton :disabled="blocked" @click="removeImage(id)">删除未引用图片</ElButton></ElCard></div></ElTabPane>
    </ElTabs>
    <ElCard v-if="pending || pendingAdmin || pendingUpload" shadow="never" class="pending-operation"><h3>原请求结果待确认</h3><p>连接失败不表示未受理；保留原编号和原文，不会自动重发。</p><p class="small revision">{{ pending?.body.operationId ?? pendingUpload?.id ?? pendingAdmin?.body.operationId }}</p><ElButton v-if="pending" :disabled="busy" @click="run(query)">查询原请求</ElButton><ElButton :disabled="busy" @click="run(pending ? deliver : pendingAdmin ? retryAdmin : retryUpload)">原样重试</ElButton><ElButton :disabled="busy" @click="forgetPending">放弃原请求</ElButton></ElCard>
  </div>
</template>

