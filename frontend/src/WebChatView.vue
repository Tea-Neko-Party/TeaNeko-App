<script setup lang="ts">
// 2026/11/04：刷新类操作统一使用带完整动作提示的图标按钮。
import RefreshButton from './RefreshButton.vue'
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
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
import type {Platform} from './api'
import {ApiError, request} from './api'
import ConsoleIcon from './ConsoleIcon.vue'
import {useConsolePreferences} from './console-preferences'
import type {AgentTarget} from './AgentWorkspaceView.vue'
import AgentWorkspaceView from './AgentWorkspaceView.vue'
import ChatSettingsView from './ChatSettingsView.vue'
import AgentChatStatus from './AgentChatStatus.vue'

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
const props = defineProps<{ active: boolean; clients: Platform[] }>()
const {advanced} = useConsolePreferences()
const emit = defineEmits<{ expired: []; inspect: [target: AgentTarget]; settings: [target: AgentTarget]; roomState: [open: boolean] }>()
// 2026/11/04：目录与聊天室分开，返回保留本房间草稿及原请求。
const roomOpen = ref(false)
const roomTab = ref('messages')
const directoryKind = ref('')
const profileOpen = ref(false)
// 2026/11/04：房间子页首次进入才挂载，各房间分别保留配置草稿与待确认操作。
const roomPages = ref<Record<string, {settings?: boolean; agent?: boolean}>>({})
function roomTarget(item: Room): AgentTarget { return {client: 'web', account: 'web-bot', target: item.id, name: item.name} }
function openRoomPage(panel: 'chat-config' | 'agent') {
  if (!room.value) return
  const state = roomPages.value[roomId.value] ??= {}
  if (panel === 'agent') state.agent = true
  else state.settings = true
  roomTab.value = panel
}
const visibleRooms = computed(() => home.value?.rooms.filter(item => !directoryKind.value || item.kind === directoryKind.value) ?? [])
interface ChatCommand {command: string; aliases: string[]; subcommand: string; subAliases: string[]; insertion: string; usage: string; description: string; introduction: string; regex: boolean}
const commands = ref<ChatCommand[]>([])
const commandsLoading = ref(false)
const commandError = ref('')
const completionIndex = ref(0)
const completionDismissed = ref(false)
const highlighted = ref('')
let commandEpoch = 0
let commandController: AbortController | undefined
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
// 2026/11/04：上传与拖放共用原受限媒体接口，预览只引用当前聊天的服务端图片。
const fileInput = ref<HTMLInputElement>()
const dragging = ref(false)
const pending = ref<{ room: string; body: Send }>()
const pendingAdmin = ref<Admin>()
const pendingUpload = ref<{ room: string; id: string; file: File }>()
// 2026/11/05：在途请求不等于连接失败，仅失败后显示恢复卡片。
const pendingFailed = ref(false)
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
let polling = false
let pullSequence = 0
let pollController: AbortController | undefined
let interval: ReturnType<typeof setInterval> | undefined
const base = (id = roomId.value) => `chat/rooms/${encodeURIComponent(id)}`
const imageUrl = (id: string) => `/api/v1/${base()}/media/${encodeURIComponent(id)}`
const blocked = computed(() => busy.value || !!pending.value || !!pendingAdmin.value || !!pendingUpload.value)
const members = computed(() => room.value?.members.map(value => ({ ...value, name: home.value?.profiles.find(profile => profile.id === value.profile)?.name ?? value.profile })) ?? [])
// 2026/11/05：顶部权限始终对应当前聊天的发言档案，不借用网页登录管理身份。
const currentMember = computed(() => members.value.find(member => member.profile === sender.value))
const learning = computed(() => ['enabled', 'historyCaptureEnabled', 'autoSummaryEnabled', 'memoryLearningEnabled', 'personalityLearningEnabled']
  .map(name => settings.value?.fields.find(field => field.name === name)).filter(value => !!value))
const roles: Record<string, string> = { MEMBER: '普通成员', ADMIN: '群管理员', OWNER: '群主' }
// 2026/11/05：私聊使用中性的权限名称，群聊沿用原成员角色名称。
const currentRoles = computed<Record<string, string>>(() => room.value?.kind === 'GROUP'
  ? roles : {MEMBER: '普通成员', ADMIN: '管理员', OWNER: '所有者'})
