<script setup lang="ts">
import {pluginName} from './plugin-console'

// 2026/11/10：归属优先显示可读名称，悬停仍能查看用于定位的稳定 ID。
defineProps<{pluginId?: string | null}>()
</script>

<template>
  <span class="plugin-label" :title="pluginId ? `插件 ID：${pluginId}` : '系统原生功能'">{{ pluginName(pluginId) }}</span>
</template>

<style scoped>
.plugin-label {font-size:12px; color:var(--muted); overflow-wrap:anywhere}
</style>
