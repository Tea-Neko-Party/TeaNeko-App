<script setup lang="ts">
// 2026/11/06：为字段分配实例唯一标识；关联实际输入节点，兼容未透传 ARIA 的组件库控件。
import {computed, onMounted, onUpdated, ref, useId} from 'vue'

const props = defineProps<{label: string; description?: string; error?: string}>()
const id = `field-${useId()}`
const root = ref<HTMLElement>()
const describedBy = computed(() => [props.description ? `${id}-description` : '', props.error ? `${id}-error` : ''].filter(Boolean).join(' '))
const attributes = computed(() => ({id, 'aria-label': props.label, 'aria-describedby': describedBy.value, 'aria-invalid': !!props.error}))

/** 组件库的根容器不一定是可聚焦控件，关联必须落到字段值所属的原生节点。 */
function associate() {
  const control = root.value?.querySelector<HTMLElement>('input:not([type="hidden"]), textarea, [role="combobox"], [contenteditable="true"]')
    ?? root.value?.querySelector<HTMLElement>('button')
  if (!control) return
  // 2026/11/07：组件根容器可能也接收到 id；只保留实际输入节点的标识，避免重复关联。
  root.value?.querySelectorAll<HTMLElement>('[id]').forEach(node => {
    if (node !== control && node.id === id) node.removeAttribute('id')
  })
  control.id = id
  control.setAttribute('aria-label', props.label)
  control.setAttribute('aria-describedby', describedBy.value)
  control.setAttribute('aria-invalid', String(!!props.error))
}
onMounted(associate)
onUpdated(associate)
</script>

<template>
  <div ref="root" class="assembly-parameter">
    <label :for="id">{{ label }}</label>
    <p v-if="description" :id="`${id}-description`" class="config-description">{{ description }}</p>
    <slot :attributes="attributes" />
    <p v-if="error" :id="`${id}-error`" role="alert">{{ error }}</p>
  </div>
</template>
