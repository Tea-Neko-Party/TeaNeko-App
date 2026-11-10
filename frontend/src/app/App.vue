<script setup lang="ts">
// 2026/11/07：按功能目录引用共享能力与页面契约。
// 2026/11/06：外壳仅管理认证与布局，页面声明、业务跳转和草稿生命周期交给页面宿主。
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {ElAlert, ElConfigProvider, ElDrawer} from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import LoginView from '../features/auth/LoginView.vue'
import type {Overview, Platform, Session} from '../shared/api/client'
import {ApiError, login, logout, request} from '../shared/api/client'
import {rememberLoginToken, takeLoginToken} from '../features/auth/login-token'
import AppearanceControls from '../shared/components/AppearanceControls.vue'
import ConsoleIcon from '../shared/components/ConsoleIcon.vue'
import ConsoleNavigation from './ConsoleNavigation.vue'
import PluginNavigation from './PluginNavigation.vue'
import {provideConsolePreferences} from '../shared/state/console-preferences'
import ConsolePageHost from './pages/ConsolePageHost.vue'
import type {PageEnvironment} from './pages/page-contracts'
import type {ConsolePageKey} from './pages/page-registry'
import {
  clearPluginCatalog,
  pluginCatalogBusy,
  pluginCatalogPollingPaused,
  pluginEnabled,
  refreshPluginCatalog
} from '../shared/plugins/plugin-console'
import {useActivePolling} from '../shared/lifecycle/active-polling'

const {collapsed, toggleSidebar} = provideConsolePreferences()
const mobileOpen = ref(false)
// 2026/11/10：主导航与插件二级栏共享展开状态，页面跳转仍经过原草稿保护。
const pluginMenuOpen = ref(false)
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
// 2026/11/07：插件撤销后立即隐藏客户端缓存，后续读取仍由服务端检查真实资格。
const availablePlatforms = computed(() => platforms.value.filter(item => pluginEnabled(item.pluginId)))
const busy = ref(false)
const error = ref('')
const initialPage = ref<ConsolePageKey>('overview')
const pages = ref<InstanceType<typeof ConsolePageHost>>()
// 2026/11/10：插件卡片等页面内部跳转也同步右侧导航；单纯收起菜单不改变当前页面。
watch(() => pages.value?.current, key => { pluginMenuOpen.value = !!key?.startsWith('plugin:') })
const sessionEpoch = ref(0)
const environment: PageEnvironment = {
  session, overview, platforms: availablePlatforms, busy, refresh, expired: clearSession, tokensApplied: machineTokensApplied, signOut,
}

async function navigate(key: ConsolePageKey) {
  if (await pages.value?.navigate(key)) {
    mobileOpen.value = false
    pluginMenuOpen.value = key.startsWith('plugin:')
  }
}

function clearSession() {
  sessionEpoch.value++
  clearPluginCatalog()
  session.value = undefined
  overview.value = undefined
  platforms.value = []
  mobileOpen.value = false
  pluginMenuOpen.value = false
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
    refreshPluginCatalog(),
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

// 2026/11/07：被本机授权拒绝后仅在用户重新聚焦时复核一次，成功才恢复定时刷新。
function recheckPluginAccess() {
  if (session.value && !document.hidden && pluginCatalogPollingPaused.value && !pluginCatalogBusy.value) {
    void refreshPluginCatalog().catch(() => clearSession())
  }
}
onMounted(() => { window.addEventListener('focus', recheckPluginAccess); void run(refresh, true) })
onBeforeUnmount(() => window.removeEventListener('focus', recheckPluginAccess))
// 2026/11/07：其他管理会话的启停也会使页面资格失效；后台浏览器停止轮询。
useActivePolling(() => !!session.value && !pluginCatalogPollingPaused.value,
  () => { void refreshPluginCatalog().catch(() => clearSession()) }, 10000)
</script>

<template>
  <!-- 2026/11/06：桌面与移动导航共用声明；注销时销毁整个宿主，不跨会话保留页面。 -->
  <ElConfigProvider :locale="zhCn">
    <LoginView v-if="!session" :busy="busy" :error="error" :initial-token="initialToken" @login="signIn" />
    <div v-else class="console-shell">
      <a class="skip-link" href="#main-content">跳到主要内容</a>
      <aside class="sidebar" :class="{'is-collapsed': collapsed}">
        <ConsoleNavigation :page="pages?.current ?? initialPage" :collapsed="collapsed" :version="overview?.version"
          :environment="environment" :disabled="pages?.changing" :plugin-menu-open="pluginMenuOpen" submenu-id="desktop-plugin-menu"
          @toggle-plugins="pluginMenuOpen = !pluginMenuOpen" @select="navigate" />
      </aside>
      <aside v-if="pluginMenuOpen" id="desktop-plugin-menu" class="plugin-sidebar" aria-label="插件配置菜单">
        <PluginNavigation :page="pages?.current ?? initialPage" :disabled="pages?.changing" @select="navigate" @close="pluginMenuOpen = false" />
      </aside>
      <ElDrawer v-model="mobileOpen" direction="ltr" :size="pluginMenuOpen ? 'min(540px, 96vw)' : '260px'" :with-header="false" title="控制台导航" class="navigation-drawer">
        <div class="mobile-navigation" :class="{'has-plugin-menu': pluginMenuOpen}">
          <ConsoleNavigation :page="pages?.current ?? initialPage" :version="overview?.version"
            :environment="environment" :disabled="pages?.changing" :plugin-menu-open="pluginMenuOpen" submenu-id="mobile-plugin-menu"
            @toggle-plugins="pluginMenuOpen = !pluginMenuOpen" @select="navigate" />
          <PluginNavigation v-if="pluginMenuOpen" id="mobile-plugin-menu" :page="pages?.current ?? initialPage" :disabled="pages?.changing" @select="navigate" @close="pluginMenuOpen = false" />
        </div>
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
