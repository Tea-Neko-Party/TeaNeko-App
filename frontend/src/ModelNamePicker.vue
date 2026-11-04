<script setup lang="ts">
// 2026/11/04：默认使用维护的模型列表，手动开关保留旧名称及自定义模型。
import {ref, watch} from 'vue'
import {ElInput, ElOption, ElSelect, ElSwitch} from 'element-plus'

const props = defineProps<{modelValue: string; names: string[]; disabled?: boolean; label: string}>()
const emit = defineEmits<{'update:modelValue': [value: string]; select: [value: string]}>()
const manual = ref(!props.names.length || !!props.modelValue && !props.names.includes(props.modelValue))
watch(() => props.names.join(','), () => { manual.value = !props.names.length || !!props.modelValue && !props.names.includes(props.modelValue) })
function select(value: string) { emit('update:modelValue', value); emit('select', value) }
</script>

<template>
  <div class="model-name-picker"><label class="small muted">手动输入 <ElSwitch v-model="manual" :disabled="disabled" :aria-label="label + '手动输入'" /></label>
    <ElInput v-if="manual" :model-value="modelValue" :disabled="disabled" :aria-label="label" @update:model-value="value => emit('update:modelValue', value)" />
    <ElSelect v-else filterable :model-value="modelValue" :disabled="disabled" :aria-label="label" @update:model-value="value => select(String(value))"><ElOption v-for="name in names" :key="name" :label="name" :value="name" /><ElOption v-if="modelValue && !names.includes(modelValue)" :label="modelValue + '（当前值）'" :value="modelValue" /></ElSelect>
  </div>
</template>
