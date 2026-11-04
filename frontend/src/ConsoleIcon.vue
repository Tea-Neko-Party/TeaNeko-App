<script setup lang="ts">
// 2026/11/04：用本地矢量描边统一导航图标，不引入额外图标依赖。
const paths = {
  overview: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  platforms: 'M8 3h8v5H8z M3 16h6v5H3z M15 16h6v5h-6z M12 8v5 M6 16v-3h12v3',
  settings: 'M4 6h16 M4 12h16 M4 18h16 M8 3v6 M16 9v6 M10 15v6',
  agent: 'M5 7h14v13H5z M12 3v4 M2 11v5 M22 11v5 M9 11v2 M15 11v2 M9 17h6',
  files: 'M4 5h6l2 3h8v12H4z',
  runtime: 'M12 7v5l3 2 M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
  console: 'm5 7 5 5-5 5 M13 17h6',
  chat: 'M21 4H3v13h5l4 4 4-4h5z M7 9h10 M7 13h6',
  // 2026/11/04：聊天类型使用独立图形，配合文字区分群聊、私聊与其他功能。
  group: 'M4 8a3 3 0 1 1 6 0 3 3 0 0 1-6 0 M14 8a3 3 0 1 1 6 0 3 3 0 0 1-6 0 M1 21v-2a6 6 0 0 1 12 0v2 M13 14a6 6 0 0 1 10 5v2',
  private: 'M3 8a4 4 0 1 1 8 0 4 4 0 0 1-8 0 M1 22v-2a6 6 0 0 1 12 0v2 M14 3h9v10h-3l-3 3v-3h-3z',
  other: 'M3 3h6v6H3z M15 3h6v6h-6z M3 15h6v6H3z M18 14v8 M14 18h8',
  session: 'M8 7a4 4 0 1 1 8 0 4 4 0 0 1-8 0 M4 21v-2a8 8 0 0 1 16 0v2',
  menu: 'M4 6h16 M4 12h16 M4 18h16',
  refresh: 'M20 7v5h-5 M4 17v-5h5 M6 6a8 8 0 0 1 14 6 M4 12a8 8 0 0 0 14 6',
  light: 'M8 12a4 4 0 1 1 8 0 4 4 0 0 1-8 0 M12 2v2 M12 20v2 M2 12h2 M20 12h2 M5 5l1 1 M18 18l1 1 M5 19l1-1 M18 6l1-1',
  dark: 'M20 15A9 9 0 0 1 9 4a9 9 0 1 0 11 11',
  system: 'M3 4h18v13H3z M8 21h8 M12 17v4',
  logout: 'M9 4H4v16h5 M10 12h11 m-4-4 4 4-4 4',
} as const
defineProps<{name: keyof typeof paths}>()
</script>

<template>
  <svg class="console-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
    stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="paths[name]" /></svg>
</template>