const status: Record<string, string> = { PERSISTED: '消息已保存，入站通知待确认', DISPATCHED: '已通知原消息链', REJECTED: '原入站被拒绝', INDETERMINATE: '入站结果未确认，不自动重放' }
const profileName = (id: string) => id === home.value?.self ? 'TeaNeko（当前机器人）' : home.value?.profiles.find(value => value.id === id)?.name ?? id
const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })
const operationId = () => crypto.randomUUID()

/** 2026/11/04：只在指令名或子指令位置补全，不将参数占位符写入真实消息。 */
const completions = computed(() => {
  if (!text.value.startsWith('/') || completionDismissed.value || blocked.value) return []
  const words = text.value.split(/\s+/)
  if (words.length > 2) return []
  const items = new Map<string, {text: string; description: string; usage: string}>()
  for (const entry of commands.value.filter(item => !item.regex)) {
    if (words.length === 1) {
      const alias = entry.aliases.find(item => item.startsWith(words[0]!))
      if (alias && !items.has(alias)) items.set(alias, {text: alias, description: entry.introduction, usage: alias})
    } else if (entry.aliases.includes(words[0]!) && entry.subcommand) {
      const alias = entry.subAliases.find(item => item.startsWith(words[1]!))
      if (alias) {
        const insertion = `${words[0]} ${alias}`
        items.set(insertion, {text: insertion, description: entry.description, usage: entry.usage})
      }
    }
  }
  return [...items.values()].slice(0, 12)
})

/** 2026/11/04：目录使用当前真实成员身份；迟到或已取消响应不能覆盖新房间目录。 */
async function loadCommands() {
  commandController?.abort()
  const epoch = ++commandEpoch
  commands.value = []
  commandsLoading.value = false
  commandError.value = ''
  if (!roomOpen.value || !sender.value) return
  const controller = new AbortController()
  commandController = controller
  commandsLoading.value = true
  try {
    const result = await request<ChatCommand[]>(`${base()}/commands`, {method: 'POST', signal: controller.signal,
      headers: {'Content-Type': 'application/json'}, body: JSON.stringify({profile: sender.value})})
    if (epoch === commandEpoch) commands.value = result
  } catch (failure) {
    if (epoch !== commandEpoch || controller.signal.aborted) return
    commandError.value = '指令目录暂不可用，仍可继续输入。'
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
  } finally { if (epoch === commandEpoch) commandsLoading.value = false }
}

/** 2026/11/04：选择补全后继续填写参数，不直接发送消息。 */
function completeCommand(value: string) {
  text.value = value + ' '
  void nextTick(() => document.querySelector<HTMLTextAreaElement>('[aria-label="网页消息正文"]')?.focus())
}

/** 2026/11/04：引用以作者和内容摘要呈现，编号不作为简洁模式的正文。 */
function replyPreview(id: string) {
  const original = messages.value.find(item => item.id === id)
  if (!original) return '原消息不在当前消息窗口中'
  const body = original.parts.filter(item => item.kind === 'TEXT').map(item => item.value).join('')
    || (original.parts.some(item => item.kind === 'IMAGE') ? '[图片]' : '[消息]')
  return `${original.card || original.sender.name}：${body.length > 100 ? body.slice(0, 100) + '…' : body}`
}

/** 2026/11/04：点击引用定位原消息，超出当前窗口时保留明确说明。 */
function locateReply(id: string) {
  const element = document.getElementById('web-message-' + id)
  if (element) { highlighted.value = id; element.scrollIntoView({block: 'center', behavior: 'smooth'}) }
  else notice.value = '原消息不在当前消息窗口中。'
}

function backToRooms() {
  roomOpen.value = false
  pollController?.abort()
  commandController?.abort()
  emit('roomState', false)
}

async function confirm(message: string, label = '确认') {
  try { await ElMessageBox.confirm(message, '网页聊天', { confirmButtonText: label, cancelButtonText: '取消', type: 'warning' }); return true }
  catch { return false }
}

