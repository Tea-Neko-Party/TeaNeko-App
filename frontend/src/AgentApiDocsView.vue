<script setup lang="ts">
// 2026/11/04：从实际注册目录读取说明，不在前端重复维护组件及工具清单。
import {computed, ref, watch} from 'vue'
import {ElAlert, ElDrawer, ElEmpty, ElInput, ElOption, ElSelect, ElTag} from 'element-plus'
import {ApiError, request} from './api'
import RefreshButton from './RefreshButton.vue'

interface Field {name: string; label: string; description: string; kind: string}
interface SystemDoc {id: string; phase: string; required: string[]; writes: string[]; after: string[]; optional: boolean}
interface ComponentDoc {id: string; name: string; description: string; version: number; requires: string[]; systems: SystemDoc[]; fields: Field[]}
interface Parameter {type: string; description: string; required?: string[]; properties?: Record<string, Parameter>}
interface ToolDoc {name: string; description: string; clients: string[]; conditional: boolean; parameters: Parameter}
const props = defineProps<{mode: 'components' | 'tools'}>()
const emit = defineEmits<{expired: []}>()
const components = ref<ComponentDoc[]>([])
const tools = ref<ToolDoc[]>([])
const search = ref('')
// 2026/11/06：摘要目录与详情分开；客户端筛选只限制展示，不替代调用时权限检查。
const client = ref('')
const selected = ref('')
const detailOpen = ref(false)
const clientOptions = computed(() => [...new Set(tools.value.flatMap(tool => tool.clients))].sort((a, b) => a.localeCompare(b, 'zh-CN')))
const componentDetail = computed(() => components.value.find(item => item.id === selected.value))
const toolDetail = computed(() => tools.value.find(item => item.name === selected.value))
function showDetail(id: string) {
  selected.value = id
  detailOpen.value = true
}
const busy = ref(false)
const error = ref('')
let generation = 0
const matches = (value: unknown) => JSON.stringify(value).toLowerCase().includes(search.value.toLowerCase())
const visibleComponents = computed(() => components.value.filter(matches))
const visibleTools = computed(() => tools.value.filter(tool => matches(tool) && (!client.value || !tool.clients.length || tool.clients.includes(client.value))))
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
watch(() => props.mode, () => { search.value = ''; client.value = ''; detailOpen.value = false; void load() }, {immediate: true})
</script>

