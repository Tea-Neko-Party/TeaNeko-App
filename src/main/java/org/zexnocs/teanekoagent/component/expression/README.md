# 拟人表达与有序交付

当前交付：4.6.2-l 已接通文字分段、发送节奏、类别选图及 STICKER/FACE 独立发送；命令和自动回复共用真实目标队列，自主文本保留原零工具合同。采集与选图配置见[素材模块](../sticker/README.md)，交付与验收见[实施计划](../../../../../../../../docs/plan/old/agent-expression-stickers.md)。

## 使用与配置

v4.8.2-l 的普通回复优先使用正文，只有分段或专用指令才使用信封。格式错误通过安全诊断分别说明 BEGIN、END、当轮标识或指令问题，不记录模型原文、不交付半截控制文本；默认经原发送器提示用户，可用 `/cfg set agent failureReplyEnabled false` 关闭失败提示。

v4.8.3-l 在整个计划冻结前检查解码后的正文，拒绝混入内部工具调用标记；JSON 转义不能绕过检查。普通代码围栏示例保留，包括自动分段后的长示例。模型阶段的有限协议纠正见[交互规则](../llm/framework/interaction/README.md#内部工具标记泄漏)。

默认能力 teaneko.agent:expression 安装到使用默认能力的实例，显式 features 集合需要加入该能力。实际来源 scope 仍须已登记并启用 Agent；通过 `/cfg set agent expressionEnabled false` 关闭该区域的表达合同。关闭后保留原普通图文/JSON 回复和统一目标顺序，不关闭人格或记忆。

本地 config/agent/expression.yml 由原 FileConfig 服务加载，缺失时使用项目模板；支持 Core 环境占位符，读取不写回。它只控制开关、数量和时序，模型和凭据仍在 model.yml，人格在原人格模块。

| 参数 | 默认值 | 约束与含义 |
| --- | ---: | --- |
| enabled | true | 本地拟人总开关 |
| max-messages | 8 | 新特殊信封最多 1–32 条；兼容协议保留原 32 条硬限制 |
| max-step-delay | 3000 | 单段等待毫秒上限，允许 0–5000；首段额外限制最多 500ms |
| max-total-delay | 10000 | 全轮新增等待毫秒上限，允许 0–30000 |
| default-delay | 350 | 未显式等待的后续段基数，须不超过单段上限，再按有限字数增加 |
| timeout-seconds | 90 | 排队、原模型工作及交付的独立总截止，允许 1–180 秒；原模型预算和时限继续生效 |
| max-queued | 16 | 每个真实目标的在途项上限，允许 1–128；实例合计另有限制 256 项 |

当前已采用的人格、用户偏好与聊天场景决定表达。闲聊可以自然减少句号、采用少量口癖和颜文字；代码、数值、链接与正式说明保留格式。后端不会机械删除标点、重写文字或为拆句额外调用模型。

## 一次输出的实际协议

模型从本轮完整表达合同取得十二位标识；下面只是示例，不是可直接用于任何轮次的固定标识：

```text
⟦TNK:a7c2f9104d6e|BEGIN|protocol=teaneko:expression|v=1⟧
⟦TNK:a7c2f9104d6e|TEXT|mode=reply|wait=0⟧
好呀
⟦TNK:a7c2f9104d6e|TEXT|mode=plain|wait=850⟧
我再看看 (・ω・)
⟦TNK:a7c2f9104d6e|END⟧
```

TEXT 支持 reply/at/plain，默认 plain；wait 可省略，使用非负十进制毫秒，语法硬上限 60000，再按本地单段与累计策略缩短。间隔从前一段真实回执完成后计算，最终计划只归一化一次，重放不重新分段或改时间。

控制行独占一行，允许行首、行尾空白，但 `⟧` 后不能接正文或标点；正文从下一行开始，原有缩进和行尾空白保持不变。字段禁止重复；当轮标识、版本、协议或指令无效时在第一段发送前失败。代码围栏中的标记保持字面，正文可用反斜杠转义控制前缀。普通图文与旧 teaneko_reply:1 JSON 继续可用；新协议解析失败不回退成正文。原生入站、工具结果、记忆或模型推理都不作为正式回复协议执行。

## 扩展开发

### 独立原生表情

安装 expression 后，`NativeFaceService` 独立提供 `native_faces` 当轮资料；只装 llm+expression 也可使用客户端原生表情，不需要 tool、sticker 或图库文件。目录遵守媒体总开关、scope 原生表情开关、客户端能力及插件状态，不访问图库事务。

FACE 只能使用当轮签名目录中的 ID，许可绑定请求、真实目标、机器人账号、配置和扩展代次，十分钟失效；编译与发送前复核。每轮最多四条、不重复 ID，单独发送并等待有效消息 ID。图库类别、`sticker_list` 与 STICKER 仍由 sticker 提供，详见[素材模块](../sticker/README.md#类别选图与原生表情)。

| 扩展 | 声明与接口 | 精确键 |
| --- | --- | --- |
| 完整协议 | Spring Bean + @TeaNekoReplyProtocol + IReplyProtocolParser | value 和 version |
| 协议指令 | Spring Bean + @TeaNekoReplyDirective + IReplyDirectiveParser | protocol、version 和 value |
| 内容处理 | Spring Bean + @TeaNekoReplyPartHandler + IReplyPartHandler&lt;P&gt; | 内容 ID/version，加准确 Java payload 类型 |

复用 Core [AnnotatedBeanRegistry](../../../teanekocore/framework/extension/AnnotatedBeanRegistry.java) 与既有 IBeanScanner，扫描一次并保留实际 Spring 代理。扩展类需用 @Service/@Component 或明确 @Bean 加入容器；不能仅写注解而没有 Bean。重复键、错误接口、重复 payload 类型和保留的 BEGIN/END 指令名明确拒绝，不按扫描顺序覆盖。

完整协议解析器从实际 assistant 最终答案生成草稿，并通过 instructions 提供语法。指令解析器只处理本条字段和字面正文，不做平台发送。可参考 [TextReplyDirective](TextReplyDirective.java)；新增类即可扩展指令，宿主不需要增加中央 switch。

内容使用不可变 ReplyPlans.Content 实现，推荐不可变记录及集合。处理器通过 type 声明准确类型：resolve 借用 ReplyCompileContext 中原请求、实际输入、控制、当前真实工具回执及完整草稿，可异步校验来源、数量和重复；prepare 纯验证并冻结版本、方式、等待、历史描述及完整摘要；history 生成普通语义内容。所有资源解析完成后才交付计划，不能在 resolve/prepare 发送消息，也不能在 Spring 单例字段保存“当前用户”。

字面内容继续使用 send(data,content,mode)；有异步资源的实现覆写 send(ReplySendContext,content,mode)。公共注册入口独立等待原守卫，扩展在异步读取后、真正传输前再次等待 context.guard().get() 并检查借用控制。上下文提供原请求、段号及真实目标，不写入内容或组件；在途任务取消后仍以实际结束为准释放资源。可参考 [StickerReplyHandler](../sticker/StickerReplyHandler.java) 和 [StickerReplyService](../sticker/StickerReplyService.java)，不直接调用资源处理器的旧无上下文入口。

编译端口返回 CompletionStage，生产宿主传入并拥有原 ExecutionControl，完成回调回到业务域，不 join 数据库阶段。扩展不得关闭借用控制、线程池或连接。解析器不接收任意模型指定的 Java 类名；原目标、client、权限、预算和计划数量继续由宿主复核。

第三方实现声明 @PluginOwned，所属插件必须注册且启用；未声明的项目内实现沿用 teaneko.agent。独立旧宿主可在没有插件目录时注册无归属实现，不能借此跳过已声明的插件归属。每轮固定扩展版本，禁用再启用也不恢复旧计划。

发现实现和实际启用分开：ExpressionFeature 由实例选择，scope 及本地开关决定是否注入合同；只有实际采用了合同的轮次生成新计划。没有提供相应 Feature 的原宿主不会自动获得新语法。

## 发送、取消与历史

ReplyCoordinator 在生成前按 client/scope/机器人账号取得顺序位置，命令、自动回复和自主文本共用；其他目标继续并发。等待借用 Core [ExecutionDelays](../../../teanekocore/actuator/execution/ExecutionDelays.java)，不 Thread.sleep、不阻塞 TaskStageChain、不持数据库锁跨等待。

`/agent stop 请求ID` 能停止本人已生成但仍等待发送的请求。取消立即结束观察，未开始的队列项在前项实际结束后自动跳过，避免中间项取消使后项越过原发送；入口关闭只取消自己拥有的工作。已经开始的模型或传输仍以真实结束为准，不把观察取消当成副作用回滚。

`config/agent/expression.yml` 的 `timeout-seconds` 默认为 90 秒，覆盖入队、资料与准入等待、模型生成及发送全过程，不是单次 HTTP 超时。原生 `/agent chat` 超时或主动取消会提示一次；入口关闭时不额外发消息。超时后迟到的准入结果不会再启动模型，迟到的模型结果不会再发送答案或重复状态提示。

每段依次等待、复核原结果/资料/人格/票据/目标、持久占用、再复核控制及扩展版本、发送并等待平台消息 ID、提交回执。重连导致机器人账号改变时不能改投新账号。原回执为未知时不补发，已确认部分才进入送达投影；新指令处理器不能绕过这些公共步骤。

Approved 由编译器封装，外部不能直接构造“已审核计划”。原答案摘要、版本、步骤、等待和语义答案一起冻结；诊断保留模型原文，窗口和持久历史只使用经再次核验的语义正文。控制符不会作为普通聊天沉积，旧 raw/tool 身份和前缀验证仍保留。重放缓存计入计划大小，重启不恢复未发送尾部。

自主发言继续使用原 speak/text、一次模型、零工具合同，仅接入共同顺序，不在本批开放表情包目录工具或特殊图文信封。其他独立插件的发送不自动获得 Agent 队列的顺序保证。

## 验证范围

协议、代理、顺序、取消及原自主发言保持原核心覆盖；媒体闭环复用原事务和假客户端，验证注解查询、当前 ID 许可、独立编码、真实回执、文件占用及目录预算。实际模型文风、OneBot 展示、等待体验与生产负载仍需实机验收；历史证据通过实施入口查阅。
