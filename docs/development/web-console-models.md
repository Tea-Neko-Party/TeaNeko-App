# 模型配置

v4.8.0-l 的聊天模型选择位于“聊天设置 → 组件装配 → 大语言模型”。系统模型继续使用本页流程；聊天私有模型直接保存到专用 cfg 数据库，不写文件，见[Agent 组件装配](agent-composition.md#聊天私有模型)。

v4.7.5-l 起，“模型配置”独立于普通本地配置。入口需登录管理员会话，且安全文件的 `file-sections` 包含 `agent.models`，相关插件已经启用。沿用原 `model.yml`，没有新增凭据文件。

## 添加和调整

1. 点击模型目录右侧“＋”，在弹窗内选择 DeepSeek、Kimi 或 OpenAI 类型，再填写新模型；类型不会直接展开在主页面。地址及接口路径由供应商适配器填写。具体模型默认从列表选择；需要自定义名称时打开“手动输入”。已保存模型也使用相同控件，旧名称不会被列表更新覆盖。选择已知型号时同步其真实上下文容量，手动填写时需自行核对。
2. API key 选择“手动填写”并输入新密钥，或选择“环境变量”只填写变量名，如 `DEEPSEEK_API_KEY`；后者保存为 `${DEEPSEEK_API_KEY}`，应用时由 Core 统一解析。变量必须在后端进程环境中存在，网页不会读取或显示变量值。
3. 可勾选设为默认模型，然后加入本次修改。“保存到磁盘”只写文件；“应用磁盘配置”独立发布到当前进程。保存前会提示 YAML 将重新排版且不保留注释。
4. 模型以摘要小卡片排列，显示路由、型号、默认标记及输出/上下文额度；点击卡片才打开单个模型的编辑弹窗。型号和手动开关同行，高级模式展示其他原有参数。关闭弹窗保留本页草稿，再通过页面保存和应用；已有密钥和连接不回显，保持原值可保留原始环境模板。

样板可先以空密钥保存，实际调用前须配置有效凭据。预检和应用确认不调用远端，也不证明供应商授权、余额或模型能力可用。v4.7.7-l 起同一协议可添加多个配置，`id` 是唯一配置路由，`adapter` 是实际协议；例如 `id: deepseek-chat`、`adapter: deepseek`。每条路由独立保存型号、密钥和连接，重复 `id` 被拒绝。旧文件省略 `adapter` 时沿用同名协议，一次编辑允许一个结构增删动作，保存后可继续添加或编辑。

模型图标随程序从本地资源加载：DeepSeek 使用无文字的鲸鱼图标，Kimi 与 OpenAI 使用各自官方图标。品牌名称和标志属于对应供应商，仅用于识别所选服务。[DeepSeek](https://api-docs.deepseek.com/)、[Kimi](https://www.kimi.com/resources/kimi-brand)、[OpenAI](https://openai.com/brand/)。

## 切换聊天模型

先在“模型配置”添加、保存并应用路由，再进入该聊天的“聊天设置”选择“使用模型”；空值继承文件默认。这个字段也在网页房间内的聊天设置子页显示。

命令 `/agent models` 列出已经应用的路由；OWNER 可使用 `/agent model deepseek-chat` 切换当前聊天，或 `/agent model default` 恢复继承。模型选择与其他聊天配置共用原版本事务；切换从新请求生效，正在执行的旧策略会在后续复核时停止，不自动重发。私聊和群聊继续沿用原配置共享规则。

“自定义模型”选择已注册的协议适配器，自行填写模型名、服务基础地址和相对接口路径；已有模型也可打开“自定义连接”替换地址及路径。自定义连接须符合所选适配器的协议，不会动态加载新 Java 实现。

输出额度统一使用此处的 `max-tokens`。上下文容量必须对应实际模型；输入默认最多占 80%，且仍为完整输出预留空间。切换模型时须同步核对容量，不能只更换名称。样板容量依据对应供应商资料：[DeepSeek](https://api-docs.deepseek.com/api/list-models/)、[Kimi](https://platform.kimi.ai/docs/guide/kimi-k2-6-quickstart)、[OpenAI](https://developers.openai.com/api/docs/models/gpt-5.5)。

## 扩展入口

### 维护的型号列表

截至 2026/11/04，页面手动维护以下选项，不启动供应商模型列表请求：

- DeepSeek：`deepseek-flash`、`deepseek-v4-pro`，上下文均为 1,048,576 tokens。[官方型号说明](https://api-docs.deepseek.com/quick_start/pricing/)
- Kimi：`kimi-k3.1-pro`、`kimi-k3.1-pro-highspeed`、`kimi-k3`（1,048,576 tokens），以及 `kimi-k2.7-code`、`kimi-k2.7-code-highspeed`、`kimi-k2.6`（262,144 tokens）。K2 系列对新注册账号的开放范围受供应商限制，已有配置不自动迁移。[官方模型列表](https://platform.kimi.com/docs/models)、[容量与开放范围](https://platform.kimi.com/docs/pricing/chat)

后续型号变化由维护者更新供应商目录中的 `models` 和 `contextWindows`，随补丁发布；网页可通过手动输入开关立即填写新的兼容型号。列表声明和保存成功不证明当前 API key 对该型号具有调用权限。

供应商目录中的 `@LLMConfigurationAdapter(id = "实际协议标识", icon = "/console/models/图标文件")` Spring Bean 实现 `ILLMConfigurationAdapter`，返回公开名称、说明、默认参数及模型名建议；固定地址和路径复用同目录适配器常量。图标是可选的同源资源，未声明时显示名称缩写；新增适配器不需要修改前端供应商判断。只有模型服务中实际注册且插件启用的协议进入目录，重复标识和声明不一致明确报错。

新增凭据输入方式使用 `@LLMCredentialInput(id = "唯一标识")` 并实现 `ILLMCredentialInput`，提供显示文本、掩码、正则及前后缀编码。页面自动发现并构造原文件可接受的字符串；扩展输入方式不会自动为后端增加新的凭据解析协议。完整职责见[插件指南](plugins/management.md#网页房间与模型配置扩展)。

目录接口 GET `/api/v1/model-configurations` 只返回公开样板，不读出配置凭据。实际预检、保存、应用及回执继续使用 `/api/v1/file-configs/agent.models` 下的原接口、文件版本和操作编号；未确认操作先查询回执，不能自动换编号重试。详细事务及权限见[文件管理](web-console-files.md)。
