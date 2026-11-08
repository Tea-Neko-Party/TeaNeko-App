<script setup lang="ts">
// 2026/11/07：按功能目录引用共享能力与页面契约。
import {useConsolePreferences} from '../state/console-preferences'

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
