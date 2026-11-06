<script setup lang="ts">
import {computed, ref} from 'vue'
import {ElCard} from 'element-plus'
import ConsoleIcon from '../ConsoleIcon.vue'
import MachineStatusView from '../MachineStatusView.vue'
import PlatformCards from './PlatformCards.vue'
import {usePageContext} from './page-contracts'

// 2026/11/06：概览没有编辑草稿，离开即卸载机器采样，手动刷新仍复用原读取。
const context = usePageContext()
defineOptions({inheritAttrs: false})
const {platforms, overview} = context
const machine = ref<InstanceType<typeof MachineStatusView>>()
const connections = computed(() => platforms.value.reduce((total, item) => total + item.connections, 0))
const date = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', {hour12: false}) : '尚未更新'
async function refresh() {
  await context.refresh()
  await machine.value?.refresh()
}
defineExpose({refresh})
</script>

<template>
  <div class="stats">
    <ElCard shadow="never"><div class="stat-label"><span>客户端</span><ConsoleIcon name="platforms" /></div><strong>{{ platforms.length }}</strong></ElCard>
    <ElCard shadow="never"><div class="stat-label"><span>开放连接</span><span class="live-dot"></span></div><strong>{{ connections }}</strong></ElCard>
    <ElCard shadow="never"><div class="stat-label"><span>应用版本</span><ConsoleIcon name="overview" /></div><strong class="version">{{ overview?.version ?? '读取中' }}</strong></ElCard>
  </div>
  <MachineStatusView ref="machine" @expired="context.expired" />
  <div class="section-title"><h2>客户端</h2><span class="small muted">{{ date(overview?.observedAt) }}</span></div>
  <PlatformCards />
</template>
