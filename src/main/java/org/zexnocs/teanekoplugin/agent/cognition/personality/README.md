# 基础人格与独立补正

默认完整装配通过 `PersonalityConfig` 向独立轮次和会话邮箱提供同一个 `IAgentPersonalitySource`。人格已实际进入模型输入，补正、备份和绑定保存到 `tea_neko_agent_data` 的独立 `agent.personality.v1` 业务域；不与用户画像或 memory namespace 混存，不新增线程池。

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

correctionKey 不属于普通 cfg 字段。独立 DEBUG 指令已接入 key 绑定、分页/导出、备份、克隆、新增、编辑、锁定与撤销。真实角色和启用状态在入口及事务提交前复核，普通用户或模型参数不能自行获得权限；命令正文不作为人格运行规则。

## DEBUG 补正管理

下列指令仅供已登记且启用作用域的真实 DEBUG 使用。先执行 `/agent personality` 获取当前 key、bindingRevision、stateRevision 和允许字段；每次成功修改后重新查询版本。`key` 省略时取当前引用，指定其他 key 仅供可信 DEBUG 查询、克隆或备份；编辑只允许当前绑定的可写 key。字段 `speaking detail` 在命令中写成 `speaking_detail`。

| 指令，均加 `/agent` 前缀 | 行为 |
| --- | --- |
| `personality [key] [offset]` | 显示版本、来源、锁定及正文，每页五项。首次查询原子初始化当前引用，基础人格变化时先备份再切换。 |
| `exportpersonality [key] [offset]` | 每页两条完整来源 JSON，包含集合/管理版本及后继偏移；单页至多 128 KiB，长内容用原转发发送器分段。 |
| `personalitybackups [key] [offset]` | 查询源 key 最近六十四份备份的分页引用，每页五项。较早备份仍可用完整 key 查询。 |
| `setcorrectionkey <目标key> <bindingRevision> <目标stateRevision>` | 先备份当前补正，再仅切换当前 scope；新建空 key 的目标版本填 -1，已有 key 填其查询版本。 |
| `clonecorrections <源key> <bindingRevision> <源stateRevision>` | 克隆为新的独立可写 key，保留来源、时间和锁定；不自动切换绑定。 |
| `backuppersonality <源key> <bindingRevision> <源stateRevision>` | 显式保存不可变备份，不改变源内容。 |
| `addcorrection <当前key> <bindingRevision> <stateRevision> <字段> <正文>` | 按共同白名单新增实际操作者的补正，不能改身份、硬边界、工具权限或画像。 |
| `editcorrection <当前key> <bindingRevision> <stateRevision> <补正UUID> <正文>` | 修改正文，保留原记录标识、来源、时间、有效期与锁定。 |
| `lockcorrection <当前key> <bindingRevision> <stateRevision> <补正UUID> <true或false>` | 明确锁定/解锁，不接受任意字符串冒充布尔值。 |
| `revokecorrection <当前key> <bindingRevision> <stateRevision> <补正UUID>` | 保存修改前备份后撤销，迟到学习不能按旧管理代次重新写回。 |

所有修改保存原操作回执；同一原消息的同一动作只有一笔管理操作，重放返回原结果，改变来源、参数或预期版本报告冲突；新操作须发送新消息。回执不是当前最新状态。备份、修改与回执同事务提交，授权失效或中途写入失败整体回滚；取消观察不证明已开始事务回滚。备份不可直接绑定或编辑，需要先克隆，再用新消息绑定克隆 key。

管理编辑先保存不可变备份，保留原补正证据；普通候选不能授予 locked 或覆盖锁定字段。已有补正与当前基础版本/白名单不兼容时拒绝编辑，但允许撤销或克隆保存。共享 key 的管理变更协调全部已知 scope 的短期窗口、摘要、完成重放及在途请求；绑定只协调当前 scope。同步失败单独报告，使用前身份复核仍拒绝旧结果；重复同步不再次清除已经采用新人格的窗口。长期事实与 memoryNamespace 不受影响。

