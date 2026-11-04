<script setup lang="ts">
import {ElButton} from 'element-plus'
import ConsoleIcon from './ConsoleIcon.vue'

// 2026/11/04：刷新操作统一使用图标，完整动作名称保留给悬停提示与读屏。
withDefaults(defineProps<{label?: string; loading?: boolean}>(), {label: '刷新', loading: false})
</script>

<template>
  <ElButton circle class="refresh-button" :loading="loading" :aria-label="label" :title="label"><ConsoleIcon v-if="!loading" name="refresh" /></ElButton>
</template>
