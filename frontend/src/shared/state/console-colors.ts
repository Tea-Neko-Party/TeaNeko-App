export interface ConsoleColors { background: string; foreground: string; accent: string }

// 2026/11/04：只接受完整十六进制颜色，不把任意存储字符串插入 CSS。
export const validColor = (value: unknown): value is string => typeof value === 'string' && /^#[0-9a-f]{6}$/i.test(value)

export function parseColors(value: string | null): ConsoleColors | undefined {
  if (!value) return undefined
  let parsed: unknown
  try { parsed = JSON.parse(value) }
  catch { return undefined }
  if (!parsed || typeof parsed !== 'object') return undefined
  const palette = parsed as Partial<ConsoleColors>
  return validColor(palette.background) && validColor(palette.foreground) && validColor(palette.accent)
    ? {background: palette.background, foreground: palette.foreground, accent: palette.accent} : undefined
}

/** 2026/11/04：按通道混色派生面板、边框和悬停色，保持自定义配色在组件库中一致。 */
function mix(base: string, color: string, amount: number): string {
  return '#' + [1, 3, 5].map(index => Math.round(parseInt(base.slice(index, index + 2), 16) * (1 - amount)
    + parseInt(color.slice(index, index + 2), 16) * amount).toString(16).padStart(2, '0')).join('')
}

export function luminance(color: string): number {
  const channels = [1, 3, 5].map(index => {
    const channel = parseInt(color.slice(index, index + 2), 16) / 255
    return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4
  })
  return channels[0]! * 0.2126 + channels[1]! * 0.7152 + channels[2]! * 0.0722
}

export function colorVariables(colors: ConsoleColors): Record<string, string> {
  const {background, foreground, accent} = colors
  const contrast = luminance(accent) > 0.179 ? '#000000' : '#ffffff'
  return {
    '--canvas': background, '--ink': foreground, '--accent': accent,
    '--surface': mix(background, foreground, 0.025), '--surface-soft': mix(background, foreground, 0.06),
    '--muted': mix(background, foreground, 0.68), '--line': mix(background, foreground, 0.16),
    '--accent-soft': mix(background, accent, 0.12), '--accent-contrast': contrast,
    '--el-color-primary-light-3': mix(background, accent, 0.65),
    '--el-color-primary-light-5': mix(background, accent, 0.45),
    '--el-color-primary-light-7': mix(background, accent, 0.25),
    '--el-color-primary-light-8': mix(background, accent, 0.16),
    '--el-color-primary-dark-2': mix(accent, contrast, 0.15),
  }
}

export const customColorKeys = Object.keys(colorVariables({background: '#ffffff', foreground: '#000000', accent: '#287b5e'}))
