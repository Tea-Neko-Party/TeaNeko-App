<script setup lang="ts">
// 2026/11/04：同源资源由供应商适配器声明；未知扩展仍显示可读缩写。
import {ref, watch} from 'vue'

const props = defineProps<{icon?: string; name: string}>()
const failed = ref(false)
watch(() => props.icon, () => { failed.value = false })
</script>
<template><span class="model-icon"><img v-if="icon && !failed" :src="icon" :alt="name + ' 图标'" @error="failed = true" /><span v-else aria-hidden="true">{{ name.slice(0, 2).toUpperCase() }}</span></span></template>
