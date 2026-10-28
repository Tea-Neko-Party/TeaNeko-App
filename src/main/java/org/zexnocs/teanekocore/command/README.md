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
8. 通过 CommandExecuteEvent 交给 ICommandExecutor，在原 TaskService 阶段中反射调用处理方法。
9. 展开并等待处理方法返回的 CompletionStage，再完成 dispatch 的 CommandResult；异常向调用方传播。
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
    public CompletionStage<String> hello(CommandData<?> data, @DefaultValue("world") String name) {
        return CompletableFuture.completedStage("hello " + name);
    }

    @SubCommand("times")
    public CompletionStage<Void> times(String name, int count) {
        // 同步业务结束后返回已完成 Stage；异步业务必须返回其真实完成链。
        return CompletableFuture.completedStage(null);
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

权限写入提供 `addPermissionAsync`、`removePermissionAsync`、`banPermissionAsync`、`unbanPermissionAsync`，均返回 `CompletionStage<Void>`，等待数据库和缓存任务完成，失败向调用方传播。原有便捷方法继续提交任务并处理终端日志，二者使用同一份变更构建逻辑。

权限数据的当前布局为 `namespace=command.permission.enable/disable`、`target=scopeId@permissionId`、`key=userId`，读写一致。当前布局没有该用户键时，兼容读取旧检查器布局 `target=permissionId`、`key=scopeId@userId`；当前布局的显式 false 优先，避免已撤销授权或解除的禁止被旧数据重新恢复。权限仍按原先顺序判断：禁止记录、DEBUG 身份限制、原生角色、额外授权；额外授权不能获得 DEBUG。

命令处理方法必须为 public，返回类型必须实现 `CompletionStage<?>`，通常使用 `CompletionStage<Void>`。扫描器和执行器共用 `CommandMethodContract`；void、非 Stage 和实际返回 null 的方法都会被拒绝。执行器保留 TaskService/namespace，通过完成阶段展开返回值，不等待阻塞线程，也不在内部安装终端错误日志。

`ICommandDispatcher.dispatch(...)` 返回 `CompletionStage<CommandResult>`，`ICommandExecutor.execute(...)` 返回处理方法的最终值。提交过程仍可通过 Dispatch/Execute Event 观察；返回的 Stage 只有在反馈和业务链结束后才完成。同步事件监听器依然可取消或调整错误/帮助处理器，异步观察监听器沿用原 EventService 行为。

| CommandResult 状态 | 含义 |
| --- | --- |
| `NOT_MATCHED` / `DISABLED` | 没有匹配命令 / 命令关闭。未匹配默认不回复。 |
| `CANCELLED` | Dispatch 或 Execute Event 在开始分发/执行前被取消。 |
| `METHOD_NOT_FOUND` / `INVALID_ARGUMENTS` / `DATA_TYPE_MISMATCH` | 未找到处理方法 / 参数错误 / 原始数据类型不适用。 |
| `OUT_OF_SCOPE` / `PERMISSION_DENIED` | scope 或客户端不支持 / 原权限链拒绝。 |
| `HELP` | 帮助反馈链结束。 |
| `COMPLETED` | 处理方法的 Stage 已结束，value 为其最终返回值；不等同于具体业务成功。 |

校验、事件、反射、异步业务和反馈发送的异常由 Stage 向上传播。错误/帮助处理器也返回 `CompletionStage<Void>`；无反馈可返回已完成 Stage，错误处理器未覆盖的方法默认不回复。调用者应在最外层统一处理异常；App 的原生命令接收器在链尾记录日志。

`Void` 只能说明链已结束。原命令的拒绝提示、投票未达到阈值、已有签到等业务结果仍可能通过正常原生反馈表达；需要结构化业务信息时由处理方法返回具体值。取消或发送失败不会撤销已提交的数据库变更或已发生的平台动作。

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
