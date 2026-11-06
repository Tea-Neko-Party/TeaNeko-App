<script setup lang="ts">
// 2026/11/06：空间与画像共用持久目录，不要求手填 UUID；空页带游标时仍可继续。
import {computed, ref} from 'vue'
import {ElAlert, ElEmpty, ElInput} from 'element-plus'
import type {LibraryItem} from './agent-library'
import {useAgentLibrary} from './agent-library'
import RefreshButton from './RefreshButton.vue'

export type {LibraryItem} from './agent-library'

const props = withDefaults(defineProps<{kind: 'memory' | 'personality' | 'profiles'; namespace?: string; generation?: number; disabled?: boolean}>(), {namespace: '', generation: -1})
const emit = defineEmits<{select: [value: LibraryItem]; expired: []}>()
const search = ref('')
const {items, busy, error, load} = useAgentLibrary(() => ({kind: props.kind, namespace: props.namespace, generation: props.generation}), () => emit('expired'))
const visible = computed(() => items.value.filter(value => JSON.stringify(value).toLowerCase().includes(search.value.toLowerCase())))
</script>

<template>
  <section class="library-picker">
    <div class="toolbar"><ElInput v-model="search" placeholder="搜索名称或平台号码" clearable aria-label="筛选持久目录" /><RefreshButton :loading="busy" :disabled="disabled" @click="load()" label="重新读取目录" /></div>
    <p v-if="busy" class="small muted" role="status">正在读取目录，已找到 {{ items.length }} 项…</p>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <div class="agent-target-list">
      <button v-for="item in visible" :key="item.id" class="target-button library-card" :disabled="disabled" @click="emit('select', item)">
        <strong>{{ item.name }}</strong><span class="small muted">{{ item.detail }}<template v-if="item.revision >= 0"> · 版本 {{ item.revision }}</template></span>
        <span v-for="mapping in item.mappings" :key="mapping.client + ':' + mapping.platformId" class="small">{{ mapping.client }} · {{ mapping.nickname || mapping.platformId }}<template v-if="mapping.nickname">（{{ mapping.platformId }}）</template></span>
        <span v-if="item.name !== item.id" class="small muted">{{ item.id }}</span>
      </button>
    </div>
    <ElEmpty v-if="!busy && !visible.length && !error" :description="search ? '没有匹配的项目' : kind === 'profiles' ? '这个空间还没有用户画像' : '没有已存项目'" :image-size="60" />
  </section>
</template>