分页查询是各次请求的版本快照，并非冻结导出；跨页应比较 key、revision 和 managementRevision，发现变化后从头查询。

## 轮次、历史与重放

邮箱在历史装配前完成准入及人格解析，固定实际人格身份并预留完整协议字节。独立轮次再次解析实际来源，核对邮箱身份提示，不接受请求携带人格正文。基础人格以 SYSTEM 人格层加入原前导运行规则之后，来源补正仅以 USER 数据加入，不能升为规则或权限。

实际输入同时计入人格、补正、参考资料、原问题和历史的完整协议预算；按完整旧轮次缩小窗口，不能静默删掉基础人格。历史提交仍验证实际前缀、原输入投影、工具闭环和最终候选；窗口不重复保存人格或补正。基础内容指纹覆盖实际内容，修改正文而未递增配置版本也会触发身份更新。

人格绑定或应用开关改变后，当前 scope 的短期窗口/完成缓存换代，旧等待项失效。每次模型、工具、最终交付及缓存重放只读复核当前配置和原绑定，旧运行不能公开新身份下的回答。普通学习追加只改变内容 revision，从下一轮生效。DEBUG 新增/编辑/锁定/撤销同时推进 managementRevision，使共享该 key 的旧快照失效；候选在状态锁内复核原管理代次，迟到学习不能回灌。已采用补正到期后不能继续使用或重放。

## 持久化生命周期

首次按 `scope-<client和scope的摘要>` 初始化空补正，避免不同客户端的同名 scope 意外共享。基础人格变化时，在一个 DatabaseTask 事务中锁定当前绑定及旧补正，保存 `backup-<源key摘要>-<时间>-<操作ID>` 的不可变副本，创建当前 scope 的新空 key，再更新绑定 revision。其他引用原 key 的 scope 保留原补正；长期记忆、namespace 和其他 scope 的会话不被清除。

补正以有容量上限的版本集合保存，每空间最多 64 条；追加、切换及共享写入使用同一行锁顺序和既有 Version 仲裁，提交成功才完成 Stage。达到上限明确拒绝，不悄悄覆盖来源。备份不允许作为可写空间绑定。旧绑定的迟到候选即使来源相同也不能改投新 key。

宿主可将真实互动产生的候选交给 `PersonalityService.propose(scope, basis, candidate, control)`；basis 必须是产生候选时的实际快照，candidate 保留原请求 ID、真实用户/来源和时间。请求 ID 提供幂等，相同 ID 改内容拒绝。只接受基础版本兼容、置信度至少 0.2 且位于代码和文件共同白名单内的表达细节/关系表达；身份、硬边界、工具、密钥和用户画像字段不在白名单。普通候选不能授予 locked，已有锁定字段不能被后续候选覆盖。

取消、配置变化或交付前拒绝不意味着已经开始的事务回滚；候选追加仍以原存储回执为准。数据库内部备份/绑定是原子提交，配置文件、数据库与内存会话之间采用使用前复核和代次协调，不宣称跨资源原子事务。

## 持久观察式人格

默认从真实人类表达中观察风格，不要求用户逐条发出人格修改指令。已安装 memory 且历史采集、自动摘要与观察通路均可用时，观察与长期事实整理共用原 LEARNING 活动、一次无工具模型调用和同一个后台处理器；事实与风格分别返回候选，memoryLearningEnabled 和 personalityLearningEnabled 独立生效。关闭事实学习仍可观察表达，关闭人格学习也不停止已授权事实学习和短期采集。

v4.8.3-l 在未安装 memory、关闭历史采集或关闭自动摘要时，改走独立逐轮学习：使用当前真实用户措辞观察人格，不要求记忆引用，也不创建记忆空间。通路按当前 scope 判断，两种采样互斥，仍遵守人格学习开关、冷却、预算与提交前资格检查。要求机器人改变语气的指令不能冒充用户表达习惯。

