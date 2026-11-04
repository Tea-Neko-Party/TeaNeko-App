<script setup lang="ts">
// 2026/11/04：组件目录驱动装配表单，所有更改仅进入父页面草稿，由原配置事务提交。
import {computed, onMounted, ref} from 'vue'
import {ElAlert, ElButton, ElDialog, ElInput, ElInputNumber, ElOption, ElSelect, ElSwitch, ElTag} from 'element-plus'
import {ApiError, request} from './api'
import ModelNamePicker from './ModelNamePicker.vue'
import ModelIcon from './ModelIcon.vue'
import type {ModelCatalog} from './ModelTemplateEditor.vue'
import {useConsolePreferences} from './console-preferences'

interface Field {name: string; label: string; description: string; kind: string}
interface Component {id: string; name: string; description: string; version: number; requires: string[]; defaults: Record<string, unknown>; fields: Field[]}
interface Catalog {components: Component[]; models: ModelCatalog; systemRoutes: Record<string, string>; modelError: string}
interface Assembly {version: number; components: Record<string, Record<string, unknown>>}
const props = defineProps<{modelValue: string; disabled: boolean}>()
const emit = defineEmits<{'update:modelValue': [value: string]; expired: []; validity: [valid: boolean]}>()
const {advanced} = useConsolePreferences()
const catalog = ref<Catalog>()
const selected = ref('')
const error = ref('')
const editingJSON = ref<Record<string, string>>({})
const invalidJSON = ref(new Set<string>())
const assembly = computed<Assembly>(() => JSON.parse(props.modelValue))
const current = computed(() => catalog.value?.components.find(item => item.id === selected.value))
const parameters = computed(() => assembly.value.components[selected.value] ?? {})
const llm = computed(() => assembly.value.components.llm!)
const privateModel = computed(() => (llm.value.configuration ?? {}) as Record<string, unknown>)
const adapter = computed(() => catalog.value?.models.routes.find(item => item.id === privateModel.value.adapter))
const secret = computed(() => privateModel.value['api-key'])
const keepSecret = computed(() => typeof secret.value === 'object' && secret.value !== null)
const environment = computed(() => typeof secret.value === 'string' && secret.value.startsWith('${'))
const secretText = computed(() => keepSecret.value ? '' : environment.value ? String(secret.value).slice(2, -1) : String(secret.value ?? ''))
// 2026/11/04：高级预览也不回显本次新输入的秘密。
const safePreview = computed(() => {
  const value = structuredClone(assembly.value)
  for (const item of catalog.value?.components ?? []) {
    const parameters = value.components[item.id]
    if (!parameters) continue
    for (const field of item.fields) if (field.kind === 'SECRET' && field.name in parameters) parameters[field.name] = '（已隐藏）'
  }
  const configuration = value.components.llm?.configuration as Record<string, unknown> | undefined
  if (configuration && 'api-key' in configuration) configuration['api-key'] = '（已隐藏）'
  return JSON.stringify(value, null, 2)
})

async function load() {
  try { catalog.value = await request<Catalog>('agent/catalog/components') }
  catch (failure) {
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
    error.value = failure instanceof Error ? failure.message : '无法读取组件目录。'
  }
}
onMounted(load)

