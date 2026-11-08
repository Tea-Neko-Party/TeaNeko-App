# 插件开发入口

适用版本：v4.9.0-l，宿主插件 SPI 版本为 1。

| 要开发的内容 | 阅读文档 |
| --- | --- |
| 插件入口、依赖、启停、命令、事件、同步任务阶段、通用异步拦截 | [Core 插件](core.md) |
| Agent 工具、组件、System、资料、可监听事件、异步链、模型和任务动作 | [Agent 插件](agent.md) |
| 可序列化组件、独立参数、系统阶段和聊天装配表单 | [可装配组件](agent-components.md) |
| 通过 Spring Bean 与注解新增工具、参数、client 和异步图像结果 | [注解式工具](agent-tools.md) |
| 为 Web 提供配置表单、查询、管理操作和插件独立页面 | [管理扩展](management.md) |

插件是随应用装配的受信任 Java 模块，业务类型位于 `org.zexnocs.teanekoplugin` 及子包，入口使用 `@TeaNekoPlugin` 集中声明身份、依赖、客户端要求和启停方式。可以放在当前工程，也可以编译为依赖 JAR，由宿主显式导入 Spring 配置。当前实现不提供上传目录自动安装、任意 JAR 热加载、类卸载或恶意代码沙箱。宿主统一持久化启用意愿并协调可重复启停；插件通过 IPluginLifecycle 停止自有工作，最终销毁仍由 Spring 管理。

先选扩展点，再提供小范围贡献；不复制 AgentService、模型工具循环、TaskService、TimerService 或数据库仓库。LLM 与通用工具底座属于原生系统能力；记忆等具体业务通过插件贡献装配。Core、Agent 的稳定资源身份使用独立宿主能力合同，不作为业务插件进入管理开关。

最短示例是已编译并有实际工具闭环验证的 [ExampleAgentPlugin](../../../src/main/java/org/zexnocs/teanekoplugin/agent/example/ExampleAgentPlugin.java)、[ExampleClockFeature](../../../src/main/java/org/zexnocs/teanekoplugin/agent/example/ExampleClockFeature.java) 和 [ExampleClockListener](../../../src/main/java/org/zexnocs/teanekoplugin/agent/example/ExampleClockListener.java)。它同时演示模块、实例时区组件、轮次时间组件、前置 System、时间工具、资料异步环绕及可修改事件。

本地配置 `tea-neko.plugins.example.enabled=true` 只负责装配示例模块；实例还须在 AgentDefinition.features 显式加入 `example.clock:clock`。默认聊天实例不会因此获得示例能力；宿主需用插件化 AgentFactory 创建选择该能力的实例，再通过原 Agent 服务执行。实际模型、scope 配置、原生权限与平台回执仍需自己的运行环境。

Java 插件当前依赖 TeaNeko App 的普通 JAR 及必要依赖，编译使用 JDK 25。先在同仓库实现并验证，再作为依赖接入；本版没有独立发布 Maven SDK 工件。v4.9.0-l 的业务插件包迁移需要同步更新 import 并重新编译，按[项目分层](../project-layers.md)选择扩展位置，不依赖私有实现、反射方法名或 Bean 扫描顺序。
