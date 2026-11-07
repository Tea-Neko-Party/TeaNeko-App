# Agent 部署与最终验收

代码与离线契约已交付；以下实机步骤尚未在交付记录中完成验收，不能由编译或 FakeClient 推断已通过。运行基线为 JDK 25、Gradle Wrapper 9.8.2、Spring Boot 4.1.2；构建版本及产物名以根目录 version.properties 为准。功能与升级变化见 [版本日志](../changelog/README.md)，Git 提交、标签和远程发布按版本流程显式执行。

## 首次安装

1. 准备 MySQL 与 Java 25。按实际环境配置 Spring datasource、账号及连接地址；默认驱动为 MySQL Connector/J，不把 JDBC URL 类型与其他数据库混用。
2. 首次安装执行 `./gradlew copyTemplatesToConfig`，根据模板创建本地 config。已有部署先备份配置，核对复制任务的覆盖范围，不用模板覆盖真实凭据。
3. 根据[模型配置](../../src/main/java/org/zexnocs/teanekoagent/component/llm/file_config/README.md)在 config/agent/model.yml 统一填写 default-model-id、模型名、max-tokens、真实 context-window-tokens、api-key、连接与 metadata；context-input-ratio 默认 0.8，可使用 `${环境变量:默认值}`。已有模型条目须补充容量声明，缺失时选定模型拒绝就绪。Core YAML 展开占位符，main-config.yml 只保留 Agent 总开关；不再配置 application 的另一份供应商项，凭据不进入聊天或普通 metadata。
4. 核对活动 Profile。当前 application 与 `./gradlew bootRun` 默认使用 prod；本机开发显式选择 `dev,web-dev`。按实际部署配置数据库及网络边界，启动问题读取完整首条根因与最深 Caused by，并检查实际 Profile、JDBC URL 和驱动。
5. 执行 `./gradlew bootJar`，以 `java -jar build/libs/TeaNeko-App-<实际版本>.jar --spring.profiles.active=prod` 启动。普通 plain.jar 不是可执行部署包。Spring 及客户端启动日志不得泄漏凭据。
6. 在真实作用域执行 `/cfg reg agent`，再用 `/agent status` 检查登记；默认最低被动模式。先验证 `/agent chat 你好`，再按[平台说明](../../src/main/java/org/zexnocs/teanekoagent/integration/message/README.md)启用自动图文或 ACTIVE 候选。命令工具保持默认关闭，确需启用时由 OWNER 操作。

## 原生访问与轮次失败诊断

