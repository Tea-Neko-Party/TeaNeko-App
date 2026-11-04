<script setup lang="ts">
import {computed} from 'vue'
import ConsoleIcon from './ConsoleIcon.vue'
import {useConsolePreferences} from './console-preferences'

defineProps<{showMode?: boolean}>()
const {advanced, theme, setAdvanced, cycleTheme} = useConsolePreferences()
const themeLabel = computed(() => ({system: '跟随系统', light: '浅色', dark: '深色'})[theme.value])
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
  </div>
</template>
