package org.zexnocs.teanekoplugin.agent.social.social.adapter;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.database.easydata.EasyDataMaintenancePage;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.EasyDataMemoryLifecycle;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.EasyDataMemoryNamespaceStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.IMemoryMaintenance;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoplugin.agent.social.social.ISocialStateStore;
import org.zexnocs.teanekoplugin.agent.social.social.SocialState;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;

/**
 * 关系行与已评估来源同事务提交；空间锁先于关系锁，不产生丢失更新。
 * <br>2026/11/06：关系与防重属于持久状态，清空空间后按新代次隔离。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.9.0-l
 */
@Service
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.social")
public final class EasyDataSocialStateStore implements ISocialStateStore, IMemoryMaintenance {
    private final TeaNekoAgentEasyData data;
    private final EasyDataMemoryNamespaceStore namespaces;
    private final ObjectMapper mapper;
    private final EasyDataMemoryLifecycle lifecycle;

    /**
     * @param data 专用入口
     * @param namespaces 空间代次锁
     * @param mapper 序列化器
     */
    public EasyDataSocialStateStore(TeaNekoAgentEasyData data, EasyDataMemoryNamespaceStore namespaces, ObjectMapper mapper) {
        this.data = data.inNamespace("agent.social.v1");
        this.namespaces = namespaces;
        this.mapper = mapper;
        this.lifecycle = new EasyDataMemoryLifecycle(data, namespaces, mapper);
    }

    /**
     * @param reference 空间
     * @param user 用户
     * @param initial 初值
     * @return 原状态或未持久化初值
     */
    @Override public CompletionStage<SocialState> read(MemoryNamespaceReference reference, UUID user, int initial) {
        return data.transaction("读取关系与心情", () -> namespaces.inGeneration(reference, () -> {
            var row = data.get(EasyDataMemoryLifecycle.space(reference)).atomic().find(user.toString(), EasyDataLock.READ);
            return row == null ? SocialState.initial(initial) : mapper.readValue(row.getValue(), SocialState.class);
        }));
    }

    /**
     * @param reference 空间
     * @param user 用户
     * @param initial 初值
     * @param event 原消息的防重身份
     * @param now 事件时间
     * @param update 锁内纯转换
     * @return 实际提交的状态
     */
    @Override public CompletionStage<SocialState> update(MemoryNamespaceReference reference, UUID user, int initial,
            String event, Instant now, UnaryOperator<SocialState> update) {
        return data.transaction("更新关系与心情", () -> namespaces.inGeneration(reference, () -> {
            var target = EasyDataMemoryLifecycle.space(reference);
            var dto = data.get(target).atomic();
            var row = dto.find(user.toString(), EasyDataLock.WRITE);
            var previous = row == null ? SocialState.initial(initial) : mapper.readValue(row.getValue(), SocialState.class);
            var events = data.get("events:" + target + ":" + user).atomic();
            if (!event.isEmpty() && events.find(event, EasyDataLock.READ) != null) { return previous; }
            var next = update.apply(previous);
            // 2026/11/06：无正文的代次定位用于原维护设施清理旧关系，不因空间换代无限遗留事件账本。
            var locations = data.get("spaces").atomic();
            if (locations.find(target, EasyDataLock.NONE) == null) {
                locations.save(locations.create(target, mapper.writeValueAsString(reference)));
            }
            if (!next.equals(previous)) {
                var body = mapper.writeValueAsString(next);
                if (row == null) { row = dto.create(user.toString(), body); }
                else { row.setValue(body); }
                dto.save(row);
            }
            if (!event.isEmpty()) { events.save(events.create(event, now.toString())); }
            return next;
        }));
    }

    /**
     * 清理已经换代的关系及防重记录；当前代次的防重记录仍保留，不能让旧消息再次刷分。
     * @param now 原维护时刻，空间退役以权威代次为准
     * @return 本页实际删除的记录数
     */
    @Override public CompletionStage<Integer> maintain(Instant now) {
        return EasyDataMaintenancePage.run(data, "清理旧代次关系与心情", rows -> {
            int removed = 0;
            for (var row : rows) {
                var target = row.getTarget();
                String space = target.startsWith("events:") ? target.substring(7).split(":", 2)[0] : target;
                if (space.length() != 64) { continue; }
                var locator = data.get("spaces").atomic().find(space, EasyDataLock.NONE);
                if (locator == null) { continue; }
                var reference = mapper.readValue(locator.getValue(), MemoryNamespaceReference.class);
                if (!lifecycle.retired(reference)) { continue; }
                var owner = data.get(target).atomic();
                var current = owner.find(row.getKey(), EasyDataLock.WRITE);
                if (current != null) {
                    owner.delete(current);
                    removed++;
                }
            }
            return removed;
        });
    }
}
