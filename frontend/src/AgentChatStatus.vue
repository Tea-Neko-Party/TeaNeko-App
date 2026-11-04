<script setup lang="ts">
// 2026/11/04：读取既有授权活动窗口，状态观察独立于聊天输入及发送，不触发 Agent。
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {ApiError, request} from './api'
import type {AgentTarget} from './AgentWorkspaceView.vue'

interface Event {sequence: number; requestId: string; activity: string; stage: string; state: string; at: string}
interface Page {instance: string; reset: boolean; next: number; events: Event[]}
const props = defineProps<{target: AgentTarget; active: boolean; enabled: boolean}>()
const emit = defineEmits<{expired: []}>()
const events = ref<Event[]>([])
const available = ref(true)
let instance = ''
let cursor = 0
let timer: ReturnType<typeof setTimeout> | undefined
let controller: AbortController | undefined
let epoch = 0
const states: Record<string, string> = {
  SUCCESS: '已生成回复', FAILED: '处理失败', CANCELLED: '已取消', TIMED_OUT: '处理超时',
  CONFIGURATION_ERROR: '模型或上下文配置不可用', INVALID_INPUT: '输入不可用', SKIPPED: '本轮已跳过',
  STALE_CONVERSATION: '会话已变化', INPUT_TOO_LARGE: '上下文超出容量', BUDGET_EXCEEDED: '本轮预算已用尽',
  EMPTY_OUTPUT: '模型未返回内容', TRUNCATED: '回复被截断', REFUSED: '模型拒绝回复', INVALID_RESPONSE: '模型返回格式不可用',
  BUSY: 'Agent 正忙', QUEUE_TIMED_OUT: '排队超时', REENTRANT: '重复嵌套调用已拒绝',
  DUPLICATE_REQUEST: '重复请求未执行', REQUEST_CONFLICT: '请求编号冲突', REPLAY_UNAVAILABLE: '历史结果不可回放'
}
const stages: Record<string, string> = {'agent.admit': '检查聊天配置', 'agent.prompt': '整理上下文', 'agent.interact': '正在思考与调用工具', 'agent.complete': '整理回复', 'agent.plugins.prepare': '准备扩展', 'agent.plugins.review': '检查回复'}
const runs = computed(() => {
  const values = new Map<string, Event>()
  for (const event of events.value) if (['COMMAND', 'AUTOMATIC_REPLY', 'SERVICE', 'PROACTIVE', 'SCHEDULED'].includes(event.activity)) values.set(event.requestId, event)
  return [...values.values()]
})
// 2026/11/04：轮次只有准备和启动属于在途，预算耗尽等结束状态不能继续显示处理中。
const processing = computed(() => runs.value.filter(event => event.stage !== 'agent.turn' || ['PREPARED', 'STARTED'].includes(event.state)))
const status = computed(() => {
  if (!props.enabled) return 'Agent 未启用'
  if (!available.value) return 'Agent 状态暂不可用'
  const running = processing.value.at(-1)
  if (running) return (stages[running.stage] ?? '正在处理') + (processing.value.length > 1 ? ` · ${processing.value.length} 个请求` : '')
  const latest = runs.value.at(-1)
  return latest ? states[latest.state] ?? '本轮已结束' : 'Agent 等待消息'
})
async function poll(current: number) {
  if (current !== epoch || !props.active) return
  const abort = new AbortController()
  controller = abort
  try {
    if (!document.hidden && props.enabled) {
      const target = props.target
      const path = `clients/${encodeURIComponent(target.client)}/accounts/${encodeURIComponent(target.account)}/targets/${encodeURIComponent(target.target)}/management/teaneko.agent:activity`
      const result = await request<Page>(path, {method: 'POST', signal: abort.signal, headers: {'Content-Type': 'application/json'}, body: JSON.stringify({after: cursor, limit: 100})})
      if (current !== epoch) return
      if (result.reset || instance !== result.instance) events.value = []
      instance = result.instance
      cursor = result.next
      const merged = new Map(events.value.map(event => [event.sequence, event]))
      result.events.forEach(event => merged.set(event.sequence, event))
      events.value = [...merged.values()].slice(-300)
      available.value = true
    }
  } catch (failure) {
    if (current !== epoch || abort.signal.aborted) return
    available.value = false
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
  } finally {
    if (current === epoch && props.active) timer = setTimeout(() => void poll(current), 1500)
  }
}
watch(() => [props.target.client, props.target.account, props.target.target, props.active], () => {
  epoch++
  clearTimeout(timer)
  controller?.abort()
  events.value = []
  instance = ''
  cursor = 0
  if (props.active) void poll(epoch)
}, {immediate: true})
onBeforeUnmount(() => { epoch++; controller?.abort(); clearTimeout(timer) })
</script>

<template><span class="agent-chat-status" :class="{processing: enabled && processing.length > 0, unavailable: !available}" role="status" aria-live="polite"><i aria-hidden="true" />{{ status }}</span></template>
