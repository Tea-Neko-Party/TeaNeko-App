# 实例共享表情包素材库

已实现主动/被动采集、受限下载、内容去重、一次视觉分析、持久作业、容量维护、类别查询、STICKER/FACE 独立发送，以及全局管理 SPI 和受限事件。机器人只从本轮实际提供的资源中选择，不保证每次收藏后立即使用该图。进度见[实施入口](../../../../../../../docs/plan/old/agent-expression-stickers.md)。

## 启用与配置

本地文件为 config/agent/stickers.yml，字段和注释见[模板](../../../../../../../src/main/resources/templates/config/agent/stickers.yml)。已有部署只补缺少的文件，避免覆盖已有模型凭据。模型、连接和密钥仍统一放在 model.yml；model-id 填写已有适配器 ID，空值沿用默认路由。该模型必须明确声明 USER 图片能力；缺少视觉路由时作业每五分钟检查一次，最长保留一天，不启动普通聊天或额外工具循环。

已登记且 enabled=true 的 Agent scope 默认 stickerCollectEnabled=true。收到真实人类普通图片即可入队，无须先提及机器人，不依赖 memoryLearningEnabled、historyCaptureEnabled 或 expressionEnabled。每条入站最多观察前四张图片，排除机器人自身与 OTHER 消息；客户端原生表情不会仅因带有 URL 而混入图库。

- enabled 控制图库功能；collect-enabled 控制主动/被动采集，passive-enabled 单独控制被动采集。
- 单区域可用 `/cfg set agent stickerCollectEnabled false` 停止采集。关闭本地采集仍会回收待处理作业和暂存，已有有效图库保留；正常维护不等于清空图库。
- catalog-id 默认 main，是部署实例的稳定身份。全实例共享，不按 client、scope、人格或记忆 namespace 分库；clear/reset 记忆不触及素材域。
- storage-root 默认 data/agent/stickers；多副本必须同时共享目录和数据库。目录中的身份文件与数据库绑定，换盘或丢失身份时拒绝自动接管，不删除对方看不到的文件。

| 预算 | 默认值及含义 |
| --- | --- |
| max-active / max-per-category | 全实例 200 项 / 每主类别 32 项，硬上限分别 1000 / 64 |
| max-pending / max-pending-bytes | 最多 32 项待处理及待清理作业，共 64MiB 最坏文件预约 |
| max-storage-bytes / max-file-bytes | 共 512MiB / 单图 6MiB；预约、有效与待删文件共同占用预算，短时保守重复计数 |
| downloads-per-hour / scope-downloads-per-hour | 实例每小时 120 次、单 scope 30 次；主动请求不绕过额度 |
| analyses-per-hour / scope-analyses-per-hour | 实例每小时 20 次、单 scope 5 次；已有同图结果复用，不占第二次分析额度 |
| timeout-seconds / maintenance-seconds | 作业 60 秒；无后台作业时每 60 秒回收及轮转校验，单轮各最多八项 |
| min-confidence / min-quality | 0.85 / 0.65；质量是一次模型给出的综合分数，不是客观测量 |
| probation-days / replacement-margin | 入库后三天保护期，新项竞争分数至少高出旧项 0.08 才替换 |
| score-weights | 质量 0.40、独立人类 0.20、衰减频率 0.20、近期使用 0.10、类别稀缺 0.10；五项非负且总和为一 |

categories 可配置至多 32 个稳定 ID 到中文说明的映射；缺省为开心、伤心、生气、疑惑、惊讶、赞同、拒绝、安慰、感谢、尴尬、调侃、等待。每图只有一个主类别和至多四个次级标签。

## 主动收藏工具

Spring 自动扫描 StickerCollectionTools：

| 工具 | 参数与行为 |
| --- | --- |
| sticker_collect | messageId：当前消息或其直接引用的原始 ID；imageIndex：从零开始的图片序号。宿主从原 client 消息端口读取并验证同一 scope，只返回持久入队回执 |
| sticker_collection_status | jobId：原回执中的作业 ID。只允许原请求者在原 scope 查询，不暴露源图片 URL、作者或群聊正文 |
| sticker_list | categoryId：本轮类别摘要中的 ID。返回该区域当前全部可用 ID、revision、画面摘要与完整用途；complete=false 时没有可授权条目 |

