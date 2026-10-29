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

personalityId 为空时使用文件默认。自定义描述替换人格描述层，保留基础身份约束和硬边界，不能修改运行权限。开启自定义前先设置有效正文；自定义正文和风格各最多 4096 字符。应用补正和接受候选是独立开关，均默认 true；关闭应用不删记录。当前尚不自动调用模型生成候选，学习开关不会触发额外模型请求，也不能授权写用户事实。

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

旧 `llm_related` 的人格 delta/conflict 原文保留，本功能不猜测旧主体或自动导入；后续迁移/DEBUG 管理与自动补正生成继续作为独立功能实现。离线验证复用原事务夹具覆盖实际模型输入、历史/重放、共享引用切换、回滚和旧轮失效；实际 MySQL/JPA 的事务代理及行锁仍需实机联调。
