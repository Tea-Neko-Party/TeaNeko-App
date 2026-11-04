<script setup lang="ts">
import {useConsolePreferences} from './console-preferences'

// 2026/11/04：详细内容只折叠呈现，切换模式不卸载输入框或清空草稿。
defineProps<{title?: string}>()
const {advanced} = useConsolePreferences()
</script>

<template>
  <details class="detail-panel" :open="advanced">
    <summary>{{ title || '详细信息' }}</summary>
    <div class="detail-content"><slot /></div>
  </details>
</template>
