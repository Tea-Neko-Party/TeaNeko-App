# v4.8.6-l Java 结构与书写规范审查

评估日期：2026/11/06。生产基线是单实例、小规模受控用户；审查期间共享记忆与社交计划的 S01–S08 已归档完成，部署环境实机验收仍单列。本轮结论依据当前源码和本轮验证，不把历史计划的完成记录视作本轮已重跑的证据。只审查与记录，不修改生产代码或正式测试。

结论：包级单向依赖和组件扩展的总体方向适合当前 MVP，主要问题集中在 Agent 实现内部。图库尤其存在大量多语句同一行、控制流压缩和复杂异步回调，已不符合 [AGENTS.md](../../../AGENTS.md) 明确要求。`list()` 的问题不只是空格：同一段代码交织进程缓存、数据库事务、预算与异步完成，隐藏了可重试事务中的内存副作用。

## 条目与优先级

| 编号 | 性质／优先级 | 结论 | 修正方向 |
| --- | --- | --- | --- |
| V486-J01 | 已复现功能缺陷／P2 | 图库 `list()` 在可重试事务中累加进程内输入预算，回滚无法撤销；一次重试可把原本装得下的类别误报为 `INPUT_BUDGET` | 预算结算移到实际事务提交后，按请求与类别只结算一次；保留有冲突／无冲突对照验收 |
| V486-J02 | 已确认规范违约、维护风险／P2 | 图库、会话及后台服务普遍将多个状态变化、锁及异常分支写在同一行 | 先按行为等价展开控制流，再分离私有职责；不得一边格式化一边改变锁、预算及取消语义 |
| V486-J03 | 诊断缺陷／P2 | 本地图库多处捕获 `IOException` 后抛出没有原原因的通用异常，底层安全类型与位置丢失 | 复用安全诊断快照，保留固定中文业务说明，不能直接公开路径或原始异常文本 |
| V486-J04 | 文档与类型维护／P3 | 部分 JavaDoc 已有真实乱码、修改记录间空行；个别类型化入口绕经 `Object` 后再未经检查转换 | 按修改范围恢复说明、补必要契约；通过有类型的辅助方法移除可避免的转换，不批量重置版本与创建时间 |
| V486-J05 | 工程治理／P3 | 尚无版本化格式配置；Qodana 有基础配置，但没有启用失败条件，也未见仓库内 Java 格式门禁 | 约定 IntelliJ 项目格式，在新增／改动范围逐步执行；不要以全仓重排制造巨大差异 |
| V486-J06 | 非阻断结构风险／P3 | 会话主服务与长期记忆 Store 聚合多项业务状态和恢复逻辑，变更影响面偏大 | 依实际变更热点提取有明确合同的协作者；MVP 不必立即拆微服务或重建全部目录 |

## 扫描范围与证据等级

2026/11/06 21:00（Asia/Shanghai）的只读扫描快照包含 `src/main/java` 的 1312 个 Java 文件与 `src/test/java` 的 200 个文件。下表是去掉注释、字符串后得到的**词法候选统计**，用于排序人工复核，不能把每个命中都算成缺陷。普通 `for` 的语句分隔符、方法声明和必要长类型等仍须结合语法判断；120 列是软上限，不是逐行拒绝标准。

21:47–21:50 期间又有并行实现变更。收尾已按当前文件内容和审查开始时的 SHA-256 复核本文具体定位：J01–J03 的引用实现未变，J04 的 `metadata` 未经检查转换及三个乱码示例仍在，J05 的配置缺口和 J06 的职责热点仍成立。下表和 J04 的全仓数量仍保留为 21:00 快照，未重算为后续修改后的精确总数；这些源码复核也不表示重新执行了诊断或回归。

| 范围 | 文件数 | 代码行超过 120 列候选 | 同行多语句候选 | 同行压缩控制结构候选 |
| --- | ---: | ---: | ---: | ---: |
| 生产 Java | 1312 | 3435 | 1262 | 1592 |
| Agent | 605 | 2984 | 1168 | 1472 |
| Agent／sticker | 42 | 466 | 280 | 234 |
| Core | 304 | 157 | 50 | 66 |
| App | 139 | 89 | 1 | 6 |
| Client | 180 | 150 | 37 | 42 |
| Plugin | 84 | 55 | 6 | 6 |
| 测试 Java | 200 | 3233 | 2299 | 140 |

