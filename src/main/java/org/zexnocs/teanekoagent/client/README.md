# 内部客户端与三类异步操作

保留客户端 ID `teaneko-agent`、专用 Toolbox 及 `TaskFuture<ITaskResult<具体响应>>` 集成方式。新 Java API 使用当前包，旧包已删除，不保留旧 Runtime、旧数据库入口或源码兼容门面。内部客户端无 WebSocket，只产生 AgentResponseEvent，不发布普通消息/命令事件，不支持原始字符串请求或普通平台工具。

| Sender | 输入 | 响应与行为 |
| --- | --- | --- |
| AgentConversationSender | 真实 ITeaNekoMessageData | AgentConversationResponse：业务状态及正式图文/回复形式；不自动发送外部平台 |
| AgentMemoryWriteSender | 当前用户的真实原文消息 | AgentMemoryWriteResponse：STORED、DUPLICATE 或明确失败/重放状态及可用记录 ID |
| AgentPersonalityCorrectionSender | 真实 OWNER/DEBUG 消息、允许的表达字段 | AgentPersonalityCorrectionResponse：提交状态及是否新增 |

每类 Sender 有可选父 ExecutionControl 重载，父控制由原宿主拥有。直接服务 `AgentOperations.conversation(source, parent)`、`memory(source, parent)`、`correction(source, field, parent)` 返回 CompletionStage；与 Sender 复用同一业务，不另建模型循环。独立对话从原邮箱取得本地配置预算；派生对话共用父预算与取消。组件/响应不保存控制或平台资源。

对话使用当前原生图文，不允许调用方填写其他用户、scope 或不受保护的会话 ID；同 client/scope/实例沿用原共享邮箱。工具身份租约保留到原结果复核结束。公开响应只含正式输出，供应商透明状态、工具参数及 Prompt 不序列化。图片能力仍由实际模型声明。调用方若自行发送返回内容，必须继续复核当前权限/引用与原平台状态；需要完整托管发送时使用现有显式聊天或自动入口。

记忆新增只认当前原文的明确“请记住：正文”等格式，复用原 MemoryAddIntent 和 MemoryAccessService；主体固定为本人，实际写入还检查当前开关及空间代次，不能借 Sender 编辑/清空或写其他人。人格输入只提供表达字段和当前原文，基础人格/补正引用由原服务解析；既有字段白名单、学习开关、绑定、锁定和版本仲裁继续生效，自定义只读人格源没有候选能力时明确拒绝。

所有操作先以原生来源+操作类别取得 CLIENT_ENTRY 持久化回执；echo 只匹配传输响应。相同来源重复、冲突或旧结果不明返回 REPLAY_*，不重新执行或恢复原答案；改变正文而复用来源不能覆盖旧操作。写入已开始后取消不声称回滚。服务至多六十四个在途操作；关闭取消自有控制和实例，不关闭共享邮箱、执行器或借用父控制。

原 SenderService 注册 echo 并等待 ResponseService 解析具体 DTO；业务实际结束后内部 Client 发布一次响应。发送/响应事件取消或发布失败明确结束原 Future，不靠五分钟超时或自动补发恢复。模型失败、STOPPED、CONFLICT、REPLAY_* 不能因传输任务完成而报告为业务成功，调用方应检查 isSuccess 和 DTO.status。

离线用例复用真实邮箱、模型替身、事务夹具、SenderService 与 ResponseService，覆盖三类实际操作、角色/意图/开关、父取消、防重及事件取消；未发送外部平台消息。配置和实机验收见[部署说明](../../../../../../../docs/development/agent-deployment.md)。
