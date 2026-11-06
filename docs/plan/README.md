# 开发计划索引

进行中的实施计划放在本目录；完成其约定的实现与验收范围后，主计划及配套需求、规格和历史记录一并移入 old。归档只调整必要的定位链接，保留原始答复、进度证据及当时的验收结论。

最新完成内容见 [4.8.6-l 共享记忆与社交组件](old/4.8.6-social-memory.md)及同版本[请求观测与记忆学习](old/4.8.6-observation-memory.md)，配置与升级见[发布说明](../changelog/4.8.6-l.md)。审查发现按版本维护在[审查索引](../review/README.md)；历史复核及尚未执行的实机验收不等同于当前代码待办。

本轮生产 MVP 和 Java 规范评估见 [4.8.6-l 下一步实施](../review/4.8.6-l/next-steps.md)与[评估标准](../review/4.8.6-l/assessment-standard.md)。这是新增审查发现，尚未建立实施计划；不改变下表已归档任务的原验收范围。

## 进行中

当前约定的代码实施均已归档；部署环境实机验收单独维护。

## 已完成

| 计划 | 完成范围 | 发布说明 |
| --- | --- | --- |
| [v4.8.6-l 共享记忆与社交组件](old/4.8.6-social-memory.md) | S01–S08、349 项定向回归、9 项分层与插件检查、8 项页面检查及实际产物核对，100%；实机验收单独安排 | [v4.8.6-l（待发布）](../changelog/4.8.6-l.md) |
| [v4.8.6-l 请求观测与记忆学习](old/4.8.6-observation-memory.md) | F01–F08、287 项定向回归、JavaDoc 与实际产物核对，100%；真实模型及数据库另行验收 | [v4.8.6-l（待发布）](../changelog/4.8.6-l.md) |
| [v4.8.5-l 观测复核修正](old/4.8.5-observation-review-fixes.md) | O01/O02、135 项定向回归、4 项原诊断、JavaDoc 和产物核对，100%；实机验收单独安排 | [v4.8.5-l](../changelog/4.8.5-l.md) |
| [v4.8.5-l 目录、请求观测与存储整理](old/4.8.5-observation-storage.md) | 四项功能、1025 项回归、四组浏览器检查与产物核对，100%；部署数据库单独验收 | [v4.8.5-l](../changelog/4.8.5-l.md) |
| [v4.8.4-l 页面扩展收尾](old/4.8.4-console-pages.md) | 单一页面声明、草稿及轮询生命周期、资源白名单、11 组源码/生产交互与 17 项安全回归、产物同步，100% | [v4.8.4-l（待发布）](../changelog/4.8.4-l.md) |
| [v4.8.4-l 控制台编辑与 Agent 管理](old/4.8.4-console-editors-workspace.md) | F01–F11、独立审查修正、1038 项回归及七组浏览器交互、离线产物核对，100% | [v4.8.4-l（待发布）](../changelog/4.8.4-l.md) |
| [v4.8.3-l 继承工具代理隔离](old/4.8.3-inherited-tool-proxy.md) | G03 独立方法规格、继承条件/业务的真实代理回归及离线产物核对，100% | [v4.8.3-l](../changelog/4.8.3-l.md) |
| [v4.8.3-l 统一结构化输出](old/4.8.3-structured-output.md) | 类型描述、JSON/Schema 适配、六类输出迁移、安全诊断及离线产物核对，100%；真实模型另行验收 | [v4.8.3-l](../changelog/4.8.3-l.md) |
| [v4.8.3-l 记忆领取失败退避](old/4.8.3-memory-scan-retry.md) | 领取前退避、分区隔离、安全格式诊断、旧记录兼容及离线产物核对，100% | [v4.8.3-l](../changelog/4.8.3-l.md) |
| [v4.8.3-l 组件复评修正](old/4.8.3-component-review-followup.md) | G01/G02、证据诊断及暂停恢复、删除记录分行迁移、组合回归与离线产物核对，100%；实机容量另行验收 | [v4.8.3-l](../changelog/4.8.3-l.md) |
| [v4.8.3-l 表情包工具协议修复](old/4.8.3-sticker-tool-protocol.md) | 双竖线 DSML 漏检、有限纠正、目录依赖诊断及图片交付离线回归，100% | [v4.8.3-l](../changelog/4.8.3-l.md) |
| [v4.8.3-l Agent 组件修正](old/4.8.3-agent-component-fixes.md) | F01–F11、相关 R01–R04 边界、文档同步及离线验收，100%；图库整行锁的真实吞吐另行验收 | [v4.8.3-l](../changelog/4.8.3-l.md) |
| [v4.8.2-l 机器 WS 与独立 WSS 入口](old/4.8.2-machine-ws.md) | 直接 WS 认证、Docker 接入、公开 WSS 端口及离线验收，100% | [v4.8.2-l](../changelog/4.8.2-l.md) |
| [v4.8.2-l 扫描与配置查询审计](old/4.8.2-scanner-audit.md) | 类与 Bean 发现缓存、显式重扫恢复、轻量文件配置查询及离线验收，100% | [v4.8.2-l](../changelog/4.8.2-l.md) |
| [v4.8.2-l 聊天与连接追加修正](old/4.8.2-chat-connection-followup.md) | 重复扫描、会话准备诊断、连接与配置卡片修正；用户确认聊天恢复，100% | [v4.8.2-l](../changelog/4.8.2-l.md) |
| [v4.8.2-l 模型与聊天补丁](old/4.8.2-web-model-chat.md) | 模型直接保存与检测、聊天删除/布局、失败原因回执及离线验收，100% | [v4.8.2-l](../changelog/4.8.2-l.md) |
| [v4.8.0-l 分层与组件目录重构](old/4.8.0-layering.md) | 单向依赖、完整能力归类、客户端迁移、单工具类及隔离回归/产物核对，100% | [v4.8.0-l（待发布）](../changelog/4.8.0-l.md) |
| [v4.8.0-l Agent 可序列化组件装配](old/4.8.0-agent-composition.md) | 组件与系统解耦、cfg/Web 装配、私有模型、API 文档及离线验收，100% | [v4.8.0-l（待发布）](../changelog/4.8.0-l.md) |
| [v4.7.7-l 聊天状态与模型管理](old/4.7.7-console-chat-models.md) | 聊天状态、指令规则筛选、公开资料更新、多模型弹窗与路由切换及离线验证，100% | [v4.7.7-l（待发布）](../changelog/4.7.7-l.md) |
| [v4.7.6-l 控制台补丁](old/4.7.6-console-polish.md) | 指令名称、迁移目录、模型选择、配置解释、房间内管理和离线验证，100% | [v4.7.6-l（待发布）](../changelog/4.7.6-l.md) |
| [v4.7.5-l 聊天室与模型配置](old/4.7.5-web-chat-models.md) | 网页入站、私聊默认启用、聊天室、指令补全及一览、模型专页和注解式配置扩展，100% | [v4.7.5-l（待发布）](../changelog/4.7.5-l.md) |
| [v4.7.4-l 网页聊天与配置补丁](old/4.7.4-web-chat-patch.md) | 输入轮询、附件与直接导航、配置显示和模型准入诊断，100% | [v4.7.4-l（待发布）](../changelog/4.7.4-l.md) |
| [v4.7.3-l 网页易用性补丁](old/4.7.3-web-usability.md) | 独立卡片高度、聊天表单、管理员直接配置、Agent 独立选择、刷新图标、15 个任务介绍及自定义配色，100% | [v4.7.3-l（待发布）](../changelog/4.7.3-l.md) |
| [模型 token 与上下文策略统一](old/model-token-policy.md) | 统一模型输出配置、默认 80% 输入上下文、完整输出预留及显式预算拒绝，100% | [v4.7.2-l（待发布）](../changelog/4.7.2-l.md) |
| [4.7.1 Web 管理补丁](old/4.7.1-web-operations.md) | 界面、机器令牌/诊断、通用配置、任务分类、机器状态及离线验收，100% | [v4.7.1-l（待发布）](../changelog/4.7.1-l.md) |
| [4.7.0 Web 控制台](old/4.7.0-web-console.md) | 登录安全、配置/Agent/任务/日志、客户端指令台、独立网页聊天、模拟成员、受控媒体及离线验收，100% | [v4.7.0-l（待发布）](../changelog/4.7.0-l.md) |
| [4.6.2 拟人表达与表情包](old/agent-expression-stickers.md) | 拟人分段、共享图库、类别选图、独立发送与原生表情、全局管理 SPI、受限事件及离线验收，100% | [v4.6.2-l](../changelog/4.6.2-l.md) |
| [4.6.1 三层自动记忆与观察式人格](old/4.6.1-layered-memory.md) | 短中长期持久化、自动画像、表达观察、明确保存、逐段送达及维护，已完成离线验收 | 尚未确认发布 |
| [4.6.0 注解工具](old/4.6.0-annotated-tools.md) | Spring 自动注册、参数 schema、client、可信上下文、异步图像及十项工具迁移 | [v4.6.0-l](../changelog/4.6.0-l.md) |
| [4.6.0 引用图片工具回填](old/4.6.0-tool-images.md) | 显式图像结果、OneBot 视觉输入、批次配对及历史投影 | [v4.6.0-l](../changelog/4.6.0-l.md) |
| [4.6.0 平台上下文与共享记忆修正](old/4.6.0-agent-context.md) | 私聊触发、命令帮助、namespace 共享召回、client 工具边界、OneBot 查询与模型消息元数据 | [v4.6.0-l](../changelog/4.6.0-l.md) |
| [4.6.0 插件化](old/4.6.0-plugins.md) | 插件底座、Agent 能力、事件与异步拦截、管理 SPI、开发文档及离线验收 | [v4.6.0-l](../changelog/4.6.0-l.md) |
| [4.5.0 Agent](old/4.5.0-agent.md) | S0–S9 实现与离线交付，原始需求、规格和分阶段证据随主计划归档 | [v4.5.0-l](../changelog/4.5.0-l.md) |

## 后续验收与设计

- [记忆与人格重设计规格归档](old/4.6.1-memory-specification.md)：保留自动三层记忆、观察式人格、持久化契约及采用边界；当前实现及离线验收见同目录实施记录，操作与配置查阅模块说明，项目对照记录仅放在 temp/docs。

- 真实 MySQL、OneBot、模型、完整启动与性能检查继续维护在 [部署验收清单](../development/agent-deployment.md#实机验收清单)，不计入已完成计划的实机通过结论。
- [Web 管理设计](../design/web-management-api.md) 的 v4.7.0-l 实施已完成离线验收，记录见[已归档计划](old/4.7.0-web-console.md)。当前入口及仍需执行的 HTTPS、代理、数据库和平台检查见[部署开发说明](../development/web-console.md)，管理 SPI 见[管理扩展指南](../development/plugins/management.md)。不把后续目标接口或实机检查标为已通过。
- 历史计划中的“当前版本”“下一步”和阶段百分比表示归档时状态；最新版本与操作方式查阅 [发布日志](../changelog/README.md) 及对应模块文档。
