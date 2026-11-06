# 持久化文本历史

默认完整装配在真实用户原文进入模型前等待历史提交，在成功答案交付前保存纯文本答案并再次检查当前边界。记录通过专用 EasyData 保存在 tea_neko_agent_data 的 agent.history.v1 域；不将临时人格、记忆资料、完整 Prompt、供应商状态或工具内容写成普通历史。ASSISTANT 记录表示已生成答案，不能据此声称平台已发送成功。

2026/11/02：默认历史事务同时采集 [分层短期消息](../layered/README.md) 和持久目录，失败一起回滚。分层资料可在重启后恢复同 scope 的近期上下文，并自动生成持久中期片段；原历史查询边界和已有数据保持原语义。

2026/11/03：默认轮次记录器在模型执行前通过 ExplicitMemoryCapture 等待明确保存，即使关闭历史采集也按独立 memoryAddEnabled 保存，不依赖模型调用工具；这一步的事实事务与历史事务各自提交，失败不会假称另一事务回滚。自定义记录器需自行承担同一前置职责，原独立构造不自动安装。

平台送达另由 IAgentDeliveryStore 持久化，按原 scope/引用及请求 UUID 记录逐段占用和确认。原历史查询保留生成原文语义；分层近期注入和后台摘要额外读取回执，PARTIAL 仅使用已确认正文，UNKNOWN 不冒充已发送。没有平台动作的直接服务调用仍属于未确认生成，ACKNOWLEDGED 只表示平台接受且不表示已读。细节见[回执契约](../layered/README.md#明确保存与平台回执)。

## 配置与边界

```text
/cfg set agent historyCaptureEnabled false
/cfg set agent historyReadEnabled false
```

两项均默认 true，真实注册并启用 agent 是前提。采集关闭时不新增记录，查询开关独立；注销或 enabled=false 后不允许两种业务。原 cfg 权限链继续执行，不能通过字段修改历史分区。

本地配置：

```properties
tea-neko.agent.history.retention-days=7
tea-neko.agent.history.cleanup-interval-ms=60000
```

保留期按发生时间计算，允许一至三百六十五天，懒读取非法值会拒绝该次写入。没有原始发生时间的轮次记录明确标记 estimatedTime=true，使用处理时间。时间统一为 Instant，精度对齐 MySQL 微秒；正文最多 65536 个 Java 字符，超过时标记 truncated，不省略来源。

普通历史分区包含完整 client/scope、namespace、绑定 revision、generation 和实际人格身份。默认历史接口不共享其他聊天原文；可选 `shared-memory` 组件通过独立端口召回同空间同用户的群聊资料，保留来源并复核绑定，私聊原文不向其他聊天公开。详见[共享回忆](../../../../../../../../../docs/development/agent-social-memory.md)。切换空间、clear 或换人格后不自动读回旧分区；内存 TTL、随机实例/epoch 或进程重启不改变持久化边界，因此重启后仍能查询有效历史，但不会恢复旧内存窗口或重跑未完成工具。

## 查询工具

默认 builtin-tools 能力显式加入 history_query；v4.6.0-l 的自定义 IAgentToolSource 作为宿主额外来源组合，插件通过 AgentFeature 贡献，不再替换内置策略。模型只提交 keyword、before、limit、start、end，不接受 actor、scope、namespace 或授权字段。调用从实际组件取得固定引用和人格，执行前及结果交付前复核动态开关、原引用及人格。

```json
{"keyword":"上次讨论","limit":8}
```

查询使用大小写无关的字面子串、可选 ISO-8601 时间边界，按保存序号倒序。用原结果 nextBefore 继续查询；零表示本次已扫描至末尾。每次最多二十条、扫描二百行，完整消息数组最多六千字符，完整工具 JSON 最多八千字符。字符不足整条省略并标记 omittedCharacters；非零游标表示还有未检查范围，空部分页不能声称不存在历史。文字只作为低信任数据，不能授权工具或修改运行规则。

优先使用当前事实/画像，再查询原对话。当前工具只搜索本地已保存文本，不冒充平台最近消息搜索。客户端已知消息 ID 补取将在平台适配中按真实能力接入；尚未采集的旁观消息不会出现在本地结果中。

## 开发接口与运行

IConversationHistory.append(context, message, control) 已由 [平台旁观采集](../../../integration/message/README.md) 复用，不运行模型、构建窗口或新增个人事实。可信入口固定真实 UUID、当前空间/人格，并对 USER 保留原消息 ID；不能从引用、旧历史或模型文字构造原始发言者。实际轮次只从独立 AgentUserText 凭据记录当前用户文字，不遍历整个已有历史；缺少凭据时不猜测原文。原生入口提供实际平台时刻，采集与轮次以相同正文和时刻防重；旧两参数凭据仍标注为处理时间估算。显式旧构造方式不自动获得历史记录能力。

业务层只依赖 IHistoryStore；EasyDataHistoryStore 经专用 DTO 保存到 tea_neko_agent_data，复用现有 DatabaseTask，提交成功才完成 Stage。同分区角色/消息 ID 幂等，重复不延长保留期，冲突正文拒绝覆盖。历史正文与可排序到期索引同事务保存，分页和清理均限制原始扫描量。专用表及部署方式见 [Agent EasyData](../../../database/easydata/README.md)，旧表不读取、迁移或清空。

清理复用原 Spring 调度设施，每次最多一百行，前一批未结束时不重叠；清理失败只记固定原因，过期记录在查询阶段排除。历史记录失败会结束当前轮次并保留明确错误，不声称记录成功；关闭采集后可继续不记录的对话。已经开始的事务可能在取消或切换后完成原分区写入，不会改投新分区；使用前检查不构成配置、数据库和内存之间的原子事务。

离线验证复用原事务任务夹具和模拟仓库，覆盖真实适配器/Agent/LLM 工具组合及 Hibernate 映射。原生旁观文本采集、已知引用补取、原生时间及平台图文发送已接入；持久化历史仍仅保存字面文本，不保存图片内容或链接。实际 MySQL 建表、唯一键重试、事务代理、索引与锁行为，以及真实平台联调仍须验收。
