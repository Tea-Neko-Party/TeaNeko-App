<script setup lang="ts">
import {computed, watch} from 'vue'
import {ElOption, ElSelect} from 'element-plus'
import {pluginAvailability, pluginEnabled} from './plugin-console'

// 2026/11/07：筛选真实目录中的插件归属，不从组件名称猜测插件。
const props = defineProps<{modelValue: string; items: readonly {pluginId?: string | null}[]}>()
const emit = defineEmits<{'update:modelValue': [value: string]}>()
const owners = computed(() => [...new Set(props.items.map(item => item.pluginId).filter((id): id is string => !!id && pluginEnabled(id)))].sort())
// 2026/11/07：资格撤销同步清除所选插件，避免选择器继续显示已关闭功能的标识。
watch(pluginAvailability, () => {
  if (props.modelValue && !pluginEnabled(props.modelValue)) emit('update:modelValue', '')
}, {flush: 'sync'})
</script>

<template>
  <ElSelect :model-value="modelValue" clearable filterable placeholder="全部插件" aria-label="按插件 ID 筛选"
    @update:model-value="value => emit('update:modelValue', value || '')">
    <ElOption v-for="id in owners" :key="id" :label="id" :value="id" />
  </ElSelect>
</template>
