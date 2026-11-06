package org.zexnocs.teanekoagent.component.memory.adapter;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.memory.access.MemoryAddResult;
import org.zexnocs.teanekoagent.component.memory.fact.MemoryFactAppend;
import org.zexnocs.teanekoagent.component.memory.fact.MemoryFactGenerationException;
import org.zexnocs.teanekoagent.component.memory.fact.MemoryFactReceipt;
import org.zexnocs.teanekoagent.component.memory.fact.MemoryFactValue;
import org.zexnocs.teanekoagent.component.memory.history.HistoryMessage;
import org.zexnocs.teanekoagent.component.memory.layered.*;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.component.personality.IStyleObservationStore;
import org.zexnocs.teanekoagent.component.personality.StyleObservations;
import org.zexnocs.teanekoagent.database.easydata.EasyDataMaintenancePage;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataRow;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataTransactionDto;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 将已提交中期片段整理为 namespace 共享的长期记录，事实引用逐字核对真实用户消息。
 * 记录、主体/话题索引和整理水位同事务提交，遗忘墓碑阻止旧内容自动复活。
 * <br>2026/11/03：表达样本、渐进采用和整理水位共同提交，保留生成端口替换能力。
 * <br>2026/11/03：回收旧代次与失效索引，精简过期原话并保留当前遗忘保护。
 * <br>2026/11/03：明确保存接入同一长期领域，保留原文和完整附加信息。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/03：独立管理员与本人维护复用同一版本、索引、墓碑和回执事务。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：长期事务入口和提交前复核资格，非空单字查询执行有界字面匹配。
 * <br>2026/11/05：复用候选关系预检，逐项区分事务内来源失效并持久限制重复无效生成。
 * <br>2026/11/06：证据、片段及租约期限包含实际排队耗时，避免使用入队时的过期快照。
 * <br>2026/11/06：仅为旧作业缺失的管理版本补零，不放宽其他字段反序列化校验。
 * <br>2026/11/06：持久化格式失败报告安全的类型与字段，不泄露 JSON 或反序列化异常正文。
 * <br>2026/11/06：分页发现已存画像主体，沿用空间代次与原索引。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.8.4-l
 */