StyleObservationService 固定原 scope、基础人格和管理身份，并从原短期行筛选仍属于该人格的有效 USER 样本。模型只能选择 speakingStyle 或 speaking detail 中当前配置允许的字段；摘要、助手回答、工具结果不能作为新的风格证据。单个用户要求“对我简短些”应进入用户偏好，临时指令只影响当前轮次，不能变成全群人格。自然语言识别与模仿质量仍需真实模型验收。

每个模式保留场景 GENERAL/TECHNICAL/CASUAL、最多十二条真实来源和样本原话。相同 client/scope、作者与平台消息只算一条独立证据，重复整理不提高支持次数。至少三条不同消息、来自至少两个中期片段才可采用；单批最多采用一个模式，采用间隔沿用 cooldown-seconds，默认五分钟。作用于整个 scope 的模式始终带场景说明，不把用户的身份、经历、个人偏好或关系复制给机器人。

4.8.6-l 在有界候选窗口内先合并同字段、同场景的近义摘要，再生成 `styleTarget.known`。新候选优先复用已有模式并合并独立证据，保留原描述和采用时间；旧重复记录在对应提交中合并。比较使用常见同义词、文字特征和否定/频率保护，不新增模型调用；语义不确定时保留独立候选。跨人格、跨 scope、不同场景和相反结论不会因此合并。

观察、原人格补正与长期整理水位在同一个专用 EasyData 事务提交；任意写入失败整体回滚。clear、原租约、基础人格切换、管理换代或学习资格撤销后不能提交迟到观察。字段锁定或补正容量已满时保留合格观察但不自动覆盖原补正。补正从后续轮次生效，不改写已经生成的答案。

原基础人格和显式管理入口继续有效。观察目标按真实 scope 与人格身份隔离，不因长期 namespace 共享而把其他群的交流方式直接套入当前群；原管理操作改变身份后，旧观察不会自动回灌。人格引用被管理员显式共享时，已接受补正仍遵循原共享语义。用户事实独立保存，更换基础角色不删除事实。

本地默认：

```properties
tea-neko.agent.personality-learning.enabled=true
tea-neko.agent.personality-learning.observation-enabled=true
tea-neko.agent.personality-learning.cooldown-seconds=300
tea-neko.agent.personality-learning.retention-days=30
```

保留期控制观察可用样本及新补正的有效期，补正实际到期时刻在采用时固定；修改配置不自动延长已经采用的补正。到期模式只有取得采用之后的新独立样本才可再次采用，不用旧证据自动续期。

默认后台维护按域分页精简过期原话；支持样本已清空且补正已到期的模式物理回收，将采用时刻与到期时刻移入无正文水位，防止空记录长期占据查询窗口。仍在有效期的已采用模式即使原话已到期，也可显示采用状态及当前零样本覆盖。普通可写补正集合去掉到期项并推进内容 revision，释放六十四条容量；managementRevision 保持不变，不重置管理保护，不清除有效/无期限补正，不修改不可变备份。维护失败时游标和修改一起回滚。

新增注解工具 personality_observations，默认所有 client 可用，要求真实登记并启用 Agent、historyReadEnabled 和 memoryReadEnabled。它只查询当前 scope 当前人格的最多十六项摘要，包含表达模式、场景、独立消息/片段数、作者 UUID 及是否采用，不返回原话或其他区域数据；关闭学习不自动关闭查询。字段锁定、撤销和基础角色管理继续使用原 /agent personality 入口。

IStyleObservationStore 可替换观察持久化，联合生成由 ILongTermMemoryConsolidator.analyze 扩展；原 consolidate 函数式实现继续只返回事实。自定义联合存储必须支持与原水位同事务提交，缺少支持时明确失败，不静默丢弃风格候选。默认事务适配器以 EasyDataPersonalityStore 为基础；替换整个人格存储时需同时提供兼容观察端口及服务，或保留原独立学习入口。

## 兼容后台自动补正

下述逐轮入口在缺少默认观察装配、关闭 observation-enabled 或关闭分层功能时继续可用；默认观察已装配并开启时不再对同一聊天重复发起这一人格模型分析。

