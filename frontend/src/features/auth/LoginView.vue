<script setup lang="ts">
// 2026/11/07：按功能目录引用共享能力与页面契约。
import {ref} from 'vue'
import {ElAlert, ElButton, ElCheckbox, ElInput} from 'element-plus'
import {savedLoginToken} from './login-token'
import AppearanceControls from '../../shared/components/AppearanceControls.vue'

// 2026/11/04：链接令牌优先于已保存值，持久化须经用户选择及成功认证。
const props = defineProps<{ busy: boolean; error: string; initialToken: string }>()
const emit = defineEmits<{ login: [token: string, remember: boolean] }>()
const saved = savedLoginToken()
const token = ref(props.initialToken || saved)
const remember = ref(Boolean(saved))

function submit() {
  if (!token.value) return
  const value = token.value
  emit('login', value, remember.value)
}
</script>

<template>
  <!-- 2026/11/04：单卡片登录只保留必要输入，令牌来源与保存说明按需展开。 -->
  <main class="login-shell">
    <div class="login-appearance"><AppearanceControls /></div>
    <section class="login-card" aria-labelledby="login-heading">
      <div class="login-brand"><span class="brand-mark">茶</span><h1 id="login-heading">TeaNeko</h1><p class="muted">管理控制台</p></div>
      <ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <form @submit.prevent="submit">
        <label for="admin-token">管理员令牌</label>
        <ElInput id="admin-token" v-model="token" type="password" show-password
          name="token" autocomplete="current-password" :maxlength="256" size="large" placeholder="输入管理员令牌" :disabled="busy" />
        <ElCheckbox v-model="remember" :disabled="busy">记住令牌</ElCheckbox>
        <ElButton native-type="submit" type="primary" size="large" :loading="busy" :disabled="!token || busy">
          登录
        </ElButton>
      </form>
      <details class="login-help"><summary>令牌在哪里？</summary>
        <p>查看后端启动日志，或本地 Web 安全配置。使用启动日志中的快捷链接可自动填入。</p>
        <p>勾选后仅在登录成功时保存；取消勾选并登录可清除。随机令牌随服务重启更新。</p>
      </details>
    </section>
  </main>
</template>
