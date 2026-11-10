<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {ElOption, ElSelect} from 'element-plus'
import {pluginAvailability, pluginEnabled, pluginName} from './plugin-console'

// 2026/11/07：筛选真实目录中的插件归属，不从组件名称猜测插件。
const props = defineProps<{modelValue: string; items: readonly {pluginId?: string | null}[]}>()
const emit = defineEmits<{'update:modelValue': [value: string]}>()
// 2026/11/10：按名称排序，搜索同时接受名称和 ID，所选值仍是稳定 ID。
const query = ref('')
const owners = computed(() => [...new Set(props.items.map(item => item.pluginId).filter((id): id is string => !!id && pluginEnabled(id)))]
  .filter(id => `${pluginName(id)} ${id}`.toLocaleLowerCase().includes(query.value.trim().toLocaleLowerCase()))
  .sort((left, right) => pluginName(left).localeCompare(pluginName(right), 'zh-CN') || left.localeCompare(right)))
// 2026/11/07：资格撤销同步清除所选插件，避免选择器继续显示已关闭功能的标识。
watch(pluginAvailability, () => {
  if (props.modelValue && !pluginEnabled(props.modelValue)) emit('update:modelValue', '')
}, {flush: 'sync'})
</script>

<template>
  <ElSelect :model-value="modelValue" clearable filterable placeholder="全部插件" aria-label="按插件名称或 ID 筛选"
    :filter-method="value => query = value" @visible-change="() => query = ''"
    @update:model-value="value => emit('update:modelValue', value || '')">
    <ElOption v-for="id in owners" :key="id" :label="pluginName(id)" :value="id">
      <span>{{ pluginName(id) }}</span><small class="plugin-filter-id">{{ id }}</small>
    </ElOption>
  </ElSelect>
</template>

<style scoped>
.plugin-filter-id {margin-left:12px; color:var(--muted)}
</style>
