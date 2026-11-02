# 事实与偏好的事务存储

独立事实服务提供可信来源、稳定追加动作、完全重复合并、原操作回执及当前 generation 内的原证据读取。[Recall](../recall/README.md) 负责过滤、排序和证据预算，[画像](../profile/README.md) 按 namespace+UUID 组合证据视图，[DEBUG 管理](../admin/README.md) 提供分页、导出、编辑、锁定、克隆、清空及有界回收。

## 业务与时间边界

`MemoryFactAppend` 固定 operationId、MemoryNamespaceReference、主体 UUID、内容及来源。`MemoryFactSource` 保留原请求、稳定 actionId 和完整真实 actor。重试保持原操作 ID，多个动作不能共用一个 ID。调用方复核当前 scope、绑定、明确新增意图和权限，存储不从文本推断授权。

同 namespace 可检索不同用户及 scope 的记录，主体和发起者分别保存。画像是已接受事实与偏好的读取视图，不成为另一份真相；人格补正独立管理。

`MemoryFactValue` 只允许 FACT/PREFERENCE，正文非空且最多 4096 字符；标签最多 16 项，每项非空且最多 64 字符，去重冻结。置信度必须为 0–1 有限值，正负零归一；标签不提升权限，正文和来源不进入异常文本。

事件起止、精度、原表达与记录时间分开保存；单侧端点表示时间点，倒置范围拒绝。记录时间由可信 Clock 固定，重试不重新取时。未知事件时间只有 includeEstimated=true 才回退记录时间。到达 expiresAt 即过期；原记录分页仍包含过期证据，Recall 再筛选，不能把有限页中没有当作确定不存在。

## 专用存储与原子提交

事实与空间元数据统一使用 [Agent EasyData](../../database/easydata/README.md) 的 `tea_neko_agent_data` 表，Store 只操作事务 DTO，复用原任务、事务、Version 和重试。构造无 I/O，提交成功才完成 Stage。

| namespace | target | o_key | 正文 |
| --- | --- | --- | --- |
| agent.fact.v1 | `record:<空间>:g:<generation>` | 首次 operationId | 显式 schema 的事实记录 |
| agent.fact.v1 | `duplicate:<空间>:g:<generation>` | 主体及全部内容摘要 | 首次事实 ID |
| agent.fact.v1 | `operation:<空间>:g:<generation>` | 原 operationId | 完整命令与原回执 |

追加通过事务 DTO 写锁当前空间元数据，核对最新 generation，锁持有至事务结束。clear 与追加按该行协调：追加先提交则被下一代隐藏，clear 先提交则拒绝旧追加。事实、索引和回执一起提交或回滚，不无版本覆盖整列表。

同 operationId 和完整命令返回原回执；变更主体、来源、内容或引用返回 CONFLICT。不同操作只有同主体及全部内容相等才合并为 DUPLICATE，保留首条记录、来源和时间，新来源进入本操作回执。重复不修改 locked 记录。回执只是原提交结果，generation 校验先于旧回执读取，不能用重放绕过 clear。

读取按 UUID 字符串升序分页，检查空间与代次；缺失或失效抛出明确业务异常，不回退旧表或伪装为空。取消观察不能撤销已开始的数据库事务。DEBUG 编辑、克隆和旧代次回收沿用相同事务和来源边界。

Agent 未上线，本次直接使用新表，不保留旧 JSON 导入端口，不读取、迁移或清理旧表。克隆仍保留主体、来源、时间和追溯信息。真实 MySQL 的 DDL、隔离、行锁和索引性能需实机验收。
