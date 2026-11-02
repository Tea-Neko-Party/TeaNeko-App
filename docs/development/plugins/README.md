# 插件开发入口

适用版本：v4.6.0-l，宿主插件 SPI 版本为 1。

| 要开发的内容 | 阅读文档 |
| --- | --- |
| 插件入口、依赖、启停、命令、事件、同步任务阶段、通用异步拦截 | [Core 插件](core.md) |
| Agent 工具、组件、System、资料、可监听事件、异步链、模型和任务动作 | [Agent 插件](agent.md) |
| 为未来 Web 提供配置表单、查询和管理操作 | [管理扩展](management.md) |

插件是随应用装配的受信任 Java 模块。可以放在当前工程，也可以编译为依赖 JAR，由宿主显式导入 Spring 配置。当前实现不提供上传目录自动安装、任意 JAR 热加载、类卸载或恶意代码沙箱。逻辑开关控制新增路由和调用版本，Bean 与自有资源最终由 Spring 生命周期释放。

先选扩展点，再提供小范围贡献；不复制 AgentService、模型工具循环、TaskService、TimerService 或数据库仓库。内置工具和记忆也使用 AgentFeature 贡献，新增插件不会令这些默认能力退让。

最短示例是已编译并有实际工具闭环验证的 [ExampleAgentPlugin](../../../src/main/java/org/zexnocs/teanekoplugin/agent_example/ExampleAgentPlugin.java)、[ExampleClockFeature](../../../src/main/java/org/zexnocs/teanekoplugin/agent_example/ExampleClockFeature.java) 和 [ExampleClockListener](../../../src/main/java/org/zexnocs/teanekoplugin/agent_example/ExampleClockListener.java)。它同时演示模块、实例时区组件、轮次时间组件、前置 System、时间工具、资料异步环绕及可修改事件。

本地配置 `tea-neko.plugins.example.enabled=true` 只负责装配示例模块；实例还须在 AgentDefinition.features 显式加入 `example.clock:clock`。默认聊天实例不会因此获得示例能力；宿主需用插件化 AgentFactory 创建选择该能力的实例，再通过原 Agent 服务执行。实际模型、scope 配置、原生权限与平台回执仍需自己的运行环境。

Java 插件当前依赖 TeaNeko App 的普通 JAR及必要依赖，编译使用 JDK 25。先在同仓库实现并验证，再作为依赖接入；公共 SPI 仍位于原包内，本版没有独立发布 Maven SDK 工件。不依赖私有实现、反射方法名或 Bean 扫描顺序来实现扩展。
