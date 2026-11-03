<script setup lang="ts">
import {ref} from 'vue'
import {ElAlert, ElButton, ElCheckbox, ElInput} from 'element-plus'
import {savedLoginToken} from './login-token'

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
  <main class="login-shell">
    <section class="login-intro">
      <div class="brand"><span class="brand-mark">茶</span> TeaNeko App</div>
      <div><p class="eyebrow">管理控制台</p><h1>让每一次对话<br />都有迹可循。</h1>
        <p class="muted">从一个入口了解运行状态，管理你的聊天平台。</p></div>
      <p class="small muted">仅限已授权的管理员访问</p>
    </section>
    <section class="login-form">
      <p class="eyebrow">欢迎回来</p><h2>登录控制台</h2>
      <p class="muted">请输入服务器本地配置的管理员令牌。</p>
      <ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <form @submit.prevent="submit">
        <label for="admin-token">管理员令牌</label>
        <ElInput id="admin-token" v-model="token" type="password" show-password
          name="token" autocomplete="current-password" :maxlength="256" size="large" placeholder="输入管理员令牌" :disabled="busy" />
        <ElCheckbox v-model="remember" :disabled="busy">在此浏览器保存令牌</ElCheckbox>
        <ElButton native-type="submit" type="primary" size="large" :loading="busy" :disabled="!token || busy">
          安全登录
        </ElButton>
      </form>
      <p class="small muted">保存后下次访问会自动填入；取消勾选并成功登录即可清除。服务器随机令牌重启后会更新。</p>
    </section>
  </main>
</template>
