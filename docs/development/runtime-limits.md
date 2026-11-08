# 任务与管理调用的运行边界

| 入口 | 默认限制 | 配置或观察入口 |
| --- | --- | --- |
| Core Task | 合计 1024 个排队、执行或等待外部回执的任务 | Spring 属性 `tea-neko.tasks.max-in-flight`；`TaskService.inFlightCount()` |
| Web 管理资源 | 总计 32 个、每资源 4 个；等待 30 秒 | `config/web/management.yml`；运行管理的 Core 任务页、`GET /api/v1/management-calls` |
| OneBot 入站 | 64 个完整消息处理；API 回执另保留 32 个 | `OnebotClient.getInboundCount()`；饱和时关闭连接并返回 WebSocket 1013 |
| OneBot 出站 | 64 个传输写入调用 | `OnebotClient.getOutboundCount()`；既有帧大小、发送时限和缓冲限制继续生效 |

`management.yml` 支持 `timeout-millis`（100～300000）、`max-in-flight`（2～256）和 `max-per-resource`（1～总数减一）。新调用读取当前配置；降低限额不会销毁已经开始的工作。

等待结束与工作结束是两个状态。任务到期或取消时撤销后续执行资格：排队任务不再启动，运行线程收到中断；阶段入口和数据库提交前复核资格。数据库任务在完整事务代理内延迟线程中断，让提交前校验触发正常回滚，避免中断破坏回滚连接；事务结束后才投递中断。无法立即停止的 Callable、外部请求或写入继续占用容量，直到实际收尾。未知写入不会因超时而自动重试，迟到结果不能把原失败回执改成成功。数据库已提交、平台已接收的动作不能由取消撤回。

管理等待到期返回 `504 EXECUTION_WAIT_ENDED`，容量不足返回 `429 EXECUTION_BUSY`。网页保留原业务操作编号，通过对应资源的查询操作确认结果。管理调用列表中的宿主请求号与业务操作号不同；列表只显示资源、占用时间和等待状态，不显示正文或主体。

新增 `IManagementResource.execute()` 必须返回覆盖真实业务收尾的阶段，不能返回提前结束的超时包装。事件监听器同步登记异步业务，客户端使用 `pushEventWithCompletion()` 等待整条通知链与已登记业务；普通 `pushEventWithFuture()` 保留原通知完成合同。
