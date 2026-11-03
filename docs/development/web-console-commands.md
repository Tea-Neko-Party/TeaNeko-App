# 客户端指令台

在“聊天设置”选择平台、账号和聊天，点击“打开客户端指令台”。页面显示实际连接状态、已声明的管理方法及 `@Description` 用法。入口继续使用原登录会话、来源检查和 CSRF，不能执行操作系统命令或提交任意 OneBot JSON。

## 本机启用

在 `config/web/security.yml` 明确配置并按原流程重载，随后重新登录：

```yaml
console-prefixes: ['/console']
```

旧配置缺少此项时默认不开放命令。前缀最多 32 项；只允许已注册且方法带 `@ManagementCommand`、接收受信管理上下文的入口。仅添加 `/agent` 等原生命令前缀不会使其自动支持指令台。该设置属于认证根配置，不能在网页修改，变更撤销旧会话能力。

目前提供：

- `/console`：输出固定目标及来源说明，只在指令台显示。
- `/console say 大家好`：向所选真实聊天发送一条文本，等待实际平台消息号。
- `/console help`、`/console help say`：在指令台显示原方法用法。

提交前确认目标和影响。输入原文最多 4096 字符；实际发送每条最多 2000 字符、每个操作最多五条。本批没有把依赖原始引用、平台成员或消息身份的原生命令改为管理调用。

## 来源、连接与权限

管理调用使用 `CommandOrigin.MANAGEMENT` 和服务器固定的 `web.local-admin`，原权限等级固定为 MEMBER；它表示命令系统中的执行上限，不表示操作者是某个群成员。不会因网页登录而成为 OWNER 或 DEBUG。原命令权限、客户端范围、作用域、插件归属、事件和参数绑定仍会执行；NativeCommandBridge 的 USER 原生来源约束保持不变。

方法没有明确管理声明或上下文签名不合法时拒绝；实际排队启动时再次检查会话和插件代次。异步业务在自己的每次副作用前仍须调用管理上下文 `checkActive()`。上下文提供的发送能力会再次检查原会话、本机前缀、插件版本、目标及原连接。

OneBot 首期要求唯一开放连接、该连接已经确认自身账号、所选账号匹配、目标可解析且原平台为 DEFAULT 状态。未连接、账号未知、账号不匹配、多连接和限用状态分别展示；不以缓存机器人号或生命周期运行标记证明可操作。传输写入锁定原会话及连接代次，断线或换号后拒绝，不遍历其他会话、不自动重试或更换连接。真正多账号定向执行尚未开放。

普通消息发送继续使用原 OneBot 消息事件、状态管理和响应解析；没有伪造原平台消息来绕过 STOP、DEBUG 等限制。写入 WebSocket 不代表消息已被平台接受，只有原响应返回有效消息号才记为已确认。

## 受理、回执与重试

输入使用操作 UUID、原目录连接版本和服务端观察时刻。新操作必须使用五分钟内的目录；过期后先刷新再明确提交。原请求只在页面内存保留，不进入浏览器存储；受理确认后释放原输入。重连或刷新页面不会自动提交旧文本。

| 状态 | 含义 |
| --- | --- |
| ACCEPTED | 原请求已经持久受理，不证明命令已启动 |
| RUNNING | 原命令处理链执行中，尚未证明业务或发送成功 |
| COMPLETED | 原处理链或帮助反馈结束；具体发送看已确认消息号 |
| REJECTED | 前置检查拒绝或原分发状态没有完成处理；不声称撤销此前外部动作 |
| INDETERMINATE | 实际结果未确认，或原进程/工作拥有者丢失；不自动重放 |

页面可见时每两秒查询受理操作，离开或隐藏停止查询。断网不等于未受理，可按原 UUID 查询或原样重试；相同编号绑定同主体、目标、原文、连接和时刻，改变参数会被拒绝。不同编号代表新的明确操作，不能用它猜测上次是否发送。

回执位于通用 EasyData 的 `web.console.v1 / operations` 域，通过原 DTO 事务访问；没有业务层 Repository。保留一天、最多 256 项，窗口满拒绝新受理。账本保存摘要、原分发状态、有界明确输出及消息号，不保存输入命令原文、反射返回对象或异常链。原请求的五分钟新鲜度限制可避免回执淘汰后无意重放旧请求。

当前最多八个实际工作；受理 HTTP 返回不释放工作槽，物理处理及事实保存结束后才释放。业务链结束立即撤销来源，回执仍在保存时也不允许迟到副作用。单次宿主能力最多五分钟，超时后不得新增副作用；不能保证外部不响应取消的 IO 已停止。退出或权限撤销后，不再授予执行能力，但仍允许宿主把已经发生的结果保存到原回执。重启后孤立非终态标为不确定，不恢复执行。

## 插件开发

插件命令仍用原 `@Command`、`@DefaultCommand` 或 `@SubCommand` 注册 Spring Bean。管理方法额外声明 `@ManagementCommand`，参数必须为具体 `CommandData<ConsoleInvocation>`；返回 `CompletionStage<?>`。使用 `print` 在指令台输出，使用明确声明发送能力的 `send` 向固定目标发送；不要输出凭据或原始异常。

```java
/**
 * 插件的受限管理命令。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Command(value = "/pluginstatus", permission = CommandPermission.ALL, scope = CommandScope.ALL)
public class PluginStatusCommand {
    /**
     * @param command 受信管理来源
     * @return 指令台输出完成阶段
     */
    @DefaultCommand
    @ManagementCommand
    @Description("查看当前目标。示例：/pluginstatus；不向真实聊天发送。")
    public CompletionStage<Void> status(CommandData<ConsoleInvocation> command) {
        var context = command.getRawData();
        context.checkActive();
        return context.print("当前目标：" + context.targetName());
    }
}
```

需要发送时声明 `@ManagementCommand(sendsMessages = true)` 并等待 `context.send(text)`；不声明不能调用发送能力。插件自己的数据库、文件或其他副作用也须在真实执行前检查上下文，不能仅在入队时检查。沿用 `@PluginOwned`，停用或换代后原调用不能继续借用管理发送能力。

平台管理适配器实现原 `IClientManagementAdapter.consoleState` 和 `consoleLease`，默认不支持。Lease 固定真实客户端类型、目标、连接代次、当前守卫和发送方法，发送方法返回实际消息号；不提供任意原始 API 或浏览器自报身份。具体实现仍由现有注解、Spring 和 scanner 发现，不修改中央控制器增加平台分支。

HTTP 路径均为 `/api/v1/clients/{client}/accounts/{account}/targets/{target}/console` 下的 GET 目录、POST 提交和 GET `operations/{UUID}`。路径引用由原账号/聊天目录提供，不作为授权票据。真实平台断线、状态管理、权限撤销和消息回执仍须在部署环境验收；开发测试只使用离线会话和事务夹具，不发送真实消息。
