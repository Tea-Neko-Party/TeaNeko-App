// 2026/11/07：按功能目录引用共享能力与页面契约。
import type {PluginConsoleModule} from '../../../shared/plugins/plugin-page'

// 2026/11/07：认知页面通过构建期收集接入，宿主不维护插件页面分支。
export default {
  key: 'agent.cognition:workspace',
  load: () => import('./CognitionPage.vue'),
} satisfies PluginConsoleModule