JDK 25 默认允许未授权的原生访问并发出警告。Netty 的 `System.loadLibrary` 警告本身不表示 Agent 请求失败，须结合实际异常判断。项目的 `bootRun` 已添加 `--enable-native-access=ALL-UNNAMED`，新构建的可执行 JAR 通过 Manifest 声明同一权限；直接在 IntelliJ 运行主类时，在运行配置的 **VM options** 中添加该参数，不放入程序参数或 Gradle 守护进程参数。详见 [JDK 原生访问说明](https://docs.oracle.com/en/java/javase/25/core/restricted-methods.html)。

Agent 轮次失败报告包含请求标识、活动、模型尝试次数、异常类型链、代码位置和可取得的 HTTP 状态码；先按请求标识定位，再区分模型 HTTP 错误、工具错误与本地管线异常。普通错误不直接输出原异常消息或请求头。请求汇总另按[观测配置](web-console-agent.md#info-观测)选择级别和正文，一条链只汇总一次，凭据始终脱敏。没有 HTTP 状态码也可能是请求发出前失败、连接失败或解析失败，不能据此假定供应商已拒绝请求。

DeepSeek 的 `reasoning_effort` 位于请求顶层，不能放进 `thinking`；只配置强度时不发送缺少 `type` 的 `thinking` 对象。升级后重新构建并重启，再验证 `/agent chat 你好` 和 `@机器人 你好`。代码与构建验证不能代替真实请求成功的确认。

## 数据与旧配置

所有新版 Agent 数据含 /cfg 使用 tea_neko_agent_data，经专用 EasyData DTO、原事务/行锁/版本重试访问。实体与索引以[专用 EasyData](../../src/main/java/org/zexnocs/teanekoagent/database/easydata/README.md)为准；v4.8.7-l 起 prod 只做结构校验，部署前执行[版本化迁移](database-upgrades.md)。任务容量、超时和关闭合同见[运行边界](runtime-limits.md)。

用户已明确 Agent 尚未上线，新版直接使用新表，不迁移、读取或删除旧 general_data/llm_related/历史表中的 Agent 数据；需要登记的 scope 在新版重新注册。不要为本次升级清理共用业务表。旧 Java 包及其专属测试已删除，内部 Client ID 保留，外部 Java 调用改用[新版三类 Sender/直接服务](../../src/main/java/org/zexnocs/teanekoclient/agent/README.md)。

主配置只使用 enabled；default-model-id 已统一移入 model.yml。旧 thinking-enabled/max-thinking-steps/max-thought-summary-length/include-thoughts-in-output 不再代表实际执行能力；轮次、工具预算和时限仍是独立运行策略，使用 tea-neko.llm.interaction，见[交互配置](../../src/main/java/org/zexnocs/teanekoagent/component/llm/framework/interaction/README.md)。旧 application 供应商 api-key/base-url/api-path 应移到同一 model.yml 条目；敏感加载错误脱敏，读取不写回，显式保存会写入当前已解析值。旧 token-monitor.yml 模板已退役，监控使用 tea-neko.agent.monitor，见[监控说明](../../src/main/java/org/zexnocs/teanekoagent/runtime/monitor/README.md)。

人格、记忆、历史、摘要、Skill、任务各自的当前配置只在[模块索引](../../src/main/java/org/zexnocs/teanekoagent/README.md)指向的文档维护。密钥错误或路由缺失应只让 Agent 不可执行，不阻止其他机器人功能；固定配置后按现有文件重载流程恢复。

## 离线复核

PowerShell 示例：

```powershell
$env:JAVA_HOME = '<本地 Java 25 目录>'
$env:TEANEKO_LIVE_LLM_TESTS = 'false'
.\gradlew.bat --no-daemon --rerun-tasks test --tests 'org.zexnocs.teanekoagent.*' --tests 'org.zexnocs.teanekocore.command.*' --tests 'org.zexnocs.teanekocore.plugin.*' --tests 'org.zexnocs.teanekoapp.sender.MessageSenderAsyncTest' jar bootJar
git diff --check
```

离线机器有完整依赖缓存时可追加 --offline。默认不要执行依赖真实数据库的全应用 SpringBootTest，也不要设置 TEANEKO_LIVE_LLM_TESTS=true 作为普通回归。最终核对实际 JAR 中新版 Client/服务、资源和类字节，确认没有 teanekoagent_old 或退役存储类；文档与中文 JavaDoc 一并检查。各版本精确回归清单和日志位置通过 [计划索引](../plan/README.md) 查阅归档记录。

## 实机验收清单

| 项目 | 必须观察的真实结果 |
| --- | --- |
| 启动与 MySQL | 活动 Profile、驱动和 schema 正确；新表/唯一键/索引存在；事务代理、并发冲突重试和行锁符合语义；应用可正常关闭 |
| 原生权限与身份 | OneBot 自身 ID 在连接后可用；未登记/禁用时无采集、模型、工具或自主行为；真实 MEMBER/OWNER/DEBUG 边界有效 |
| 自身提及 | `@机器人 你好` 的模型上下文包含实际 selfPlatformId 和 mentionedSelf=true；提及其他用户或全体成员不误判，原文授权保持独立 |
| 图文与回复形式 | 文本、纯图、混排、data 图片及 reply/at/plain 实际显示和顺序正确；模型不支持图片时明确拒绝；平台关闭/静默状态不被绕过 |
| 副作用与回执 | 真实命令异步反馈、内部 Sender 和平台回执均可结束；断网、超时、重复来源不盲目重发；结果不明不假称回滚 |
| 分段确认与重启 | OneBot 返回实际消息 ID 才确认；第二段失败时近期记忆只呈现已确认第一段，重启不补发未知占用；数据库确认失败不推断平台未收到 |
| 拟人表达 | expression.yml 与 scope 开关生效；一次最终答案按原顺序分条，间隔体感合适；stop/关闭终止未发尾部；同目标多请求不交错，其他群继续运行，机器人账号变化不改投；控制符不进入实际聊天和普通历史 |
| 表情包素材 | 补齐 stickers.yml；已登记区域默认收集真实图片，独立开关及实例/scope 下载和分析额度生效；真实视觉样本区分画作与表情包，GIF 采样和原动画保留正确；重启/停用回收暂存，缺失文件撤销有效状态。多副本同时共享数据库、素材卷及身份文件；实测磁盘、网络和长期运行负载 |
| 表情选图与发送 | 补齐 media-replies.yml；核对实际 OneBot face ID/描述。模型先选类别再查询完整列表，最终 STICKER 独立发送且只可附 reply，原生 FACE 不入图库；实测动画/base64 上限、关闭区域/换号/目录换代/文件缺失/断网及取消中的占用，未知回执不补发，跨群共享素材且回避记录独立 |
| 图库管理及事件 | 接入真实已认证 ManagementAuthority 后核验全局 inspect/preview/manage 映射及撤权；实测预览大小、并发修改、删除/恢复和后台刷新。event-plugins 默认空，启用指定插件后检查同步拒绝、低信任提示与提交后只读观察；通知不是可靠审计，不直接映射 Web 推送；本期没有新增 HTTP 或前端 |
| 记忆与人格 | namespace reset/clear、共享绑定、人格备份/切换和迟到后台任务按代次拒绝；新空间不沿用旧画像 |
| 自动三层与明确保存 | 跨 scope 共用 namespace 的长期画像可读，近期原文仍按 scope 隔离；短中期压缩、观察式人格和维护索引/回收速率符合配置；关闭自动学习或历史采集后，明确“请记住我喜欢花茶”仍按新增权限提交且无工具依赖 |
| 定时、重启与取消 | 持久化提醒恢复；自主定时重启后等未来间隔、不补跑；静默、冷却、退避和当前配置撤销生效 |
| 监控 | 正文/凭据不进入告警；监听失败不阻断答案；报告/估算/未知用量分开；真实费用与供应商账单另核对 |
| 性能 | 分别记录普通文本、图片、工具往返、同群排队、跨群并发、摘要/学习和长时间运行的耗时、内存及吞吐 |
| v4.8.3-l 组件组合 | 真实模型验证仅 llm+personality、关闭历史采集/自动摘要后的独立学习，以及仅 llm+expression 的原生表情；未安装 memory 的主动活动仍持久防重，不创建记忆空间 |
| v4.8.3-l 主动索取表情包 | 确认有启用且未处于冷却的素材后，要求发送指定情绪图片；观察真实 sticker_list 回执与独立图片消息。DSML 不作为正文发送；原预算内纠正失败时有明确原因，模型自述不代替工具日志。空图库、工具被过滤和配置失效须可区分 |
| v4.8.3-l 长期恢复 | 实际错误应有证据原因、候选域与脱敏定位；同输入三次无效后暂停，重启保留；工作区版本化恢复、撤权和旧租约拒绝有效，不跳过证据或成功水位 |
| v4.8.3-l 读取失败退避 | 升级后旧作业可读取；仍有格式错误时应显示记录类型与受控字段，领取前故障从 30 秒起退避，冷却内不重复读取或告警，其他分区可继续。修复原格式后恢复；进程内退避重启重置，不宣称三次模型无效暂停 |
| v4.8.3-l 结构化输出 | 用实际模型核对表情包分类、中期压缩、长期事实/风格、独立人格和主动发言；原生 JSON/Schema 与适配器声明一致，不支持时有警告。格式失败可定位字段或行列，普通日志不含原文；需要原文时临时启用[受限诊断](../../src/main/java/org/zexnocs/teanekoagent/component/llm/framework/structured/README.md#诊断)，排查后关闭。图片超限应报告具体约束，不泄漏地址 |
| v4.8.3-l 图库迁移 | 备份并停旧实例后验证首次迁移耗时、独立删除索引/计数、重复采集拒绝、分页及显式恢复；迁移失败整体回滚，不混跑旧二进制；回退使用配套数据备份 |
| v4.8.3-l 容量 | 大量终态任务下核对到期索引查询、启动补建耗时和四槽饱和；图库根锁下核对选图/采集/发送竞争、独立删除行增长、主目录容量及吞吐，离线夹具不代替 MySQL 测量 |
| v4.8.6-l 观测 | 应用观测配置后核对 INFO、DEBUG、NONE 及独立内容开关；一次 Agent 链只汇总一次，默认仅摘要、本轮输入与最终回复；Schema/工具跨轮不重印，历史助手和凭据不出现 |
| v4.8.6-l 结构与学习 | 真实供应商接受原生约束，JSON 对象模式仍取得字段说明；批次未满时等待、到期或重要事件触发，纯问候及已处理证据不发起模型提取；重启不重复推进，近义风格累加新证据，真实 MySQL 写入失败整体回滚 |
| v4.8.6-l 共享回忆 | 显式装配后，同一真实用户从群聊转私聊能识别来源群与当前私聊；其他用户、私聊原文、旧代次及到期记录不混入；旧索引在维护后可查询 |
| v4.8.6-l 受邀私聊 | 当前群消息明确邀请后，仅向同客户端、已启用 Agent 且同空间的本人私聊；目标未登记、映射改变、空间切换或未知回执时不误发、不重发 |
| v4.8.6-l 情感与关怀 | 实际模型正确引用本轮原文调用情感工具；重复来信不加分；ACTIVE、静默、冷却、次数上限及新来信取消有效；人格表达自然，状态重启保留、清空空间后隔离并维护回收 |

普通对话三十秒是目标，尚无实机数据证明达到。使用固定模型/输入规模记录样本数及 P50/P95，区分模型耗时、排队和平台发送；不要把单次 FakeClient 响应当作生产性能证明。

## 停用与回退

暂停单个 scope 使用 /cfg set agent enabled false，停止自主候选可另关相应开关；停止应用后旧回调不得重新提交新代次。关闭不代表撤销已发生的外部发送或数据库事务。回退前备份实际 JAR、版本文件、当前 config 和数据库；旧版不理解新表、当前 key/代次及回执，不把换回旧 JAR 当作数据迁移或继续重放。代码历史可由 v4.4.0-main 等原始快照追溯，数据库回退必须按实际部署独立制定。
