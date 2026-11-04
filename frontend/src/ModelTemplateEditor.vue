<script setup lang="ts">
// 2026/11/04：模型样板来自已注册供应商适配器，新增在确认后直接保存，保存与应用仍分开。
import {computed, ref} from 'vue'
import {ElAlert, ElButton, ElDialog, ElInput, ElOption, ElSelect, ElSwitch} from 'element-plus'
import type {CredentialInput} from './FileFieldEditor.vue'
import ModelNamePicker from './ModelNamePicker.vue'
import ModelIcon from './ModelIcon.vue'
import ConsoleIcon from './ConsoleIcon.vue'

export interface ModelRoute {id: string; name: string; description: string; defaults: Record<string, unknown>; models: string[]; contextWindows?: Record<string, number>; icon?: string}
export interface ModelCatalog {routes: ModelRoute[]; inputs: CredentialInput[]}
const props = defineProps<{catalog: ModelCatalog; configured: string[]; disabled: boolean; hasDefault: boolean}>()
const emit = defineEmits<{append: [value: Record<string, unknown>, defaultRoute: boolean]}>()
const open = ref(false)
const picking = ref(false)
const custom = ref(false)
const routeId = ref('')
const configurationId = ref('')
const form = ref({model: '', url: '', path: '', output: '', context: '', ratio: '0.8'})
const inputId = ref('manual')
const secret = ref('')
const makeDefault = ref(false)
const error = ref('')
const route = computed(() => props.catalog.routes.find(item => item.id === routeId.value))
const input = computed(() => props.catalog.inputs.find(item => item.id === inputId.value) ?? props.catalog.inputs[0])
const available = computed(() => props.catalog.routes)

function selectRoute(id: string) {
  routeId.value = id
  // 2026/11/04：同一协议允许反复添加，建议未使用的路由；提交时仍检查唯一性。
  let candidate = id
  for (let suffix = 2; props.configured.includes(candidate); suffix++) candidate = `${id}-${suffix}`
  configurationId.value = candidate
  const defaults = route.value?.defaults ?? {}
  form.value = {model: String(defaults.model ?? ''), url: String(defaults['base-url'] ?? ''), path: String(defaults['api-path'] ?? ''),
    output: String(defaults['max-tokens'] ?? ''), context: String(defaults['context-window-tokens'] ?? ''), ratio: String(defaults['context-input-ratio'] ?? 0.8)}
}
function begin(id: string, customize = false) {
  picking.value = false
  custom.value = customize
  secret.value = ''
  error.value = ''
  makeDefault.value = !props.hasDefault
  selectRoute(id)
  open.value = true
}
function add() {
  error.value = ''
  if (!/^[a-z][a-z0-9-]{0,63}$/.test(configurationId.value) || props.configured.includes(configurationId.value)) {
    error.value = '路由须以小写字母开头，只含字母、数字或连字符，最多64字符，且不能重复。'
    return
  }
  if (configurationId.value !== routeId.value && props.catalog.routes.some(item => item.id === configurationId.value)) {
    error.value = '该标识属于其他协议，请为当前配置使用新的路由名称。'
    return
  }
  const mode = input.value
  if (!route.value || !form.value.model.trim() || !Number.isSafeInteger(Number(form.value.output)) || Number(form.value.output) < 1
      || !Number.isSafeInteger(Number(form.value.context)) || Number(form.value.context) <= Number(form.value.output)
      || !Number.isFinite(Number(form.value.ratio)) || Number(form.value.ratio) <= 0 || Number(form.value.ratio) > 1) {
    error.value = '请填写模型名称、正数输出额度及足以容纳输入和输出的真实上下文容量。'
    return
  }
  if (secret.value && (!mode || !new RegExp(mode.pattern).test(secret.value))) { error.value = mode?.placeholder ?? '凭据输入方式不可用。'; return }
  if (custom.value && (!form.value.url.trim() || !form.value.path.trim())) { error.value = '自定义连接需要填写服务地址和接口路径。'; return }
  const value: Record<string, unknown> = {...route.value.defaults, id: configurationId.value, adapter: routeId.value, model: form.value.model.trim(),
    'max-tokens': Number(form.value.output), 'context-window-tokens': Number(form.value.context),
    'context-input-ratio': Number(form.value.ratio), 'api-key': secret.value && mode ? mode.prefix + secret.value + mode.suffix : ''}
  if (custom.value) { value['base-url'] = form.value.url.trim(); value['api-path'] = form.value.path.trim() }
  emit('append', value, makeDefault.value)
  secret.value = ''
  open.value = false
}
</script>

