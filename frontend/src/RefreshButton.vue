<script setup lang="ts">
import {ElButton} from 'element-plus'
import ConsoleIcon from './ConsoleIcon.vue'

// 2026/11/04：刷新操作统一使用图标，完整动作名称保留给悬停提示与读屏。
withDefaults(defineProps<{label?: string; loading?: boolean}>(), {label: '刷新', loading: false})
</script>

<template>
  <!-- 2026/11/04：加载时旋转原矢量图标，避免替换为尺寸不同的框架加载图。 -->
  <ElButton circle class="refresh-button" :aria-busy="loading" :aria-label="label" :title="label" :disabled="loading"><ConsoleIcon name="refresh" :class="{'is-spinning': loading}" /></ElButton>
</template>

<style scoped>
/* 2026/11/04：在组件内声明按钮样式，保留固定点击面积并避免依赖组件库类名。 */
button.refresh-button { width: 36px; height: 36px; flex-shrink: 0; padding: 7px; }
/* 2026/11/04：只调整内部图标，优先于页头的通用图标尺寸。 */
.refresh-button :deep(svg.console-icon) { width: 22px; height: 22px; margin: 0; }
.refresh-button :deep(.is-spinning) { animation: console-spin 1s linear infinite; }
@keyframes console-spin { to { transform: rotate(360deg); } }
</style>
