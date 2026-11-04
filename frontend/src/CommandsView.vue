<script setup lang="ts">
// 2026/11/04：说明页面读取实际命令目录，查看说明不执行命令或取得其他成员权限。
import {computed, onMounted, ref} from 'vue'
import {ElAlert, ElCard, ElEmpty, ElInput, ElOption, ElSelect, ElTag} from 'element-plus'
import {ApiError, request} from './api'
import RefreshButton from './RefreshButton.vue'
import CommandRules from './CommandRules.vue'

interface Entry {command: string; displayName: string; aliases: string[]; subcommand: string; subAliases: string[]; insertion: string; usage: string; introduction: string; description: string; parameters: {name: string; type: string; description: string; optional: boolean; defaultValue: string}[]; permission: string; scope: string; clients: string[]; regex: boolean; management: boolean; defaultPermission: string; defaultScope: string; defaultPackages: string[]; permissionPackages: string[]; defaultClients: string[]}
const emit = defineEmits<{expired: []}>()
const entries = ref<Entry[]>([])
const query = ref('')
const selected = ref('')
const busy = ref(false)
const error = ref('')
// 2026/11/04：按方法有效规则筛选，再聚合主指令；子指令放宽范围时也能发现。
const scopeFilter = ref('')
const memberFilter = ref('')
const clientFilter = ref('')
const levels: Record<string, number> = {DEFAULT: 0, DEBUG: 1, OWNER: 2, ADMIN: 3, MEMBER: 4, ALL: 5}
function matchesRules(scope: string, permission: string, supported: string[]) {
  return (!scopeFilter.value || scope === 'ALL' || scope === scopeFilter.value)
    && (!memberFilter.value || (levels[permission] ?? 0) >= (levels[memberFilter.value] ?? 5))
    && (!clientFilter.value || supported.includes('ICommandClient') || supported.includes(clientFilter.value))
}
const matching = computed(() => entries.value.filter(item => `${item.command} ${item.displayName} ${item.aliases.join(' ')} ${item.usage} ${item.description} ${item.introduction}`.toLowerCase().includes(query.value.toLowerCase())
  && (matchesRules(item.scope, item.permission, item.clients) || matchesRules(item.defaultScope, item.defaultPermission, item.defaultClients))))
const commands = computed(() => [...new Set(matching.value.map(item => item.command))])
const current = computed(() => entries.value.filter(item => item.command === (commands.value.includes(selected.value) ? selected.value : commands.value[0])))
const permissions: Record<string, string> = {ALL: '所有成员', MEMBER: '普通成员', ADMIN: '管理员', OWNER: '群主 / 所有者', DEBUG: '调试权限'}
const scopes: Record<string, string> = {ALL: '所有聊天', GROUP: '群聊', PRIVATE: '私聊', OTHER: '其他来源'}
const clients: Record<string, string> = {ICommandClient: '所有客户端', OnebotTeaNekoClient: 'OneBot', WebTeaNekoClient: '网页聊天'}
const clientOptions = computed(() => [...new Set([...Object.keys(clients), ...entries.value.flatMap(item => item.clients)])].filter(value => value !== 'ICommandClient'))
const differentPackages = (item: Entry) => JSON.stringify([...item.permissionPackages].sort()) !== JSON.stringify([...item.defaultPackages].sort())
const differentClients = (item: Entry) => JSON.stringify([...item.clients].sort()) !== JSON.stringify([...item.defaultClients].sort())

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
    <div class="command-filters"><ElSelect v-model="scopeFilter" clearable placeholder="全部聊天范围" aria-label="筛选聊天范围"><ElOption v-for="(label, value) in scopes" :key="value" :label="label" :value="value" /></ElSelect><ElSelect v-model="memberFilter" clearable placeholder="全部成员要求" aria-label="筛选可用成员"><ElOption v-for="(label, value) in permissions" :key="value" :label="label" :value="value" /></ElSelect><ElSelect v-model="clientFilter" clearable placeholder="全部客户端" aria-label="筛选客户端"><ElOption v-for="value in clientOptions" :key="value" :label="clients[value] ?? value" :value="value" /></ElSelect></div>
    <p class="small muted">这里显示实际已启用的命令。每次执行仍会检查客户端、聊天范围和操作者权限。</p>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <div class="settings-columns">
      <!-- 2026/11/04：前缀和中文名称并列，说明按职责分组，交替底色帮助扫描。 -->
      <ElCard shadow="never" class="target-list command-list"><button v-for="(command, index) in commands" :key="command" class="target-button" :class="{selected: current[0]?.command === command, alternate: index % 2 === 1}" @click="selected = command"><strong>{{ command }}</strong><span>{{ matching.find(item => item.command === command)?.displayName || command }}</span></button><ElEmpty v-if="!commands.length && !busy" description="没有匹配的指令" :image-size="60" /></ElCard>
      <div v-if="current.length" class="settings-editor">
        <ElCard shadow="never" class="command-general"><span class="small muted">指令总览</span><div class="command-title"><h2>{{ current[0]!.displayName || current[0]!.command }}</h2><code>{{ current[0]!.aliases.join('、') }}</code></div><CommandRules :scope="current[0]!.defaultScope" :permission="current[0]!.defaultPermission" :clients="current[0]!.defaultClients" /><div class="command-packages"><small>权限组</small><ElTag v-for="value in current[0]!.defaultPackages" :key="value" type="warning">{{ value }}</ElTag><span v-if="!current[0]!.defaultPackages.length" class="small muted">无额外权限组</span></div><p class="command-introduction">{{ current[0]!.introduction || '该指令未提供总体说明。' }}</p></ElCard>
        <section v-for="group in [{title: '默认指令', items: current.filter(item => !item.subcommand)}, {title: '子指令', items: current.filter(item => item.subcommand)}]" v-show="group.items.length" :key="group.title" class="command-section">
          <h2>{{ group.title }}</h2>
          <ElCard v-for="(item, index) in group.items" :key="item.usage" shadow="never" class="command-detail" :class="{alternate: index % 2 === 1}">
            <h3>{{ item.subcommand || '直接调用' }}</h3><code>{{ item.usage }}</code>
            <CommandRules :scope="item.scope !== item.defaultScope ? item.scope : undefined" :permission="item.permission !== item.defaultPermission ? item.permission : undefined" :clients="differentClients(item) ? item.clients : undefined" />
            <div v-if="differentPackages(item)" class="command-packages"><small>特殊权限组</small><ElTag v-for="value in item.permissionPackages" :key="value" type="warning">{{ value }}</ElTag></div>
            <p class="command-introduction">{{ item.description || '该方法未提供额外说明。' }}</p>
            <div v-if="item.management || item.regex || item.subAliases.length > 1" class="editor-actions"><ElTag v-if="item.management" type="warning">仅管理来源</ElTag><ElTag v-if="item.regex" type="info">正则匹配</ElTag><span v-if="item.subAliases.length > 1" class="small muted">别名：{{ item.subAliases.join('、') }}</span></div>
            <dl v-if="item.parameters.length" class="command-parameters"><template v-for="parameter in item.parameters" :key="parameter.name"><dt>{{ parameter.name }} <span class="small muted">{{ parameter.optional ? '可选' : '必填' }} · {{ parameter.type }}</span></dt><dd>{{ parameter.description || '未提供参数说明' }}<span v-if="parameter.optional" class="small muted"> · 默认：{{ parameter.defaultValue || '空值' }}</span></dd></template></dl>
          </ElCard>
        </section>
      </div>
    </div>
  </div>
</template>
