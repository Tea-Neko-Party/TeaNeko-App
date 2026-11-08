<script setup lang="ts">
import type {Component} from 'vue'
// 2026/11/07：按功能目录引用共享能力与页面契约。
import {computed, onMounted, useTemplateRef} from 'vue'
import type {ConsolePage} from './page-registry'
import type {PageHandle, PageIntent, PageLeave, PageOutletHandle} from './page-contracts'
import {usePageContext} from './page-contracts'
import {providePageGuard} from '../../shared/lifecycle/page-guards'

// 2026/11/06：每个页面单独持有组件引用和草稿保护，避免同一组件的多个页面共享实例。
const props = defineProps<{page: ConsolePage; component: Component; active: boolean}>()
const emit = defineEmits<{ready: [handle: PageOutletHandle]}>()
const context = usePageContext()
const instance = useTemplateRef<PageHandle>('instance')
const guard = providePageGuard(props.page.title)
const headingHidden = computed(() => instance.value?.headingHidden ?? false)
const hasRefresh = computed(() => !!instance.value?.refresh)
async function open(intent: PageIntent): Promise<boolean> {
  if (!instance.value?.open) throw new Error('该页面不接受此导航操作。')
  return instance.value.open(intent)
}
async function canLeave(leave: PageLeave): Promise<boolean> {
  return await instance.value?.canLeave?.(leave) !== false && await guard(leave)
}
async function refresh() {
  await instance.value?.refresh?.()
}
onMounted(() => emit('ready', {open, canLeave, refresh, headingHidden, hasRefresh}))
</script>

<template>
  <section :data-console-page="page.key" :aria-label="page.title">
    <component :is="component" ref="instance" v-bind="page.props?.(context, active)" @expired="context.expired" />
  </section>
</template>
