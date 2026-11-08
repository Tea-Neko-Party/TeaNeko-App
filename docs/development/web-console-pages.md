# 控制台页面扩展

适用版本：4.9.0-l。页面随前端构建交付，不下载远端脚本。普通配置仍优先复用[管理表单](plugins/management.md)，独立页面才使用以下入口。

## 宿主页面声明

源码按[前端职责目录](../../frontend/README.md#源码职责)组织。宿主页面实现放在 `frontend/src/features/<功能>`，在 `frontend/src/app/pages/page-registry.ts` 的 `definitions` 中增加一项；页面类型、导航、分组及标题自动派生，无须修改外壳 `app/App.vue` 或 `app/ConsoleNavigation.vue`。

```ts
{
  key: 'reports', title: '运行报告', group: '管理', icon: 'runtime',
  load: () => import('../../features/reports/ReportsPage.vue'),
  props: (_, active) => ({active}),
}
```

标识必须唯一；顺序按声明排列，`group: 'footer'` 放在侧栏底部。`visible(context)` 可按实际能力隐藏入口，宿主导航也检查它，但这不是授权：所有请求仍经过服务端会话、角色、CSRF、资源白名单及插件复核。

`load` 只能引用本地构建模块。加载失败保留原页，显示恢复提示，不自动重放操作或刷新浏览器。文件页和模型页即使使用同一组件，也按页面标识分别保存实例。

## 插件独立页面

插件实现 Spring Bean `IPluginConsoleContribution`，返回已注册的 `pluginId()` 和页面列表。每项 `PluginConsolePage` 声明局部 `id`、中文标题与说明、构建模块 `moduleKey`、排序及所需管理资源集合。宿主在初始化或显式重扫时检查重复标识、插件归属和资源键，完整成功后一次发布目录；查询只读取缓存并实时检查插件和资源白名单。

在 `frontend/src/plugins/<分类>/<插件>/entry.ts` 导出 `PluginConsoleModule`，例如：

```ts
import type {PluginConsoleModule} from '../../../shared/plugins/plugin-page'

export default {
  key: 'sample.plugin:workspace',
  load: () => import('./WorkspacePage.vue'),
} satisfies PluginConsoleModule
```

前端自动收集本地入口，服务端声明的 `moduleKey` 必须与 `key` 一致。页面出现在侧栏的“插件配置”二级菜单，插件总管理卡片也提供入口。重复模块键直接报错；缺少构建模块时插件管理页明确诊断，不下载远端脚本、不把 JAR 资源当成前端执行代码。插件页面复用下述 `PageContext`、草稿和轮询合同。

`GET /api/v1/plugins/pages` 返回当前可用目录。插件关闭或资源权限撤销后，页面声明和已挂载实例同时撤销，迟到模块不能恢复旧页面；当前页转回插件管理。插件总管理仍保留关闭项，便于重新启用。宿主配置、组件、命令、任务及客户端目录投影 `pluginId`，提供一致的归属标签与筛选。

本机资源限制返回 `LOCAL_RESOURCE_NOT_ALLOWED` 时显示服务端的授权说明，不将它称为会话过期。插件目录收到 403 后停止自动轮询；显式刷新或重新聚焦窗口会复核一次，成功后恢复轮询。系统模型配置继续按自身文件授权使用，不依赖插件总管理权限。

## 生命周期与草稿

| 场景 | 合同 |
| --- | --- |
| 首次进入 | 动态加载并挂载，传入 `active: true` |
| 切换其他页面 | 默认保留实例，`active: false`；停止后续轮询，不清除草稿和原操作编号 |
| 返回页面 | 原实例重新激活，恢复轮询；不得重发原写请求 |
| `retention: 'active'` | 离开时卸载；只读概览使用此策略，编辑页不应随意采用 |
| 主动退出 | 检查所有已挂载页及其嵌套编辑器；确认不提前清空，注销成功后才统一销毁 |
| 插件或页面权限撤销 | 销毁该插件页面与编辑器、清空秘密草稿；保留服务端历史，不自动重发在途操作 |
| 会话失效 | 立即销毁页面；旧模块加载和迟到事件不能重建旧会话 |

轮询使用 `shared/lifecycle/active-polling.ts` 的 `useActivePolling`，自行保留忙碌检查及错误处理。页面隐藏和浏览器不可见时停止计时；已有读取可安全完成，不能把隐藏当作写入取消或没有副作用。机器采样及网页消息保留各自中止读取的处理。

编辑器使用 `shared/lifecycle/page-guards.ts` 的 `usePageDraft(() => ({dirty, pending, busy}))` 登记实际状态。嵌套房间配置、补正和弹窗草稿汇总到所属页；回调随组件卸载移除。浏览器刷新仍沿用各编辑器的 `beforeunload` 提示，草稿不写入浏览器持久存储。

## 页面协作

`app/pages/page-contracts.ts` 的 `usePageContext()` 提供只读环境引用、认证失效通知和受控 `navigate(key, intent)`。聊天目标、客户端选择或 `{kind: 'plugin', pluginId}` 配置筛选在所属页面处理，不把内部编辑器引用交给外壳。

组件可通过 `defineExpose` 实现 `PageHandle` 的可选方法：

- `open(intent)`：处理目标导航，返回 `false` 表示拒绝切换。更换真实编辑目标仍走原业务的草稿确认。
- `canLeave({reason, retained})`：补充页面特有的离开检查；保留实例时不要清空状态。检查本身不提交业务。
- `refresh()`：显示页头刷新按钮；请求异常交给宿主映射。
- `headingHidden`：只控制页头显示，网页聊天室以此进入沉浸模式。

仅展示前端页面不会自动开放 HTTP 入口。新增普通业务走已有类型化管理资源；确有专用协议需求时另行定义精确路由，不放宽默认拒绝规则。
