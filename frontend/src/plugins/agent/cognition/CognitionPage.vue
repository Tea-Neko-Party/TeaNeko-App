<script setup lang="ts">
// 2026/11/07：按功能目录引用共享能力与页面契约。
import AgentPage from '../../../features/agent/AgentPage.vue'

// 2026/11/07：认知插件拥有记忆及人格管理入口，共用原目标、草稿和授权流程。
defineProps<{active: boolean}>()
</script>

<template>
  <p class="small muted">统一管理聊天记忆、跨聊天回忆、用户画像与人格。插件 ID：<code>agent.cognition</code></p>
  <AgentPage :active="active" cognition-only />
</template>
