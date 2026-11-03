<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {ElAlert, ElButton, ElCard, ElConfigProvider, ElEmpty} from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import LoginView from './LoginView.vue'
import ChatSettingsView from './ChatSettingsView.vue'
import type {AgentTarget} from './AgentWorkspaceView.vue'
import AgentWorkspaceView from './AgentWorkspaceView.vue'
import FileSettingsView from './FileSettingsView.vue'
import RuntimeView from './RuntimeView.vue'
import CommandConsoleView from './CommandConsoleView.vue'
import WebChatView from './WebChatView.vue'
import type {Overview, Platform, Session} from './api'
import {ApiError, login, logout, request} from './api'
import {rememberLoginToken, takeLoginToken} from './login-token'

// 2026/11/04：先消费链接令牌，再进行任何会话请求，包括已有登录会话的情况。
const initialToken = ref(takeLoginToken())

const session = ref<Session>()
const overview = ref<Overview>()
const platforms = ref<Platform[]>([])
const busy = ref(false)
const error = ref('')
const page = ref<'overview' | 'platforms' | 'session' | 'settings' | 'agent' | 'files' | 'runtime' | 'console' | 'chat'>('overview')
const chatOpened = ref(false)
const consoleTarget = ref<AgentTarget>()
const consoleView = ref<InstanceType<typeof CommandConsoleView>>()
const runtimeOpened = ref(false)
const filesOpened = ref(false)
const agentTarget = ref<AgentTarget>()
const agentView = ref<InstanceType<typeof AgentWorkspaceView>>()
const settingsOpened = ref(false)
const selectedClient = ref('')
const title = computed(() => ({ overview: '运行概览', platforms: '聊天平台', session: '当前会话', settings: '聊天设置', agent: 'Agent 工作区', files: '本地配置', runtime: '任务与日志', console: '客户端指令台', chat: '网页聊天' })[page.value])
const connectionCount = computed(() => platforms.value.reduce((total, item) => total + item.connections, 0))
const date = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '尚未更新'

function openSettings(client = '') {
  selectedClient.value = client
  settingsOpened.value = true
  page.value = 'settings'
}

function clearSession() {
  session.value = undefined
  overview.value = undefined
  platforms.value = []
  settingsOpened.value = false
  filesOpened.value = false
  runtimeOpened.value = false
  agentTarget.value = undefined
  consoleTarget.value = undefined
  chatOpened.value = false
}

