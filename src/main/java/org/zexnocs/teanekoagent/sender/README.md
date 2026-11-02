# Agent Sender

三类 Sender 继续使用原 App SenderService、SentEvent、echo、ResponseService 和 TaskFuture，不创建第二套异步框架。输入、返回 DTO、直接服务、预算、授权、防重和失败语义统一维护在[内部客户端说明](../client/README.md)。

发送不自动重试；内部对话只返回正式内容，由入口决定外部平台交付。[平台回复计划](../adapter/platform/README.md)支持图文及模型选择的引用、提及和分段。