默认邮箱提交完整成功轮次后，在锁外交接 `IAgentPersonalityLearning`，前台不等待模型。只有具有 `AgentUserText` 原文、真实 UUID、实际人格和完整空间引用的人类互动参与；失败、系统任务和缓存重放不触发。旧显式构造保留不自动学习模式；独立宿主可显式交接已接受互动，同时提供原会话有效性检查。

`PersonalityLearningService` 复用原准入、LLM 交互及引用复核；默认人格服务通过 `IPersonalityLearningTarget` 接受候选。自定义只读人格源未实现接收端口时不自动学习。生成检查真实 LEARNING 活动，执行者为无用户权限的系统身份；候选来源仍固定为原人类互动。

Spring application 支持以下本地配置：

| 前缀 `tea-neko.agent.personality-learning.` | 默认值 | 含义 |
| --- | ---: | --- |
| enabled | true | 全局自动生成开关，与 scope 的 personalityLearningEnabled 同时成立 |
| cooldown-seconds | 300 | 同会话尝试冷却，无候选及拒绝也进入冷却 |
| timeout-seconds | 45 | 生成、校验和提交观察的独立总时限 |
| max-model-calls | 1 | 实际模型请求上限，不表示自动重试 |
| max-total-tokens | 未配置 | 可选的独立交互总 token 预算；不足时拒绝 |
| max-input-bytes | 131072 | 完整生成输入上限，超限跳过，不截断原文 |
| retention-days | 30 | 自动候选有效期 |

默认沿用本次实际模型，完整继承其 `max-tokens`；无工具、不自动切模型，输入还受模型上下文容量与默认 0.8 的输入占比约束。全局最多两个在途任务、同会话一个，无模型等待队列；容量不足等以后真实互动再尝试。冷却缓存最多一千项、空闲半小时惰性淘汰，不恢复重启前工作。显式总 token 配额的预占及结算由原 LLM 服务执行，不借用或重置前台、摘要预算，不缩减输出额度。

模型通过[类型化 JSON 服务](../../../../teanekoagent/llm/framework/structured/README.md)返回 `{"adjustment":null}` 表示不学习，或 `{"adjustment":{"field":"…","text":"…","confidence":0.9}}` 提出候选；字段受代码和配置共同白名单限制，正文最多 512 字符。原请求、真实来源、基础 ID/版本、basis、锁定 false 和有效期由宿主固定；候选时间是后台处理时刻，不冒充平台事件时间。实际写入经过 `PersonalityService.propose`，沿用原绑定、置信度、到期、容量、锁定和幂等检查，下一轮才应用。

每次模型、提交前后复核当前学习开关、活动准入、原空间、人格和会话代次；切换后不把旧候选改投新 key。超时、实例关闭或服务关闭结束自有控制，不关闭借用线程池；邮箱换代/关闭后的迟到模型结果被拒绝，传输受独立时限约束。失败不影响当前答案，也不写用户画像或个人事实。

`reports()` 最近一百条记录及观察者提供原请求、独立执行 ID、实际模型状态、提交观察结果和逐次 REPORTED/ESTIMATED/未知 token，不含正文。APPLIED 表示已观察新增提交和最终校验；REJECTED/FAILED 不能证明已经开始的数据库事务回滚。模型表达仍是低信任数据，字段校验不保证自然语言质量，需真实模型人工验收。

## 数据入口与验证

人格存储通过 [Agent EasyData](../../../../teanekoagent/database/easydata/README.md) 的事务 DTO 访问，不直接操作仓库。Agent 未上线，直接使用新表，不提供旧人格迁移端口。普通补正继续保留原始来源和字段兼容校验。真实 MySQL 事务代理、行锁及模型质量仍需实机验收。

自定义可写人格存储须实现带 managementRevision 和准入 guard 的 append 重载；默认实现明确拒绝缺少换代保护的学习写入，不能降级到无校验追加。自定义只读人格源可继续只实现解析与复核端口。
