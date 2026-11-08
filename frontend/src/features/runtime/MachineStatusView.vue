<script setup lang="ts">
// 2026/11/07：按功能目录引用共享能力与页面契约。
import {onBeforeUnmount, onMounted, ref} from 'vue'
import {ElAlert, ElCard, ElProgress} from 'element-plus'
import {ApiError, request} from '../../shared/api/client'
import DetailPanel from '../../shared/components/DetailPanel.vue'

interface MachineSnapshot {
  observedAt: string; uptimeMillis: number; processors: number; cpuWarmingUp: boolean; cpuIntervalMillis: number
  systemCpuPercent: number | null; processCpuPercent: number | null
  heapUsedBytes: number; heapCommittedBytes: number; heapMaxBytes: number | null
  nonHeapUsedBytes: number; nonHeapCommittedBytes: number
  physicalUsedBytes: number | null; physicalTotalBytes: number | null; processResidentBytes: number | null
  // 2026/11/05：程序所在卷单独展示，不把可用空间与所有空闲空间混为一谈。
  diskUsedBytes: number | null; diskTotalBytes: number | null; diskUsableBytes: number | null
}

const emit = defineEmits<{expired: []}>()
const snapshot = ref<MachineSnapshot>()
const busy = ref(false)
const error = ref('')
let controller: AbortController | undefined
let timer: ReturnType<typeof setTimeout> | undefined
let disposed = false

const bytes = (value: number | null | undefined) => {
  if (value == null) return '暂不可用'
  if (value < 1024) return `${value} B`
  const units = ['KiB', 'MiB', 'GiB', 'TiB']
  const exponent = Math.min(units.length, Math.floor(Math.log2(value) / 10))
  return `${(value / 1024 ** exponent).toFixed(1)} ${units[exponent - 1]}`
}
const cpu = (value: number | null) => value === null ? snapshot.value?.cpuWarmingUp ? '采样中' : '暂不可用' : `${value.toFixed(1)}%`
const ratio = (used: number | null, total: number | null) => used !== null && total !== null && total > 0
  ? Math.min(100, used * 100 / total) : undefined
const uptime = (value: number) => {
  const minutes = Math.floor(value / 60000)
  return minutes < 60 ? `${minutes} 分钟` : minutes < 1440 ? `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`
    : `${Math.floor(minutes / 1440)} 天 ${Math.floor(minutes % 1440 / 60)} 小时`
}
const date = (value: string) => new Date(value).toLocaleString('zh-CN', {hour12: false})

// 2026/11/04：每轮完成后再计时；隐藏、离开概览或退出登录时取消在途读取及后续刷新。
function cancel() {
  clearTimeout(timer)
  timer = undefined
  const previous = controller
  controller = undefined
  previous?.abort()
  busy.value = false
}

async function refresh() {
  if (disposed || document.hidden || controller) return
  clearTimeout(timer)
  const current = new AbortController()
  controller = current
  busy.value = true
  error.value = ''
  try {
    const result = await request<MachineSnapshot>('console/machine', {signal: current.signal})
    if (!current.signal.aborted && !disposed) snapshot.value = result
  } catch (failure) {
    if (current.signal.aborted || disposed) return
    error.value = failure instanceof Error ? failure.message : '机器状态暂时无法读取。'
    if (failure instanceof ApiError && failure.status === 401) emit('expired')
  } finally {
    // 2026/11/04：已取消的旧请求不能覆盖新请求，也不能重新安装刷新定时器。
    if (controller === current) {
      controller = undefined
      busy.value = false
      if (!disposed && !document.hidden) timer = setTimeout(() => void refresh(), 5000)
    }
  }
}

function visibilityChanged() {
  if (document.hidden) cancel()
  else void refresh()
}

onMounted(() => {
  document.addEventListener('visibilitychange', visibilityChanged)
  void refresh()
})
onBeforeUnmount(() => {
  disposed = true
  cancel()
  document.removeEventListener('visibilitychange', visibilityChanged)
})
defineExpose({refresh})
</script>