function publish(value: Assembly) {
  if (props.disabled) return
  emit('update:modelValue', JSON.stringify(value))
}
function toggle(id: string, installed: boolean) {
  error.value = ''
  const value = structuredClone(assembly.value)
  if (installed) {
    const add = (key: string) => {
      if (value.components[key]) return
      const item = catalog.value!.components.find(item => item.id === key)
      if (!item) throw new Error('缺少依赖组件：' + key)
      // 2026/11/04：目录来自 Vue 响应式代理，按 JSON 协议复制，不能直接 structuredClone 代理。
      value.components[key] = JSON.parse(JSON.stringify(item.defaults))
      item.requires.forEach(add)
    }
    try { add(id) } catch (failure) { error.value = String(failure); return }
  } else {
    const dependent = catalog.value!.components.find(item => value.components[item.id] && item.requires.includes(id))
    if (dependent) { error.value = `请先移除「${dependent.name}」。`; return }
    delete value.components[id]
  }
  publish(value)
}
function parameter(name: string, value: unknown) {
  const document = structuredClone(assembly.value)
  document.components[selected.value]![name] = value
  publish(document)
}
function source(value: string) {
  const document = structuredClone(assembly.value)
  document.components.llm = value === 'system' ? {source: 'system', route: ''} : {source: 'custom', configuration: {}}
  publish(document)
}
function modelField(name: string, value: unknown) {
  parameter('configuration', {...privateModel.value, [name]: value})
}
function selectModel(value: string) {
  // 2026/11/04：型号和容量在同一草稿更新中提交，避免同帧双事件覆盖刚选中的型号。
  const capacity = adapter.value?.contextWindows?.[value]
  parameter('configuration', {...privateModel.value, model: value,
    ...(capacity ? {'context-window-tokens': capacity} : {})})
}
function chooseAdapter(id: string) {
  const template = catalog.value!.models.routes.find(item => item.id === id)!
  const configuration = JSON.parse(JSON.stringify(template.defaults)) as Record<string, unknown>
  delete configuration.id
  delete configuration.provider
  parameter('configuration', {...configuration, adapter: id, 'api-key': ''})
}
function jsonField(name: string, text: string, model = false) {
  editingJSON.value[name] = text
  try {
    const value: unknown = JSON.parse(text)
    if (model) modelField(name, value); else parameter(name, value)
    invalidJSON.value.delete(name)
  } catch {
    invalidJSON.value.add(name)
  }
  error.value = invalidJSON.value.size ? 'JSON 格式尚未完成，请修正后保存。' : ''
  emit('validity', invalidJSON.value.size === 0)
}
function open(id: string) {
  selected.value = id
  editingJSON.value = {}
  invalidJSON.value.clear()
  error.value = ''
  emit('validity', true)
}
</script>

