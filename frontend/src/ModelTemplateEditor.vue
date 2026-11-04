<script setup lang="ts">
// 2026/11/04：模型样板来自已注册供应商适配器，新增只进入当前草稿，保存与应用仍分开。
import {computed, ref} from 'vue'
import {ElAlert, ElButton, ElDialog, ElInput, ElOption, ElSelect, ElSwitch} from 'element-plus'
import type {CredentialInput} from './FileFieldEditor.vue'

export interface ModelRoute {id: string; name: string; description: string; defaults: Record<string, unknown>; models: string[]}
export interface ModelCatalog {routes: ModelRoute[]; inputs: CredentialInput[]}
const props = defineProps<{catalog: ModelCatalog; configured: string[]; disabled: boolean; hasDefault: boolean}>()
const emit = defineEmits<{append: [value: Record<string, unknown>, defaultRoute: boolean]}>()
const open = ref(false)
const custom = ref(false)
const routeId = ref('')
const form = ref({model: '', url: '', path: '', output: '', context: '', ratio: '0.8'})
const inputId = ref('manual')
const secret = ref('')
const makeDefault = ref(false)
const error = ref('')
const route = computed(() => props.catalog.routes.find(item => item.id === routeId.value))
const input = computed(() => props.catalog.inputs.find(item => item.id === inputId.value) ?? props.catalog.inputs[0])
const available = computed(() => props.catalog.routes.filter(item => !props.configured.includes(item.id)))

function selectRoute(id: string) {
  routeId.value = id
  const defaults = route.value?.defaults ?? {}
  form.value = {model: String(defaults.model ?? ''), url: String(defaults['base-url'] ?? ''), path: String(defaults['api-path'] ?? ''),
    output: String(defaults['max-tokens'] ?? ''), context: String(defaults['context-window-tokens'] ?? ''), ratio: String(defaults['context-input-ratio'] ?? 0.8)}
}
function begin(id: string, customize = false) {
  custom.value = customize
  secret.value = ''
  error.value = ''
  makeDefault.value = !props.hasDefault
  selectRoute(id)
  open.value = true
}
function add() {
  error.value = ''
  const mode = input.value
  if (!route.value || !form.value.model.trim() || !Number.isSafeInteger(Number(form.value.output)) || Number(form.value.output) < 1
      || !Number.isSafeInteger(Number(form.value.context)) || Number(form.value.context) <= Number(form.value.output)
      || !Number.isFinite(Number(form.value.ratio)) || Number(form.value.ratio) <= 0 || Number(form.value.ratio) > 1) {
    error.value = '请填写模型名称、正数输出额度及足以容纳输入和输出的真实上下文容量。'
    return
  }
  if (secret.value && (!mode || !new RegExp(mode.pattern).test(secret.value))) { error.value = mode?.placeholder ?? '凭据输入方式不可用。'; return }
  if (custom.value && (!form.value.url.trim() || !form.value.path.trim())) { error.value = '自定义连接需要填写服务地址和接口路径。'; return }
  const value: Record<string, unknown> = {...route.value.defaults, id: routeId.value, model: form.value.model.trim(),
    'max-tokens': Number(form.value.output), 'context-window-tokens': Number(form.value.context),
    'context-input-ratio': Number(form.value.ratio), 'api-key': secret.value && mode ? mode.prefix + secret.value + mode.suffix : ''}
  if (custom.value) { value['base-url'] = form.value.url.trim(); value['api-path'] = form.value.path.trim() }
  emit('append', value, makeDefault.value)
  secret.value = ''
  open.value = false
}
</script>

<template>
  <div class="model-template-picker"><h3>添加模型</h3><p class="small muted">选择协议样板，默认地址和路径自动填写。每个适配器保留一个路由，可修改具体模型或使用自定义服务。</p>
    <div class="editor-actions"><ElButton v-for="item in catalog.routes" :key="item.id" :disabled="disabled || configured.includes(item.id)" @click="begin(item.id)">＋ {{ item.name }}{{ configured.includes(item.id) ? '（已添加）' : '' }}</ElButton><ElButton :disabled="disabled || !available.length" @click="begin(available[0]!.id, true)">自定义模型</ElButton></div>
    <ElDialog v-model="open" :title="custom ? '自定义模型' : `添加 ${route?.name ?? '模型'} 样板`" width="min(620px, 94vw)" @closed="secret = ''">
      <div class="model-create-form">
        <label>协议适配器<ElSelect :model-value="routeId" :disabled="!custom" aria-label="新增模型协议适配器" @update:model-value="value => selectRoute(String(value))"><ElOption v-for="item in available" :key="item.id" :value="item.id" :label="item.name" /></ElSelect></label>
        <p class="config-description">{{ route?.description }}</p>
        <label>模型名称<ElSelect v-model="form.model" filterable allow-create default-first-option aria-label="新增模型名称"><ElOption v-for="model in route?.models" :key="model" :value="model" :label="model" /></ElSelect></label>
        <template v-if="custom"><label>服务地址<ElInput v-model="form.url" aria-label="新增模型服务地址" placeholder="https://example.com/v1" /></label><label>接口路径<ElInput v-model="form.path" aria-label="新增模型接口路径" placeholder="/chat/completions" /></label></template>
        <div class="model-create-numbers"><label>最大输出 token<ElInput v-model="form.output" type="number" aria-label="新增模型最大输出" /></label><label>上下文总容量<ElInput v-model="form.context" type="number" aria-label="新增模型上下文容量" /></label></div>
        <label>输入上下文占比<ElInput v-model="form.ratio" type="number" aria-label="新增模型输入比例" /></label>
        <label>API key 输入方式<ElSelect v-model="inputId" aria-label="新增模型密钥输入方式" @change="secret = ''"><ElOption v-for="mode in catalog.inputs" :key="mode.id" :value="mode.id" :label="mode.name" /></ElSelect></label>
        <label>API key<ElInput v-model="secret" :type="input?.secret ? 'password' : 'text'" :placeholder="input?.placeholder" aria-label="新增模型密钥" autocomplete="off" /></label><p class="small muted">{{ input?.description }} 留空可先保存样板，配置有效密钥后才能调用。</p>
        <label class="model-default-toggle">设为默认模型<ElSwitch v-model="makeDefault" aria-label="新增后设为默认模型" /></label>
        <ElAlert v-if="error" :title="error" type="error" :closable="false" />
        <div class="editor-actions"><ElButton @click="open = false">取消</ElButton><ElButton type="primary" @click="add">加入本次修改</ElButton></div>
      </div>
    </ElDialog>
  </div>
</template>
