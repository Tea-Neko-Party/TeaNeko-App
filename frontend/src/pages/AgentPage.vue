<script setup lang="ts">
import {nextTick, ref} from 'vue'
import AgentWorkspaceView from '../AgentWorkspaceView.vue'
import type {AgentTarget} from '../AgentWorkspaceView.vue'
import ChatTargetPicker from '../ChatTargetPicker.vue'
import AgentLibraryPicker from '../AgentLibraryPicker.vue'
import type {LibraryItem} from '../AgentLibraryPicker.vue'
import {usePageContext} from './page-contracts'
import type {PageIntent} from './page-contracts'

// 2026/11/06：工作区独立持有模式、目录与目标；切换真实编辑目标前复用原草稿保护。
defineProps<{active: boolean}>()
defineOptions({inheritAttrs: false})
const {platforms, expired} = usePageContext()
const mode = ref<'chats' | 'memory' | 'personality'>('chats')
const target = ref<AgentTarget>()
const library = ref<LibraryItem>()
const pickerOpen = ref(false)
const picker = ref<InstanceType<typeof ChatTargetPicker>>()
const view = ref<InstanceType<typeof AgentWorkspaceView>>()
async function selectTarget(next: AgentTarget): Promise<boolean> {
  const same = target.value?.client === next.client && target.value.account === next.account && target.value.target === next.target
  if ((mode.value !== 'chats' || !same) && view.value && !await view.value.canLeave()) return false
  mode.value = 'chats'
  if (!same) target.value = next
  pickerOpen.value = false
  return true
}
async function chooseMode(next: typeof mode.value) {
  if (next === mode.value || view.value && !await view.value.canLeave()) return
  mode.value = next
  library.value = undefined
}
async function selectLibrary(next?: LibraryItem) {
  if (view.value && !await view.value.canLeave()) return
  library.value = next
}
async function showPicker() {
  pickerOpen.value = true
  await nextTick()
  picker.value?.focus()
}
async function open(intent: PageIntent): Promise<boolean> {
  if (intent.kind !== 'target') throw new Error('聊天工作区需要指定聊天目标。')
  return selectTarget(intent.target)
}
defineExpose({open})
</script>

<template>
  <div class="agent-tabs">
    <button :class="{selected: mode === 'chats'}" @click="chooseMode('chats')">聊天工作区</button>
    <button :class="{selected: mode === 'memory'}" @click="chooseMode('memory')">记忆空间</button>
    <button :class="{selected: mode === 'personality'}" @click="chooseMode('personality')">人格空间</button>
  </div>
  <AgentLibraryPicker v-if="mode !== 'chats' && !library" :kind="mode" @expired="expired" @select="selectLibrary" />
  <AgentWorkspaceView v-if="mode !== 'chats' && library" :key="mode + library.id" ref="view"
    :library="{kind: mode, key: library.id, name: library.name}" :active="active" @expired="expired" @select-target="selectLibrary()" />
  <ChatTargetPicker v-show="mode === 'chats' && (!target || pickerOpen)" ref="picker" :clients="platforms" :target="target" @expired="expired" @select="selectTarget" />
  <AgentWorkspaceView v-if="mode === 'chats' && target" ref="view" v-show="!pickerOpen" :target="target" :active="active && !pickerOpen"
    @expired="expired" @select-target="showPicker" />
</template>