图库只占 42 个文件，却包含 Agent 中近四分之一的同行多语句候选，应该优先整理。不能因此认定整个图库不可用，也不能用“其他模块也这样写”视为符合规范。

扫描材料位于 `temp/verification/4.8.6-l/mvp-review/java-style/`：`scan-style.mjs`、`style-summary.json`、逐文件 `style-files.json` 和逐行 `style-candidates.json`。这些是本轮工作区快照，不是对未来提交版本的静态分析认证。

## V486-J01：事务重试与进程预算混用

位置：[StickerReplyService.java:232](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/StickerReplyService.java#L232)。

`list()` 先把本请求与类别的 `CompletableFuture` 登记到进程缓存，再调用 `catalog.change`。其事务回调不只修改图库状态，还在 `synchronized (queries)` 中修改 `Query.bytes`。后者是进程内可变对象，不属于数据库事务。

[TeaNekoAgentEasyData.transaction](../../../src/main/java/org/zexnocs/teanekoagent/database/easydata/TeaNekoAgentEasyData.java#L89) 将回调放入原数据库任务；[DatabaseRetryHandlingTaskStage](../../../src/main/java/org/zexnocs/teanekocore/database/base/stage/DatabaseRetryHandlingTaskStage.java#L51) 将乐观锁及约束冲突转为重试。第一次回调已经执行 `bytes += pageBytes`，提交失败回滚后，下一次回调再次读取已增加的 `bytes`。若预算只足够容纳一页，重试会返回没有条目的 `INPUT_BUDGET`；该不完整页随后进入本请求的缓存，重复查询不会恢复为完整页。

这是单实例下也存在的失败边界，无需多副本才可能发生。具体发生频率取决于事务冲突，不能将离线复现当作实际数据库冲突率。

建议保持数据库回调只产生已提交的候选页，在 `catalog.change` 的实际完成阶段再进入进程锁做预算判定与一次结算、签名及 future 完成。不同类别的并发结算仍必须原子仲裁；相同类别继续共享一次查询和结果。不要仅在每次异常后盲目减回预算，这会引入其他类别并发结算及取消竞态。

验收应包括：相同有限预算下无冲突成功；一次提交冲突后成功且只计一页；两个不同类别并发时总预算不超限；同类别重放不重复计费；数据库最终失败、控制取消和缓存过期不会留下未完成 future。

隔离诊断由 `create-query-retry-diagnostic.mjs` 复用正式 `MediaReplyIntegrationTest.Harness` 生成，测试名为 `StickerQueryRetryReviewTest.queryRetryMustNotChargeInputBudgetTwice`。诊断先执行无冲突成功对照，再通过现有 `beforeCommit` 夹具只注入一次乐观锁冲突，不改变生产合同、不访问真实数据库。

本轮已执行该诊断：有限预算 7302 字节、单页估算 862 字节时，无冲突页 `complete=true`；一次提交冲突重试后变为 `complete=false / INPUT_BUDGET`。冲突次数等于 1 的断言通过，最终“重试后应返回完整页”的断言失败，证实上述根因。JUnit 记录为 1 项、1 个预期暴露缺陷的断言失败、0 个执行错误；记录位于 `temp/verification/4.8.6-l/mvp-review/java-style/diagnostic-results/`。这不是正式回归通过记录，修复后应将同一行为要求转为通过。

## V486-J02：应优先展开的具体位置

以下为人工核查的代表位置，覆盖锁、事务、授权、资源、协议和后台任务。它们是实施范围清单，不能只对长行自动换行后全部勾选完成。

| 文件／方法 | 具体问题 | 改写要求 |
| --- | --- | --- |
| [StickerReplyService.list:213](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/StickerReplyService.java#L213) | 紧凑方法混合输入校验、缓存过期、去重、两次锁、事务、页预算、签名和 future 完成；`a/s/original` 难以识别职责 | 先等价展开，再拆缓存领取、事务取页、提交后结算；J01 独立修复 |
| [StickerReplyService.sticker:245](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/StickerReplyService.java#L245) | 多层循环、回执验证、冲突判断与条目授权压缩在一行 | 分开“验证本轮完整回执”和“当前条目复核”；保留工具失败／截断过滤及当前版本校验 |
| [StickerReplyService.sendSticker:283](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/StickerReplyService.java#L283) | 容量取得、同步抛错和异步结束两条释放路径不易辨认 | 分行标出资源拥有关系与释放点，不把异步资源放进提前结束的 try-with-resources |
| [StickerReplyService.acquireTransfer:294](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/StickerReplyService.java#L294) | 循环内两次取消检查、关闭检查和许可归还嵌套压缩 | 每个分支完整成块；保留取得许可之后的二次检查与中断标志恢复 |
| [StickerLibrary.offer:71](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/StickerLibrary.java#L71) | 两层异步回调内部缩进不反映层级；一次表达式混合多个容量上限；连续修改配额、作业和来源回执 | 提取可命名的容量判定，事务写入分行；事件等待仍在事务外 |
| [StickerLibrary.claim:102](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/StickerLibrary.java#L102) | 租约过期收束、配置停用、清理优先、选择新任务和领取混写 | 明确“收束旧作业→优先清理→领取”顺序，不改变后台单租约与清理优先语义 |
| [StickerLibrary.defer:131](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/StickerLibrary.java#L131)、[cleaned:264](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/StickerLibrary.java#L264) | 一行事务里改变作业状态、预约和占用；回调结束位置难以追踪 | 显式写出事务边界与每一步状态变化；提交后通知保持在事务外 |
| [StickerManagementService.change:124](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/StickerManagementService.java#L124) | 预读取、物理文件复核、第二次事务和管理 action switch 交错；单 case 内多个修改 | 将准备、外部 I/O、版本化提交、通知分段；所有 case 展开，保留二次授权与版本仲裁 |
| [StickerManagementService.preview:184](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/StickerManagementService.java#L184)、[releasePreview:209](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/StickerManagementService.java#L209) | 嵌套 try/catch/lambda 混合许可、文件读者租约和失败合并 | 独立展示两种资源所有权；保留原失败为主原因及清理失败的 suppressed 关系 |
| [LocalStickerAssets.identity:58](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/LocalStickerAssets.java#L58)、[publish:93](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/LocalStickerAssets.java#L93) | 多层目录资源、身份检查及原子移动被压成很长的语句 | 展开文件 Stream 的 try-with-resources、校验和移动分支；不要放宽符号链接或目录接管限制 |
| [LocalStickerAssets.deleteFile:479](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/LocalStickerAssets.java#L479) | 文件存在、类型检查、删除、异常映射都在一个方法行里 | 使用常规方法块，同时修复 J03 的诊断链丢失 |
| [StickerCollectionService.run:98](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/StickerCollectionService.java#L98)、[cleanup:133](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/StickerCollectionService.java#L133) | 后台领取、下载、分析、发布与清理紧密挤在一起；同步等待隐藏在短 helper 中 | 分阶段命名并解释 `await` 只运行在借用的后台执行域；不要仅因返回 Stage 就声称全链无阻塞 |
| [ReplyCoordinator:61](../../../src/main/java/org/zexnocs/teanekoagent/runtime/delivery/ReplyCoordinator.java#L61) 的排队及完成回调 | 观察完成与物理完成、队尾移除、资源关闭紧贴，容易误把取消当作实际完成 | 显式保留两个 future 的职责，展开所有完成分支及锁内状态变化 |
| [AgentConversationSummaryService.offer:150](../../../src/main/java/org/zexnocs/teanekoplugin/agent/cognition/memory/summary/AgentConversationSummaryService.java#L150)、[begin:161](../../../src/main/java/org/zexnocs/teanekoplugin/agent/cognition/memory/summary/AgentConversationSummaryService.java#L161) | 单行双重 try；候选裁剪、槽位、执行控制和后台完成逻辑同处 | 区分执行器拒绝与运行失败；每种失败说明是否可恢复、何时释放槽位 |
| [AgentConversationService.submit:555](../../../src/main/java/org/zexnocs/teanekoagent/runtime/conversation/AgentConversationService.java#L555)、[startPrepared:1096](../../../src/main/java/org/zexnocs/teanekoagent/runtime/conversation/AgentConversationService.java#L1096) | 长协调器内大量单行早退与成组字段赋值，追踪队列、memory/personality 准备和取消需跨很大范围 | 先展开状态转换与锁边界；后续按 J06 提取职责，不能直接搬锁内逻辑到外部 |
| [AgentOperations.execute:178](../../../src/main/java/org/zexnocs/teanekoclient/agent/service/AgentOperations.java#L178) | 许可、控制创建、登记和失败释放连续混排，泛型异步链过长 | 以资源取得、业务执行、终态释放划段，保留父控制借用语义 |
| [StickerReplyTools.list:48](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/StickerReplyTools.java#L48) | 工具适配器也把取上下文、空值断言和业务调用挤在一行，并直接使用全限定 `Objects` | 采用普通参数换行与 import；工具类继续只声明一个工具，不把领域逻辑搬进注解方法 |

此外，`EasyDataLayeredMemoryStore`、`EasyDataLongTermMemoryStore`、`EasyDataPersonalityStore`、`ProactiveService`、`AgentPluginRuntime` 均进入候选前列。按逐行清单结合相关业务改动整理，不因本轮扫描而同时格式化所有 Store。

### `list()` 的行为等价格式样例

下面是**格式阶段**的完整方法样例：不增加线程、不改拒绝条件、不改变缓存和同步／异步异常行为。它刻意保留原预算结算位置，便于将“格式调整”和 **J01 的语义修复**分成两个可审查步骤；不能将这一样例直接视为 J01 的修复完成。

```java
/**
 * 同请求同类别复用原回执；预算不足时返回不完整页且不授权其中条目。
 * <br>2026/11/06：展开缓存、事务和异步完成边界，保留原行为供后续修复对照。
 *
 * @param request 原请求
 * @param input 实际布局
 * @param category 本轮提供的类别 ID
 * @param control 借用工具控制
 * @return 原完整页或明确拒绝页
 * @throws IllegalArgumentException 当类别不属于本轮可用目录时
 * @throws IllegalStateException 当进程查询目录已满时
 */
public CompletionStage<MediaReplies.Page> list(
        AgentRunRequest request,
        AgentPromptInput input,
        String category,
        ExecutionControl control) {
    control.checkActive();
    var resources = resources(request, input);
    var access = access(request).orElseThrow();
    if (!resources.images()
            || resources.categories().stream().noneMatch(value -> value.id().equals(category))) {
        throw new IllegalArgumentException("类别不是本轮提供的可用类别。");
    }

    final CompletableFuture<MediaReplies.Page> result;
    final Query query;
    synchronized (queries) {
        queries.values().removeIf(value -> value.expires.isBefore(Instant.now()));
        var existing = queries.get(request.requestId());
        if (existing == null) {
            if (queries.size() >= 1024) {
                throw new IllegalStateException("本轮媒体查询目录已满。");
            }
            existing = new Query(resources.binding().expiresAt());
            queries.put(request.requestId(), existing);
        }
        query = existing;

        var previous = query.pages.get(category);
        if (previous != null) {
            return previous.minimalCompletionStage();
        }
        if (query.pages.size() >= access.media().maxCategories()) {
            return CompletableFuture.failedStage(
                    new IllegalArgumentException("本轮类别展开次数已达到上限。"));
        }
        result = new CompletableFuture<>();
        query.pages.put(category, result);
    }

    // 2026/11/06：保留此阶段以接住目录端口的同步异常，避免留下永不完成的缓存 future。
    CompletableFuture.completedFuture(null)
            .thenCompose(ignored -> access.storage().catalog().change(access.settings(), state -> {
                control.checkActive();
                check(resources.binding(), request, access);
                var usage = touch(state, request, access.data());
                if (!state.getStorageIdentity().equals(resources.binding().storage())) {
                    throw new IllegalStateException("素材卷身份已经变化。");
                }

                var items = eligible(state, access, usage, category).stream()
                        .map(entry -> new MediaReplies.Listing(
                                entry.getId(), entry.getRevision(), description(entry)))
                        .sorted(Comparator.comparing(MediaReplies.Listing::id))
                        .toList();
                var page = new MediaReplies.Page(
                        request.requestId(), resources.proof(), category,
                        state.getRevision(), items, true, "", "");
                long bytes = mapper.writeValueAsBytes(page).length + 512;
                String reason = "";
                if (items.size() > access.settings().maxPerCategory()
                        || mapper.writeValueAsString(page).length() + 128
                        > access.media().maxCategoryCharacters()) {
                    reason = "CATEGORY_BUDGET";
                }

                // 仅保留原行为作格式对照；J01 必须另将结算移到真实提交后。
                synchronized (queries) {
                    if (request.inputMaxBytes() > 0
                            && codec.signature(input.messages()).bytes()
                            + query.bytes + bytes + 1024 > request.inputMaxBytes()) {
                        reason = "INPUT_BUDGET";
                    }
                    if (reason.isEmpty()) {
                        query.bytes += bytes;
                    }
                }
                if (!reason.isEmpty()) {
                    page = new MediaReplies.Page(
                            request.requestId(), resources.proof(), category,
                            state.getRevision(), List.of(), false, reason, "");
                }
                return new MediaReplies.Page(
                        page.requestId(), page.resourceProof(), page.category(), page.catalogRevision(),
                        page.items(), page.complete(), page.reason(), sign(page));
            }))
            .whenComplete((page, failure) -> {
                if (failure == null) {
                    result.complete(page);
                } else {
                    result.completeExceptionally(failure);
                }
            });
    return result.minimalCompletionStage();
}
```

`completedFuture(null).thenCompose(...)` 不会自动切换线程。此处它还能把 `catalog.change(...)` 的同步抛错转为异步失败，最后完成已经登记的 `result`。机械删掉这个阶段、直接调用目录端口，可能在同步抛错时留下永不完成的缓存 future。格式工具无法理解这个合同；需要显式异常边界后才能简化。

第二阶段的职责目标是三个清楚的区域：查询缓存领取与复用、事务内当前资源校验与候选页生成、事务完成后的有界预算结算与签名。优先使用私有方法和现有 DTO，只有出现实际复用或可替换需求才引入独立类；不为每个 `if` 建立策略接口。

## V486-J03：保留安全根因

[LocalStickerAssets.java:73](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/LocalStickerAssets.java#L73)、85、100、133、448、459、479 等位置，捕获 I/O 异常后只抛固定 `IllegalStateException`。例如原子移动不受支持、目录权限拒绝和文件读取错误会失去原异常类型、代码位置及原因链，最后的 [StickerCollectionService.warn](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/StickerCollectionService.java#L146) 无法还原已丢弃的信息。

固定中文消息和隐藏文件路径是合理边界，但不必牺牲安全诊断。可复用 [DiagnosticException](../../../src/main/java/org/zexnocs/teanekocore/logger/DiagnosticException.java#L14) 或等价的领域安全诊断字段，仅保留类型、位置和安全分类。原始绝对路径、URL、请求头和异常消息仍不能出现在公开结果。

`exists()` 返回 `false` 表示文件不可用，不能只看 `catch` 就认定它错误；应结合调用合同判断是否需要区分“确实不存在”和“暂时不可读”。同样，通知失败不能推翻已提交事务的现有捕获，以及因其他进程已创建身份文件而忽略 `FileAlreadyExistsException`，都有明确用途，不应统一删除。

验收：注入不同 I/O 失败时，公开结果保持固定安全说明，诊断能区分原始异常类型与位置，且不输出原路径；业务终态、资源释放与重试策略保持原合同。本轮没有执行真实磁盘权限故障实验，不宣称已观察到生产事故。

## V486-J04：JavaDoc、类型及空值

21:00 扫描快照中，38 个生产／测试 Java 文件的 64 行包含真实 `U+FFFD` 替换字符。例：[LLMInteractionExecution.java:38](../../../src/main/java/org/zexnocs/teanekoagent/llm/framework/interaction/LLMInteractionExecution.java#L38)、[AgentConversationSummaryService.java:55](../../../src/main/java/org/zexnocs/teanekoplugin/agent/cognition/memory/summary/AgentConversationSummaryService.java#L55)、[LLMStickerAnalyzer.java:48](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/LLMStickerAnalyzer.java#L48)。收尾重读确认这三个示例仍在；这是文件内容已经损坏，不是本次终端显示字体问题。应根据 Git 中未损坏文本或相应提交事实恢复；无法确认的历史文字重新写清含义，但不要伪造原日期或重置 `@since`。

同一快照另有 584 个文件的 1313 处修改记录之间空白行候选，与 AGENTS 要求“逐行追加、条目之间不插入空行”不一致。[StickerReplyService 类注释](../../../src/main/java/org/zexnocs/teanekoplugin/agent/expression/sticker/StickerReplyService.java#L44)就是可直接检查的例子。按本次修改范围整理，保留真实历史条目；未修改类型不机械更新 `@version`。

[LLMProtocolSnapshots.metadata:173](../../../src/main/java/org/zexnocs/teanekoagent/llm/framework/message/LLMProtocolSnapshots.java#L173) 将已知 Map 输入交给返回 `Object` 的递归入口，再转回 `Map<String, Object>` 并压制 unchecked。该文件 21:49 的更新没有移除这段写法，收尾已重新读取确认。内部实现已经验证 Map 键，因此本轮没有证据表明发生类型污染；更清楚的写法是将 Map 分支提为返回 `Map<String, Object>` 的辅助方法，递归 JSON 入口复用它，保留深度、循环、null、凭据及不可变合同。

该扫描快照未发现声明的 `serialVersionUID` 缺少 `@Serial` 的候选，不将该项列为待修。空值与 Lombok 必须逐合同核查，本轮不以字段默认 `null` 或存在 `@Nullable` 本身判断错误。`AgentTurn` 对借用控制的资源提示抑制有明确生命周期背景，不能靠提前关闭控制来消除 IDE 告警。

## V486-J05：可执行的代码格式基线

“与 IntelliJ 对齐”应落实为项目共享配置，不能依赖每台机器的默认值。IntelliJ 的默认方案不是 AGENTS 的替代品，也不意味着默认行宽就是 120。

| 项目规则 | 实施方式 | 通过条件 |
| --- | --- | --- |
| 四空格、空格代替 Tab、中文 UTF-8 | 建立版本化 `.editorconfig`，与 IDE 项目方案一致 | 修改文件无混合缩进、乱码和尾部空格 |
| 120 列软上限 | Java code style 设置项目行宽；长签名、参数及调用链按语义换行 | 审查者无需横向滚动才能理解控制流；合理长常量或必要类型不机械拒绝 |
| 一行一条语句、复杂控制结构完整成块 | 关闭保留简单 block／method 为单行的项目格式选项；人工拆分成组状态变化 | 锁、分支、异常、循环及复杂 lambda 的边界独立可见 |
| 注解、声明与命名 | `@Override` 独立一行；每个局部声明单列；复杂回调用能识别职责的参数名 | 不出现 `@Override public ...` 与一行多声明；`state`、`access`、`query` 的角色可直接识别，短局部参数不一律扩写 |
| 复杂调用链与异步回调 | 一层操作一层缩进；多步 lambda 使用块结构 | 能直接识别执行线程、状态变更、错误传播与资源终点 |
| JavaDoc 与导入 | IDE Optimize Imports；按 AGENTS 填中文职责、参数、结果和明确抛出条件 | 无未使用导入；创建时间及首次版本不被格式动作重置 |
| 质量检查 | 在现有构建／CI 中加入有范围的格式及检查，Qodana 按项目规则启用实际失败条件 | 新增严重问题或新增密集控制流能够阻止合入，已有基线逐批减少 |

当前有 [qodana.yaml](../../../qodana.yaml)（`qodana.starter`、JDK 25），但 `failureConditions` 为注释；未找到版本化 `.editorconfig`、项目 `codeStyles`，也未在 Gradle 中发现 Checkstyle／Spotless 配置。不能据此推断开发机从未运行 IDE 检查，只能说明仓库没有提供可复现的格式门禁证据。

可以先引入以下标准 EditorConfig 项，不在未确认仓库换行约定前强制整仓转换 LF／CRLF：

```editorconfig
root = true

[*.java]
charset = utf-8
indent_style = space
indent_size = 4
insert_final_newline = true
trim_trailing_whitespace = true
```

JavaDoc 语义、事务回调外部副作用、异步资源释放不属于格式化工具可证明的内容。不要新增一批只断言缩进或行长的 JUnit 测试；格式规则由格式工具或静态检查负责，行为由有价值的回归测试负责。

## V486-J06：包结构与职责边界

当前采用单 Gradle 应用、五层 Java 包的模块化单体。21:00 的生产源码 import／全限定类型引用扫描快照未发现逆层候选；已有 `ProjectLayerBoundaryTest` 还覆盖反射类名和单工具提供者规则，后续变更是否通过按本轮主审查的实际验证记录判断。它适合当前小团队部署，不需要为了“现代扩展性”立即拆成独立服务。

但包边界仍靠约定与测试，不是编译期模块边界。新增业务应继续由上层实现下层接口、通过组件或工具声明注册，不能为了便利在 Core／App 写新的 Agent 条件分支。团队或发布责任真正分化后，再考虑把稳定的 Core／App 合同提取为 Gradle 子模块；这不是首发阻断项。

`AgentConversationService` 当前约 1670 行，承担邮箱容量、排队、超时、重放、记忆代次、人格准备、轮次启动、交付与停止。`EasyDataLongTermMemoryStore` 当前约 1190 行，结合长期条目、证据、学习作业、租约、维护及管理回执。文件长本身不是缺陷，但这么多状态共享锁与辅助方法，要求实现器每次修改都理解大量无关路径。

建议按已存在的边界渐进处理：会话侧先将出队前资料准备和终态投影提为协作者，邮箱状态与锁仍保持一个拥有者；长期存储侧可按事务内查询／写入及作业状态转换分组私有 helper，确认相同锁顺序和原子提交后再考虑拆类。不为减少行数把同一事务拆成多个独立调用，也不把普通 Store 迁为直接使用 Repository。

MVP 采用受控有界运行、现有扩展 SPI 和明确数据合同即可。无业务收益的空接口、对一行 helper 建类、过早拆分多个部署服务，会增加维护负担，不能作为这份审查的完成指标。

## 实施批次与验收

1. 先修 J01 并保留诊断回归；整理其修改到的 `list()` 和直接协作方法，不把全仓格式变更混进功能补丁。
2. 整理图库的锁／租约／发送／清理代码，修 J03；复用图库采集、管理、目录查询及媒体发送相关回归，确认取消和失败清理路径。
3. 随会话、记忆和人格的实际业务改动整理对应热点文件。先纯格式差异，再职责提取；只有需要改变业务行为时才另立修复项与测试。
4. 补共享格式配置，逐批修中文 JavaDoc 与历史乱码；记录格式基线，只约束新增问题，不以一轮全仓重排作为完成。

每批至少满足：`git diff --check`；只改授权范围；格式阶段 token／控制流没有意外变化；实际语义重构的定向回归通过；修改类的 JavaDoc、空值、导入和资源检查完成。修改纯文档时不为形式完整重新打包；涉及发布产物的代码交付仍按 AGENTS 核对实际产物。

## 旧基线复核：E1、A1、A2

本节回查 [4.8.1-l 的三项旧待办](../4.8.1-l/project-extensibility-maintainability-async-review.md)，按 2026/11/06 当前源码重新判断。这里只区分已修内容与剩余合同，不沿用旧 P1，也不把源码可达风险写成生产事故。本次补查没有新增或运行测试。

### 已修内容

E1 中的**命令目录重扫发布**已修：[CommandScanner.reload:126](../../../src/main/java/org/zexnocs/teanekocore/command/CommandScanner.java#L126) 不再清空旧目录，`_scan()` 使用局部映射，最终在 [210 行](../../../src/main/java/org/zexnocs/teanekocore/command/CommandScanner.java#L210) 一次替换 `volatile` 的不可变快照。扫描中途抛错不会替换已发布目录；[现有 CommandCatalogTest:84](../../../src/test/java/org/zexnocs/teanekocore/command/CommandCatalogTest.java#L84) 已声明失败重扫后保留目录的回归要求。本文仅核查该测试源码，不将其写成本轮已重跑。

### 当前仍需实施

| 旧编号／本轮状态 | 当前证据与失效边界 | 单实例 MVP 判断与修改方向 |
| --- | --- | --- |
| E1／部分修复；剩余按 P2 | [TeaNekoClientScanner:59](../../../src/main/java/org/zexnocs/teanekoapp/client/TeaNekoClientScanner.java#L59) 仍按同 ID 覆盖；[ContentPartScanner:54](../../../src/main/java/org/zexnocs/teanekoapp/message/ContentPartScanner.java#L54) 先覆盖再警告；两者仍继承 [AbstractScanner.reload:35](../../../src/main/java/org/zexnocs/teanekocore/reload/AbstractScanner.java#L35) 的清空后重扫。命令目录虽已原子发布，[CommandScanner:174](../../../src/main/java/org/zexnocs/teanekocore/command/CommandScanner.java#L174)、[199](../../../src/main/java/org/zexnocs/teanekocore/command/CommandScanner.java#L199) 对重复子命令／主命令仍只报告后跳过后者 | 固定受控扩展、避免运行中重扫可缩小触发面，但不满足扩展标识唯一和失败保留旧目录的合同。先逐个目录拒绝冲突并报告双方来源；客户端及内容目录改为完整校验后一次发布。不要重复重写已修的命令快照，也不要把冲突处理误写为已完成 |
| A1／仍存在；P2，纳入生产负荷门槛 | [ThreadPoolConfig:36](../../../src/main/java/org/zexnocs/teanekocore/_app_config/ThreadPoolConfig.java#L36) 的调度器未提供注释声称的有界队列；[76 行](../../../src/main/java/org/zexnocs/teanekocore/_app_config/ThreadPoolConfig.java#L76) 仍每任务创建虚拟线程。[TaskService.subscribeWithFuture:86](../../../src/main/java/org/zexnocs/teanekocore/actuator/task/TaskService.java#L86) 没有总在途额度；[OnebotClient.executeAsync:305](../../../src/main/java/org/zexnocs/teanekoclient/onebot/core/OnebotClient.java#L305) 仅处理停机／提交失败，没有完整处理准入 | 慢数据库、慢平台、重试积压在单实例也可触发。不能从小用户数推导在途工作有界，亦不能宣称已测得内存耗尽。先校正线程池注释；在接收与所属外部资源边界设置可观测的有界准入及繁忙结果，保留现有会话额度。额度按实际工作完成释放，不在入站转换结束后提前归还；不把虚拟线程本身当作错误 |
| A2／仍存在；P2，纳入超时与副作用合同 | [TaskExecuteService:134](../../../src/main/java/org/zexnocs/teanekocore/actuator/task/TaskExecuteService.java#L134) 登记的是 `runAsync` 返回的完成信号；[TaskService:284](../../../src/main/java/org/zexnocs/teanekocore/actuator/task/TaskService.java#L284) 在执行超时分支仅 `cancel(true)`，随后结束回执、标记终态并移除缓存。运行中的 Callable 没有由此获得停止信号，返回时还会进入 [TaskExecuteService:219](../../../src/main/java/org/zexnocs/teanekocore/actuator/task/TaskExecuteService.java#L219) 的结果处理 | 一项慢写操作即足以触发，与用户规模无关。先把“调用者等待已结束”与“实际工作已停止”分开，改正已取消任务的过强表述。需要停止的旧任务接入协作控制，在业务阶段及提交前检查，底层资源由拥有者释放；不可撤回的工作追踪到收敛，不因未知结果自动重复写。不能假设新 HTTP 链的取消能力已覆盖旧 Callable |

当前普通业务与模型链已有局部容量、控制和取消机制，上述剩余问题不等于全项目没有异步设计。E1 的后续验收应覆盖冲突拒绝及新旧完整目录；A1 应覆盖慢依赖下限额、明确拒绝和实际完成后的容量回收；A2 应覆盖协作停止、不可取消工作收敛及迟到结果不能伪造成功。新增功能不得绕过这些合同；既有路径按真实首发启用范围逐步补齐。
