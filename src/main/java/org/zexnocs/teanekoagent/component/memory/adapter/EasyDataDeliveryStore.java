package org.zexnocs.teanekoagent.component.memory.adapter;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.component.memory.history.IAgentDeliveryStore;
import org.zexnocs.teanekoagent.component.memory.layered.IMemoryMaintenance;
import org.zexnocs.teanekoagent.component.memory.layered.MemoryLayers;
import org.zexnocs.teanekoagent.component.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.database.easydata.EasyDataMaintenancePage;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataRow;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * 专用 EasyData 逐段发送账本；任何未取得真实回执的占用都不宣称成功或自动重试。
 *
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/06：发送正文迁入可清理表，持久摘要继续阻止重复发送；迁移共享原事务。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.5-l
 */
public final class EasyDataDeliveryStore implements IAgentDeliveryStore, IMemoryMaintenance {
    private final TeaNekoAgentEasyData data;
    private final TeaNekoAgentEasyData details;
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
        // 2026/11/06：正文回执可清理，持久表仅保留防重摘要与真实状态。
        this.details = this.data.cleanable();
        lifecycle = new EasyDataMemoryLifecycle(data, namespaces, mapper);
    }
    /**
     * 首次占用同事务固定内容；已确认段不重新发送，未知段必须由人工判断。
     * @param part 原宿主发送段
     * @return 首次占用时为真
     */
    @Override public CompletionStage<Boolean> begin(Part part) {
        return data.transaction("占用 Agent 平台发送段", () -> {
            requireCurrent(part.reference());
            var target = target(part.reference());
            var row = claim(target, key(part));
            if (row != null) {
                var old = mapper.readValue(row.getValue(), Claim.class);
                if (!old.fingerprint().equals(fingerprint(part)) || !old.state().equals("ACKNOWLEDGED")) {
                    throw new IllegalStateException("平台发送段冲突或尚未确认，禁止自动补发。");
                }
                return false;
            }
            var claims = data.get("claims:" + target).atomic();
            claims.save(claims.create(key(part), mapper.writeValueAsString(new Claim(fingerprint(part), "SENDING"))));
            storeDetail(target, key(part), new Stored(part, "SENDING", part.at()));
            return true;
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
            lifecycle.retired(part.reference());
            var target = target(part.reference());
            var row = claim(target, key(part));
            if (row == null) { throw new IllegalStateException("平台发送占用已缺失，不能伪造确认。"); }
            var old = mapper.readValue(row.getValue(), Claim.class);
            if (!old.fingerprint().equals(fingerprint(part))) {
                throw new IllegalArgumentException("平台回执与原发送内容不一致。");
            }
            if (!old.state().equals("ACKNOWLEDGED")) {
                var state = acknowledged ? "ACKNOWLEDGED" : "UNKNOWN";
                row.setValue(mapper.writeValueAsString(new Claim(old.fingerprint(), state)));
                data.get("claims:" + target).atomic().save(row);
                if (part.expiresAt().isAfter(now)) {
                    storeDetail(target, key(part), new Stored(part, state, now));
                }
            }
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
            requireCurrent(reference);
            var result = new LinkedHashMap<UUID, View>();
            var target = target(reference);
            var dto = details.get(target).atomic();
            for (var id : ids) {
                // 2026/11/06：先迁移本次查询范围的旧记录，再从可清理表读取，不依赖全表升级。
                for (var legacy : data.get(target).atomic().scan(id + ".", id + ".99", 32, EasyDataLock.NONE)) {
                    claim(target, legacy.getKey());
                }
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
        return EasyDataMaintenancePage.run(data, "迁移 Agent 旧发送回执", rows -> {
            int changed = 0;
            for (var scanned : rows) {
                if (scanned.getTarget().startsWith("receipts:")) {
                    var observed = mapper.readValue(scanned.getValue(), Stored.class);
                    lifecycle.retired(observed.part().reference());
                    claim(scanned.getTarget(), scanned.getKey());
                    changed++;
                }
            }
            return changed;
        }).thenCompose(migrated -> EasyDataMaintenancePage.run(details, "回收 Agent 到期发送回执", rows -> {
            int changed = 0;
            for (var scanned : rows) {
                if (!scanned.getTarget().startsWith("receipts:")) { continue; }
                var observed = mapper.readValue(scanned.getValue(), Stored.class);
                boolean retired = lifecycle.retired(observed.part().reference());
                var dto = details.get(scanned.getTarget()).atomic(); var row = dto.find(scanned.getKey(), EasyDataLock.WRITE);
                if (row == null) { continue; } var value = mapper.readValue(row.getValue(), Stored.class);
                if (retired || !value.part().expiresAt().isAfter(now)) { dto.delete(row); changed++; }
            }
            return changed;
        }).thenApply(cleaned -> migrated + cleaned));
    }

    /**
     * 在原事务内迁移旧回执：先保存摘要和短期详情，再删除旧正文；失败整体回滚。
     * 锁顺序固定为防重摘要、旧行、短期详情；清空短期表不能清除执行占用。
     * @param target 固定发送分区
     * @param key 请求及段号
     * @return 持久摘要，尚未占用时为空
     */
    private @org.jspecify.annotations.Nullable EasyDataRow claim(String target, String key) {
        var claims = data.get("claims:" + target).atomic();
        var row = claims.find(key, EasyDataLock.WRITE);
        var legacy = data.get(target).atomic();
        var previous = legacy.find(key, EasyDataLock.WRITE);
        if (previous != null) {
            var stored = mapper.readValue(previous.getValue(), Stored.class);
            var expected = new Claim(fingerprint(stored.part()), stored.state());
            if (row == null) {
                row = claims.create(key, mapper.writeValueAsString(expected));
                claims.save(row);
            } else if (!mapper.readValue(row.getValue(), Claim.class).equals(expected)) {
                throw new IllegalStateException("平台回执迁移遇到不一致的防重记录。");
            }
            storeDetail(target, key, stored);
            legacy.delete(previous);
        }
        return row;
    }

    /**
     * 保存可回收的观察正文，与防重状态共享同一事务。
     * @param target 原分区
     * @param key 稳定段编号
     * @param value 本次实际观察
     */
    private void storeDetail(String target, String key, Stored value) {
        var dto = details.get(target).atomic();
        var row = dto.find(key, EasyDataLock.WRITE);
        var json = mapper.writeValueAsString(value);
        if (row == null) {
            row = dto.create(key, json);
        } else {
            row.setValue(json);
        }
        dto.save(row);
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
     * @param part 原发送意图
     * @return 完整身份的稳定摘要，不复制发送正文和临时观察时间
     */
    private String fingerprint(Part part) {
        return StableDigest.sha256(mapper.writeValueAsString(part.reference()), mapper.writeValueAsString(part.actor()),
                part.requestId().toString(), Integer.toString(part.index()), Integer.toString(part.total()), part.digest());
    }

    /**
     * 长期有效的最小防重标记，清理观察正文不能导致重复发送。
     * <br>2026/11/06：不保存 Part、文本或媒体引用。
     * @param fingerprint 原意图摘要
     * @param state 真实发送状态
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.5-l
     * @version 4.8.5-l
     */
    private record Claim(String fingerprint, String state) {
        /**
         * 拒绝损坏状态，不将未知值视为可重发。
         * @param fingerprint 固定摘要
         * @param state 已知状态
         * @throws IllegalArgumentException 当记录结构无效时
         */
        private Claim {
            if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")
                    || !Set.of("SENDING", "ACKNOWLEDGED", "UNKNOWN").contains(state)) {
                throw new IllegalArgumentException("平台发送防重记录无效。");
            }
        }
    }
    /**
     * 发送占用与真实观察状态，不保存传输对象、密钥或图片数据。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param part 原发送段
     * @param state SENDING、ACKNOWLEDGED 或 UNKNOWN
     * @param updatedAt 观察时间
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    private record Stored(Part part, String state, Instant updatedAt) { }
}
