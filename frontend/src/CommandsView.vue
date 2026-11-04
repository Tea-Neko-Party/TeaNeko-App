<script setup lang="ts">
// 2026/11/04：说明页面读取实际命令目录，查看说明不执行命令或取得其他成员权限。
import {computed, onMounted, ref} from 'vue'
import {ElAlert, ElCard, ElEmpty, ElInput, ElTag} from 'element-plus'
import {ApiError, request} from './api'
import RefreshButton from './RefreshButton.vue'

interface Entry {command: string; aliases: string[]; subcommand: string; subAliases: string[]; insertion: string; usage: string; introduction: string; description: string; parameters: {name: string; type: string; description: string; optional: boolean; defaultValue: string}[]; permission: string; scope: string; clients: string[]; regex: boolean; management: boolean}
const emit = defineEmits<{expired: []}>()
const entries = ref<Entry[]>([])
const query = ref('')
const selected = ref('')
const busy = ref(false)
const error = ref('')
const matching = computed(() => entries.value.filter(item => `${item.command} ${item.aliases.join(' ')} ${item.usage} ${item.description} ${item.introduction}`.toLowerCase().includes(query.value.toLowerCase())))
const commands = computed(() => [...new Set(matching.value.map(item => item.command))])
const current = computed(() => matching.value.filter(item => item.command === (commands.value.includes(selected.value) ? selected.value : commands.value[0])))
const permissions: Record<string, string> = {ALL: '所有成员', MEMBER: '普通成员', ADMIN: '管理员', OWNER: '群主 / 所有者', DEBUG: '调试权限'}
const scopes: Record<string, string> = {ALL: '所有聊天', GROUP: '群聊', PRIVATE: '私聊', OTHER: '其他来源'}
const clients: Record<string, string> = {ICommandClient: '所有客户端', OnebotTeaNekoClient: 'OneBot', WebTeaNekoClient: '网页聊天'}

async function load() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try { entries.value = await request<Entry[]>('commands') }
  catch (failure) {
    error.value = failure instanceof Error ? failure.message : '指令目录暂不可用。'
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
  } finally { busy.value = false }
}
onMounted(load)
</script>

<template>
  <div class="commands-page">
    <div class="editor-heading"><ElInput v-model="query" clearable placeholder="搜索指令、用法或说明" aria-label="搜索指令" /><RefreshButton :loading="busy" label="刷新指令一览" @click="load" /></div>
    <p class="small muted">这里显示实际已启用的命令。每次执行仍会检查客户端、聊天范围和操作者权限。</p>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <div class="settings-columns">
      <ElCard shadow="never" class="target-list"><button v-for="command in commands" :key="command" class="target-button" :class="{selected: current[0]?.command === command}" @click="selected = command"><strong>{{ command }}</strong></button><ElEmpty v-if="!commands.length && !busy" description="没有匹配的指令" :image-size="60" /></ElCard>
      <div v-if="current.length" class="settings-editor"><ElCard shadow="never"><h2>{{ current[0]!.command }}</h2><p class="config-description">{{ current[0]!.introduction || '该指令未提供总体说明。' }}</p><p class="small muted">别名：{{ current[0]!.aliases.join('、') }}</p></ElCard>
        <ElCard v-for="item in current" :key="item.usage" shadow="never" class="command-detail"><h3><code>{{ item.usage }}</code></h3><p class="config-description">{{ item.description || '该方法未提供额外说明。' }}</p><div class="editor-actions"><ElTag>{{ permissions[item.permission] ?? item.permission }}</ElTag><ElTag type="info">{{ scopes[item.scope] ?? item.scope }}</ElTag><ElTag v-if="item.management" type="warning">仅管理来源</ElTag><ElTag v-if="item.regex" type="info">正则匹配</ElTag></div><p class="small muted">客户端：{{ item.clients.map(value => clients[value] ?? value).join('、') }}<span v-if="item.subAliases.length > 1"> · 子指令别名：{{ item.subAliases.join('、') }}</span></p>
          <dl v-if="item.parameters.length" class="command-parameters"><template v-for="parameter in item.parameters" :key="parameter.name"><dt>{{ parameter.name }} <span class="small muted">{{ parameter.optional ? '可选' : '必填' }} · {{ parameter.type }}</span></dt><dd>{{ parameter.description || '未提供参数说明' }}<span v-if="parameter.optional" class="small muted"> · 默认：{{ parameter.defaultValue || '空值' }}</span></dd></template></dl>
        </ElCard>
      </div>
    </div>
  </div>
</template>
