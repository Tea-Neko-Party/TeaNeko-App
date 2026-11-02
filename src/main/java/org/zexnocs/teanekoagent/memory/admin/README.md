# DEBUG 记忆管理

先在来源作用域登记并启用 Agent。下列入口必须通过原命令链的 DEBUG 权限，服务还会复核真实原生角色、当前启用及取消；OWNER 或额外普通权限不能替代 DEBUG。普通用户仍只能明确请求新增本人事实，OWNER 可切换空间，维护与新增分别授权。

## 命令

| 命令 | 行为 |
| --- | --- |
| `/agent memory <空间> [代次=-1] [游标]` | 查看空间 revision/generation、事实 ID、事实 revision、主语和锁定状态，每页最多五条。 |
| `/agent exportmemory <空间> [代次=-1] [游标]` | 每页一个完整 JSON 事实，包含来源、时间、版本、锁定和原始证据；空页或 nextCursor=null 表示结束。 |
| `/agent editmemory <空间> <代次> <事实UUID> <事实revision> <正文>` | 仅替换正文，保留主体、原来源、标签、置信度、事件时间、有效期及锁定状态。 |
| `/agent lockmemory <空间> <代次> <事实UUID> <事实revision> <true或false>` | 显式锁定或解锁，不接受其他布尔文本。 |
| `/agent clonememory <源空间> <代次> <空间revision>` | 原子复制当前代次的完整有界事实快照到新空间，返回新空间 ID，不自动绑定。 |
| `/agent clear <空间> <代次> <空间revision>` | 先提交逻辑清空及 generation 换代，再同步引用该空间的所有已知群/私聊会话，并回收首批旧行。 |
| `/agent purgememory <空间> <旧代次>` | 每批至多回收一百行，重复执行至返回零；拒绝当前或未来代次。 |

首次查看可以省略代次，后继页面必须带回原 generation 和 nextCursor；clear 后旧分页明确失败，不把两个代次混合导出。导出是独立管理数据，不注入模型；长 JSON 通过原转发发送器拆成不超过三千字符的片段，按顺序拼接可还原原文，不截断代理对。单页 UTF-8 上限为 131072 字节，超限明确失败。

示例中的版本和 UUID 应取自实际查询结果；revision 不是所有记录共用的计数。

```text
/agent memory default
/agent exportmemory default 0
/agent lockmemory default 0 <事实UUID> 0 true
/agent editmemory default 0 <事实UUID> 1 修正后的事实
/agent clonememory default 0 0
/agent clear default 0 0
/agent purgememory default 0
```

## 提交与数据边界

原消息和管理动作产生固定操作 ID，完整身份、参数、预期版本及成功回执一同落库。相同操作重放返回原回执，改变参数明确冲突；历史回执不代表当前仍处于该版本，应重新查询后再发起新的管理操作。事务内及提交前复核授权，检查观察到授权失效或取消时回滚未提交事务；检查后的配置/取消变化与数据库提交不构成原子边界。提交、会话同步和平台反馈也不是一个分布式原子事务，观察失败不能证明没有提交。

编辑使用事实 revision 做比较，更新正文时同步维护内容去重索引；撞到其他既有内容则整批冲突，不隐式合并或覆盖。普通追加无法解锁或改写已锁定记录，DEBUG 可按版本修正。原追加与管理回执保留历史事实，不通过旧成功结果恢复被编辑的数据。

克隆使用源空间锁和当前读固定快照，包含当前代次的过期及锁定事实，保留主语、原时间和来源，每条复制记录有独立 ID 及原始快照证据。目标画像从目标事实重新构建；不复制 scope 绑定、人格补正、聊天历史、会话、命令执行权或计划任务。新空间记录 DEBUG 创建来源，并使用同一创建配额。目标、全部事实、配额和回执同事务提交，失败不会留下空目标或部分数据。

clear 会影响所有共享该空间的 scope。元数据换代提交后，普通读取立即只见新代次；旧轮、旧后台任务和旧请求追加受到 generation/epoch 校验。已知共享会话跨实例、跨 scope 同步换代；同步失败会明确报告，后续请求仍需重新读取权威引用。人格补正独立保留，其他空间和克隆空间不受影响。

物理回收通过专用 EasyData 事务 DTO，只处理 tea_neko_agent_data 中 agent.fact.v1 的指定旧代次 record/duplicate/operation 三类目标，按固定批量删除。不会删除管理回执、命令防重账本、其他业务域、人格或历史。Agent 尚未上线，不提供旧表导入路径。

## 配置与实现

`tea-neko.agent.memory.management.max-records` 默认 1000、范围 1–10000；`max-bytes` 默认 1048576、范围 1024–16777216。它们限制编辑所需的索引修复快照及完整克隆，超限返回 TOO_LARGE，不生成截断快照。配置懒读，非法值只使相关管理操作失败。普通只读分页和 clear 不依赖完整快照预算。

业务层只公开管理命令、结果及存储端口；适配器通过 EasyData 复用既有 DatabaseTask、空间锁、事实格式和索引。管理回执保存在 tea_neko_agent_data 的 agent.namespace.v1/management-operation 域；不把平台对象、控制句柄或 JSON 类名存入账本。管理查询使用事务 DTO 当前读，防止较早的配置读取使等待空间锁后的事实或回执落后。

`/cfg set agent memoryNamespace <已有空间>` 与 `/agent setnamespace` 共用 [原绑定服务](../namespace/README.md)。`/cfg agent` 显示权威值，`/cfg all agent` 显示字段说明；重复注册/注销配置保留原绑定和空间，重新启用继续沿用。ConfigData 中没有第二份 memoryNamespace，插件应使用绑定端口；correctionKey 与密钥仍不是普通 `/cfg` 字段。

离线验收覆盖实际命令/DEBUG、原子编辑索引、克隆及失败回滚、并发清空防重、授权失效、旧写入拒绝、分批回收和跨群会话失效。真实 MySQL 隔离、锁等待/重试、排序规则和 OneBot 长转发仍需实机验证。人格补正 key 的 DEBUG 编辑、克隆及备份由独立 [人格管理](../../personality/README.md#debug-补正管理) 提供，不与记忆 namespace 混用。
