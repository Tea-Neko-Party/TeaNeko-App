# Core 插件基础设施

v4.6.0-l 已实现插件声明、完整依赖校验、逻辑启停及版本令牌；命令、事件、同步任务阶段及模型调用可声明归属，异步操作通过类型化环绕组合。Bean 与资源由 Spring 生命周期拥有，不提供任意 JAR 热卸载。

公开合同统一见 [Core 插件指南](../../../../../../../docs/development/plugins/core.md)，全项目入口见 [插件指南](../../../../../../../docs/development/plugins/README.md)。

| 位置 | 职责 |
| --- | --- |
| PluginDescriptor / PluginModule | 元数据与模块入口 |
| PluginRegistry | 依赖目录、启动禁用列表、逻辑开关及调用版本复核 |
| PluginOwned / PluginAccess | 原扫描式扩展的归属守卫 |
| PluginScopedEvent | 事件参与范围及异步监听资格 |
| operation | 类型化异步环绕，保留原任务调用栈 |
| management | 默认拒绝的管理授权、资源 schema、类型化用例及路由 |