工具不接受 URL、本地路径、client、scope 或任意作者。主动收藏仍须当前区域允许采集，不强制把画作认定为表情包。模型可以向用户说明“已提交收藏”，只有 SAVED 回执才能说明已入库。

状态为 QUEUED、RUNNING、SAVED、DUPLICATE、REJECTED、FAILED。重复源消息可能返回同一作业；旧回执已回收时 SOURCE_SEEN 仅说明曾处理过该来源，不代表收藏成功。CONTENT_SEEN 且附带 stickerId 才表示复用已有有效素材。BUSY、CAPACITY、DOWNLOAD_RATE、ANALYSIS_RATE、VISUAL_REJECTED、QUALITY_CAPACITY 等原因码区分未受理、限流与分类/容量拒绝。

## 下载、视觉与维护

只处理消息端口给出的 HTTP(S) 或短内联图片引用，持久引用最多 16384 字符。默认信任模板中的平台媒体域名；每跳重定向均校验白名单、端口及 DNS 解析地址，最多三跳，不使用进程代理。私有媒体服务须同时在 allowed-hosts 和 private-hosts 中明确允许。域名白名单表示部署者信任其 DNS 管理，JDK HTTP 客户端不提供本实现可用的地址钉住机制。

按实际解码确定 PNG/JPEG/GIF/WebP，缺少解码器则拒绝。像素上限 4194304、帧数上限 120、总画布解码像素上限 33554432；读取每个子帧前也校验尺寸。GIF 处理画布偏移、背景恢复和前帧恢复，最多四幅合成采样帧在同次视觉请求中发送，并带采样时间。原动画字节保持原样；APNG 及不能可靠合成的其他动画首期明确跳过。

视觉端口通过统一 STICKER_ANALYSIS 活动、SYSTEM 身份、一次模型调用、零工具和原执行控制工作，不创建普通会话或记忆。严格 JSON 同时给出类型、置信度、收藏建议、画面/用途、主类别、标签、综合质量与简短依据。宿主再次要求 STICKER、可信阈值、质量阈值及受控类别；插画、照片、截图、其他或不确定结果不会激活。

同一 SHA-256 与 analysis-version 复用缓存，拒绝与分析不确定结果通常保留 30 天。超时或中断可能已经产生供应商费用；普通重复发送不自动重试这次不确定分析。切换分析器、模型或识别规则时同步递增 analysis-version；它使再次采集的非有效内容重新判断，不对已有图库立即全量重评。缓存总量最多 2048 项，满载优先淘汰无文件且无在途占用的旧项；被淘汰的图再次出现可能按当前额度重新分析。

人类频率只来自已接受的真实入站，按 client/scope/消息 ID/图片序号幂等；同一作者每天评分贡献最多三次，保留近 30 天至多 32 个作者日计数。主动查询旧消息、机器人回显与工具调用不计人类热度。统计是经过采集限流后的观察样本，不能当作全群精确使用总量。

数量或类别满时，确定性评分比较未固定、无有效发送占用、已过保护期的旧项。淘汰和激活在一个根行事务中完成，旧文件随后回收；容量不能低于固定项要求，不会自动解除固定。缩容按分数收敛，文件缺失或损坏立即撤销有效状态。

元数据位于 TeaNekoAgentEasyData 的 agent.sticker.v1，target 为 catalog:<catalog-id>。一行有界状态在原 DTO 写锁下仲裁作业、限流、缓存、目录及回收；没有状态变化时不重复写入。模型预览不持久化，短期来源引用随作业回收，最多保留最近 128 项已清理终态且最长一天；来源去重摘要最长 48 小时、至多 8192 项。

文件与数据库不是同一原子事务：先登记作业并预约，下载暂存、提交内容元数据、原子发布，最后事务激活。失败或重启留下的已知暂存和发布文件由持久作业收敛；后台租约五分钟过期后将原在途任务标为不确定，不重复启动其视觉任务。清理确认前不释放容量预约；不递归删除目录，不接管未知文件。

