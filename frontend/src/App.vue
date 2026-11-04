<script setup lang="ts">
// 2026/11/04：刷新类操作统一使用带完整动作提示的图标按钮。
import RefreshButton from './RefreshButton.vue'
import {computed, nextTick, onBeforeUnmount, onMounted, ref} from 'vue'
import {ElAlert, ElButton, ElCard, ElConfigProvider, ElDrawer, ElEmpty} from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import LoginView from './LoginView.vue'
import ChatSettingsView from './ChatSettingsView.vue'
import type {AgentTarget} from './AgentWorkspaceView.vue'
import AgentWorkspaceView from './AgentWorkspaceView.vue'
import ChatTargetPicker from './ChatTargetPicker.vue'
import FileSettingsView from './FileSettingsView.vue'
import RuntimeView from './RuntimeView.vue'
import CommandConsoleView from './CommandConsoleView.vue'
// 2026/11/04：指令说明和模型文件分别保留页面及编辑生命周期。
import CommandsView from './CommandsView.vue'
import AgentApiDocsView from './AgentApiDocsView.vue'
import WebChatView from './WebChatView.vue'
import type {Overview, Platform, Session} from './api'
import {ApiError, login, logout, request} from './api'
import {rememberLoginToken, takeLoginToken} from './login-token'
import AppearanceControls from './AppearanceControls.vue'
import ConsoleIcon from './ConsoleIcon.vue'
import type {ConsolePage} from './ConsoleNavigation.vue'
import ConsoleNavigation from './ConsoleNavigation.vue'
import DetailPanel from './DetailPanel.vue'
import {provideConsolePreferences} from './console-preferences'
import MachineTokensView from './MachineTokensView.vue'
import MachineStatusView from './MachineStatusView.vue'

// 2026/11/04：界面状态只控制呈现，原页面保持挂载以保留草稿和待确认请求。
const {collapsed, toggleSidebar} = provideConsolePreferences()
const mobileOpen = ref(false)
const mobileQuery = window.matchMedia('(max-width: 760px)')
function closeMobileOnDesktop(event: MediaQueryListEvent) {
  if (!event.matches) mobileOpen.value = false
}
mobileQuery.addEventListener('change', closeMobileOnDesktop)
onBeforeUnmount(() => mobileQuery.removeEventListener('change', closeMobileOnDesktop))

// 2026/11/04：先消费链接令牌，再进行任何会话请求，包括已有登录会话的情况。
const initialToken = ref(takeLoginToken())

const session = ref<Session>()
const overview = ref<Overview>()
const machineView = ref<InstanceType<typeof MachineStatusView>>()
const platforms = ref<Platform[]>([])
const busy = ref(false)
const error = ref('')
const page = ref<ConsolePage>('overview')
const chatOpened = ref(false)
const consoleTarget = ref<AgentTarget>()
const consoleView = ref<InstanceType<typeof CommandConsoleView>>()
const runtimeOpened = ref(false)
const filesOpened = ref(false)
const modelsOpened = ref(false)
const commandsOpened = ref(false)
const chatImmersive = ref(false)
const agentTarget = ref<AgentTarget>()
// 2026/11/04：Agent 目录与聊天配置各自保留选择状态，不通过配置页中转。
const agentOpened = ref(false)
const agentPicker = ref<InstanceType<typeof ChatTargetPicker>>()
const agentView = ref<InstanceType<typeof AgentWorkspaceView>>()
const settingsOpened = ref(false)
const settingsView = ref<InstanceType<typeof ChatSettingsView>>()
const agentPickerOpen = ref(false)
const tokensOpened = ref(false)
const selectedClient = ref('')
const title = computed(() => ({ overview: '运行概览', platforms: '客户端', session: '当前会话', settings: '聊天配置', agent: 'Agent 工作区', files: '本地配置', models: '模型配置', commands: '指令一览', components: '组件与系统', tools: '工具目录', runtime: '任务与日志', console: '指令台', chat: '网页聊天' })[page.value])
const connectionCount = computed(() => platforms.value.reduce((total, item) => total + item.connections, 0))
const date = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '尚未更新'

function navigate(value: ConsolePage) {
  // 2026/11/04：令牌页只在首次进入客户端时加载，不把秘密并入概览请求。
  if (value === 'platforms') tokensOpened.value = true
  if (value === 'settings') settingsOpened.value = true
  if (value === 'files') filesOpened.value = true
  if (value === 'models') modelsOpened.value = true
  if (value === 'commands') commandsOpened.value = true
  if (value === 'runtime') runtimeOpened.value = true
  if (value === 'chat') chatOpened.value = true
  if (value === 'agent') agentOpened.value = true
  page.value = value
  mobileOpen.value = false
}

function openSettings(client = '') {
  selectedClient.value = client
  navigate('settings')
}

// 2026/11/04：网页聊天携带平台、账号及目标直达设置，复用设置页的草稿确认。
async function openChatSettings(target: AgentTarget) {
  navigate('settings')
  await nextTick()
  await settingsView.value?.openTarget(target)
}

