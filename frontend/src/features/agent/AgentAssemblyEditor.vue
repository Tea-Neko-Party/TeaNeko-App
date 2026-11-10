<script setup lang="ts">
// 2026/11/10：插件归属统一优先展示名称，稳定 ID 保留在悬停说明及筛选中。
import PluginLabel from '../../shared/plugins/PluginLabel.vue'
// 2026/11/07：按功能目录引用共享能力与页面契约。
// 2026/11/04：组件目录驱动装配表单，所有更改仅进入父页面草稿，由原配置事务提交。
import {computed, onMounted, onScopeDispose, ref, watch} from 'vue'
import {ElAlert, ElButton, ElDialog, ElInput, ElInputNumber, ElSwitch, ElTag} from 'element-plus'
import {ApiError} from '../../shared/api/client'
import ModelSelectionEditor from '../models/ModelSelectionEditor.vue'
import ModelVisionProbeButton from '../models/ModelVisionProbeButton.vue'
import ValueEditor from '../../shared/editors/ValueEditor.vue'
import FieldFrame from '../../shared/components/FieldFrame.vue'
import type {ModelCatalog} from '../models/ModelTemplateEditor.vue'
import {useConsolePreferences} from '../../shared/state/console-preferences'
import PluginFilter from '../../shared/plugins/PluginFilter.vue'
import {
  pluginAvailability,
  pluginEnabled,
  PluginRequestSuperseded,
  usePluginRequests
} from '../../shared/plugins/plugin-console'
import {appliedModelRevision} from '../models/model-configuration-state'
import {useActivePolling} from '../../shared/lifecycle/active-polling'

interface Field {name: string; label: string; description: string; kind: string; probe?: string}
interface Component {pluginId?: string | null; id: string; name: string; description: string; version: number; requires: string[]; defaults: Record<string, unknown>; fields: Field[]}
interface Catalog {components: Component[]; models: ModelCatalog; systemRoutes: Record<string, string>; modelError: string}
interface Assembly {version: number; components: Record<string, Record<string, unknown>>}
const props = defineProps<{modelValue: string; disabled: boolean; probeContext?: Record<string, string>; unsaved?: boolean}>()
const emit = defineEmits<{'update:modelValue': [value: string]; expired: []; validity: [valid: boolean]}>()
const {advanced} = useConsolePreferences()
const catalog = ref<Catalog>()
// 2026/11/10：刷新只替换目录，不改装配草稿；迟到结果不得跨越插件启停或组件销毁。
const {request} = usePluginRequests()
let loading = 0
const selected = ref('')
const pluginId = ref('')
const visibleComponents = computed(() => catalog.value?.components.filter(item => pluginEnabled(item.pluginId) && (!pluginId.value || item.pluginId === pluginId.value)) ?? [])
const error = ref('')
const editingJSON = ref<Record<string, string>>({})
const invalidJSON = ref(new Set<string>())
const assembly = computed<Assembly>(() => JSON.parse(props.modelValue))
const current = computed(() => catalog.value?.components.find(item => item.id === selected.value && pluginEnabled(item.pluginId)))
watch(pluginAvailability, () => { if (!current.value) selected.value = '' })
const parameters = computed(() => assembly.value.components[selected.value] ?? {})
const llm = computed(() => assembly.value.components.llm!)
// 2026/11/04：高级预览也不回显本次新输入的秘密。
const safePreview = computed(() => {
  const value = structuredClone(assembly.value)
  // 2026/11/07：停用组件的持久配置仍保留，公开预览只展示当前可见目录。
  const available = new Set((catalog.value?.components ?? []).filter(item => pluginEnabled(item.pluginId)).map(item => item.id))
  for (const id of Object.keys(value.components)) if (!available.has(id)) delete value.components[id]
  for (const item of catalog.value?.components ?? []) {
    const parameters = value.components[item.id]
    if (!parameters) continue
    for (const field of item.fields) {
      if (field.kind === 'SECRET' && field.name in parameters) parameters[field.name] = '（已隐藏）'
      if (field.kind === 'MODEL') {
        const model = parameters[field.name] as {configuration?: Record<string, unknown>} | undefined
        if (model?.configuration && 'api-key' in model.configuration) model.configuration['api-key'] = '（已隐藏）'
      }
    }
  }
  const configuration = value.components.llm?.configuration as Record<string, unknown> | undefined
  if (configuration && 'api-key' in configuration) configuration['api-key'] = '（已隐藏）'
  return JSON.stringify(value, null, 2)
})

