<script setup lang="ts">
import {computed, ref} from 'vue'
import {ElEmpty, ElInput} from 'element-plus'
import {consolePages} from './pages/page-registry'
import {pluginName} from '../shared/plugins/plugin-console'
import ConsoleIcon from '../shared/components/ConsoleIcon.vue'

// 2026/11/10：右侧二级栏只投影已发布的插件页面，不在宿主枚举具体插件。
defineProps<{page: string; disabled?: boolean}>()
const emit = defineEmits<{select: [page: string]; close: []}>()
const search = ref('')
const groups = computed(() => {
  const pages = consolePages.value.filter(page => page.group === '插件配置' && page.pluginId
    && `${pluginName(page.pluginId)} ${page.pluginId} ${page.title}`.toLocaleLowerCase().includes(search.value.trim().toLocaleLowerCase()))
  return [...new Set(pages.map(page => page.pluginId!))].map(id => ({id, name: pluginName(id), pages: pages.filter(page => page.pluginId === id)}))
})
</script>

<template>
  <div class="plugin-navigation">
    <header><h2>插件配置</h2><button class="icon-button" aria-label="收起插件配置菜单" @click="emit('close')"><ConsoleIcon name="close" /></button></header>
    <ElInput v-model="search" clearable placeholder="名称或 ID" aria-label="筛选插件配置菜单" />
    <nav aria-label="插件配置二级导航">
      <section v-for="group in groups" :key="group.id">
        <h3 :title="group.id">{{ group.name }}</h3>
        <button v-for="item in group.pages" :key="item.key" :disabled="disabled" :class="{active: page === item.key}"
          :aria-current="page === item.key ? 'page' : undefined" @click="emit('select', item.key)">{{ item.title }}</button>
      </section>
      <ElEmpty v-if="!groups.length" description="没有匹配的插件页面" :image-size="48" />
    </nav>
  </div>
</template>

<style scoped>
.plugin-navigation {display:flex; flex-direction:column; gap:18px; height:100%; padding:24px 16px; overflow-y:auto}
header {display:flex; justify-content:space-between; align-items:center; gap:8px}
h2 {font-size:16px; margin:0}
nav section + section {border-top:1px solid var(--line); padding-top:16px; margin-top:16px}
h3 {font-size:12px; color:var(--muted); margin:0 8px 8px; overflow-wrap:anywhere}
nav button {display:block; width:100%; padding:11px 12px; border:0; border-radius:8px; text-align:left; color:var(--ink); background:transparent; overflow-wrap:anywhere}
nav button:hover {background:var(--canvas)}
nav button.active {color:var(--accent); background:var(--accent-soft); font-weight:600}
</style>
