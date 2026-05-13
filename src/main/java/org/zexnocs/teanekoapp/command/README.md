# 一. Command 模块结构介绍

`command` 模块把 `message` 模块中的 `ITeaNekoMessageData` 转换为 `teanekocore.command.CommandData`，并接入 core 命令调度器。它还提供 TeaNeko App 的帮助、权限、作用域等内置命令。

| 类 | 作用 |
|:---:|---|
| `TeaNekoCommandConverter` | 将 `ITeaNekoMessageData` 解析为 `CommandData<ITeaNekoMessageData>`。 |
| `TeaNekoCommandReceiver` | 监听 `TeaNekoMessageReceiveEvent<?>`，解析后交给 `ICommandDispatcher`。 |
| `TeaNekoCommandErrorHandler` | 把命令错误回复给消息发送者。 |
| `TeaNekoHelpSubCommandHandler` | 处理 core 命令系统的子命令帮助输出。 |
| `TeaNekoHelpCommand` | `/help` 命令，分页输出当前用户可见命令。 |
| `TeaNekoPermissionCommand` | `/permission` / `/权限` 命令，管理群作用域内用户权限。 |
| `TeaNekoScopeCommand` | `/scope` 调试命令，管理命令在群作用域中的启用和禁用状态。 |

# 二. 命令解析流程

```markdown
1. 客户端推送 TeaNekoMessageReceiveEvent<?>。
2. TeaNekoCommandReceiver.handle(...) 在事件末尾阶段处理消息。
3. TeaNekoCommandConverter.parse(...) 读取消息内容片段。
4. 如果开头是 reply，先移除 reply；如果后面紧跟 at，也一并移除。
5. 逐个调用 ITeaNekoContentPart.toCommandArgs()，扁平化成字符串列表。
6. 第一个字符串成为 CommandData.body，其余字符串成为 CommandData.args。
7. 根据 TeaNekoMessageType 转换 CommandScope。
8. 设置 scopeId、permission、senderId、rawData 和 clientClass。
9. ICommandDispatcher.dispatch(...) 执行命令。
```

# 三. CommandData 映射

| CommandData 字段 | 来源 |
|---|---|
| `body` | 消息解析后第一个参数。 |
| `args` | 消息解析后除第一个参数外的剩余参数。 |
| `scope` | `PRIVATE`、`PRIVATE_TEMP` -> `CommandScope.PRIVATE`；`GROUP` -> `CommandScope.GROUP`；其他 -> `CommandScope.OTHER`。 |
| `scopeId` | `ITeaNekoMessageData.getScopeId()`。 |
| `permission` | `ITeaNekoUserData.getRole()`。 |
| `senderId` | `ITeaNekoUserData.getUuid().toString()`。 |
| `rawData` | 原始 `ITeaNekoMessageData`。 |
| `clientClass` | `data.getClient().getClass()`。 |

# 四. 内置命令

| 命令 | 权限 | 作用 |
|---|---|---|
| `/help [page]` | `ALL` | 分页列出当前用户有权限、且在当前 scope 可用的命令。 |
| `/permission add <user_id> <permission>` | `OWNER` + `teaneko.admin.permission` | 为当前 scope 内用户添加权限。 |
| `/permission remove <user_id> <permission>` | `OWNER` + `teaneko.admin.permission` | 移除用户权限。 |
| `/permission ban <user_id> <permission>` | `OWNER` + `teaneko.admin.permission` | 禁止用户拥有某权限。 |
| `/permission unban <user_id> <permission>` | `OWNER` + `teaneko.admin.permission` | 取消权限禁止。 |
| `/scope add-group <commandId> [groupId]` | `DEBUG` | 允许群使用原本不可用的命令。 |
| `/scope remove-group <commandId> [groupId]` | `DEBUG` | 移除群命令启用记录。 |
| `/scope ban-group <commandId> [groupId]` | `DEBUG` | 禁止群使用某命令。 |
| `/scope unban-group <commandId> [groupId]` | `DEBUG` | 取消群命令禁用。 |

# 五. 错误处理

| 错误类型 | 行为 |
|---|---|
| 命令关闭 | `handleCommandClosed` 当前不回复。 |
| 方法不存在 | 对以 `/` 开头的命令回复“未找到相应的指令”。 |
| 参数错误 | 回复“指令参数错误”。 |
| 无权限 | 回复“没有权限执行该指令”。 |
| 不在作用域内 | 回复“指令不在作用域内”。 |
| 非 `/` 开头 | 默认忽略，避免普通聊天触发错误回复。 |

# 六. 注意事项

| 项 | 说明 |
|---|---|
| reply/at 预处理 | 回复某人时平台常自动带 reply + at，解析命令前会剥离开头的这两个片段。 |
| 自定义内容片段 | 如果希望内容参与命令解析，必须正确实现 `toCommandArgs()`。 |
| scope ID | `TeaNekoCommandConverter.getScopeId(...)` 可基于消息类型、用户数据和 client 构造统一作用域 ID。 |
| 帮助输出 | `/help` 使用转发消息 builder 输出列表；子命令帮助同样使用 forward builder。 |
