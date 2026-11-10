<script setup lang="ts">
// 2026/11/10：聊天、插件组件与全局模型选择共用表单，凭据只留在所属页面草稿。
import {computed, ref, watch} from 'vue'
import {ElAlert, ElButton, ElInput, ElInputNumber, ElOption, ElSelect, ElSwitch} from 'element-plus'
import ModelNamePicker from './ModelNamePicker.vue'
import ModelIcon from './ModelIcon.vue'
import type {ModelCatalog} from './ModelTemplateEditor.vue'
import ValueEditor from '../../shared/editors/ValueEditor.vue'
import {useConsolePreferences} from '../../shared/state/console-preferences'

export interface ModelSelection {source?: string; route?: string; configuration?: Record<string, unknown>}
const props = withDefaults(defineProps<{modelValue: ModelSelection; catalog: ModelCatalog; routes: Record<string, string>; disabled: boolean; defaultSource?: string}>(), {defaultSource: 'system'})
const emit = defineEmits<{'update:modelValue': [value: ModelSelection]; validity: [value: boolean]}>()
const {advanced} = useConsolePreferences()
const source = computed(() => props.modelValue.source ?? props.defaultSource)
const configuration = computed(() => props.modelValue.configuration ?? {})
const adapter = computed(() => props.catalog.routes.find(item => item.id === configuration.value.adapter))
const secret = computed(() => configuration.value['api-key'])
const kept = computed(() => !!secret.value && typeof secret.value === 'object')
const environment = computed(() => typeof secret.value === 'string' && secret.value.startsWith('${'))
const secretText = computed(() => kept.value ? '' : environment.value ? String(secret.value).slice(2, -1) : String(secret.value ?? ''))
const jsonDraft = ref<string>()
const invalid = ref(false)
watch(() => [source.value, configuration.value.adapter], () => {jsonDraft.value = undefined; invalid.value = false; emit('validity', true)})
function publish(value: ModelSelection) { if (!props.disabled) emit('update:modelValue', value) }
function chooseSource(value: string) {
  publish(value === 'custom' ? {source: value, configuration: {}} : value === 'system' ? {source: value, route: ''} : {source: value})
}
function field(name: string, value: unknown) { publish({...props.modelValue, configuration: {...configuration.value, [name]: value}}) }
function chooseAdapter(id: string) {
  const template = props.catalog.routes.find(item => item.id === id)!
  const value = JSON.parse(JSON.stringify(template.defaults)) as Record<string, unknown>
  delete value.id; delete value.provider
  publish({source: 'custom', configuration: {...value, adapter: id, 'api-key': ''}})
}
function selectModel(value: string) {
  const capacity = adapter.value?.contextWindows?.[value]
  publish({...props.modelValue, configuration: {...configuration.value, model: value, ...(capacity ? {'context-window-tokens': capacity} : {})}})
}
function imageInput(value: string) {
  const next = {...configuration.value}
  if (value === 'auto') delete next['image-input']; else next['image-input'] = value === 'true'
  publish({...props.modelValue, configuration: next})
}
function metadata(value: string) {
  jsonDraft.value = value
  try { field('metadata', JSON.parse(value)); invalid.value = false }
  catch { invalid.value = true }
  emit('validity', !invalid.value)
}
</script>

