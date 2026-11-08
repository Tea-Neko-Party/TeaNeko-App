<script setup lang="ts">
// 2026/11/07：按功能目录引用共享能力与页面契约。
import {ref} from 'vue'
import ChatSettingsView from './ChatSettingsView.vue'
import {usePageContext} from '../../../app/pages/page-contracts'
import type {PageIntent} from '../../../app/pages/page-contracts'

// 2026/11/06：客户端/目标导航归设置页处理，不再要求外壳认识业务组件及其挂载时间。
const {platforms, expired, navigate} = usePageContext()
defineOptions({inheritAttrs: false})
const view = ref<InstanceType<typeof ChatSettingsView>>()
async function open(intent: PageIntent): Promise<boolean> {
  if (!view.value) return false
  if (intent.kind === 'target') await view.value.openTarget(intent.target)
  else if (intent.kind === 'client') await view.value.openClient(intent.client)
  else await view.value.openPlugin(intent.pluginId, intent.section)
  return true
}
defineExpose({open})
</script>

<template>
  <ChatSettingsView ref="view" :clients="platforms" initial-client="" @expired="expired"
    @inspect="target => navigate('agent', {kind: 'target', target})"
    @console="target => navigate('console', {kind: 'target', target})" />
</template>
