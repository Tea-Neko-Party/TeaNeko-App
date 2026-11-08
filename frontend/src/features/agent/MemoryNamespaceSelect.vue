<script setup lang="ts">
// 2026/11/06：仅选择已存空间；创建与绑定仍走专门管理动作，不把任意文本当成存在的空间。
import {ElAlert, ElOption, ElSelect} from 'element-plus'
import {computed} from 'vue'
import {useAgentLibrary} from './agent-library'

const props = withDefaults(defineProps<{modelValue: string; disabled?: boolean; allowCurrent?: boolean}>(), {allowCurrent: true})
const emit = defineEmits<{'update:modelValue': [value: string]; expired: []}>()
const {items, busy, error, load} = useAgentLibrary(() => ({kind: 'memory'}), () => emit('expired'))
const missing = computed(() => !!props.modelValue && !items.value.some(item => item.id === props.modelValue))
</script>

<template>
  <div class="namespace-select">
    <ElSelect :model-value="modelValue" :disabled="disabled" :loading="busy" filterable placeholder="选择已存记忆空间" aria-label="记忆空间" @update:model-value="value => emit('update:modelValue', String(value))">
      <ElOption v-if="allowCurrent" value="" label="当前绑定空间" />
      <ElOption v-if="missing" :value="modelValue" :label="modelValue + (busy ? '（正在读取）' : '（目录中未找到）')" disabled />
      <ElOption v-for="item in items" :key="item.id" :value="item.id" :label="item.name" />
    </ElSelect>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <button v-if="error" class="text-button" :disabled="busy || disabled" @click="load">重试读取空间</button>
  </div>
</template>
