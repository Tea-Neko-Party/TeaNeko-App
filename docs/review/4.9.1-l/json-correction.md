# v4.9.1-l 结构化输出与有限纠错验收

2026/11/10 完成协议核对、纠错实现与离线验收，3/3；随后按用户新增授权完成两次合成远端诊断。当前 Gemini Responses 路由在严格 Schema 请求中仍返回缺少必需字段的正常终态，不能依赖该路由强制保证结构。

## 问题判断

用户报告 `styles[0].context` 缺失。模型交互的 `SUCCESS` 表示取得正常终态，随后仍须经过本地结构与证据校验；它不代表内容已满足记忆协议。本地合同要求风格候选包含 `field`、`pattern`、`context`、`quotes`、`segments`，其中 `context` 是 `GENERAL / TECHNICAL / CASUAL` 枚举，缺失时应拒绝。

本次序列化回归核对实际 Responses 请求中的 `text.format.type=json_schema`、`strict=true`、完整嵌套 `required`、`additionalProperties=false` 与枚举；模型配置的 `max_output_tokens=163840` 保留。没有发现本地漏发 `context` 或把 Schema 放错协议字段。原通用指令误指向 Chat Completions 的 `response_format`，已改为协议无关的“本次请求携带的 JSON Schema”。

原生 Schema 模式的首次提示只保留顶层字段名，完整 Schema 在协议字段中。后续合成诊断已证实当前路由出现未执行必需字段约束的结果；兼容服务没有执行该字段时，模型可能缺少嵌套字段提示。当前资料仍不能确定网关转译与上游中的具体责任层；模型别名也不能证明网关具体映射。官方能力及查询记录保留在 `temp/docs/structured-output/`。

## 授权合成诊断

用户明确答复“授权两次合成诊断”后，执行 `temp/verification/4.9.1-l/json-correction/probe-schema.py`。两次均使用当前 `gemini` 路由，每次 `max_output_tokens=2048`，不发送聊天、业务 Schema 或工具，不跟随重定向、不自动重试。执行标记防止误重复使用本次授权。

合成 Schema 要求 `items` 恰有一个对象，包含 `label="PROBE"` 和必需的 `context="TEST"`；同一合成提示要求仅返回 `label`、省略 `context`，用来检查严格约束能否覆盖相冲突的普通提示。普通 JSON 模式使用相同提示作为对照。

| 请求 | HTTP / 状态 | 实际结构 | 供应商报告总 token | 耗时 |
| --- | --- | --- | --- | --- |
| `text.format=json_schema, strict=true` | 200 / completed | `{"items":[{"label":"PROBE"}]}`，缺少必需 `context` | 359 | 3.031 秒 |
| `text.format=json_object` | 200 / completed | 同上，合法 JSON，未按合成 Schema 补齐字段 | 370 | 4.828 秒 |

结果记录：`temp/verification/4.9.1-l/json-correction/probe-results.json`。合计报告 729 tokens，不据此推算费用。两次响应均未回显格式/strict；判断依据是严格请求返回的完整 JSON 违反必需字段，而非是否回显选项。

这证明当前路由至少存在忽略严格约束的情形，不能保证 Schema；不代表 Gemini 原生 API 或其他路由也不支持。两次合成请求没有测试真实记忆质量或远端纠错成功率。现有本地严格校验和完整 Schema 反馈纠错仍有必要；持续不合规仍按原机制拒绝提交。

## 实现边界

- 独立 LLM 服务新增显式 `generateWithCorrection`，原单次入口保持原行为。仅 `SUCCESS` 响应中的语法、结构、绑定和本地长度错误允许一次反馈重生成，最多两次请求。
- 反馈使用脱敏错误路径和完整 Schema，不回填原错误答案；要求依据原资料完整重生成，不补默认值、不删错误候选、不改别名、不换模型。仍通过原本地严格解析。
- 两次共享父预算与总截止时间，扣除首轮局部调用/token 消耗。追加反馈后的完整输入重新预检，每次真实请求复核准入；实际模型资源排空后才开始纠错或完成，取消及迟到结果不交付。
- 中长期记忆启用此入口，首轮加一次纠错共用批次预算。只有最终有效结果才进入原证据核验及事务；持续失败只登记一次批次失败，不推进水位。网络错误、拒答、截断、取消、权限撤销及证据关系错误不触发即时纠错。
- 原持久退避、同输入三次失败暂停、事实/风格联合事务、行锁与版本合同保持。已有暂停作业不自动恢复。

## 验证

93 项定向测试通过，0 失败、0 错误、0 跳过。包括 11 项纠错边界用例、严格请求序列化、中长期生成器与处理器、持久记忆事务/暂停、人格联合写入及项目分层。

纠错测试通过真实交互服务和 Core 执行控制，覆盖缺字段后成功、连续错误停止、局部/共享调用额度、扣减局部 token、共同截止时间、主动取消、传输失败、拒答、截断、输入扩张、动态准入及真实排空。中长期处理器验证首错后成功仅提交一次、两次错误不提交且退避。旧严格候选集成夹具已适配排空接口和完整预算回执。

84 个变更 Java 文件通过 `checkJavaChanges` 和 JavaDoc。`bootJar --offline` 通过；前端未修改，沿用已存在且匹配构建输入的前端构建任务结果，未重复浏览器验收。完整日志、XML 和产物结果：`temp/verification/4.9.1-l/json-correction/`；JavaDoc：`temp/javadoc/4.9.1-l/json-correction/`。

## 实际产物与部署

`build/libs/TeaNeko-App-4.9.1-l.jar` 的清单与运行版本均为 4.9.1-l。2186 个 class 和 54 个前端资源与构建目录逐字节一致，无缺失或额外项；本次未删除或改名生产类型。

SHA-256：`15b75768e8fd9228944b1aec8fd70ca182703a70b7d059fc5dea878974b77310`。保留用户的 `gradle.properties` 与实际模型配置，未改变告警阈值 163840。

本次没有启动实际应用或访问真实数据库/客户端；真实模型调用严格限于上述已授权的两次合成诊断。后续仅更新诊断脚本和文档，`git diff --check` 通过，未修改生产代码或重新打包，JAR 仍为上述校验值。完整重启后加载修复；已 `PAUSED` 的批次在工作区核对原因后恢复。后续见[环境验收](next-steps.md)，使用方式见[结构化输出](../../../src/main/java/org/zexnocs/teanekoagent/llm/framework/structured/README.md)和[分层记忆](../../../src/main/java/org/zexnocs/teanekoplugin/agent/cognition/memory/layered/README.md)。