<template>
  <div class="agent-assembly-editor">
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <div v-if="catalog" class="assembly-grid">
      <article v-for="item in catalog.components" :key="item.id" class="assembly-card" :class="{'is-installed': !!assembly.components[item.id]}">
        <div class="assembly-card-heading"><strong>{{ item.name }}</strong><ElSwitch :model-value="!!assembly.components[item.id]" :disabled="disabled || item.id === 'llm'" :aria-label="'安装' + item.name" @change="value => toggle(item.id, !!value)" /></div>
        <p class="small muted">{{ item.description }}</p>
        <div class="assembly-card-actions"><ElTag v-if="item.id === 'llm'" size="small">必要组件</ElTag><span v-else class="small muted">{{ assembly.components[item.id] ? '已安装' : '未安装' }}</span><ElButton v-if="assembly.components[item.id]" text :disabled="disabled" @click="open(item.id)">参数设置</ElButton></div>
      </article>
    </div>
    <ElDialog :model-value="!!selected" :title="current?.name + ' · 组件参数'" width="min(690px, 94vw)" @close="selected = ''">
      <div v-if="current" class="assembly-parameters">
        <p class="config-description">{{ current.description }}</p>
        <template v-if="selected === 'llm'">
          <label>模型来源<ElSelect :model-value="String(llm.source ?? 'system')" :disabled="disabled" @change="value => source(String(value))"><ElOption value="system" label="系统模型" /><ElOption value="custom" label="当前聊天私有模型" /></ElSelect></label>
          <template v-if="llm.source !== 'custom'">
            <ElAlert v-if="catalog?.modelError" :title="catalog.modelError" type="warning" :closable="false" />
            <label>模型路由<ElSelect :model-value="String(llm.route ?? '')" :disabled="disabled" @change="value => parameter('route', value)"><ElOption v-for="(label, value) in catalog?.systemRoutes" :key="value" :value="value" :label="label" /></ElSelect></label>
          </template>
          <template v-else>
            <p class="small muted">只保存到当前聊天配置，不修改系统模型文件。密钥不回显。</p>
            <label>协议适配器<ElSelect :model-value="String(privateModel.adapter ?? '')" :disabled="disabled" @change="value => chooseAdapter(String(value))"><ElOption v-for="item in catalog?.models.routes" :key="item.id" :value="item.id" :label="item.name"><ModelIcon :icon="item.icon" :name="item.name" /> {{ item.name }}</ElOption></ElSelect></label>
            <template v-if="privateModel.adapter">
              <div><label>模型名称</label><ModelNamePicker :model-value="String(privateModel.model ?? '')" :names="adapter?.models ?? []" :disabled="disabled" label="私有模型名称" @update:model-value="value => modelField('model', value)" @select="selectModel" /></div>
              <label>服务地址<ElInput :model-value="String(privateModel['base-url'] ?? '')" :disabled="disabled" @update:model-value="value => modelField('base-url', value)" /></label>
              <label>接口路径<ElInput :model-value="String(privateModel['api-path'] ?? '')" :disabled="disabled" @update:model-value="value => modelField('api-path', value)" /></label>
              <div class="assembly-number-row"><label>最大输出 token<ElInputNumber :model-value="Number(privateModel['max-tokens'])" :min="1" :disabled="disabled" @change="value => modelField('max-tokens', value)" /></label><label>上下文总容量<ElInputNumber :model-value="Number(privateModel['context-window-tokens'])" :min="1" :disabled="disabled" @change="value => modelField('context-window-tokens', value)" /></label></div>
              <label>输入上下文占比<ElInputNumber :model-value="Number(privateModel['context-input-ratio'] ?? 0.8)" :min="0.01" :max="1" :step="0.05" :disabled="disabled" @change="value => modelField('context-input-ratio', value)" /></label>
              <div v-if="keepSecret" class="assembly-card-heading"><span>API key 已保存</span><ElButton :disabled="disabled" @click="modelField('api-key', '')">替换密钥</ElButton></div>
              <template v-else><label>从环境变量读取<ElSwitch :model-value="environment" :disabled="disabled" @change="value => modelField('api-key', value ? '${}' : '')" /></label><label>{{ environment ? '环境变量名' : 'API key' }}<ElInput :model-value="secretText" :type="environment ? 'text' : 'password'" :disabled="disabled" autocomplete="off" @update:model-value="value => modelField('api-key', environment ? '${' + value + '}' : value)" /></label></template>
              <template v-if="advanced"><label>扩展 metadata（JSON）<ElInput type="textarea" :rows="4" :model-value="editingJSON.metadata ?? JSON.stringify(privateModel.metadata ?? {}, null, 2)" :disabled="disabled" @update:model-value="value => jsonField('metadata', value, true)" /></label><label>温度<ElInputNumber :model-value="Number(privateModel.temperature ?? 1)" :disabled="disabled" :step="0.1" @change="value => modelField('temperature', value)" /></label><label>思考模式<ElSwitch :model-value="privateModel.thinking === true" :disabled="disabled" @change="value => modelField('thinking', value)" /></label></template>
            </template>
          </template>
        </template>
        <template v-else>
          <div v-for="field in current.fields" :key="field.name" class="assembly-parameter"><label>{{ field.label }}</label><p class="config-description">{{ field.description }}</p>
            <ElSwitch v-if="field.kind === 'BOOLEAN'" :model-value="Boolean(parameters[field.name] ?? current.defaults[field.name])" :disabled="disabled" @change="value => parameter(field.name, value)" />
            <ElInputNumber v-else-if="field.kind === 'NUMBER'" :model-value="Number(parameters[field.name] ?? current.defaults[field.name])" :disabled="disabled" @change="value => parameter(field.name, value)" />
            <ElInput v-else-if="field.kind === 'JSON'" type="textarea" :rows="4" :model-value="editingJSON[field.name] ?? JSON.stringify(parameters[field.name] ?? current.defaults[field.name] ?? {}, null, 2)" :disabled="disabled" @update:model-value="value => jsonField(field.name, value)" />
            <template v-else-if="field.kind === 'SECRET' && typeof parameters[field.name] === 'object'"><span class="small">已保存，不回显</span><ElButton :disabled="disabled" @click="parameter(field.name, '')">替换</ElButton></template>
            <ElInput v-else :model-value="String(parameters[field.name] ?? current.defaults[field.name] ?? '')" :type="field.kind === 'LONG_TEXT' ? 'textarea' : field.kind === 'SECRET' ? 'password' : 'text'" :disabled="disabled" @update:model-value="value => parameter(field.name, value)" />
          </div>
        </template>
        <ElAlert v-if="error" :title="error" type="error" :closable="false" />
        <p v-if="current.requires.length" class="small muted">依赖：{{ current.requires.map(id => catalog?.components.find(item => item.id === id)?.name ?? id).join('、') }}</p>
        <div class="editor-actions"><ElButton type="primary" :disabled="invalidJSON.size > 0" @click="selected = ''">完成设置</ElButton></div>
      </div>
    </ElDialog>
    <details v-if="advanced"><summary>装配 JSON（已隐藏密钥）</summary><pre class="assembly-json">{{ safePreview }}</pre></details>
  </div>
</template>