<template>
  <div class="assembly-parameters model-selection-editor">
    <label>模型来源<ElSelect :model-value="source" :disabled="disabled" @change="value => chooseSource(String(value))">
      <ElOption v-if="defaultSource === 'inherit'" value="inherit" label="跟随 LLM 组件" />
      <ElOption v-if="defaultSource === 'none'" value="none" label="不设置备用模型" />
      <ElOption value="system" label="已记录的模型" /><ElOption value="custom" label="单独填写模型" />
    </ElSelect></label>
    <label v-if="source === 'system'">模型路由<ElSelect :model-value="String(modelValue.route ?? '')" :empty-values="[null, undefined]" filterable :disabled="disabled" @change="value => publish({source: 'system', route: String(value)})"><ElOption v-for="(label, value) in routes" :key="value" :value="value" :label="label" /></ElSelect></label>
    <p v-else-if="source === 'inherit'" class="small muted">使用当前聊天 LLM 组件的完整模型配置；其默认路由改变后自动跟随。</p>
    <template v-else-if="source === 'custom'">
      <p class="small muted">仅保存到当前设置，不修改系统模型目录。密钥不回显。</p>
      <label>协议适配器<ElSelect :model-value="String(configuration.adapter ?? '')" :disabled="disabled" @change="value => chooseAdapter(String(value))"><ElOption v-for="item in catalog.routes" :key="item.id" :value="item.id" :label="item.name"><ModelIcon :icon="item.icon" :name="item.name" /> {{ item.name }}</ElOption></ElSelect></label>
      <template v-if="configuration.adapter">
        <div><label>模型名称</label><ModelNamePicker :model-value="String(configuration.model ?? '')" :names="adapter?.models ?? []" :disabled="disabled" label="私有模型名称" @update:model-value="value => field('model', value)" @select="selectModel" /></div>
        <label>图片输入<ElSelect :model-value="configuration['image-input'] === undefined ? 'auto' : String(configuration['image-input'])" :disabled="disabled" @change="value => imageInput(String(value))"><ElOption value="auto" label="按协议已知型号判断" /><ElOption value="true" label="允许图片输入（需模型支持）" /><ElOption value="false" label="禁止图片输入" /></ElSelect></label>
        <label>服务地址<ElInput :model-value="String(configuration['base-url'] ?? '')" :disabled="disabled" @update:model-value="value => field('base-url', value)" /></label>
        <label>接口路径<ElInput :model-value="String(configuration['api-path'] ?? '')" :disabled="disabled" @update:model-value="value => field('api-path', value)" /></label>
        <div class="assembly-number-row"><label>最大输出 token<ElInputNumber :model-value="Number(configuration['max-tokens'])" :min="1" :disabled="disabled" @change="value => field('max-tokens', value)" /></label><label>上下文总容量<ElInputNumber :model-value="Number(configuration['context-window-tokens'])" :min="1" :disabled="disabled" @change="value => field('context-window-tokens', value)" /></label></div>
        <label>输入上下文占比<ElInputNumber :model-value="Number(configuration['context-input-ratio'] ?? 0.8)" :min="0.01" :max="1" :step="0.05" :disabled="disabled" @change="value => field('context-input-ratio', value)" /></label>
        <div v-if="kept" class="assembly-card-heading"><span>API key 已保存</span><ElButton :disabled="disabled" @click="field('api-key', '')">替换密钥</ElButton></div>
        <template v-else><label>从环境变量读取<ElSwitch :model-value="environment" :disabled="disabled" @change="value => field('api-key', value ? '${}' : '')" /></label><label>{{ environment ? '环境变量名' : 'API key' }}<ElInput :model-value="secretText" :type="environment ? 'text' : 'password'" :disabled="disabled" autocomplete="off" @update:model-value="value => field('api-key', environment ? '${' + value + '}' : value)" /></label></template>
        <template v-if="advanced"><label>扩展 metadata（JSON）<ValueEditor kind="JSON" label="扩展 metadata" :model-value="jsonDraft ?? JSON.stringify(configuration.metadata ?? {}, null, 2)" :disabled="disabled" @update:model-value="metadata" /></label><label>温度<ElInputNumber :model-value="Number(configuration.temperature ?? 1)" :disabled="disabled" :step="0.1" @change="value => field('temperature', value)" /></label><label>思考模式<ElSwitch :model-value="configuration.thinking === true" :disabled="disabled" @change="value => field('thinking', value)" /></label></template>
      </template>
    </template>
    <ElAlert v-if="invalid" title="扩展参数 JSON 格式尚未完成。" type="error" :closable="false" />
  </div>
</template>
