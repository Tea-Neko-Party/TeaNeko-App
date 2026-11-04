import {createApp} from 'vue'
import App from './App.vue'
import 'element-plus/dist/index.css'
// 2026/11/04：深色模式沿用组件库的语义变量，与控制台配色同步。
import 'element-plus/theme-chalk/dark/css-vars.css'
import './style.css'

createApp(App).mount('#app')
