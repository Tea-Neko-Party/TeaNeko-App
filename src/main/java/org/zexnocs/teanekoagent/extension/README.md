# 文件、代码与知识源扩展

本版提供可运行的工具适配及后端端口。默认应用不安装宿主文件访问、代码执行或完整知识库后端；宿主明确提供实现、授权策略、工具选择及资料源后才生效。扩展复用原 LLMToolBinding、工具预算、Prompt 生命周期和当前来源，不建立第二套执行循环。

| 端口 | 实际工具 | 后端职责 |
| --- | --- | --- |
| IAgentFileAccess.read | file_read | 受控相对路径、目录/链接边界、源版本、有界正文及完整性；区分确认不存在和无法读取。 |
| IAgentFileAccess.write | file_write | 当前真实确认、expectedRevision CAS、NEW 仅创建、防重及真实提交回执。 |
| IAgentCodeExecutor | code_execute | 显式语言列表、隔离环境、实际权限、取消/截止时间、输出脱敏和真实执行状态。 |
| IKnowledgeSource | knowledge_search | 按真实上下文过滤来源，返回 source/documentId/chunkId/revision/text，保留有界检索及完整性。 |

## 装配

由宿主选择所需能力并提供后端，工具与资料必须同时组合：

```java
var extension = new AgentExtensionTools(policy, mapper, executor);
var toolSource = extension.tools(originalTools, List.of(
        extension.fileRead(files),
        extension.knowledgeSearch(knowledge)));
var promptSource = extension.prompts(originalPrompts, Set.of(
        AgentExtensionCapability.FILE_READ,
        AgentExtensionCapability.KNOWLEDGE_SEARCH));
```

将这两个来源传入现有 AgentService 组合。保留原来源的职责和复核，避免在 Spring 中让同类型来源互相注入形成循环。自定义来源仍可被原 Skill 适配器装饰，Skill 可声明上述工具为依赖；缺失工具不自动补入。没有实际保留的扩展声明时，工具源不加入隐藏能力；执行器也核对真实布局，单独取得一个绑定不能绕过声明及授权。

## 授权与业务合同

IAgentExtensionPolicy.check 接收宿主固定的能力和 AgentExtensionContext，调用前、结果公开前、后续模型使用及完成重放前持续复核。身份、scope、固定记忆引用和执行控制来自实际组件，模型 schema 只接收业务参数，没有任意用户、数据库、根目录或“已授权”布尔值。

上下文的 phase 明确区分 EXECUTION 和 REFERENCE_CHECK。真实执行的确认及权限必须在 EXECUTION 阶段检查；资料检查只读复核当前来源资格。不能根据 callId 的特殊字符串判断阶段，模型可能使用相同的调用 ID。策略可结合后端的来源版本、有效期及数据权限提供更严格复核。

文件工具先规范化斜杠，拒绝绝对路径、驱动/URI、空段和上级跳转；后端打开文件时仍须核对真实根目录及链接，不能只依靠字符串校验。读取结果明确 exists、revision、text 和 complete；确认不存在时 exists=false、revision 为空、正文为空且 complete=true。代码端口没有默认解释器或 shell，语言必须由本地后端显式声明。

写入及执行结果使用 COMPLETED、REJECTED、INDETERMINATE。后端接收 requestId+callId 作为原操作标识，同标识改变参数必须拒绝；适配器不自动重试。已经交给后端但失败、超限或未确认的结果返回 INDETERMINATE，不能宣称回滚。授权撤销后不公开迟到输出；取消观察不保证后端外部动作停止，实现需主动响应借用控制。

输入和结果都有硬上限：文件/代码正文最多 16384 字符；读取或执行输出最多 4096 字符；知识查询最多 512 字符、最多四块、正文总量最多 4096 字符。JSON 工具信封最多 7600 字符，防止原工具集静默截断结构；后端应在更大输出时提供完整性标记或拒绝超限。知识页 complete=false 的空结果不能解释为确定不存在，返回 revision 表示观察版本，不宣称内容始终最新。

扩展结果仅用于原轮次，提交历史时保留调用和 ID 配对，以占位文本替换正文；实际交互诊断保留原输出。敏感内容由后端先脱敏，后端异常及权限细节不直接回传模型。涉及 Agent 自有持久记录时仍遵循专用 EasyData 规范，不直接访问 Repository。

## 离线示例及后续实现

```powershell
.\gradlew.bat test --tests "org.zexnocs.teanekoagent.extension.AgentExtensionToolsTest"
```

示例使用内存文件、固定表达式结果及带来源的知识分块，经过真实工具集和授权链；不访问宿主文件或执行候选代码。PDF/网页导入、embedding、向量索引和真实隔离执行环境由后续后端实现，不由当前端口冒充已交付。
