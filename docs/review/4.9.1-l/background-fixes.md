# v4.9.1-l 后台采集与记忆整理验收

2026/11/10 完成实现及隔离验收，未发布、部署或重启真实应用。后续实机操作见[环境验收入口](next-steps.md)，本记录不替代真实模型、数据库与客户端验收。

## 已核实的问题与修复

- 图库原来源判断把聊天采集关闭和客户端账号未就绪都合并为 false，`StickerLibrary.check` 再报告为普通运行状态异常。现有日志不能区分本次命中了哪个分支。现在使用明确资格结果：未开始的旧作业在账号未就绪时保留队列与预约，30 秒后复核，原 24 小时过期仍生效；来源关闭终止并清理；已开始任务的资格失效保留具体失败原因，不重新发送视觉请求。新候选资格不足直接拒绝，配置或数据库读取错误继续传播。
- 请求 `9074e62d-6e13-4bb6-be92-90079d908199` 在约 44.8 秒时超过原 45 秒配置。分层记忆默认改为 90 秒，显式配置和 120 秒上限不变；扫描与整理仍独立计时，超时不推进水位，已开始的持久操作仍等待实际结果。
- 请求 `dda984a8-37f1-4f7f-836d-85e6240959c4` 返回成功，但诊断输出为 `{"facts":[],"styles":[]}`，与 `memories/styles` 合同不符。请求声明中存在正确 Schema，原生模式的文字指令却省略字段名。现在由缓存 Schema 生成简短的顶层字段名提示，仍不重复全文 Schema，不自动替换别名、补字段或重试。缺少网络级证据，不能确认兼容网关忽略约束的具体原因，也不保证提示能让所有远端返回正确结构。
- Gradle 与 JAR 已声明原生访问，本机 IDEA 主运行配置只设置了开发 Profile。已在被 Git 忽略的 `.idea/workspace.xml` 中保留原参数并补上 `--enable-native-access=ALL-UNNAMED`，下次启动生效。该警告独立于上述业务失败。

## 验证结果

| 范围 | 实际结果 |
| --- | --- |
| Java 定向回归 | 13 个测试类、89 项通过，0 失败、0 错误、0 跳过 |
| 重点行为 | 未就绪队列恢复后仅一次分析、预约释放、来源撤销和配置变化、迟到结果拒绝、原生 Schema 短字段提示、错误别名仍拒绝、50 秒生成允许及超出新时限禁止提交 |
| 相关回归 | 图库采集、下载、删除、管理和便携导入，JSON 协议，中长期记忆、模型预算及项目分层 |
| 源码检查 | 18 个当前修改 Java 文件的变更规范检查及 JavaDoc 通过；最终 `git diff --check` 通过 |
| 实际产物 | 离线 `bootJar` 通过，清单与运行版本均为 4.9.1-l；2169 个 class 字节与 49 个前端资源匹配，无缺失、额外项或已删除类型残留 |
| 默认配置与启动参数 | JAR 的应用配置、补充及生成元数据均为 90 秒；JAR 清单和本机 IDEA 配置均包含原生访问声明 |

本轮未修改前端，复用[控制台验收](README.md)对应的已构建资源，未重复运行浏览器或前端检查。测试使用隔离夹具，未发起真实模型、数据库或平台请求。

本次 `TeaNeko-App-4.9.1-l.jar` SHA-256：`80218909faebe3e3f50edd3038ae0b38296dd09076e42e1d0a2c4b7015f68e19`。本轮只增加两个 Java 类型，没有删除或改名类型；实际构建 class 与 JAR 均已核对。

完整证据位于 `temp/verification/4.9.1-l/background/`，JavaDoc 参数、HTML 和日志位于 `temp/javadoc/4.9.1-l/background/`。源码说明见[图库](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/README.md)、[分层记忆](../../../src/main/java/org/zexnocs/teanekoplugin/agent/cognition/memory/layered/README.md)及[结构化输出](../../../src/main/java/org/zexnocs/teanekoagent/llm/framework/structured/README.md)。
