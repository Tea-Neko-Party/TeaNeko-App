<script setup lang="ts">
import {ElButton, ElCard} from 'element-plus'
import {usePageContext} from './page-contracts'

// 2026/11/06：退出经过宿主的全页草稿检查，强制失效仍直接销毁会话页面。
const {session, busy, signOut} = usePageContext()
defineOptions({inheritAttrs: false})
const date = (value: string) => new Date(value).toLocaleString('zh-CN', {hour12: false})
</script>

<template>
  <ElCard v-if="session" shadow="never" class="session-card">
    <h2>{{ session.name }}</h2><dl><dt>登录时间</dt><dd>{{ date(session.authenticatedAt) }}</dd>
      <dt>最长有效至</dt><dd>{{ date(session.expiresAt) }}</dd></dl>
    <p class="muted">长时间没有访问、服务器重启或凭据变更后，需要重新登录。</p>
    <ElButton @click="signOut" :disabled="busy">退出当前会话</ElButton>
  </ElCard>
</template>
