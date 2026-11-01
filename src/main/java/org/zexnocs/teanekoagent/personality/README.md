# 基础人格与独立补正

默认完整装配通过 `PersonalityConfig` 向独立轮次和会话邮箱提供同一个 `IAgentPersonalitySource`。人格已实际进入模型输入，补正、备份和绑定保存到 `general_data` 的独立 `agent.personality.v1` 业务域；不与用户画像或 memory namespace 混存，不新增线程池。

## 配置与使用

继续使用 `config/agent/personality.yml` 的 `default-agent-id`、`characters`、identity、core-traits、speaking-style、boundaries 和 learning-policy 布局，模板提供可直接使用的 teaneko / TeaNeko 示例。旧文件的空 characters 列表在选择 teaneko 时兼容内置中文设定；其他不存在的 ID、重复 ID、加载失败或非法字段明确拒绝该次 Agent 调用，不静默换角色。

已登记 scope 可以沿用原 `/cfg` 权限设置：

```text
/cfg set agent personalityId teaneko
/cfg set agent customPersonality 一位喜欢自然交流的技术伙伴
/cfg set agent customSpeakingStyle 表达清晰简洁
/cfg set agent customPersonalityEnabled true
/cfg set agent personalityCorrectionsEnabled false
/cfg set agent personalityLearningEnabled false
```

personalityId 为空时使用文件默认。自定义描述替换人格描述层，保留基础身份约束和硬边界，不能修改运行权限。开启自定义前先设置有效正文；自定义正文和风格各最多 4096 字符。应用补正和生成/接受候选是独立开关，均默认 true；关闭应用不删记录，也不自动关闭独立学习。学习开关不能授权写用户事实。

correctionKey 不属于普通 cfg 字段。可信管理端口 `IPersonalityStore.bind` 支持绑定已有或新空 key，后续聊天管理入口必须经过真实 DEBUG 权限；当前没有向用户或模型开放任意 key、克隆、编辑或导出工具。

## 轮次、历史与重放

邮箱在历史装配前完成准入及人格解析，固定实际人格身份并预留完整协议字节。独立轮次再次解析实际来源，核对邮箱身份提示，不接受请求携带人格正文。基础人格以 SYSTEM 人格层加入原前导运行规则之后，来源补正仅以 USER 数据加入，不能升为规则或权限。

实际输入同时计入人格、补正、参考资料、原问题和历史的完整协议预算；按完整旧轮次缩小窗口，不能静默删掉基础人格。历史提交仍验证实际前缀、原输入投影、工具闭环和最终候选；窗口不重复保存人格或补正。基础内容指纹覆盖实际内容，修改正文而未递增配置版本也会触发身份更新。

人格绑定或应用开关改变后，当前 scope 的短期窗口/完成缓存换代，旧等待项失效。每次模型、工具、最终交付及缓存重放只读复核当前配置和原绑定，旧运行不能公开新身份下的回答。补正 revision 更新从下一轮生效，不中途替换当前快照；已采用补正到期后不能继续使用或重放。

## 持久化生命周期

首次按 `scope-<scopeId>` 初始化空补正。基础人格变化时，在一个 DatabaseTask 事务中锁定当前绑定及旧补正，保存 `backup-<原key>-<时间>-<唯一序号>` 的不可变副本，创建当前 scope 的新空 key，再更新绑定 revision。其他引用原 key 的 scope 保留原补正；长期记忆、namespace 和其他 scope 的会话不被清除。

补正以有容量上限的版本集合保存，每空间最多 64 条；追加、切换及共享写入使用同一行锁顺序和既有 Version 仲裁，提交成功才完成 Stage。达到上限明确拒绝，不悄悄覆盖来源。备份不允许作为可写空间绑定。旧绑定的迟到候选即使来源相同也不能改投新 key。

宿主可将真实互动产生的候选交给 `PersonalityService.propose(scope, basis, candidate, control)`；basis 必须是产生候选时的实际快照，candidate 保留原请求 ID、真实用户/来源和时间。请求 ID 提供幂等，相同 ID 改内容拒绝。只接受基础版本兼容、置信度至少 0.2 且位于代码和文件共同白名单内的表达细节/关系表达；身份、硬边界、工具、密钥和用户画像字段不在白名单。普通候选不能授予 locked，已有锁定字段不能被后续候选覆盖。

取消、配置变化或交付前拒绝不意味着已经开始的事务回滚；候选追加仍以原存储回执为准。数据库内部备份/绑定是原子提交，配置文件、数据库与内存会话之间采用使用前复核和代次协调，不宣称跨资源原子事务。

## 后台自动补正

