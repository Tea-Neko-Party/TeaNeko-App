<script setup lang="ts">
// 2026/11/10：插件归属统一优先展示名称，稳定 ID 保留在悬停说明及筛选中。
import PluginLabel from '../../shared/plugins/PluginLabel.vue'
// 2026/11/07：按功能目录引用共享能力与页面契约。
import {computed, ref} from 'vue'
import {ElButton, ElCard, ElEmpty, ElTag} from 'element-plus'
import PluginFilter from '../../shared/plugins/PluginFilter.vue'
import DetailPanel from '../../shared/components/DetailPanel.vue'
import {usePageContext} from '../../app/pages/page-contracts'

// 2026/11/06：概览和客户端页复用纯展示卡片，不把令牌读取带入概览。
const {platforms, navigate} = usePageContext()
// 2026/11/07：官方与扩展客户端共同展示，插件贡献可按真实归属筛选。
const owner = ref('')
const matching = computed(() => platforms.value.filter(item => !owner.value || item.pluginId === owner.value))
const date = (value: string) => new Date(value).toLocaleString('zh-CN', {hour12: false})
</script>

<template>
  <PluginFilter v-if="platforms.some(item => item.pluginId)" v-model="owner" :items="platforms" />
  <div class="platform-grid">
    <ElEmpty v-if="!matching.length" description="没有匹配的聊天平台" />
    <ElCard v-for="platform in matching" :key="platform.clientId" shadow="never" class="platform-card">
      <div class="card-heading"><div class="platform-symbol">{{ platform.name.slice(0, 1) }}</div><h2>{{ platform.name }}</h2>
        <span class="connection-state" :class="{connected: platform.state === 'CONNECTED'}">
          {{ platform.state === 'CONNECTED' ? '已连接' : platform.state === 'AVAILABLE' ? '服务可用' : platform.state === 'DISABLED' ? '未启用' : '未连接' }}
        </span></div>
      <ElTag v-if="platform.pluginId" type="info"><PluginLabel :plugin-id="platform.pluginId" /></ElTag>
      <p class="muted">{{ platform.connections }} 个开放连接</p>
      <ElButton v-if="platform.settingsAvailable" type="primary" plain @click="navigate('settings', {kind: 'client', client: platform.clientId})">聊天配置</ElButton>
      <DetailPanel title="连接详情"><p class="small muted">{{ platform.explanation }}</p><span class="small muted">{{ date(platform.observedAt) }}</span></DetailPanel>
    </ElCard>
  </div>
</template>
