<script setup lang="ts">
// 2026/11/07：按功能目录引用共享能力与页面契约。
import MachineTokensView from './MachineTokensView.vue'
import PlatformCards from './PlatformCards.vue'
import {usePageContext} from '../../app/pages/page-contracts'

// 2026/11/06：令牌页首次访问才读取，隐藏时收起明文但不丢弃编辑状态。
defineProps<{active: boolean}>()
defineOptions({inheritAttrs: false})
const context = usePageContext()
defineExpose({refresh: context.refresh})
</script>

<template>
  <PlatformCards />
  <MachineTokensView :active="active" @expired="context.expired" @applied="context.tokensApplied" />
</template>
