import type {PluginConsoleModule} from '../../../shared/plugins/plugin-page'
// 2026/11/10：随前端构建注册插件模型页面，宿主菜单不增加业务分支。
export default {key: 'agent.expression:sticker-model', load: () => import('./StickerModelPage.vue')} satisfies PluginConsoleModule
