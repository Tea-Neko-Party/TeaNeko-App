package org.zexnocs.teanekoagent.adapter.personality;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.agent.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.database.easydata.EasyDataMaintenancePage;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekoagent.memory.layered.IMemoryMaintenance;
import org.zexnocs.teanekoagent.memory.layered.LongMemories;
import org.zexnocs.teanekoagent.memory.layered.MemoryLayers;
import org.zexnocs.teanekoagent.personality.IStyleObservationStore;
import org.zexnocs.teanekoagent.personality.PersonalityCorrection;
import org.zexnocs.teanekoagent.personality.StyleObservations;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 保存真实表达样本并渐进采用，观察、补正与原长期进度共享同一数据库事务。
 * <br>2026/11/03：按域循环清理过期原话，保留采用时间以防旧证据自动续期。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.1-l
 * @version 4.6.1-l
 */
public final class EasyDataStyleObservationStore implements IStyleObservationStore, IMemoryMaintenance {
    private final TeaNekoAgentEasyData data;
    private final TeaNekoAgentEasyData layers;
    private final EasyDataPersonalityStore personalities;
    private final ObjectMapper mapper;
    private final Supplier<Duration> retention;
    private final Supplier<Duration> cooldown;
    /**
     * 复用专用入口及人格事务实现，构造期间无 I/O。
     * @param data 原 EasyData
     * @param personalities 原人格存储
     * @param mapper 原转换器
     */
    public EasyDataStyleObservationStore(TeaNekoAgentEasyData data, EasyDataPersonalityStore personalities, ObjectMapper mapper) {
        this(data, personalities, mapper, () -> Duration.ofDays(30), () -> Duration.ofSeconds(300));
    }
    /**
     * 沿用本地人格的保留期与采用间隔，构造不读取配置。
     * @param data 原专用入口
     * @param personalities 原人格存储
     * @param mapper 原转换器
     * @param retention 观察和补正保留期
     * @param cooldown 渐进采用间隔
     */
    public EasyDataStyleObservationStore(TeaNekoAgentEasyData data, EasyDataPersonalityStore personalities, ObjectMapper mapper,
            Supplier<Duration> retention, Supplier<Duration> cooldown) {
        this.data = data.inNamespace("agent.personality.observations.v1"); this.layers = data.inNamespace("agent.memory.layers.v1");
        this.personalities = personalities; this.mapper = mapper; this.retention = retention; this.cooldown = cooldown;
    }
    /**
     * 固定仍属于原人格的未到期真实消息，并给模型提供少量已有模式摘要。
     * @param batch 原输入
     * @param basis 当前人格
     * @param fields 可学习字段
     * @param now 当前时间
     * @return 固定目标
     */
    @Override public CompletionStage<StyleObservations.Target> prepare(LongMemories.Batch batch, AgentPersonalitySnapshot basis, Set<String> fields, Instant now) {
        return data.transaction("准备人类表达观察", () -> {
            var ids = new HashSet<String>();
            for (var evidence : batch.evidence()) {
                var row = layers.get("short:" + batch.stream().id()).atomic().find(sequence(evidence.sequence()), EasyDataLock.NONE);
                if (row == null) { continue; }
                var message = mapper.readValue(row.getValue(), MemoryLayers.Message.class);
                if (message.personality().equals(basis.identity()) && message.expiresAt().isAfter(now)
                        && message.message().occurredAt().plus(retention()).isAfter(now) && message.message().equals(evidence.message())) { ids.add(evidence.id()); }
            }
            var known = data.get(group(batch.stream().reference().scope(), basis)).atomic().recent(Long.MAX_VALUE, 16).stream().map(row -> mapper.readValue(row.getValue(), Pattern.class))
                    .filter(item -> item.samples().stream().anyMatch(sample -> sample.at().plus(retention()).isAfter(now)))
                    .map(item -> item.field() + " | " + item.context() + " | " + item.text()).toList();
            return new StyleObservations.Target(basis, fields, ids, known);
        });
    }
    /**
     * 原作者和原话已由长期事务复核，独立消息和片段才增加支持；每批最多采用一个模式。
     * @param batch 原输入
     * @param target 原人格目标
     * @param candidates 已核对候选
     * @param now 当前时间
     * @param guard 当前学习资格
     */
    @Override public void acceptInTransaction(LongMemories.Batch batch, StyleObservations.Target target,
            List<StyleObservations.Candidate> candidates, Instant now, Runnable guard) {
        guard.run(); personalities.verifyObservationTargetInTransaction(batch.stream().reference().scope(), target.basis(), guard);
        var group = group(batch.stream().reference().scope(), target.basis()); var dto = data.get(group).atomic();
        var clock = data.get("adoption").atomic(); var clockRow = clock.find(group, EasyDataLock.WRITE);
        var last = clockRow == null ? Instant.EPOCH : Instant.parse(clockRow.getValue()); boolean adopted = false;
        for (var candidate : candidates) {
            String id = StableDigest.sha256(candidate.field(), candidate.context().name(), candidate.pattern());
            var row = dto.find(id, EasyDataLock.WRITE); var previous = row == null ? null : mapper.readValue(row.getValue(), Pattern.class);
            // 2026/11/03：空观察可物理回收，采用水位单独保存，重新发现旧模式也不能用旧证据续期。
            var marker = data.get("retired-" + group).atomic().find(id, EasyDataLock.READ);
            var adoption = marker == null ? null : mapper.readValue(marker.getValue(), Adoption.class);
            var samples = new LinkedHashMap<String, Sample>();
            if (previous != null) {
                for (var sample : previous.samples()) { if (sample.at().plus(retention()).isAfter(now)) { samples.put(sample.key(), sample); } }
            }
            for (var quote : candidate.quotes()) {
                var evidence = batch.evidence().stream().filter(value -> value.id().equals(quote.evidenceId())).findFirst().orElseThrow();
                var segment = batch.segments().stream().filter(value -> candidate.segments().contains(value.id())
                        && evidence.sequence() >= value.from() && evidence.sequence() <= value.through()).findFirst().orElseThrow();
                var original = evidence.message();
                if (!original.occurredAt().plus(retention()).isAfter(now)) { continue; }
                String key = StableDigest.sha256(original.source().scope().clientId(), original.source().scope().scopeId(), original.messageId(), original.source().userId().orElseThrow().toString());
                samples.putIfAbsent(key, new Sample(key, original.source(), segment.id(), quote.text(), original.occurredAt()));
            }
            var support = samples.values().stream().sorted(Comparator.comparing(Sample::at).reversed()).limit(12).toList();
            Instant adoptedAt = previous == null ? null : previous.adoptedAt();
            Instant adoptedUntil = previous == null ? null : previous.adoptedUntil();
            if (previous == null && adoption != null) { adoptedAt = adoption.at(); adoptedUntil = adoption.until(); }
            Instant priorAdoption = adoptedAt;
            var fresh = support.stream().filter(sample -> priorAdoption == null || sample.at().isAfter(priorAdoption)).toList();
            var interval = cooldown.get();
            if (interval.isNegative() || interval.compareTo(Duration.ofDays(1)) > 0) { throw new IllegalArgumentException("表达采用间隔必须在零至一天内。"); }
            if ((adoptedUntil == null || !adoptedUntil.isAfter(now)) && !adopted && !last.plus(interval).isAfter(now) && fresh.size() >= 3
                    && fresh.stream().map(Sample::segment).distinct().count() >= 2) {
                var latest = fresh.getFirst();
                var correction = new PersonalityCorrection(UUID.nameUUIDFromBytes((group + id + latest.key()).getBytes(StandardCharsets.UTF_8)), target.basis().baseId(),
                        target.basis().baseVersion(), candidate.field(), "观察模仿，适用场景=" + candidate.context() + "：" + candidate.pattern()
                        + "。仅学习表达方式，不复制样本作者的身份、经历或个人偏好。", 0.6, latest.source(), latest.at(), now.plus(retention()), false);
                if (personalities.adoptObservedInTransaction(batch.stream().reference().scope(), target.basis(), correction, guard)) {
                    adoptedAt = now; adoptedUntil = correction.expiresAt(); adopted = true;
                    if (clockRow == null) { clockRow = clock.create(group, now.toString()); } else { clockRow.setValue(now.toString()); }
                    clock.save(clockRow);
                }
            }
            var json = mapper.writeValueAsString(new Pattern(id, candidate.field(), candidate.pattern(), candidate.context(), support, adoptedAt, adoptedUntil));
            if (row == null) { row = dto.create(id, json); } else { row.setValue(json); } dto.save(row);
        }
        guard.run();
    }
    /**
     * 观察按 scope 与人格管理身份隔离，不按共享 namespace 混合群体表达。
     * @param scope 固定作用域
     * @param basis 固定人格
     * @return 稳定目标键
     */
    private static String group(AgentScope scope, AgentPersonalitySnapshot basis) {
        return "patterns:" + StableDigest.sha256(scope.clientId(), scope.scopeId(), basis.identity());
    }
    /**
     * 查看有限观察状态，样本正文不进入普通工具结果。
     * @param scope 当前区域
     * @param basis 当前人格
     * @param now 当前时刻
     * @return 有效观察摘要
     */
    @Override public CompletionStage<List<StyleObservations.View>> inspect(AgentScope scope, AgentPersonalitySnapshot basis, Instant now) {
        return data.transaction("查看当前人格表达观察", () -> data.get(group(scope, basis)).atomic().recent(Long.MAX_VALUE, 16).stream()
                .map(row -> mapper.readValue(row.getValue(), Pattern.class)).map(value -> {
                    var samples = value.samples().stream().filter(sample -> sample.at().plus(retention()).isAfter(now)).toList();
                    return new StyleObservations.View(value.field(), value.text(), value.context(), samples.size(),
                            (int) samples.stream().map(Sample::segment).distinct().count(),
                            samples.stream().map(sample -> sample.source().userId().orElseThrow()).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                            value.adoptedUntil() != null && value.adoptedUntil().isAfter(now));
                }).filter(value -> value.messages() > 0 || value.adopted()).toList());
    }
    /**
     * 采用前校验已有本地保留配置，非法值不能静默扩大原话保存语义。
     * @return 一至三百六十五天的观察保留期
     * @throws IllegalArgumentException 当保留期超出范围时
     */
    private Duration retention() {
        var value = retention.get();
        if (value.compareTo(Duration.ofDays(1)) < 0 || value.compareTo(Duration.ofDays(365)) > 0) { throw new IllegalArgumentException("表达观察保留期必须在一至三百六十五天内。"); }
        return value;
    }
    /**
     * 2026/11/03：清理当前或历史人格目标的过期样本，不依赖启动时的内存目录。
     * @param now 当前时刻
     * @return 实际精简的观察记录数量
     */
    @Override public CompletionStage<Integer> maintain(Instant now) {
        return EasyDataMaintenancePage.run(data, "精简人格观察过期样本", rows -> {
            int changed = 0; var lifetime = retention();
            for (var scanned : rows) {
                if (!scanned.getTarget().startsWith("patterns:")) { continue; }
                var dto = data.get(scanned.getTarget()).atomic(); var row = dto.find(scanned.getKey(), EasyDataLock.WRITE);
                if (row == null) { continue; } var value = mapper.readValue(row.getValue(), Pattern.class);
                var samples = value.samples().stream().filter(sample -> sample.at().plus(lifetime).isAfter(now)).toList();
                if (samples.isEmpty() && (value.adoptedUntil() == null || !value.adoptedUntil().isAfter(now))) {
                    if (value.adoptedAt() != null) {
                        var markers = data.get("retired-" + scanned.getTarget()).atomic(); var marker = markers.find(value.id(), EasyDataLock.WRITE);
                        String json = mapper.writeValueAsString(new Adoption(value.adoptedAt(), value.adoptedUntil()));
                        if (marker == null) { marker = markers.create(value.id(), json); } else { marker.setValue(json); } markers.save(marker);
                    }
                    dto.delete(row); changed++; continue;
                }
                if (samples.size() == value.samples().size()) { continue; }
                row.setValue(mapper.writeValueAsString(new Pattern(value.id(), value.field(), value.text(), value.context(), samples, value.adoptedAt(), value.adoptedUntil())));
                dto.save(row); changed++;
            }
            return changed;
        });
    }
    /**
     * 取得与短期一致的排序键。
     * @param value 原消息序号
     * @return 稳定排序键
     */
    private static String sequence(long value) { return String.format(Locale.ROOT, "%020d", value); }
    /**
     * 有来源的独立表达样本，真实平台来源参与防重。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param key 来源摘要
     * @param source 原作者
     * @param segment 原中期片段
     * @param quote 原话
     * @param at 事件时刻
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.1-l
     * @version 4.6.1-l
     */
    private record Sample(String key, AgentActorContext source, UUID segment, String quote, Instant at) { }
    /**
     * 已保存的表达模式及支持样本，采用不会丢弃观察依据。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param id 稳定模式标识
     * @param field 表达字段
     * @param text 模式摘要
     * @param context 适用场景
     * @param samples 有限真实样本
     * @param adoptedAt 实际采用时刻，空值表示仍为候选
     * @param adoptedUntil 原补正实际到期时刻，不随配置修改伪造续期
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.1-l
     * @version 4.6.1-l
     */
    private record Pattern(String id, String field, String text, StyleObservations.Context context, List<Sample> samples, @Nullable Instant adoptedAt, @Nullable Instant adoptedUntil) { }
    /**
     * 已回收模式的无正文采用水位，避免旧样本重新导入后自动续期。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param at 原采用时间
     * @param until 原实际到期
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.1-l
     * @version 4.6.1-l
     */
    private record Adoption(Instant at, @Nullable Instant until) { }
}