public final class EasyDataLongTermMemoryStore implements ILongTermMemoryStore, IMemoryMaintenance {
    /**
     * 2026/11/06：只从权威长期记录分页读取主体，排除已遗忘或失效记录。
     * @param reference 当前固定代次
     * @param after 上页记录键
     * @param limit 有界记录数
     * @return 实际主体及未扫描部分的游标
     */
    @Override
    public CompletionStage<org.zexnocs.teanekoagent.database.easydata.StoredPage<UUID>> subjects(
            MemoryNamespaceReference reference, String after, int limit) {
        if (after.length() > 64 || limit < 1 || limit > 50) {
            throw new IllegalArgumentException("画像目录分页无效。");
        }
        return data.transaction("读取长期画像主体", () -> {
            requireGeneration(reference);
            String space = space(reference);
            var rows = dto("entries:" + space).scan(after, null, limit + 1, EasyDataLock.NONE);
            var subjects = new LinkedHashSet<UUID>();
            for (var row : rows.stream().limit(limit).toList()) {
                var value = read(row, LongMemories.Entry.class);
                if (value.subjectId() != null && (value.status() == LongMemories.Status.ACTIVE
                        || value.status() == LongMemories.Status.CONFLICT) && liveExplicit(space, value.id())) {
                    subjects.add(value.subjectId());
                }
            }
            return new org.zexnocs.teanekoagent.database.easydata.StoredPage<>(List.copyOf(subjects),
                    rows.size() > limit ? rows.get(limit - 1).getKey() : "");
        });
    }
    private final TeaNekoAgentEasyData data;
    private final TeaNekoAgentEasyData layers;
    private final EasyDataMemoryNamespaceStore namespaces;
    private final ObjectMapper mapper;
    private final Supplier<LayeredMemorySettings> settings;
    private final @Nullable IStyleObservationStore styles;
    private final EasyDataMemoryLifecycle lifecycle;
    private final Clock clock;
    /**
     * 借用专用数据入口，构造不进行 I/O。
     * @param data 原数据入口
     * @param namespaces 原空间锁
     * @param mapper 原转换器
     * @param settings 原分层预算
     */
    public EasyDataLongTermMemoryStore(TeaNekoAgentEasyData data, EasyDataMemoryNamespaceStore namespaces, ObjectMapper mapper,
            Supplier<LayeredMemorySettings> settings) {
        // 2026/11/03：保留只处理事实的原独立构造。
        this(data, namespaces, mapper, settings, null);
    }
    /**
     * 2026/11/03：可选观察接收器必须参加调用者当前事务。
     * @param data 原数据入口
     * @param namespaces 原空间锁
     * @param mapper 原转换器
     * @param settings 原预算
     * @param styles 可选表达存储
     */
    public EasyDataLongTermMemoryStore(TeaNekoAgentEasyData data, EasyDataMemoryNamespaceStore namespaces, ObjectMapper mapper,
            Supplier<LayeredMemorySettings> settings, @Nullable IStyleObservationStore styles) {
        // 2026/11/03：原构造继续使用实际时钟。
        this(data, namespaces, mapper, settings, styles, Clock.systemUTC());
    }
    /**
     * 2026/11/03：明确条目的有效期读取同一时钟，支持独立宿主确定性验证。
     * @param data 原入口
     * @param namespaces 空间锁
     * @param mapper 原转换器
     * @param settings 原预算
     * @param styles 可选观察端口
     * @param clock 实际时钟
     */
    public EasyDataLongTermMemoryStore(TeaNekoAgentEasyData data, EasyDataMemoryNamespaceStore namespaces, ObjectMapper mapper,
            Supplier<LayeredMemorySettings> settings, @Nullable IStyleObservationStore styles, Clock clock) {
        this.data = data.inNamespace("agent.memory.long.v1"); this.layers = data.inNamespace("agent.memory.layers.v1");
        this.namespaces = namespaces; this.settings = settings; this.styles = styles;
        this.clock = clock;
        // 2026/11/03：作业与共享索引保持无正文的稳定归属。
        this.lifecycle = new EasyDataMemoryLifecycle(data, namespaces, mapper);
        this.mapper = mapper.rebuild().disable(SerializationFeature.INDENT_OUTPUT, SerializationFeature.WRAP_ROOT_VALUE).build();
    }
    /**
     * 2026/11/03：本人明确保存无需等待中期整理，完整元数据与回执同事务保存。
     * @param command 已验证原文的宿主命令
     * @param now 实际提交时刻
     * @return 原操作回执，人工保护或失效条目返回冲突
     * @throws IllegalArgumentException 当来源不是当前真实用户本人时
     */
    @Override public CompletionStage<MemoryAddResult> appendExplicit(MemoryFactAppend command, Instant now) {
        var actor = command.source().actor(); var value = command.value();
        if (actor.origin() != AgentActorContext.Origin.USER || !actor.userId().orElseThrow().equals(command.subjectId())) {
            throw new IllegalArgumentException("明确保存只能写入真实发起者本人事实。");
        }
        return data.transaction("明确保存本人长期记忆", () -> {
            requireGeneration(command.reference());
            lifecycle.registerInTransaction(new MemoryLayers.Stream(MemoryLayers.stream(command.reference()), command.reference()));
            String space = space(command.reference()); var receipts = dto("explicit:" + space);
            String fingerprint = StableDigest.sha256(mapper.writeValueAsString(command));
            var prior = receipts.find(command.operationId().toString(), EasyDataLock.WRITE);
            if (prior != null) {
                var receipt = read(prior, ExplicitReceipt.class);
                return receipt.fingerprint().equals(fingerprint) ? receipt.result() : new MemoryAddResult(MemoryFactReceipt.Status.CONFLICT, null);
            }
            var kind = LongMemories.Kind.valueOf(value.kind().name()); String content = contentKey(command.subjectId(), value.text());
            var alias = dto("content:" + space).find(content, EasyDataLock.READ);
            var id = alias == null ? UUID.nameUUIDFromBytes(("explicit:" + content).getBytes(StandardCharsets.UTF_8)) : UUID.fromString(alias.getValue());
            var entries = dto("entries:" + space); var row = entries.find(id.toString(), EasyDataLock.WRITE);
            var old = row == null ? null : read(row, LongMemories.Entry.class);
            var explicit = dto("explicit-values:" + space).find(id.toString(), EasyDataLock.READ);
            MemoryAddResult result;
            if (value.expiresAt() != null && !value.expiresAt().isAfter(now) || explicit != null && !read(explicit, MemoryFactValue.class).equals(value)
                    || old != null && (old.status() != LongMemories.Status.ACTIVE || !old.text().equals(value.text()) || !liveExplicit(space, id))) {
                result = new MemoryAddResult(MemoryFactReceipt.Status.CONFLICT, null);
            } else if (old != null) {
                if (explicit == null) { save(dto("explicit-values:" + space), null, id.toString(), value); }
                result = new MemoryAddResult(MemoryFactReceipt.Status.DUPLICATE, id);
            } else {
                String sourceKey = "source:" + StableDigest.sha256(actor.scope().clientId(), actor.scope().scopeId(), actor.userId().orElseThrow().toString(), actor.sourceId());
                write(space, null, new LongMemories.Entry(id, kind, command.subjectId(), "explicit." + kind.name().toLowerCase(Locale.ROOT), value.text(),
                        LongMemories.Status.ACTIVE, 0, false, List.of(), List.of(sourceKey), List.of(), now, now, ""));
                save(dto("explicit-values:" + space), null, id.toString(), value);
                result = new MemoryAddResult(MemoryFactReceipt.Status.STORED, id);
            }
            save(receipts, null, command.operationId().toString(), new ExplicitReceipt(fingerprint, result)); return result;
        });
    }
    /**
     * 领取至多四个中期片段；只有一个片段时，空闲十分钟后也可整理。
     * @param stream 原分区
     * @param now 当前时间
     * @return 已固定输入和租约
     */
    @Override public CompletionStage<Optional<LongMemories.Batch>> claim(MemoryLayers.Stream stream, Instant now) {
        return data.transaction("领取长期记忆整理", () -> {
            if (!generation(stream.reference(), EasyDataLock.READ)) { return Optional.empty(); }
            var row = dto("jobs").find(stream.id(), EasyDataLock.WRITE); var job = row == null ? Job.empty() : readJob(row);
            // 2026/11/05：暂停作业仍检查固定前缀是否变化；不变则不再调用模型。
            if (job.until().isAfter(now) || !paused(job) && job.retryAt().isAfter(now)) { return Optional.empty(); }
            var selected = layer("medium:" + stream.id()).scan(sequence(job.through()), null, 4, EasyDataLock.READ).stream()
                    .map(item -> read(item, MemoryLayers.Segment.class)).toList();
            // 2026/11/03：到期前缀只推进跳过水位，不将失效片段反复送入模型。
            long skipped = job.through();
            for (var segment : selected) {
                if (segment.createdAt().plus(settings.get().retention()).isAfter(now)) { break; }
                skipped = segment.through();
            }
            if (skipped != job.through()) {
                job = new Job(skipped, null, Instant.EPOCH, Instant.EPOCH, 0, 0, "", null, job.revision() + 1);
                save(dto("jobs"), row, stream.id(), job);
                row = dto("jobs").find(stream.id(), EasyDataLock.WRITE);
            }
            long covered = skipped;
            var segments = selected.stream().filter(item -> item.through() > covered).toList();
            if (segments.isEmpty() || segments.size() == 1 && segments.getFirst().createdAt().plusSeconds(600).isAfter(now)) { return Optional.empty(); }
            if (segments.stream().anyMatch(item -> !item.stream().equals(stream))) { throw new IllegalStateException("长期整理中期来源不一致。"); }
            var evidence = new ArrayList<LongMemories.Evidence>(); int budget = settings.get().maxInputBytes() / 2;
            for (var segment : segments) {
                if (!segment.createdAt().plus(settings.get().retention()).isAfter(now)) { continue; }
                var messages = layer("short:" + stream.id()).scan(sequence(segment.from() - 1), sequence(segment.through()), 64, EasyDataLock.READ);
                for (var messageRow : messages) {
                    var item = read(messageRow, MemoryLayers.Message.class);
                    if (item.message().role() != HistoryMessage.Role.USER || !item.expiresAt().isAfter(now) || evidence.size() == 64) { continue; }
                    evidence.add(new LongMemories.Evidence(stream.id() + ":" + item.sequence(), item.sequence(), item.message()));
                    if (mapper.writeValueAsBytes(evidence).length > budget) { evidence.removeLast(); }
                }
            }
            var fingerprint = LongMemories.fingerprint(mapper.writeValueAsString(segments), mapper.writeValueAsString(evidence));
            var trouble = job.trouble();
            boolean sameInput = trouble != null && trouble.fingerprint().equals(fingerprint);
            if (paused(job) && sameInput) {
                return Optional.empty();
            }
            var id = UUID.randomUUID(); var batch = new LongMemories.Batch(stream, id, segments, evidence, fingerprint);
            save(dto("jobs"), row, stream.id(), new Job(job.through(), id, now.plus(settings.get().timeout()).plusSeconds(30),
                    Instant.EPOCH, sameInput ? job.failures() : 0, segments.getLast().through(), fingerprint,
                    sameInput ? trouble : null, job.revision() + 1));
            return Optional.of(batch);
        });
    }
    /**
     * 在空间锁内核对来源与原话，再提交记录、索引和作业水位。
     * @param batch 原输入
     * @param candidates 有界候选
     * @param now 当前时间
     * @return 原租约有效且提交时为真
     */
    @Override public CompletionStage<Boolean> commit(LongMemories.Batch batch, List<LongMemories.Candidate> candidates, Instant now) {
        // 2026/11/03：旧事实提交沿用同一租约及证据仲裁。
        return commit(batch, new StyleObservations.Analysis(candidates, List.of()), null, now, () -> {});
    }
    /**
     * 2026/11/03：两类证据全部校验后再写记录与水位，任意观察失败整体回滚。
     * @param batch 原租约
     * @param analysis 原生成结果
     * @param target 宿主固定目标
     * @param now 当前时间
     * @param guard 当前学习守卫
     * @return 实际事务提交结果
     */
    @Override public CompletionStage<Boolean> commit(LongMemories.Batch batch, StyleObservations.Analysis analysis,
            StyleObservations.@Nullable Target target, Instant now, Runnable guard) {
        var frozen = analysis.memories();
        if (frozen.size() > 8) { throw new IllegalArgumentException("长期候选数量超过预算。"); }
        // 2026/11/06：保留调用者的业务时钟基准，同时计入进入事务前的等待时间。
        var queuedAt = clock.instant();
        return data.transaction("提交长期记忆和画像证据", () -> {
            // 2026/11/05：数据库回调可能排队，纯事实及空候选也须在事务内复核生命周期。
            guard.run();
            if (!generation(batch.stream().reference(), EasyDataLock.WRITE)) { return false; }
            // 2026/11/03：支持清理原消息目录后继续识别旧代次索引与作业。
            lifecycle.registerInTransaction(batch.stream());
            var row = dto("jobs").find(batch.stream().id(), EasyDataLock.WRITE);
            if (row == null) { return false; } var job = readJob(row);
            var elapsed = Duration.between(queuedAt, clock.instant());
            var committedAt = elapsed.isNegative() ? now : now.plus(elapsed);
            if (!owns(job, batch) || !job.until().isAfter(committedAt)
                    || !LongMemories.fingerprint(mapper.writeValueAsString(batch.segments()), mapper.writeValueAsString(batch.evidence())).equals(batch.fingerprint())) { return false; }
            for (var segment : batch.segments()) {
                var actual = layer("medium:" + batch.stream().id()).find(sequence(segment.through()), EasyDataLock.READ);
                if (actual == null || !read(actual, MemoryLayers.Segment.class).equals(segment)
                        || !segment.createdAt().plus(settings.get().retention()).isAfter(committedAt)) { return false; }
            }
            // 2026/11/05：固定关系与权威来源均需通过，混合批次任何一项失败都不写事实、风格或水位。
            validateSources(batch, analysis, target, committedAt);
            if (!analysis.styles().isEmpty() && styles == null) {
                throw new IllegalStateException("当前长期存储未装配表达观察接收器。");
            }
            for (var candidate : frozen) { merge(batch, candidate, committedAt); }
            if (!analysis.styles().isEmpty()) { Objects.requireNonNull(styles).acceptInTransaction(batch, Objects.requireNonNull(target), analysis.styles(), committedAt, guard); }
            save(dto("jobs"), row, batch.stream().id(), new Job(job.claimedThrough(), null, Instant.EPOCH,
                    Instant.EPOCH, 0, 0, "", null, job.revision() + 1));
            guard.run();
            return true;
        });
    }
    /**
     * 错误退避不推进整理水位。
     * @param batch 原任务
     * @param now 当前时间
     * @return 实际提交阶段
     */
    @Override public CompletionStage<Void> retry(LongMemories.Batch batch, Instant now) {
        // 2026/11/05：旧调用保持基础设施退避语义，不虚构无效模型结果。
        return retry(batch, now, null);
    }
    /**
     * 2026/11/05：固定输入连续无效三次后持久暂停；来源变化、撤权和基础设施故障不计该阈值。
     * @param batch 原任务
     * @param now 失败时刻
     * @param failure 原失败，只持久化安全枚举
     * @return 实际保存阶段
     */
    @Override public CompletionStage<Void> retry(LongMemories.Batch batch, Instant now, @Nullable Throwable failure) {
        var classified = LongMemoryJobs.Failure.from(failure);
        return data.transaction("保存长期整理退避", () -> {
            var row = dto("jobs").find(batch.stream().id(), EasyDataLock.WRITE);
            if (row != null) {
                var job = readJob(row);
                if (owns(job, batch)) {
                    int failures = Math.min(30, job.failures() + 1);
                    var prior = job.trouble();
                    int invalid = prior != null && prior.fingerprint().equals(batch.fingerprint()) ? prior.invalidAttempts() : 0;
                    if (classified.invalidCandidate()) {
                        invalid = Math.min(LongMemoryJobs.INVALID_LIMIT, invalid + 1);
                    }
                    var trouble = new Trouble(batch.fingerprint(), invalid, classified.code(), now);
                    save(dto("jobs"), row, batch.stream().id(), new Job(job.through(), null, Instant.EPOCH,
                            now.plusSeconds(Math.min(3600, 30L << Math.min(7, failures - 1))), failures, 0, "",
                            trouble, job.revision() + 1));
                }
            }
            return true;
        }).thenApply(ignored -> null);
    }