async function selectAgentTarget() {
  agentPickerOpen.value = true
  await nextTick()
  agentPicker.value?.focus()
}

function clearSession() {
  session.value = undefined
  overview.value = undefined
  platforms.value = []
  settingsOpened.value = false
  tokensOpened.value = false
  filesOpened.value = false
  modelsOpened.value = false
  commandsOpened.value = false
  chatImmersive.value = false
  runtimeOpened.value = false
  agentTarget.value = undefined
  agentOpened.value = false
  consoleTarget.value = undefined
  chatOpened.value = false
  mobileOpen.value = false
}

// 2026/11/04：应用成功先明确显示结果，再以原管理员令牌重新登录，不能误报保存失败。
function machineTokensApplied() {
  clearSession()
  page.value = 'platforms'
  error.value = '机器令牌已应用，请使用原管理员令牌重新登录。'
}

async function inspectAgent(target: AgentTarget) {
  agentOpened.value = true
  if (agentTarget.value && JSON.stringify(agentTarget.value) === JSON.stringify(target)) { agentPickerOpen.value = false; page.value = 'agent'; return }
  if (agentTarget.value && JSON.stringify(agentTarget.value) !== JSON.stringify(target) && !await agentView.value?.canLeave()) return
  agentTarget.value = target
  agentPickerOpen.value = false
  page.value = 'agent'
}

async function inspectConsole(target: AgentTarget) {
  if (consoleTarget.value && JSON.stringify(consoleTarget.value) === JSON.stringify(target)) { page.value = 'console'; return }
  if (consoleTarget.value && JSON.stringify(consoleTarget.value) !== JSON.stringify(target) && !await consoleView.value?.canLeave()) return
  consoleTarget.value = target
  page.value = 'console'
}

async function refresh() {
  session.value = await request<Session>('auth/session')
  const [application, clients] = await Promise.all([
    request<Overview>('console/overview'), request<Platform[]>('clients'),
  ])
  overview.value = application
  platforms.value = clients
  if (page.value === 'platforms') tokensOpened.value = true
}

// 2026/11/04：首次加载由组件自行采样，只有手动刷新同步读取机器卡片。
async function refreshPage() {
  await refresh()
  await machineView.value?.refresh()
}

async function run(action: () => Promise<void>, initial = false) {
  busy.value = true
  error.value = ''
  try {
    await action()
  } catch (failure) {
    if (failure instanceof ApiError && failure.status === 401) {
      clearSession()
      if (!initial) error.value = failure.message
    } else {
      error.value = failure instanceof Error ? failure.message : '暂时无法连接控制台。'
    }
  } finally {
    busy.value = false
  }
}

// 2026/11/04：只有服务端认证成功后才按用户选择持久化令牌。
async function signIn(token: string, remember: boolean) {
  await run(async () => {
    await login(token)
    const stored = rememberLoginToken(token, remember)
    initialToken.value = ''
    await refresh()
    if (!stored) error.value = '已登录，但浏览器未允许保存或清除令牌。'
  })
}

async function signOut() {
  await run(async () => {
    await logout()
    clearSession()
    page.value = 'overview'
  })
}

onMounted(() => run(refresh, true))
</script>