async function inspectAgent(target: AgentTarget) {
  if (agentTarget.value && JSON.stringify(agentTarget.value) === JSON.stringify(target)) { page.value = 'agent'; return }
  if (agentTarget.value && JSON.stringify(agentTarget.value) !== JSON.stringify(target) && !await agentView.value?.canLeave()) return
  agentTarget.value = target
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
  <ElConfigProvider :locale="zhCn">
  <LoginView v-if="!session" :busy="busy" :error="error" :initial-token="initialToken" @login="signIn" />
  <div v-else class="console-shell">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark">茶</span> TeaNeko</div>
      <p class="eyebrow">管理控制台</p>
      <nav aria-label="控制台导航">
        <button :class="{ active: page === 'overview' }" @click="page = 'overview'">运行概览</button>
        <button :class="{ active: page === 'platforms' }" @click="page = 'platforms'">聊天平台</button>
        <button v-if="platforms.some(item => item.settingsAvailable)" :class="{ active: page === 'settings' }" @click="openSettings()">聊天设置</button>
        <button v-if="agentTarget" :class="{ active: page === 'agent' }" @click="page = 'agent'">Agent 工作区</button>
        <button :class="{ active: page === 'files' }" @click="filesOpened = true; page = 'files'">本地配置</button>
        <button :class="{ active: page === 'runtime' }" @click="runtimeOpened = true; page = 'runtime'">任务与日志</button>
        <button v-if="consoleTarget" :class="{ active: page === 'console' }" @click="page = 'console'">客户端指令台</button>
        <button :class="{ active: page === 'chat' }" @click="chatOpened = true; page = 'chat'">网页聊天</button>
        <button :class="{ active: page === 'session' }" @click="page = 'session'">当前会话</button>
      </nav>
      <div class="sidebar-footer"><span class="live-dot"></span> 已认证连接<br /><small>{{ overview?.version }}</small></div>
    </aside>
    <main class="workspace">
      <header class="topbar"><span>TeaNeko App / {{ title }}</span><div><span>{{ session.name }}</span>
        <ElButton text :disabled="busy" @click="signOut">退出登录</ElButton></div></header>
      <section class="content">
        <div class="page-heading"><div><p class="eyebrow">TEANEKO CONSOLE</p><h1>{{ title }}</h1>
          <p class="muted">{{ page === 'session' ? '了解当前登录的有效期限。' : '查看当前状态，按需刷新最新信息。' }}</p></div>
          <ElButton :loading="busy" @click="run(refresh)">刷新状态</ElButton></div>
        <ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon />
        <ChatSettingsView v-if="settingsOpened" v-show="page === 'settings'" :clients="platforms" :initial-client="selectedClient" @expired="clearSession" @inspect="inspectAgent" @console="inspectConsole" />
        <AgentWorkspaceView v-if="agentTarget" ref="agentView" v-show="page === 'agent'" :target="agentTarget" :active="page === 'agent'" @expired="clearSession" @select-target="openSettings()" />
        <FileSettingsView v-if="filesOpened" v-show="page === 'files'" @expired="clearSession" />
        <RuntimeView v-if="runtimeOpened" v-show="page === 'runtime'" :active="page === 'runtime'" @expired="clearSession" />
        <CommandConsoleView v-if="consoleTarget" ref="consoleView" v-show="page === 'console'" :target="consoleTarget" :active="page === 'console'" @expired="clearSession" @select-target="openSettings()" />
        <WebChatView v-if="chatOpened" v-show="page === 'chat'" :active="page === 'chat'" @expired="clearSession" @inspect="inspectAgent" @settings="openSettings('web')" />
        <template v-if="page === 'overview'">
          <div class="stats"><ElCard shadow="never"><span class="muted">已接入的平台</span><strong>{{ platforms.length }}</strong></ElCard>
            <ElCard shadow="never"><span class="muted">当前开放连接</span><strong>{{ connectionCount }}</strong></ElCard>
            <ElCard shadow="never"><span class="muted">应用版本</span><strong class="version">{{ overview?.version ?? '读取中' }}</strong></ElCard></div>
          <div class="section-title"><h2>聊天平台</h2><span class="small muted">最后更新 {{ date(overview?.observedAt) }}</span></div>
        </template>
        <div v-if="page === 'overview' || page === 'platforms'" class="platform-grid">
          <ElEmpty v-if="!platforms.length" description="还没有可显示的聊天平台" />
          <ElCard v-for="platform in platforms" :key="platform.clientId" shadow="never" class="platform-card">
            <div class="card-heading"><div class="platform-symbol">{{ platform.name.slice(0, 1) }}</div><h2>{{ platform.name }}</h2>
              <span class="connection-state" :class="{ connected: platform.state === 'CONNECTED' }">
                {{ platform.state === 'CONNECTED' ? '已连接' : platform.state === 'AVAILABLE' ? '服务可用' : platform.state === 'DISABLED' ? '未启用' : '未连接' }}
              </span></div>
            <p class="muted">{{ platform.explanation }}</p>
            <ElButton v-if="platform.settingsAvailable" text type="primary" @click="openSettings(platform.clientId)">管理聊天设置</ElButton>
            <div class="card-footer"><span>{{ platform.connections }} 个开放连接</span><span>{{ date(platform.observedAt) }}</span></div>
          </ElCard>
        </div>
        <ElCard v-if="page === 'session'" shadow="never" class="session-card">
          <h2>{{ session.name }}</h2><dl><dt>登录时间</dt><dd>{{ date(session.authenticatedAt) }}</dd>
            <dt>最长有效至</dt><dd>{{ date(session.expiresAt) }}</dd></dl>
          <p class="muted">长时间没有访问、服务器重启或凭据变更后，需要重新登录。</p>
          <ElButton @click="signOut" :disabled="busy">退出当前会话</ElButton>
        </ElCard>
      </section>
    </main>
  </div>
  </ElConfigProvider>
</template>