<template>
  <section class="machine-status" aria-label="机器状态" :aria-busy="busy">
    <div class="section-title"><h2>机器状态</h2><span class="small muted">{{ snapshot ? date(snapshot.observedAt) : '正在读取' }}</span></div>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <p v-if="snapshot && error" class="small muted">以下保留上次成功采样，请以采样时间为准。</p>
    <template v-if="snapshot">
      <div class="machine-grid">
        <ElCard shadow="never" class="machine-card"><h3>系统 CPU</h3><strong>{{ cpu(snapshot.systemCpuPercent) }}</strong>
          <ElProgress v-if="snapshot.systemCpuPercent !== null" :percentage="snapshot.systemCpuPercent" :show-text="false" aria-label="系统 CPU 使用率" />
          <p class="small muted">{{ snapshot.processors }} 个可用处理器</p>
        </ElCard>
        <ElCard shadow="never" class="machine-card"><h3>程序 CPU</h3><strong>{{ cpu(snapshot.processCpuPercent) }}</strong>
          <ElProgress v-if="snapshot.processCpuPercent !== null" :percentage="snapshot.processCpuPercent" :show-text="false" aria-label="程序 CPU 使用率" />
          <p class="small muted">当前进程 · 按可用处理器数归一化</p>
        </ElCard>
        <ElCard shadow="never" class="machine-card"><h3>物理内存</h3><strong>{{ bytes(snapshot.physicalUsedBytes) }}</strong>
          <ElProgress v-if="ratio(snapshot.physicalUsedBytes, snapshot.physicalTotalBytes) !== undefined" :percentage="ratio(snapshot.physicalUsedBytes, snapshot.physicalTotalBytes)!" :show-text="false" aria-label="物理内存使用率" />
          <p class="small muted">总量 {{ bytes(snapshot.physicalTotalBytes) }} · 不含交换空间</p>
        </ElCard>
        <ElCard shadow="never" class="machine-card"><h3>程序内存（JVM 堆）</h3><strong>{{ bytes(snapshot.heapUsedBytes) }}</strong>
          <ElProgress v-if="ratio(snapshot.heapUsedBytes, snapshot.heapMaxBytes) !== undefined" :percentage="ratio(snapshot.heapUsedBytes, snapshot.heapMaxBytes)!" :show-text="false" aria-label="JVM 堆使用率" />
          <p class="small muted">堆上限 {{ bytes(snapshot.heapMaxBytes) }} · 非堆 {{ bytes(snapshot.nonHeapUsedBytes) }}</p>
        </ElCard>
        <!-- 2026/11/05：磁盘与现有机器指标同频刷新，未知容量不显示虚假进度。 -->
        <ElCard shadow="never" class="machine-card"><h3>程序所在磁盘</h3><strong>{{ bytes(snapshot.diskUsedBytes) }}</strong>
          <ElProgress v-if="ratio(snapshot.diskUsedBytes, snapshot.diskTotalBytes) !== undefined" :percentage="ratio(snapshot.diskUsedBytes, snapshot.diskTotalBytes)!" :show-text="false" aria-label="程序所在磁盘使用率" />
          <p class="small muted">总量 {{ bytes(snapshot.diskTotalBytes) }} · 可用 {{ bytes(snapshot.diskUsableBytes) }}</p>
        </ElCard>
      </div>
      <p class="small muted">已运行 {{ uptime(snapshot.uptimeMillis) }} · 当前页面可见时每 5 秒更新</p>
      <DetailPanel title="指标说明"><dl class="machine-details">
        <dt>JVM 堆已用 / 已提交 / 上限</dt><dd>{{ bytes(snapshot.heapUsedBytes) }} / {{ bytes(snapshot.heapCommittedBytes) }} / {{ bytes(snapshot.heapMaxBytes) }}</dd>
        <dt>JVM 非堆已用 / 已提交</dt><dd>{{ bytes(snapshot.nonHeapUsedBytes) }} / {{ bytes(snapshot.nonHeapCommittedBytes) }}</dd>
        <dt>进程常驻物理内存</dt><dd>{{ snapshot.processResidentBytes === null ? 'JDK 管理接口未提供' : bytes(snapshot.processResidentBytes) }}</dd>
        <dt>程序所在磁盘</dt><dd>工作目录所在卷的已用 / 总量；可用量按当前进程权限计算，可能少于磁盘空闲量，不代表程序文件自身大小。</dd>
        <dt>CPU 采样间隔</dt><dd>{{ snapshot.cpuWarmingUp ? '首次建立基准，下次更新显示负载' : `${(snapshot.cpuIntervalMillis / 1000).toFixed(1)} 秒` }}</dd>
      </dl><p class="small muted">系统 CPU 使用 JDK 最近统计窗口，程序 CPU 使用两次采样间平均值。已提交量和堆上限不代表进程实际物理占用；不支持的指标保留为空。</p></DetailPanel>
    </template>
  </section>
</template>
