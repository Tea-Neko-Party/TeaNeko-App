<script setup lang="ts">
// 2026/11/10：检测结果固定到已保存配置与当前页面代次，切换后不回填旧结果。
import {ref, watch} from 'vue'
import {ElAlert, ElButton} from 'element-plus'
import {ApiError} from '../../shared/api/client'
import {PluginRequestSuperseded, usePluginRequests} from '../../shared/plugins/plugin-console'
import {usePageDraft} from '../../shared/lifecycle/page-guards'

const props = defineProps<{endpoint: string; context?: Record<string, string>; disabled: boolean}>()
const emit = defineEmits<{expired: []}>()
const busy = ref(false)
usePageDraft(() => ({busy: busy.value}))
const result = ref<{status: string; reason: string}>()
const {request, capture} = usePluginRequests(() => JSON.stringify([props.context, props.disabled]))
watch(() => [props.context, props.disabled], () => {result.value = undefined}, {deep: true})
async function probe() {
  if (!props.context || props.disabled || busy.value) return
  const current = capture()
  busy.value = true
  result.value = undefined
  try { result.value = await request(props.endpoint, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(props.context)}) }
  catch (error) {
    if (!(error instanceof PluginRequestSuperseded) && current()) {
      if (error instanceof ApiError && error.status === 401) emit('expired')
      result.value = {status: 'FAILED', reason: error instanceof Error ? error.message : '识图检测未完成。'}
    }
  } finally {busy.value = false}
}
</script>
<template>
  <div class="model-vision-probe">
    <ElButton type="primary" plain :loading="busy" :disabled="disabled || !context" @click="probe">检测已保存模型识图</ElButton>
    <p class="small muted">先保存设置。检测只发送内置低分辨率图片，产生一次模型调用；不会采集素材或调用备用模型。</p>
    <ElAlert v-if="result" :title="result.reason" :type="result.status === 'SUCCESS' ? 'success' : 'warning'" :closable="false" />
  </div>
</template>

<style scoped>
.model-vision-probe { margin-top: 1rem; }
</style>
