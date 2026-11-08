<script setup lang="ts">
// 2026/11/07：按功能目录引用共享能力与页面契约。
import {computed, ref, watch} from 'vue'
import {ElButton, ElDialog, ElInput} from 'element-plus'
import ConsoleIcon from './ConsoleIcon.vue'
import {useConsolePreferences} from '../state/console-preferences'
import type {ConsoleColors} from '../state/console-colors'
import {validColor} from '../state/console-colors'

defineProps<{showMode?: boolean}>()
const {advanced, theme, colors, setColors, setAdvanced, cycleTheme} = useConsolePreferences()
const themeLabel = computed(() => ({system: '跟随系统', light: '浅色', dark: '深色'})[theme.value])
// 2026/11/04：颜色设置先在预览中编辑，保存后应用到全站并保留在当前浏览器。
const opened = ref(false)
const draft = ref<ConsoleColors>({background: '#f5f3ed', foreground: '#29372f', accent: '#287b5e'})
const fields = [{key: 'background', label: '网页背景'}, {key: 'foreground', label: '前景文字'}, {key: 'accent', label: '强调色'}] as const
const valid = computed(() => Object.values(draft.value).every(validColor))
watch(opened, value => {
  if (!value) return
  const style = getComputedStyle(document.documentElement)
  draft.value = colors.value ? {...colors.value} : {
    background: style.getPropertyValue('--canvas').trim(),
    foreground: style.getPropertyValue('--ink').trim(),
    accent: style.getPropertyValue('--accent').trim(),
  }
})

function save() {
  if (!valid.value) return
  setColors(draft.value)
  opened.value = false
}

function reset() {
  setColors()
  opened.value = false
}
</script>

<template>
  <div class="appearance-controls">
    <div v-if="showMode" class="mode-switch" role="group" aria-label="界面模式">
      <button :aria-pressed="!advanced" @click="setAdvanced(false)">简洁</button>
      <button :aria-pressed="advanced" @click="setAdvanced(true)">高级</button>
    </div>
    <button class="icon-button" :aria-label="`切换主题，当前：${themeLabel}`" :title="`主题：${themeLabel}`" @click="cycleTheme">
      <ConsoleIcon :name="theme" />
    </button>
    <button class="icon-button" aria-label="外观设置" title="外观设置" @click="opened = true"><ConsoleIcon name="settings" /></button>
    <ElDialog v-model="opened" title="外观设置" width="min(520px, 94vw)" class="appearance-dialog">
      <div class="appearance-form">
        <p class="small muted">自定义配色优先于主题颜色，只保存在当前浏览器。恢复默认后继续跟随所选的浅色、深色或系统主题。</p>
        <label v-for="field in fields" :key="field.key">{{ field.label }}
          <div class="appearance-color-input"><input v-model="draft[field.key]" type="color" :aria-label="`${field.label}拾色器`" /><ElInput v-model="draft[field.key]" :aria-label="`${field.label}色值`" maxlength="7" placeholder="#RRGGBB" /></div>
        </label>
        <div v-if="valid" class="appearance-preview" :style="{background: draft.background, color: draft.foreground, borderColor: draft.accent}">
          <strong>配色预览</strong><p>网页背景与前景文字</p><span :style="{color: draft.accent}">强调色与选中状态</span>
        </div>
        <p v-else class="small muted">请填写完整的六位十六进制颜色，例如 #287b5e。</p>
      </div>
      <template #footer><ElButton @click="reset">恢复默认配色</ElButton><ElButton @click="opened = false">取消</ElButton><ElButton type="primary" :disabled="!valid" @click="save">保存配色</ElButton></template>
    </ElDialog>
  </div>
</template>
