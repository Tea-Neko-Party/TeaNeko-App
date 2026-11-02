package org.zexnocs.teanekoagent.adapter.memory;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekoagent.history.HistoryContext;
import org.zexnocs.teanekoagent.history.HistoryMessage;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactGenerationException;
import org.zexnocs.teanekoagent.memory.layered.ILayeredMemoryStore;
import org.zexnocs.teanekoagent.memory.layered.IMemoryMaintenance;
import org.zexnocs.teanekoagent.memory.layered.LayeredMemorySettings;
import org.zexnocs.teanekoagent.memory.layered.MemoryLayers;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataRow;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataTransactionDto;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 专用 EasyData 中的短期消息、中期片段和压缩水位，使用空间锁与租约拒绝迟到覆盖。
 * 短期采集可加入现有历史事务，重启后目录和未处理序号仍在，不依赖内存通知。
 * <br>2026/11/03：有界清理过期与旧代次内容，保留当前分区序号和未处理连续性。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.1-l
 */
public final class EasyDataLayeredMemoryStore implements ILayeredMemoryStore, IMemoryMaintenance {
    private final TeaNekoAgentEasyData data;
    private final EasyDataMemoryNamespaceStore namespaces;
    private final ObjectMapper mapper;
    private final EasyDataMemoryLifecycle lifecycle;
    private final Supplier<LayeredMemorySettings> settings;
    /**
     * 保存既有事务入口，不预先读库。
     * @param data 专用数据入口
     * @param namespaces 原空间锁与解析入口
     * @param mapper 原 JSON 转换器
     */
    public EasyDataLayeredMemoryStore(TeaNekoAgentEasyData data, EasyDataMemoryNamespaceStore namespaces, ObjectMapper mapper) {
        // 2026/11/03：独立旧宿主沿用默认保留期。
        this(data, namespaces, mapper, LayeredMemorySettings::defaults);
    }
    /**
     * 2026/11/03：维护与读取共用同一本地保留策略。
     * @param data 原数据入口
     * @param namespaces 原空间元数据
     * @param mapper 原转换器
     * @param settings 动态保留策略
     */
    public EasyDataLayeredMemoryStore(TeaNekoAgentEasyData data, EasyDataMemoryNamespaceStore namespaces, ObjectMapper mapper, Supplier<LayeredMemorySettings> settings) {
        this.data = data.inNamespace("agent.memory.layers.v1"); this.namespaces = namespaces;
        this.lifecycle = new EasyDataMemoryLifecycle(data, namespaces, mapper); this.settings = settings;
        this.mapper = mapper.rebuild().disable(SerializationFeature.INDENT_OUTPUT, SerializationFeature.WRAP_ROOT_VALUE).build();
    }
    /**
     * 与调用方原历史事务一起提交消息、序号和可恢复目录，不启动异步任务。
     * @param context 原历史授权边界
     * @param message 真实消息
     * @param expiresAt 原始保留期
     * @throws IllegalArgumentException 当重投内容或来源冲突时
     * @throws MemoryFactGenerationException 当空间代次已变化时
     */
    public void captureInTransaction(HistoryContext context, HistoryMessage message, Instant expiresAt) {
        var reference = context.reference();
        if (!message.source().scope().equals(reference.scope())) { throw new IllegalArgumentException("短期记忆来源作用域不匹配。"); }
        requireGeneration(reference);
        var stream = new MemoryLayers.Stream(MemoryLayers.stream(reference), reference); var stateRow = stateRow(stream);
        // 2026/11/03：无正文定位与原消息同事务保存，为旧代次回收保留归属。
        lifecycle.registerInTransaction(stream);
        var state = stateRow == null ? State.empty() : decode(stateRow, State.class);
        var messages = dto("short:" + stream.id()); var source = dto("source:" + stream.id());
        var sourceKey = StableDigest.sha256(message.role().name(), message.messageId());
        var existing = source.find(sourceKey, EasyDataLock.NONE);
        if (existing != null) {
            var row = messages.find(existing.getValue(), EasyDataLock.NONE);
            if (row == null || !decode(row, MemoryLayers.Message.class).message().sameContent(message)) {
                throw new IllegalArgumentException("同一短期记忆消息对应不同正文。");
            }
            return;
        }
        long sequence = Math.incrementExact(state.last()); String key = key(sequence);
        messages.save(messages.create(key, mapper.writeValueAsString(new MemoryLayers.Message(sequence, message, context.personality().identity(), expiresAt))));
        source.save(source.create(sourceKey, key));
        saveState(stream, stateRow, new State(sequence, state.covered(), state.discarded(), state.lease(), state.until(), state.notBefore(),
                state.attempts(), state.claimedThrough(), state.fingerprint()));
        var directory = dto("streams");
        if (directory.find(stream.id(), EasyDataLock.NONE) == null) { directory.save(directory.create(stream.id(), mapper.writeValueAsString(stream))); }
    }
    /**
     * 有界读取持久目录，不因任务进程重启丢失分区。
     * @param after 原目录游标
     * @return 最多十六个目录项
     */
    @Override public CompletionStage<List<MemoryLayers.Stream>> streams(String after) {
        return data.transaction("读取分层记忆目录", () -> dto("streams").scan(after, null, 16, EasyDataLock.NONE).stream()
                .map(row -> decode(row, MemoryLayers.Stream.class)).toList());
    }
    /**
     * 原子领取已达到阈值的连续旧段，保留最近消息，失败租约到期后可恢复。
     * @param stream 固定来源分区
     * @param settings 本次工作量
     * @param now 当前时间
     * @return 已取得租约的不可变输入
     */
    @Override public CompletionStage<Optional<MemoryLayers.Batch>> claim(MemoryLayers.Stream stream, LayeredMemorySettings settings, Instant now) {
        return data.transaction("领取记忆压缩任务", () -> {
            if (!generation(stream.reference())) { return Optional.empty(); }
            var row = stateRow(stream); if (row == null) { return Optional.empty(); } var state = decode(row, State.class);
            long processed = Math.max(state.covered(), state.discarded());
            if (!settings.enabled() || now.isBefore(state.until()) || now.isBefore(state.notBefore())
                    || state.last() - processed <= settings.keepMessages()) { return Optional.empty(); }
            long end = state.last() - settings.keepMessages();
            var rows = dto("short:" + stream.id()).scan(key(processed), key(end), settings.batchMessages(), EasyDataLock.READ);
            var selected = new ArrayList<MemoryLayers.Message>(); long discarded = state.discarded();
            for (var messageRow : rows) {
                var message = decode(messageRow, MemoryLayers.Message.class);
                if (!message.expiresAt().isAfter(now)) {
                    if (!selected.isEmpty()) { break; }
                    discarded = message.sequence(); continue;
                }
                if (message.sequence() != (selected.isEmpty() ? Math.max(processed, discarded) + 1 : selected.getLast().sequence() + 1)) {
                    throw new IllegalStateException("短期记忆压缩区间不连续。");
                }
                selected.add(message);
                if (mapper.writeValueAsBytes(selected).length > settings.maxInputBytes() - 2048) { selected.removeLast(); break; }
            }
            if (selected.isEmpty()) {
                saveState(stream, row, new State(state.last(), state.covered(), discarded, null, Instant.EPOCH,
                        now.plusSeconds(60), state.attempts(), 0, ""));
                return Optional.empty();
            }
            if (state.last() - processed < settings.triggerMessages() && mapper.writeValueAsBytes(selected).length < settings.triggerBytes()) {
                return Optional.empty();
            }
            var lease = UUID.randomUUID(); var fingerprint = StableDigest.sha256(mapper.writeValueAsString(selected));
            long through = selected.getLast().sequence();
            saveState(stream, row, new State(state.last(), state.covered(), discarded, lease, now.plus(settings.timeout()).plusSeconds(30),
                    Instant.EPOCH, state.attempts(), through, fingerprint));
            return Optional.of(new MemoryLayers.Batch(stream, lease, through, fingerprint, selected));
        });
    }
    /**
     * 摘要和水位同事务提交，追加的新尾部不使原输入失效。
     * @param batch 原租约和输入
     * @param digest 已生成候选
     * @param now 提交观察时刻
     * @return 原租约仍有效且确实压缩时为真
     */
    @Override public CompletionStage<Boolean> commit(MemoryLayers.Batch batch, MemoryLayers.Digest digest, Instant now) {
        return data.transaction("提交中期记忆片段", () -> {
            if (!generation(batch.stream().reference())) { return false; }
            var row = stateRow(batch.stream()); if (row == null) { return false; } var state = decode(row, State.class);
            if (!owns(state, batch) || !state.until().isAfter(now)) { return false; }
            var actual = dto("short:" + batch.stream().id()).scan(key(batch.messages().getFirst().sequence() - 1), key(batch.through()),
                    batch.messages().size(), EasyDataLock.READ).stream().map(item -> decode(item, MemoryLayers.Message.class)).toList();
            if (!StableDigest.sha256(mapper.writeValueAsString(actual)).equals(batch.fingerprint())
                    || actual.stream().anyMatch(message -> !message.expiresAt().isAfter(now))) { return false; }
            if (mapper.writeValueAsBytes(digest).length >= mapper.writeValueAsBytes(actual).length) {
                throw new IllegalArgumentException("中期候选没有缩小原输入。");
            }
            var id = UUID.nameUUIDFromBytes((batch.stream().id() + ":" + batch.fingerprint()).getBytes(StandardCharsets.UTF_8));
            var start = actual.stream().map(value -> value.message().occurredAt()).min(Comparator.naturalOrder()).orElseThrow();
            var end = actual.stream().map(value -> value.message().occurredAt()).max(Comparator.naturalOrder()).orElseThrow();
            var segment = new MemoryLayers.Segment(id, batch.stream(), actual.getFirst().sequence(), batch.through(), batch.fingerprint(), digest, start, end, now);
            var segments = dto("medium:" + batch.stream().id()); String key = key(batch.through());
            if (segments.find(key, EasyDataLock.NONE) != null) { throw new IllegalStateException("中期覆盖片段重复。"); }
            segments.save(segments.create(key, mapper.writeValueAsString(segment)));
            saveState(batch.stream(), row, new State(state.last(), batch.through(), state.discarded(), null, Instant.EPOCH, Instant.EPOCH, 0, 0, ""));
            return true;
        });
    }
    /**
     * 保存失败退避和累计尝试，旧租约不能覆盖新任务。
     * @param batch 原任务
     * @param now 当前时间
     * @return 事务完成阶段
     */
    @Override public CompletionStage<Void> retry(MemoryLayers.Batch batch, Instant now) {
        return data.transaction("保存记忆压缩退避", () -> {
            if (!generation(batch.stream().reference())) { return false; }
            var row = stateRow(batch.stream()); if (row == null) { return false; } var state = decode(row, State.class);
            if (!owns(state, batch)) { return false; }
            int attempts = Math.min(30, state.attempts() + 1);
            long seconds = Math.min(3600, 30L << Math.min(7, attempts - 1));
            saveState(batch.stream(), row, new State(state.last(), state.covered(), state.discarded(), null, Instant.EPOCH,
                    now.plusSeconds(seconds), attempts, 0, ""));
            return true;
        }).thenApply(ignored -> null);
    }
    /**
     * 重启后读取同一来源的最新消息及片段，过期内容不注入。
     * @param reference 当前授权引用
     * @param now 查询时间
     * @param retention 中期保留期
     * @return 固定代次内的有界恢复视图
     */
    @Override public CompletionStage<MemoryLayers.View> read(MemoryNamespaceReference reference, Instant now, Duration retention) {
        return data.transaction("读取分层记忆上下文", () -> {
            requireGeneration(reference); var stream = new MemoryLayers.Stream(MemoryLayers.stream(reference), reference);
            var row = stateRow(stream); var state = row == null ? State.empty() : decode(row, State.class);
            var recent = dto("short:" + stream.id()).recent(Long.MAX_VALUE, 24).stream().map(item -> decode(item, MemoryLayers.Message.class))
                    .filter(item -> item.expiresAt().isAfter(now)).sorted(Comparator.comparingLong(MemoryLayers.Message::sequence)).toList();
            var segments = dto("medium:" + stream.id()).recent(Long.MAX_VALUE, 6).stream().map(item -> decode(item, MemoryLayers.Segment.class))
                    .filter(item -> item.createdAt().plus(retention).isAfter(now)).sorted(Comparator.comparingLong(MemoryLayers.Segment::from)).toList();
            return new MemoryLayers.View(recent, segments, state.covered());
        });
    }
    /**
     * 2026/11/03：每批维护一个分区，短期十六行和中期八行；清理不假装消息已压缩。
     * @param now 当前时间
     * @return 实际回收的正文及失效状态数量
     */
    @Override public CompletionStage<Integer> maintain(Instant now) {
        return data.transaction("清理分层记忆过期内容", () -> {
            var progress = dto("maintenance"); var cursor = progress.find("stream", EasyDataLock.WRITE);
            String after = cursor == null ? "" : cursor.getValue();
            var page = dto("streams").scan(after, null, 1, EasyDataLock.NONE);
            if (page.isEmpty()) { storeProgress(progress, cursor, "stream", ""); return 0; }
            var directoryRow = page.getFirst(); var stream = decode(directoryRow, MemoryLayers.Stream.class);
            boolean retired = lifecycle.retired(stream.reference()); lifecycle.registerInTransaction(stream);
            var stateRow = stateRow(stream); var state = stateRow == null ? State.empty() : decode(stateRow, State.class);
            var step = progress.find(stream.id(), EasyDataLock.WRITE);
            var position = step == null ? new Sweep("", "") : decode(step, Sweep.class);
            var shortData = dto("short:" + stream.id()); var source = dto("source:" + stream.id());
            var rows = shortData.scan(position.shortAfter(), null, 16, EasyDataLock.WRITE); int changed = 0;
            long discarded = state.discarded(); long processed = Math.max(state.covered(), discarded);
            for (var row : rows) {
                var message = decode(row, MemoryLayers.Message.class);
                if (!retired && (message.expiresAt().isAfter(now) || message.sequence() > processed + 1)) { continue; }
                shortData.delete(row); changed++;
                var link = source.find(StableDigest.sha256(message.message().role().name(), message.message().messageId()), EasyDataLock.WRITE);
                if (link != null && link.getValue().equals(row.getKey())) { source.delete(link); }
                if (!retired && message.sequence() == processed + 1) { discarded = message.sequence(); processed = discarded; }
            }
            if (!retired && stateRow != null && discarded != state.discarded()) {
                saveState(stream, stateRow, new State(state.last(), state.covered(), discarded, state.lease(), state.until(), state.notBefore(),
                        state.attempts(), state.claimedThrough(), state.fingerprint()));
            }
            var medium = dto("medium:" + stream.id()); var segments = medium.scan(position.mediumAfter(), null, 8, EasyDataLock.WRITE);
            var retention = settings.get().retention();
            for (var row : segments) {
                if (retired || !decode(row, MemoryLayers.Segment.class).createdAt().plus(retention).isAfter(now)) { medium.delete(row); changed++; }
            }
            if (retired) {
                for (var row : source.scan("", null, 16, EasyDataLock.WRITE)) { source.delete(row); }
            }
            if (retired && shortData.scan("", null, 1, EasyDataLock.NONE).isEmpty()
                    && source.scan("", null, 1, EasyDataLock.NONE).isEmpty() && medium.scan("", null, 1, EasyDataLock.NONE).isEmpty()) {
                if (stateRow != null) { dto("states").delete(stateRow); changed++; }
                dto("streams").delete(directoryRow); if (step != null) { progress.delete(step); }
            } else {
                storeProgress(progress, step, stream.id(), mapper.writeValueAsString(new Sweep(rows.size() < 16 ? "" : rows.getLast().getKey(),
                        segments.size() < 8 ? "" : segments.getLast().getKey())));
            }
            storeProgress(progress, cursor, "stream", stream.id()); return changed;
        });
    }
    /**
     * 2026/11/03：维护游标只在实际提交后推进，删除失败不会越过原批次。
     * @param progress 维护目标
     * @param row 原行
     * @param key 进度键
     * @param value 新进度
     */
    private void storeProgress(IEasyDataTransactionDto progress, @Nullable EasyDataRow row, String key, String value) {
        if (row == null) { row = progress.create(key, value); } else { row.setValue(value); } progress.save(row);
    }
    /**
     * 分区内的独立有界扫描位置，不与成功压缩水位混用。
     * @param shortAfter 短期排除下界
     * @param mediumAfter 中期排除下界
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.1-l
     */
    private record Sweep(String shortAfter, String mediumAfter) { }
    /**
     * 固定单目标事务 DTO，不公开仓库或 SQL。
     * @param target 本域目标
     * @return 仅当前事务可用的 DTO
     */
    private IEasyDataTransactionDto dto(String target) { return data.get(target).atomic(); }
    /**
     * 始终先锁空间，再锁单分区水位，保持写入和清空的锁顺序。
     * @param reference 固定引用
     * @return 当前空间代次仍匹配时为真
     */
    private boolean generation(MemoryNamespaceReference reference) {
        var row = namespaces.lockNamespace(reference.namespaceId(), EasyDataLock.READ);
        return row != null && namespaces.namespace(row, reference.namespaceId()).generation() == reference.generation();
    }
    /**
     * 拒绝失效代次，不伪装空记忆。
     * @param reference 原引用
     * @throws MemoryFactGenerationException 当空间缺失或代次失效时
     */
    private void requireGeneration(MemoryNamespaceReference reference) {
        if (!generation(reference)) { throw new MemoryFactGenerationException(false); }
    }
    /**
     * 读取并锁定当前分区水位。
     * @param stream 原分区
     * @return 原行，不存在时为空
     */
    private @Nullable EasyDataRow stateRow(MemoryLayers.Stream stream) { return dto("states").find(stream.id(), EasyDataLock.WRITE); }
    /**
     * 保留原行版本保存水位，首次创建由唯一键仲裁。
     * @param stream 分区
     * @param row 原行或空
     * @param state 新状态
     */
    private void saveState(MemoryLayers.Stream stream, @Nullable EasyDataRow row, State state) {
        var states = dto("states"); String json = mapper.writeValueAsString(state);
        if (row == null) { row = states.create(stream.id(), json); } else { row.setValue(json); } states.save(row);
    }
    /**
     * 同时核对领取标识、范围和原指纹。
     * @param state 当前持久状态
     * @param batch 原任务
     * @return 原领取仍拥有提交权时为真
     */
    private static boolean owns(State state, MemoryLayers.Batch batch) {
        return Objects.equals(state.lease(), batch.leaseId()) && state.claimedThrough() == batch.through() && state.fingerprint().equals(batch.fingerprint());
    }
    /**
     * 将正序号编码为可按键扫描的固定宽度值。
     * @param sequence 非负序号
     * @return 排序键
     */
    private static String key(long sequence) { return String.format(Locale.ROOT, "%020d", sequence); }
    /**
     * 解析显式 DTO，损坏数据不得进入模型。
     * @param row 原数据行
     * @param type 明确类型
     * @param <T> 返回类型
     * @return 领域数据
     */
    private <T> T decode(EasyDataRow row, Class<T> type) { return mapper.readValue(row.getValue(), type); }
    /**
     * 持久作业状态；过期源跳过水位与成功压缩水位分开，不能声称已经记住已过期原文。
     * @param last 最新消息序号
     * @param covered 已提交摘要覆盖
     * @param discarded 已过期源跳过位置
     * @param lease 原领取令牌
     * @param until 领取到期
     * @param notBefore 下次尝试时刻
     * @param attempts 连续失败次数
     * @param claimedThrough 原领取范围
     * @param fingerprint 原领取指纹
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.6.1-l
     */
    private record State(long last, long covered, long discarded, @Nullable UUID lease, Instant until, Instant notBefore,
                         int attempts, long claimedThrough, String fingerprint) {
        /** @return 尚无消息和处理进度的初始状态 */
        private static State empty() { return new State(0, 0, 0, null, Instant.EPOCH, Instant.EPOCH, 0, 0, ""); }
    }
}
