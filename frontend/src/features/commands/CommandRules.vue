<script setup lang="ts">
// 2026/11/04：范围、成员与客户端使用固定分组底色；客户端文字颜色由名称稳定派生。
defineProps<{scope?: string; permission?: string; clients?: string[]}>()
const scopes: Record<string, string> = {ALL: '所有聊天', GROUP: '群聊', PRIVATE: '私聊', OTHER: '其他来源'}
const permissions: Record<string, string> = {ALL: '所有成员', MEMBER: '普通成员及以上', ADMIN: '管理员及以上', OWNER: '群主及以上', DEBUG: '调试权限', DEFAULT: '不可用'}
const clientLabels: Record<string, string> = {ICommandClient: '所有客户端', OnebotTeaNekoClient: 'OneBot', WebTeaNekoClient: '网页聊天'}
function clientColor(value: string) {
  let hash = 0
  for (const character of value) hash = (Math.imul(hash, 31) + character.charCodeAt(0)) | 0
  return `hsl(${(hash >>> 0) % 360} 58% 32%)`
}
</script>

<template>
  <div class="command-rules">
    <span v-if="scope" class="command-rule rule-scope"><small>范围</small><b :class="'value-' + scope.toLowerCase()">{{ scopes[scope] ?? scope }}</b></span>
    <span v-if="permission" class="command-rule rule-member"><small>成员</small><b :class="'value-' + permission.toLowerCase()">{{ permissions[permission] ?? permission }}</b></span>
    <span v-if="clients?.length" class="command-rule rule-client"><small>客户端</small><b v-for="client in clients" :key="client" :style="{color: clientColor(client)}">{{ clientLabels[client] ?? client }}</b></span>
  </div>
</template>
