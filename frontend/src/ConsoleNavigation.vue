<script setup lang="ts">
import {computed} from 'vue'
import ConsoleIcon from './ConsoleIcon.vue'
import {consolePages} from './pages/page-registry'
import type {ConsolePageKey} from './pages/page-registry'
import type {PageEnvironment} from './pages/page-contracts'

// 2026/11/06：导航仅投影页面声明，标题、分组和可见条件不再维护第二份。
const props = defineProps<{page: ConsolePageKey; collapsed?: boolean; environment: PageEnvironment; disabled?: boolean; version?: string}>()
const emit = defineEmits<{select: [page: ConsolePageKey]}>()
const visible = computed(() => consolePages.filter(page => !page.visible || page.visible(props.environment)))
const groups = computed(() => [...new Set(visible.value.map(page => page.group))]
  .filter(group => group !== 'footer')
  .map(name => ({name, pages: visible.value.filter(page => page.group === name)})))
const footer = computed(() => visible.value.filter(page => page.group === 'footer'))
</script>

<template>
  <div class="navigation-body" :class="{'is-collapsed': collapsed}">
    <div class="brand"><span class="brand-mark">茶</span><span v-if="!collapsed">TeaNeko</span></div>
    <nav aria-label="控制台导航">
      <div v-for="group in groups" :key="group.name" class="nav-group">
        <p v-if="!collapsed" class="nav-group-title">{{ group.name }}</p>
        <template v-for="item in group.pages" :key="item.key">
          <button :class="{active: page === item.key}" :disabled="disabled"
            :aria-current="page === item.key ? 'page' : undefined" :aria-label="item.title"
            :title="collapsed ? item.title : undefined" @click="emit('select', item.key)">
            <ConsoleIcon :name="item.icon" /><span v-if="!collapsed">{{ item.title }}</span>
          </button>
        </template>
      </div>
    </nav>
    <div class="sidebar-footer">
      <button v-for="item in footer" :key="item.key" :class="{active: page === item.key}" :disabled="disabled"
        :aria-current="page === item.key ? 'page' : undefined" :aria-label="item.title" :title="collapsed ? item.title : undefined" @click="emit('select', item.key)">
        <ConsoleIcon :name="item.icon" /><span v-if="!collapsed">{{ item.title }}</span>
      </button>
      <span v-if="!collapsed" class="sidebar-version"><i class="live-dot"></i>{{ version || '读取版本中' }}</span>
    </div>
  </div>
</template>
