<script setup lang="ts">
import {ref} from 'vue'
import ChatTargetPicker from '../ChatTargetPicker.vue'
import CommandConsoleView from '../CommandConsoleView.vue'
import type {AgentTarget} from '../AgentWorkspaceView.vue'
import {usePageContext} from './page-contracts'
import type {PageIntent} from './page-contracts'

defineProps<{active: boolean}>()
defineOptions({inheritAttrs: false})
const {platforms, expired} = usePageContext()
const target = ref<AgentTarget>()
const pickerOpen = ref(false)
const view = ref<InstanceType<typeof CommandConsoleView>>()
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
