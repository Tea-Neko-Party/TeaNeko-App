<script setup lang="ts">
// 2026/11/07：按功能目录引用共享能力与页面契约。
import {ref, watch} from 'vue'
import ChatTargetPicker from '../chat/ChatTargetPicker.vue'
import CommandConsoleView from './CommandConsoleView.vue'
import type {AgentTarget} from '../agent/AgentWorkspaceView.vue'
import type {PageIntent} from '../../app/pages/page-contracts'
import {usePageContext} from '../../app/pages/page-contracts'

defineProps<{active: boolean}>()
defineOptions({inheritAttrs: false})
const {platforms, expired} = usePageContext()
const target = ref<AgentTarget>()
const pickerOpen = ref(false)
const view = ref<InstanceType<typeof CommandConsoleView>>()
// 2026/11/07：客户端所属插件撤销后销毁已捕获目标与编辑器。
watch(platforms, () => {
  if (target.value && !platforms.value.some(item => item.clientId === target.value!.client)) { target.value = undefined; pickerOpen.value = false }
})
/** 2026/11/06：更换真实目标仍使用原确认，不因页面注册而丢失未决指令。 */
async function select(next: AgentTarget): Promise<boolean> {
  const same = target.value?.client === next.client && target.value.account === next.account && target.value.target === next.target
  if (!same && view.value && !await view.value.canLeave()) return false
  if (!same) target.value = next
  pickerOpen.value = false
  return true
}
async function open(intent: PageIntent): Promise<boolean> {
  if (intent.kind !== 'target') throw new Error('指令台需要指定聊天目标。')
  return select(intent.target)
}
defineExpose({open})
</script>

<template>
  <ChatTargetPicker v-show="!target || pickerOpen" title="指令台" :clients="platforms" :target="target" @expired="expired" @select="select" />
  <CommandConsoleView v-if="target" ref="view" v-show="!pickerOpen" :target="target" :active="active && !pickerOpen"
    @expired="expired" @select-target="pickerOpen = true" />
</template>
