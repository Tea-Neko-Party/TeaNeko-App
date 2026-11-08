# 自动化插件

插件 ID：`agent.automation`。提供 `skill`、`schedule` 和 `command` 组件。技能只加载显式允许的本地流程，计划任务保留原持久防重与权限，命令工具复用真实命令链。

旧 `tool.commandToolsEnabled` 开关迁入 `command.commandToolsEnabled`；原值为 false 时不会因升级安装新的命令能力。新旧值冲突时报错并保留原配置。

`integration/command/AutomationCommands` 使用 `@CommandExtension(prefix = "/agent")` 贡献业务子命令，扫描目录缓存真实处理 Bean，帮助、Web 目录和执行都使用该插件的运行资格。命令前缀与 Spring Bean 名分离。关闭插件后自然语言提醒捷径也停止截获普通聊天。

自然语言提醒通过 `NaturalTaskCommandListener` 监听通用 `AgentChatCommandEvent`。只有原生聊天正文进入识别链，派生命令沿用父执行控制；未识别正文交回普通对话。已认领业务和实际反馈一起参与事件完成，发送前再次检查插件代次。

原生来源租约由 Agent 的 `NativeMessageSources` 管理，持久防重由 `runtime/operation` 提供，普通聊天不依赖自动化业务。命令代执行、[文件与代码工具扩展](extension/README.md)及知识源端口按业务归入本插件。
