# Agent 部署与最终验收

代码与离线契约已交付；以下实机步骤尚未在本次工作区执行，不能由编译或 FakeClient 推断已通过。运行基线为 JDK 25、Gradle Wrapper 9.8.2、Spring Boot 4.1.2。计划版本为 4.5.0-l；版本文件仍为 4.4.1，构建产物名以版本文件为准，本次不自动提交、打标签、推送或发布。

## 首次安装

1. 准备 MySQL 与 Java 25。按实际环境配置 Spring datasource、账号及连接地址；默认驱动为 MySQL Connector/J，不把 JDBC URL 类型与其他数据库混用。
2. 首次安装执行 `./gradlew copyTemplatesToConfig`，根据模板创建本地 config。已有部署先备份配置，核对复制任务的覆盖范围，不用模板覆盖真实凭据。
3. 根据[模型配置](../../src/main/java/org/zexnocs/teanekoagent/llm/file_config/README.md)在 config/agent/model.yml 统一填写 default-model-id、模型名、api-key、连接与 metadata，可使用 `${环境变量:默认值}`。Core YAML 展开占位符，main-config.yml 只保留 Agent 总开关；不再配置 application 的另一份供应商项，凭据不进入聊天或普通 metadata。
4. 核对活动 Profile。当前 application 默认通过 springProfile 取 dev；生产启动须显式选择 prod 或实际部署 Profile，配置数据库及网络边界。启动问题读取完整首条根因与最深 Caused by，并检查实际 Profile、JDBC URL 和驱动。
5. 执行 `./gradlew bootJar`，以 `java -jar build/libs/TeaNeko-App-<实际版本>.jar --spring.profiles.active=prod` 启动。普通 plain.jar 不是可执行部署包。Spring 及客户端启动日志不得泄漏凭据。
6. 在真实作用域执行 `/cfg reg agent`，再用 `/agent status` 检查登记；默认最低被动模式。先验证 `/agent chat 你好`，再按[平台说明](../../src/main/java/org/zexnocs/teanekoagent/adapter/platform/README.md)启用自动图文或 ACTIVE 候选。命令工具保持默认关闭，确需启用时由 OWNER 操作。

## 数据与旧配置

所有新版 Agent 数据含 /cfg 使用 tea_neko_agent_data，经专用 EasyData DTO、原事务/行锁/版本重试访问。实体与索引以[专用 EasyData](../../src/main/java/org/zexnocs/teanekoagent/database/easydata/README.md)为准；当前 dev/prod 采用 ddl-auto=update，关闭自动 DDL 的部署必须自行按实体建表并检查权限。

用户已明确 Agent 尚未上线，新版直接使用新表，不迁移、读取或删除旧 general_data/llm_related/历史表中的 Agent 数据；需要登记的 scope 在新版重新注册。不要为本次升级清理共用业务表。旧 Java 包及其专属测试已删除，内部 Client ID 保留，外部 Java 调用改用[新版三类 Sender/直接服务](../../src/main/java/org/zexnocs/teanekoagent/client/README.md)。

主配置只使用 enabled；default-model-id 已统一移入 model.yml。旧 thinking-enabled/max-thinking-steps/max-thought-summary-length/include-thoughts-in-output 不再代表实际执行能力；轮次、工具预算和时限仍是独立运行策略，使用 tea-neko.llm.interaction，见[交互配置](../../src/main/java/org/zexnocs/teanekoagent/llm/framework/interaction/README.md)。旧 application 供应商 api-key/base-url/api-path 应移到同一 model.yml 条目；敏感加载错误脱敏，读取不写回，显式保存会写入当前已解析值。旧 token-monitor.yml 模板已退役，监控使用 tea-neko.agent.monitor，见[监控说明](../../src/main/java/org/zexnocs/teanekoagent/agent/monitor/README.md)。

人格、记忆、历史、摘要、Skill、任务各自的当前配置只在[模块索引](../../src/main/java/org/zexnocs/teanekoagent/README.md)指向的文档维护。密钥错误或路由缺失应只让 Agent 不可执行，不阻止其他机器人功能；固定配置后按现有文件重载流程恢复。

## 离线复核

PowerShell 示例：

```powershell
$env:JAVA_HOME = '<本地 Java 25 目录>'
$env:TEANEKO_LIVE_LLM_TESTS = 'false'
.\gradlew.bat --no-daemon --rerun-tasks test --tests 'org.zexnocs.teanekoagent.*' --tests 'org.zexnocs.teanekocore.command.*' --tests 'org.zexnocs.teanekoapp.sender.MessageSenderAsyncTest' jar bootJar
git diff --check
```

离线机器有完整依赖缓存时可追加 --offline。默认不要执行依赖真实数据库的全应用 SpringBootTest，也不要设置 TEANEKO_LIVE_LLM_TESTS=true 作为普通回归。最终核对实际 JAR 中新版 Client/服务、资源和类字节，确认没有 teanekoagent_old 或退役存储类；文档与中文 JavaDoc 一并检查。最近精确回归清单和日志位置见[计划](../plan/4.5.0-agent.md)。

## 实机验收清单

| 项目 | 必须观察的真实结果 |
| --- | --- |
| 启动与 MySQL | 活动 Profile、驱动和 schema 正确；新表/唯一键/索引存在；事务代理、并发冲突重试和行锁符合语义；应用可正常关闭 |
| 原生权限与身份 | OneBot 自身 ID 在连接后可用；未登记/禁用时无采集、模型、工具或自主行为；真实 MEMBER/OWNER/DEBUG 边界有效 |
| 图文与回复形式 | 文本、纯图、混排、data 图片及 reply/at/plain 实际显示和顺序正确；模型不支持图片时明确拒绝；平台关闭/静默状态不被绕过 |
| 副作用与回执 | 真实命令异步反馈、内部 Sender 和平台回执均可结束；断网、超时、重复来源不盲目重发；结果不明不假称回滚 |
| 记忆与人格 | namespace reset/clear、共享绑定、人格备份/切换和迟到后台任务按代次拒绝；新空间不沿用旧画像 |
| 定时、重启与取消 | 持久化提醒恢复；自主定时重启后等未来间隔、不补跑；静默、冷却、退避和当前配置撤销生效 |
| 监控 | 正文/凭据不进入告警；监听失败不阻断答案；报告/估算/未知用量分开；真实费用与供应商账单另核对 |
| 性能 | 分别记录普通文本、图片、工具往返、同群排队、跨群并发、摘要/学习和长时间运行的耗时、内存及吞吐 |

普通对话三十秒是目标，尚无实机数据证明达到。使用固定模型/输入规模记录样本数及 P50/P95，区分模型耗时、排队和平台发送；不要把单次 FakeClient 响应当作生产性能证明。

## 停用与回退

暂停单个 scope 使用 /cfg set agent enabled false，停止自主候选可另关相应开关；停止应用后旧回调不得重新提交新代次。关闭不代表撤销已发生的外部发送或数据库事务。回退前备份实际 JAR、版本文件、当前 config 和数据库；旧版不理解新表、当前 key/代次及回执，不把换回旧 JAR 当作数据迁移或继续重放。代码历史可由 v4.4.0-main 等原始快照追溯，数据库回退必须按实际部署独立制定。