<template>
  <!-- 2026/11/06：固定信息层次的目录卡片保持对齐，完整内容在独立详情中展示。 -->
  <section class="api-docs-view">
    <div class="toolbar">
      <ElInput v-model="search" clearable :placeholder="mode === 'components' ? '搜索组件、参数或系统' : '搜索工具或参数'" aria-label="筛选 API 文档" />
      <ElSelect v-if="mode === 'tools'" v-model="client" clearable filterable placeholder="全部客户端" aria-label="筛选工具客户端">
        <ElOption v-for="name in clientOptions" :key="name" :label="name" :value="name" />
      </ElSelect>
      <RefreshButton :disabled="busy" @click="load" />
    </div>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <div class="api-directory-caption"><p class="small muted">{{ mode === 'components' ? '按需装配组件，点击查看参数与执行系统。' : '选择客户端查看适用工具，通用工具始终包含在结果中。' }}</p><span class="small muted">{{ mode === 'components' ? visibleComponents.length : visibleTools.length }} 项</span></div>
    <div v-if="mode === 'components'" class="api-docs-grid">
      <article v-for="item in visibleComponents" :key="item.id" class="api-docs-card">
        <button class="api-docs-summary" @click="showDetail(item.id)" :aria-label="'查看组件 ' + item.name">
          <header><div><h3>{{ item.name }}</h3><code>{{ item.id }}</code></div><ElTag size="small">{{ item.id === 'llm' ? '必要' : '组件' }}</ElTag></header>
          <p class="api-docs-brief">{{ item.description }}</p>
          <footer><span>{{ item.systems.length }} 个系统 · {{ item.fields.length }} 个参数</span><span class="api-docs-link">查看详情 →</span></footer>
        </button>
      </article>
    </div>
    <div v-else class="api-docs-grid">
      <article v-for="tool in visibleTools" :key="tool.name" class="api-docs-card">
        <button class="api-docs-summary" @click="showDetail(tool.name)" :aria-label="'查看工具 ' + tool.name">
          <header><h3>{{ tool.name }}</h3><ElTag size="small">{{ tool.clients.length ? '专用' : '通用' }}</ElTag></header>
          <p class="api-docs-brief">{{ tool.description }}</p>
          <footer><span>{{ Object.keys(tool.parameters.properties ?? {}).length }} 个参数{{ tool.conditional ? ' · 有使用条件' : '' }}</span><span class="api-docs-link">查看详情 →</span></footer>
        </button>
      </article>
    </div>
    <ElEmpty v-if="!busy && !(mode === 'components' ? visibleComponents.length : visibleTools.length)" description="没有匹配的声明" />
    <ElDrawer v-model="detailOpen" :title="mode === 'components' ? componentDetail?.name : toolDetail?.name" size="min(680px, 94vw)" class="api-docs-drawer">
      <article v-if="mode === 'components' && componentDetail" class="api-docs-detail">
        <div class="api-detail-meta"><code>{{ componentDetail.id }}</code><ElTag size="small">协议 {{ componentDetail.version }}</ElTag></div>
        <p class="api-detail-description">{{ componentDetail.description }}</p>
        <p v-if="componentDetail.requires.length" class="small muted">依赖：{{ componentDetail.requires.join('、') }}</p>
        <section v-if="componentDetail.systems.length"><h3>执行系统 <small>{{ componentDetail.systems.length }}</small></h3>
          <div v-for="system in componentDetail.systems" :key="system.id" class="api-docs-field">
            <div class="api-detail-meta"><code>{{ system.id }}</code><ElTag size="small">{{ phases[system.phase] ?? system.phase }}</ElTag></div>
            <dl><dt>必需输入</dt><dd>{{ system.required.join('、') || '无' }}</dd><dt>写入</dt><dd>{{ system.writes.join('、') || '无' }}</dd><template v-if="system.after.length"><dt>前置系统</dt><dd>{{ system.after.join('、') }}</dd></template></dl>
            <p v-if="system.optional" class="small muted">输入缺失时允许跳过</p>
          </div>
        </section>
        <section v-if="componentDetail.fields.length"><h3>组件参数 <small>{{ componentDetail.fields.length }}</small></h3>
          <div v-for="field in componentDetail.fields" :key="field.name" class="api-docs-field">
            <div class="api-detail-meta"><strong>{{ field.label }}</strong><code>{{ field.name }}</code><span class="small muted">{{ field.kind }}</span></div><p>{{ field.description }}</p>
          </div>
        </section>
      </article>
      <article v-else-if="mode === 'tools' && toolDetail" class="api-docs-detail">
        <div class="api-detail-meta"><ElTag v-for="name in toolDetail.clients.length ? toolDetail.clients : ['所有客户端']" :key="name" size="small">{{ name }}</ElTag></div>
        <p class="api-detail-description">{{ toolDetail.description }}</p>
        <p class="small muted">实际调用仍检查组件、客户端、权限{{ toolDetail.conditional ? '及业务条件' : '' }}。</p>
        <section><h3>调用参数</h3>
          <p v-if="!Object.keys(toolDetail.parameters.properties ?? {}).length" class="muted">无需参数</p>
          <div v-for="(parameter, name) in toolDetail.parameters.properties" :key="name" class="api-docs-field">
            <div class="api-detail-meta"><code>{{ name }}</code><ElTag size="small" :type="(toolDetail.parameters.required ?? []).includes(String(name)) ? 'warning' : 'info'">{{ (toolDetail.parameters.required ?? []).includes(String(name)) ? '必填' : '可选' }}</ElTag><span class="small muted">{{ parameter.type }}</span></div><p>{{ parameter.description }}</p>
          </div>
        </section>
      </article>
      <ElEmpty v-else description="此声明已不可用，请刷新目录" />
    </ElDrawer>
  </section>
</template>