<template>
  <!-- 2026/11/04：使用分组导航与轻量页头，保留所有领域页面的挂载和请求生命周期。 -->
  <ElConfigProvider :locale="zhCn">
  <LoginView v-if="!session" :busy="busy" :error="error" :initial-token="initialToken" @login="signIn" />
  <div v-else class="console-shell">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <aside class="sidebar" :class="{'is-collapsed': collapsed}">
      <ConsoleNavigation :page="page" :collapsed="collapsed" :version="overview?.version"
        :settings-available="platforms.some(item => item.settingsAvailable)" @select="navigate" />
    </aside>
    <ElDrawer v-model="mobileOpen" direction="ltr" :size="260" :with-header="false" title="控制台导航" class="navigation-drawer">
      <ConsoleNavigation :page="page" :version="overview?.version"
        :settings-available="platforms.some(item => item.settingsAvailable)" @select="navigate" />
    </ElDrawer>
    <div class="workspace">
      <header class="topbar">
        <div class="topbar-location">
          <button class="icon-button desktop-menu" :aria-label="collapsed ? '展开侧栏' : '收起侧栏'" :aria-expanded="!collapsed" @click="toggleSidebar"><ConsoleIcon name="menu" /></button>
          <button class="icon-button mobile-menu" aria-label="打开导航" :aria-expanded="mobileOpen" @click="mobileOpen = true"><ConsoleIcon name="menu" /></button>
          <span class="breadcrumb">控制台<span aria-hidden="true">/</span>{{ title }}</span>
        </div>
        <AppearanceControls show-mode />
      </header>
      <main id="main-content" class="content" tabindex="-1">
        <div v-show="page !== 'chat' || !chatImmersive" class="page-heading"><h1>{{ title }}</h1>
          <RefreshButton v-if="['overview', 'platforms'].includes(page)" :loading="busy" @click="run(refreshPage)" label="刷新" /></div>
        <ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon />
        <ChatSettingsView v-if="settingsOpened" ref="settingsView" v-show="page === 'settings'" :clients="platforms" :initial-client="selectedClient" @expired="clearSession" @inspect="inspectAgent" @console="inspectConsole" />
        <section v-if="agentOpened" v-show="page === 'agent'">
          <ChatTargetPicker v-show="!agentTarget || agentPickerOpen" ref="agentPicker" :clients="platforms" :target="agentTarget" @expired="clearSession" @select="inspectAgent" />
          <AgentWorkspaceView v-if="agentTarget" ref="agentView" :target="agentTarget" :active="page === 'agent'" @expired="clearSession" @select-target="selectAgentTarget" />
        </section>
        <FileSettingsView v-if="filesOpened" v-show="page === 'files'" @expired="clearSession" />
        <FileSettingsView v-if="modelsOpened" v-show="page === 'models'" model-mode @expired="clearSession" />
        <CommandsView v-if="commandsOpened" v-show="page === 'commands'" @expired="clearSession" />
          <AgentApiDocsView v-if="page === 'components' || page === 'tools'" :mode="page" @expired="clearSession" />
        <RuntimeView v-if="runtimeOpened" v-show="page === 'runtime'" :active="page === 'runtime'" @expired="clearSession" />
        <CommandConsoleView v-if="consoleTarget" ref="consoleView" v-show="page === 'console'" :target="consoleTarget" :active="page === 'console'" @expired="clearSession" @select-target="openSettings()" />
        <ElEmpty v-if="page === 'console' && !consoleTarget" description="先选择要管理的聊天">
          <ElButton type="primary" @click="openSettings()">选择聊天</ElButton>
        </ElEmpty>
        <WebChatView v-if="chatOpened" v-show="page === 'chat'" :clients="platforms" :active="page === 'chat'" @room-state="value => chatImmersive = value" @expired="clearSession" @inspect="inspectAgent" @settings="openChatSettings" />
        <template v-if="page === 'overview'">
          <div class="stats"><ElCard shadow="never"><div class="stat-label"><span>客户端</span><ConsoleIcon name="platforms" /></div><strong>{{ platforms.length }}</strong></ElCard>
            <ElCard shadow="never"><div class="stat-label"><span>开放连接</span><span class="live-dot"></span></div><strong>{{ connectionCount }}</strong></ElCard>
            <ElCard shadow="never"><div class="stat-label"><span>应用版本</span><ConsoleIcon name="overview" /></div><strong class="version">{{ overview?.version ?? '读取中' }}</strong></ElCard></div>
          <!-- 2026/11/04：仅概览持有采样组件，离开或退出会销毁并中止刷新。 -->
          <MachineStatusView ref="machineView" @expired="clearSession" />
          <div class="section-title"><h2>客户端</h2><span class="small muted">{{ date(overview?.observedAt) }}</span></div>
        </template>
        <div v-if="page === 'overview' || page === 'platforms'" class="platform-grid">
          <ElEmpty v-if="!platforms.length" description="还没有可显示的聊天平台" />
          <ElCard v-for="platform in platforms" :key="platform.clientId" shadow="never" class="platform-card">
            <div class="card-heading"><div class="platform-symbol">{{ platform.name.slice(0, 1) }}</div><h2>{{ platform.name }}</h2>
              <span class="connection-state" :class="{ connected: platform.state === 'CONNECTED' }">
                {{ platform.state === 'CONNECTED' ? '已连接' : platform.state === 'AVAILABLE' ? '服务可用' : platform.state === 'DISABLED' ? '未启用' : '未连接' }}
              </span></div>
            <p class="muted">{{ platform.connections }} 个开放连接</p>
            <ElButton v-if="platform.settingsAvailable" type="primary" plain @click="openSettings(platform.clientId)">聊天配置</ElButton>
            <DetailPanel title="连接详情"><p class="small muted">{{ platform.explanation }}</p><span class="small muted">{{ date(platform.observedAt) }}</span></DetailPanel>
          </ElCard>
        </div>
        <ElCard v-if="page === 'session'" shadow="never" class="session-card">
          <h2>{{ session.name }}</h2><dl><dt>登录时间</dt><dd>{{ date(session.authenticatedAt) }}</dd>
            <dt>最长有效至</dt><dd>{{ date(session.expiresAt) }}</dd></dl>
          <p class="muted">长时间没有访问、服务器重启或凭据变更后，需要重新登录。</p>
          <ElButton @click="signOut" :disabled="busy">退出当前会话</ElButton>
        </ElCard>
        <MachineTokensView v-if="tokensOpened" v-show="page === 'platforms'" :active="page === 'platforms'" @expired="clearSession" @applied="machineTokensApplied" />
      </main>
    </div>
  </div>
  </ElConfigProvider>
</template>
