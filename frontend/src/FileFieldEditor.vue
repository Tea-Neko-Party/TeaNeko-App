<script setup lang="ts">
// 2026/11/04：普通与模型表单共用局部字段编辑；原秘密只显示配置状态。
import {computed, ref, watch} from 'vue'
import {ElInput, ElOption, ElSelect} from 'element-plus'

export interface FileField {path: string; label: string; description: string; kind: string; secret: boolean; configured: boolean; source: string; value: unknown}
export interface CredentialInput {id: string; name: string; description: string; prefix: string; suffix: string; pattern: string; secret: boolean; placeholder: string}
const props = defineProps<{field: FileField; draft: {action: string; text: string}; disabled: boolean; advanced: boolean; inputs?: CredentialInput[]; choices?: {value: string; label: string}[]}>()
const emit = defineEmits<{action: [value: string]; text: [value: string]; error: [value: string]}>()
const inputId = ref('manual')
const inputText = ref('')
const credential = computed(() => props.field.path.endsWith('/api-key') && !!props.inputs?.length)
const mode = computed(() => props.inputs?.find(item => item.id === inputId.value) ?? props.inputs?.[0])
const id = computed(() => 'file-' + props.field.path)

function encode(value: string) {
  inputText.value = value
  const selected = mode.value!
  emit('error', value && !new RegExp(selected.pattern).test(value) ? `${props.field.label}：${selected.placeholder}` : '')
  emit('text', value ? selected.prefix + value + selected.suffix : '')
}
function chooseMode(value: string) {
  inputId.value = value
  encode('')
}
watch(() => props.draft.action, action => {
  if (action !== 'REPLACE_SECRET') { inputText.value = ''; emit('error', '') }
})
</script>

<template>
  <div class="settings-field">
    <div><label :for="id">{{ field.label }}</label><span v-if="field.secret" class="field-source"> · {{ field.configured ? '已配置' : '未配置' }}</span><p class="config-description">{{ field.description }}</p><span v-if="advanced" class="field-source">{{ field.path }} · {{ field.source }}</span></div>
    <div class="file-input">
      <ElSelect v-if="advanced || field.secret || !['KEEP', 'SET'].includes(draft.action)" :id="id + '-action'" :model-value="draft.action" :aria-label="`${field.label}修改方式`" :disabled="disabled" @update:model-value="value => emit('action', String(value))"><ElOption label="保持原值" value="KEEP" /><ElOption v-if="!field.secret" label="设置新值" value="SET" /><ElOption v-if="!field.secret" label="移除声明" value="REMOVE" /><ElOption v-if="field.secret" label="替换秘密" value="REPLACE_SECRET" /><ElOption v-if="field.secret" label="清除秘密" value="CLEAR_SECRET" /></ElSelect>
      <template v-if="credential && draft.action === 'REPLACE_SECRET'">
        <ElSelect :model-value="mode?.id" :aria-label="`${field.label}输入方式`" :disabled="disabled" @update:model-value="value => chooseMode(String(value))"><ElOption v-for="input in inputs" :key="input.id" :value="input.id" :label="input.name" /></ElSelect>
        <ElInput :id="id" :model-value="inputText" :type="mode?.secret ? 'password' : 'text'" :placeholder="mode?.placeholder" :aria-label="field.label" :disabled="disabled" autocomplete="off" @update:model-value="encode" /><p class="small muted">{{ mode?.description }}</p>
      </template>
      <ElSelect v-else-if="choices?.length && !field.secret" :id="id" filterable :model-value="draft.text" :disabled="disabled" :aria-label="field.label" @update:model-value="value => emit('text', String(value))"><ElOption v-for="choice in choices" :key="choice.value" :value="choice.value" :label="choice.label" /></ElSelect>
      <ElInput v-else-if="!field.secret || draft.action === 'REPLACE_SECRET'" :id="id" :model-value="draft.text" :type="field.kind === 'JSON' ? 'textarea' : field.path.endsWith('/api-key') ? 'password' : 'text'" :rows="3" :disabled="disabled || !['KEEP', 'SET', 'REPLACE_SECRET'].includes(draft.action)" :placeholder="field.secret ? '仅填写本次新值，原值不会回显' : '支持环境占位符'" :aria-label="field.label" autocomplete="off" @update:model-value="value => emit('text', value)" />
    </div>
  </div>
</template>