<template>
  <div class="model-template-picker"><ElButton circle :disabled="disabled || !available.length" aria-label="添加模型" title="添加模型" @click="picking = true; open = true"><ConsoleIcon name="plus" /></ElButton>
    <!-- 2026/11/04：新增入口只保留加号，类型目录与配置表单在同一弹窗分步展示。 -->
    <ElDialog v-model="open" :title="picking ? '添加模型' : custom ? '自定义模型' : `添加 ${route?.name ?? '模型'} 样板`" width="min(620px, 94vw)" @closed="secret = ''">
      <div v-if="picking"><p class="small muted">选择类型，自动填写协议连接。同一类型可添加多次。</p><div class="model-template-grid"><button v-for="item in catalog.routes" :key="item.id" class="model-template-button" @click="begin(item.id)"><ModelIcon :icon="item.icon" :name="item.name" /><span>{{ item.name }}</span><span class="muted">＋</span></button><ElButton :disabled="!available.length" @click="begin(available[0]!.id, true)">自定义模型</ElButton></div></div>
      <div v-else class="model-create-form">
        <!-- 2026/11/05：连接信息先于路由和模型参数显示；供应商样板地址只读，自定义仍可编辑。 -->
        <label>协议适配器<ElSelect :model-value="routeId" :disabled="!custom" aria-label="新增模型协议适配器" @update:model-value="value => selectRoute(String(value))"><ElOption v-for="item in available" :key="item.id" :value="item.id" :label="item.name" /></ElSelect></label>
        <label>服务地址<ElInput v-model="form.url" :readonly="!custom" aria-label="新增模型服务地址" placeholder="https://example.com/v1" /></label>
        <label>接口路径<ElInput v-model="form.path" :readonly="!custom" aria-label="新增模型接口路径" placeholder="/chat/completions" /></label>
        <label>路由标识<ElInput v-model="configurationId" aria-label="新增模型路由标识" placeholder="例如 deepseek-chat" /></label>
        <p class="config-description">{{ route?.description }}</p>
        <div><label>模型名称</label><ModelNamePicker v-model="form.model" :names="route?.models ?? []" label="新增模型名称" @select="value => { const capacity = route?.contextWindows?.[value]; if (capacity) form.context = String(capacity) }" /></div>
        <div class="model-create-numbers"><label>最大输出 token<ElInput v-model="form.output" type="number" aria-label="新增模型最大输出" /></label><label>上下文总容量<ElInput v-model="form.context" type="number" aria-label="新增模型上下文容量" /></label></div>
        <label>输入上下文占比<ElInput v-model="form.ratio" type="number" aria-label="新增模型输入比例" /></label>
        <!-- 2026/11/05：新增模型与编辑表单统一使用 API 密钥名称。 -->
        <label>API 密钥输入方式<ElSelect v-model="inputId" aria-label="新增模型密钥输入方式" @change="secret = ''"><ElOption v-for="mode in catalog.inputs" :key="mode.id" :value="mode.id" :label="mode.name" /></ElSelect></label>
        <label>API 密钥<ElInput v-model="secret" :type="input?.secret ? 'password' : 'text'" :placeholder="input?.placeholder" aria-label="新增模型密钥" autocomplete="off" /></label><p class="small muted">{{ input?.description }} 留空可先保存样板，配置有效密钥后才能调用。保存会重新排版 YAML、不保留原注释；运行应用仍需单独确认。</p>
        <label class="model-default-toggle">设为默认模型<ElSwitch v-model="makeDefault" aria-label="新增后设为默认模型" /></label>
        <ElAlert v-if="error" :title="error" type="error" :closable="false" />
        <div class="editor-actions"><ElButton @click="secret = ''; picking = true">返回</ElButton><ElButton @click="open = false">取消</ElButton><ElButton type="primary" @click="add">保存模型</ElButton></div>
      </div>
    </ElDialog>
  </div>
</template>
