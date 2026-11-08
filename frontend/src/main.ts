// 2026/11/07：按功能目录引用共享能力与页面契约。
import {createApp} from 'vue'
import App from './app/App.vue'
import 'element-plus/dist/index.css'
// 2026/11/04：深色模式沿用组件库的语义变量，与控制台配色同步。
import 'element-plus/theme-chalk/dark/css-vars.css'
import './app/styles/style.css'

createApp(App).mount('#app')