async function load() {
  const generation = ++loading
  try {
    const value = await request<Catalog>('agent/catalog/components')
    if (generation === loading) {
      catalog.value = value
      error.value = ''
    }
  }
  catch (failure) {
    if (failure instanceof PluginRequestSuperseded || generation !== loading) return
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
    error.value = failure instanceof Error ? failure.message : '无法读取组件目录。'
  }
}
onMounted(load)
watch(appliedModelRevision, () => { void load() })
watch(pluginAvailability, () => { void load() })
// 2026/11/10：弹窗内轮询及重新聚焦同步其他会话的模型更新，始终保留未保存的用户参数。
useActivePolling(() => !!selected.value, () => { void load() }, 10000)
const refreshOnFocus = () => { if (!!selected.value && !document.hidden) void load() }
onMounted(() => window.addEventListener('focus', refreshOnFocus))
onScopeDispose(() => window.removeEventListener('focus', refreshOnFocus))

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
function jsonField(name: string, text: string) {
  editingJSON.value[name] = text
  try {
    const value: unknown = JSON.parse(text)
    parameter(name, value)
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
  // 2026/11/10：再次打开时读取当前路由，避免保留页面继续使用首次挂载的旧目录。
  void load()
}
</script>

<template>
  <div class="agent-assembly-editor">
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <div v-if="catalog" class="assembly-grid">
      <PluginFilter v-model="pluginId" :items="catalog.components" /><article v-for="item in visibleComponents" :key="item.id" class="assembly-card" :class="{'is-installed': !!assembly.components[item.id]}">
        <div class="assembly-card-heading"><strong>{{ item.name }}</strong><ElSwitch :model-value="!!assembly.components[item.id]" :disabled="disabled || item.id === 'llm'" :aria-label="'安装' + item.name" @change="value => toggle(item.id, !!value)" /></div>
        <code v-if="item.pluginId"><PluginLabel :plugin-id="item.pluginId" /></code><p class="small muted">{{ item.description }}</p>
        <p class="component-dependencies small"><span class="muted">依赖</span> {{ item.requires.map(id => catalog?.components.find(entry => entry.id === id)?.name ?? id).join('、') || '无' }}</p>
        <!-- 2026/11/05：安装状态由开关表达，仅保留必要标识；参数入口统一靠卡片底部右侧。 -->
        <div class="assembly-card-actions">
          <ElTag v-if="item.id === 'llm'" size="small">必要组件</ElTag>
          <!-- 2026/11/07：使用主题色突出参数入口，与组件安装开关区分。 -->
          <ElButton v-if="assembly.components[item.id]" type="primary" plain :disabled="disabled" @click="open(item.id)">参数设置</ElButton>
        </div>
      </article>
    </div>
    <ElDialog :model-value="!!selected" :title="current?.name + ' · 组件参数'" width="min(690px, 94vw)" @close="selected = ''">
      <div v-if="current" class="assembly-parameters">
        <code v-if="current.pluginId"><PluginLabel :plugin-id="current.pluginId" /></code><p class="config-description">{{ current.description }}</p>
        <template v-if="selected === 'llm' && catalog">
          <ElAlert v-if="catalog.modelError" :title="catalog.modelError" type="warning" :closable="false" />
          <ModelSelectionEditor :model-value="llm" :catalog="catalog.models" :routes="catalog.systemRoutes" :disabled="disabled" @update:model-value="value => publish({...assembly, components: {...assembly.components, llm: {...value}}})" @validity="value => emit('validity', value)" />
        </template>
        <template v-else>
          <FieldFrame v-for="field in current.fields" :key="field.name" :label="field.label" :description="field.description" :error="invalidJSON.has(field.name) ? 'JSON 格式尚未完成' : ''" v-slot="{attributes}">
            <template v-if="field.kind === 'MODEL' && catalog">
              <ModelSelectionEditor :model-value="(parameters[field.name] ?? current.defaults[field.name] ?? {}) as Record<string, unknown>" :catalog="catalog.models" :routes="catalog.systemRoutes" :disabled="disabled" default-source="inherit" @update:model-value="value => parameter(field.name, value)" @validity="value => emit('validity', value)" />
              <ModelVisionProbeButton v-if="field.probe" :endpoint="field.probe" :context="probeContext" :disabled="disabled || !!unsaved" @expired="emit('expired')" />
            </template>
            <ElSwitch v-bind="attributes" v-else-if="field.kind === 'BOOLEAN'" :model-value="Boolean(parameters[field.name] ?? current.defaults[field.name])" :disabled="disabled" @change="value => parameter(field.name, value)" />
            <ElInputNumber v-bind="attributes" v-else-if="field.kind === 'NUMBER'" :model-value="Number(parameters[field.name] ?? current.defaults[field.name])" :disabled="disabled" @change="value => parameter(field.name, value)" />
            <ValueEditor v-else-if="field.kind === 'JSON'" kind="JSON" :label="field.label" :model-value="editingJSON[field.name] ?? JSON.stringify(parameters[field.name] ?? current.defaults[field.name] ?? {}, null, 2)" :disabled="disabled" @update:model-value="value => jsonField(field.name, value)" />
            <ValueEditor v-else-if="field.kind === 'LONG_TEXT'" :label="field.label" :model-value="String(parameters[field.name] ?? current.defaults[field.name] ?? '')" :disabled="disabled" @update:model-value="value => parameter(field.name, value)" />
            <template v-else-if="field.kind === 'SECRET' && typeof parameters[field.name] === 'object'"><span class="small">已保存，不回显</span><ElButton :disabled="disabled" @click="parameter(field.name, '')">替换</ElButton></template>
            <ElInput v-bind="attributes" v-else :model-value="String(parameters[field.name] ?? current.defaults[field.name] ?? '')" :type="field.kind === 'LONG_TEXT' ? 'textarea' : field.kind === 'SECRET' ? 'password' : 'text'" :disabled="disabled" @update:model-value="value => parameter(field.name, value)" />
          </FieldFrame>
        </template>
        <ElAlert v-if="error" :title="error" type="error" :closable="false" />
        <p v-if="current.requires.length" class="small muted">依赖：{{ current.requires.map(id => catalog?.components.find(item => item.id === id)?.name ?? id).join('、') }}</p>
        <div class="editor-actions"><ElButton type="primary" :disabled="invalidJSON.size > 0" @click="selected = ''">完成设置</ElButton></div>
      </div>
    </ElDialog>
    <details v-if="advanced"><summary>装配 JSON（已隐藏密钥）</summary><pre class="assembly-json">{{ safePreview }}</pre></details>
  </div>
</template>
