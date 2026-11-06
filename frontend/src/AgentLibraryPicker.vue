<script setup lang="ts">
// 2026/11/06：空间与画像共用持久目录，不要求手填 UUID；空页带游标时仍可继续。
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {ElAlert, ElButton, ElEmpty, ElInput} from 'element-plus'
import {ApiError, request} from './api'
import RefreshButton from './RefreshButton.vue'

export interface LibraryItem {id: string; name: string; detail: string; generation: number; revision: number; readOnly: boolean; mappings: {client: string; platformId: string; nickname: string}[]}
interface Page {items: LibraryItem[]; next: string}
const props = withDefaults(defineProps<{kind: 'memory' | 'personality' | 'profiles'; namespace?: string; generation?: number; disabled?: boolean}>(), {namespace: '', generation: -1})
const emit = defineEmits<{select: [value: LibraryItem]; expired: []}>()
const items = ref<LibraryItem[]>([])
const next = ref('')
const search = ref('')
const busy = ref(false)
const error = ref('')
let epoch = 0
const visible = computed(() => items.value.filter(value => JSON.stringify(value).toLowerCase().includes(search.value.toLowerCase())))
async function load(more = false) {
  const ticket = ++epoch
  if (props.kind === 'profiles' && (!props.namespace || props.generation < 0)) { items.value = []; next.value = ''; busy.value = false; return }
  busy.value = true
  error.value = ''
  try {
    const value = await request<Page>('management/teaneko.agent:library', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({kind: props.kind, after: more ? next.value : '', namespace: props.namespace, generation: props.generation})})
    if (ticket !== epoch) return
    const merged = new Map((more ? items.value : []).map(item => [item.id, item]))
    value.items.forEach(item => merged.set(item.id, item))
    items.value = [...merged.values()]
    next.value = value.next
  } catch (failure) {
    if (ticket !== epoch) return
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
    error.value = failure instanceof ApiError && failure.status === 403
      ? '请在本地 Web 安全配置的 management-resources 中开放 teaneko.agent:library 及对应空间资源。'
      : failure instanceof Error ? failure.message : '持久目录读取失败。'
  } finally { if (ticket === epoch) busy.value = false }
}
watch(() => [props.kind, props.namespace, props.generation], () => { items.value = []; next.value = ''; void load() }, {immediate: true})
onBeforeUnmount(() => epoch++)
</script>

<template>
  <section class="library-picker">
    <div class="toolbar"><ElInput v-model="search" placeholder="筛选已加载的名称、标识或平台号码" clearable aria-label="筛选持久目录" /><RefreshButton :disabled="busy || disabled" @click="load()" label="重新读取目录" /></div>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <div class="agent-target-list">
      <button v-for="item in visible" :key="item.id" class="target-button library-card" :disabled="busy || disabled" @click="emit('select', item)">
        <strong>{{ item.name }}</strong><span class="small muted">{{ item.detail }}<template v-if="item.revision >= 0"> · 版本 {{ item.revision }}</template></span>
        <span v-for="mapping in item.mappings" :key="mapping.client + ':' + mapping.platformId" class="small">{{ mapping.client }} · {{ mapping.nickname || mapping.platformId }}<template v-if="mapping.nickname">（{{ mapping.platformId }}）</template></span>
        <span v-if="item.name !== item.id" class="small muted">{{ item.id }}</span>
      </button>
    </div>
    <ElEmpty v-if="!busy && !visible.length && !error" :description="next ? '本页没有匹配项目，可继续读取后页' : '没有已存项目'" :image-size="60" />
    <ElButton v-if="next" :disabled="busy || disabled" @click="load(true)">继续加载目录</ElButton>
  </section>
</template>
