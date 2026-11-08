# TeaNeko 控制台前端

使用 Vue 3、TypeScript、Vite、Element Plus。控制台提供认证、聊天与配置、Agent 工作区、插件管理和运行观测；数据来自受保护后端，模型执行留在服务端。

构建与 HTTPS 开发步骤、配置、代理、机器接入及验收见[Web 控制台说明](../docs/development/web-console.md)。`npm run build` 执行类型检查并写入 build/generated/web/console；Gradle 通过 webInstall/webBuild 将产物打包到两个 JAR。

应用版本只由根目录 version.properties 管理，页面查询后端；package.json 不维护第二套应用版本。依赖版本及兼容性由锁文件固定，不直接追随不兼容的主版本升级。

## 源码职责

| 目录 | 职责 |
| --- | --- |
| `src/main.ts` | 创建应用，加载组件库和全局样式 |
| `src/app` | 应用外壳与导航；`pages` 维护页面注册、宿主和协作契约，`styles` 保存全局样式 |
| `src/features` | 按 `agent`、`models`、`plugins`、`runtime`、`chat`、`settings`、`commands`、`platforms`、`auth` 聚合页面、业务组件与状态；`settings/chat` 和 `settings/files` 分别维护聊天配置与文件事务表单 |
| `src/shared` | `components` 通用控件、`editors` 字段编辑器、`api` 网络与回执、`state` 外观状态、`lifecycle` 草稿与轮询、`plugins` 插件目录及资格守卫 |
| `src/plugins/<分类>/<插件>` | 插件独立页面及构建入口；与插件总管理功能 `features/plugins` 分开 |
| `public` | 同源静态资源，模型图标等资源地址不随组件目录改变 |

页面装配和插件扩展使用[页面契约](../docs/development/web-console-pages.md)。系统模型配置复用 `features/settings/files` 的文件事务表单，模型专属选择和样板组件位于 `features/models`。共享请求保持原会话、CSRF 和回执检查；目录组织不改变接口权限与业务生命周期。
