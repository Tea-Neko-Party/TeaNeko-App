<script setup lang="ts">
import MachineTokensView from '../MachineTokensView.vue'
import PlatformCards from './PlatformCards.vue'
import {usePageContext} from './page-contracts'

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
