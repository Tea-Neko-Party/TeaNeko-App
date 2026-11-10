# v4.9.1-l Gemini 原生代理验收

2026/11/10 完成适配、实际配置切换、合成 Schema 对照与产物核对，3/3。当前 `gemini` 路由通过 Antigravity 分组专用原生入口成功生成；严格 Schema 对照满足必需字段，原 Responses 路由的历史结论不再代表新入口。

## 实现与配置

新增七个生产类型，全部位于独立 LLM 的 `instance/antigravity`：模型入口、配置样板、请求 DTO/映射、原生消息及响应 DTO/映射。沿用既有注解注册、HTTP 服务、凭据代次、预算、取消及本地结构校验；没有新增 Core/App 分支或插件归属。

支持系统提示、文本/图片、工具声明与续轮、原序思考签名、Schema/JSON 模式、真实终态和用量。原生协议允许无参函数省略 `args`，统一模型中解释为 `{}`，实际执行仍检查工具参数合同；透明回放保留原始部件，显式非法参数拒绝。非成功终态不执行部分工具输出。详细参数及边界见[协议说明](../../../src/main/java/org/zexnocs/teanekoagent/llm/instance/antigravity/README.md)。

按用户要求备份并修改本机 `config/agent/model.yml` 中唯一的 `gemini` 条目：`adapter=antigravity`，基础地址去除末尾 `/v1`，集合路径为 `/antigravity/v1beta/models`。ID、型号、密钥、`image-input`、默认路由、`max-tokens=163840`、上下文容量和其他配置均保留；保留用户 `gradle.properties`，不改变告警阈值。备份位于忽略跟踪的本地临时目录，不进入源码或发布包。

密钥分组是服务端调度范围，Gemini、Responses、Anthropic 是不同 API 协议。用户最终确认分组为 Antigravity。本次验证了其原生专用入口，不需要切换到 Anthropic 或更换密钥。已有 Responses / Chat Completions 适配器继续可选，多个连接使用不同路由 ID 并存，不在失败后隐式换协议。

## 真实合成对照

用户要求切换后复验 Schema，并要求尝试分组兼容。全部请求只使用合成资料，无聊天、业务 Schema 或工具；生成请求每次最多 2048 输出 tokens、无自动重试或重定向。实际请求体由编译后的 Java 适配器生成。

| 检查 | 实际结果 |
| --- | --- |
| 普通 `/v1beta/models` 的 Schema / JSON 两次生成 | 均 HTTP 400，未取得模型结果；后续只读模型查询确认 `API key group platform is not gemini` |
| 专用 `/antigravity/v1beta/models` 的只读模型查询 | HTTP 200，声明支持 `generateContent` 与 `streamGenerateContent`；适配器本次只实现非流式 |
| 专用路径首次严格 Schema | 传输 `URLError`，21.047 秒，无完整响应，结果及用量未知 |
| 专用路径普通 JSON | HTTP 200 / STOP，`{"items":[{"label":"PROBE"}]}`；437 tokens，11.500 秒 |
| 补测专用路径严格 Schema | HTTP 200 / STOP，`{"items":[{"label":"PROBE","context":"TEST"}]}`；1525 tokens，13.562 秒 |

同一合成提示要求省略 `context`，Schema 则要求该字段及枚举 `TEST`。严格模式使用 `generationConfig.responseJsonSchema`，普通模式仅设置 `responseMimeType=application/json`。结果证明当前原生入口在本次合同上执行了结构约束；普通 JSON 没有该保证。两次成功响应合计报告 1962 tokens，失败请求的实际处理与计费不能由缺失响应推断。

首次 400 只证明普通入口拒绝分组，不能证明原生协议不可用。专用入口先由公开路由资料定位，再通过同一网关的实际 GET/POST 确认；不据此断言网关的具体实现身份。公开调研材料在 `temp/docs/antigravity-api/`，历次执行标记、请求体和脱敏结果在 `temp/verification/4.9.1-l/antigravity/`。

原 Responses 严格模式缺字段的历史证据见[前轮记录](json-correction.md)。当前成功不替代业务事实/证据校验，保留本地严格解码和显式一次纠错。

## 离线验证与产物

74 项定向测试通过，0 失败、0 错误、0 跳过。覆盖原生消息/图片/嵌套 Schema、并行工具及签名、真实交互工具续轮、可选空参数、终态/用量、配置注解扫描、稳定路由 ID 与凭据隔离、取消、JSON 纠错及项目分层。目录查询复用发现缓存，不进行网络请求或重复扫描。

93 个当前变更 Java 文件通过 `checkJavaChanges` 和 JavaDoc；`bootJar --offline` 通过。未修改前端实现，构建复用匹配输入的既有前端产物，未重复浏览器验收。完成文档引用、配置保留核对与 `git diff --check`。

实际 `build/libs/TeaNeko-App-4.9.1-l.jar` 清单与运行版本均为 4.9.1-l；2197 个 class、54 个前端文件及更新的模型模板与构建目录逐字节一致，无缺失/额外项。本轮没有删除或改名生产类型。SHA-256：`2ac97cc95d3e94d2c760e98222c243612ce8737a93faa4dcf5a362f6e478c2a6`。

完整日志与测试 XML 在 `temp/verification/4.9.1-l/antigravity/`，JavaDoc 在 `temp/javadoc/4.9.1-l/antigravity/`。尚未启动实际应用；需完整重启加载新适配器及本机新配置。真实图片、业务工具、记忆生成/纠错、数据库事务、全额输出上限与长时连接仍见[环境验收](next-steps.md)，不能用合成成功代替。

## 2026/11/10 图标补充

按用户“为Antigravity提供图标”的追加要求，引入官网 SVG 为 `frontend/public/models/antigravity.svg`，保留来源说明。配置适配器通过原注解声明 `/console/models/antigravity.svg`；通用模型卡片、模型选择复用既有组件，不增加前端协议分支，运行时不访问外网加载图标。

6 项模型/目录回归、前端类型检查与生产构建、Java 规范和 JavaDoc 均通过。生产页面使用隔离接口验证浅色/深色主题：卡片与新增类型选项图片解码成功，无缩写回退或页面异常；已查看两张截图。SVG 无脚本或外部资源引用，源码、前端构建与 JAR 内字节一致。`git diff --check` 通过，未调用模型或真实业务接口。

补充后实际 JAR 仍为 `build/libs/TeaNeko-App-4.9.1-l.jar`；2197 个 class 和 55 个前端文件与构建目录一致。最新 SHA-256：`329b65d7ed06b0fc39e13d7a5fbb106cb1dda78e6e2d76376493b84f975c2892`，取代上方首次适配交付的产物校验值。日志/截图在 `temp/verification/4.9.1-l/antigravity-icon/`，JavaDoc 在同版本的 `antigravity-icon/` 子目录。