async function run(action: () => Promise<void>) {
  if (busy.value) return
  const current = generation
  busy.value = true
  pendingFailed.value = false
  error.value = ''
  try { await action() }
  catch (failure) {
    if (current !== generation) return
    pendingFailed.value = !!(pending.value || pendingAdmin.value || pendingUpload.value)
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

/** 2026/11/05：删除聊天前只确认一次，服务端按目录版本原子清理消息和附件。 */
async function deleteRoom(item: Room) {
  if (!await confirm(`删除“${item.name}”及其全部聊天消息、图片？此操作无法复原。共享记忆与聊天配置不会删除。`, '删除聊天')) return
  await run(async () => {
    await request(`chat/rooms/${encodeURIComponent(item.id)}?revision=${item.revision}`, {method: 'DELETE'})
    if (roomId.value === item.id) {
      pollController?.abort()
      roomOpen.value = false
      roomId.value = ''
      picker.value = ''
      messages.value = []
      text.value = ''
      media.value = ''
      reply.value = ''
      pending.value = undefined
      pendingUpload.value = undefined
      receipt.value = undefined
      emit('roomState', false)
    }
    delete roomPages.value[item.id]
    await refreshHome()
  })
}

async function pull(signal?: AbortSignal) {
  if (!room.value) return
  const follow = !messagePane.value || messagePane.value.scrollHeight - messagePane.value.scrollTop - messagePane.value.clientHeight < 100
  const current = generation
  const sequence = ++pullSequence
  const result = await request<Page>(`${base()}/messages?after=${cursor.value}`, {signal})
  if (current !== generation || sequence !== pullSequence) return
  if (result.reset) {
    messages.value = []
    notice.value = '消息窗口已前移，正在显示当前可用记录；未显示的部分不能解释为没有发生。'
  }
  // 2026/11/04：空轮询不重建消息列表，保持输入焦点和已加载图片稳定。
  if (result.items.length) {
    const indexed = new Map(messages.value.map(item => [item.id, item]))
    result.items.forEach(item => indexed.set(item.id, item))
    messages.value = [...indexed.values()].sort((a, b) => a.sequence - b.sequence).slice(-2000)
  }
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
  if (id === roomId.value) { roomOpen.value = true; roomTab.value = 'messages'; emit('roomState', true); return }
  if ((pending.value || text.value.trim() || media.value || reply.value || pendingAdmin.value || pendingUpload.value)
    && !await confirm('当前有草稿或未确认请求。切换只放弃本页草稿，不撤销已受理消息；是否切换？', '放弃并切换')) {
    picker.value = roomId.value
    return
  }
  generation++
  pollController?.abort()
  busy.value = false
  roomId.value = id
  roomOpen.value = true
  roomTab.value = 'messages'
  emit('roomState', true)
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
  await run(async () => {
    await request(`${base()}/open`, {method: 'POST'})
    await pull(); await context(); await images()
  })
}

/** 2026/11/05：确认后重新检查房间与在途请求，防止对旧目标提交并覆盖原请求。 */
async function administrative(path: string, body: Admin['body'], action: string) {
  if (blocked.value) return
  const current = generation
  if (!await confirm(`${action}只作用于网页客户端。聊天中的成员权限与网页登录管理权限分开，确认提交？`, '确认提交')) return
  if (current !== generation || blocked.value) return
  pendingAdmin.value = { path, body, action }
  await run(retryAdmin)
}

/** 2026/11/05：权限切换复用原成员事务、版本与操作编号，不重建输入区或清除草稿。 */
async function changeSenderRole(role: string) {
  if (blocked.value || !room.value || !currentMember.value || role === currentMember.value.role) return
  const member = currentMember.value
  await administrative(`${base()}/members`, {
    operationId: operationId(), revision: room.value.revision,
    member: {profile: member.profile, card: member.card, role}, action: 'UPSERT'
  }, `将“${member.card || member.name}”的网页权限改为${currentRoles.value[role]}`)
}

/** 2026/11/05：重试仍使用原请求；切换聊天后迟到的管理响应不能覆盖新聊天状态。 */
async function retryAdmin() {
  const original = pendingAdmin.value!
  const current = generation
  const result = await request<{ id: string }>(original.path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(original.body) })
  if (current !== generation) return
  pendingAdmin.value = undefined
  nickname.value = ''
  await refreshHome()
  if (current !== generation) return
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

/** 2026/11/04：中文输入法组合输入期间不触发快捷发送。 */
function sendShortcut(event: Event | KeyboardEvent) {
  if (!(event instanceof KeyboardEvent) || event.isComposing) return
  if (event.ctrlKey && event.key === 'Enter') { event.preventDefault(); void send(); return }
  if (!completions.value.length) return
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    completionIndex.value = (completionIndex.value + (event.key === 'ArrowDown' ? 1 : completions.value.length - 1)) % completions.value.length
  } else if (event.key === 'Tab' || event.key === 'Enter') {
    event.preventDefault()
    completeCommand((completions.value[completionIndex.value] ?? completions.value[0])!.text)
  } else if (event.key === 'Escape') { event.preventDefault(); completionDismissed.value = true }
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
  if (file) await uploadFile(file)
}

/** 2026/11/04：按钮选择和拖放采用相同的单图、类型与容量校验。 */
async function uploadFile(file: File) {
  if (blocked.value || !room.value) return
  if (file.type && !['image/png', 'image/jpeg', 'image/gif'].includes(file.type)) {
    error.value = '请选择 PNG、JPEG 或 GIF 图片。'
    return
  }
  if (file.size > 1048576) { error.value = '每张图片最多一 MiB，标准 PNG 超过上限也会被拒绝。'; return }
  pendingUpload.value = { room: roomId.value, id: crypto.randomUUID(), file }
  await run(retryUpload)
}

/** 2026/11/04：每条消息只支持一张附图，不能悄悄丢弃多文件拖放。 */
function dropImage(event: DragEvent) {
  dragging.value = false
  if (blocked.value) return
  const files = event.dataTransfer?.files
  if (!files?.length) return
  event.preventDefault()
  if (files.length !== 1) { error.value = '每条消息请选择一张图片。'; return }
  void uploadFile(files[0]!)
}

/** 2026/11/04：只接管文件拖放，正文里的普通文字拖动仍由浏览器处理。 */
function dragImage(event: DragEvent) {
  if (!event.dataTransfer?.types.includes('Files')) return
  event.preventDefault()
  dragging.value = !blocked.value
}

/** 2026/11/04：跨越输入区子元素不闪烁，真正离开区域才清除拖放提示。 */
function leaveImage(event: DragEvent) {
  if (event.relatedTarget instanceof Node && (event.currentTarget as HTMLElement).contains(event.relatedTarget)) return
  dragging.value = false
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
  openRoomPage('agent')
}

/** 2026/11/04：设置入口携带当前真实聊天，省去再次选择会话。 */
function openSettings() {
  openRoomPage('chat-config')
}

/** 2026/11/04：后台读取不占用编辑锁，且不会清除正在输入的新草稿。 */
async function poll() {
  if (polling || busy.value || !props.active || !roomOpen.value || document.hidden || !room.value) return
  const current = generation
  const controller = new AbortController()
  pollController = controller
  polling = true
  try {
    await pull(controller.signal)
    const original = receipt.value
    if (current === generation && original?.status === 'PERSISTED') {
      const result = await request<Receipt>(`${base()}/operations/${encodeURIComponent(original.operationId)}`, {signal: controller.signal})
      if (current === generation && receipt.value?.operationId === original.operationId) receipt.value = result
    }
  } catch (failure) {
    if (current !== generation || controller.signal.aborted) return
    error.value = failure instanceof Error ? failure.message : '消息读取未完成。'
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
  } finally {
    polling = false
    if (pollController === controller) pollController = undefined
  }
}

function beforeUnload(event: BeforeUnloadEvent) {
  if (text.value.trim() || media.value || pending.value || pendingAdmin.value || pendingUpload.value) { event.preventDefault(); event.returnValue = '' }
}

// 2026/11/05：成员版本变化时刷新指令权限投影，角色切换不留下旧身份的补全结果。
watch([roomOpen, roomId, sender, () => room.value?.revision], () => void loadCommands())
// 2026/11/04：从房间管理子页返回时重读启用状态，不占用发送锁，也不重置输入草稿。
watch(roomTab, async (tab, previous) => {
  if (tab !== 'messages' || !['chat-config', 'agent'].includes(previous) || !roomOpen.value) return
  try { await context() }
  catch (failure) {
    error.value = failure instanceof Error ? failure.message : '聊天配置读取未完成。'
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
  }
})
watch(text, (value, previous) => {
  completionIndex.value = 0
  completionDismissed.value = false
  if (value.startsWith('/') && !previous.startsWith('/')) void loadCommands()
})
onMounted(() => {
  void run(refresh)
  window.addEventListener('beforeunload', beforeUnload)
  interval = setInterval(() => void poll(), 2000)
})
onBeforeUnmount(() => { generation++; commandEpoch++; commandController?.abort(); pollController?.abort(); clearInterval(interval); pending.value = undefined; pendingAdmin.value = undefined; pendingUpload.value = undefined; window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <!-- 2026/11/04：简洁模式优先展示消息与发送，保留成员选择和原请求处理。 -->
  <div class="web-chat" :class="{'web-chat--room': roomOpen}">
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <ElAlert v-if="notice" :title="notice" type="info" :closable="false" />
    <ElCard v-if="!roomOpen" shadow="never" class="runtime-card"><div class="editor-heading"><h2>选择聊天室</h2><RefreshButton :disabled="busy" @click="run(refresh)" label="刷新聊天与档案" /></div>
      <!-- 2026/11/04：常驻创建入口，不要求先进入档案管理页。 -->
      <div class="web-room-picker">
        <div class="editor-actions"><ElButton :disabled="busy || !home" @click="openCreateRoom('PRIVATE')"><ConsoleIcon name="private" /> 创建私聊</ElButton><ElButton :disabled="busy || !home" @click="openCreateRoom('GROUP')"><ConsoleIcon name="group" /> 创建模拟群</ElButton><ElButton @click="profileOpen = true">网页档案</ElButton></div>
        <div class="agent-tabs" aria-label="聊天室类型"><button v-for="item in [{id: '', name: '全部'}, {id: 'PRIVATE', name: '私聊'}, {id: 'GROUP', name: '群聊'}]" :key="item.id" :class="{selected: directoryKind === item.id}" @click="directoryKind = item.id">{{ item.name }}</button></div>
        <!-- 2026/11/05：进入与删除是独立按钮，避免嵌套点击。 -->
        <div class="web-room-grid"><div v-for="item in visibleRooms" :key="item.id" class="web-room-entry"><button class="web-room-card" :disabled="busy" @click="choose(item.id)"><ConsoleIcon :name="item.kind === 'GROUP' ? 'group' : 'private'" /><strong>{{ item.name }}</strong><span class="small muted">{{ item.kind === 'GROUP' ? `${item.members.length} 位成员` : '私聊' }}</span></button><button class="icon-button room-delete" :disabled="blocked" :aria-label="`删除聊天 ${item.name}`" title="删除聊天" @click="deleteRoom(item)"><ConsoleIcon name="trash" /></button></div></div>
        <ElEmpty v-if="home && !visibleRooms.length" description="创建一个聊天室开始对话" />
      </div>
    </ElCard>
    <!-- 2026/11/04：关闭弹窗保留表单及原请求，可返回页面处理未确认回执。 -->
    <ElDialog v-model="createRoomOpen" title="新建私聊或模拟群" width="min(520px, 94vw)" class="web-chat">
      <div v-if="home" class="web-create-form">
        <label>聊天显示名<ElInput v-model="roomName" maxlength="80" aria-label="新网页聊天名称" :disabled="blocked" placeholder="聊天显示名" /></label>
        <label>聊天类型<ElSelect v-model="roomKind" aria-label="新网页聊天类型" :disabled="blocked"><ElOption label="私聊" value="PRIVATE"><ConsoleIcon name="private" /> 私聊</ElOption><ElOption label="模拟群" value="GROUP"><ConsoleIcon name="group" /> 模拟群</ElOption></ElSelect></label>
        <label>聊天成员<ElSelect v-model="selectedProfiles" multiple aria-label="新网页聊天成员" :disabled="blocked"><ElOption v-for="profile in home.profiles" :key="profile.id" :label="profile.name" :value="profile.id" /></ElSelect></label>
        <p class="small muted">私聊选择一位档案，每个档案只有一个私聊；模拟群可选多位。初始权限均为普通成员。私聊首次登记时默认启用 Agent，已有关闭设置会保留；群聊需自行启用。新增档案可通过“网页档案”。</p>
        <ElAlert v-if="error" :title="error" type="error" :closable="false" />
        <ElButton type="primary" :disabled="blocked || !roomName.trim() || !selectedProfiles.length || roomKind === 'PRIVATE' && selectedProfiles.length !== 1" @click="administrative('chat/rooms', { operationId: operationId(), kind: roomKind, name: roomName, profiles: [...selectedProfiles] }, '创建网页聊天')">创建聊天</ElButton>
      </div>
    </ElDialog>
    <div v-if="roomOpen && room" class="web-room-heading"><button class="icon-button" aria-label="返回聊天室列表" title="返回聊天室列表" @click="backToRooms"><ConsoleIcon name="back" /></button><h2>{{ room.name }}</h2><ElTag>{{ room.kind === 'GROUP' ? '群聊' : '私聊' }}</ElTag><AgentChatStatus :target="roomTarget(room)" :active="active && roomOpen && roomTab === 'messages'" :enabled="!!settings?.registered && settings.fields.some(field => field.name === 'enabled' && field.value === 'true')" @expired="emit('expired')" /></div>
    <!-- 2026/11/05：权限下拉固定在聊天室标签栏右端，私聊与群聊共用当前发言成员。 -->
    <div v-if="home" v-show="roomOpen" class="web-chat-workspace">
      <label v-if="currentMember" class="web-chat-role" :title="`当前发言档案：${currentMember.card || currentMember.name}`">
        <span>权限</span>
        <ElSelect :model-value="currentMember.role" aria-label="当前网页用户权限" :disabled="blocked" @change="changeSenderRole">
          <ElOption v-for="(label, role) in currentRoles" :key="role" :label="label" :value="role" />
        </ElSelect>
      </label>
    <ElTabs v-model="roomTab" class="web-chat-tabs" @tab-change="value => { if (value === 'chat-config' || value === 'agent') openRoomPage(value) }">
      <ElTabPane label="聊天" name="messages" class="web-conversation">
        <ElEmpty v-if="!room" description="创建一个私聊或模拟群后开始；每个档案只对应一个私聊" />
        <template v-else>
          <div ref="messagePane" class="web-messages" aria-label="网页聊天消息" aria-live="polite">
            <ElEmpty v-if="!messages.length" description="当前消息窗口为空" />
            <article v-for="message in messages" :id="'web-message-' + message.id" :key="message.id" class="web-bubble" :class="{ 'web-own-bubble': !message.bot && message.sender.id === sender, 'is-referenced': highlighted === message.id }">
              <div class="web-message-heading"><strong>{{ message.card || message.sender.name }}</strong><ElTag v-if="advanced" size="small">{{ message.bot ? 'TeaNeko' : roles[message.role] ?? message.role }}</ElTag><time>{{ date(message.at) }}</time></div>
              <template v-for="(part, index) in message.parts" :key="index">
                <p v-if="part.kind === 'TEXT'" class="web-message-text">{{ part.value }}</p>
                <p v-else-if="part.kind === 'AT'" class="web-mention">@{{ profileName(part.value) }}</p>
                <div v-else-if="part.kind === 'REPLY'" class="web-reply"><button @click="locateReply(part.value)">{{ replyPreview(part.value) }}</button><code v-if="advanced" class="small revision">引用 ID：{{ part.value }}</code></div>
                <img v-else-if="part.kind === 'IMAGE'" :src="imageUrl(part.value)" class="web-message-image" alt="当前网页聊天的受限图片" loading="lazy" />
              </template>
              <p v-show="advanced" class="small revision">{{ message.id }}</p><ElButton text :disabled="blocked" @click="reply = message.id">引用</ElButton>
            </article>
          </div>
          <ElButton v-if="more" :disabled="busy" @click="run(pull)">继续读取消息</ElButton>
          <!-- 2026/11/04：将附件、正文和发送动作合为一个可拖放的输入区。 -->
          <ElCard shadow="never" class="web-composer-card">
            <div v-if="reply" class="web-reply composer-reply"><span>{{ replyPreview(reply) }}</span><code v-if="advanced" class="small revision">{{ reply }}</code><ElButton text :disabled="blocked" @click="reply = ''">取消引用</ElButton></div>
            <div v-if="completions.length" class="command-completions" role="listbox" aria-label="可用指令补全"><button v-for="(item, index) in completions" :key="item.text" role="option" :aria-selected="completionIndex === index" :class="{selected: completionIndex === index}" @mousedown.prevent="completeCommand(item.text)"><strong>{{ item.text }}</strong><span>{{ item.description || item.usage }}</span></button></div>
            <p v-else-if="text.startsWith('/') && (commandsLoading || commandError)" class="small muted">{{ commandsLoading ? '正在读取当前成员可用的指令…' : commandError }}</p>
            <div class="web-composer" :class="{'is-dragging': dragging}" @dragover="dragImage" @dragleave="leaveImage" @drop="dropImage">
              <div v-if="media" class="web-attachment"><img :src="imageUrl(media)" alt="待发送的附图" /><button class="icon-button attachment-remove" aria-label="移除附图" title="移除附图" :disabled="blocked" @click="media = ''"><ConsoleIcon name="close" /></button></div>
              <div class="web-composer-row">
                <input ref="fileInput" class="visually-hidden" type="file" tabindex="-1" accept="image/png,image/jpeg,image/gif" aria-label="上传网页聊天图片" :disabled="blocked" @change="upload" />
                <button class="icon-button composer-add" aria-label="添加图片" title="添加图片（也可拖放）" :disabled="blocked" @click="fileInput?.click()"><ConsoleIcon name="plus" /></button>
                <ElInput v-model="text" type="textarea" :autosize="{minRows: 1, maxRows: 8}" maxlength="16384" aria-label="网页消息正文" :disabled="blocked" placeholder="发送消息，输入 / 查看指令，或拖入图片" @keydown="sendShortcut" />
                <button class="icon-button composer-send" aria-label="发送消息" title="发送消息（Ctrl+Enter）" :disabled="blocked || !sender || !text.trim() && !media" @click="send"><ConsoleIcon name="send" /></button>
              </div>
              <p v-if="dragging" class="small muted composer-drop-hint">松开即可上传图片</p>
            </div>
            <div class="composer-footer"><ElSwitch v-if="room.kind === 'GROUP'" v-model="mention" active-text="提及 TeaNeko" :disabled="blocked" /><span v-if="receipt?.status === 'PERSISTED'" class="small muted">消息处理中…</span><span class="small muted">Ctrl+Enter 发送 · 图片最大 1 MiB</span></div>
          </ElCard>
          <ElAlert v-if="receipt && ['REJECTED', 'INDETERMINATE'].includes(receipt.status)" :title="receipt.note" type="warning" :closable="false" />
        </template>
      </ElTabPane>
      <ElTabPane label="会话设置" name="settings">
        <ElCard v-if="room" shadow="never" class="runtime-card"><h3>当前聊天</h3><p v-if="settings && !settings.registered">群聊尚未登记 Agent；网页私聊默认启用，已有关闭设置会保留。</p><div class="editor-actions"><ElButton @click="openSettings">聊天设置</ElButton><ElButton @click="inspect">活动与记忆</ElButton></div><p>记忆空间：{{ namespace }}；人格按当前聊天独立。</p><p v-for="field in learning" :key="field.name">{{ field.label }}：{{ settings?.registered && field.value === 'true' ? '开启' : '关闭或尚未登记' }}</p>
          <label for="web-sender">当前发言档案</label><ElSelect id="web-sender" v-model="sender" aria-label="网页发言档案" :disabled="blocked"><ElOption v-for="member in members" :key="member.profile" :label="`${member.card || member.name} · ${roles[member.role]}`" :value="member.profile" /></ElSelect>
        </ElCard>
        <ElCard v-if="advanced && receipt" shadow="never" class="operation-receipt"><h3>{{ status[receipt.status] ?? receipt.status }}</h3><p>{{ receipt.note }}</p><p v-if="advanced" class="small revision">原操作：{{ receipt.operationId }}<br />消息：{{ receipt.messageId }}</p><ElButton :disabled="busy" @click="run(query)">查询原消息回执</ElButton></ElCard>
      </ElTabPane>
      <ElTabPane label="成员" name="members">
        <!-- 2026/11/04：档案与成员分卡片留白，三个成员字段和提交操作分别排列。 -->
        <div class="web-chat-admin">
          <ElButton @click="profileOpen = true">管理网页档案</ElButton>
          <ElCard v-if="room?.kind === 'GROUP'" shadow="never" class="runtime-card"><h3>当前群成员管理</h3><p class="small muted">保存会撤销旧消息的借用能力。角色只影响网页平台，不能获得其他客户端或 DEBUG 权限。</p><p v-for="member in members" :key="member.profile">{{ member.card }} · {{ roles[member.role] }} <ElButton text :disabled="blocked" @click="administrative(`${base()}/members`, { operationId: operationId(), revision: room!.revision, member: { profile: member.profile, card: member.card, role: member.role }, action: 'REMOVE' }, '移出网页成员')">移出</ElButton></p>
            <div class="web-member-form">
              <label>成员档案<ElSelect v-model="memberProfile" aria-label="待管理网页成员" :disabled="blocked"><ElOption v-for="profile in home.profiles" :key="profile.id" :label="profile.name" :value="profile.id" /></ElSelect></label>
              <label>群名片<ElInput v-model="memberCard" aria-label="网页群名片" maxlength="80" :disabled="blocked" placeholder="群名片" /></label>
              <label>群内角色<ElSelect v-model="memberRole" aria-label="网页成员角色" :disabled="blocked"><ElOption label="普通成员" value="MEMBER" /><ElOption label="群管理员" value="ADMIN" /><ElOption label="群主" value="OWNER" /></ElSelect></label>
              <div class="editor-actions"><ElButton :disabled="blocked || !memberProfile || !memberCard.trim()" @click="administrative(`${base()}/members`, { operationId: operationId(), revision: room!.revision, member: { profile: memberProfile, card: memberCard, role: memberRole }, action: 'UPSERT' }, '保存网页成员')">保存成员</ElButton></div>
            </div>
          </ElCard>
        </div>
      </ElTabPane>
      <ElTabPane label="图片" name="media"><ElEmpty v-if="!room" description="先选择一个聊天" /><div v-else class="web-media-grid"><ElCard v-for="id in mediaIds" :key="id" shadow="never"><img :src="imageUrl(id)" class="web-message-image" alt="当前聊天图片" loading="lazy" /><p v-if="advanced" class="small revision">{{ id }}</p><ElButton :disabled="blocked" @click="media = id; roomTab = 'messages'">选为附图</ElButton><ElButton :disabled="blocked" @click="removeImage(id)">删除未引用图片</ElButton></ElCard></div></ElTabPane>
      <ElTabPane label="聊天设置" name="chat-config"><template v-for="item in home.rooms" :key="item.id"><ChatSettingsView v-if="roomPages[item.id]?.settings" v-show="roomId === item.id" :clients="clients" initial-client="web" :fixed-target="roomTarget(item)" embedded @expired="emit('expired')" @inspect="inspect" /></template></ElTabPane>
      <ElTabPane label="活动与记忆" name="agent"><template v-for="item in home.rooms" :key="item.id"><AgentWorkspaceView v-if="roomPages[item.id]?.agent" v-show="roomId === item.id" :target="roomTarget(item)" :active="active && roomOpen && roomId === item.id && roomTab === 'agent'" embedded @expired="emit('expired')" /></template></ElTabPane>
    </ElTabs>
    </div>
    <ElDialog v-model="profileOpen" title="网页档案" width="min(560px, 94vw)"><div v-if="home" class="web-profile-form"><ElInput v-model="nickname" maxlength="80" aria-label="新网页档案昵称" :disabled="blocked" placeholder="例如：小茶" /><ElButton :disabled="blocked || !nickname.trim()" @click="administrative('chat/profiles', { operationId: operationId(), name: nickname }, '创建网页档案')">创建档案</ElButton><p v-for="profile in home.profiles" :key="profile.id">{{ profile.name }}<span v-if="advanced" class="small revision"> · {{ profile.id }} · {{ profile.uuid }}</span></p></div></ElDialog>
    <ElCard v-if="pendingFailed && !busy && (pending || pendingAdmin || pendingUpload)" shadow="never" class="pending-operation"><h3>原请求结果待确认</h3><p>连接失败不表示未受理；保留原编号和原文，不会自动重发。</p><p class="small revision">{{ pending?.body.operationId ?? pendingUpload?.id ?? pendingAdmin?.body.operationId }}</p><ElButton v-if="pending" :disabled="busy" @click="run(query)">查询原请求</ElButton><ElButton :disabled="busy" @click="run(pending ? deliver : pendingAdmin ? retryAdmin : retryUpload)">原样重试</ElButton><ElButton :disabled="busy" @click="forgetPending">放弃原请求</ElButton></ElCard>
  </div>
</template>

