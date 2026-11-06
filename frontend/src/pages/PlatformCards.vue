<script setup lang="ts">
import {ElButton, ElCard, ElEmpty} from 'element-plus'
import DetailPanel from '../DetailPanel.vue'
import {usePageContext} from './page-contracts'

// 2026/11/06：概览和客户端页复用纯展示卡片，不把令牌读取带入概览。
const {platforms, navigate} = usePageContext()
const date = (value: string) => new Date(value).toLocaleString('zh-CN', {hour12: false})
</script>

<template>
  <div class="platform-grid">
    <ElEmpty v-if="!platforms.length" description="还没有可显示的聊天平台" />
    <ElCard v-for="platform in platforms" :key="platform.clientId" shadow="never" class="platform-card">
      <div class="card-heading"><div class="platform-symbol">{{ platform.name.slice(0, 1) }}</div><h2>{{ platform.name }}</h2>
        <span class="connection-state" :class="{connected: platform.state === 'CONNECTED'}">
          {{ platform.state === 'CONNECTED' ? '已连接' : platform.state === 'AVAILABLE' ? '服务可用' : platform.state === 'DISABLED' ? '未启用' : '未连接' }}
        </span></div>
      <p class="muted">{{ platform.connections }} 个开放连接</p>
      <ElButton v-if="platform.settingsAvailable" type="primary" plain @click="navigate('settings', {kind: 'client', client: platform.clientId})">聊天配置</ElButton>
      <DetailPanel title="连接详情"><p class="small muted">{{ platform.explanation }}</p><span class="small muted">{{ date(platform.observedAt) }}</span></DetailPanel>
    </ElCard>
  </div>
</template>