## 扩展开发

已有 Spring Bean 实现下列接口并添加对应注解，通过 Core scanner 和注册目录发现；同类重复标识、错误接口和不支持的版本明确失败。目前契约版本为 1，文件配置明确选择标识，不依赖扫描顺序覆盖。

| 端口 | 注解 | 默认实现与配置 |
| --- | --- | --- |
| IStickerAnalyzer | @TeaNekoStickerAnalyzer | LLMStickerAnalyzer，analyzer: llm |
| IStickerAssetStore | @TeaNekoStickerAssets | LocalStickerAssets，assets: local |
| IStickerAdmissionPolicy | @TeaNekoStickerAdmission | WeightedStickerAdmission，admission: weighted |
| IStickerCatalog | @TeaNekoStickerCatalog | EasyDataStickerCatalog，catalog: easydata |

实现继续遵守 @PluginOwned 及原插件启停代次。端口与控制仅在宿主任务上下文中持有，不进入组件或模型。目录事务回调只操作有界状态，文件/网络/模型动作在事务外执行；后端替换必须保留原子仲裁、容量预约、持久身份、租约及提交后才公开结果的合同。文件后端须让相同 catalog-id 的副本看到同一身份和内容，删除必须幂等。

文件后端同时实现 preview：只对已有受控 Blob 返回有界合成 PNG，不重新下载来源、不发布文件。管理刷新复用该能力及原一次视觉分析合同。当前 readers 由实际发送、预览和刷新取得/释放；robotShows 与 lastRobot 仅在真实平台确认后更新，独立于人类评分。

## 全局管理 SPI

通过原 ManagementGateway 注册四个真实资源，不开放 HTTP 或默认管理身份；[管理指南](../../../../../../../docs/development/plugins/management.md)说明宿主认证接入。ManagementContext.scopeRef 必须明确为 global，主体来自可信适配器，不能用群管理员身份直接查看整个实例图库。

| 资源 | 请求和权限 | 结果 |
| --- | --- | --- |
| teaneko.agent:stickers | Query(kind,after,limit,category,state)，agent.stickers.inspect | CATALOG 目录或 JOBS 作业，稳定 ID 游标，1–100 项及同快照容量；category/state 只过滤目录 |
| teaneko.agent:stickers.preview | Preview(id,expectedRevision)，agent.stickers.preview | 当前有效/停用项第一幅合成 PNG 内联数据，最多 1.5MiB；不返回原路径或来源链接 |
| teaneko.agent:stickers.change | Change(operationId,id,expectedRevision,action,details,analysisScope)，agent.stickers.manage | 真实提交的条目版本、状态、目录版本，以及刷新 jobId |
| teaneko.agent:stickers.operation | OperationQuery(operationId)，agent.stickers.inspect | 当前主体本人原操作的持久回执，标为 replayed |

写入使用公开条目 revision 作 CAS；同 operationId、主体和参数在 24 小时内重放真实回执，不重复执行或通知，不要求旧 revision 仍为当前值。相同操作 ID 换参数或主体明确拒绝。回执至多 512 项，窗口已满拒绝新写入；返回失败或取消不证明此前没有提交，应查询原 operationId。它是业务幂等记录，不替代独立安全审计。

| action | 行为及限制 |
| --- | --- |
| PIN / UNPIN | 固定有效/停用项，或解除固定；固定项仍可被明确停用或删除 |
| DISABLE / ENABLE | 人工停用保留文件；普通同图聊天不能重新激活。启用重新检查文件卷、类型、阈值、类别和容量，不自动挤掉其他项 |
| DELETE | 撤销选择并解除固定，保留摘要墓碑；等待已有文件占用结束后回收字节，同图再次出现仍被拒绝 |
| RESTORE | 显式解除墓碑；回收中的文件暂不可恢复。有文件变为停用，无文件变为过期缓存，后续重新采集或显式启用 |
| PATCH | details 完整提供 category、visualDescription、usageDescription、tags；仅修正已有表情包，不把插画或低置信度结果提升为合格项 |
| REFRESH | 必须提供真实已登记 analysisScope(clientId,scopeId)，所在区域允许采集且客户端账号就绪；原文件进入后台 SYSTEM 分析，不下载旧 URL，不增加人类热度 |

