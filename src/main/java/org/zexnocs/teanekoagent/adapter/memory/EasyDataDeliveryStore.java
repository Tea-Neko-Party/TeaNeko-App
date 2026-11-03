package org.zexnocs.teanekoagent.adapter.memory;

import org.zexnocs.teanekoagent.database.easydata.EasyDataMaintenancePage;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekoagent.history.IAgentDeliveryStore;
import org.zexnocs.teanekoagent.memory.layered.IMemoryMaintenance;
import org.zexnocs.teanekoagent.memory.layered.MemoryLayers;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * 专用 EasyData 逐段发送账本；任何未取得真实回执的占用都不宣称成功或自动重试。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.2-l
 */
public final class EasyDataDeliveryStore implements IAgentDeliveryStore, IMemoryMaintenance {
    private final TeaNekoAgentEasyData data;
    private final EasyDataMemoryNamespaceStore namespaces;
    private final EasyDataMemoryLifecycle lifecycle;
    private final ObjectMapper mapper;
    /**
     * 借用原空间锁和数据入口，构造无 I/O。
     * @param data 专用入口
     * @param namespaces 原空间元数据
     * @param mapper 原转换器
     */
    public EasyDataDeliveryStore(TeaNekoAgentEasyData data, EasyDataMemoryNamespaceStore namespaces, ObjectMapper mapper) {
        this.data = data.inNamespace("agent.delivery.v1"); this.namespaces = namespaces; this.mapper = mapper;
        lifecycle = new EasyDataMemoryLifecycle(data, namespaces, mapper);
    }
    /**
     * 首次占用同事务固定内容；已确认段不重新发送，未知段必须由人工判断。
     * @param part 原宿主发送段
     * @return 首次占用时为真
     */
    @Override public CompletionStage<Boolean> begin(Part part) {
        return data.transaction("占用 Agent 平台发送段", () -> {
            requireCurrent(part.reference()); var dto = data.get(target(part.reference())).atomic(); var row = dto.find(key(part), EasyDataLock.WRITE);
            if (row != null) {
                var old = mapper.readValue(row.getValue(), Stored.class);
                if (!same(old.part(), part) || !old.state().equals("ACKNOWLEDGED")) { throw new IllegalStateException("平台发送段冲突或尚未确认，禁止自动补发。"); }
                return false;
            }
            dto.save(dto.create(key(part), mapper.writeValueAsString(new Stored(part, "SENDING", part.at())))); return true;
        });
    }
    /**
     * 发送结果仍记录到原分区，clear 不会把已经发生的外部动作改投新空间。
     * @param part 原占用
     * @param acknowledged 实际平台回执是否成功
     * @param now 观察时刻
     * @return 真实提交阶段
     */
    @Override public CompletionStage<Void> finish(Part part, boolean acknowledged, Instant now) {
        return data.transaction("记录 Agent 平台发送回执", () -> {
            lifecycle.retired(part.reference()); var dto = data.get(target(part.reference())).atomic(); var row = dto.find(key(part), EasyDataLock.WRITE);
            if (row == null) { throw new IllegalStateException("平台发送占用已缺失，不能伪造确认。"); }
            var old = mapper.readValue(row.getValue(), Stored.class);
            if (!same(old.part(), part)) { throw new IllegalArgumentException("平台回执与原发送内容不一致。"); }
            if (!old.state().equals("ACKNOWLEDGED")) { row.setValue(mapper.writeValueAsString(new Stored(old.part(), acknowledged ? "ACKNOWLEDGED" : "UNKNOWN", now))); dto.save(row); }
            return true;
        }).thenApply(ignored -> null);
    }
    /**
     * 当前固定代次只返回已确认段的文本，部分成功不冒充整条成功。
     * @param reference 当前引用
     * @param requests 有界请求集合
     * @param now 当前时刻
     * @return 回执摘要
     * @throws IllegalArgumentException 当请求数量超过二十四个时
     */
    @Override public CompletionStage<Map<UUID, View>> read(MemoryNamespaceReference reference, Set<UUID> requests, Instant now) {
        var ids = Set.copyOf(requests); if (ids.size() > 24) { throw new IllegalArgumentException("平台回执查询超过预算。"); }
        return data.transaction("读取 Agent 平台回执", () -> {
            requireCurrent(reference); var result = new LinkedHashMap<UUID, View>(); var dto = data.get(target(reference)).atomic();
            for (var id : ids) {
                var values = dto.scan(id + ".", id + ".99", 32, EasyDataLock.READ).stream().map(row -> mapper.readValue(row.getValue(), Stored.class))
                        .filter(value -> value.part().expiresAt().isAfter(now)).toList();
                if (values.isEmpty()) { continue; }
                int total = values.getFirst().part().total(); var confirmed = values.stream().filter(value -> value.state().equals("ACKNOWLEDGED")).toList();
                result.put(id, new View(confirmed.size() == total ? "ACKNOWLEDGED" : confirmed.isEmpty() ? "UNKNOWN" : "PARTIAL", confirmed.size(), total,
                        confirmed.stream().map(value -> value.part().text()).collect(Collectors.joining("\n"))));
            }
            return Map.copyOf(result);
        });
    }
    /**
     * 到期或旧代次逐页回收，未决段不自动重发。
     * @param now 当前时刻
     * @return 实际回收数量
     */
    @Override public CompletionStage<Integer> maintain(Instant now) {
        return EasyDataMaintenancePage.run(data, "回收 Agent 到期发送回执", rows -> {
            int changed = 0;
            for (var scanned : rows) {
                if (!scanned.getTarget().startsWith("receipts:")) { continue; }
                var observed = mapper.readValue(scanned.getValue(), Stored.class);
                boolean retired = lifecycle.retired(observed.part().reference());
                var dto = data.get(scanned.getTarget()).atomic(); var row = dto.find(scanned.getKey(), EasyDataLock.WRITE);
                if (row == null) { continue; } var value = mapper.readValue(row.getValue(), Stored.class);
                if (retired || !value.part().expiresAt().isAfter(now)) { dto.delete(row); changed++; }
            }
            return changed;
        });
    }
    /**
     * 新发送占用必须仍属于原代次。
     * @param reference 固定引用
     * @throws IllegalStateException 当原空间缺失或已经换代时
     */
    private void requireCurrent(MemoryNamespaceReference reference) {
        var row = namespaces.lockNamespace(reference.namespaceId(), EasyDataLock.READ);
        if (row == null || namespaces.namespace(row, reference.namespaceId()).generation() != reference.generation()) { throw new IllegalStateException("平台发送的原空间已换代。"); }
    }
    /**
     * 固定 scope、绑定与空间代次，不共享其他群的发送内容。
     * @param reference 原引用
     * @return 固定目标
     */
    private static String target(MemoryNamespaceReference reference) { return "receipts:" + MemoryLayers.stream(reference); }
    /**
     * 保留同一请求中的顺序。
     * @param part 原发送段
     * @return 稳定段键
     */
    private static String key(Part part) { return part.requestId() + "." + String.format(Locale.ROOT, "%02d", part.index()); }
    /**
     * 重放忽略新的观察时间，目标、内容和总段数必须相同。
     * @param left 已持久占用
     * @param right 当前发送意图
     * @return 同一固定操作时为真
     */
    private static boolean same(Part left, Part right) {
        return left.reference().equals(right.reference()) && left.actor().equals(right.actor()) && left.requestId().equals(right.requestId())
                && left.index() == right.index() && left.total() == right.total() && left.digest().equals(right.digest());
    }
    /**
     * 发送占用与真实观察状态，不保存传输对象、密钥或图片数据。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * @param part 原发送段
     * @param state SENDING、ACKNOWLEDGED 或 UNKNOWN
     * @param updatedAt 观察时间
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.2-l
     */
    private record Stored(Part part, String state, Instant updatedAt) { }
}
