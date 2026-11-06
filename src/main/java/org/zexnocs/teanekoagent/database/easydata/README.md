# Agent 专用 EasyData

4.8.5-l 起，长期业务状态保存在 `tea_neko_agent_data`；可丢弃的复查数据保存在 `cleanable_data`。两表沿用 Core 的 ID、`@Version` 和唯一键 `(namespace, target, o_key)`，通过同一 EasyData 事务提交。业务 Store 不直接操作仓库或 JPA 实体。

| namespace | 持久数据及保留原因 |
| --- | --- |
| `config` | `/cfg` 的 Agent 装配和参数，target=`agent` |
| `agent.chat-directory.v1` | 已知聊天及名称；迁移、重启后仍可选择旧聊天 |
| `agent.namespace.v1` | 空间、创建者索引、绑定、代次、管理回执；维持授权与清空/克隆的原操作结果 |
| `agent.fact.v1` | 事实、来源去重及维护回执；避免重复录入和旧事实复活 |
| `agent.personality.v1` | 人格、补正、备份及原管理回执；支持还原、重试核验及绑定仲裁 |
| `agent.command.v1` | 命令占用、身份摘要和终态；不存命令正文，未知副作用不能自动重试 |
| `agent.history.v1` | 会话上下文及到期索引；按原保留期清理，仍是有效业务输入 |
| `agent.memory.layers.v1` | 短中期消息、分区、租约和整理水位；清理必须遵守保留期与代次 |
| `agent.memory.long.v1` | 长期结论、画像、证据及索引、整理水位、遗忘标记和原维护回执 |
| `agent.delivery.v1` | `claims:receipts:*` 仅保存内容/身份摘要及发送状态；临时回执丢失后仍禁止重复发送 |
| `agent.personality.observations.v1` | 表达样本、模式及采用时间；样本到期清理，采用标记避免旧证据再次续期 |
| `agent.memory.lifecycle.v1` | 无正文的代次/分区定位标记，保留迟到任务的归属判断 |
| `agent.sticker.v1` | 素材目录、删除决定、配额和防重；仅需执行或释放资源的作业留在主目录 |
| `agent.schedule.v1` | 计划规格、状态、版本及到期索引；终态阻止重启补跑和旧请求复活任务 |
| `agent.social.v1` | 空间及真实用户的关系、心情、未回复关怀次数、未决发送和来源摘要；防止重复加分或重启追问，旧代次有界回收 |

记忆、人格管理回执仍承担持久业务合同：按原主体和完整参数核验重放，返回原提交结果，关联克隆、备份或遗忘决定。这些不是诊断日志，不能仅因带有 `operation` 名称而清理。

## 可清理数据

通过 `data.cleanable()` 获取同 namespace 的临时表视图，继续使用 `get(target).atomic()` 与原事务。不能把执行权、租约、业务水位或唯一防重证据写入临时表。

| namespace / target | 内容与清理行为 |
| --- | --- |
| 各维护域 / `maintenance:<实体名>` | 可重建的分页游标；删除后从首页继续维护，不改变业务整理水位 |
| `agent.delivery.v1` / `receipts:*` | 逐段回执正文与观察时间；随原历史保留期、空间退役逐页回收 |
| `agent.schedule.v1` / `management` | 最多 256 条、一天内的管理回执；清理后查询显示不存在，任务版本继续拒绝旧操作 |
| `agent.sticker.v1` / `catalog:*` 的 `observations` | 已结束且完成资源清理的作业、短期管理回执；沿用一天窗口及数量上限 |
| `agent.memory.layers.v1` / `shared:*` | 同用户跨聊天的原消息指针，无正文；按原消息到期和空间代次回收，可由维护任务补建 |

清理临时表只让复查信息缺失，不重新取得执行权。仍有文件、额度或刷新占用需要释放的表情包作业不能提前迁出。请求上下文不写这两张表；网页活动时间线仅保留有界内存窗口。

## 迁移与事务

首次访问旧记录时，在同一事务中先写新记录，再删除旧内容；任一步失败全部回滚。发送回执和维护游标也由有界维护页逐步迁移；图库和计划管理回执在对应事务触发迁移，不在启动时全表重写。

升级前停止旧进程并备份数据库；不混跑新旧版本。迁移后回退到旧程序需要恢复迁移前的一致备份，旧程序无法读取分流后的防重及观察结构。此次开发未修改真实数据库。

普通配置沿用 `TeaNekoAgentEasyData.of(namespace).get(target)` 的缓存读取和 `getTaskConfig(...).set(...).pushAsync()`。构造 Store 时注入根入口，通过 `inNamespace(namespace)` 捕获同一个 EasyData 服务，便于隔离测试及宿主装配。

需要原子修改多个目标时，使用入口的 `transaction(name, supplier)`；它仅组合原有 DatabaseTask 和 `pushAsync()`，沿用现有事务、重试和线程设施。事务内通过 `get(target).atomic()` 取得 DTO：`find` 支持快照、共享锁或写锁，`scan` 支持有界键范围，`recent` 支持 ID 倒序分页。`EasyDataRow` 只公开正文、稳定定位和版本，不能跨仓库或目标保存。所有原子操作必须在活动事务内执行，事务提交后才失效普通缓存；回滚不会发布候选值。

recentNamespace(before, limit) 只读分页当前固定 namespace 的所有 target，仍有一至一千条硬上限；业务拿到其他 target 的行后须通过该目标 DTO 重新锁定，再修改或删除，不能借扫描视图越过原目标边界。维护进度和变更使用同一个事务，普通缓存只在真实提交后失效。使用此能力的实体应提供 namespace,id 索引。

关闭自动 DDL 的既有部署需为新维护分页补齐索引，未在程序外自动执行 SQL：

```sql
CREATE INDEX idx_agent_easy_maintenance ON tea_neko_agent_data (namespace, id);
CREATE INDEX idx_cleanable_easy_maintenance ON cleanable_data (namespace, id);
```

部署前检查是否已存在同名/等价索引，再按原变更流程执行。dev/prod 的 ddl-auto=update 仍由原 Hibernate 配置处理；实际索引、隔离与 EXPLAIN 结果需实机验收。

历史正文与到期索引同事务提交。到期索引采用固定宽度的秒和纳秒排序键，每批最多处理一百项；历史单次至多扫描二百行，过期或过滤不命中的行仍推进游标。空间 clear 先推进 generation，旧代次在 DEBUG 管理入口分批回收。

这里的分流只涉及当前 Agent 数据域，不自动删除或搬迁 `general_data`、`llm_related` 等旧表。关闭自动 DDL 的环境须预先准备实体对应的表与索引；真实 MySQL 的事务隔离与索引性能仍需部署环境验收。
