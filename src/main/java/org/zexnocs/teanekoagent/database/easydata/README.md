# Agent 专用 EasyData

Agent 的作用域配置、空间、事实、人格、命令账本及历史统一保存到 `tea_neko_agent_data`。`TeaNekoAgentEasyDataObject` 继承 Core 的 ID 和 `@Version`，唯一键为 `(namespace, target, o_key)`，近期查询索引为 `(namespace, target, id)`，维护分页索引为 `(namespace, id)`。仓库仅供 Core EasyData 基础设施使用，业务 Store 不操作仓库或 JPA 实体。

| namespace | 数据 |
| --- | --- |
| config | `/cfg` 的 agent 配置，target=agent |
| agent.namespace.v1 | 空间注册、创建者索引、scope 绑定与操作回执 |
| agent.fact.v1 | 按业务空间及 generation 隔离的事实、去重索引、操作回执 |
| agent.personality.v1 | 人格引用、补正、不可变备份 |
| agent.command.v1 | 原生命令工具的执行占用与终态 |
| agent.history.v1 | 按完整有效引用和人格隔离的消息、可排序到期索引 |
| agent.memory.layers.v1 | 与历史同事务采集的短期消息、持久分区目录、压缩租约/水位及中期片段 |
| agent.memory.long.v1 | 自动长期事实/偏好/经历、画像及话题索引、整理水位、本人维护回执和旧证据抑制 |
| agent.personality.observations.v1 | 按 scope/人格身份隔离的真实表达样本、模式和渐进采用时间；与长期水位及人格补正同事务提交 |
| agent.memory.lifecycle.v1 | 空间及分区的无正文定位标记，原目录回收后仍能判定旧代次数据归属 |

普通配置沿用 `TeaNekoAgentEasyData.of(namespace).get(target)` 的缓存读取和 `getTaskConfig(...).set(...).pushAsync()`。构造 Store 时注入根入口，通过 `inNamespace(namespace)` 捕获同一个 EasyData 服务，便于隔离测试及宿主装配。

需要原子修改多个目标时，使用入口的 `transaction(name, supplier)`；它仅组合原有 DatabaseTask 和 `pushAsync()`，沿用现有事务、重试和线程设施。事务内通过 `get(target).atomic()` 取得 DTO：`find` 支持快照、共享锁或写锁，`scan` 支持有界键范围，`recent` 支持 ID 倒序分页。`EasyDataRow` 只公开正文、稳定定位和版本，不能跨仓库或目标保存。所有原子操作必须在活动事务内执行，事务提交后才失效普通缓存；回滚不会发布候选值。

recentNamespace(before, limit) 只读分页当前固定 namespace 的所有 target，仍有一至一千条硬上限；业务拿到其他 target 的行后须通过该目标 DTO 重新锁定，再修改或删除，不能借扫描视图越过原目标边界。维护进度和变更使用同一个事务，普通缓存只在真实提交后失效。使用此能力的实体应提供 namespace,id 索引。

关闭自动 DDL 的既有部署需为新维护分页补齐索引，未在程序外自动执行 SQL：

```sql
CREATE INDEX idx_agent_easy_maintenance ON tea_neko_agent_data (namespace, id);
```

部署前检查是否已存在同名/等价索引，再按原变更流程执行。dev/prod 的 ddl-auto=update 仍由原 Hibernate 配置处理；实际索引、隔离与 EXPLAIN 结果需实机验收。

历史正文与到期索引同事务提交。到期索引采用固定宽度的秒和纳秒排序键，每批最多处理一百项；历史单次至多扫描二百行，过期或过滤不命中的行仍推进游标。空间 clear 先推进 generation，旧代次在 DEBUG 管理入口分批回收。

Agent 尚未上线，本次直接使用新表，不读取、迁移或清理原 `general_data`、`llm_related` 与旧历史表。旧生产模块及其专属测试已移除，旧数据库表不自动删除。默认部署按现有 JPA 建表配置创建新表；关闭自动 DDL 的环境须依据该实体预先建表。离线测试不替代真实 MySQL 的 DDL、事务代理、隔离与索引性能验收。
