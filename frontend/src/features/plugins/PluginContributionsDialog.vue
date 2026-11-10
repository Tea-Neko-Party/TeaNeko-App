<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {ElDialog, ElEmpty} from 'element-plus'
import type {PluginItem} from '../../shared/plugins/plugin-console'

// 2026/11/10：安装声明独立展示，停用后仍可查阅；分组由贡献 kind 驱动，不增加插件特例。
const props = defineProps<{plugin?: PluginItem}>()
const emit = defineEmits<{close: []}>()
const selected = ref('')
const content = ref<HTMLElement>()
const kinds: Record<string, string> = {component: 'Agent 组件', system: 'Agent 系统', tool: 'Agent 工具',
  feature: 'Agent 能力', command: '聊天命令', 'file-config': '本地配置', 'chat-config': '聊天配置',
  task: '后台任务', page: '独立页面', client: '客户端'}
const groups = computed(() => [...new Set(props.plugin?.contributions.map(value => value.kind) ?? [])]
  .map(kind => ({kind, title: kinds[kind] || kind, values: props.plugin!.contributions.filter(value => value.kind === kind)})))
watch(() => props.plugin?.id, () => { selected.value = groups.value[0]?.kind ?? '' })
function jump(kind: string) {
  selected.value = kind
  const section = [...content.value?.querySelectorAll<HTMLElement>('[data-kind]') ?? []]
    .find(element => element.dataset.kind === kind)
  section?.scrollIntoView({behavior: 'smooth', block: 'start'})
  section?.focus({preventScroll: true})
}
</script>

<template>
  <ElDialog :model-value="!!plugin" :title="`${plugin?.name ?? ''} · 已安装功能`" width="min(920px, 94vw)"
    class="plugin-contributions-dialog" @close="emit('close')">
    <div v-if="groups.length" class="contributions-layout">
      <nav class="contributions-nav" aria-label="功能领域快速导航">
        <button v-for="group in groups" :key="group.kind" :class="{selected: selected === group.kind}"
          :aria-current="selected === group.kind ? 'location' : undefined" @click="jump(group.kind)">
          {{ group.title }}<span>{{ group.values.length }}</span>
        </button>
      </nav>
      <div ref="content" class="contributions-content">
        <section v-for="group in groups" :key="group.kind" :data-kind="group.kind" tabindex="-1" class="contribution-group">
          <h3>{{ group.title }}<span>{{ group.values.length }} 项</span></h3>
          <ul><li v-for="value in group.values" :key="value.id">
            <strong>{{ value.name }}</strong><code v-if="value.name !== value.id">{{ value.id }}</code>
            <p v-if="value.description">{{ value.description }}</p>
          </li></ul>
        </section>
      </div>
    </div>
    <ElEmpty v-else description="此插件未声明可展示的功能说明" />
  </ElDialog>
</template>

<style scoped>
.contributions-layout {display:grid; grid-template-columns:164px minmax(0,1fr); gap:20px}
.contributions-nav {display:flex; flex-direction:column; gap:6px; max-height:65vh; overflow:auto}
.contributions-nav button {display:flex; align-items:center; justify-content:space-between; gap:8px; padding:11px 12px; border:0; border-radius:8px; background:var(--canvas); color:var(--ink); text-align:left}
.contributions-nav button.selected {background:var(--accent-soft); color:var(--accent); font-weight:600}
.contributions-nav span {font-size:12px; color:var(--muted)}
.contributions-content {max-height:65vh; overflow-y:auto; padding-right:8px; scroll-behavior:smooth}
.contribution-group {border:1px solid var(--line); border-radius:10px; overflow:hidden; margin-bottom:20px; scroll-margin:4px}
.contribution-group h3 {display:flex; justify-content:space-between; align-items:center; margin:0; padding:12px 16px; color:var(--accent); background:var(--accent-soft); font-size:16px; border-bottom:1px solid var(--line)}
.contribution-group h3 span {font-size:12px; font-weight:400}
.contribution-group ul {list-style:none; padding:0 16px; margin:0}
.contribution-group li {padding:14px 0; overflow-wrap:anywhere}
.contribution-group li + li {border-top:1px solid var(--line)}
.contribution-group strong {display:block; color:var(--ink)}
.contribution-group code {font-size:12px; color:var(--muted)}
.contribution-group p {white-space:pre-line; margin:6px 0 0; color:var(--muted); line-height:1.7}
@media(max-width:600px) {
  .contributions-layout {grid-template-columns:minmax(0,1fr); gap:12px}
  .contributions-nav {flex-direction:row; overflow-x:auto}
  .contributions-nav button {flex-shrink:0}
  .contributions-content {max-height:56vh}
}
</style>
