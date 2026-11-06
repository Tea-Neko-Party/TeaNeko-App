<script setup lang="ts">
// 2026/11/06：外壳仅管理认证与布局，页面声明、业务跳转和草稿生命周期交给页面宿主。
import {onBeforeUnmount, onMounted, ref} from 'vue'
import {ElAlert, ElConfigProvider, ElDrawer} from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import LoginView from './LoginView.vue'
import type {Overview, Platform, Session} from './api'
import {ApiError, login, logout, request} from './api'
import {rememberLoginToken, takeLoginToken} from './login-token'
import AppearanceControls from './AppearanceControls.vue'
import ConsoleIcon from './ConsoleIcon.vue'
import ConsoleNavigation from './ConsoleNavigation.vue'
import {provideConsolePreferences} from './console-preferences'
import ConsolePageHost from './pages/ConsolePageHost.vue'
import type {PageEnvironment} from './pages/page-contracts'
import type {ConsolePageKey} from './pages/page-registry'

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
const platforms = ref<Platform[]>([])
const busy = ref(false)
const error = ref('')
const initialPage = ref<ConsolePageKey>('overview')
const pages = ref<InstanceType<typeof ConsolePageHost>>()
const sessionEpoch = ref(0)
const environment: PageEnvironment = {
  session, overview, platforms, busy, refresh, expired: clearSession, tokensApplied: machineTokensApplied, signOut,
}

async function navigate(key: ConsolePageKey) {
  if (await pages.value?.navigate(key)) mobileOpen.value = false
}

function clearSession() {
  sessionEpoch.value++
  session.value = undefined
  overview.value = undefined
  platforms.value = []
  mobileOpen.value = false
  initialPage.value = 'overview'
}

// 2026/11/04：应用成功先明确显示结果，再以原管理员令牌重新登录，不能误报保存失败。
function machineTokensApplied() {
  clearSession()
  initialPage.value = 'platforms'
  error.value = '机器令牌已应用，请使用原管理员令牌重新登录。'
}

async function refresh() {
  // 2026/11/06：旧会话的迟到读取不得重新挂载已注销页面。
  const epoch = sessionEpoch.value
  const authenticated = await request<Session>('auth/session')
  const [application, clients] = await Promise.all([
    request<Overview>('console/overview'), request<Platform[]>('clients'),
  ])
  if (epoch !== sessionEpoch.value) return
  overview.value = application
  platforms.value = clients
  session.value = authenticated
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
  })
}

onMounted(() => run(refresh, true))
</script>

<template>
  <!-- 2026/11/06：桌面与移动导航共用声明；注销时销毁整个宿主，不跨会话保留页面。 -->
  <ElConfigProvider :locale="zhCn">
    <LoginView v-if="!session" :busy="busy" :error="error" :initial-token="initialToken" @login="signIn" />
    <div v-else class="console-shell">
      <a class="skip-link" href="#main-content">跳到主要内容</a>
      <aside class="sidebar" :class="{'is-collapsed': collapsed}">
        <ConsoleNavigation :page="pages?.current ?? initialPage" :collapsed="collapsed" :version="overview?.version"
          :environment="environment" :disabled="pages?.changing" @select="navigate" />
      </aside>
      <ElDrawer v-model="mobileOpen" direction="ltr" :size="260" :with-header="false" title="控制台导航" class="navigation-drawer">
        <ConsoleNavigation :page="pages?.current ?? initialPage" :version="overview?.version"
          :environment="environment" :disabled="pages?.changing" @select="navigate" />
      </ElDrawer>
      <div class="workspace">
        <header class="topbar">
          <div class="topbar-location">
            <button class="icon-button desktop-menu" :aria-label="collapsed ? '展开侧栏' : '收起侧栏'" :aria-expanded="!collapsed" @click="toggleSidebar"><ConsoleIcon name="menu" /></button>
            <button class="icon-button mobile-menu" aria-label="打开导航" :aria-expanded="mobileOpen" @click="mobileOpen = true"><ConsoleIcon name="menu" /></button>
            <span class="breadcrumb">控制台<span aria-hidden="true">/</span>{{ pages?.title }}</span>
          </div>
          <AppearanceControls show-mode />
        </header>
        <main id="main-content" class="content" tabindex="-1">
          <ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon />
          <ConsolePageHost :key="sessionEpoch" ref="pages" :environment="environment" :initial-page="initialPage" />
        </main>
      </div>
    </div>
  </ElConfigProvider>
</template>
