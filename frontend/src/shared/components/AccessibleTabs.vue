<script setup lang="ts">
// 2026/11/06：页签采用手动激活；移动焦点不触发请求，提交切换仍由页面草稿守卫决定。
import {computed, nextTick, ref, useId, watch} from 'vue'

const props = defineProps<{modelValue: string; items: {id: string; name: string}[]; label: string; disabled?: boolean}>()
const emit = defineEmits<{'update:modelValue': [value: string]}>()
const base = useId()
const list = ref<HTMLElement>()
const focused = ref(props.modelValue)
const tabId = (id: string) => `${base}-tab-${id}`
const panelId = computed(() => `${base}-panel-${props.modelValue}`)
watch(() => props.modelValue, value => { focused.value = value })

function move(event: KeyboardEvent, id: string) {
  const index = props.items.findIndex(item => item.id === id)
  let target: number
  if (event.key === 'ArrowRight') target = (index + 1) % props.items.length
  else if (event.key === 'ArrowLeft') target = (index - 1 + props.items.length) % props.items.length
  else if (event.key === 'Home') target = 0
  else if (event.key === 'End') target = props.items.length - 1
  else return
  event.preventDefault()
  focused.value = props.items[target]!.id
  void nextTick(() => list.value?.querySelector<HTMLButtonElement>(`#${CSS.escape(tabId(focused.value))}`)?.focus())
}
</script>

<template>
  <div ref="list" class="agent-tabs" role="tablist" :aria-label="label">
    <button v-for="item in items" :id="tabId(item.id)" :key="item.id" type="button" role="tab"
      :tabindex="focused === item.id ? 0 : -1" :aria-selected="modelValue === item.id"
      :aria-controls="`${base}-panel-${item.id}`" :class="{selected: modelValue === item.id}"
      :disabled="disabled" @keydown="move($event, item.id)" @focus="focused = item.id"
      @click="emit('update:modelValue', item.id)">{{ item.name }}</button>
  </div>
  <section :id="panelId" role="tabpanel" :aria-labelledby="tabId(modelValue)" tabindex="0">
    <slot />
  </section>
  <section v-for="item in items.filter(item => item.id !== modelValue)" :id="`${base}-panel-${item.id}`"
    :key="item.id" role="tabpanel" :aria-labelledby="tabId(item.id)" hidden />
</template>
