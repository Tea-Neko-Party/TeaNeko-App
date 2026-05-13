# 一. Command 结构介绍

`command` 是通用命令分发模块，用于把不同客户端输入转换成 `CommandData`，再通过注解扫描、权限校验、作用域校验和异步执行完成命令调用。

| 区域 | 作用 |
|:---:|---|
| `api` | 命令、默认命令、子命令、默认参数、权限和作用域注解或枚举。 |
| `interfaces` | 分发器、转换器、执行器、参数处理器、权限管理器、作用域管理器、错误处理器等接口。 |
| `event` | 命令分发事件和命令执行事件。 |
| `easydata` | 命令模块使用的 EasyData 存储表。 |
| `exception` | 参数类型不匹配等异常。 |

# 二. 执行流程

```markdown
1. 外部适配层实现 ICommandConverter<T>，把原始消息转换成 CommandData<T>。
2. 调用 ICommandDispatcher.dispatch(data, errorHandler, helpHandler)。
3. CommandDispatcher 先推送 CommandDispatchEvent，实际分发在事件中继续执行。
4. CommandScanner 根据 @Command 扫描 prefix 命令和 regex 命令。
5. prefix 命令优先匹配 data.body；regex 命令匹配 body + args 拼接后的完整字符串。
6. 匹配后检查 enable、scope 和 permission。
7. CommandArgumentProcessor 将 String[] args 转换为方法参数。
8. 通过 CommandExecuteEvent 交给 ICommandExecutor 执行，默认执行器会提交到 TaskService。
```

# 三. 命令声明 API

| API | 说明 |
|---|---|
| `@Command(value, permission, scope, supportedClients, enable, mode, permissionPackage, taskNamespace)` | 标记一个命令类。`value` 可配置多个命令名，第一个通常作为主名。 |
| `@DefaultCommand(permission, scope, permissionPackage)` | 标记默认命令方法，适用于 `主命令 参数`。 |
| `@SubCommand(value, permission, scope, permissionPackage)` | 标记子命令方法，适用于 `主命令 子命令 参数`。 |
| `@DefaultValue("...")` | 标记方法参数默认值。参数不足或转换失败时尝试使用。 |
| `Command.CommandMode.PREFIX` | 前缀命令，按命令名直接匹配。 |
| `Command.CommandMode.REGEX` | 正则命令，只支持默认命令方法，方法参数只能为空或 `CommandData<?>`。 |
| `CommandPermission` | `DEFAULT`、`DEBUG`、`OWNER`、`ADMIN`、`MEMBER`、`ALL`。数值越大限制越低。 |
| `CommandScope` | `DEFAULT`、`GROUP`、`PRIVATE`、`ALL`、`OTHER`。 |

## 示例

```java
@Command(
        value = {"/hello", "/hi"},
        permission = CommandPermission.ALL,
        scope = CommandScope.ALL
)
public class HelloCommand {
    @DefaultCommand
    public void hello(CommandData<?> data, @DefaultValue("world") String name) {
        // execute
    }

    @SubCommand("times")
    public void times(String name, int count) {
        // execute
    }
}
```

# 四. 数据和扩展接口

| API | 说明 |
|---|---|
| `CommandData<T>` | 命令输入模型，包含 `body`、`args`、`scope`、`clientClass`、`scopeId`、`permission`、`senderId` 和原始数据 `rawData`。 |
| `ICommandConverter<T>` | 把客户端原始数据解析为 `CommandData<T>`。 |
| `ICommandDispatcher` | 命令分发入口。 |
| `ICommandErrorHandler` | 处理命令关闭、未找到方法、参数错误、无权限、不在作用域等结果。 |
| `IHelpSubCommandHandler` | 处理帮助子命令，默认帮助 key 是 `help`。 |
| `ICommandArgumentProcessor` | 将字符串参数转换为 Java 方法参数。 |
| `ICommandExecutor` | 实际执行命令方法。 |
| `ICommandPermissionManager` | 判断和管理权限。 |
| `ICommandScopeManager` | 判断命令是否位于当前作用域。 |
| `ICommandClient` | 客户端标记接口，用于 `supportedClients` 限制。 |

# 五. 参数转换规则

`CommandArgumentProcessor` 当前支持：

| 类型 | 说明 |
|---|---|
| `String` | 原样传入。 |
| `int/Integer`、`long/Long`、`float/Float`、`double/Double` | 使用对应 parse 方法转换。 |
| `boolean/Boolean` | 使用 `Boolean.parseBoolean`。 |
| `enum` | 使用 `Enum.valueOf`，大小写需匹配枚举常量。 |
| `List<T>` | 尝试消费多个参数，`T` 支持上述基础转换；无泛型时按 `String` 处理。 |
| `CommandData<T>` | 直接注入当前命令数据，并校验泛型原始数据类型。 |

# 六. 注意事项

| 场景 | 建议 |
|---|---|
| `DEFAULT` 权限或作用域 | `DEFAULT` 用于子命令继承父命令。不要把顶层 `@Command` 直接设置为 `DEFAULT`，否则通常不可用。 |
| 正则命令 | 正则命令不会解析子命令，只执行默认方法。 |
| 帮助子命令 | 帮助子命令在普通子命令前匹配。 |
| 事件拦截 | 命令分发和执行都会进入事件系统，可通过事件监听器做审计、取消或增强。 |
