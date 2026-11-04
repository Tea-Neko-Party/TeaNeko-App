<script setup lang="ts">
// 2026/11/04：从实际注册目录读取说明，不在前端重复维护组件及工具清单。
import {computed, ref, watch} from 'vue'
import {ElAlert, ElEmpty, ElInput, ElTag} from 'element-plus'
import {ApiError, request} from './api'
import RefreshButton from './RefreshButton.vue'

interface Field {name: string; label: string; description: string; kind: string}
interface SystemDoc {id: string; phase: string; required: string[]; writes: string[]; after: string[]; optional: boolean}
interface ComponentDoc {id: string; name: string; description: string; version: number; requires: string[]; systems: SystemDoc[]; fields: Field[]}
interface Parameter {type: string; description: string; required: string[]; properties: Record<string, Parameter>}
interface ToolDoc {name: string; description: string; clients: string[]; conditional: boolean; parameters: Parameter}
const props = defineProps<{mode: 'components' | 'tools'}>()
const emit = defineEmits<{expired: []}>()
const components = ref<ComponentDoc[]>([])
const tools = ref<ToolDoc[]>([])
const search = ref('')
const busy = ref(false)
const error = ref('')
let generation = 0
const matches = (value: unknown) => JSON.stringify(value).toLowerCase().includes(search.value.toLowerCase())
const visibleComponents = computed(() => components.value.filter(matches))
const visibleTools = computed(() => tools.value.filter(matches))
const phases: Record<string, string> = {BEFORE_PROMPT: '准备输入前', BEFORE_INTERACTION: '调用模型前', AFTER_INTERACTION: '模型返回后', BEFORE_DELIVERY: '最终交付前'}
async function load() {
  const current = ++generation
  busy.value = true
  error.value = ''
  try {
    if (props.mode === 'components') {
      const value = await request<{components: ComponentDoc[]}>('agent/catalog/components')
      if (current === generation) components.value = value.components
    } else {
      const value = await request<ToolDoc[]>('agent/catalog/tools')
      if (current === generation) tools.value = value
    }
  } catch (failure) {
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
    if (current === generation) error.value = failure instanceof Error ? failure.message : '目录暂不可用。'
  } finally { if (current === generation) busy.value = false }
}
watch(() => props.mode, () => { search.value = ''; void load() }, {immediate: true})
</script>

<template>
  <section class="api-docs-view">
    <div class="toolbar"><ElInput v-model="search" clearable :placeholder="mode === 'components' ? '搜索组件、参数或系统' : '搜索工具、参数或客户端'" aria-label="筛选 API 文档" /><RefreshButton :disabled="busy" @click="load" /></div>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <p class="small muted">{{ mode === 'components' ? '在聊天设置中安装组件；系统读取组件参数，在固定阶段运行。新登记默认只有 LLM。' : '工具目录仅表示已注册能力。实际调用仍检查组件、客户端、权限和当前业务条件。' }}</p>
    <div v-if="mode === 'components'" class="api-docs-grid">
      <article v-for="item in visibleComponents" :key="item.id" class="api-docs-card"><header><h3>{{ item.name }}</h3><code>{{ item.id }}</code><ElTag size="small">协议 {{ item.version }}</ElTag></header><p>{{ item.description }}</p>
        <p v-if="item.requires.length" class="small">依赖：{{ item.requires.join('、') }}</p>
        <div v-if="item.systems.length"><h4>执行系统</h4><div v-for="system in item.systems" :key="system.id" class="api-docs-field small"><code>{{ system.id }}</code><ElTag size="small">{{ phases[system.phase] ?? system.phase }}</ElTag><p>必需输入：{{ system.required.join('、') || '无' }}</p><p>写入：{{ system.writes.join('、') || '无' }}</p><p v-if="system.after.length">前置系统：{{ system.after.join('、') }}</p><p v-if="system.optional">输入缺失时允许跳过</p></div></div>
        <h4>组件参数</h4><div v-for="field in item.fields" :key="field.name" class="api-docs-field"><strong>{{ field.label }}</strong> <code>{{ field.name }}</code><span class="small muted">{{ field.kind }}</span><p class="small">{{ field.description }}</p></div>
      </article>
    </div>
    <div v-else class="api-docs-grid">
      <article v-for="tool in visibleTools" :key="tool.name" class="api-docs-card"><header><h3>{{ tool.name }}</h3><ElTag size="small">{{ tool.clients.length ? tool.clients.join('、') : '所有客户端' }}</ElTag></header><p>{{ tool.description }}</p>
        <p v-if="tool.conditional" class="small muted">另有业务条件，由真实调用时检查。</p>
        <div v-for="(parameter, name) in tool.parameters.properties" :key="name" class="api-docs-field"><code>{{ name }}</code><ElTag size="small" :type="tool.parameters.required.includes(String(name)) ? 'warning' : 'info'">{{ tool.parameters.required.includes(String(name)) ? '必填' : '可选' }}</ElTag><span class="small muted">{{ parameter.type }}</span><p class="small">{{ parameter.description }}</p></div>
      </article>
    </div>
    <ElEmpty v-if="!busy && !(mode === 'components' ? visibleComponents.length : visibleTools.length)" description="没有匹配的声明" />
  </section>
</template>