    /**
     * 2026/11/05：状态查询复核代次及管理授权，不触发后台任务。
     * @param reference 当前分区
     * @param now 观察时刻
     * @param guard 原管理授权
     * @return 有界安全状态
     */
    @Override public CompletionStage<LongMemoryJobs.Status> consolidationState(MemoryNamespaceReference reference,
            Instant now, Runnable guard) {
        return data.transaction("查看长期整理状态", () -> {
            guard.run();
            requireGeneration(reference);
            var row = dto("jobs").find(MemoryLayers.stream(reference), EasyDataLock.READ);
            var result = status(row == null ? Job.empty() : readJob(row), now);
            guard.run();
            return result;
        });
    }

    /**
     * 2026/11/05：按版本解除暂停并保存独立管理回执，所有旧租约立即失去提交权。
     * @param reference 原空间与分区
     * @param actor 可信管理主体
     * @param revision 原作业版本
     * @param operationId 幂等操作
     * @param now 当前时间
     * @param guard 事务前后授权
     * @return 原恢复回执
     * @throws IllegalStateException 当状态或版本已经变化时
     */
    @Override public CompletionStage<LongMemoryJobs.Status> resumeConsolidation(MemoryNamespaceReference reference,
            AgentActorContext actor, long revision, UUID operationId, Instant now, Runnable guard) {
        return data.transaction("恢复长期整理", () -> {
            guard.run();
            requireManager(reference, actor);
            requireGeneration(reference);
            var stream = MemoryLayers.stream(reference);
            var receipts = dto(replays(reference));
            var prior = receipts.find(operationId.toString(), EasyDataLock.WRITE);
            if (prior != null) {
                var receipt = read(prior, ReplayReceipt.class);
                if (!receipt.actorId().equals(actor.userId().orElseThrow()) || receipt.revision() != revision
                        || !receipt.stream().equals(stream)) {
                    throw new IllegalStateException("整理恢复操作标识与原请求不一致。");
                }
                guard.run();
                return receipt.status();
            }
            var row = dto("jobs").find(stream, EasyDataLock.WRITE);
            var job = row == null ? Job.empty() : readJob(row);
            if (job.revision() != revision || !paused(job)) {
                throw new IllegalStateException("整理作业未暂停或版本已变化，请重新读取。");
            }
            var resumed = new Job(job.through(), null, Instant.EPOCH, Instant.EPOCH, 0, 0, "", null, job.revision() + 1);
            save(dto("jobs"), row, stream, resumed);
            var result = status(resumed, now);
            save(receipts, null, operationId.toString(), new ReplayReceipt(actor.userId().orElseThrow(), stream, revision, now, result));
            guard.run();
            return result;
        });
    }

