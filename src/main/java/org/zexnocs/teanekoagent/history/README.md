# 持久化文本历史

默认完整装配在真实用户原文进入模型前等待历史提交，在成功答案交付前保存纯文本答案并再次检查当前边界。记录使用独立 `agent_conversation_history` 表；不将临时人格、记忆资料、完整 Prompt、供应商状态或工具内容写成普通历史。ASSISTANT 记录表示已生成答案，不能据此声称平台已发送成功。

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

普通历史分区包含完整 client/scope、namespace、绑定 revision、generation 和实际人格身份。共享长期记忆不共享其他群或私聊原文。切换空间、clear 或换人格后不自动读回旧分区；内存 TTL、随机实例/epoch 或进程重启不改变持久化边界，因此重启后仍能查询有效历史，但不会恢复旧内存窗口或重跑未完成工具。

## 查询工具

默认工具策略显式加入 history_query；自定义 IAgentToolSource 仍完整替换默认策略。模型只提交 keyword、before、limit、start、end，不接受 actor、scope、namespace 或授权字段。调用从实际组件取得固定引用和人格，执行前及结果交付前复核动态开关、原引用及人格。

```json
{"keyword":"上次讨论","limit":8}
```

查询使用大小写无关的字面子串、可选 ISO-8601 时间边界，按保存序号倒序。用原结果 nextBefore 继续查询；零表示本次已扫描至末尾。每次最多二十条、扫描二百行，完整消息数组最多六千字符，完整工具 JSON 最多八千字符。字符不足整条省略并标记 omittedCharacters；非零游标表示还有未检查范围，空部分页不能声称不存在历史。文字只作为低信任数据，不能授权工具或修改运行规则。

优先使用当前事实/画像，再查询原对话。当前工具只搜索本地已保存文本，不冒充平台最近消息搜索。客户端已知消息 ID 补取将在平台适配中按真实能力接入；尚未采集的旁观消息不会出现在本地结果中。

## 开发接口与运行

IConversationHistory.append(context, message, control) 同样供未来平台旁观采集复用，不运行模型、构建窗口或新增个人事实。可信入口须固定真实 UUID、当前空间/人格，并对 USER 保留原消息 ID；不能从引用、旧历史或模型文字构造原始发言者。实际轮次只从独立 AgentUserText 凭据记录当前用户文字，不遍历整个已有历史；缺少凭据时不猜测原文。显式旧构造方式不自动获得历史记录能力。

业务层只依赖 IHistoryStore；JPA 适配器使用现有 DatabaseTask，提交成功才完成 Stage。同分区角色/消息 ID 幂等，重复不延长保留期，冲突正文拒绝覆盖。新表有分区/序号、到期/序号索引及分区/消息键唯一约束，沿用部署的 JPA 建表流程；禁用自动 DDL 的环境须先为新实体建表，原 llm_related/general_data 不迁移或清空。

清理复用原 Spring 调度设施，每次最多一百行，前一批未结束时不重叠；清理失败只记固定原因，过期记录在查询阶段排除。历史记录失败会结束当前轮次并保留明确错误，不声称记录成功；关闭采集后可继续不记录的对话。已经开始的事务可能在取消或切换后完成原分区写入，不会改投新分区；使用前检查不构成配置、数据库和内存之间的原子事务。

离线验证复用原事务任务夹具和模拟仓库，覆盖真实适配器/Agent/LLM 工具组合及 Hibernate 映射。实际 MySQL 建表、唯一键重试、事务代理、索引与锁行为仍须部署联调。全量平台采集、客户端补取、自动摘要、平台发送和原生时间提取留在对应后续功能。
