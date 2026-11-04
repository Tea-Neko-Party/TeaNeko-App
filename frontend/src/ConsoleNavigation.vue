<script setup lang="ts">
import ConsoleIcon from './ConsoleIcon.vue'

export type ConsolePage = 'overview' | 'platforms' | 'settings' | 'agent' | 'files' | 'runtime' | 'console' | 'chat' | 'session'

defineProps<{page: ConsolePage; collapsed?: boolean; settingsAvailable: boolean; version?: string}>()
const emit = defineEmits<{select: [page: ConsolePage]}>()
const groups: {name: string; pages: {id: ConsolePage; label: string}[]}[] = [
  {name: '日常', pages: [{id: 'overview', label: '运行概览'}, {id: 'platforms', label: '客户端'}, {id: 'chat', label: '网页聊天'}]},
  {name: '管理', pages: [{id: 'settings', label: '聊天配置'}, {id: 'agent', label: 'Agent 工作区'}, {id: 'files', label: '本地配置'}, {id: 'runtime', label: '任务与日志'}, {id: 'console', label: '指令台'}]},
]
</script>

<template>
  <div class="navigation-body" :class="{'is-collapsed': collapsed}">
    <div class="brand"><span class="brand-mark">茶</span><span v-if="!collapsed">TeaNeko</span></div>
    <nav aria-label="控制台导航">
      <div v-for="group in groups" :key="group.name" class="nav-group">
        <p v-if="!collapsed" class="nav-group-title">{{ group.name }}</p>
        <template v-for="item in group.pages" :key="item.id">
          <button v-if="item.id !== 'settings' || settingsAvailable" :class="{active: page === item.id}"
            :aria-current="page === item.id ? 'page' : undefined" :aria-label="item.label"
            :title="collapsed ? item.label : undefined" @click="emit('select', item.id)">
            <ConsoleIcon :name="item.id" /><span v-if="!collapsed">{{ item.label }}</span>
          </button>
        </template>
      </div>
    </nav>
    <div class="sidebar-footer">
      <button :class="{active: page === 'session'}" aria-label="当前会话" :title="collapsed ? '当前会话' : undefined" @click="emit('select', 'session')">
        <ConsoleIcon name="session" /><span v-if="!collapsed">当前会话</span>
      </button>
      <span v-if="!collapsed" class="sidebar-version"><i class="live-dot"></i>{{ version || '读取版本中' }}</span>
    </div>
  </div>
</template>