    /**
     * 2026/11/05：只读取原主体的恢复结果，轮询不再次重放。
     * @param reference 原空间
     * @param actor 管理主体
     * @param operationId 原操作
     * @param guard 当前授权
     * @return 原回执
     */
    @Override public CompletionStage<LongMemoryJobs.Status> consolidationOperation(MemoryNamespaceReference reference,
            AgentActorContext actor, UUID operationId, Runnable guard) {
        return data.transaction("查询长期整理恢复回执", () -> {
            guard.run();
            requireManager(reference, actor);
            requireGeneration(reference);
            var row = dto(replays(reference)).find(operationId.toString(), EasyDataLock.READ);
            if (row == null) {
                throw new NoSuchElementException("整理恢复回执尚不存在。");
            }
            var receipt = read(row, ReplayReceipt.class);
            if (!receipt.actorId().equals(actor.userId().orElseThrow())) {
                throw new IllegalStateException("整理恢复回执不属于当前管理主体。");
            }
            guard.run();
            return receipt.status();
        });
    }

    /**
     * 2026/11/05：管理身份与目标由宿主提供，模型来源不能调用恢复。
     * @param reference 原目标
     * @param actor 原身份
     * @throws IllegalArgumentException 当来源不是该聊天的独立管理主体时
     */
    private static void requireManager(MemoryNamespaceReference reference, AgentActorContext actor) {
        if (actor.origin() != AgentActorContext.Origin.MANAGEMENT || actor.userId().isEmpty()
                || !actor.scope().equals(reference.scope())) {
            throw new IllegalArgumentException("整理恢复需要当前聊天的独立管理身份。");
        }
    }

    /**
     * 2026/11/05：回执可在绑定版本变化后查询，但不同聊天与空间仍隔离。
     * @param reference 原目标
     * @return 无正文的回执分区
     */
    private static String replays(MemoryNamespaceReference reference) {
        return "job-replays:" + space(reference) + ":"
                + StableDigest.sha256(reference.scope().clientId(), reference.scope().scopeId());
    }

    /**
     * @param job 当前持久作业
     * @return 2026/11/05：达到同输入无效生成阈值时为真
     */
    private static boolean paused(Job job) {
        return job.trouble() != null && job.trouble().invalidAttempts() >= LongMemoryJobs.INVALID_LIMIT;
    }

    /**
     * 2026/11/05：公开状态不包含租约或原文，失效租约只显示待处理。
     * @param job 持久状态
     * @param now 观察时间
     * @return 管理投影
     */
    private static LongMemoryJobs.Status status(Job job, Instant now) {
        var trouble = job.trouble();
        var state = paused(job) ? LongMemoryJobs.State.PAUSED : job.until().isAfter(now) ? LongMemoryJobs.State.RUNNING
                : job.retryAt().isAfter(now) ? LongMemoryJobs.State.BACKOFF : LongMemoryJobs.State.READY;
        return new LongMemoryJobs.Status(job.revision(), job.through(), state, job.failures(),
                trouble == null ? 0 : trouble.invalidAttempts(), trouble == null ? "" : trouble.code(),
                trouble == null ? null : trouble.at(), job.retryAt(), trouble == null ? "" : trouble.fingerprint());
    }
    /**
     * 按主体或字面话题索引读取，索引命中必须回查权威状态。
     * @param reference 当前空间引用
     * @param subjectId 可选主体
     * @param query 本轮话题
     * @param limit 结果条数上限
     * @return 有界投影
     */
    @Override public CompletionStage<LongMemories.Result> query(MemoryNamespaceReference reference, @Nullable UUID subjectId, String query, int limit) {
        if (limit < 1 || limit > 20 || query.length() > 1024) { throw new IllegalArgumentException("长期检索预算无效。"); }
        return data.transaction("检索长期记忆", () -> {
            requireGeneration(reference); String space = space(reference); var ids = new LinkedHashSet<UUID>(); boolean limited = false;
            var terms = LongMemories.terms(query, 8);
            // 2026/11/05：没有索引词的非空查询仍作有界字面筛选，不能退化为列出全部最近项。
            var literal = query.strip().toLowerCase(Locale.ROOT);
            var indices = new ArrayList<IEasyDataTransactionDto>();
            if (subjectId != null) { indices.add(dto("subject:" + space + ":" + subjectId)); }
            else if (!terms.isEmpty()) { for (var term : terms) { indices.add(dto("term:" + space + ":" + StableDigest.sha256(term).substring(0, 32))); } }
            else { indices.add(dto("recent:" + space)); }
            for (var index : indices) {
                var rows = index.recent(Long.MAX_VALUE, 64); if (rows.size() == 64) { limited = true; }
                for (var row : rows) { ids.add(UUID.fromString(row.getValue())); }
            }
            var values = new ArrayList<LongMemories.Entry>(); int scanned = 0;
            for (var id : ids) {
                if (scanned++ == 256) { limited = true; break; }
                var row = dto("entries:" + space).find(id.toString(), EasyDataLock.NONE); if (row == null) { continue; }
                var value = read(row, LongMemories.Entry.class);
                if ((value.status() == LongMemories.Status.ACTIVE || value.status() == LongMemories.Status.CONFLICT)
                        && liveExplicit(space, value.id())
                        && (subjectId == null || subjectId.equals(value.subjectId()))
                        && (terms.isEmpty() ? literal.isEmpty()
                                || (value.attribute() + " " + value.text()).toLowerCase(Locale.ROOT).contains(literal)
                                : score(value, terms) > 0)) { values.add(value); }
            }
            values.sort(Comparator.<LongMemories.Entry>comparingInt(value -> score(value, terms)).reversed()
                    .thenComparing(LongMemories.Entry::observedAt, Comparator.reverseOrder()).thenComparing(value -> value.id().toString()));
            limited |= values.size() > limit;
            return new LongMemories.Result(values.stream().limit(limit).map(LongMemories.Entry::item).toList(), Math.min(scanned, 256), limited);
        });
    }
    /**
     * 只允许真实主体按版本纠正或遗忘，保留墓碑且不删除原聊天。
     * @param reference 当前引用
     * @param actorId 真实操作者
     * @param id 记录标识
     * @param revision 预期版本
     * @param text 新正文或遗忘
     * @param operationId 原宿主操作标识
     * @param sourceId 原用户消息标识
     * @param now 当前时间
     * @return 提交后的公开投影
     */
    @Override public CompletionStage<LongMemories.Item> change(MemoryNamespaceReference reference, UUID actorId, UUID id, long revision,
            @Nullable String text, UUID operationId, String sourceId, Instant now) {
        // 2026/11/03：原用户入口继续核对记忆主体，不获得管理旁路。
        return changeGuarded(reference, actorId, id, revision, text, operationId, sourceId, now, false, () -> {});
    }

