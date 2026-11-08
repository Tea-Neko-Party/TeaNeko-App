import type {Component} from 'vue'

/** 2026/11/07：扩展只导出本地构建模块，权限和导航元数据由 Java 贡献目录提供。 */
export interface PluginConsoleModule {
  key: string
  load: () => Promise<{default: Component}>
}