默认邮箱提交完整成功轮次后，在锁外交接 `IAgentPersonalityLearning`，前台不等待模型。只有具有 `AgentUserText` 原文、真实 UUID、实际人格和完整空间引用的人类互动参与；失败、系统任务和缓存重放不触发。旧显式构造保留不自动学习模式；独立宿主可显式交接已接受互动，同时提供原会话有效性检查。

`PersonalityLearningService` 复用原准入、LLM 交互及引用复核；默认人格服务通过 `IPersonalityLearningTarget` 接受候选。自定义只读人格源未实现接收端口时不自动学习。生成检查真实 LEARNING 活动，执行者为无用户权限的系统身份；候选来源仍固定为原人类互动。

Spring application 支持以下本地配置：

| 前缀 `tea-neko.agent.personality-learning.` | 默认值 | 含义 |
| --- | ---: | --- |
| enabled | true | 全局自动生成开关，与 scope 的 personalityLearningEnabled 同时成立 |
| cooldown-seconds | 300 | 同会话尝试冷却，无候选及拒绝也进入冷却 |
| timeout-seconds | 45 | 生成、校验和提交观察的独立总时限 |
| max-model-calls | 1 | 实际模型请求上限，不表示自动重试 |
| max-total-tokens | 16384 | 独立交互总 token 预算 |
| max-input-bytes | 131072 | 完整生成输入上限，超限跳过，不截断原文 |
| retention-days | 30 | 自动候选有效期 |

默认沿用本次实际模型，输出最多 512 token，仍受原模型较小上限约束；无工具、不自动切模型。全局最多两个在途任务、同会话一个，无模型等待队列；容量不足等以后真实互动再尝试。冷却缓存最多一千项、空闲半小时惰性淘汰，不恢复重启前工作。token 预占及结算由原 LLM 服务执行，不借用或重置前台、摘要预算。

模型仅能返回 `{}` 或仅含 `field/text/confidence` 的一个对象；字段受代码和配置共同白名单限制，正文最多 512 字符。原请求、真实来源、基础 ID/版本、basis、锁定 false 和有效期由宿主固定；候选时间是后台处理时刻，不冒充平台事件时间。实际写入经过 `PersonalityService.propose`，沿用原绑定、置信度、到期、容量、锁定和幂等检查，下一轮才应用。

每次模型、提交前后复核当前学习开关、活动准入、原空间、人格和会话代次；切换后不把旧候选改投新 key。超时、实例关闭或服务关闭结束自有控制，不关闭借用线程池；邮箱换代/关闭后的迟到模型结果被拒绝，传输受独立时限约束。失败不影响当前答案，也不写用户画像或个人事实。

`reports()` 最近一百条记录及观察者提供原请求、独立执行 ID、实际模型状态、提交观察结果和逐次 REPORTED/ESTIMATED/未知 token，不含正文。APPLIED 表示已观察新增提交和最终校验；REJECTED/FAILED 不能证明已经开始的数据库事务回滚。模型表达仍是低信任数据，字段校验不保证自然语言质量，需真实模型人工验收。

## 显式旧人格迁移

`ILegacyPersonalityStore.preview/importSnapshot` 仅供可信离线迁移及后续 DEBUG 管理入口，普通解析、启动、自动学习和 `/cfg` 不自动导入，也未注册模型工具。聊天管理命令尚未实现；后续入口必须先校验真实 DEBUG 权限，存储端口不从模型或任意布尔值取得权限。

用 `Location(scopeId, agentId, conflicts)` 访问原 `llm_related` 中 `memory / scope:<scopeId>:agent:<agentId> / personality.delta` 或 `personality.conflict`。先预览源 Version、完整 JSON/摘要和逐条字段，再构造 `Import`，提供原目标绑定、当前可信基础人格、旧 source 字符串到真实 `AgentActorContext` 的明确映射及真实管理操作者。不能猜测旧平台主体 UUID；缺失 ID 固定为行内位置，缺失创建时间才用预览时固定的估算。

导入与补正追加、审计同事务完成，统一按绑定、补正、旧行顺序持锁。复核原文/版本、目标基础指纹、scope 和共同白名单；明确来源缺失、正文非法、锁定冲突、容量或提交失败使整批回滚。原 locked/createdAt/expiresAt 保留，tags/eventTime/updatedAt/冲突原因等元数据保存在完整审计原文中，不自行解释为新语义。原表始终不修改或删除。

冲突列表只作审计，不兼容字段、到期或低置信候选记录跳过原因，均不进入 Prompt。每个物理源行只迁移一次，来源定位为全局幂等键；相同完整映射返回原回执，不重复追加。源内容变化、换目标/key、改映射或换人格后不能借新请求标识重导，需要 DEBUG 人工核对和独立修正。审计不作为普通事实查询返回。

离线验证复用原事务和模型夹具，只补关键流程及高风险路径；实际 MySQL/JPA 事务代理、行锁、真实旧 JSON、供应商生成质量/成本仍需实机联调。