    /**
     * @param reference 原空间
     * @param actor 独立管理来源
     * @param id 原记录
     * @param revision 原版本
     * @param text 新正文或遗忘
     * @param operation 原操作
     * @param now 固定时刻
     * @param guard 当前授权
     * @return 原领域持久化结果
     */
    @Override
    public CompletionStage<LongMemories.Item> manage(MemoryNamespaceReference reference,
            org.zexnocs.teanekoagent.runtime.AgentActorContext actor, UUID id, long revision, @Nullable String text,
            UUID operation, Instant now, Runnable guard) {
        if (actor.origin() != org.zexnocs.teanekoagent.runtime.AgentActorContext.Origin.MANAGEMENT
                || !actor.scope().equals(reference.scope())) {
            throw new SecurityException("长期管理来源或目标无效。");
        }
        return changeGuarded(reference, actor.userId().orElseThrow(), id, revision, text, operation,
                mapper.writeValueAsString(actor), now, true, guard);
    }

    /**
     * @param reference 原空间代次
     * @param actor 原管理来源
     * @param operation 原编号
     * @param guard 当前授权
     * @return 原结果，允许查询已经失效代次的已提交操作
     */
    @Override
    public CompletionStage<LongMemories.Item> managementOperation(MemoryNamespaceReference reference,
            org.zexnocs.teanekoagent.runtime.AgentActorContext actor, UUID operation, Runnable guard) {
        return data.transaction("查询长期记忆管理回执", () -> {
            guard.run();
            var row = dto("changes:" + space(reference)).find(operation.toString(), EasyDataLock.READ);
            if (row == null) {
                throw new NoSuchElementException("长期记忆管理回执不存在。");
            }
            var receipt = read(row, ChangeReceipt.class);
            if (actor.origin() != org.zexnocs.teanekoagent.runtime.AgentActorContext.Origin.MANAGEMENT
                    || !actor.scope().equals(reference.scope()) || !receipt.actorId().equals(actor.userId().orElseThrow())
                    || !receipt.sourceId().equals(mapper.writeValueAsString(actor))) {
                throw new SecurityException("长期记忆操作不属于当前管理主体或目标。");
            }
            guard.run();
            return receipt.item();
        });
    }

