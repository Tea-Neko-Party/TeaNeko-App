import type {InjectionKey} from 'vue'
import {computed, inject, onScopeDispose, provide, ref, watchEffect} from 'vue'
import type {ConsoleColors} from './console-colors'
import {colorVariables, customColorKeys, luminance, parseColors} from './console-colors'

type Theme = 'system' | 'light' | 'dark'

// 2026/11/04：界面偏好与登录凭据分开保存，受限浏览器仍可在内存中切换。
function read(key: string): string | null {
  try {
    return window.localStorage.getItem(`teaneko.console.${key}`)
  } catch {
    return null
  }
}

function write(key: string, value: string): void {
  try {
    window.localStorage.setItem(`teaneko.console.${key}`, value)
  } catch {
    // 2026/11/04：存储不可用只影响下次访问的偏好，不影响本次操作。
  }
}

function createPreferences() {
  const advanced = ref(read('mode') === 'advanced')
  const storedTheme = read('theme')
  const theme = ref<Theme>(storedTheme === 'light' || storedTheme === 'dark' ? storedTheme : 'system')
  const collapsed = ref(read('sidebar') === 'collapsed')
  // 2026/11/04：自定义配色仅保存界面颜色，关闭或重置后继续使用原主题。
  const colors = ref<ConsoleColors | undefined>(parseColors(read('colors')))
  const media = window.matchMedia('(prefers-color-scheme: dark)')
  const systemDark = ref(media.matches)
  const updateSystem = (event: MediaQueryListEvent) => { systemDark.value = event.matches }
  media.addEventListener('change', updateSystem)
  onScopeDispose(() => media.removeEventListener('change', updateSystem))

  const dark = computed(() => theme.value === 'dark' || theme.value === 'system' && systemDark.value)
  watchEffect(() => {
    document.documentElement.classList.toggle('dark', dark.value)
    document.documentElement.style.colorScheme = (colors.value ? luminance(colors.value.background) < 0.179 : dark.value) ? 'dark' : 'light'
    const variables = colors.value ? colorVariables(colors.value) : {}
    for (const key of customColorKeys) {
      const value = variables[key]
      if (value) document.documentElement.style.setProperty(key, value)
      else document.documentElement.style.removeProperty(key)
    }
  })

  function setAdvanced(value: boolean) {
    advanced.value = value
    write('mode', value ? 'advanced' : 'simple')
  }

  function cycleTheme() {
    theme.value = theme.value === 'system' ? 'light' : theme.value === 'light' ? 'dark' : 'system'
    write('theme', theme.value)
  }

  function toggleSidebar() {
    collapsed.value = !collapsed.value
    write('sidebar', collapsed.value ? 'collapsed' : 'expanded')
  }

  function setColors(value?: ConsoleColors) {
    colors.value = value ? {...value} : undefined
    write('colors', value ? JSON.stringify(value) : '')
  }

  return {advanced, theme, dark, collapsed, colors, setColors, setAdvanced, cycleTheme, toggleSidebar}
}

const key: InjectionKey<ReturnType<typeof createPreferences>> = Symbol('控制台界面偏好')

export function provideConsolePreferences() {
  const preferences = createPreferences()
  provide(key, preferences)
  return preferences
}

export function useConsolePreferences() {
  const preferences = inject(key)
  if (!preferences) throw new Error('控制台界面偏好尚未装配。')
  return preferences
}
