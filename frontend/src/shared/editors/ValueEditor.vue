<script setup lang="ts">
// 2026/11/07：按功能目录引用共享能力与页面契约。
// 2026/11/06：弹窗独立草稿；只在确认且校验成功后更新父表单，不持久化输入内容。
import {computed, nextTick, onBeforeUnmount, ref, watch} from 'vue'
import {ElAlert, ElButton, ElDialog} from 'element-plus'
import type {EditorView as EditorViewType} from '@codemirror/view'
import {valueEditor} from './value-editors'
import {usePageDraft} from '../lifecycle/page-guards'

const props = withDefaults(defineProps<{modelValue: string; kind?: string; label?: string; disabled?: boolean; maxlength?: number}>(), {kind: 'LONG_TEXT', label: '编辑内容'})
const emit = defineEmits<{'update:modelValue': [value: string]}>()
const opened = ref(false)
const ready = ref(false)
const loadError = ref('')
let epoch = 0
const host = ref<HTMLElement>()
const draft = ref('')
const baseline = ref('')
// 2026/11/06：未确认的弹窗内容也参与所属页面退出保护。
usePageDraft(() => ({dirty: opened.value && draft.value !== baseline.value}))
const mode = computed(() => valueEditor(props.kind))
const issue = computed(() => props.maxlength && draft.value.length > props.maxlength
  ? `最多允许 ${props.maxlength} 个字符。`
  : mode.value?.validate(draft.value) ?? '当前编辑器未注册，不能提交。')
const changedOutside = computed(() => opened.value && props.modelValue !== baseline.value)
const preview = computed(() => props.modelValue.slice(0, 260) || '尚未填写')
let editor: EditorViewType | undefined

/** 使用当前表单值创建编辑器；取消或卸载后销毁历史与草稿。 */
function open() {
  if (props.disabled) return
  epoch++
  ready.value = false
  loadError.value = ''
  baseline.value = props.modelValue
  draft.value = props.modelValue
  opened.value = true
}
/** 对话框完成挂载后创建视图，避免对不存在的元素初始化。 */
async function mountEditor() {
  const current = ++epoch
  ready.value = false
  loadError.value = ''
  try {
    await nextTick()
    const [{basicSetup}, {EditorState}, {EditorView, keymap}, {indentWithTab}, extensions] = await Promise.all([
      import('codemirror'), import('@codemirror/state'), import('@codemirror/view'), import('@codemirror/commands'),
      mode.value?.extensions() ?? []
    ])
    if (current !== epoch || !opened.value || !host.value) return
    editor?.destroy()
    editor = new EditorView({parent: host.value, state: EditorState.create({
      doc: draft.value,
      extensions: [basicSetup, keymap.of([indentWithTab]), EditorView.lineWrapping,
        EditorView.contentAttributes.of({'aria-label': props.label}),
        EditorView.theme({'&': {height: 'min(60vh, 650px)', fontSize: '14px'}, '.cm-scroller': {overflow: 'auto'}}),
        ...extensions,
        EditorView.updateListener.of(update => { if (update.docChanged) draft.value = update.state.doc.toString() })]
    })})
    editor.focus()
    ready.value = true
  } catch {
    if (current === epoch && opened.value) loadError.value = '编辑器资源加载失败，请重试或刷新页面。'
  }
}
/** 统一格式化操作进入撤销历史，不直接覆盖父级值。 */
function format() {
  if (issue.value || !mode.value?.format || !editor) return
  editor.dispatch({changes: {from: 0, to: editor.state.doc.length, insert: mode.value.format(draft.value)}})
}
function apply() {
  if (props.disabled || !ready.value || issue.value || changedOutside.value) return
  emit('update:modelValue', draft.value)
  opened.value = false
}
function dispose() {
  epoch++
  ready.value = false
  editor?.destroy()
  editor = undefined
  draft.value = ''
  baseline.value = ''
}
watch(() => props.disabled, value => { if (value) opened.value = false })
onBeforeUnmount(dispose)
</script>

<template>
  <div class="value-editor-input">
    <pre class="value-editor-preview">{{ preview }}{{ modelValue.length > 260 ? '…' : '' }}</pre>
    <ElButton :disabled="disabled" @click="open">{{ kind === 'JSON' ? '编辑 JSON' : '展开编辑' }}</ElButton>
    <ElDialog v-model="opened" :title="label" width="min(1000px, 96vw)" append-to-body :close-on-click-modal="false" destroy-on-close @opened="mountEditor" @closed="dispose">
      <div class="value-editor-toolbar"><span class="small muted">{{ mode?.name ?? kind }} · {{ draft.length }} 字符 · 支持撤销、查找和 Tab 缩进</span><ElButton v-if="mode?.format" :disabled="!!issue" @click="format">格式化</ElButton></div>
      <div ref="host" class="value-editor-surface"></div>
      <ElAlert v-if="loadError" :title="loadError" type="error" :closable="false" />
      <ElButton v-if="loadError" @click="mountEditor">重试加载</ElButton>
      <p v-else-if="!ready" class="small muted">正在加载编辑器…</p>
      <ElAlert v-if="issue" :title="issue" type="error" :closable="false" />
      <ElAlert v-if="changedOutside" title="原值已变化，请取消后重新打开，避免覆盖新值。" type="warning" :closable="false" />
      <template #footer><ElButton @click="opened = false">取消</ElButton><ElButton type="primary" :disabled="disabled || !ready || !!issue || changedOutside" @click="apply">确认内容</ElButton></template>
    </ElDialog>
  </div>
</template>