    /**
     * 2026/11/03：复用原维护事务，管理与本人维护使用不同来源并保留原指纹。
     * @param reference 原空间
     * @param actorId 实际主体
     * @param id 原记录
     * @param revision 原版本
     * @param text 正文或遗忘
     * @param operationId 原操作
     * @param sourceId 稳定来源
     * @param now 固定时刻
     * @param management 是否已在管理入口核对独立来源
     * @param guard 当前授权
     * @return 提交后的公开投影
     */
    private CompletionStage<LongMemories.Item> changeGuarded(MemoryNamespaceReference reference, UUID actorId,
            UUID id, long revision, @Nullable String text, UUID operationId, String sourceId, Instant now,
            boolean management, Runnable guard) {
        if (text != null && (text.isBlank() || text.length() > 512)) { throw new IllegalArgumentException("长期记忆纠正正文无效。"); }
        return data.transaction("纠正或遗忘本人长期记忆", () -> {
            guard.run();
            requireGeneration(reference); String space = space(reference); var entries = dto("entries:" + space);
            // 2026/11/03：回执与修改同事务提交；重复执行不再次修改版本，原调用不能换参。
            String fingerprint = StableDigest.sha256(mapper.writeValueAsString(Arrays.asList(reference, actorId, id, revision, text, sourceId)));
            var receipts = dto("changes:" + space); var receiptRow = receipts.find(operationId.toString(), EasyDataLock.WRITE);
            if (receiptRow != null) {
                var receipt = read(receiptRow, ChangeReceipt.class);
                if (!receipt.fingerprint().equals(fingerprint)) { throw new IllegalArgumentException("记忆维护操作标识不能用于不同参数。"); }
                guard.run();
                return receipt.item();
            }
            var row = entries.find(id.toString(), EasyDataLock.WRITE);
            if (row == null) { throw new IllegalArgumentException("长期记忆不存在。"); }
            var old = read(row, LongMemories.Entry.class);
            if (!management && !actorId.equals(old.subjectId()) || old.revision() != revision) {
                throw new IllegalArgumentException("长期记忆主体或版本不匹配。");
            }
            var changed = new LongMemories.Entry(old.id(), old.kind(), old.subjectId(), old.attribute(), text == null ? old.text() : text,
                    text == null ? LongMemories.Status.FORGOTTEN : LongMemories.Status.ACTIVE, old.revision() + 1, true,
                    old.quotes(), old.sourceKeys(), old.segments(), old.observedAt(), now, text == null ? old.timeHint() : "");
            write(space, row, changed);
            // 2026/11/03：按原片段登记派生抑制，改写正文或属性也不能复活同一旧证据。
            for (var segment : old.segments()) {
                var blocks = dto("blocked:" + space);
                for (var key : List.of(old.subjectId() + ":" + segment, "episode:" + segment)) {
                    if (blocks.find(key, EasyDataLock.NONE) == null) { save(blocks, null, key, operationId); }
                }
            }
            for (var key : old.sourceKeys()) {
                var blocks = dto("blocked:" + space);
                if (blocks.find(key, EasyDataLock.NONE) == null) { save(blocks, null, key, operationId); }
            }
            save(receipts, null, operationId.toString(), new ChangeReceipt(fingerprint, actorId, sourceId, now, changed.item()));
            guard.run();
            return changed.item();
        });
    }
    /**
     * 发送模型或重放前重新核对原条目，维护提交后不继续使用旧资料。
     * @param reference 固定空间
     * @param items 已采用条目
     * @return 原投影均未改变时为真
     */
    @Override public CompletionStage<Boolean> verify(MemoryNamespaceReference reference, List<LongMemories.Item> items) {
        var fixed = List.copyOf(items);
        if (fixed.size() > 40) { throw new IllegalArgumentException("长期记忆复核数量超过预算。"); }
        return data.transaction("复核已采用长期记忆", () -> {
            if (!generation(reference, EasyDataLock.READ)) { return false; }
            for (var item : fixed) {
                var row = dto("entries:" + space(reference)).find(item.id().toString(), EasyDataLock.READ);
                if (row == null) { return false; }
                var value = read(row, LongMemories.Entry.class);
                if ((value.status() != LongMemories.Status.ACTIVE && value.status() != LongMemories.Status.CONFLICT)
                        || !liveExplicit(space(reference), value.id())
                        || !value.item().equals(item)) { return false; }
            }
            return true;
        });
    }
    /**
     * 2026/11/03：逐页回收旧代次，当前条目只清理到期原话，不删除事实、回执和墓碑。
     * @param now 当前时刻
     * @return 实际删除或精简数量
     */
    @Override public CompletionStage<Integer> maintain(Instant now) {
        return EasyDataMaintenancePage.run(data, "维护长期记忆与来源证据", rows -> {
            int changed = 0;
            for (var scanned : rows) {
                var parts = scanned.getTarget().split(":");
                MemoryNamespaceReference reference;
                if (scanned.getTarget().equals("jobs")) { reference = lifecycle.streamReference(scanned.getKey()); }
                // 2026/11/05：恢复回执同样按空间摘要定位，仅回收已退出的代次。
                else if (parts.length >= 2 && parts[1].matches("[0-9a-f]{64}")) { reference = lifecycle.spaceReference(parts[1]); }
                else { continue; }
                if (reference == null) { continue; }
                boolean retired = lifecycle.retired(reference); var target = dto(scanned.getTarget());
                var row = target.find(scanned.getKey(), EasyDataLock.WRITE); if (row == null) { continue; }
                if (retired) { target.delete(row); changed++; continue; }
                if (parts[0].equals("entries")) {
                    var value = read(row, LongMemories.Entry.class); var quotes = value.quotes().stream().filter(quote -> validQuote(quote, now)).toList();
                    if (quotes.size() != value.quotes().size()) {
                        // 2026/11/03：公开内容与版本不变，保留来源摘要以继续抑制已遗忘旧证据。
                        save(target, row, row.getKey(), new LongMemories.Entry(value.id(), value.kind(), value.subjectId(), value.attribute(), value.text(),
                                value.status(), value.revision(), value.locked(), quotes, value.sourceKeys(), value.segments(), value.observedAt(), value.updatedAt(), value.timeHint()));
                        changed++;
                    }
                } else if (Set.of("recent", "subject", "term", "family").contains(parts[0])) {
                    var entryRow = dto("entries:" + parts[1]).find(row.getValue(), EasyDataLock.READ);
                    var value = entryRow == null ? null : read(entryRow, LongMemories.Entry.class);
                    if (value == null || value.status() != LongMemories.Status.ACTIVE && value.status() != LongMemories.Status.CONFLICT
                            || !row.getKey().equals(parts[0].equals("family") ? value.id().toString() : indexKey(value))) { target.delete(row); changed++; }
                }
            }
            return changed;
        });
    }
    /**
     * 2026/11/03：原话受原短期保留约束，来源缺失不等于长期结论自动失效。
     * @param quote 原证据引用
     * @param now 当前时刻
     * @return 对应真实原文尚在保留期时为真
     */
    private boolean validQuote(LongMemories.Quote quote, Instant now) {
        int separator = quote.evidenceId().lastIndexOf(':'); if (separator < 1) { return false; }
        var row = layer("short:" + quote.evidenceId().substring(0, separator)).find(sequence(Long.parseLong(quote.evidenceId().substring(separator + 1))), EasyDataLock.READ);
        return row != null && read(row, MemoryLayers.Message.class).expiresAt().isAfter(now);
    }
    /**
     * 2026/11/05：关系检查与生成阶段共用，权威原消息、期限和人格仍在提交事务内重新读取。
     * @param batch 固定输入
     * @param analysis 完整联合候选
     * @param target 原观察目标
     * @param now 提交时刻
     * @throws MemoryEvidenceException 当关系不成立或权威来源已失效时
     */
    private void validateSources(LongMemories.Batch batch, StyleObservations.Analysis analysis,
            StyleObservations.@Nullable Target target, Instant now) {
        for (var use : LongMemoryEvidence.validate(batch, analysis, target)) {
            var evidence = use.source();
            var row = layer("short:" + batch.stream().id()).find(sequence(evidence.sequence()), EasyDataLock.READ);
            if (row == null) {
                throw use.failure(batch, MemoryEvidenceException.Code.EVIDENCE_MISSING);
            }
            var original = read(row, MemoryLayers.Message.class);
            if (!original.message().equals(evidence.message())) {
                throw use.failure(batch, MemoryEvidenceException.Code.EVIDENCE_CHANGED);
            }
            if (!original.expiresAt().isAfter(now)) {
                throw use.failure(batch, MemoryEvidenceException.Code.EVIDENCE_EXPIRED);
            }
            if (use.domain() == MemoryEvidenceException.Domain.STYLES
                    && !original.personality().equals(Objects.requireNonNull(target).basis().identity())) {
                throw use.failure(batch, MemoryEvidenceException.Code.STYLE_TARGET_CHANGED);
            }
        }
    }
    /**
     * 原子合并同主体同内容；明确变更可替代旧值，锁定与遗忘不得被自动学习覆盖。
     * @param batch 原输入
     * @param candidate 已验证候选
     * @param now 提交时刻
     */
    private void merge(LongMemories.Batch batch, LongMemories.Candidate candidate, Instant now) {
        String space = space(batch.stream().reference());
        var blocks = dto("blocked:" + space);
        if (candidate.segments().stream().anyMatch(segment -> blocks.find(
                (candidate.kind() == LongMemories.Kind.EPISODE ? "episode" : Objects.requireNonNull(candidate.subjectId()).toString()) + ":" + segment,
                EasyDataLock.NONE) != null)) { return; }
        if (batch.evidence().stream().filter(value -> candidate.kind() == LongMemories.Kind.EPISODE
                ? batch.segments().stream().filter(segment -> candidate.segments().contains(segment.id()))
                    .anyMatch(segment -> value.sequence() >= segment.from() && value.sequence() <= segment.through())
                : candidate.quotes().stream().anyMatch(quote -> quote.evidenceId().equals(value.id())))
                .anyMatch(value -> blocks.find(sourceKey(value.message()), EasyDataLock.NONE) != null)) { return; }
        var identity = candidate.kind() == LongMemories.Kind.EPISODE ? batch.stream().id() + candidate.segments() : Objects.toString(candidate.subjectId());
        // 2026/11/03：同正文的明确保存和自动提取共用记录，不在两个来源重复增加事实。
        var alias = candidate.kind() == LongMemories.Kind.EPISODE ? null : dto("content:" + space).find(contentKey(candidate.subjectId(), candidate.text()), EasyDataLock.READ);
        var id = alias == null ? UUID.nameUUIDFromBytes(StableDigest.sha256(identity, candidate.kind().name(), candidate.attribute(), candidate.text()).getBytes(StandardCharsets.UTF_8)) : UUID.fromString(alias.getValue());
        var entries = dto("entries:" + space); var row = entries.find(id.toString(), EasyDataLock.WRITE);
        var old = row == null ? null : read(row, LongMemories.Entry.class);
        if (old != null && (old.locked() || old.status() == LongMemories.Status.FORGOTTEN || old.status() == LongMemories.Status.SUPERSEDED || !liveExplicit(space, id))) { return; }
        var observed = batch.evidence().stream().filter(value -> candidate.quotes().stream().anyMatch(quote -> quote.evidenceId().equals(value.id())))
                .map(value -> value.message().occurredAt()).max(Comparator.naturalOrder()).orElse(batch.segments().getLast().occurredThrough());
        var status = candidate.mode() == LongMemories.Mode.CONFLICT ? LongMemories.Status.CONFLICT : LongMemories.Status.ACTIVE;
        if (candidate.mode() == LongMemories.Mode.REPLACE) {
            var family = dto(family(space, candidate.subjectId(), candidate.attribute()));
            var replacing = new ArrayList<EasyDataRow>();
            var familyPage = family.recent(Long.MAX_VALUE, 64);
            if (familyPage.size() == 64) { status = LongMemories.Status.CONFLICT; }
            for (var index : familyPage) {
                var priorRow = entries.find(index.getValue(), EasyDataLock.WRITE); if (priorRow == null) { continue; }
                var prior = read(priorRow, LongMemories.Entry.class);
                if (!prior.id().equals(id) && prior.status() == LongMemories.Status.ACTIVE) {
                    if (prior.locked() || !observed.isAfter(prior.observedAt())) { status = LongMemories.Status.CONFLICT; }
                    replacing.add(priorRow);
                }
            }
            // 2026/11/03：先完整仲裁再替代，任何受保护旧值都使本次候选保留为冲突。
            if (status != LongMemories.Status.CONFLICT) {
                for (var priorRow : replacing) {
                    var prior = read(priorRow, LongMemories.Entry.class);
                    write(space, priorRow, new LongMemories.Entry(prior.id(), prior.kind(), prior.subjectId(), prior.attribute(), prior.text(),
                            LongMemories.Status.SUPERSEDED, prior.revision() + 1, false, prior.quotes(), prior.sourceKeys(), prior.segments(), prior.observedAt(), now, prior.timeHint()));
                }
            }
        }
        var quotes = new LinkedHashSet<LongMemories.Quote>(); var sourceIds = new LinkedHashSet<UUID>();
        var sourceKeys = new LinkedHashSet<String>();
        if (old != null) { quotes.addAll(old.quotes()); sourceIds.addAll(old.segments()); } quotes.addAll(candidate.quotes()); sourceIds.addAll(candidate.segments());
        if (old != null) { sourceKeys.addAll(old.sourceKeys()); }
        batch.evidence().stream().filter(value -> candidate.quotes().stream().anyMatch(quote -> quote.evidenceId().equals(value.id())))
                .map(value -> sourceKey(value.message())).forEach(sourceKeys::add);
        write(space, row, new LongMemories.Entry(id, old == null ? candidate.kind() : old.kind(), candidate.subjectId(), old == null ? candidate.attribute() : old.attribute(), candidate.text(), status,
                old == null ? 0 : old.revision() + 1, false, quotes.stream().limit(32).toList(), sourceKeys.stream().limit(32).toList(), sourceIds.stream().limit(32).toList(),
                old != null && old.observedAt().isAfter(observed) ? old.observedAt() : observed, now, candidate.timeHint()));
    }
    /**
     * 保存权威状态和有限索引，旧索引可存在但必须经权威回查。
     * @param space 当前业务空间摘要
     * @param row 原记录或空
     * @param entry 新记录
     */
    private void write(String space, @Nullable EasyDataRow row, LongMemories.Entry entry) {
        var previous = row == null ? null : read(row, LongMemories.Entry.class);
        save(dto("entries:" + space), row, entry.id().toString(), entry);
        // 2026/11/03：正文别名作为同代次保护保留；人工改写不让旧正文被重新自动生成。
        if (entry.subjectId() != null) {
            var aliases = dto("content:" + space); String key = contentKey(entry.subjectId(), entry.text());
            if (aliases.find(key, EasyDataLock.NONE) == null) { aliases.save(aliases.create(key, entry.id().toString())); }
        }
        if (previous != null) {
            for (var target : targets(space, previous)) {
                var index = dto(target); var old = index.find(indexKey(previous), EasyDataLock.NONE);
                if (old != null && previous.revision() != entry.revision()) { index.delete(old); }
            }
        }
        if (entry.status() != LongMemories.Status.ACTIVE && entry.status() != LongMemories.Status.CONFLICT) {
            if (entry.subjectId() != null) {
                var family = dto(family(space, entry.subjectId(), entry.attribute()));
                var link = family.find(entry.id().toString(), EasyDataLock.NONE);
                if (link != null) { family.delete(link); }
            }
            return;
        }
        for (var target : targets(space, entry)) {
            var index = dto(target); var key = indexKey(entry);
            if (index.find(key, EasyDataLock.NONE) == null) { index.save(index.create(key, entry.id().toString())); }
        }
        if (entry.subjectId() != null) {
            var family = dto(family(space, entry.subjectId(), entry.attribute())); String key = entry.id().toString();
            if (family.find(key, EasyDataLock.NONE) == null) { family.save(family.create(key, key)); }
        }
    }
    /**
     * 收集有限主体和话题索引，更新时移除旧版本的入口。
     * @param space 空间摘要
     * @param entry 原记录
     * @return 索引目标
     */
    private static Set<String> targets(String space, LongMemories.Entry entry) {
        var values = new LinkedHashSet<String>(); values.add("recent:" + space);
        if (entry.subjectId() != null) { values.add("subject:" + space + ":" + entry.subjectId()); }
        for (var term : LongMemories.terms(entry.attribute() + " " + entry.text(), 48)) { values.add("term:" + space + ":" + StableDigest.sha256(term).substring(0, 32)); }
        return values;
    }
    /**
     * 用版本构建新索引键，避免同事务删除并重建相同唯一键。
     * @param entry 当前记录
     * @return 稳定索引键
     */
    private static String indexKey(LongMemories.Entry entry) { return entry.revision() + ":" + entry.id(); }
    /**
     * 2026/11/03：同主体正文防重，类别和属性的提取差异不应让同一原文保存多份。
     * @param subject 真实主体
     * @param text 事实正文
     * @return 稳定别名摘要
     */
    private static String contentKey(@Nullable UUID subject, String text) { return StableDigest.sha256(Objects.toString(subject), text); }
    /**
     * 2026/11/03：明确保存的有效期独立于后台原话保留期。
     * @param space 共享空间
     * @param id 条目标识
     * @return 原明确值尚在有效期或没有设置有效期时为真
     */
    private boolean liveExplicit(String space, UUID id) {
        var row = dto("explicit-values:" + space).find(id.toString(), EasyDataLock.NONE);
        if (row == null) { return true; } var expiry = read(row, MemoryFactValue.class).expiresAt(); return expiry == null || expiry.isAfter(clock.instant());
    }
    /**
     * 明确保存的原操作回执，不因后台合并而更换原请求结果。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param fingerprint 原始命令摘要
     * @param result 实际回执
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    private record ExplicitReceipt(String fingerprint, MemoryAddResult result) { }
    /**
     * 以真实平台来源标识证据，空间重新绑定后重复导入同一旧消息也不能绕过遗忘。
     * @param message 原真实消息
     * @return 不含原话的抑制键
     */
    private static String sourceKey(HistoryMessage message) {
        return "source:" + StableDigest.sha256(message.source().scope().clientId(), message.source().scope().scopeId(),
                message.source().userId().orElseThrow().toString(), message.messageId());
    }
    /**
     * 话题命中分数，不把字面检索声称为语义向量检索。
     * @param entry 候选记录
     * @param terms 有界查询片段
     * @return 命中数量
     */
    private static int score(LongMemories.Entry entry, List<String> terms) {
        String text = (entry.attribute() + " " + entry.text()).toLowerCase(Locale.ROOT);
        return (int) terms.stream().filter(text::contains).count();
    }
    /**
     * 单值属性仲裁索引，同主体共享空间中一致，不按来源 scope 隔离。
     * @param space 空间摘要
     * @param subject 事实主体
     * @param attribute 语义属性
     * @return 有界目标键
     */
    private static String family(String space, @Nullable UUID subject, String attribute) { return "family:" + space + ":" + subject + ":" + StableDigest.sha256(attribute).substring(0, 16); }
    /**
     * 长期共享空间仅包含 namespace 与 generation。
     * @param reference 当前授权引用
     * @return 稳定空间摘要
     */
    private static String space(MemoryNamespaceReference reference) { return StableDigest.sha256(reference.namespaceId().value(), Long.toString(reference.generation())); }
    /**
     * 在原空间锁协议内检查代次。
     * @param reference 当前引用
     * @param lock 锁方式
     * @return 当前代次匹配时为真
     */
    private boolean generation(MemoryNamespaceReference reference, EasyDataLock lock) {
        var row = namespaces.lockNamespace(reference.namespaceId(), lock);
        return row != null && namespaces.namespace(row, reference.namespaceId()).generation() == reference.generation();
    }
    /**
     * 普通访问拒绝失效空间，不伪装空数据。
     * @param reference 原引用
     * @throws MemoryFactGenerationException 当空间代次失效时
     */
    private void requireGeneration(MemoryNamespaceReference reference) {
        if (!generation(reference, EasyDataLock.WRITE)) { throw new MemoryFactGenerationException(false); }
    }
    /**
     * 校验固定租约及原输入。
     * @param job 当前持久任务
     * @param batch 原输入
     * @return 当前仍拥有提交权时为真
     */
    private static boolean owns(Job job, LongMemories.Batch batch) {
        return Objects.equals(job.lease(), batch.leaseId()) && job.claimedThrough() == batch.segments().getLast().through() && job.fingerprint().equals(batch.fingerprint());
    }
    /**
     * @param target 长期域目标
     * @return 当前事务 DTO
     */
    private IEasyDataTransactionDto dto(String target) { return data.get(target).atomic(); }
    /**
     * @param target 中期域目标
     * @return 当前事务 DTO
     */
    private IEasyDataTransactionDto layer(String target) { return layers.get(target).atomic(); }
    /**
     * @param value 序号
     * @return 与短期存储一致的排序键
     */
    private static String sequence(long value) { return String.format(Locale.ROOT, "%020d", value); }
    /**
     * 2026/11/06：旧格式没有管理版本，仅补此缺省；显式空值和其他损坏字段仍报错。
     * @param row 持久作业
     * @return 保留原水位、租约和退避的作业
     * @throws MemoryStorageException 当已知旧字段兼容后仍无法解析时
     */
    private Job readJob(EasyDataRow row) {
        try {
            var node = mapper.readTree(row.getValue());
            if (node instanceof ObjectNode object && !object.has("revision")) {
                object.put("revision", 0);
            }
            return mapper.treeToValue(node, Job.class);
        } catch (JacksonException failure) {
            // 2026/11/06：只补已知缺省；其他不匹配须保留原记录并向扫描层报告。
            throw new MemoryStorageException(Job.class, failure);
        }
    }
    /**
     * @param row 原行
     * @param type 目标类型
     * @param <T> 数据类型
     * @return 已解析领域数据
     * @throws MemoryStorageException 当持久 JSON 与领域结构不匹配时
     */
    private <T> T read(EasyDataRow row, Class<T> type) {
        try {
            return mapper.readValue(row.getValue(), type);
        } catch (JacksonException failure) {
            // 2026/11/06：中期、原消息及长期记录均采用同一脱敏格式诊断。
            throw new MemoryStorageException(type, failure);
        }
    }
    /**
     * 按原 DTO 版本保存，不绕过事务基础层。
     * @param dto 原事务目标
     * @param row 原行或空
     * @param key 稳定键
     * @param value 新正文
     */
    private void save(IEasyDataTransactionDto dto, @Nullable EasyDataRow row, String key, Object value) {
        var json = mapper.writeValueAsString(value); if (row == null) { row = dto.create(key, json); } else { row.setValue(json); } dto.save(row);
    }
    /**
     * 本人维护的幂等回执与来源审计，不进入模型普通查询。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param fingerprint 原调用参数指纹
     * @param actorId 真实操作者
     * @param sourceId 原消息标识
     * @param at 实际提交时间
     * @param item 已提交公开投影
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.1-l
     * @version 4.8.0-l
     */
    private record ChangeReceipt(String fingerprint, UUID actorId, String sourceId, Instant at, LongMemories.Item item) { }
    /**
     * 整理租约与已完成覆盖，零候选提交也推进原水位，避免反复学习闲聊。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/05：增加可缺省的失败状态与管理版本，不重置原租约和水位。
     * <br>2026/11/06：旧作业由专用读取入口补齐缺失版本，仍保留严格字段校验。
     *
     * @param through 已整理覆盖
     * @param lease 当前租约
     * @param until 租约到期
     * @param retryAt 下次尝试
     * @param failures 连续失败数
     * @param claimedThrough 本次领取覆盖
     * @param fingerprint 本次输入指纹
     * @param trouble 安全失败状态，旧记录缺省为空
     * @param revision 管理并发版本，旧记录缺省为零
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.1-l
     * @version 4.8.3-l
     */
    private record Job(long through, @Nullable UUID lease, Instant until, Instant retryAt, int failures,
                       long claimedThrough, String fingerprint, @Nullable Trouble trouble, long revision) {
        /** @return 尚无整理进度的任务 */
        private static Job empty() { return new Job(0, null, Instant.EPOCH, Instant.EPOCH, 0, 0, "", null, 0); }
    }

    /**
     * 固定输入的失败计数，不保存候选或异常正文。
     * @param fingerprint 原输入摘要
     * @param invalidAttempts 无效生成次数
     * @param code 最近安全原因
     * @param at 最近失败时刻
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.3-l
     * @version 4.8.3-l
     */
    private record Trouble(String fingerprint, int invalidAttempts, String code, Instant at) { }

    /**
     * 独立管理恢复的幂等回执，保留原操作者及期望版本。
     * @param actorId 真实管理主体
     * @param stream 原绑定分区，重放不得更换
     * @param revision 原请求版本
     * @param at 实际提交时间
     * @param status 原提交结果
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.3-l
     * @version 4.8.3-l
     */
    private record ReplayReceipt(UUID actorId, String stream, long revision, Instant at, LongMemoryJobs.Status status) { }
}