刷新立即停用并登记文件占用，经原有有界队列、分析额度和视觉路由执行。原本有效项在通过复核后可重新激活；原本停用项分析完成仍停用，需显式 ENABLE。缺少视觉路由按原队列延期，失败仍保留停用文件。刷新期间发生人工修改会使迟到结果版本失效，不能覆盖人工修改。预览最多两项并发、每项有独立租约；取消观察不提前释放仍实际执行中的文件读取。

## 受限生命周期事件

使用 Core @EventListener、@EventHandler 和 @PluginOwned，插件必须在 stickers.yml 的 event-plugins 中明确选择并保持启用；默认空列表。至多 16 个插件，分发继续遵守原插件代次，不新增另一套事件 scanner。

| 事件 | 数据 | 可操作部分 |
| --- | --- | --- |
| StickerCollectingEvent | Candidate(proposalId,scope,manual,image,occurredAt)：稳定候选 ID、真实区域、主动请求标记、原图片序号、时间；不含 URL、作者和正文 | 同步 setCancelled(true) 拒绝，suggest 提供至多 256 字符低信任视觉提示；不允许替换来源、重开取消或异步监听，分发结束/失败/超时后冻结 |
| StickerCommittedEvent | Notice(catalogId,kind,operationId,entryId,catalogRevision,at,status)：图库、业务种类、作业/操作 ID、可能为空的素材 ID、提交版本、时间及终态/动作 | 只读，可异步观察；取消和替换均拒绝，回调失败不改变已提交结果 |

