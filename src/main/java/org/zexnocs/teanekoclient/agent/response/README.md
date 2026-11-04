# Agent Response

AgentResponseEvent 继承 App ResponseEvent，仅携带原 echo、明确成功标记和具体 DTO。ResponseService 使用原 SendData 的响应类型完成 TaskFuture；内部客户端不回灌普通平台消息。

AgentConversationResponse 只暴露请求标识、业务状态和有序 type/value/mode 内容，执行前失败时尚无请求标识可为空；不序列化交互诊断、工具参数、供应商透明状态或资源。记忆和人格响应只返回实际提交状态与必要标识，不能凭传输任务完成推断副作用成功或回滚。

完整调用和失败语义见[内部客户端说明](../README.md)。停止、重放或事件失败均无自动补发；迟到响应不能重新运行原业务。