采集决策在入队前执行，随后宿主重新核验真实来源、开关、配额和容量；建议不能绕过插画判断。决策等待至多五秒，物理任务未结束仍占用并发名额；决策与通知合计最多 64 个在途事件。通知仅在真实事务成功后发出，幂等重放不再次通知；通知拥塞或失败可能丢失，管理订阅应据 operationId/目录重新查询，不能把内部通知当可靠审计或直接推送给浏览器。插件示例见[Agent 插件指南](../../../../../../../docs/development/plugins/agent.md#表情包生命周期事件)。

## 类别选图与原生表情

默认能力 teaneko.agent:media-replies 提供 reply_resources 资料及注解 sticker_list 工具，并声明依赖 teaneko.agent:expression。显式 features 只选择媒体能力时也会安装表达合同。仍要求真实原生命令或自动回复目标、已登记 enabled/表达开关，以及明确的客户端适配；普通 SERVICE 和自主发言不扩大为目录工具调用。

每轮资料只提供非空可用类别的名称、数量和当前 client 的原生表情目录，不注入全部素材。模型希望用图时查询选中类别；每图描述包含最多 48 个 Java 字符的画面摘要（有省略标记）和完整用途，不返回源群、作者、源消息或路径。完整类别结果最多 7800 字符，并检查已知输入预算余量；预算不足明确返回 complete=false、空 items 和原因码。默认最多展开两个类别，同请求同类别复用同一回执。

本轮成功且未截断的真实工具回执才能授权 ID；另一群、历史记录、伪造描述和旧请求中的同 ID 都不能替代当前查询。一次失败/不完整类别不妨碍选择另一个完整类别。工具回填在普通历史中使用原临时结果投影，不长期重复注入整份目录与签名。

配置文件为 config/agent/media-replies.yml，见[模板](../../../../../../../src/main/resources/templates/config/agent/media-replies.yml)：

| 参数 | 默认及语义 |
| --- | --- |
| enabled | true，媒体表达总开关；expression.yml 的表达开关仍须开启 |
| max-per-reply | 2 张表情包，允许 1–4；同一 ID 不重复；原生表情最多四条且不重复，全部步骤仍受 expression 的总条数约束 |
| max-categories | 2 类，允许 1–2；完整与不完整查询都占一次类别展开位置 |
| cooldown-turns | 3；同区域已准入的媒体对话尝试中回避近期展示，失败尝试也计轮次，其他区域独立 |
| max-category-characters | 7800，允许 1024–7800，低于原工具回填 8000 字符上限；不能截断目录后仍标 complete=true |
| native-faces | 按 client 配置不透明 ID 到描述，单客户端最多 64 项；首期提供常用 OneBot face，可按实际客户端版本核实增删 |

区域开关 stickerReplyEnabled 和 nativeExpressionsEnabled 默认 true，分别使用原 `/cfg set agent 字段 false` 关闭。采集开关独立：关闭 stickerCollectEnabled 或 collect-enabled 仍可使用已有图库；关闭 stickers.yml 的 enabled 停止图库选择，原生表情仍由媒体和区域开关控制。

最终模型答案沿用[表达信封](../expression/README.md)，资源必须使用独立控制行。例如把下例的当轮标识和素材 ID 替换为本轮实际值：

```text
⟦TNK:当轮标识|BEGIN|protocol=teaneko:expression|v=1⟧
⟦TNK:当轮标识|TEXT|wait=0⟧
好呀
⟦TNK:当轮标识|STICKER|id=本轮查询ID|mode=reply|wait=700⟧
⟦TNK:当轮标识|FACE|id=14|mode=plain|wait=500⟧
⟦TNK:当轮标识|END⟧
```

STICKER/FACE 不跟正文，只能 plain/reply，不能 at；每个资源单独创建消息，原生表情不进入图库数量或人类热度。最终答案只生成一次，后端不为选中图片或逐段发送追加模型调用。

## 发送许可、占用与真实统计

资源资料、完整类别及单项计划由本进程宿主签名，绑定原请求、原身份/账号、配置摘要、目录版本与插件代次，有效期十分钟。签名不是可跨请求使用的权限，不持久保存签名密钥；重启不恢复旧未发送计划。新素材入库不使无关文字回答失效，选中项的 revision、指纹、当前开关和客户端目录在编译与发送前继续核验。

实例最多八项图片传输同时持有原图及 base64，容量等待在借用虚拟域响应取消，不持目录锁或文件占用。随后在目录事务取得原文件占用，在虚拟执行域读取、复核大小与摘要，再等待原守卫和条目状态检查。默认 OneBot 通过 base64 上传原始字节，原动画不变，不使用远端不可见的 file:// 路径，也不回退到旧下载 URL。文件缺失或版本变化拒绝发送，不暗中换图。

占用最长十分钟作为进程中断后的恢复边界，实际正常任务在原平台阶段结束后释放；取消观察不提前释放仍在途的读取/传输。平台已确认才幂等记录 robotShows、lastRobot 及当前区域回避。已开始发送后的停用不撤销外部动作，确认统计也不假装未发送；机器人展示永远不加入人类喜欢程度评分。

区域轮次目录最多 1024 项，保留七天，只有十分钟以上未更新的旧区域可在满载时让位；每区域保留最近 16 个请求和至多 16 轮内的已用 ID。类别缓存最多 1024 个请求、每请求最多两页，随原十分钟资料失效；真实展示幂等摘要最多 8192 项、保留十分钟，均不保存原对话。

## 扩展客户端编码

已有 Spring Bean 实现 IExpressionClientAdapter，添加 @TeaNekoExpressionClient(clientId)，由 ExpressionClientRegistry 使用原 scanner 注册；重复 client、错误接口或不支持版本明确拒绝，未知 client 不推测图片和表情能力。

- images 明确是否支持读取图库后的内联上传；faces 返回当前客户端允许的有界原生目录。ID 对模型是不透明值，只有该适配器解释其编码。
- image/face 每次建立独立消息，只可 plain/reply，并返回有真实消息 ID 的确认阶段；保留原平台状态、零重试和失败不自动补发，不能将任务“接受”当作成功。
- 声明 @PluginOwned 时沿用原插件目录及代次；目录或插件换代使旧计划失效。实现仍由原容器拥有，不在单例中保存当前用户，不关闭借用控制。

默认 [OnebotExpressionClient](../adapter/onebot/OnebotExpressionClient.java) 提供图片和 face。新增其他客户端只需实现该端口及注解，不修改协议解析或中央分支；扩展新的资源指令继续使用 [表达处理器](../expression/README.md#扩展开发)。
